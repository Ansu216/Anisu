package com.ansu.anime.data.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * How many nightly builds have been downloaded.
 *
 * GitHub counts a download only for **release assets**: an APK that lives on the `apk-nightly`
 * branch, as every nightly does, has no counter at all. The number therefore has to be counted by
 * the app itself — a nightly download adds one to a free public counter and the About screen reads
 * the total back. It counts the downloads Anisu makes, which is what the nightly channel exists for:
 * somebody installing a nightly from inside the app.
 *
 * Best-effort on purpose, in both directions: a counter that cannot be reached shows as `—` in
 * About instead of failing anything, and a download that cannot be recorded still installs. The
 * counter carries no identifier whatsoever — one anonymous number, incremented by one.
 */
class NightlyDownloads(private val client: OkHttpClient) {

    private var cached: Long? = null

    /** The total, or `null` when the counter could not be read. Asked once per app run. */
    suspend fun total(): Long? {
        cached?.let { return it }
        return withContext(Dispatchers.IO) {
            runCatching { read(COUNT_URL) }.getOrNull()?.also { cached = it }
        }
    }

    /** Adds one download to the counter. Silent: counting must never hold up an install. */
    suspend fun recordDownload() {
        withContext(Dispatchers.IO) {
            runCatching { read(HIT_URL) }.getOrNull()?.let { cached = it }
        }
    }

    private fun read(url: String): Long {
        val request = Request.Builder()
            .url(url)
            // The service answers 403 without a User-Agent, and the name says who is counting.
            .header("User-Agent", USER_AGENT)
            .build()
        client.newCall(request).execute().use { response ->
            // A counter nobody has incremented yet answers 404; that is a real zero, not a failure.
            if (response.code == 404) return 0
            if (!response.isSuccessful) error("Counter returned HTTP ${response.code}")
            return json.decodeFromString<CounterResponse>(response.body.string()).value
        }
    }

    companion object {
        private const val BASE_URL = "https://abacus.jasoncameron.dev"
        private const val NAMESPACE = "ansu-anime"
        private const val KEY = "nightly-apk"
        private const val COUNT_URL = "$BASE_URL/get/$NAMESPACE/$KEY"
        private const val HIT_URL = "$BASE_URL/hit/$NAMESPACE/$KEY"
        private const val USER_AGENT = "Ansu-Android"

        private val json = Json { ignoreUnknownKeys = true }
    }
}

@Serializable
private data class CounterResponse(val value: Long = 0)
