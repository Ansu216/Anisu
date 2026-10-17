package com.ansu.anime.anilist

import com.ansu.anime.core.net.ApiErrorHandler
import kotlinx.coroutines.CancellationException
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
            errors.report(what, e, quiet)
            fallback
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

    /** One page of a home-screen feed; null means the request failed (as opposed to an empty page). */
    suspend fun getFeedPage(feed: AniListFeed, page: Int, perPage: Int): AniListMediaPage? =
        guarded<AniListMediaPage?>("Loading ${feed.title}", null) { api.getMediaPage(page, perPage, feed.sort, feed.status, feed.formats) }

    suspend fun getAllTitles(mediaId: Int): List<String> = guarded("Loading alternate titles", emptyList<String>(), quiet = true) { api.getAllTitles(mediaId) }

    suspend fun getStreamingEpisodes(mediaId: Int) = guarded("Loading episode titles", emptyList(), quiet = true) { api.getStreamingEpisodes(mediaId) }

    suspend fun search(query: String, page: Int = 1) = guarded("Searching", emptyList()) { api.searchMedia(query, page) }

    /** One page of the Search screen's query with filters; null means the request failed. */
    suspend fun searchPage(query: String, filters: AniListSearchFilters, page: Int): AniListMediaPage? =
        guarded<AniListMediaPage?>("Searching", null) { api.searchMediaPage(query, filters, page) }

    suspend fun reportProgress(mediaId: Int, episode: Int) {
        if (authManager.isLoggedIn) guarded("Saving watch progress", false, quiet = true) { api.updateProgress(mediaId, episode) }
    }

    /** Full CornCastle-style details: stats, characters, staff, related shows. Public data - works logged out too. */
    suspend fun getMediaDetails(mediaId: Int): AniListMediaDetails? = guarded<AniListMediaDetails?>("Loading title details", null) { api.getMediaDetails(mediaId) }

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
