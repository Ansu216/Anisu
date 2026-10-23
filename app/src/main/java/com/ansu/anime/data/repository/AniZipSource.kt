package com.ansu.anime.data.repository

import com.ansu.anime.core.net.httpApiException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * The one place the app asks ani.zip (api.ani.zip/mappings) about a show. Artwork (logo, backdrop, TMDB
 * id) and episode details (titles, synopses, thumbnails) both come out of the same answer, and used to
 * ask for it separately: opening one title cost two identical requests.
 *
 * It returns the raw answer as text and each caller reads the part it needs. An answer is kept in memory
 * for [TTL_MS] (the last [MAX_ENTRIES] shows) and callers asking for the same show at the same moment
 * share one request. "No mapping" (HTTP 404) is a real answer and is kept as blank text; a failure is
 * thrown and never kept.
 */
class AniZipSource(client: OkHttpClient) {

    // Short timeouts: this data is optional, so a slow host must never hold a page up for a minute.
    private val http: OkHttpClient = client.newBuilder()
        .connectTimeout(6, TimeUnit.SECONDS)
        .callTimeout(15, TimeUnit.SECONDS)
        .build()

    // Own scope, so a request that was started keeps going when the screen that asked leaves, and the
    // next screen (Home -> details) joins it instead of starting a second one.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val inFlight = ConcurrentHashMap<Int, Deferred<String>>()

    private class Entry(val text: String, val at: Long)

    private val cache = object : LinkedHashMap<Int, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Int, Entry>?): Boolean = size > MAX_ENTRIES
    }

    /** Forgets every kept answer, so the next page asks again. */
    fun clear() {
        synchronized(cache) { cache.clear() }
    }

    /** ani.zip's answer for [anilistId] as JSON text; blank when ani.zip has no mapping. Throws on any other failure. */
    suspend fun get(anilistId: Int): String {
        synchronized(cache) { cache[anilistId] }
            ?.takeIf { System.currentTimeMillis() - it.at <= TTL_MS }
            ?.let { return it.text }
        val job = inFlight.computeIfAbsent(anilistId) { id ->
            scope.async(start = CoroutineStart.LAZY) {
                try {
                    fetch(id).also { text ->
                        synchronized(cache) { cache[id] = Entry(text, System.currentTimeMillis()) }
                    }
                } finally {
                    inFlight.remove(id)
                }
            }
        }
        job.start()
        return job.await()
    }

    private suspend fun fetch(anilistId: Int): String = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.ani.zip/mappings?anilist_id=$anilistId").build()
        http.newCall(request).execute().use { response ->
            // ani.zip answers 404 for shows it has no mapping for; that is "no data", not a failure.
            if (response.code == 404) return@use ""
            if (!response.isSuccessful) throw httpApiException(response.code, response.header("Retry-After")?.toLongOrNull(), null)
            response.body.string()
        }
    }

    private companion object {
        const val TTL_MS = 5 * 60_000L
        const val MAX_ENTRIES = 12
    }
}
