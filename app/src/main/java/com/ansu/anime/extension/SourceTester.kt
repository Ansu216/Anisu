package com.ansu.anime.extension

import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.model.InstalledAddon
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.Video
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import okhttp3.Request

/** One thing an installed source can be tested as: an extension source or a Stremio/Nuvio addon. */
sealed class TestTarget {
    abstract val key: String
    abstract val label: String

    data class Extension(val source: AnimeCatalogueSource) : TestTarget() {
        override val key get() = "ext:${source.id}"
        override val label get() = source.name
    }

    data class Addon(val addon: InstalledAddon) : TestTarget() {
        override val key get() = "addon:${addon.id}"
        override val label get() = addon.name
    }
}

/** Outcome of [SourceTester.test], shown under "Test Result". */
data class SourceTestResult(
    val target: String,
    /** Round trip to the site itself (HTTP status line + time), null if it was not reachable. */
    val pingMs: Long?,
    val httpStatus: Int?,
    /** Time the source took to answer a real request (popular list / first catalog). */
    val latencyMs: Long?,
    val itemCount: Int?,
    val sampleTitle: String?,
    val error: String?,
    /** What the streaming check found (an episode's hosters and videos), null when it was not run. */
    val streamNote: String? = null,
) {
    val ok: Boolean get() = error == null
}

/**
 * Pings a source's site and runs real requests through it, so "works in the app" is checked the way the app uses it:
 * the catalogue (popular list) and then streaming (an episode's hosters and videos, what the player asks for).
 */
