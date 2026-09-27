package com.streamhub.data.anilist

/**
 * Minimal AniList (https://anilist.co) GraphQL client for anime metadata:
 * titles, synopsis, cover/banner art, episode counts, genres, scores, etc.
 * Public API — no auth/API key required for these read-only queries.
 * Schema reference: https://anilist.gitbook.io/anilist-apiv2-docs/
 *
 * Gradle dependencies (add if not already present):
 *   implementation("com.squareup.okhttp3:okhttp:4.12.0")
 *   implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
 * Plugin (module-level build.gradle.kts, alongside the Compose plugin):
 *   id("org.jetbrains.kotlin.plugin.serialization")
 */

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

// ---------- Response models ----------

@Serializable
data class AniListTitle(
    val romaji: String? = null,
    val english: String? = null,
    val native: String? = null,
)

@Serializable
data class AniListCoverImage(
    val extraLarge: String? = null,
    val large: String? = null,
    val color: String? = null,
)

@Serializable
data class AniListName(
    val full: String? = null,
)

@Serializable
data class AniListPersonImage(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class AniListCharacterNode(
    val id: Int,
    val name: AniListName = AniListName(),
    val image: AniListPersonImage? = null,
)

@Serializable
data class AniListCharacterEdge(
    val role: String? = null, // "MAIN" | "SUPPORTING" | "BACKGROUND"
    val node: AniListCharacterNode,
)

@Serializable
data class AniListCharacterConnection(
    val edges: List<AniListCharacterEdge> = emptyList(),
)

@Serializable
data class AniListStaffNode(
    val id: Int,
    val name: AniListName = AniListName(),
    val image: AniListPersonImage? = null,
)

@Serializable
data class AniListStaffEdge(
    val role: String? = null, // free-text credit, e.g. "Original Creator", "Director"
    val node: AniListStaffNode,
)

@Serializable
data class AniListStaffConnection(
    val edges: List<AniListStaffEdge> = emptyList(),
)

@Serializable
data class AniListStudioNode(
    val id: Int,
    val name: String,
)

@Serializable
data class AniListStudioConnection(
    val nodes: List<AniListStudioNode> = emptyList(),
)

/** Shared shape for both "relations" and "recommendations" target media. */
@Serializable
data class AniListRelatedNode(
    val id: Int,
    val title: AniListTitle = AniListTitle(),
    val coverImage: AniListCoverImage? = null,
    val format: String? = null,
    val type: String? = null, // "ANIME" | "MANGA"
)

@Serializable
data class AniListRelationEdge(
    val relationType: String? = null, // "SEQUEL" | "PREQUEL" | "SIDE_STORY" | "ADAPTATION" | ...
    val node: AniListRelatedNode,
)

@Serializable
data class AniListRelationConnection(
    val edges: List<AniListRelationEdge> = emptyList(),
)

@Serializable
data class AniListRecommendationNode(
    val mediaRecommendation: AniListRelatedNode? = null,
)

@Serializable
data class AniListRecommendationConnection(
    val nodes: List<AniListRecommendationNode> = emptyList(),
)

@Serializable
data class AniListTrailer(
    val id: String? = null,
    val site: String? = null,      // "youtube" | "dailymotion"
    val thumbnail: String? = null,
)

@Serializable
data class AniListStreamingEpisode(
    val title: String? = null,     // e.g. "Episode 1 - Ryomen Sukuna"
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null,      // e.g. "Crunchyroll"
)

@Serializable
data class AniListMedia(
    val id: Int,
    val title: AniListTitle,
    val description: String? = null,
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val episodes: Int? = null,
    val duration: Int? = null,
    val genres: List<String> = emptyList(),
    val averageScore: Int? = null,
    val status: String? = null,
    val seasonYear: Int? = null,
    val format: String? = null,
    // Populated only by getMediaDetails() — list/search queries omit these to stay light.
    val studios: AniListStudioConnection? = null,
    val trailer: AniListTrailer? = null,
    val streamingEpisodes: List<AniListStreamingEpisode> = emptyList(),
    val characters: AniListCharacterConnection? = null,
    val staff: AniListStaffConnection? = null,
    val relations: AniListRelationConnection? = null,
    val recommendations: AniListRecommendationConnection? = null,
) {
    /** Best available display title, preferring English. */
    val displayTitle: String get() = title.english ?: title.romaji ?: title.native ?: "Untitled"

    /** Synopsis with AniList's HTML line breaks/tags stripped. AniList descriptions
     *  commonly end with a "(Source: ...)" attribution line — left intact on purpose. */
    val plainDescription: String
        get() = description?.replace("<br>", "\n")?.replace(Regex("<.*?>"), "") ?: ""
}

class AniListException(message: String) : IOException(message)

@Serializable
private data class PageData(val media: List<AniListMedia> = emptyList())

@Serializable
private data class ResponseData(
    @SerialName("Page") val page: PageData? = null,
    @SerialName("Media") val media: AniListMedia? = null,
)

@Serializable
private data class GraphQLError(val message: String)

@Serializable
private data class GraphQLEnvelope(
    val data: ResponseData? = null,
    val errors: List<GraphQLError>? = null,
)

// ---------- Client ----------

object AniListApi {
    private const val ENDPOINT = "https://graphql.anilist.co"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

    // Shared field selection reused by every light-weight query below (rows, search, grids).
    private const val MEDIA_FIELDS = """
        id
        title { romaji english native }
        description(asHtml: false)
        coverImage { extraLarge large color }
        bannerImage
        episodes
        duration
        genres
        averageScore
        status
        seasonYear
        format
    """

    // Superset used only by getMediaDetails() — everything the details screen renders:
    // hero, stats, genres, description, episode list, trailer, related/recommended, cast & crew.
    private const val MEDIA_DETAILS_FIELDS = """
        $MEDIA_FIELDS
        studios(isMain: true) {
            nodes { id name }
        }
        trailer { id site thumbnail }
        streamingEpisodes { title thumbnail url site }
        characters(sort: [ROLE, RELEVANCE], perPage: 12) {
            edges {
                role
                node { id name { full } image { large medium } }
            }
        }
        staff(sort: [RELEVANCE], perPage: 12) {
            edges {
                role
                node { id name { full } image { large medium } }
            }
        }
        relations {
            edges {
                relationType(version: 2)
                node {
                    id
                    title { romaji english }
                    coverImage { extraLarge large }
                    format
                    type
                }
            }
        }
        recommendations(sort: RATING_DESC, perPage: 10) {
            nodes {
                mediaRecommendation {
                    id
                    title { romaji english }
                    coverImage { extraLarge large }
                    format
                    type
                }
            }
        }
    """

    /** Search anime by title — powers a search screen or "add to list" flow. */
    suspend fun searchAnime(query: String, perPage: Int = 20): List<AniListMedia> {
        val gql = """
            query (${'$'}search: String, ${'$'}perPage: Int) {
                Page(perPage: ${'$'}perPage) {
                    media(search: ${'$'}search, type: ANIME, sort: SEARCH_MATCH) {
                        $MEDIA_FIELDS
                    }
                }
            }
        """.trimIndent()
        return execute(gql, mapOf("search" to query, "perPage" to perPage)).page?.media ?: emptyList()
    }

    /** Currently trending anime — a natural source for a "Trending Now" row. */
    suspend fun getTrending(perPage: Int = 20): List<AniListMedia> {
        val gql = """
            query (${'$'}perPage: Int) {
                Page(perPage: ${'$'}perPage) {
                    media(sort: TRENDING_DESC, type: ANIME) {
                        $MEDIA_FIELDS
                    }
                }
            }
        """.trimIndent()
        return execute(gql, mapOf("perPage" to perPage)).page?.media ?: emptyList()
    }

    /** Highest-rated anime this season — good source for "Top Picks". */
    suspend fun getTopThisSeason(season: String, seasonYear: Int, perPage: Int = 20): List<AniListMedia> {
        val gql = """
            query (${'$'}season: MediaSeason, ${'$'}seasonYear: Int, ${'$'}perPage: Int) {
                Page(perPage: ${'$'}perPage) {
                    media(season: ${'$'}season, seasonYear: ${'$'}seasonYear, type: ANIME, sort: POPULARITY_DESC) {
                        $MEDIA_FIELDS
                    }
                }
            }
        """.trimIndent()
        return execute(gql, mapOf("season" to season, "seasonYear" to seasonYear, "perPage" to perPage))
            .page?.media ?: emptyList()
    }

    /** Single title lookup by AniList ID, light fields only — for hero cards, "My List" rows, etc. */
    suspend fun getMediaById(id: Int): AniListMedia? {
        val gql = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: ANIME) {
                    $MEDIA_FIELDS
                }
            }
        """.trimIndent()
        return execute(gql, mapOf("id" to id)).media
    }

    /** Full record for the details screen: adds studios, cast, crew, relations, trailer, episodes. */
    suspend fun getMediaDetails(id: Int): AniListMedia? {
        val gql = """
            query (${'$'}id: Int) {
                Media(id: ${'$'}id, type: ANIME) {
                    $MEDIA_DETAILS_FIELDS
                }
            }
        """.trimIndent()
        return execute(gql, mapOf("id" to id)).media
    }

    private suspend fun execute(query: String, variables: Map<String, Any?>): ResponseData =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("query", query)
                put("variables", buildJsonObject {
                    variables.forEach { (key, value) -> put(key, toJsonElement(value)) }
                })
            }.toString()

            val request = Request.Builder()
                .url(ENDPOINT)
                .addHeader("Content-Type", "application/json")
                .addHeader("Accept", "application/json")
                .post(payload.toRequestBody(jsonMediaType))
                .build()

            client.newCall(request).execute().use { response ->
                val raw = response.body?.string()
                    ?: throw AniListException("Empty response from AniList")
                if (!response.isSuccessful) {
                    throw AniListException("AniList request failed (${response.code}): $raw")
                }
                val envelope = json.decodeFromString(GraphQLEnvelope.serializer(), raw)
                envelope.errors?.firstOrNull()?.let { throw AniListException(it.message) }
                envelope.data ?: throw AniListException("AniList returned no data")
            }
        }

    private fun toJsonElement(value: Any?): JsonElement = when (value) {
        null -> JsonPrimitive(null as String?)
        is Int -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        else -> JsonPrimitive(value.toString())
    }
}
