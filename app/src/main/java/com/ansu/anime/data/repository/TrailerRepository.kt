package com.ansu.anime.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap

/** A trailer / promo video hosted on YouTube. */
data class Trailer(val youtubeId: String, val title: String) {
    /** YouTube's own still for the video; no API key needed. */
    val thumbnailUrl: String get() = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$youtubeId"
}

/**
 * Trailers and promo videos (PVs, teasers) for a show, from Jikan (api.jikan.moe, the free MyAnimeList mirror).
 * Keyed by MyAnimeList id; there is no API key. Playback itself is YouTube's embedded player.
 */
class TrailerRepository(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<Int, List<Trailer>>()

    /** The show's promo videos, or an empty list when Jikan has none or cannot be reached (not cached then). */
    suspend fun getTrailers(malId: Int): List<Trailer> {
        cache[malId]?.let { return it }
        return try {
            val trailers = fetch(malId)
            cache[malId] = trailers
            trailers
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun fetch(malId: Int): List<Trailer> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.jikan.moe/v4/anime/$malId/videos").build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) return@use emptyList()
            if (!response.isSuccessful) error("Jikan HTTP ${response.code}")
            val root = json.parseToJsonElement(response.body?.string().orEmpty()) as? JsonObject ?: return@use emptyList()
            val promos = root["data"]?.jsonObject?.get("promo")?.jsonArray ?: return@use emptyList()
            promos.mapNotNull { element ->
                val obj = element.jsonObject
                val id = (obj["trailer"] as? JsonObject)?.get("youtube_id")?.jsonPrimitive?.contentOrNull
                    ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                Trailer(id, obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: "Trailer")
            }.distinctBy { it.youtubeId }
        }
    }
}
