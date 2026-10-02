package com.ansu.anime.extension

import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.model.InstalledAddon
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
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
) {
    val ok: Boolean get() = error == null
}

/** Pings a source's site and runs one real request through it, so "works in the app" is checked the way the app uses it. */
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
            SourceTestResult(
                target = source.name,
                pingMs = pingMs,
                httpStatus = status,
                latencyMs = latency,
                itemCount = page.animes.size,
                sampleTitle = page.animes.firstOrNull()?.title,
                error = if (page.animes.isEmpty()) "Reached the source but its popular list came back empty" else null,
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
    }
}
