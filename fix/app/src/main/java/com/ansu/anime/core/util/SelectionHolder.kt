package com.ansu.anime.core.util

import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Navigation Compose passes string/primitive arguments well but is awkward
 * for the sealed [com.ansu.anime.core.model.MediaOrigin] hierarchy we carry
 * around with an [SAnime]. Since this is a single-activity app, a small
 * shared holder for "what's currently selected" is simpler and just as
 * correct as round-tripping everything through a nav-arg codec.
 */
class SelectionHolder {
    private val _currentAnime = MutableStateFlow<SAnime?>(null)
    val currentAnime: StateFlow<SAnime?> = _currentAnime

    private val _currentEpisode = MutableStateFlow<SEpisode?>(null)
    val currentEpisode: StateFlow<SEpisode?> = _currentEpisode

    /** Playable episodes of [currentAnime], so the player can offer previous/next and a "more episodes" list. */
    private val _episodes = MutableStateFlow<List<SEpisode>>(emptyList())
    val episodes: StateFlow<List<SEpisode>> = _episodes

    fun selectAnime(anime: SAnime) {
        if (_currentAnime.value?.id != anime.id) _episodes.value = emptyList()
        _currentAnime.value = anime
    }

    fun selectEpisodes(list: List<SEpisode>) {
        _episodes.value = list
    }

    /** Id of the downloaded episode to play from the device, or null when the episode should be streamed. */
    @Volatile
    var offlineDownloadId: String? = null
        private set

    /**
     * The streams the source picker found and the one the person chose; the player takes it (once) when it opens.
     * Cleared by every normal episode choice, so only the picker's own hand-off reaches the player.
     */
    @Volatile
    var pickedStream: com.ansu.anime.ui.sources.SourcePick? = null

    fun selectEpisode(episode: SEpisode) {
        _currentEpisode.value = episode
        pickedStream = null
        // Any normal episode choice (details page, next episode) streams; only [selectOffline] plays a download.
        offlineDownloadId = null
    }

    /** Plays [episode] of [anime] from the download stored under [downloadId]. */
    fun selectOffline(anime: SAnime, episode: SEpisode, downloadId: String) {
        selectAnime(anime)
        _currentEpisode.value = episode
        offlineDownloadId = downloadId
    }
}
