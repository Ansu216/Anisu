package com.ansu.anime.extension

import com.ansu.anime.core.model.AnimeFilterList
import com.ansu.anime.core.model.AnimesPage
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.Video
import com.ansu.anime.data.prefs.TitleLanguage
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * A source built into the app itself (rather than loaded from a separate
 * APK) so the home screen has real anime metadata to show out of the box.
 * It only pulls public metadata from AniList's GraphQL API and points
 * playback at a public HLS test stream — it is not a piracy source, and is
 * meant as a working reference for how a real extension implements
 * [AnimeCatalogueSource].
 */
class DemoSource(
    /** Read per response so the title language chosen in Settings applies to the next request. */
    private val titleLanguage: () -> TitleLanguage = { TitleLanguage.ROMAJI },
) : AnimeCatalogueSource {
    override val id: Long = 1L
    override val name: String = "Demo (AniList metadata)"
    override val lang: String = "en"
    override val baseUrl: String = "https://graphql.anilist.co"
    override val supportsLatest: Boolean = true

    private val client = OkHttpClient()
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun getPopularAnime(page: Int): AnimesPage = fetchPage(page, "TRENDING_DESC")
    override suspend fun getLatestUpdates(page: Int): AnimesPage = fetchPage(page, "START_DATE_DESC")

    override suspend fun getSearchAnime(page: Int, query: String, filters: AnimeFilterList): AnimesPage {
        val gql = """
            query (${'$'}page: Int, ${'$'}search: String) {
              Page(page: ${'$'}page, perPage: 20) {
                pageInfo { hasNextPage }
                media(type: ANIME, search: ${'$'}search) { ...fields }
              }
            }
            $ANIME_FIELDS
        """.trimIndent()
        return withContext(Dispatchers.IO) { runQuery(gql, mapOf("page" to page, "search" to query)) }
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // AniList doesn't expose a stream-ready episode list; a real source
        // would scrape or call its own site's API here. We synthesize one
        // entry per real episode (count looked up from AniList) so playback
        // can still be exercised end to end for the whole show.
        val count = fetchEpisodeCount(anime.anilistId ?: anime.id.toIntOrNull()) ?: FALLBACK_EPISODE_COUNT
        return (1..count).map { ep ->
            SEpisode(
                id = "${anime.id}-ep$ep",
                name = "Episode $ep",
                episodeNumber = ep.toFloat(),
            )
        }
    }

    /**
     * Total episodes AniList knows about: the final count for finished shows,
     * or the number already aired (next airing episode - 1) for shows that are
     * still running and have no fixed total. Null when it can't be determined.
     */
    private suspend fun fetchEpisodeCount(anilistId: Int?): Int? {
        if (anilistId == null) return null
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) { episodes nextAiringEpisode { episode } }
            }
        """.trimIndent()
        return withContext(Dispatchers.IO) {
            runCatching {
                val bodyJson = """{"query": ${json.encodeToString(kotlinx.serialization.serializer<String>(), gql)}, "variables": ${buildJsonVariables(mapOf("id" to anilistId))}}"""
                val request = Request.Builder()
                    .url(baseUrl)
                    .post(bodyJson.toRequestBody("application/json".toMediaType()))
                    .build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    val media = json.parseToJsonElement(response.body.string())
                        .jsonObject["data"]?.jsonObject?.get("Media")?.jsonObject ?: return@use null
                    val total = media["episodes"]?.jsonPrimitive?.content?.toIntOrNull()
                    val aired = media["nextAiringEpisode"]?.takeIf { it !is kotlinx.serialization.json.JsonNull }
                        ?.jsonObject?.get("episode")?.jsonPrimitive?.content?.toIntOrNull()?.minus(1)
                    // A running show's total can be a planned figure; never list more than has aired.
                    (if (aired != null && aired > 0) aired else total)?.takeIf { it > 0 }
                }
            }.getOrNull()
        }
    }

    override suspend fun getVideoList(episode: SEpisode): List<Video> {
        return listOf(
            Video(
                url = "https://devstreaming-cdn.apple.com/videos/streaming/examples/bipbop_16x9/bipbop_16x9_variant.m3u8",
                quality = "Auto (demo stream)",
                sourceLabel = name,
            ),
        )
    }

    private suspend fun fetchPage(page: Int, sort: String): AnimesPage {
        val gql = """
            query (${'$'}page: Int, ${'$'}sort: [MediaSort]) {
              Page(page: ${'$'}page, perPage: 20) {
                pageInfo { hasNextPage }
                media(type: ANIME, sort: ${'$'}sort) { ...fields }
              }
            }
            $ANIME_FIELDS
        """.trimIndent()
        return withContext(Dispatchers.IO) { runQuery(gql, mapOf("page" to page, "sort" to sort)) }
    }

    private fun runQuery(query: String, variables: Map<String, Any?>): AnimesPage {
        val variablesJson = buildJsonVariables(variables)
        val bodyJson = """{"query": ${json.encodeToString(kotlinx.serialization.serializer<String>(), query)}, "variables": $variablesJson}"""
        val request = Request.Builder()
            .url(baseUrl)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body.string()
            if (!response.isSuccessful) return AnimesPage(emptyList(), false)
            val root = json.parseToJsonElement(bodyText).jsonObject
            val page = root["data"]?.jsonObject?.get("Page")?.jsonObject ?: return AnimesPage(emptyList(), false)
            val hasNext = page["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content == "true"
            val mediaList = page["media"]?.jsonArray.orEmpty()
            val animes = mediaList.mapNotNull { it.jsonObject.toSAnime() }
            return AnimesPage(animes, hasNext)
        }
    }

    private fun JsonObject.toSAnime(): SAnime? {
        val idValue = this["id"]?.jsonPrimitive?.longOrNull ?: return null
        val titleObj = this["title"]?.jsonObject
        val romaji = titleObj?.get("romaji")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val english = titleObj?.get("english")?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
        val title = when (titleLanguage()) {
            TitleLanguage.ENGLISH -> english ?: romaji
            TitleLanguage.ROMAJI -> romaji ?: english
        } ?: return null
        return SAnime(
            id = idValue.toString(),
            title = title,
            posterUrl = this["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content,
            bannerUrl = this["bannerImage"]?.jsonPrimitive?.content,
            description = this["description"]?.jsonPrimitive?.content,
            genres = this["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            releaseYear = this["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content?.toIntOrNull(),
            rating = this["averageScore"]?.jsonPrimitive?.content?.toDoubleOrNull()?.div(10.0),
            anilistId = idValue.toInt(),
            origin = MediaOrigin.Extension(sourceId = id, urlPath = idValue.toString()),
        )
    }

    private fun buildJsonVariables(variables: Map<String, Any?>): String {
        val parts = variables.entries.joinToString(",") { (key, value) ->
            val jsonValue = when (value) {
                is Int -> value.toString()
                is Long -> value.toString()
                is String -> json.encodeToString(kotlinx.serialization.serializer<String>(), value)
                null -> "null"
                else -> json.encodeToString(kotlinx.serialization.serializer<String>(), value.toString())
            }
            "\"$key\": $jsonValue"
        }
        return "{$parts}"
    }

    companion object {
        /** Used only when AniList can't tell us how many episodes a show has. */
        private const val FALLBACK_EPISODE_COUNT = 12

        private const val ANIME_FIELDS = """
            fragment fields on Media {
              id
              title { romaji english }
              coverImage { extraLarge }
              bannerImage
              description(asHtml: false)
              genres
              averageScore
              startDate { year }
            }
        """
    }
}
