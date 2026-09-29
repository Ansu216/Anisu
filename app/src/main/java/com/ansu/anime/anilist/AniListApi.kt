package com.ansu.anime.anilist

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
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

    /**
     * One page of the Search screen's query: optional search text plus the filter sheet's
     * [filters]. Returns null on a network/API failure so the UI can tell it apart from "no results".
     * Filter values are inlined into the query text, so each is checked by [safeLiteral] first.
     */
    suspend fun searchMediaPage(
        query: String,
        filters: AniListSearchFilters,
        page: Int,
        perPage: Int = 30,
    ): AniListMediaPage? {
        val hasText = query.isNotBlank()
        // No text and no filters means the Search tab's landing state: top trending titles.
        val defaultSort = when {
            hasText -> "SEARCH_MATCH"
            filters.isActive -> "POPULARITY_DESC"
            else -> "TRENDING_DESC"
        }
        val sort = filters.sort?.takeIf { safeLiteral(it) } ?: defaultSort
        val args = buildList {
            add("type: ANIME")
            if (hasText) add("search: ${'$'}search")
            add("sort: [$sort]")
            filters.formats.filter { safeLiteral(it) }.takeIf { it.isNotEmpty() }
                ?.let { add("format_in: [${it.joinToString()}]") }
            filters.statuses.filter { safeLiteral(it) }.takeIf { it.isNotEmpty() }
                ?.let { add("status_in: [${it.joinToString()}]") }
            filters.genres.filter { safeLiteral(it) }.takeIf { it.isNotEmpty() }
                ?.let { list -> add("genre_in: [${list.joinToString { "\"$it\"" }}]") }
            filters.tags.filter { safeLiteral(it) }.takeIf { it.isNotEmpty() }
                ?.let { list -> add("tag_in: [${list.joinToString { "\"$it\"" }}]") }
            filters.season?.takeIf { safeLiteral(it) }?.let { add("season: $it") }
            filters.year?.let { add("seasonYear: $it") }
            if (!filters.showAdult) add("isAdult: false")
        }.joinToString(", ")
        val searchVar = if (hasText) ", ${'$'}search: String" else ""
        val gql = """
            query (${'$'}page: Int, ${'$'}perPage: Int$searchVar) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                media($args) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val variables = buildMap<String, Any?> {
            put("page", page)
            put("perPage", perPage)
            if (hasText) put("search", query)
        }
        val data = execute(gql, variables) ?: return null
        val pageObj = data["Page"]?.jsonObject ?: return null
        return AniListMediaPage(
            media = pageObj["media"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() },
            hasNextPage = pageObj["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content == "true",
        )
    }

    /** Only letters, digits, space, hyphen and underscore may be inlined into a GraphQL literal. */
    private fun safeLiteral(value: String): Boolean =
        value.isNotEmpty() && value.all { it.isLetterOrDigit() || it == ' ' || it == '-' || it == '_' }

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

    /**
     * One page of a filtered/sorted anime query — powers the home screen's endless rows.
     * Returns null on a network/API failure so callers can tell it apart from "no results".
     * [sort], [status] and [formats] are AniList enum names supplied by [AniListFeed], never user input.
     */
    suspend fun getMediaPage(
        page: Int,
        perPage: Int,
        sort: String,
        status: String? = null,
        formats: List<String> = emptyList(),
    ): AniListMediaPage? {
        val args = buildList {
            add("type: ANIME")
            add("sort: $sort")
            status?.let { add("status: $it") }
            if (formats.isNotEmpty()) add("format_in: [${formats.joinToString()}]")
            add("isAdult: false")
        }.joinToString(", ")
        val gql = """
            query (${'$'}page: Int, ${'$'}perPage: Int) {
              Page(page: ${'$'}page, perPage: ${'$'}perPage) {
                pageInfo { hasNextPage }
                media($args) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page, "perPage" to perPage)) ?: return null
        val pageObj = data["Page"]?.jsonObject ?: return null
        return AniListMediaPage(
            media = pageObj["media"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() },
            hasNextPage = pageObj["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content == "true",
        )
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
                mediaListEntry { status }
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

    /** Episode titles and thumbnails AniList knows about for a show; empty when it has none. */
    suspend fun getStreamingEpisodes(mediaId: Int): List<AniListStreamingEpisode> {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) { streamingEpisodes { title thumbnail } }
            }
        """.trimIndent()
        val data = execute(gql, mapOf("id" to mediaId)) ?: return emptyList()
        val entries = data["Media"]?.jsonObject?.get("streamingEpisodes")?.jsonArray.orEmpty()
        return entries.mapNotNull { element ->
            val obj = element.jsonObject
            val title = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AniListStreamingEpisode(title = title, thumbnailUrl = obj["thumbnail"]?.jsonPrimitive?.contentOrNull)
        }
    }

    /**
     * Episodes airing between [fromEpochSeconds] and [toEpochSeconds] — powers the Schedule tab.
     * A two-week window holds far more than one 50-row page, so this follows `hasNextPage`
     * (capped at [MAX_SCHEDULE_PAGES]) and returns whatever was fetched if a later page fails.
     */
    suspend fun getAiringSchedule(fromEpochSeconds: Long, toEpochSeconds: Long): List<AniListAiringEntry> {
        val gql = """
            query (${'$'}from: Int, ${'$'}to: Int, ${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 50) {
                pageInfo { hasNextPage }
                airingSchedules(airingAt_greater: ${'$'}from, airingAt_lesser: ${'$'}to, sort: TIME) {
                  airingAt
                  episode
                  media { ...mediaFields }
                }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val result = mutableListOf<AniListAiringEntry>()
        var page = 1
        while (page <= MAX_SCHEDULE_PAGES) {
            val data = execute(
                gql,
                mapOf("from" to fromEpochSeconds.toInt(), "to" to toEpochSeconds.toInt(), "page" to page),
            ) ?: break
            val pageObj = data["Page"]?.jsonObject
            val entries = pageObj?.get("airingSchedules")?.jsonArray.orEmpty()
            entries.mapNotNullTo(result) { entry ->
                val obj = entry.jsonObject
                val media = obj["media"]?.jsonObject?.toMedia() ?: return@mapNotNullTo null
                AniListAiringEntry(
                    airingAt = obj["airingAt"]!!.jsonPrimitive.content.toLong(),
                    episode = obj["episode"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    media = media,
                )
            }
            val hasNext = pageObj?.get("pageInfo")?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content == "true"
            if (!hasNext) break
            page++
        }
        return result
    }

    /** The signed-in user's heart and list entry for one show; null on a network/API failure. */
    suspend fun getUserState(mediaId: Int): AniListUserState? = getUserEntry(mediaId)?.second

    /** Same as [getUserState] plus the list entry id AniList needs to delete an entry. */
    private suspend fun getUserEntry(mediaId: Int): Pair<Int?, AniListUserState>? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) { isFavourite mediaListEntry { id status progress } }
            }
        """.trimIndent()
        val media = execute(gql, mapOf("id" to mediaId))?.get("Media") as? JsonObject ?: return null
        val entry = media["mediaListEntry"] as? JsonObject
        return entry?.get("id")?.jsonPrimitive?.content?.toIntOrNull() to AniListUserState(
            isFavourite = media["isFavourite"]?.jsonPrimitive?.content == "true",
            listStatus = entry?.get("status")?.jsonPrimitive?.contentOrNull,
            progress = entry?.get("progress")?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        )
    }

    /** Adds the show to the user's list with [status] (or moves it there). [progress] is left alone when null. */
    suspend fun saveListStatus(mediaId: Int, status: String, progress: Int? = null): Boolean {
        val progressArg = if (progress != null) ", progress: ${'$'}progress" else ""
        val progressVar = if (progress != null) ", ${'$'}progress: Int" else ""
        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}status: MediaListStatus$progressVar) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, status: ${'$'}status$progressArg) { id }
            }
        """.trimIndent()
        val variables = buildMap<String, Any?> {
            put("mediaId", mediaId)
            put("status", status)
            if (progress != null) put("progress", progress)
        }
        return execute(mutation, variables) != null
    }

    /** Removes the show from the user's list. Succeeds when it was not on the list to begin with. */
    suspend fun removeFromList(mediaId: Int): Boolean {
        val (entryId, _) = getUserEntry(mediaId) ?: return false
        if (entryId == null) return true
        val mutation = """
            mutation (${'$'}id: Int) { DeleteMediaListEntry(id: ${'$'}id) { deleted } }
        """.trimIndent()
        return execute(mutation, mapOf("id" to entryId)) != null
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
            format = this["format"]?.jsonPrimitive?.contentOrNull,
            isAdult = this["isAdult"]?.jsonPrimitive?.content == "true",
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
            listStatus = (this["mediaListEntry"] as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull,
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
        private const val MAX_SCHEDULE_PAGES = 12

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
              format
              isAdult
            }
        """
    }
}
