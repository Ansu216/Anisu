package com.ansu.anime.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

enum class SkipType(val buttonLabel: String) {
    INTRO("Skip intro"),
    OUTRO("Skip outro"),
    RECAP("Skip recap"),
}

/** A stretch of an episode (intro, outro, recap) the viewer can jump over. */
data class SkipSegment(val type: SkipType, val startMs: Long, val endMs: Long)

/**
 * Free, community-maintained intro/outro timestamps from AniSkip (api.aniskip.com). Keyed by MyAnimeList id
 * and episode number; there is no API key. Titles nobody has timed simply return nothing.
 */
class AniSkipRepository(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, List<SkipSegment>>()

    /** Segments for one episode, or an empty list when AniSkip has none or cannot be reached. */
    suspend fun getSkipTimes(malId: Int, episodeNumber: Int, episodeLengthSeconds: Double = 0.0): List<SkipSegment> {
        val key = "$malId:$episodeNumber"
        cache[key]?.let { return it }
        return try {
            val segments = fetch(malId, episodeNumber, episodeLengthSeconds)
            cache[key] = segments
            segments
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Network trouble is not cached, so the next episode load tries again.
            emptyList()
        }
    }

    private suspend fun fetch(malId: Int, episode: Int, lengthSeconds: Double): List<SkipSegment> = withContext(Dispatchers.IO) {
        val url = "https://api.aniskip.com/v2/skip-times/$malId/$episode".toHttpUrl().newBuilder()
            .addQueryParameter("types", "op")
            .addQueryParameter("types", "ed")
            .addQueryParameter("types", "mixed-op")
            .addQueryParameter("types", "mixed-ed")
            .addQueryParameter("types", "recap")
            .addQueryParameter("episodeLength", lengthSeconds.coerceAtLeast(0.0).toString())
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().use { response ->
            // 404 is how AniSkip says "nobody has timed this episode".
            if (response.code == 404) return@use emptyList()
            if (!response.isSuccessful) error("AniSkip HTTP ${response.code}")
            val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject ?: return@use emptyList()
            val results = root["results"]?.jsonArray ?: return@use emptyList()
            results.mapNotNull { element ->
                val obj = element.jsonObject
                val type = when (obj["skipType"]?.jsonPrimitive?.contentOrNull) {
                    "op", "mixed-op" -> SkipType.INTRO
                    "ed", "mixed-ed" -> SkipType.OUTRO
                    "recap" -> SkipType.RECAP
                    else -> return@mapNotNull null
                }
                val interval = obj["interval"]?.jsonObject ?: return@mapNotNull null
                val start = interval["startTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                val end = interval["endTime"]?.jsonPrimitive?.doubleOrNull ?: return@mapNotNull null
                if (end <= start) return@mapNotNull null
                SkipSegment(type, (start * 1000).toLong(), (end * 1000).toLong())
            }.sortedBy { it.startMs }
        }
    }
}
