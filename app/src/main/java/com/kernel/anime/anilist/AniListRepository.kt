package com.kernel.anime.anilist

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
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val _viewer = MutableStateFlow<AniListViewer?>(null)
    val viewer: StateFlow<AniListViewer?> = _viewer

    val isLoggedIn: StateFlow<Boolean> = authManager.accessToken
        .map { it != null }
        .stateIn(scope, kotlinx.coroutines.flow.SharingStarted.Eagerly, authManager.isLoggedIn)

    init {
        scope.launch { refreshViewer() }
    }

    suspend fun refreshViewer() {
        if (authManager.isLoggedIn) {
            _viewer.value = runCatching { api.getViewer() }.getOrNull()
        } else {
            _viewer.value = null
        }
    }

    suspend fun getCurrentlyWatching(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return runCatching { api.getMediaListCollection(userId, "CURRENT") }.getOrDefault(emptyList())
    }

    suspend fun getPlanning(): List<AniListMediaListEntry> {
        val userId = _viewer.value?.id ?: return emptyList()
        return runCatching { api.getMediaListCollection(userId, "PLANNING") }.getOrDefault(emptyList())
    }

    suspend fun getTrending(page: Int = 1) = runCatching { api.getTrending(page) }.getOrDefault(emptyList())

    suspend fun search(query: String, page: Int = 1) = runCatching { api.searchMedia(query, page) }.getOrDefault(emptyList())

    suspend fun reportProgress(mediaId: Int, episode: Int) {
        if (authManager.isLoggedIn) runCatching { api.updateProgress(mediaId, episode) }
    }

    fun login() = authManager.launchLogin()

    fun logout() {
        authManager.logout()
        _viewer.value = null
    }
}
