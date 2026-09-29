package com.ansu.anime.data.repository

import com.ansu.anime.anilist.AniListRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.OkHttpClient
import okhttp3.Request

/** Display details for one episode of a show, independent of which source plays it. */
data class EpisodeMeta(
    val title: String?,
    val overview: String?,
    val thumbnailUrl: String?,
    val airDate: String? = null,
)

/**
 * Looks up real episode titles, synopses and thumbnails by AniList id, so the
 * details page has them even when the playback source (e.g. the demo source)
 * only knows episode numbers. Uses the community ani.zip mapping service for
 * titles + synopses + thumbnails, and AniList's own `streamingEpisodes` to fill
 * any gaps. Both are best-effort: on any failure the map is simply empty.
 */
class EpisodeMetadataRepository(
    private val client: OkHttpClient,
    private val aniList: AniListRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }

    /** Episode number -> details. Empty when nothing could be fetched. */
    suspend fun getEpisodeMeta(anilistId: Int): Map<Int, EpisodeMeta> {
        val primary = runCatching { fetchAniZip(anilistId) }.getOrDefault(emptyMap())
        val fallback = aniList.getStreamingEpisodes(anilistId).let { list ->
            val byNumber = mutableMapOf<Int, EpisodeMeta>()
            list.forEachIndexed { index, entry ->
                val match = STREAMING_TITLE.matchEntire(entry.title.trim())
                val number = match?.groupValues?.get(1)?.toIntOrNull() ?: (index + 1)
                val title = match?.groupValues?.get(2)?.takeIf { it.isNotBlank() } ?: entry.title
                byNumber.putIfAbsent(number, EpisodeMeta(title = title, overview = null, thumbnailUrl = entry.thumbnailUrl))
            }
            byNumber
        }
        return (primary.keys + fallback.keys).associateWith { number ->
            val a = primary[number]
            val b = fallback[number]
            EpisodeMeta(
                title = a?.title ?: b?.title,
                overview = a?.overview ?: b?.overview,
                thumbnailUrl = a?.thumbnailUrl ?: b?.thumbnailUrl,
                airDate = a?.airDate ?: b?.airDate,
            )
        }
    }

    private suspend fun fetchAniZip(anilistId: Int): Map<Int, EpisodeMeta> = withContext(Dispatchers.IO) {
        val request = Request.Builder().url("https://api.ani.zip/mappings?anilist_id=$anilistId").build()
        val text = client.newCall(request).execute().use { response ->
            if (response.isSuccessful) response.body?.string().orEmpty() else ""
        }
        if (text.isBlank()) return@withContext emptyMap()
        val episodes = json.parseToJsonElement(text).jsonObject["episodes"] as? JsonObject
            ?: return@withContext emptyMap()
        val result = mutableMapOf<Int, EpisodeMeta>()
        for ((key, value) in episodes) {
            val number = key.toIntOrNull() ?: continue // skips specials ("S1", "S2", ...)
            val obj = value as? JsonObject ?: continue
            result[number] = EpisodeMeta(
                title = obj["title"].localizedText(),
                overview = obj["overview"].asText()?.replace(HTML_TAG, "")?.trim()?.takeIf { it.isNotEmpty() },
                thumbnailUrl = obj["image"].asText(),
                airDate = (obj["airdate"].asText() ?: obj["airDate"].asText() ?: obj["airDateUtc"].asText())?.take(10),
            )
        }
        result
    }

    private fun JsonElement?.asText(): String? =
        (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    /** ani.zip gives titles as `{"en": …, "x-jat": …, "ja": …}`; take English, then romaji, then anything. */
    private fun JsonElement?.localizedText(): String? = when (this) {
        is JsonObject -> this["en"].asText() ?: this["x-jat"].asText() ?: values.firstNotNullOfOrNull { it.asText() }
        else -> asText()
    }

    private companion object {
        val HTML_TAG = Regex("<[^>]*>")
        val STREAMING_TITLE = Regex("""Episode\s+(\d+)\s*[-:–]?\s*(.*)""", RegexOption.IGNORE_CASE)
    }
}
