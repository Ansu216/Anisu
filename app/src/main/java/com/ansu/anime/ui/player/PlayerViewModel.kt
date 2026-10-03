package com.ansu.anime.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.model.StremioStream
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.SubtitleTrack
import com.ansu.anime.core.util.SelectionHolder
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.extension.ExtensionManager
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/** A single playable option, whichever extension or addon it came from - what the source-select sheet lists. */
data class PlayableSource(
    val label: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
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
    val isBuffering: Boolean = false,
)

class PlayerViewModel(
    context: Context,
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val continueWatchingRepository: ContinueWatchingRepository,
    selectionHolder: SelectionHolder,
    private val diagnostics: Diagnostics? = null,
) : ViewModel() {

    // Large buffers + a small start threshold: playback starts after a few seconds of data and the
    // rest keeps loading ahead in chunks, like other streaming apps (matters for big movie files).
    @androidx.annotation.OptIn(UnstableApi::class)
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 1_500, 3_000)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build(),
        )
        .build()

    private val _uiState = MutableStateFlow(
        PlayerUiState(anime = selectionHolder.currentAnime.value, episode = selectionHolder.currentEpisode.value),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                diagnostics?.log(LogCategory.PLAYBACK, if (isPlaying) "Playback started" else "Playback paused")
            }

            override fun onPlaybackStateChanged(state: Int) {
                _uiState.value = _uiState.value.copy(isBuffering = state == Player.STATE_BUFFERING)
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                diagnostics?.log(LogCategory.PLAYBACK, "Player error: ${error.errorCodeName}", error)
                // Fall through to the next listed source instead of leaving a black screen.
                val list = _uiState.value.sources
                val index = list.indexOf(_uiState.value.selectedSource)
                if (index in 0 until list.lastIndex) selectSource(list[index + 1])
                else _uiState.value = _uiState.value.copy(error = "Playback failed (${error.errorCodeName})")
            }
        })
        loadSources()
        startProgressLoop()
    }

    private fun loadSources() {
        val anime = _uiState.value.anime
        if (anime?.anilistId != null) {
            // Cache anime for continue-watching recovery
            continueWatchingRepository.cacheAnime(anime.anilistId, anime)
        }
        val anime = _uiState.value.anime
        val episode = _uiState.value.episode
        if (anime == null || episode == null) {
            _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "Nothing to play")
            diagnostics?.log(LogCategory.PLAYBACK, "Nothing to play")
            return
        }

        diagnostics?.log(LogCategory.PLAYBACK, "Resolving sources for ${anime.title} E${episode.episodeNumber} (ep id='${episode.id}')")
        viewModelScope.launch {
            var first = true
            val collected = mutableListOf<PlayableSource>()
            val onBatch: (List<PlayableSource>) -> Unit = { batch ->
                val fresh = batch.filter { b -> collected.none { it.url == b.url } }
                if (fresh.isNotEmpty()) {
                    collected += fresh
                    _uiState.value = _uiState.value.copy(isLoadingSources = false, sources = collected.toList(), error = null)
                    if (!first && _uiState.value.error != null) {
                        // Earlier streams all failed; a later source just answered, so try it.
                        _uiState.value = _uiState.value.copy(error = null)
                        selectSource(fresh.first())
                    }
                    if (first) {
                        first = false
                        selectSource(collected.first())
                        resumeIfSaved(anime, episode)
                    }
                }
            }
            try {
                resolveSources(anime, episode, onBatch)
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                android.util.Log.e("PlayerViewModel", "resolveSources failed", e)
                diagnostics?.log(LogCategory.PLAYBACK, "Failed to load sources: ${e::class.simpleName}: ${e.message}", e)
                if (collected.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoadingSources = false,
                        error = "${e::class.simpleName}: ${e.message ?: "Failed to load sources"}",
                    )
                    return@launch
                }
            }
            if (collected.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "No playable sources found")
                diagnostics?.log(LogCategory.PLAYBACK, "No playable sources found for ${anime.title} E${episode.episodeNumber}")
            } else {
                diagnostics?.log(LogCategory.PLAYBACK, "${collected.size} source(s) found for ${anime.title}")
            }
        }
    }

    private fun resumeIfSaved(anime: SAnime, episode: SEpisode) {
        viewModelScope.launch {
            val resumePoint = anime.anilistId?.let { continueWatchingRepository.resumePointFor(it) }
            if (resumePoint != null && resumePoint.episodeId == episode.id && resumePoint.positionSeconds > 5) {
                player.seekTo(resumePoint.positionSeconds * 1000)
            }
        }
    }

    private suspend fun resolveSources(anime: SAnime, episode: SEpisode, onBatch: (List<PlayableSource>) -> Unit) {
        when (val origin = anime.origin) {
            is MediaOrigin.Extension -> {
                // If the episode came from continue-watching and has no alternates, re-match all sources
                // to find every possible stream.
                val enrichedEpisode = if (episode.alternates.isEmpty() && episode.id.isNotBlank()) {
                    try {
                        val allEpisodes = extensionManager.findEpisodesByTitle(anime, emptyList())
                        // Find the matching episode by number
                        val matched = allEpisodes.firstOrNull { it.episodeNumber == episode.episodeNumber }
                        matched ?: episode
                    } catch (e: Exception) {
                        episode
                    }
                } else {
                    episode
                }
                
                // Ask EVERY source that has this episode, all at once; each source's streams appear in the
                // list as soon as that source answers, so a slow or broken source never blocks the rest.
                val candidates = (listOf(enrichedEpisode.copy(alternates = emptyList())) + enrichedEpisode.alternates)
                    .distinctBy { it.sourceId ?: origin.sourceId }
                kotlinx.coroutines.coroutineScope {
                    candidates.map { candidate ->
                        async {
                            val source = extensionManager.getSource(candidate.sourceId ?: origin.sourceId)
                                ?: return@async
                            try {
                                val videos = kotlinx.coroutines.withTimeout(45_000L) { source.getVideoList(candidate) }
                                diagnostics?.log(LogCategory.PLAYBACK, "  ${source.name}: ${videos.size} video(s) for ep ${candidate.episodeNumber} (id='${candidate.id}')")
                                val mapped = videos.map { video ->
                                    PlayableSource(
                                        label = if (video.sourceLabel.isBlank() || video.quality.contains(video.sourceLabel, true)) video.quality
                                        else "${video.sourceLabel} · ${video.quality}",
                                        url = video.url,
                                        headers = video.headers,
                                        subtitles = video.subtitleTracks,
                                    )
                                }
                                if (mapped.isNotEmpty()) withContext(Dispatchers.Main) { onBatch(mapped) }
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                                    diagnostics?.log(LogCategory.PLAYBACK, "  ${source.name}: timeout")
                                } else throw e
                            } catch (e: Throwable) {
                                diagnostics?.log(LogCategory.PLAYBACK, "  ${source.name}: ${e::class.simpleName}: ${e.message}", e)
                            }
                        }
                    }.awaitAll()
                }
            }
            is MediaOrigin.Addon -> {
                // Stremio asks for streams by *video* id (for a series that is the episode id such as
                // "tt0903747:1:2"); the show id only works for movies, and a movie's single episode carries it.
                val streamsByAddon = addonManager.getStreamsFromAllAddons(origin.type, episode.id.ifBlank { origin.stremioId })
                onBatch(streamsByAddon.flatMap { (addonName, streams) -> streams.mapNotNull { it.toPlayableSource(addonName) } })
            }
        }
    }

    private fun StremioStream.toPlayableSource(addonName: String): PlayableSource? {
        // Torrent-only and external-app streams carry no direct URL; ExoPlayer cannot play them.
        val streamUrl = url ?: return null
        val label = listOfNotNull(addonName, name ?: title?.lineSequence()?.firstOrNull()).joinToString(" · ")
        return PlayableSource(label = label, url = streamUrl, headers = behaviorHints?.proxyHeaders?.request.orEmpty())
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun selectSource(source: PlayableSource) {
        diagnostics?.log(LogCategory.PLAYBACK, "Source selected: ${source.label} → ${source.url}")
        _uiState.value = _uiState.value.copy(selectedSource = source)
        
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .apply {
                // Detect container type from URL or explicit hints
                val mimeType = detectMimeType(source.url)
                if (mimeType != null) {
                    diagnostics?.log(LogCategory.PLAYBACK, "Detected MIME type: $mimeType for ${source.url.substringBefore('?')}")
                    setMimeType(mimeType)
                }
                
                if (source.subtitles.isNotEmpty()) {
                    setSubtitleConfigurations(
                        source.subtitles.map { track ->
                            MediaItem.SubtitleConfiguration.Builder(Uri.parse(track.url))
                                .setMimeType(subtitleMimeType(track.url))
                                .setLanguage(track.lang)
                                .build()
                        },
                    )
                }
            }
            .build()
        
        if (source.headers.isEmpty()) {
            player.setMediaItem(mediaItem)
        } else {
            // Extension sites usually insist on a Referer/User-Agent; send what the source asked for.
            val dataSource = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(source.headers)
            player.setMediaSource(DefaultMediaSourceFactory(dataSource).createMediaSource(mediaItem))
        }
        player.prepare()
        player.playWhenReady = true
    }

    /**
     * Detect video container MIME type from URL.
     * Handles HLS, DASH, MP4, WebM, MKV, and other common formats.
     * Query parameters are stripped before checking extension.
     */
    private fun detectMimeType(url: String): String? {
        val lowerUrl = url.lowercase()
        // Remove query parameters and fragments
        val pathOnly = lowerUrl.substringBefore('?').substringBefore('#')
        
        return when {
            // HLS streams - most common for anime sites
            pathOnly.endsWith(".m3u8") || 
            lowerUrl.contains("hls") ||
            lowerUrl.contains("master.m3u8") ||
            lowerUrl.contains("/playlist/") -> 
                MimeTypes.APPLICATION_M3U8
            
            // DASH streams
            pathOnly.endsWith(".mpd") || 
            lowerUrl.contains("dash") -> 
                MimeTypes.APPLICATION_MPD
            
            // Progressive download - MP4
            pathOnly.endsWith(".mp4") -> 
                MimeTypes.VIDEO_MP4
            
            // WebM
            pathOnly.endsWith(".webm") -> 
                MimeTypes.VIDEO_WEBM
            
            // Matroska (MKV/MKA)
            pathOnly.endsWith(".mkv") || 
            pathOnly.endsWith(".mka") -> 
                MimeTypes.VIDEO_MATROSKA
            
            // MPEG-TS (common for streaming)
            pathOnly.endsWith(".ts") || 
            pathOnly.endsWith(".m2ts") ||
            pathOnly.endsWith(".mts") -> 
                MimeTypes.VIDEO_MP2T
            
            // AVI
            pathOnly.endsWith(".avi") -> 
                MimeTypes.VIDEO_AVI
            
            // QuickTime/MOV (Media3 has no VIDEO_QUICKTIME constant, so the MIME is written out)
            pathOnly.endsWith(".mov") || 
            pathOnly.endsWith(".qt") -> 
                "video/quicktime"
            
            // FLV
            pathOnly.endsWith(".flv") -> 
                MimeTypes.VIDEO_FLV
            
            // 3GPP (Media3 has no VIDEO_3GPP constant, so the MIME is written out)
            pathOnly.endsWith(".3gp") || 
            pathOnly.endsWith(".3g2") -> 
                "video/3gpp"
            
            // Heuristics for streaming URLs without clear extensions
            // These usually come from anime/CDN streaming providers
            lowerUrl.contains("stream") && 
            (lowerUrl.contains("m3u8") || lowerUrl.contains("hls")) ->
                MimeTypes.APPLICATION_M3U8
            
            lowerUrl.contains("stream") && 
            (lowerUrl.contains("mpd") || lowerUrl.contains("dash")) ->
                MimeTypes.APPLICATION_MPD
            
            // Generic CDN/streaming URLs without extension - try MP4 as most common fallback
            lowerUrl.contains("cdn") ||
            lowerUrl.contains("stream") ||
            lowerUrl.contains("video") ||
            lowerUrl.contains("media") ->
                MimeTypes.VIDEO_MP4 // Fallback to MP4 for unknown streaming URLs
            
            // No detection possible - let ExoPlayer auto-detect (less reliable)
            else -> null
        }
    }

    private fun subtitleMimeType(url: String): String {
        val path = url.substringBefore('?').lowercase()
        return when {
            path.endsWith(".srt") -> MimeTypes.APPLICATION_SUBRIP
            path.endsWith(".ass") || path.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            path.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            else -> MimeTypes.TEXT_VTT // Default to VTT
        }
    }

    fun togglePlayPause() {
        diagnostics?.log(LogCategory.CLICK, if (player.isPlaying) "Pause pressed" else "Play pressed")
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
