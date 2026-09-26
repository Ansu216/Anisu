package com.kernel.anime.ui.details

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kernel.anime.addon.AddonManager
import com.kernel.anime.core.model.MediaOrigin
import com.kernel.anime.core.model.SAnime
import com.kernel.anime.core.model.SEpisode
import com.kernel.anime.core.util.SelectionHolder
import com.kernel.anime.extension.ExtensionManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DetailsViewModel(
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    selectionHolder: SelectionHolder,
) : ViewModel() {

    val anime: StateFlow<SAnime?> = selectionHolder.currentAnime

    private val _episodes = MutableStateFlow<List<SEpisode>>(emptyList())
    val episodes: StateFlow<List<SEpisode>> = _episodes

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        val current = anime.value
        if (current == null) {
            _isLoading.value = false
            _error.value = "Nothing selected"
        } else {
            viewModelScope.launch {
                _episodes.value = runCatching { loadEpisodes(current) }.getOrElse {
                    _error.value = it.message ?: "Failed to load episodes"
                    emptyList()
                }
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadEpisodes(anime: SAnime): List<SEpisode> {
        return when (val origin = anime.origin) {
            is MediaOrigin.Extension ->
                extensionManager.getSource(origin.sourceId)?.getEpisodeList(anime).orEmpty()

            is MediaOrigin.Addon -> {
                val addon = addonManager.installedAddons.first().find { it.id == origin.addonId } ?: return emptyList()
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
}
