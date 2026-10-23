package com.ansu.anime.data.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A video address that the app's own player can play, taken from YouTube.
 * [userAgent] is the one the address was handed out to; googlevideo answers the same address with 403 for another.
 */
sealed interface TrailerStream {
    val userAgent: String

    /** An adaptive HLS playlist (several qualities, audio included). */
    data class Hls(val url: String, override val userAgent: String) : TrailerStream

    /** One file with picture and sound together (YouTube's 360p / 720p mp4). */
    data class Progressive(val url: String, override val userAgent: String, val height: Int) : TrailerStream

    /** A picture-only file and a sound-only file that are played together (the 1080p case). */
    data class Merged(val videoUrl: String, val audioUrl: String, override val userAgent: String) : TrailerStream
}

/**
 * Turns a YouTube video id into a stream the in-app player can open, without YouTube's embedded player (which many
 * videos refuse to run in). It asks YouTube's own player endpoint ("InnerTube") the way YouTube's apps do, as each
 * of a few app identities in turn: the first one that answers with playable video wins. Those identities change from
 * time to time on YouTube's side, so this is the one place to adjust when a trailer stops resolving; the trailer
 * sheet falls back to the embedded player when nothing resolves.
 */
class YouTubeStreamResolver(private val client: OkHttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /** The best stream for [videoId], or null when no identity got a playable answer. */
    suspend fun resolve(videoId: String): TrailerStream? = resolveAll(videoId).firstOrNull()

    /**
     * Every stream the identities offered, most reliable first (HLS, which is not throttled and needs no ranged
     * requests, then single files, then merged picture + sound). The player tries them in turn, so one stream
     * that stalls or is refused no longer ends in a blank video.
     */
    suspend fun resolveAll(videoId: String): List<TrailerStream> = withContext(Dispatchers.IO) {
        val found = mutableListOf<TrailerStream>()
        for (identity in IDENTITIES) {
            try {
                request(identity, videoId)?.let { found += it }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                // Try the next identity.
            }
        }
        found.sortedBy {
            when (it) {
                is TrailerStream.Hls -> 0
                is TrailerStream.Progressive -> 1
                is TrailerStream.Merged -> 2
            }
        }
    }

    private fun request(identity: Identity, videoId: String): TrailerStream? {
        val body = buildJsonObject {
            put("videoId", videoId)
            put("contentCheckOk", true)
            put("racyCheckOk", true)
            put(
                "context",
                buildJsonObject {
                    put("client", identity.clientJson())
                },
            )
        }.toString().toRequestBody("application/json".toMediaType())
        val request = Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/player?prettyPrint=false")
            .post(body)
            .header("User-Agent", identity.userAgent)
            .header("X-YouTube-Client-Name", identity.headerId.toString())
            .header("X-YouTube-Client-Version", identity.version)
            .header("Origin", "https://www.youtube.com")
            .build()
        val text = client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return null
            response.body?.string().orEmpty()
        }
        val root = json.parseToJsonElement(text) as? JsonObject ?: return null
        if (root["playabilityStatus"]?.jsonObject?.get("status")?.jsonPrimitive?.contentOrNull != "OK") return null
        val streaming = root["streamingData"] as? JsonObject ?: return null
        return pick(streaming, identity.userAgent)
    }

    /** HLS first (adaptive, sound included), then the best single file with sound, then picture + sound files joined. */
    private fun pick(streaming: JsonObject, userAgent: String): TrailerStream? {
        streaming["hlsManifestUrl"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }?.let {
            return TrailerStream.Hls(it, userAgent)
        }
        val muxed = formats(streaming["formats"]).filter { it.mime.startsWith("video/mp4") }
        muxed.maxByOrNull { it.height }?.let { return TrailerStream.Progressive(it.url, userAgent, it.height) }

        val adaptive = formats(streaming["adaptiveFormats"])
        val video = adaptive.filter { it.mime.startsWith("video/mp4") && it.height in 1..1080 }.maxByOrNull { it.height }
        val audio = adaptive.filter { it.mime.startsWith("audio/mp4") }.maxByOrNull { it.bitrate }
        if (video != null && audio != null) return TrailerStream.Merged(video.url, audio.url, userAgent)
        return null
    }

    private class Format(val url: String, val mime: String, val height: Int, val bitrate: Int)

    /** The formats that carry a plain address; the ones behind a signature cipher cannot be played and are skipped. */
    private fun formats(element: JsonElement?): List<Format> =
        (element as? JsonArray).orEmpty().mapNotNull { item ->
            val obj = item as? JsonObject ?: return@mapNotNull null
            val url = obj["url"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            Format(
                url = url,
                mime = obj["mimeType"]?.jsonPrimitive?.contentOrNull.orEmpty(),
                height = obj["height"]?.jsonPrimitive?.intOrNull ?: 0,
                bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 0,
            )
        }

    /** One app identity to ask YouTube as. */
    private class Identity(
        val name: String,
        val version: String,
        val headerId: Int,
        val userAgent: String,
        val extra: Map<String, Any>,
    ) {
        fun clientJson(): JsonObject = buildJsonObject {
            put("clientName", name)
            put("clientVersion", version)
            put("hl", "en")
            put("gl", "US")
            extra.forEach { (key, value) ->
                when (value) {
                    is Int -> put(key, value)
                    else -> put(key, JsonPrimitive(value.toString()))
                }
            }
        }
    }

    private companion object {
        /** In order of preference: the VR client needs no extra token, iOS also offers an HLS playlist, Android is the last resort. */
        val IDENTITIES = listOf(
            Identity(
                name = "ANDROID_VR",
                version = "1.60.19",
                headerId = 28,
                userAgent = "com.google.android.apps.youtube.vr.oculus/1.60.19 (Linux; U; Android 12L; eureka-user Build/SQ3A.220605.009.A1) gzip",
                extra = mapOf("deviceMake" to "Oculus", "deviceModel" to "Quest 3", "androidSdkVersion" to 32, "osName" to "Android", "osVersion" to "12L"),
            ),
            Identity(
                name = "IOS",
                version = "20.10.4",
                headerId = 5,
                userAgent = "com.google.ios.youtube/20.10.4 (iPhone16,2; U; CPU iOS 18_3_2 like Mac OS X;)",
                extra = mapOf("deviceMake" to "Apple", "deviceModel" to "iPhone16,2", "osName" to "iPhone", "osVersion" to "18.3.2.22D82"),
            ),
            Identity(
                name = "ANDROID",
                version = "20.10.38",
                headerId = 3,
                userAgent = "com.google.android.youtube/20.10.38 (Linux; U; Android 11) gzip",
                extra = mapOf("androidSdkVersion" to 30, "osName" to "Android", "osVersion" to "11"),
            ),
        )
    }
}
