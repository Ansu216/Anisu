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
    val error: String? = null,
)

class DetailsViewModel(
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val aniListRepository: AniListRepository,
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
            viewModelScope.launch {
                val episodesDeferred = async { runCatching { loadEpisodes(current) }.getOrDefault(emptyList()) }
                val detailsDeferred = async {
                    current.anilistId?.let { id -> aniListRepository.getMediaDetails(id) }
                }
                val episodes = episodesDeferred.await()
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
                    error = if (episodes.isEmpty() && details == null) "Couldn't load this title" else null,
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
}
