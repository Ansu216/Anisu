package com.ansu.anime.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.addon.AddonManager
import com.ansu.anime.anilist.AniListMediaDetails
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.data.repository.EpisodeMetadataRepository
import com.ansu.anime.data.repository.LocalListRepository
import com.ansu.anime.extension.ExtensionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

data class DetailsUiState(
    val isLoading: Boolean = true,
    val episodes: List<SEpisode> = emptyList(),
    val aniListDetails: AniListMediaDetails? = null,
    val isFavourite: Boolean = false,
    /** Local (signed-out) library state for this show. */
    val localFavourite: Boolean = false,
    val listStatus: String? = null,
    /** AniList list status for this show while signed in. */
    val remoteListStatus: String? = null,
    val isLoggedIn: Boolean = false,
    val error: String? = null,
) {
    /** The heart to draw: AniList's when signed in, the on-device one otherwise. */
    val liked: Boolean get() = if (isLoggedIn) isFavourite else localFavourite

    /** The list to show as selected: AniList's when signed in, the on-device one otherwise. */
    val shownListStatus: String? get() = if (isLoggedIn) remoteListStatus else listStatus
}

class DetailsViewModel(
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val aniListRepository: AniListRepository,
    private val episodeMetadataRepository: EpisodeMetadataRepository,
    private val localListRepository: LocalListRepository,
    selectionHolder: SelectionHolder,
) : ViewModel() {

    val anime: StateFlow<SAnime?> = selectionHolder.currentAnime

    private val _uiState = MutableStateFlow(DetailsUiState())
    val uiState: StateFlow<DetailsUiState> = _uiState

    init {
        val current = anime.value
        if (current == null) {
            _uiState.value = _uiState.value.copy(isLoading = false, error = "Nothing selected")
        } else {
            observeLocalLibrary(current)
            viewModelScope.launch {
                val episodesDeferred = async { runCatching { loadEpisodes(current) }.getOrDefault(emptyList()) }
                val metaDeferred = async {
                    current.anilistId?.let { id -> episodeMetadataRepository.getEpisodeMeta(id) }.orEmpty()
                }
                val detailsDeferred = async {
                    current.anilistId?.let { id -> aniListRepository.getMediaDetails(id) }
                }
                val meta = metaDeferred.await()
                val episodes = episodesDeferred.await().map { episode ->
                    val number = episode.episodeNumber.takeIf { it % 1f == 0f }?.toInt()
                    val info = number?.let { meta[it] } ?: return@map episode
                    episode.copy(
                        name = info.title ?: episode.name,
                        thumbnailUrl = episode.thumbnailUrl ?: info.thumbnailUrl,
                        description = info.overview ?: episode.description,
                        airDate = episode.airDate ?: info.airDate,
                    )
                }
                val details = try {
                    detailsDeferred.await()
                } catch (e: Exception) {
                    null
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    episodes = episodes,
                    aniListDetails = details,
                    isFavourite = details?.isFavourite ?: false,
                    remoteListStatus = details?.listStatus,
                    error = if (episodes.isEmpty() && details == null) "Couldn't load this title" else null,
                )
            }
        }
    }

    private fun observeLocalLibrary(current: SAnime) {
        viewModelScope.launch {
            aniListRepository.isLoggedIn.collect { loggedIn ->
                _uiState.value = _uiState.value.copy(isLoggedIn = loggedIn)
            }
        }
        val id = current.anilistId ?: return
        viewModelScope.launch {
            localListRepository.observe(id).collect { row ->
                _uiState.value = _uiState.value.copy(
                    localFavourite = row?.isFavourite ?: false,
                    listStatus = row?.status,
                )
            }
        }
    }

    private suspend fun loadEpisodes(anime: SAnime): List<SEpisode> = coroutineScope {
        when (val origin = anime.origin) {
            is MediaOrigin.Extension ->
                extensionManager.getSource(origin.sourceId)?.getEpisodeList(anime).orEmpty()

            is MediaOrigin.Addon -> {
                val addon = addonManager.installedAddons.first().find { it.id == origin.addonId } ?: return@coroutineScope emptyList()
                val meta = addonManager.getMeta(addon, origin.type, origin.stremioId)
                meta?.videos.orEmpty().map { video ->
                    SEpisode(
                        id = video.id,
                        name = video.title ?: "Episode ${video.episode ?: "?"}",
                        episodeNumber = (video.episode ?: 0).toFloat(),
                        thumbnailUrl = video.thumbnail,
                    )
                }
            }
        }
    }

    fun toggleFavourite() {
        val id = anime.value?.anilistId ?: return
        if (!aniListRepository.isLoggedIn.value) {
            // Signed out: the heart lives on the device (the row flow above updates the UI).
            val current = anime.value ?: return
            viewModelScope.launch {
                localListRepository.toggleFavourite(current, _uiState.value.aniListDetails?.episodes)
            }
            return
        }
        val optimistic = !_uiState.value.isFavourite
        _uiState.value = _uiState.value.copy(isFavourite = optimistic)
        viewModelScope.launch {
            val success = aniListRepository.toggleFavourite(id)
            if (!success) {
                // Revert if the call failed (e.g. not logged in) rather than leave a false heart state.
                _uiState.value = _uiState.value.copy(isFavourite = !optimistic)
            }
        }
    }

    /** Puts the show in a list (on AniList when signed in, on the device otherwise), or removes it when [status] is null. */
    fun setListStatus(status: String?) {
        val current = anime.value ?: return
        val id = current.anilistId ?: return
        if (!aniListRepository.isLoggedIn.value) {
            viewModelScope.launch {
                localListRepository.setStatus(current, status, _uiState.value.aniListDetails?.episodes)
            }
            return
        }
        val previous = _uiState.value.remoteListStatus
        _uiState.value = _uiState.value.copy(remoteListStatus = status)
        viewModelScope.launch {
            if (!aniListRepository.setListStatus(id, status)) {
                // Revert rather than show a list the account does not actually have.
                _uiState.value = _uiState.value.copy(remoteListStatus = previous)
            }
        }
    }
}
