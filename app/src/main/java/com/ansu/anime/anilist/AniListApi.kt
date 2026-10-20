package com.ansu.anime.anilist

import com.ansu.anime.core.net.ApiErrorKind
import com.ansu.anime.core.net.ApiException
import com.ansu.anime.core.net.httpApiException
import com.ansu.anime.core.net.toApiException
import com.ansu.anime.core.util.ContentFilter
import com.ansu.anime.data.prefs.TitleLanguage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.withLock
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
    /** Called whenever AniList really answers (never for cached data), so an "AniList is down" notice can clear. */
    private val onNetworkSuccess: () -> Unit = {},
    /** Read on every response, so a change in Settings applies to the next request. */
    private val titleLanguage: () -> TitleLanguage = { TitleLanguage.ROMAJI },
) {
    private val endpoint = "https://graphql.anilist.co"
    private val json = Json { ignoreUnknownKeys = true }

    /** The cheapest real request AniList can answer; used to find out whether it is back up. Never cached. */
    suspend fun ping() {
        execute("query { Page(page: 1, perPage: 1) { media(id: 1) { id } } }", emptyMap())
    }

    suspend fun getViewer(): AniListViewer? {
        val query = """
            query { Viewer { id name avatar { medium } bannerImage } }
        """.trimIndent()
        val data = execute(query, emptyMap()) ?: return null
        val viewer = data["Viewer"]?.jsonObject ?: return null
        // Never use `!!` on a server response: a schema change or a partial payload would
        // otherwise throw on the main path and take a screen down (or the whole app with it).
        val id = viewer["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: return null
        val name = viewer["name"]?.jsonPrimitive?.contentOrNull ?: return null
        return AniListViewer(
            id = id,
            name = name,
            avatarUrl = viewer["avatar"]?.jsonObject?.get("medium")?.jsonPrimitive?.content,
            bannerUrl = viewer["bannerImage"]?.jsonPrimitive?.contentOrNull,
        )
    }

    /**
     * The entries on one of the signed-in user's lists ([status] is an AniList list status such as
     * CURRENT). AniList's answer holds every list at once, so it is fetched once (in chunks of 500
     * entries when the library is large) and shared by all of the status calls My Space makes.
     */
    suspend fun getMediaListCollection(userId: Int, status: String = "CURRENT"): List<AniListMediaListEntry> {
        val query = """
            query (${'$'}userId: Int, ${'$'}chunk: Int) {
              MediaListCollection(userId: ${'$'}userId, type: ANIME, chunk: ${'$'}chunk, perChunk: 500) {
                hasNextChunk
                lists {
                  isCustomList
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
        val result = mutableListOf<AniListMediaListEntry>()
        var chunk = 1
        while (chunk <= MAX_LIST_CHUNKS) {
            val data = execute(query, mapOf("userId" to userId, "chunk" to chunk), cacheTtlMs = LIST_TTL_MS)
                ?: return result.takeIf { it.isNotEmpty() } ?: emptyList()
            val collection = data["MediaListCollection"]?.jsonObject ?: break
            collection["lists"]?.jsonArray.orEmpty().forEach { list ->
                val listObj = list.jsonObject
                // A custom list repeats entries that already sit on a status list.
                if (listObj["isCustomList"]?.jsonPrimitive?.content == "true") return@forEach
                listObj["entries"]?.jsonArray.orEmpty().forEach { entry ->
                    val entryObj = entry.jsonObject
                    val entryStatus = entryObj["status"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (entryStatus != status) return@forEach
                    val media = entryObj["media"]?.jsonObject?.toMedia() ?: return@forEach
                    result += AniListMediaListEntry(
                        mediaId = media.id,
                        progress = entryObj["progress"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                        status = entryStatus,
                        media = media,
                    )
                }
            }
            if (collection["hasNextChunk"]?.jsonPrimitive?.content != "true") break
            chunk++
        }
        return result
    }

    /** The signed-in user's favourited anime (the hearts), newest page of up to 50. */
    /** Ids of all favourite anime, paging until AniList says there are no more. */
    suspend fun getFavouriteIds(userId: Int): Set<Int> {
        val ids = mutableSetOf<Int>()
        var page = 1
        while (page <= 20) {
            val query = """
                query (${'$'}userId: Int, ${'$'}page: Int) {
                  User(id: ${'$'}userId) { favourites { anime(page: ${'$'}page, perPage: 50) { pageInfo { hasNextPage } nodes { id } } } }
                }
            """.trimIndent()
            val anime = execute(query, mapOf("userId" to userId, "page" to page))
                ?.get("User")?.jsonObject?.get("favourites")?.jsonObject?.get("anime")?.jsonObject ?: break
            anime["nodes"]?.jsonArray.orEmpty().forEach { n -> n.jsonObject["id"]?.jsonPrimitive?.content?.toIntOrNull()?.let(ids::add) }
            if (anime["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content != "true") break
            page++
        }
        return ids
    }

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
        val data = execute(query, mapOf("userId" to userId), cacheTtlMs = LIST_TTL_MS) ?: return emptyList()
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
                media(type: ANIME, search: ${'$'}search, isAdult: false) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page, "search" to query), cacheTtlMs = SEARCH_TTL_MS) ?: return emptyList()
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
            add("isAdult: false")
            add("genre_not_in: [\"Hentai\"]")
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
        val data = execute(gql, variables, cacheTtlMs = SEARCH_TTL_MS) ?: return null
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
                media(type: ANIME, sort: TRENDING_DESC, isAdult: false) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page), cacheTtlMs = FEED_TTL_MS) ?: return emptyList()
        return data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() }
    }

    /**
     * What AniList users who liked [mediaId] also recommend, best rated first. Powers the home screen's
     * "Your Next Watch" banner. Adult titles are dropped by [toMedia], like everywhere else.
     */
    suspend fun getRecommendations(mediaId: Int, perPage: Int = 6): List<AniListMedia> {
        val gql = """
            query (${'$'}id: Int, ${'$'}perPage: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                recommendations(perPage: ${'$'}perPage, sort: RATING_DESC) {
                  nodes { rating mediaRecommendation { ...mediaFields } }
                }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("id" to mediaId, "perPage" to perPage), cacheTtlMs = DETAILS_TTL_MS) ?: return emptyList()
        return data["Media"]?.jsonObject?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
            .mapNotNull { node ->
                val obj = node.jsonObject
                // A recommendation people voted down is not worth showing.
                val rating = obj["rating"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                if (rating < 0) null else obj["mediaRecommendation"]?.jsonObject?.toMedia()
            }
    }

    /** The most popular anime of a given season (e.g. "FALL" 2026) — powers the home screen's "Top Picks". */
    suspend fun getTopThisSeason(season: String, seasonYear: Int, page: Int = 1): List<AniListMedia> {
        val gql = """
            query (${'$'}page: Int, ${'$'}season: MediaSeason, ${'$'}seasonYear: Int) {
              Page(page: ${'$'}page, perPage: 20) {
                media(type: ANIME, season: ${'$'}season, seasonYear: ${'$'}seasonYear, sort: POPULARITY_DESC, isAdult: false) { ...mediaFields }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("page" to page, "season" to season, "seasonYear" to seasonYear), cacheTtlMs = FEED_TTL_MS) ?: return emptyList()
        return data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() }
    }

    private fun feedArgs(feed: AniListFeed): String = buildList {
        add("type: ANIME")
        add("sort: ${feed.sort}")
        feed.status?.let { add("status: $it") }
        if (feed.formats.isNotEmpty()) add("format_in: [${feed.formats.joinToString()}]")
        add("isAdult: false")
        add("genre_not_in: [\"Hentai\"]")
    }.joinToString(", ")

    private fun newReleaseArgs(formats: List<String>, from: java.time.LocalDate, to: java.time.LocalDate): String {
        fun fuzzy(d: java.time.LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
        return listOf(
            "type: ANIME",
            "sort: [START_DATE_DESC, POPULARITY_DESC]",
            "status_in: [RELEASING, FINISHED]",
            "format_in: [${formats.joinToString()}]",
            "startDate_greater: ${fuzzy(from.minusDays(1))}",
            "startDate_lesser: ${fuzzy(to.plusDays(1))}",
            "popularity_greater: 300",
            "isAdult: false",
            "genre_not_in: [\"Hentai\"]",
        ).joinToString(", ")
    }

    private fun JsonObject.toMediaPage() = AniListMediaPage(
        media = this["media"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() },
        hasNextPage = this["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content == "true",
    )

    private fun JsonObject.toNewReleases(): List<AniListNewRelease> =
        this["media"]?.jsonArray.orEmpty().mapNotNull { node ->
            val obj = node.jsonObject
            val media = obj.toMedia() ?: return@mapNotNull null
            val date = obj["startDate"]?.jsonObject
            val y = date?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val m = date?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val d = date?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (y == null || m == null || d == null) return@mapNotNull null
            val day = runCatching { java.time.LocalDate.of(y, m, d) }.getOrNull() ?: return@mapNotNull null
            AniListNewRelease(media, day)
        }

    /**
     * The first page of several home rows in ONE request (GraphQL aliases), instead of one request per row.
     * Rows whose alias is missing from the answer are left out of the result. If AniList rejects the combined
     * query (e.g. as too complex) the rows are fetched one by one, so a batch can never make Home worse.
     */
    suspend fun getFeedPages(feeds: List<AniListFeed>, perPage: Int): Map<AniListFeed, AniListMediaPage> {
        if (feeds.isEmpty()) return emptyMap()
        if (feeds.size == 1) return getMediaPage(1, perPage, feeds[0].sort, feeds[0].status, feeds[0].formats)
            ?.let { mapOf(feeds[0] to it) }.orEmpty()
        val fields = feeds.mapIndexed { i, feed ->
            "f$i: Page(page: 1, perPage: ${'$'}perPage) { pageInfo { hasNextPage } media(${feedArgs(feed)}) { ...mediaFields } }"
        }.joinToString("\n")
        val gql = "query (${'$'}perPage: Int) {\n$fields\n}\n$MEDIA_FIELDS"
        val data: JsonObject? = try {
            execute(gql, mapOf("perPage" to perPage), cacheTtlMs = FEED_TTL_MS)
        } catch (e: ApiException) {
            if (e.kind != ApiErrorKind.BAD_REQUEST) throw e
            return feeds.mapNotNull { f -> getMediaPage(1, perPage, f.sort, f.status, f.formats)?.let { f to it } }.toMap()
        }
        if (data == null) return emptyMap()
        return feeds.mapIndexedNotNull { i, feed ->
            (data["f$i"] as? JsonObject)?.let { feed to it.toMediaPage() }
        }.toMap()
    }

    /** Series and movie premieres in ONE request; see [getNewReleases] for what each list holds. */
    suspend fun getNewReleasesBatch(
        seriesFormats: List<String>,
        movieFormats: List<String>,
        from: java.time.LocalDate,
        to: java.time.LocalDate,
        perPage: Int,
    ): Pair<List<AniListNewRelease>, List<AniListNewRelease>> {
        val gql = """
            query (${'$'}perPage: Int) {
              series: Page(page: 1, perPage: ${'$'}perPage) { media(${newReleaseArgs(seriesFormats, from, to)}) { ...mediaFields startDate { year month day } } }
              movies: Page(page: 1, perPage: ${'$'}perPage) { media(${newReleaseArgs(movieFormats, from, to)}) { ...mediaFields startDate { year month day } } }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("perPage" to perPage), cacheTtlMs = FEED_TTL_MS) ?: return emptyList<AniListNewRelease>() to emptyList()
        return ((data["series"] as? JsonObject)?.toNewReleases().orEmpty()) to ((data["movies"] as? JsonObject)?.toNewReleases().orEmpty())
    }

    /** Recommendations for several shows in ONE request (an aliased `Media` per id). Missing ids map to an empty list. */
    suspend fun getRecommendationsBatch(mediaIds: List<Int>, perPage: Int = 6): Map<Int, List<AniListMedia>> {
        if (mediaIds.isEmpty()) return emptyMap()
        val fields = mediaIds.mapIndexed { i, id ->
            "r$i: Media(id: $id, type: ANIME) { recommendations(perPage: ${'$'}perPage, sort: RATING_DESC) { nodes { rating mediaRecommendation { ...mediaFields } } } }"
        }.joinToString("\n")
        val gql = "query (${'$'}perPage: Int) {\n$fields\n}\n$MEDIA_FIELDS"
        val data: JsonObject? = try {
            execute(gql, mapOf("perPage" to perPage), cacheTtlMs = DETAILS_TTL_MS)
        } catch (e: ApiException) {
            if (e.kind != ApiErrorKind.BAD_REQUEST) throw e
            return mediaIds.associateWith { getRecommendations(it, perPage) }
        }
        if (data == null) return emptyMap()
        return mediaIds.mapIndexed { i, id ->
            id to (data["r$i"] as? JsonObject)?.get("recommendations")?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
                .mapNotNull { node ->
                    val obj = node.jsonObject
                    val rating = obj["rating"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
                    if (rating < 0) null else obj["mediaRecommendation"]?.jsonObject?.toMedia()
                }
        }.toMap()
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
            add("genre_not_in: [\"Hentai\"]")
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
        val data = execute(gql, mapOf("page" to page, "perPage" to perPage), cacheTtlMs = FEED_TTL_MS) ?: return null
        val pageObj = data["Page"]?.jsonObject ?: return null
        return AniListMediaPage(
            media = pageObj["media"]?.jsonArray.orEmpty().mapNotNull { it.jsonObject.toMedia() },
            hasNextPage = pageObj["pageInfo"]?.jsonObject?.get("hasNextPage")?.jsonPrimitive?.content == "true",
        )
    }

    /**
     * Titles of the given [formats] that premiered between [from] and [to] (both inclusive), newest day first and
     * the most popular first within a day, so what came out today leads. Adult titles are excluded in the query and
     * again by [toMedia]. Empty on a failure or when nothing premiered in the window.
     */
    suspend fun getNewReleases(formats: List<String>, from: java.time.LocalDate, to: java.time.LocalDate, perPage: Int): List<AniListNewRelease>? {
        fun fuzzy(d: java.time.LocalDate) = d.year * 10000 + d.monthValue * 100 + d.dayOfMonth
        // startDate_greater / startDate_lesser are exclusive, so the window is widened by a day on each side.
        val args = listOf(
            "type: ANIME",
            "sort: [START_DATE_DESC, POPULARITY_DESC]",
            "status_in: [RELEASING, FINISHED]",
            "format_in: [${formats.joinToString()}]",
            "startDate_greater: ${fuzzy(from.minusDays(1))}",
            "startDate_lesser: ${fuzzy(to.plusDays(1))}",
            "popularity_greater: 300",
            "isAdult: false",
            "genre_not_in: [\"Hentai\"]",
        ).joinToString(", ")
        val gql = """
            query (${'$'}perPage: Int) {
              Page(page: 1, perPage: ${'$'}perPage) {
                media($args) { ...mediaFields startDate { year month day } }
              }
            }
            $MEDIA_FIELDS
        """.trimIndent()
        val data = execute(gql, mapOf("perPage" to perPage), cacheTtlMs = FEED_TTL_MS) ?: return null
        val nodes = data["Page"]?.jsonObject?.get("media")?.jsonArray ?: return null
        return nodes.mapNotNull { node ->
            val obj = node.jsonObject
            val media = obj.toMedia() ?: return@mapNotNull null
            val date = obj["startDate"]?.jsonObject
            val y = date?.get("year")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val m = date?.get("month")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val d = date?.get("day")?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            if (y == null || m == null || d == null) return@mapNotNull null
            val day = runCatching { java.time.LocalDate.of(y, m, d) }.getOrNull() ?: return@mapNotNull null
            AniListNewRelease(media, day)
        }
    }

    /** Pushes watched-episode progress back to the user's AniList list; called (debounced) as the player advances. */
    suspend fun updateProgress(mediaId: Int, progress: Int): Boolean {
        val mutation = """
            mutation (${'$'}mediaId: Int, ${'$'}progress: Int) {
              SaveMediaListEntry(mediaId: ${'$'}mediaId, progress: ${'$'}progress) { id }
            }
        """.trimIndent()
        return (execute(mutation, mapOf("mediaId" to mediaId, "progress" to progress)) != null).also { if (it) clearCache() }
    }

    /**
     * Everything the CornCastle-style details page shows beyond the basic
     * card: format, characters with their voice actors, staff, and related
     * shows for the "More like this" row.
     */
    /** The MyAnimeList id AniList has for this entry (AniSkip is keyed by it), or null when it has none. */
    suspend fun getMalId(mediaId: Int): Int? = getMediaCore(mediaId)?.malId

    /**
     * The small facts several screens need about a show (MAL id, every title, episode titles and
     * thumbnails) in ONE request. It stays apart from [getMediaDetails] on purpose: that query also
     * carries characters, staff and recommendations, and the episode search must not wait for them.
     */
    suspend fun getMediaCore(mediaId: Int): AniListMediaCore? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                idMal
                title { romaji english native userPreferred }
                synonyms
                streamingEpisodes { title thumbnail }
              }
            }
        """.trimIndent()
        val media = execute(gql, mapOf("id" to mediaId), cacheTtlMs = CORE_TTL_MS)?.get("Media") as? JsonObject ?: return null
        val title = media["title"] as? JsonObject
        val names = listOf("english", "romaji", "userPreferred", "native").mapNotNull {
            (title?.get(it) as? JsonPrimitive)?.contentOrNull
        }
        val synonyms = media["synonyms"]?.jsonArray.orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull }
        val episodes = media["streamingEpisodes"]?.jsonArray.orEmpty().mapNotNull { element ->
            val obj = element.jsonObject
            val episodeTitle = obj["title"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            AniListStreamingEpisode(title = episodeTitle, thumbnailUrl = obj["thumbnail"]?.jsonPrimitive?.contentOrNull)
        }
        return AniListMediaCore(
            malId = (media["idMal"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            titles = (names + synonyms).map { it.trim() }.filter { it.length >= 2 }.distinct(),
            episodes = episodes,
        )
    }

    /** Status, episode count and the next airing time in one tiny request; short cache so a countdown stays honest. */
    suspend fun getAiringInfo(mediaId: Int): AniListAiringInfo? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                status
                episodes
                nextAiringEpisode { episode airingAt }
              }
            }
        """.trimIndent()
        val media = execute(gql, mapOf("id" to mediaId), cacheTtlMs = AIRING_TTL_MS)?.get("Media") as? JsonObject ?: return null
        val next = media["nextAiringEpisode"] as? JsonObject
        return AniListAiringInfo(
            status = (media["status"] as? JsonPrimitive)?.contentOrNull,
            episodes = (media["episodes"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            nextEpisode = (next?.get("episode") as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
            nextAiringAt = (next?.get("airingAt") as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
        )
    }

    /**
     * [getAiringInfo] for many shows in one request (up to 50 per request), keyed by AniList id. A show AniList does
     * not know is simply missing from the map. Used by the repository, which gathers the single lookups the home
     * screen makes (one per Continue Watching card) into one of these.
     */
    suspend fun getAiringInfoBatch(ids: Collection<Int>): Map<Int, AniListAiringInfo> {
        val gql = """
            query (${'$'}ids: [Int]) {
              Page(page: 1, perPage: 50) {
                media(id_in: ${'$'}ids, type: ANIME) {
                  id
                  status
                  episodes
                  nextAiringEpisode { episode airingAt }
                }
              }
            }
        """.trimIndent()
        val result = HashMap<Int, AniListAiringInfo>()
        ids.distinct().chunked(50).forEach { chunk ->
            val data = execute(gql, mapOf("ids" to chunk), cacheTtlMs = AIRING_TTL_MS) ?: return@forEach
            data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().forEach { element ->
                val obj = element.jsonObject
                val id = (obj["id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: return@forEach
                val next = obj["nextAiringEpisode"] as? JsonObject
                result[id] = AniListAiringInfo(
                    status = (obj["status"] as? JsonPrimitive)?.contentOrNull,
                    episodes = (obj["episodes"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                    nextEpisode = (next?.get("episode") as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                    nextAiringAt = (next?.get("airingAt") as? JsonPrimitive)?.contentOrNull?.toLongOrNull(),
                )
            }
        }
        return result
    }

    suspend fun getMediaDetails(mediaId: Int): AniListMediaDetails? {
        val gql = """
            query (${'$'}id: Int) {
              Media(id: ${'$'}id, type: ANIME) {
                id
                title { romaji english }
                synonyms
                coverImage { extraLarge }
                bannerImage
                description(asHtml: false)
                genres
                isAdult
                averageScore
                episodes
                duration
                format
                isFavourite
                mediaListEntry { status }
                status
                nextAiringEpisode { episode }
                trailer { id site }
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
                relations {
                  edges {
                    relationType(version: 2)
                    node { id type title { romaji english } coverImage { extraLarge } bannerImage genres averageScore episodes format startDate { year } isAdult }
                  }
                }
                recommendations(perPage: 8, sort: RATING_DESC) {
                  nodes {
                    mediaRecommendation { id title { romaji english } coverImage { extraLarge } bannerImage genres averageScore episodes startDate { year } isAdult }
                  }
                }
              }
            }
        """.trimIndent()
        val data = execute(gql, mapOf("id" to mediaId), cacheTtlMs = DETAILS_TTL_MS) ?: return null
        val media = data["Media"]?.jsonObject ?: return null
        return media.toMediaDetails()
    }

    /** Every name AniList has for a show (romaji, English, native, synonyms), so sources can be searched under each. */
    suspend fun getAllTitles(mediaId: Int): List<String> = getMediaCore(mediaId)?.titles.orEmpty()

    /** Episode titles and thumbnails AniList knows about for a show; empty when it has none. */
    suspend fun getStreamingEpisodes(mediaId: Int): List<AniListStreamingEpisode> = getMediaCore(mediaId)?.episodes.orEmpty()

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
            val data = try {
                execute(
                    gql,
                    mapOf("from" to fromEpochSeconds.toInt(), "to" to toEpochSeconds.toInt(), "page" to page),
                    cacheTtlMs = SCHEDULE_TTL_MS,
                )
            } catch (e: ApiException) {
                // Keep the pages already fetched; only fail when there is nothing to show.
                if (result.isEmpty()) throw e else null
            } ?: break
            val pageObj = data["Page"]?.jsonObject
            val entries = pageObj?.get("airingSchedules")?.jsonArray.orEmpty()
            entries.mapNotNullTo(result) { entry ->
                val obj = entry.jsonObject
                val media = obj["media"]?.jsonObject?.toMedia() ?: return@mapNotNullTo null
                AniListAiringEntry(
                    airingAt = obj["airingAt"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: return@mapNotNullTo null,
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

    /**
     * Release status and next episode for a set of shows in one request per 50 ids, for the background notification
     * check. Never cached: it exists to notice a change. A failed page is skipped and the rest still returned.
     */
    suspend fun getReleaseInfo(ids: Collection<Int>): List<AniListReleaseInfo> {
        val gql = """
            query (${'$'}ids: [Int]) {
              Page(page: 1, perPage: 50) {
                media(id_in: ${'$'}ids, type: ANIME) {
                  id
                  title { romaji english userPreferred }
                  synonyms
                  coverImage { large }
                  status
                  episodes
                  nextAiringEpisode { episode }
                }
              }
            }
        """.trimIndent()
        val result = mutableListOf<AniListReleaseInfo>()
        ids.distinct().chunked(50).forEach { chunk ->
            val data = try {
                execute(gql, mapOf("ids" to chunk))
            } catch (e: ApiException) {
                null
            } ?: return@forEach
            data["Page"]?.jsonObject?.get("media")?.jsonArray.orEmpty().forEach { element ->
                val obj = element.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@forEach
                val title = pickTitle(obj["title"] as? JsonObject, obj["synonyms"]?.jsonArray) ?: return@forEach
                result += AniListReleaseInfo(
                    id = id,
                    title = title,
                    posterUrl = ((obj["coverImage"] as? JsonObject)?.get("large") as? JsonPrimitive)?.contentOrNull,
                    status = (obj["status"] as? JsonPrimitive)?.contentOrNull ?: return@forEach,
                    episodes = (obj["episodes"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull(),
                    nextEpisode = ((obj["nextAiringEpisode"] as? JsonObject)?.get("episode") as? JsonPrimitive)
                        ?.contentOrNull?.toIntOrNull(),
                )
            }
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
        return (execute(mutation, variables) != null).also { if (it) clearCache() }
    }

    /** Removes the show from the user's list. Succeeds when it was not on the list to begin with. */
    suspend fun removeFromList(mediaId: Int): Boolean {
        val (entryId, _) = getUserEntry(mediaId) ?: return false
        if (entryId == null) return true
        val mutation = """
            mutation (${'$'}id: Int) { DeleteMediaListEntry(id: ${'$'}id) { deleted } }
        """.trimIndent()
        return (execute(mutation, mapOf("id" to entryId)) != null).also { if (it) clearCache() }
    }

    /** Toggles the heart/favourite state on the signed-in user's AniList account for this show. */
    suspend fun toggleFavourite(mediaId: Int): Boolean {
        val mutation = """
            mutation (${'$'}id: Int) { ToggleFavourite(animeId: ${'$'}id) { anime { nodes { id } } } }
        """.trimIndent()
        return (execute(mutation, mapOf("id" to mediaId), idempotent = false) != null).also { if (it) clearCache() }
    }

    /** The newest discussion threads about [mediaId], where its comments are written. */
    suspend fun getMediaThreads(mediaId: Int): List<AniListThread> {
        val query = """
            query (${'$'}mediaId: Int) {
              Page(perPage: 20) {
                threads(mediaCategoryId: ${'$'}mediaId, sort: [REPLIED_AT_DESC]) {
                  id title replyCount isLocked user { name }
                }
              }
            }
        """.trimIndent()
        val data = execute(query, mapOf("mediaId" to mediaId)) ?: return emptyList()
        val threads = data["Page"]?.jsonObject?.get("threads") as? JsonArray ?: return emptyList()
        return threads.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            AniListThread(
                id = obj["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return@mapNotNull null,
                title = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty().ifBlank { "Untitled discussion" },
                replyCount = obj["replyCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                userName = (obj["user"] as? JsonObject)?.get("name")?.jsonPrimitive?.contentOrNull,
                isLocked = obj["isLocked"]?.jsonPrimitive?.content == "true",
            )
        }
    }

    /** One page of a thread's top-level comments, each with its replies nested (never cached: comments change). */
    suspend fun getThreadComments(threadId: Int, page: Int): AniListCommentPage? {
        val query = """
            query (${'$'}threadId: Int, ${'$'}page: Int) {
              Page(page: ${'$'}page, perPage: 25) {
                pageInfo { hasNextPage }
                threadComments(threadId: ${'$'}threadId) {
                  id comment likeCount isLiked createdAt user { id name avatar { medium } } childComments
                }
              }
            }
        """.trimIndent()
        val data = execute(query, mapOf("threadId" to threadId, "page" to page)) ?: return null
        val pageObj = data["Page"]?.jsonObject ?: return null
        val comments = (pageObj["threadComments"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toComment() }
        val hasNext = (pageObj["pageInfo"] as? JsonObject)?.get("hasNextPage")?.jsonPrimitive?.content == "true"
        return AniListCommentPage(comments, hasNext)
    }

    /** Posts [text] to [threadId], as a reply to [parentCommentId] when given. Returns the new comment's id, or null. */
    suspend fun saveThreadComment(threadId: Int, text: String, parentCommentId: Int?): Int? {
        val mutation = """
            mutation (${'$'}threadId: Int, ${'$'}parentCommentId: Int, ${'$'}comment: String) {
              SaveThreadComment(threadId: ${'$'}threadId, parentCommentId: ${'$'}parentCommentId, comment: ${'$'}comment) { id }
            }
        """.trimIndent()
        val variables = mutableMapOf<String, Any?>("threadId" to threadId, "comment" to text)
        if (parentCommentId != null) variables["parentCommentId"] = parentCommentId
        val data = execute(mutation, variables, idempotent = false) ?: return null
        return data["SaveThreadComment"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
    }

    /** Opens a new discussion thread about [mediaId]. Returns its id, or null. */
    suspend fun saveThread(mediaId: Int, title: String, body: String): Int? {
        val mutation = """
            mutation (${'$'}title: String, ${'$'}body: String, ${'$'}categories: [Int], ${'$'}mediaCategories: [Int]) {
              SaveThread(title: ${'$'}title, body: ${'$'}body, categories: ${'$'}categories, mediaCategories: ${'$'}mediaCategories) { id }
            }
        """.trimIndent()
        val data = execute(
            mutation,
            // AniList rejects a thread with no forum category; 2 is "General". The show goes in mediaCategories.
            mapOf("title" to title, "body" to body, "categories" to listOf(2), "mediaCategories" to listOf(mediaId)),
            idempotent = false,
        ) ?: return null
        return data["SaveThread"]?.jsonObject?.get("id")?.jsonPrimitive?.content?.toIntOrNull()
    }

    /** Likes or un-likes a comment as the signed-in user. */
    suspend fun toggleCommentLike(commentId: Int): Boolean {
        val mutation = """
            mutation (${'$'}id: Int) { ToggleLikeV2(id: ${'$'}id, type: THREAD_COMMENT) { __typename } }
        """.trimIndent()
        return execute(mutation, mapOf("id" to commentId), idempotent = false) != null
    }

    /** A comment object (a top-level one from the query, or a nested reply from `childComments`) with its replies. */
    private fun JsonObject.toComment(): AniListComment? {
        val id = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val user = this["user"] as? JsonObject
        return AniListComment(
            id = id,
            userId = user?.get("id")?.jsonPrimitive?.content?.toIntOrNull(),
            userName = user?.get("name")?.jsonPrimitive?.contentOrNull ?: "Unknown",
            avatarUrl = (user?.get("avatar") as? JsonObject)?.get("medium")?.jsonPrimitive?.contentOrNull,
            text = this["comment"]?.jsonPrimitive?.contentOrNull.orEmpty(),
            likeCount = this["likeCount"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            isLiked = this["isLiked"]?.jsonPrimitive?.content == "true",
            createdAt = this["createdAt"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
            replies = (this["childComments"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonObject)?.toComment() },
        )
    }

    /**
     * Sends one GraphQL request and returns its `data` object. Any failure is thrown as an
     * [ApiException] (never returned as null), so callers cannot mistake "the request broke" for
     * "there is nothing to show"; [AniListRepository] catches it and reports it to the error handler.
     *
     * Transient failures (timeouts, dropped connections, HTTP 5xx, and a short HTTP 429 wait) are
     * retried up to [MAX_ATTEMPTS] times. Pass [idempotent] = false for a mutation that is not safe to
     * repeat (a toggle): it is then only retried after a 429, where AniList refused it without acting.
     */
    private suspend fun execute(
        query: String,
        variables: Map<String, Any?>,
        idempotent: Boolean = true,
        cacheTtlMs: Long = 0L,
    ): JsonObject? {
        if (cacheTtlMs <= 0L) return executeUncached(query, variables, idempotent)
        // The signed-in user is part of the key, so logging in or out never shows the other account's data.
        val key = "${authManager.accessToken.value.orEmpty().hashCode()}|$query|${variables.entries.joinToString(",")}"
        freshCached(key, cacheTtlMs)?.let { return it }
        val lock = cacheLocks.getOrPut(key) { Mutex() }
        // One request per key at a time: callers asking for the same thing together share its answer.
        return lock.withLock {
            freshCached(key, cacheTtlMs)?.let { return@withLock it }
            val data = executeUncached(query, variables, idempotent)
            if (data != null) storeCached(key, data)
            data
        }
    }

    private class CachedResponse(val data: JsonObject, val at: Long)

    private val responseCache = java.util.concurrent.ConcurrentHashMap<String, CachedResponse>()
    private val cacheLocks = java.util.concurrent.ConcurrentHashMap<String, Mutex>()

    private fun freshCached(key: String, ttlMs: Long): JsonObject? {
        val hit = responseCache[key] ?: return null
        return if (System.currentTimeMillis() - hit.at <= ttlMs) hit.data else null
    }

    private fun storeCached(key: String, data: JsonObject) {
        if (responseCache.size >= MAX_CACHED_RESPONSES) {
            // Drop the oldest quarter so the cache never grows without bound.
            responseCache.entries.sortedBy { it.value.at }.take(MAX_CACHED_RESPONSES / 4).forEach { responseCache.remove(it.key) }
        }
        responseCache[key] = CachedResponse(data, System.currentTimeMillis())
    }

    /** Forgets every remembered response, so the next screen asks AniList again (after a change or a manual refresh). */
    fun clearCache() {
        responseCache.clear()
    }

    private suspend fun executeUncached(
        query: String,
        variables: Map<String, Any?>,
        idempotent: Boolean = true,
    ): JsonObject? = withContext(Dispatchers.IO) {
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
                        // Lists (e.g. thread categories) must be sent as JSON arrays, not as a quoted string.
                        is List<*> -> value.joinToString(",", "[", "]") { it?.toString() ?: "null" }
                        is Boolean -> value.toString()
                        else -> json.encodeToString(kotlinx.serialization.serializer<String>(), value.toString())
                    }
                    "\"$key\":$jsonValue"
                },
            )
            append("}}")
        }
        executeWithRetry(payload, idempotent)
    }

    /**
     * AniList allows roughly 90 requests a minute (and sometimes only 30 while it is degraded). The home
     * screen fires many catalogue requests at once, so every request goes through this gate: at most
     * [MAX_CONCURRENT_REQUESTS] in flight, started at least [MIN_REQUEST_GAP_MS] apart, and all of them
     * pause together while AniList has asked us to back off. That keeps us under the limit instead of
     * hitting HTTP 429 and showing an "overloaded" message.
     */
    private val requestPermits = Semaphore(MAX_CONCURRENT_REQUESTS)
    private val gateLock = Mutex()
    private var nextSlotAt = 0L

    @Volatile
    private var blockedUntil = 0L

    /** Waits for this request's turn: out of any rate-limit cooldown, and spaced from the previous start. */
    private suspend fun awaitSlot() {
        while (true) {
            val cooldown = blockedUntil - System.currentTimeMillis()
            if (cooldown <= 0L) break
            delay(cooldown)
        }
        val wait = gateLock.withLock {
            val now = System.currentTimeMillis()
            val start = maxOf(now, nextSlotAt)
            nextSlotAt = start + MIN_REQUEST_GAP_MS
            start - now
        }
        if (wait > 0L) delay(wait)
    }

    private fun startCooldown(seconds: Long) {
        val until = System.currentTimeMillis() + seconds * 1000L
        if (until > blockedUntil) blockedUntil = until
    }

    private suspend fun executeWithRetry(payload: String, idempotent: Boolean): JsonObject {
        for (attempt in 1..MAX_RATE_LIMIT_ATTEMPTS) {
            try {
                return requestPermits.withPermit {
                    awaitSlot()
                    executeOnce(payload)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.toApiException()
                val rateLimited = error.kind == ApiErrorKind.RATE_LIMITED
                val mayRepeat = idempotent || rateLimited
                val lastAttempt = attempt >= (if (rateLimited) MAX_RATE_LIMIT_ATTEMPTS else MAX_ATTEMPTS)
                if (lastAttempt || !mayRepeat || !error.isRetryable()) throw error
                if (rateLimited) {
                    // Every other request waits too, so the retries do not pile onto the same limit.
                    val seconds = (error.retryAfterSeconds ?: ApiException.DEFAULT_RETRY_AFTER_SECONDS)
                        .coerceAtLeast(1L) + 1L
                    startCooldown(seconds)
                } else {
                    delay(RETRY_BACKOFF_MS * attempt)
                }
            }
        }
        throw ApiException(ApiErrorKind.UNKNOWN)
    }

    private fun executeOnce(payload: String): JsonObject {
        val requestBuilder = Request.Builder()
            .url(endpoint)
            .post(payload.toRequestBody("application/json".toMediaType()))
        authManager.accessToken.value?.let { token -> requestBuilder.addHeader("Authorization", "Bearer $token") }

        client.newCall(requestBuilder.build()).execute().use { response ->
            val text = response.body.string()
            val root = runCatching { json.parseToJsonElement(text) as? JsonObject }.getOrNull()
            val data = root?.get("data") as? JsonObject
            if (response.isSuccessful && data != null) {
                onNetworkSuccess()
                return data
            }

            // AniList reports GraphQL failures as {"errors":[{"message":"...","status":404}]}.
            val firstError = (root?.get("errors") as? JsonArray)?.firstOrNull() as? JsonObject
            val detail = (firstError?.get("message") as? JsonPrimitive)?.contentOrNull
            val status = (firstError?.get("status") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
            if (response.isSuccessful && root == null) {
                throw ApiException(ApiErrorKind.PARSE, httpCode = response.code, detail = "Response was not JSON")
            }
            val code = if (response.isSuccessful) status ?: 400 else response.code
            throw httpApiException(code, response.header("Retry-After")?.toLongOrNull(), detail)
        }
    }

    /**
     * The title in the chosen language, falling back to the other one when AniList has no such title. Upcoming shows
     * and movies often have no English title yet; for English, a Latin-lettered synonym (AniList lists the official
     * English name there more often than not) is used before the romaji title.
     */
    private fun pickTitle(titleObj: JsonObject?, synonyms: kotlinx.serialization.json.JsonArray? = null): String? {
        fun field(name: String) = (titleObj?.get(name) as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
        return when (titleLanguage()) {
            TitleLanguage.ENGLISH -> field("english") ?: englishSynonym(field("romaji"), synonyms) ?: field("romaji")
            TitleLanguage.ROMAJI -> field("romaji") ?: field("english")
        }
    }

    private fun englishSynonym(romaji: String?, synonyms: kotlinx.serialization.json.JsonArray?): String? =
        synonyms.orEmpty()
            .mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim() }
            .filter { name ->
                name.length >= 4 && name.any { it == ' ' } &&
                    name.all { it.code < 0x250 } && // Latin letters only: no kana, kanji, Cyrillic...
                    !name.equals(romaji, ignoreCase = true) &&
                    !name.any { it.code in 0xC0..0x24F } // accented text is usually a Spanish/French/German title
            }
            .firstOrNull()

    private fun JsonObject.toMedia(): AniListMedia? {
        val idValue = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val title = pickTitle(this["title"] as? JsonObject, this["synonyms"] as? kotlinx.serialization.json.JsonArray) ?: return null
        if (isAdultMedia()) return null
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
            startMonth = this["startDate"]?.jsonObject?.get("month")?.jsonPrimitive?.content?.toIntOrNull(),
            startDay = this["startDate"]?.jsonObject?.get("day")?.jsonPrimitive?.content?.toIntOrNull(),
            format = this["format"]?.jsonPrimitive?.contentOrNull,
            isAdult = this["isAdult"]?.jsonPrimitive?.content == "true",
            altTitles = allTitles(),
            airedEpisodes = (this["nextAiringEpisode"] as? JsonObject)?.get("episode")?.jsonPrimitive?.content?.toIntOrNull()
                ?.let { it - 1 }?.takeIf { it > 0 },
        )
    }

    /** True for an AniList title flagged adult or tagged Hentai; the built-in filter drops these everywhere. */
    private fun JsonObject.isAdultMedia(): Boolean {
        if (this["isAdult"]?.jsonPrimitive?.contentOrNull == "true") return true
        val genres = this["genres"]?.jsonArray?.map { it.jsonPrimitive.content }.orEmpty()
        return ContentFilter.isAdultGenres(genres)
    }

    /** Every name AniList has for the show (romaji, English, native, synonyms), whichever language is displayed. */
    private fun JsonObject.allTitles(): List<String> {
        val titleObj = this["title"] as? JsonObject
        val names = listOf("romaji", "english", "native").mapNotNull { key ->
            (titleObj?.get(key) as? JsonPrimitive)?.contentOrNull
        }
        val synonyms = (this["synonyms"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull {
            (it as? JsonPrimitive)?.contentOrNull
        }
        return (names + synonyms).filter { it.isNotBlank() }.distinct()
    }

    private fun JsonObject.toMediaDetails(): AniListMediaDetails? {
        val idValue = this["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        val title = pickTitle(this["title"] as? JsonObject, this["synonyms"] as? kotlinx.serialization.json.JsonArray) ?: return null
        if (isAdultMedia()) return null

        // Every one of these lists is drawn with the row's own `id` as the Compose key, and a
        // LazyRow/LazyColumn throws `Key ... was already used` when two items share one. AniList
        // can (and does) return the same node twice in `characters`/`staff`/`relations` for a
        // couple of shows, and that made the details page crash as soon as that row scrolled into
        // view. Deduplicating by id here keeps every key unique no matter what the API returns.
        val characterEdges = this["characters"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
        val characters = characterEdges.mapNotNull { edge -> edge.jsonObject.toCharacter() }.distinctBy { it.id }

        val staffEdges = this["staff"]?.jsonObject?.get("edges")?.jsonArray.orEmpty()
        val staff = staffEdges.mapNotNull { edge -> edge.jsonObject.toStaffMember() }.distinctBy { it.id }

        val recommendationNodes = this["recommendations"]?.jsonObject?.get("nodes")?.jsonArray.orEmpty()
        val related = recommendationNodes.mapNotNull { node ->
            node.jsonObject["mediaRecommendation"]?.jsonObject?.toMedia()
        }.distinctBy { it.id }

        val franchise = this["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty().mapNotNull { edge ->
            val obj = edge.jsonObject
            val node = obj["node"]?.jsonObject ?: return@mapNotNull null
            if (node["type"]?.jsonPrimitive?.contentOrNull != "ANIME") return@mapNotNull null
            val type = obj["relationType"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            val order = FRANCHISE_RELATIONS.indexOf(type).takeIf { it >= 0 } ?: return@mapNotNull null
            val media = node.toMedia() ?: return@mapNotNull null
            order to AniListRelation(media = media, label = relationLabel(type, media.format, media.title))
        }.sortedWith(compareBy({ it.first }, { it.second.media.year ?: Int.MAX_VALUE })).map { it.second }.distinctBy { it.media.id }

        val hasPrequel = this["relations"]?.jsonObject?.get("edges")?.jsonArray.orEmpty().any { edge ->
            val obj = edge.jsonObject
            obj["relationType"]?.jsonPrimitive?.contentOrNull == "PREQUEL" &&
                obj["node"]?.jsonObject?.get("type")?.jsonPrimitive?.contentOrNull == "ANIME"
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
            hasPrequel = hasPrequel,
            franchise = franchise,
            listStatus = (this["mediaListEntry"] as? JsonObject)?.get("status")?.jsonPrimitive?.contentOrNull,
            status = this["status"]?.jsonPrimitive?.contentOrNull,
            nextAiringEpisode = (this["nextAiringEpisode"] as? JsonObject)?.get("episode")?.jsonPrimitive?.content?.toIntOrNull(),
            trailerYoutubeId = (this["trailer"] as? JsonObject)?.takeIf {
                it["site"]?.jsonPrimitive?.contentOrNull.equals("youtube", ignoreCase = true)
            }?.get("id")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() },
            durationMinutes = this["duration"]?.jsonPrimitive?.content?.toIntOrNull(),
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

        // How long a read answer is reused before AniList is asked again. Anything the user changes
        // (list status, progress, favourite) clears the whole cache at once, so these only bound how
        // stale data changed elsewhere (the AniList website) can look.
        private const val FEED_TTL_MS = 10 * 60_000L
        private const val SEARCH_TTL_MS = 5 * 60_000L
        private const val DETAILS_TTL_MS = 10 * 60_000L
        private const val CORE_TTL_MS = 30 * 60_000L
        private const val AIRING_TTL_MS = 5 * 60_000L
        private const val LIST_TTL_MS = 2 * 60_000L
        private const val SCHEDULE_TTL_MS = 15 * 60_000L
        private const val MAX_CACHED_RESPONSES = 120
        private const val MAX_LIST_CHUNKS = 6

        /** AniList relation types that belong to the same show/franchise, in display order. */
        private val FRANCHISE_RELATIONS = listOf(
            "PARENT", "PREQUEL", "SEQUEL", "SIDE_STORY", "SPIN_OFF", "ALTERNATIVE", "SUMMARY", "COMPILATION", "CONTAINS",
        )

        private val SEASON_PATTERNS = listOf(
            Regex("""(\d+)(?:st|nd|rd|th)\s+Season""", RegexOption.IGNORE_CASE),
            Regex("""Season\s+(\d+)""", RegexOption.IGNORE_CASE),
        )

        /** "Season 2" when the title says so ("... 2nd Season", "... Season 3"), otherwise how it relates ("Sequel", "Prequel · Movie"). */
        private fun relationLabel(type: String, format: String?, title: String): String {
            if (format == "TV") {
                SEASON_PATTERNS.firstNotNullOfOrNull { it.find(title)?.groupValues?.get(1) }?.let { return "Season $it" }
            }
            return relationTypeLabel(type, format)
        }

        private fun relationTypeLabel(type: String, format: String?): String {
            val kind = when (format) {
                "MOVIE" -> "Movie"
                "OVA" -> "OVA"
                "ONA" -> "ONA"
                "SPECIAL" -> "Special"
                else -> null
            }
            val relation = when (type) {
                "PARENT" -> "Main Story"
                "PREQUEL" -> "Prequel"
                "SEQUEL" -> "Sequel"
                "SIDE_STORY" -> "Side Story"
                "SPIN_OFF" -> "Spin-off"
                "ALTERNATIVE" -> "Alternative"
                "SUMMARY" -> "Recap"
                "COMPILATION" -> "Compilation"
                else -> "Related"
            }
            return if (kind != null && relation != "Recap") "$relation \u00b7 $kind" else relation
        }
        private const val MAX_ATTEMPTS = 3
        private const val MAX_RATE_LIMIT_ATTEMPTS = 5
        private const val MAX_CONCURRENT_REQUESTS = 2
        private const val MIN_REQUEST_GAP_MS = 350L
        private const val RETRY_BACKOFF_MS = 400L

        private const val MEDIA_FIELDS = """
            fragment mediaFields on Media {
              id
              title { romaji english native }
              synonyms
              coverImage { extraLarge }
              bannerImage
              description(asHtml: false)
              genres
              averageScore
              episodes
              startDate { year month day }
              format
              isAdult
              nextAiringEpisode { episode }
            }
        """
    }
}
