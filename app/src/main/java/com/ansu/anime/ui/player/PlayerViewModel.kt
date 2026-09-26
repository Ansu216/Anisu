package com.ansu.anime.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.model.StremioStream
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.extension.ExtensionManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A single playable option, whichever extension or addon it came from - what the source-select sheet lists. */
data class PlayableSource(
    val label: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
)

data class PlayerUiState(
    val isLoadingSources: Boolean = true,
    val anime: SAnime? = null,
    val episode: SEpisode? = null,
    val sources: List<PlayableSource> = emptyList(),
    val selectedSource: PlayableSource? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val showControls: Boolean = true,
)

class PlayerViewModel(
    context: Context,
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val continueWatchingRepository: ContinueWatchingRepository,
    selectionHolder: SelectionHolder,
) : ViewModel() {

    val player: ExoPlayer = ExoPlayer.Builder(context).build()

    private val _uiState = MutableStateFlow(
        PlayerUiState(anime = selectionHolder.currentAnime.value, episode = selectionHolder.currentEpisode.value),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
            }
        })
        loadSources()
        startProgressLoop()
    }

    private fun loadSources() {
        val anime = _uiState.value.anime
        val episode = _uiState.value.episode
        if (anime == null || episode == null) {
            _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "Nothing to play")
            return
        }

        viewModelScope.launch {
            val sources = runCatching { resolveSources(anime, episode) }.getOrElse {
                _uiState.value = _uiState.value.copy(isLoadingSources = false, error = it.message ?: "Failed to load sources")
                return@launch
            }

            if (sources.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "No playable sources found")
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoadingSources = false, sources = sources)
            selectSource(sources.first())

            // Resume from a saved position if we have one for this exact episode.
            val resumePoint = anime.anilistId?.let { continueWatchingRepository.resumePointFor(it) }
            if (resumePoint != null && resumePoint.episodeId == episode.id && resumePoint.positionSeconds > 5) {
                player.seekTo(resumePoint.positionSeconds * 1000)
            }
        }
    }

    private suspend fun resolveSources(anime: SAnime, episode: SEpisode): List<PlayableSource> {
        return when (val origin = anime.origin) {
            is MediaOrigin.Extension -> {
                val source = extensionManager.getSource(origin.sourceId) ?: return emptyList()
                source.getVideoList(episode).map { video ->
                    PlayableSource(label = "${video.sourceLabel} · ${video.quality}", url = video.url, headers = video.headers)
                }
            }
            is MediaOrigin.Addon -> {
                val streamsByAddon = addonManager.getStreamsFromAllAddons(origin.type, origin.stremioId)
                streamsByAddon.flatMap { (addonName, streams) -> streams.mapNotNull { it.toPlayableSource(addonName) } }
            }
        }
    }

    private fun StremioStream.toPlayableSource(addonName: String): PlayableSource? {
        val streamUrl = url ?: return null
        val label = listOfNotNull(addonName, name ?: title).joinToString(" · ")
        return PlayableSource(label = label, url = streamUrl)
    }

    fun selectSource(source: PlayableSource) {
        _uiState.value = _uiState.value.copy(selectedSource = source)
        val mediaItem = MediaItem.Builder().setUri(source.url).build()
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun seekBy(deltaMs: Long) {
        player.seekTo((player.currentPosition + deltaMs).coerceIn(0, player.duration.coerceAtLeast(0)))
    }

    fun seekTo(positionMs: Long) {
        player.seekTo(positionMs)
    }

    fun toggleControls() {
        _uiState.value = _uiState.value.copy(showControls = !_uiState.value.showControls)
    }

    /** Polls playback position on a fixed cadence to drive the seek bar and persist continue-watching progress. */
    private fun startProgressLoop() {
        viewModelScope.launch {
            while (true) {
                delay(1000)
                val duration = player.duration.coerceAtLeast(0)
                _uiState.value = _uiState.value.copy(positionMs = player.currentPosition, durationMs = duration)

                val anime = _uiState.value.anime
                val episode = _uiState.value.episode
                if (anime != null && episode != null && duration > 0) {
                    continueWatchingRepository.updateProgress(
                        anime = anime,
                        episode = episode,
                        positionSeconds = player.currentPosition / 1000,
                        durationSeconds = duration / 1000,
                    )
                }
            }
        }
    }

    override fun onCleared() {
        player.release()
        super.onCleared()
    }
}