class SourceTester(
    private val client: OkHttpClient,
    private val addonManager: AddonManager,
) {
    suspend fun test(target: TestTarget): SourceTestResult = withContext(Dispatchers.IO) {
        when (target) {
            is TestTarget.Extension -> testExtension(target.source)
            is TestTarget.Addon -> testAddon(target.addon)
        }
    }

    private fun ping(url: String): Pair<Long?, Int?> {
        val started = System.nanoTime()
        return try {
            val request = Request.Builder().url(url).head().build()
            client.newBuilder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                .newCall(request).execute().use { response ->
                    // Some sites reject HEAD (405/403); the round trip still proves the host answers.
                    (System.nanoTime() - started) / 1_000_000 to response.code
                }
        } catch (e: Exception) {
            null to null
        }
    }

    private suspend fun testExtension(source: AnimeCatalogueSource): SourceTestResult {
        val (pingMs, status) = ping(runCatching { source.baseUrl }.getOrDefault(""))
        val started = System.nanoTime()
        return try {
            val page = withTimeout(REQUEST_TIMEOUT_MS) { source.getPopularAnime(1) }
            val latency = (System.nanoTime() - started) / 1_000_000
            // A source can list its catalogue and still show no video in the player, so streaming is checked too.
            val stream = if (page.animes.isEmpty()) null else checkStreaming(source, page.animes)
            SourceTestResult(
                target = source.name,
                pingMs = pingMs,
                httpStatus = status,
                latencyMs = latency,
                itemCount = page.animes.size,
                sampleTitle = page.animes.firstOrNull()?.title,
                error = when {
                    page.animes.isEmpty() -> "Reached the source but its popular list came back empty"
                    stream != null && stream.videos == 0 -> "The catalogue works but streaming does not: ${stream.note}"
                    else -> null
                },
                streamNote = stream?.note,
            )
        } catch (e: TimeoutCancellationException) {
            failure(source.name, pingMs, status, "Timed out after ${REQUEST_TIMEOUT_MS / 1000}s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // Throwable on purpose: an extension built for another API version raises Errors
            // (NoSuchMethodError, AbstractMethodError...), which `catch (e: Exception)` lets through and crash the app.
            android.util.Log.e("SourceTester", "testExtension failed for ${source.name}", e)
            failure(source.name, pingMs, status, "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}")
        }
    }

    private class StreamCheck(val videos: Int, val note: String)

    /**
     * Asks for the first episode of the first popular titles and lists its videos, the way the player does.
     * Succeeds as soon as one title yields a video; otherwise reports why the last attempt found none.
     */
    private suspend fun checkStreaming(source: AnimeCatalogueSource, titles: List<SAnime>): StreamCheck {
        var reason = "no episodes were listed"
        for (anime in titles.take(STREAM_TITLES)) {
            val episodes = try {
                withTimeout(STREAM_STEP_TIMEOUT_MS) { source.getEpisodeList(anime) }
            } catch (e: TimeoutCancellationException) {
                reason = "the episode list of \"${anime.title}\" timed out"
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("SourceTester", "episode list failed for ${source.name}", e)
                reason = "episode list of \"${anime.title}\": ${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                continue
            }
            val episode = episodes.firstOrNull()
            if (episode == null) {
                reason = "\"${anime.title}\" has no episodes"
                continue
            }
            val listed = java.util.Collections.synchronizedList(mutableListOf<Video>())
            val lines = mutableListOf<String>()
            try {
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    source.streamVideos(episode, { line -> synchronized(lines) { lines += line.trim() } }) { videos ->
                        listed += videos
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("SourceTester", "streaming failed for ${source.name}", e)
                reason = "${e::class.simpleName}: ${e.message ?: "Unknown error"}"
                continue
            }
            if (listed.isEmpty()) {
                // The source's own per-server lines say which hoster failed and why.
                reason = synchronized(lines) { lines.lastOrNull() } ?: "no server returned a video for \"${anime.title}\""
                continue
            }
            // The player resolves the video that is picked (and moves on when one fails), so prove one of the first resolves.
            var playable: Video? = null
            for (video in listed.toList().take(STREAM_RESOLVE_TRIES)) {
                val pending = video.resolve
                val ready = if (pending == null) {
                    video
                } else {
                    try {
                        withTimeoutOrNull(STREAM_RESOLVE_TIMEOUT_MS) { pending() }
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        android.util.Log.e("SourceTester", "resolving '${video.quality}' failed for ${source.name}", e)
                        null
                    }
                }
                if (ready != null && ready.url.isNotBlank()) {
                    playable = ready
                    break
                }
            }
            if (playable != null) {
                return StreamCheck(listed.size, "${listed.size} video(s) for \"${anime.title}\", episode ${episode.episodeNumber}; one resolves to a stream")
            }
            reason = "${listed.size} video(s) were listed for \"${anime.title}\" but none of the first $STREAM_RESOLVE_TRIES could be resolved"
        }
        return StreamCheck(0, reason)
    }

    private suspend fun testAddon(addon: InstalledAddon): SourceTestResult {
        val (pingMs, status) = ping(addon.manifestUrl)
        val started = System.nanoTime()
        return try {
            val catalog = addon.catalogs.firstOrNull() ?: error("This addon declares no catalogs")
            val metas = withTimeout(REQUEST_TIMEOUT_MS) { addonManager.getCatalog(addon, catalog) }
            val latency = (System.nanoTime() - started) / 1_000_000
            SourceTestResult(
                target = addon.name,
                pingMs = pingMs,
                httpStatus = status,
                latencyMs = latency,
                itemCount = metas.size,
                sampleTitle = metas.firstOrNull()?.name,
                error = if (metas.isEmpty()) "Reached the addon but catalog \"${catalog.id}\" came back empty" else null,
            )
        } catch (e: TimeoutCancellationException) {
            failure(addon.name, pingMs, status, "Timed out after ${REQUEST_TIMEOUT_MS / 1000}s")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            android.util.Log.e("SourceTester", "testAddon failed for ${addon.name}", e)
            failure(addon.name, pingMs, status, "${e::class.java.simpleName}: ${e.message ?: "Unknown error"}")
        }
    }

    private fun failure(name: String, pingMs: Long?, status: Int?, message: String) = SourceTestResult(
        target = name,
        pingMs = pingMs,
        httpStatus = status,
        latencyMs = null,
        itemCount = null,
        sampleTitle = null,
        error = message,
    )

    private companion object {
        const val REQUEST_TIMEOUT_MS = 20_000L
        const val STREAM_STEP_TIMEOUT_MS = 20_000L
        const val STREAM_TIMEOUT_MS = 30_000L
        const val STREAM_RESOLVE_TIMEOUT_MS = 15_000L
        const val STREAM_RESOLVE_TRIES = 2
        const val STREAM_TITLES = 2
    }
}
