package com.ansu.anime.anilist

import com.ansu.anime.core.net.ApiErrorHandler
import com.ansu.anime.core.net.toApiException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AniListRepository(
    private val api: AniListApi,
    val authManager: AniListAuthManager,
    private val errors: ApiErrorHandler,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _viewer = MutableStateFlow<AniListViewer?>(null)
    val viewer: StateFlow<AniListViewer?> = _viewer

    val isLoggedIn: StateFlow<Boolean> = authManager.accessToken
        .map { it != null }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, authManager.isLoggedIn)

    /**
     * Runs one API call. A failure is reported to [errors] (log, plus a snackbar unless [quiet]) and
     * [fallback] is returned so the screen shows its empty state instead of crashing. Cancellation
     * is rethrown; swallowing it would keep a cancelled coroutine running.
     */
    private suspend fun <T> guarded(what: String, fallback: T, quiet: Boolean = false, block: suspend () -> T): T =
        try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val apiError = e.toApiException()
            if (errors.isAmbiguous(apiError) && probeSaysDown()) {
                errors.confirmOutage(apiError)
            }
            errors.report(what, apiError, quiet, tracksOutage = true)
            fallback
        }

    private companion object {
        const val PROBE_REUSE_MS = 15_000L
    }

    private var lastProbeAt = 0L
    private var lastProbeDown = false
    private val probeLock = kotlinx.coroutines.sync.Mutex()

    /**
     * Sends one token-free request to find out whether AniList itself is down. Several rows failing at
     * once share one probe (the answer is reused for [PROBE_REUSE_MS]), so an outage costs one request,
     * not one per row.
     */
    private suspend fun probeSaysDown(): Boolean = probeLock.withLock {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastProbeAt < PROBE_REUSE_MS) return@withLock lastProbeDown
        val down = try {
            api.pingAnonymous()
            false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            true
        }
        lastProbeAt = android.os.SystemClock.elapsedRealtime()
        lastProbeDown = down
        down
    }

    /** Asks AniList for the smallest possible answer: once it replies, the "AniList is down" card goes away by itself. */
    suspend fun checkReachable() {
        guarded("Checking AniList", Unit, quiet = true) { api.ping() }
    }

    init {
        scope.launch { refreshViewer() }
    }

    /**
     * Keeps the signed-in user's lists in the response cache for as long as it is running (the app calls it
     * only while it is on screen). All six lists and the favourites are fetched at once when the app opens,
     * then again every [LIST_REFRESH_MS], a little inside the cache's lifetime, so My Space and the home
     * banner's hearts always find their lists ready instead of waiting on the network. Signing out stops it;
     * signing in starts it. The device's own lists need none of this: they are read from the local database.
     */
    suspend fun keepListsWarm() {
        isLoggedIn.collectLatest { loggedIn ->
            if (!loggedIn) return@collectLatest
            viewer.filterNotNull().first()
            while (true) {
                warmLists()
                delay(LIST_REFRESH_MS)
            }
        }
    }

    private suspend fun warmLists() {
        val userId = _viewer.value?.id ?: return
        // One after the other, so the refresh stays far below AniList's request limit.
        for (status in listOf("CURRENT", "PLANNING", "COMPLETED", "PAUSED", "DROPPED", "REPEATING")) {
            guarded("Refreshing your $status list", Unit, quiet = true) { api.getMediaListCollection(userId, status); Unit }
        }
        guarded("Refreshing your favourites", Unit, quiet = true) { api.getFavouriteAnime(userId); Unit }
    }

    suspend fun refreshViewer() {
        if (authManager.isLoggedIn) {
            _viewer.value = guarded("Loading your AniList profile", null, quiet = true) { api.getViewer() }
        } else {
            _viewer.value = null
        }
    }

    suspend fun getCurrentlyWatching(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your Watching list", emptyList()) { api.getMediaListCollection(userId, "CURRENT") }
    }

    suspend fun getPlanning(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your Planning list", emptyList()) { api.getMediaListCollection(userId, "PLANNING") }
    }

    suspend fun getCompleted(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your Completed list", emptyList()) { api.getMediaListCollection(userId, "COMPLETED") }
    }

    private val malIds = java.util.concurrent.ConcurrentHashMap<Int, Int>()

    /** MyAnimeList id for an AniList entry (cached), or null when AniList has none or the lookup failed. */
    suspend fun getMalId(anilistId: Int): Int? {
        malIds[anilistId]?.let { return it }
        val id = guarded<Int?>("Looking up MyAnimeList id", null, quiet = true) { api.getMalId(anilistId) }
        if (id != null) malIds[anilistId] = id
        return id
    }

    suspend fun getPaused(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your Paused list", emptyList()) { api.getMediaListCollection(userId, "PAUSED") }
    }

    suspend fun getDropped(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your Dropped list", emptyList()) { api.getMediaListCollection(userId, "DROPPED") }
    }

    suspend fun getRepeating(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your Repeating list", emptyList()) { api.getMediaListCollection(userId, "REPEATING") }
    }

    /** AniList ids of every favourite (all pages). Waits for the profile, which is still loading right after launch. */
    suspend fun getLikedIds(): Set<Int> {
        val userId = _viewer.value?.id
            ?: kotlinx.coroutines.withTimeoutOrNull(15_000L) { viewer.filterNotNull().first().id }
            ?: return emptySet()
        return guarded("Loading your favourites", emptySet<Int>(), quiet = true) { api.getFavouriteIds(userId) }
    }

    suspend fun getLiked(): List<AniListMedia> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your favourites", emptyList()) { api.getFavouriteAnime(userId) }
    }

    suspend fun getTrending(page: Int = 1) = guarded("Loading trending anime", emptyList()) { api.getTrending(page) }

    /** Titles AniList users recommend after [mediaId]; empty when there are none or the request failed. */
    suspend fun getRecommendations(mediaId: Int): List<AniListMedia> =
        guarded("Loading recommendations", emptyList<AniListMedia>(), quiet = true) { api.getRecommendations(mediaId) }

    /** Most popular anime of the current season, for the home screen's "Top Picks" grid. */
    suspend fun getTopThisSeason(season: String, seasonYear: Int) =
        guarded("Loading this season's top picks", emptyList()) { api.getTopThisSeason(season, seasonYear) }

    /** First page of several home rows in one request. Rows missing from the map failed to load. */
    suspend fun getFeedFirstPages(feeds: List<AniListFeed>, perPage: Int): Map<AniListFeed, AniListMediaPage> =
        guarded("Loading catalogues", emptyMap<AniListFeed, AniListMediaPage>()) { api.getFeedPages(feeds, perPage) }

    /** Recent series and movie premieres in one request, each ordered and trimmed like [getNewReleases]. */
    suspend fun getNewReleasesBoth(days: Long = 30, limit: Int = 7): Pair<List<AniListNewRelease>, List<AniListNewRelease>> {
        val today = java.time.LocalDate.now()
        val (a, m) = guarded("Loading new releases", emptyList<AniListNewRelease>() to emptyList<AniListNewRelease>(), quiet = true) {
            api.getNewReleasesBatch(listOf("TV", "TV_SHORT", "ONA"), listOf("MOVIE"), today.minusDays(days), today, perPage = 25)
        }
        fun tidy(l: List<AniListNewRelease>) = l.sortedByDescending { it.releasedOn }.distinctBy { it.media.id }.take(limit)
        return tidy(a) to tidy(m)
    }

    /** Recommendations for several shows in one request. */
    suspend fun getRecommendationsFor(mediaIds: List<Int>): Map<Int, List<AniListMedia>> =
        guarded("Loading recommendations", emptyMap<Int, List<AniListMedia>>(), quiet = true) { api.getRecommendationsBatch(mediaIds) }

    /** One page of a home-screen feed; null means the request failed (as opposed to an empty page). */
    suspend fun getFeedPage(feed: AniListFeed, page: Int, perPage: Int): AniListMediaPage? =
        guarded<AniListMediaPage?>("Loading ${feed.title}", null) { api.getMediaPage(page, perPage, feed.sort, feed.status, feed.formats) }

    /**
     * What premiered in the last [days] days in the given [formats]: today's titles first, then yesterday's and so
     * on, at most [limit]. Empty when nothing did or the request failed.
     */
    suspend fun getNewReleases(formats: List<String>, days: Long = 30, limit: Int = 7): List<AniListNewRelease> {
        val today = java.time.LocalDate.now()
        val found = guarded<List<AniListNewRelease>?>("Loading new releases", null, quiet = true) {
            api.getNewReleases(formats, today.minusDays(days), today, perPage = 25)
        }.orEmpty()
        // Already ordered by the query; sorting again keeps today first even if AniList's tie-breaking changes.
        return found.sortedByDescending { it.releasedOn }.distinctBy { it.media.id }.take(limit)
    }

    suspend fun getAllTitles(mediaId: Int): List<String> = guarded("Loading alternate titles", emptyList<String>(), quiet = true) { api.getAllTitles(mediaId) }

    private class CachedAiring(val info: AniListAiringInfo, val at: Long)

    private val airingCache = java.util.concurrent.ConcurrentHashMap<Int, CachedAiring>()
    private val airingLock = Mutex()
    private val airingQueue = LinkedHashMap<Int, CompletableDeferred<AniListAiringInfo?>>()
    private var airingFlushScheduled = false

    /**
     * Null when the request failed, so callers can tell "no data" from "nothing airing".
     *
     * Lookups made within a few milliseconds of each other are sent as ONE request (the home screen asks once
     * per Continue Watching card, all at the same moment), and an answer is reused for [AIRING_TTL_MS]. Only the
     * request that actually goes out reports a failure, so ten waiting cards are one failed call, not ten.
     */
    suspend fun getAiringInfo(mediaId: Int): AniListAiringInfo? {
        airingCache[mediaId]?.takeIf { System.currentTimeMillis() - it.at <= AIRING_TTL_MS }?.let { return it.info }
        val waiter = airingLock.withLock {
            val pending = airingQueue.getOrPut(mediaId) { CompletableDeferred() }
            if (!airingFlushScheduled) {
                airingFlushScheduled = true
                scope.launch {
                    delay(AIRING_BATCH_WINDOW_MS)
                    flushAiring()
                }
            }
            pending
        }
        return waiter.await()
    }

    private suspend fun flushAiring() {
        val batch = airingLock.withLock {
            LinkedHashMap(airingQueue).also {
                airingQueue.clear()
                airingFlushScheduled = false
            }
        }
        if (batch.isEmpty()) return
        val answer = guarded<Map<Int, AniListAiringInfo>?>("Loading airing info", null, quiet = true) {
            api.getAiringInfoBatch(batch.keys)
        }
        val now = System.currentTimeMillis()
        batch.forEach { (id, waiter) ->
            val info = answer?.get(id)
            if (info != null) airingCache[id] = CachedAiring(info, now)
            waiter.complete(info)
        }
    }

    suspend fun getStreamingEpisodes(mediaId: Int) = guarded("Loading episode titles", emptyList(), quiet = true) { api.getStreamingEpisodes(mediaId) }

    suspend fun search(query: String, page: Int = 1) = guarded("Searching", emptyList()) { api.searchMedia(query, page) }

    /**
     * Candidates for several titles at once (a few combined requests instead of one per title), keyed by the
     * title as asked. Empty when AniList could not be asked at all.
     */
    suspend fun searchTitlesBatch(titles: List<String>): Map<String, List<AniListMedia>> =
        guarded("Searching", emptyMap<String, List<AniListMedia>>(), quiet = true) { api.searchMediaBatch(titles) }

    /** One page of the Search screen's query with filters; null means the request failed. */
    suspend fun searchPage(query: String, filters: AniListSearchFilters, page: Int): AniListMediaPage? =
        guarded<AniListMediaPage?>("Searching", null) { api.searchMediaPage(query, filters, page) }

    suspend fun reportProgress(mediaId: Int, episode: Int) {
        if (authManager.isLoggedIn) guarded("Saving watch progress", false, quiet = true) { api.updateProgress(mediaId, episode) }
    }

    /** Full CornCastle-style details: stats, characters, staff, related shows. Public data - works logged out too. */
    suspend fun getMediaDetails(mediaId: Int): AniListMediaDetails? = guarded<AniListMediaDetails?>("Loading title details", null) { api.getMediaDetails(mediaId) }

    /** How many AniList requests this session sent and how many answers came from a cache; for the diagnostics log. */
    fun requestSummary(): String = api.requestSummary()

    suspend fun toggleFavourite(mediaId: Int): Boolean {
        if (!authManager.isLoggedIn) return false
        return guarded("Updating your favourites", false) { api.toggleFavourite(mediaId) }
    }

    /** The discussion threads about a show; null when the request failed (as opposed to "there are none"). */
    suspend fun getMediaThreads(mediaId: Int): List<AniListThread>? =
        guarded<List<AniListThread>?>("Loading discussions", null) { api.getMediaThreads(mediaId) }

    /** One page of a thread's comments; null when the request failed. */
    suspend fun getThreadComments(threadId: Int, page: Int = 1): AniListCommentPage? =
        guarded<AniListCommentPage?>("Loading comments", null) { api.getThreadComments(threadId, page) }

    /** Posts a comment (or a reply); the new comment's id, or null when signed out or AniList refused it. */
    suspend fun postComment(threadId: Int, text: String, parentCommentId: Int? = null): Int? {
        if (!authManager.isLoggedIn) return null
        return guarded<Int?>("Posting your comment", null) { api.saveThreadComment(threadId, text, parentCommentId) }
    }

    /** Opens a discussion thread about a show; its id, or null when signed out or AniList refused it. */
    suspend fun startThread(mediaId: Int, title: String, body: String): Int? {
        if (!authManager.isLoggedIn) return null
        return guarded<Int?>("Starting the discussion", null) { api.saveThread(mediaId, title, body) }
    }

    suspend fun toggleCommentLike(commentId: Int): Boolean {
        if (!authManager.isLoggedIn) return false
        return guarded("Updating your like", false) { api.toggleCommentLike(commentId) }
    }

    /** The user's heart and list entry for a show; null when signed out or the request failed. */
    suspend fun getUserState(mediaId: Int): AniListUserState? {
        if (!authManager.isLoggedIn) return null
        return guarded<AniListUserState?>("Loading your list status", null, quiet = true) { api.getUserState(mediaId) }
    }

    /** Puts the show in an AniList list ([status] = CURRENT/PLANNING/COMPLETED), or removes it when null. */
    suspend fun setListStatus(mediaId: Int, status: String?, progress: Int? = null): Boolean {
        if (!authManager.isLoggedIn) return false
        return guarded("Updating your list", false) {
            if (status == null) api.removeFromList(mediaId) else api.saveListStatus(mediaId, status, progress)
        }
    }

    /** Episodes airing in the given window — public data, works logged out too. */
    suspend fun weeklySchedule(fromEpochSeconds: Long, toEpochSeconds: Long): List<AniListAiringEntry> =
        guarded("Loading the schedule", emptyList()) { api.getAiringSchedule(fromEpochSeconds, toEpochSeconds) }

    fun login() = authManager.launchLogin()

    fun logout() {
        authManager.logout()
        _viewer.value = null
    }
}

/** How often the warmed lists are fetched again; shorter than the API cache's two-minute lifetime. */
private const val LIST_REFRESH_MS = 90_000L

/** How long single airing lookups are gathered into one request, and how long an answer is reused. */
private const val AIRING_BATCH_WINDOW_MS = 40L
private const val AIRING_TTL_MS = 5 * 60_000L
