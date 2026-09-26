package com.kernel.anime.core.util

import com.kernel.anime.core.model.SAnime
import com.kernel.anime.core.model.SEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Navigation Compose passes string/primitive arguments well but is awkward
 * for the sealed [com.kernel.anime.core.model.MediaOrigin] hierarchy we carry
 * around with an [SAnime]. Since this is a single-activity app, a small
 * shared holder for "what's currently selected" is simpler and just as
 * correct as round-tripping everything through a nav-arg codec.
 */
class SelectionHolder {
    private val _currentAnime = MutableStateFlow<SAnime?>(null)
    val currentAnime: StateFlow<SAnime?> = _currentAnime

    private val _currentEpisode = MutableStateFlow<SEpisode?>(null)
    val currentEpisode: StateFlow<SEpisode?> = _currentEpisode

    fun selectAnime(anime: SAnime) {
        _currentAnime.value = anime
    }

    fun selectEpisode(episode: SEpisode) {
        _currentEpisode.value = episode
    }
}
