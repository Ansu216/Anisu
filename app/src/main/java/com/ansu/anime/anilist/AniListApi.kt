package com.ansu.anime.anilist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Talks to https://graphql.anilist.co directly with hand-written queries.
 * AniList's schema is stable and public; a raw client avoids pulling in a
 * codegen pipeline (Apollo) that we can't build-test in this environment.
 */
class AniListApi(
    private val client: OkHttpClient,
    private val authManager: AniListAuthManager,
) {
    private val endpoint = "https://graphql.anilist.co"
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun getViewer(): AniListViewer? {
        val query = """
            query { Viewer { id name avatar { medium } } }
        """.trimIndent()
        val data = execute(query, emptyMap()) ?: return null
        val viewer = data["Viewer"]?.jsonObject ?: return null
        return AniListViewer(
            id = viewer["id"]!!.jsonPrimitive.content.toInt(),
            name = viewer["name"]!!.jsonPrimitive.content,
            avatarUrl = viewer["avatar"]?.jsonObject?.get("medium")?.jsonPrimitive?.content,
        )
    }

    suspend fun getMediaListCollection(userId: Int, status: String = "CURRENT"): List<AniListMediaListEntry> {
        val query = """
            query (${'$'}userId: Int, ${'$'}status: MediaListStatus) {
              MediaListCollection(userId: ${'$'}userId, type: ANIME, status: ${'$'}status) {
                lists {
                  entries {
                    progress
                    status
                    media { ...mediaFields }
                  }
                }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(query, mapOf("userId" to userId, "status" to status)) ?: return emptyList()
        val lists = data["MediaListCollection"]?.jsonObject?.get("lists")?.jsonArray ?: return emptyList()
        return lists.flatMap { list ->
            list.jsonObject["entries"]?.jsonArray.orEmpty().mapNotNull { entry ->
                val entryObj = entry.jsonObject
                val media = entryObj["media"]?.jsonObject?.toMedia() ?: return@mapNotNull null
                AniListMediaListEntry(
                    mediaId = media.id,
                    progress = entryObj["progress"]!!.jsonPrimitive.content.toInt(),
                    status = entryObj["status"]!!.jsonPrimitive.content,
                    media = media,
                )
            }
        }
    }

    /** The signed-in user's favourited anime (the hearts), newest page of up to 50. */
    suspend fun getFavouriteAnime(userId: Int): List<AniListMedia> {
        val query = """
            query (${'$'}userId: Int) {
              User(id: ${'$'}userId) {
                favourites {
                  anime(page: 1, perPage: 50) {
                    nodes { ...mediaFields }
                  }
                }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(query, mapOf("userId" to userId)) ?: return emptyList()
        val nodes = data["User"]?.jsonObject
            ?.get("favourites")?.jsonObject
            ?.get("anime")?.jsonObject
            ?.get("nodes")?.jsonArray
            .orEmpty()
        return nodes.mapNotNull { it.jsonObject.toMedia() }
    }

    suspend fun searchMedia(query: String, page: Int = 1): List<AniListMedia> {
        val gql = """
            query (${'$'}page: Int, ${'$'}search: String) {
              Page(page: ${'$'}page, perPage: 20) {
                media(type: ANIME, search: ${'$'}search) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page, "search" to query)) ?: return emptyList()
        return data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() }
    }

    suspend fun getTrending(page: Int = 1): List<AniListMedia> {
        val gql = """
            query (${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 20) {
                media(type: ANIME, sort: TRENDING_DESC) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page)) ?: return emptyList()
        return data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() }
    }

    /** The most popular anime of a given season (e.g. "FALL" 2026) — powers the home screen's "Top Picks". */
    suspend fun getTopThisSeason(season: String, seasonYear: Int, page: Int = 1): List<AniListMedia> {
        val gql = """
            query (${'$'}page: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page(page: ${'$'}page, perPage: 20) {
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}seasonYear, sort: POPULARITY_DESC) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page, "season" to season, "seasonYear" to seasonYear)) ?: return emptyList()
        return data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() }
    }

    /** Pushes watched-episode progress back to the user's AniList list; called (debounced) as the player advances. */
    suspend fun updateProgress(mediaId: Int, progress: Int): Boolean {
        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) { id }
            }
        """.trimIndent()
        return execute(mutation, mapOf("mediaId" to mediaId, "progress" to progress)) != null
    }

    /**
     * Everything the CornCastle-style details page shows beyond the basic
     * card: format, characters with their voice actors, staff, and related
     * shows for the "More like this" row.
     */
    suspend fun getMediaDetails(mediaId: Int): AniListMediaDetails? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english }
                coverImage { extraLarge }
                bannerImage
                description(asHtml: false)
                genres
                averageScore
                episodes
                format
                isFavourite
                startDate { year }
                characters(sort: [ROLE, RELEVANCE], perPage: 10) {
                  edges {
                    role
                    node { id name { full } image { large } description(asHtml: false) }
                    voiceActors(language: JAPANESE, sort: RELEVANCE) { id name { full } image { large } }
                  }
                }
                staff(sort: [RELEVANCE], perPage: 10) {
                  edges {
                    role
                    node { id name { full } image { large } description(asHtml: false) }
                  }
                }
                recommendations(perPage: 8, sort: RATING_DESC) {
                  nodes {
                    mediaRecommendation { id title { romaji english } coverImage { extraLarge } bannerImage genres averageScore episodes startDate { year } }
                  }
                }
              }
            }
        """.trimIndent()
        val data = execute(gql, mapOf("id" to mediaId)) ?: return null
        val media = data["Media"]?.jsonObject ?: return null
        return media.toMediaDetails()
    }

    /** Episodes airing between [fromEpochSeconds] and [toEpochSeconds] — powers the Schedule tab. */
    suspend fun getAiringSchedule(fromEpochSeconds: Long, toEpochSeconds: Long): List<AniListAiringEntry> {
        val gql = """
            query (${'$'}from: Int, ${'$'}to: Int) {
              Page(perPage: 50) {
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
                  airingAt
                  episode
                  media { ...mediaFields }
                }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("from" to fromEpochSeconds.toInt(), "to" to toEpochSeconds.toInt())) ?: return emptyList()
        val entries = data["Page"]?.jsonObject?.get("airingSchedules")?.jsonArray.orEmpty()
        return entries.mapNotNull { entry ->
            val obj = entry.jsonObject
            val media = obj["media"]?.jsonObject?.toMedia() ?: return@mapNotNull null
            AniListAiringEntry(
                airingAt = obj["airingAt"]!!.jsonPrimitive.content.toLong(),
                episode = obj["episode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                media = media,
            )
        }
    }

    /** Toggles the heart/favourite state on the signed-in user's AniList account for this show. */
    suspend fun toggleFavourite(mediaId: Int): Boolean {
        val mutation = """
            mutation (${'$'}id: Int) { ToggleFavourite(animeId: ${'$'}id) { anime { nodes { id } } } }
        """.trimIndent()
        return execute(mutation, mapOf("id" to mediaId)) != null
    }

    private suspend fun execute(query: String, variables: Map<String, Any?>): JsonObject? = withContext(Dispatchers.IO) {
        val payload = buildString {
            append("{\"query\":")
            append(json.encodeToString(kotlinx.serialization.serializer<String>(), query))
            append(",\"variables\":{")
            append(
                variables.entries.joinToString(",") { (key, value) ->
                    val jsonValue = when (value) {
                        is Int -> value.toString()
                        is Long -> value.toString()
                        is String -> json.encodeToString(kotlinx.serialization.serializer<String>(), value)
                        null -> "null"
                        else -> json.encodeToString(kotlinx.serialization.serializer<String>(), value.toString())
                    }
                    "\"$key\":$jsonValue"
                },
            )
            append("}}")
        }

        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/json".toMediaType()))
        authManager.accessToken.value?.let { token -> requestBuilder.addHeader("Authorization", "Bearer $token") }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful || text.isBlank()) return@withContext null
            runCatching { json.parseToJsonElement(text).jsonObject["data"]?.jsonObject }.getOrNull()
        }
    }

    private fun JsonObject.toMedia(): AniListMedia? {
        val idValue = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val titleObj = this["title"]?.jsonObject
        val title = titleObj?.get("romaji")?.jsonPrimitive?.content
            ?: titleObj?.get("english")?.jsonPrimitive?.content
            ?: return null
        return AniListMedia(
            id = idValue,
            title = title,
            posterUrl = this["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content,
            bannerUrl = this["bannerImage"]?.jsonPrimitive?.content,
            description = this["description"]?.jsonPrimitive?.content,
            genres = this["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            averageScore = this["averageScore"]?.jsonPrimitive?.content?.toIntOrNull(),
            episodes = this["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
            year = this["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content?.toIntOrNull(),
        )
    }

    private fun JsonObject.toMediaDetails(): AniListMediaDetails? {
        val idValue = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val titleObj = this["title"]?.jsonObject
        val title = titleObj?.get("romaji")?.jsonPrimitive?.content
            ?: titleObj?.get("english")?.jsonPrimitive?.content
            ?: return null

        val characterEdges = this["characters"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
        val characters = characterEdges.mapNotNull { edge -> edge.jsonObject.toCharacter() }

        val staffEdges = this["staff"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
        val staff = staffEdges.mapNotNull { edge -> edge.jsonObject.toStaffMember() }

        val recommendationNodes = this["recommendations"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
        val related = recommendationNodes.mapNotNull { node ->
            node.jsonObject["mediaRecommendation"]?.jsonObject?.toMedia()
        }

        return AniListMediaDetails(
            id = idValue,
            title = title,
            posterUrl = this["coverImage"]?.jsonObject?.get("extraLarge")?.jsonPrimitive?.content,
            bannerUrl = this["bannerImage"]?.jsonPrimitive?.content,
            description = this["description"]?.jsonPrimitive?.content,
            genres = this["genres"]?.jsonArray?.map { it.jsonPrimitive.content } ?: emptyList(),
            averageScore = this["averageScore"]?.jsonPrimitive?.content?.toIntOrNull(),
            episodes = this["episodes"]?.jsonPrimitive?.content?.toIntOrNull(),
            year = this["startDate"]?.jsonObject?.get("year")?.jsonPrimitive?.content?.toIntOrNull(),
            format = this["format"]?.jsonPrimitive?.content,
            isFavourite = this["isFavourite"]?.jsonPrimitive?.content == "true",
            characters = characters,
            staff = staff,
            related = related,
        )
    }

    private fun JsonObject.toCharacter(): AniListCharacter? {
        val node = this["node"]?.jsonObject ?: return null
        val idValue = node["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val name = node["name"]?.jsonObject?.get("full")?.jsonPrimitive?.content ?: return null
        val voiceActor = this["voiceActors"]?.jsonArray?.firstOrNull()?.jsonObject
        return AniListCharacter(
            id = idValue,
            name = name,
            imageUrl = node["image"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
            role = this["role"]?.jsonPrimitive?.content ?: "BACKGROUND",
            description = node["description"]?.jsonPrimitive?.content,
            voiceActorName = voiceActor?.get("name")?.jsonObject?.get("full")?.jsonPrimitive?.content,
            voiceActorImageUrl = voiceActor?.get("image")?.jsonObject?.get("large")?.jsonPrimitive?.content,
        )
    }

    private fun JsonObject.toStaffMember(): AniListStaffMember? {
        val node = this["node"]?.jsonObject ?: return null
        val idValue = node["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val name = node["name"]?.jsonObject?.get("full")?.jsonPrimitive?.content ?: return null
        return AniListStaffMember(
            id = idValue,
            name = name,
            imageUrl = node["image"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
            role = this["role"]?.jsonPrimitive?.content ?: "",
            description = node["description"]?.jsonPrimitive?.content,
        )
    }

    companion object {
        private const val MEDIA_FIELDS = """
            fragment mediaFields on Media {
              id
              title { romaji english }
              coverImage { extraLarge }
              bannerImage
              description(asHtml: false)
              genres
              averageScore
              episodes
              startDate { year }
            }
        """
    }
}
