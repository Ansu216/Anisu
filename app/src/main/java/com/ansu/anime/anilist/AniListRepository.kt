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

    suspend fun getLiked(): List<AniListMedia> {
        val userId = _viewer.value?.id ?: return emptyList()
        return guarded("Loading your favourites", emptyList()) { api.getFavouriteAnime(userId) }
    }

    suspend fun getTrending(page: Int = 1) = guarded("Loading trending anime", emptyList()) { api.getTrending(page) }

    /** Most popular anime of the current season, for the home screen's "Top Picks" grid. */
    suspend fun getTopThisSeason(season: String, seasonYear: Int) =
        guarded("Loading this season's top picks", emptyList()) { api.getTopThisSeason(season, seasonYear) }

    /** One page of a home-screen feed; null means the request failed (as opposed to an empty page). */
    suspend fun getFeedPage(feed: AniListFeed, page: Int, perPage: Int): AniListMediaPage? =
        guarded<AniListMediaPage?>("Loading ${feed.title}", null) { api.getMediaPage(page, perPage, feed.sort, feed.status, feed.formats) }

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
