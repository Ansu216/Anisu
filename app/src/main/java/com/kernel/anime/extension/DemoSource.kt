package com.kernel.anime.extension

import com.kernel.anime.core.model.AnimeFilterList
import com.kernel.anime.core.model.AnimesPage
import com.kernel.anime.core.model.MediaOrigin
import com.kernel.anime.core.model.SAnime
import com.kernel.anime.core.model.SEpisode
import com.kernel.anime.core.model.Video
import com.kernel.anime.extension.api.AnimeCatalogueSource
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
class DemoSource : AnimeCatalogueSource {
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
        return runQuery(gql, mapOf("page" to page, "search" to query))
    }

    override suspend fun getEpisodeList(anime: SAnime): List<SEpisode> {
        // AniList doesn't expose a stream-ready episode list; a real source
        // would scrape or call its own site's API here. We synthesize a
        // short demo run so playback can still be exercised end to end.
        return (1..12).map { ep ->
            SEpisode(
                id = "${anime.id}-ep$ep",
                name = "Episode $ep",
                episodeNumber = ep.toFloat(),
            )
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
        return runQuery(gql, mapOf("page" to page, "sort" to sort))
    }

    private fun runQuery(query: String, variables: Map<String, Any?>): AnimesPage {
        val variablesJson = buildJsonVariables(variables)
        val bodyJson = """{"query": ${json.encodeToString(kotlinx.serialization.serializer<String>(), query)}, "variables": $variablesJson}"""
        val request = Request.Builder()
            .url(baseUrl)
            .post(bodyJson.toRequestBody("application/json".toMediaType()))
            .build()

        client.newCall(request).execute().use { response ->
            val bodyText = response.body?.string().orEmpty()
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
        val title = this["title"]?.jsonObject?.get("romaji")?.jsonPrimitive?.content
            ?: this["title"]?.jsonObject?.get("english")?.jsonPrimitive?.content
            ?: return null
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
