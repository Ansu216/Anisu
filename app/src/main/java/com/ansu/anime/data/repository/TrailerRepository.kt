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
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.ConcurrentHashMap

/** A trailer / promo video hosted on YouTube. */
data class Trailer(val youtubeId: String, val title: String) {
    /** YouTube's own still for the video; no API key needed. */
    val thumbnailUrl: String get() = "https://img.youtube.com/vi/$youtubeId/hqdefault.jpg"
    val watchUrl: String get() = "https://www.youtube.com/watch?v=$youtubeId"
}

/**
 * Trailers and promo videos (PVs, teasers) for a show, from Jikan (api.jikan.moe, the free MyAnimeList mirror).
 * Keyed by MyAnimeList id; there is no API key. Playback is the app's own player on a stream taken from YouTube ([resolveStream]), with YouTube's embedded player as the fallback.
 */
class TrailerRepository(private val client: OkHttpClient) {

    /** The app's HTTP client, shared with the trailer player so it can load the stream. */
    val httpClient: OkHttpClient get() = client

    private val resolver = YouTubeStreamResolver(client)

    /** A stream the in-app player can play for a trailer's YouTube id, or null when YouTube gave none (the sheet then falls back to the embedded player). */
    suspend fun resolveStream(youtubeId: String): TrailerStream? = resolver.resolve(youtubeId)

    /** All playable streams for a trailer, best first; the sheet moves to the next one when a stream fails or stalls. */
    suspend fun resolveStreams(youtubeId: String): List<TrailerStream> = resolver.resolveAll(youtubeId)

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<Int, List<Trailer>>()

    /** Forgets every trailer list, so the next details page asks Jikan again. */
    fun clearCache() {
        cache.clear()
    }

    /**
     * Every trailer worth showing for a show: Jikan's promo list plus a YouTube search for its trailers, PVs, teasers
     * and the sub / dub versions that Jikan leaves out. Duplicates are dropped, Jikan's own entries stay first.
     */
    suspend fun getAllTrailers(malId: Int?, titles: List<String>): List<Trailer> {
        val promos = malId?.let { getTrailers(it) }.orEmpty()
        val found = titles.map { it.trim() }.filter { it.isNotBlank() && it.all { c -> c.code < 0x250 } }.distinct().take(2).flatMap { searchTrailers(it) }
        return (promos + found).distinctBy { it.youtubeId }.take(20)
    }

    private val searchCache = ConcurrentHashMap<String, List<Trailer>>()

    /** YouTube search ("<title> official trailer anime") through the same no-key endpoint YouTube's website uses. */
    private suspend fun searchTrailers(title: String): List<Trailer> {
        searchCache[title]?.let { return it }
        return try {
            val result = withContext(Dispatchers.IO) { searchYouTube("$title official trailer anime", title) }
            if (result.isNotEmpty()) searchCache[title] = result
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun searchYouTube(query: String, title: String): List<Trailer> {
        val body = """{"context":{"client":{"clientName":"WEB","clientVersion":"2.20250101.00.00","hl":"en","gl":"US"}},"query":${json.encodeToString(kotlinx.serialization.serializer<String>(), query)}}"""
            .toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(body)
            .header("Origin", "https://www.youtube.com")
            .build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return emptyList()
            response.body?.string().orEmpty()
        }
        val out = mutableListOf<Trailer>()
        fun walk(element: kotlinx.serialization.json.JsonElement) {
            when (element) {
                is JsonObject -> {
                    val renderer = element["videoRenderer"] as? JsonObject
                    if (renderer != null) {
                        val id = renderer["videoId"]?.jsonPrimitive?.contentOrNull
                        val name = (renderer["title"] as? JsonObject)?.get("runs")?.jsonArray?.firstOrNull()
                            ?.jsonObject?.get("text")?.jsonPrimitive?.contentOrNull
                        val length = (renderer["lengthText"] as? JsonObject)?.get("simpleText")?.jsonPrimitive?.contentOrNull
                        if (id != null && name != null && looksLikeTrailer(name, title) && isShort(length)) out += Trailer(id, name)
                    } else {
                        element.values.forEach(::walk)
                    }
                }
                is kotlinx.serialization.json.JsonArray -> element.forEach(::walk)
                else -> Unit
            }
        }
        walk(json.parseToJsonElement(text))
        return out.take(10)
    }

    /** Names the show (so another anime's trailer is not picked up) and says trailer / PV / teaser / sub / dub. */
    private fun looksLikeTrailer(name: String, title: String): Boolean {
        val lower = name.lowercase()
        val kind = listOf("trailer", " pv", "teaser", "promo", "preview", "dub", "sub", "cm", "visual").any { lower.contains(it) }
        val words = title.lowercase().split(' ', ':', '-').filter { it.length >= 3 }
        val named = words.isEmpty() || words.count { lower.contains(it) } >= minOf(2, words.size)
        return kind && named
    }

    /** A trailer is short; full episodes and compilations (over 6 minutes) are left out. */
    private fun isShort(length: String?): Boolean {
        val parts = length?.split(':')?.mapNotNull { it.toIntOrNull() } ?: return true
        val seconds = parts.fold(0) { acc, n -> acc * 60 + n }
        return seconds in 1..360
    }

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
