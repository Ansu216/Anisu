package com.ansu.anime.extension

import com.ansu.anime.core.diagnostics.friendlyPlaybackStep
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

/** One thing an installed source can be tested as: an extension source. */
sealed class TestTarget {
    abstract val key: String
    abstract val label: String

    data class Extension(val source: AnimeCatalogueSource, val version: String? = null) : TestTarget() {
        override val key get() = "ext:${source.id}"

        /** "Anikoto · EN · v16.11": the same source name can come from several extensions, so language and version tell them apart. */
        override val label get() = buildList {
            add(source.name)
            source.lang.takeIf { it.isNotBlank() }?.let { add(it.uppercase()) }
            version?.takeIf { it.isNotBlank() }?.let { add("v$it") }
        }.joinToString(" · ")
    }
}

/** What a quick look at a source's website found, shown live on its card in the Extensions list. */
sealed class SiteStatus(val label: String, val good: Boolean) {
    /** The site answered; a refusal such as 403 still proves the host is up. */
    class Online(ms: Long) : SiteStatus("Site online · $ms ms", true)

    /** The site answered with a server error (Cloudflare's 530 means it cannot reach the site's own server). */
    class Down(code: Int) : SiteStatus("Site is down (HTTP $code)", false)

    /** The site's address does not resolve: the domain is gone, or this network's DNS blocks it. */
    data object NotFound : SiteStatus("Can't find the site: its address doesn't exist, or your DNS blocks it", false)

    /** Connecting failed or timed out. */
    data object Unreachable : SiteStatus("The site isn't answering", false)
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
    private val health: SourceHealth? = null,
) {
    /** Quick look at a source's website, without running anything through the extension. Null when it has no address. */
    suspend fun checkSite(url: String): SiteStatus? = withContext(Dispatchers.IO) {
        if (url.isBlank()) return@withContext null
        val started = System.nanoTime()
        try {
            val request = Request.Builder().url(url).head().build()
            client.newBuilder().callTimeout(10, java.util.concurrent.TimeUnit.SECONDS).build()
                .newCall(request).execute().use { response ->
                    if (response.code in 500..599) SiteStatus.Down(response.code) else SiteStatus.Online((System.nanoTime() - started) / 1_000_000)
                }
        } catch (e: CancellationException) {
            throw e
        } catch (e: java.net.UnknownHostException) {
            SiteStatus.NotFound
        } catch (e: Exception) {
            SiteStatus.Unreachable
        }
    }

    /** [onProgress] is told each step as it starts ("Loading the catalogue…"), from a background thread. */
    suspend fun test(target: TestTarget, onProgress: (String) -> Unit = {}): SourceTestResult = withContext(Dispatchers.IO) {
        when (target) {
            is TestTarget.Extension -> testExtension(target.source, onProgress).copy(target = target.label).also {
                health?.record(target.source.id, it.ok, it.error)
            }
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

    private suspend fun testExtension(source: AnimeCatalogueSource, onProgress: (String) -> Unit): SourceTestResult {
        onProgress("Checking the site…")
        val (pingMs, status) = ping(runCatching { source.baseUrl }.getOrDefault(""))
        onProgress(if (pingMs != null) "The site answered in $pingMs ms" else "The site didn't answer")
        onProgress("Loading the catalogue…")
        val started = System.nanoTime()
        return try {
            val page = withTimeout(REQUEST_TIMEOUT_MS) { source.getPopularAnime(1) }
            val latency = (System.nanoTime() - started) / 1_000_000
            onProgress("Catalogue loaded: ${page.animes.size} titles")
            // A source can list its catalogue and still show no video in the player, so streaming is checked too.
            val stream = if (page.animes.isEmpty()) null else checkStreaming(source, page.animes, onProgress)
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
    private suspend fun checkStreaming(source: AnimeCatalogueSource, titles: List<SAnime>, onProgress: (String) -> Unit): StreamCheck {
        var reason = "no episodes were listed"
        // Each title's failure is kept: only the last one used to be shown, which hid why the first title failed.
        val reasons = mutableListOf<String>()
        for (anime in titles.take(STREAM_TITLES)) {
            if (reasons.isNotEmpty() || reason != "no episodes were listed") reasons += reason
            onProgress("Listing the episodes of ${anime.title}…")
            val episodes = try {
                withTimeout(STREAM_STEP_TIMEOUT_MS) { source.getEpisodeList(anime) }
            } catch (e: TimeoutCancellationException) {
                reason = "the episode list of \"${anime.title}\" timed out"
                continue
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("SourceTester", "episode list failed for ${source.name}", e)
                reason = "episode list of \"${anime.title}\": ${e::class.simpleName}: ${e.message ?: "Unknown error"}" +
                    ((e as? eu.kanade.tachiyomi.network.HttpException)?.let { " (the site itself refused the request, not the app)" } ?: "")
                continue
            }
            val episode = episodes.firstOrNull()
            if (episode == null) {
                reason = "\"${anime.title}\" has no episodes"
                continue
            }
            val listed = java.util.Collections.synchronizedList(mutableListOf<Video>())
            val lines = mutableListOf<String>()
            onProgress("Asking for servers…")
            try {
                withTimeoutOrNull(STREAM_TIMEOUT_MS) {
                    source.streamVideos(episode, { line ->
                        synchronized(lines) { lines += line.trim() }
                        friendlyPlaybackStep(line)?.let(onProgress)
                    }) { videos ->
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
                reason = synchronized(lines) {
                    // Skip the "N hoster(s)" summary line and keep the last two, which carry the actual cause.
                    lines.filterNot { it.contains("hoster(s):") }.takeLast(2).joinToString(" | ").ifBlank { null }
                } ?: "no server returned a video for \"${anime.title}\""
                continue
            }
            // The player resolves the video that is picked (and moves on when one fails), so prove one of the first resolves.
            var playable: Video? = null
            onProgress("Resolving a stream…")
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
        reasons += reason
        return StreamCheck(0, reasons.filter { it != "no episodes were listed" }.distinct().joinToString("  ||  ").ifBlank { reason })
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
        const val STREAM_STEP_TIMEOUT_MS = 45_000L
        const val STREAM_TIMEOUT_MS = 30_000L
        const val STREAM_RESOLVE_TIMEOUT_MS = 15_000L
        const val STREAM_RESOLVE_TRIES = 2
        const val STREAM_TITLES = 2
    }
}
