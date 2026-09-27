package com.ansu.anime.anilist

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
data class AniListPersonImage(
    val large: String? = null,
    val medium: String? = null,
)

@Serializable
data class AniListName(
    val full: String? = null,
)

@Serializable
data class AniListCharacterNode(
    val id: Int,
    val name: AniListName = AniListName(),
    val image: AniListPersonImage? = null,
)

@Serializable
data class AniListCharacterEdge(
    val role: String? = null,
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
    val role: String? = null,
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

@Serializable
data class AniListRelatedNode(
    val id: Int,
    val title: AniListTitle = AniListTitle(),
    val coverImage: AniListCoverImage? = null,
    val format: String? = null,
    val type: String? = null,
)

@Serializable
data class AniListRelationEdge(
    val relationType: String? = null,
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
    val site: String? = null,
    val thumbnail: String? = null,
)

@Serializable
data class AniListStreamingEpisode(
    val title: String? = null,
    val thumbnail: String? = null,
    val url: String? = null,
    val site: String? = null,
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
    val studios: AniListStudioConnection? = null,
    val trailer: AniListTrailer? = null,
    val streamingEpisodes: List<AniListStreamingEpisode> = emptyList(),
    val characters: AniListCharacterConnection? = null,
    val staff: AniListStaffConnection? = null,
    val relations: AniListRelationConnection? = null,
    val recommendations: AniListRecommendationConnection? = null,
) {
    val displayTitle: String get() = title.english ?: title.romaji ?: title.native ?: "Untitled"

    val plainDescription: String
        get() = description?.replace("<br>", "\n")?.replace(Regex("<.*?>"), "") ?: ""
}

data class AniListViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
)

data class AniListMediaListEntry(
    val id: Int,
    val status: String,
    val progress: Int,
    val media: AniListMediaListMedia,
)

data class AniListMediaListMedia(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val bannerUrl: String?,
    val description: String?,
    val genres: List<String>,
    val averageScore: Int?,
    val episodes: Int?,
    val year: Int?,
)

class AniListException(message: String) : IOException(message)

@Serializable
private data class PageData(val media: List<AniListMedia> = emptyList())

@Serializable
private data class ViewerAvatar(val large: String? = null)

@Serializable
private data class ViewerResponse(
    val id: Int,
    val name: String,
    val avatar: ViewerAvatar? = null,
)

@Serializable
private data class ListMediaResponse(
    val id: Int,
    val title: AniListTitle = AniListTitle(),
    val coverImage: AniListCoverImage? = null,
    val bannerImage: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val averageScore: Int? = null,
    val episodes: Int? = null,
    val seasonYear: Int? = null,
)

@Serializable
private data class ResponseData(
    @SerialName("Page") val page: PageData? = null,
    @SerialName("Media") val media: AniListMedia? = null,
    @SerialName("Viewer") val viewer: ViewerResponse? = null,
    @SerialName("MediaListCollection") val mediaListCollection: AniListMediaListCollection? = null,
)

@Serializable
private data class AniListMediaListCollection(
    val lists: List<AniListMediaListCollectionList> = emptyList(),
)

@Serializable
private data class AniListMediaListCollectionList(
    val name: String = "",
    val isCustomList: Boolean = false,
    val entries: List<AniListMediaListEntryRow> = emptyList(),
)

@Serializable
private data class AniListMediaListEntryRow(
    val id: Int,
    val status: String = "",
    val score: Int? = null,
    val completedEpisodes: Int = 0,
    val updatedAt: Int? = null,
    val media: ListMediaResponse? = null,
)

@Serializable
private data class GraphQLError(val message: String)

@Serializable
private data class GraphQLEnvelope(
    val data: ResponseData? = null,
    val errors: List<GraphQLError>? = null,
)

object AniListApi {
    private const val ENDPOINT = "https://graphql.anilist.co"

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val jsonMediaType = "application/json".toMediaType()

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

    suspend fun searchMedia(query: String, perPage: Int = 20): List<AniListMedia> {
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

    suspend fun getViewer(): AniListViewer? {
        val gql = """
            query {
                Viewer {
                    id
                    name
                    avatar {
                        large
                    }
                }
            }
        """.trimIndent()
        return execute(gql, emptyMap()).viewer?.let { v ->
            AniListViewer(id = v.id, name = v.name, avatarUrl = v.avatar?.large)
        }
    }

    suspend fun getMediaListCollection(userId: Int, status: String): List<AniListMediaListEntry> {
        val gql = """
            query (${'$'}userId: Int, ${'$'}type: MediaListStatus) {
                MediaListCollection(user: { id: ${'$'}userId }, type: ${'$'}type) {
                    lists {
                        name
                        isCustomList
                        entries {
                            id
                            status
                            score
                            completedEpisodes
                            updatedAt
                            media {
                                id
                                title { romaji english native }
                                coverImage { extraLarge large color }
                                bannerImage
                                description(asHtml: false)
                                genres
                                averageScore
                                episodes
                                seasonYear
                                format
                            }
                        }
                    }
                }
            }
        """.trimIndent()
        val data = execute(gql, mapOf("userId" to userId, "type" to status))
        return data.mediaListCollection?.lists?.flatMap { list ->
            list.entries.mapNotNull { entry ->
                entry.media?.let { media ->
                    AniListMediaListEntry(
                        id = entry.id,
                        status = entry.status,
                        progress = entry.completedEpisodes,
                        media = AniListMediaListMedia(
                            id = media.id,
                            title = media.title.english ?: media.title.romaji ?: media.title.native ?: "Untitled",
                            posterUrl = media.coverImage?.extraLarge ?: media.coverImage?.large,
                            bannerUrl = media.bannerImage,
                            description = media.description,
                            genres = media.genres,
                            averageScore = media.averageScore,
                            episodes = media.episodes,
                            year = media.seasonYear,
                        ),
                    )
                }
            }
        } ?: emptyList()
    }

    suspend fun updateProgress(mediaId: Int, episode: Int): Boolean {
        val gql = """
            mutation (${'$'}mediaId: Int, ${'$'}episode: Int) {
                SaveMediaListEntry(mediaId: ${'$'}mediaId, status: WATCHING, progress: ${'$'}episode) {
                    id
                }
            }
        """.trimIndent()
        return runCatching { execute(gql, mapOf("mediaId" to mediaId, "episode" to episode)) }.isSuccess
    }

    suspend fun toggleFavourite(mediaId: Int): Boolean {
        val gql = """
            mutation (${'$'}mediaId: Int!) {
                SaveMediaFavourite(mediaId: ${'$'}mediaId) {
                    id
                }
            }
        """.trimIndent()
        return runCatching { execute(gql, mapOf("mediaId" to mediaId)) }.isSuccess
    }

    private suspend fun execute(query: String, variables: Map<String, Any?>): ResponseData =
        withContext(Dispatchers.IO) {
            val payload = buildJsonObject {
                put("query", query)
                put("variables", buildJsonObject {
                    variables.forEach { (key, value) -> put(key, value.toJsonElement()) }
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

    private fun Any?.toJsonElement(): JsonElement = when (this) {
        null -> JsonPrimitive(null as String?)
        is Int -> JsonPrimitive(this)
        is Boolean -> JsonPrimitive(this)
        else -> JsonPrimitive(this.toString())
    }
}
