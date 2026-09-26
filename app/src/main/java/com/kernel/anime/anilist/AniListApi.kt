package com.kernel.anime.anilist

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

    /** Pushes watched-episode progress back to the user's AniList list; called (debounced) as the player advances. */
    suspend fun updateProgress(mediaId: Int, progress: Int): Boolean {
        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) { id }
            }
        """.trimIndent()
        return execute(mutation, mapOf("mediaId" to mediaId, "progress" to progress)) != null
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
