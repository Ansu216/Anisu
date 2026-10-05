package com.ansu.anime.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.MergingMediaSource
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
import com.ansu.anime.extension.findEpisodesByTitle
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
    val audioTracks: List<SubtitleTrack> = emptyList(),
    /** Set for a source the extension listed without resolving it: [url] is empty until this returns the playable one. */
    val resolve: (suspend () -> PlayableSource?)? = null,
)

/** How long a picked source may take to resolve before the next one is tried. */
private const val RESOLVE_TIMEOUT_MS = 30_000L

/** One selectable audio or subtitle track the player reported. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
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
    /** Playable episodes of the show (for previous/next and the episode list). */
    val episodes: List<SEpisode> = emptyList(),
    val speed: Float = 1f,
    val audioOptions: List<TrackOption> = emptyList(),
    val textOptions: List<TrackOption> = emptyList(),
    val textEnabled: Boolean = false,
    /** Intro / outro / recap stretches AniSkip knows for this episode; empty when it has none. */
    val skipSegments: List<com.ansu.anime.data.repository.SkipSegment> = emptyList(),
)

class PlayerViewModel(
    context: Context,
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val selectionHolder: SelectionHolder,
    private val diagnostics: Diagnostics? = null,
    private val aniListRepository: com.ansu.anime.anilist.AniListRepository? = null,
    private val aniSkipRepository: com.ansu.anime.data.repository.AniSkipRepository? = null,
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

    // Declared before init{}: loadSources() runs from init and assigns these.
    private var loadJob: kotlinx.coroutines.Job? = null
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var skipJob: kotlinx.coroutines.Job? = null

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            anime = selectionHolder.currentAnime.value,
            episode = selectionHolder.currentEpisode.value,
            episodes = selectionHolder.episodes.value,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                diagnostics?.log(LogCategory.PLAYBACK, if (isPlaying) "Playback started" else "Playback paused")
            }

            override fun onTracksChanged(tracks: Tracks) {
                _uiState.value = _uiState.value.copy(
                    audioOptions = tracks.toOptions(C.TRACK_TYPE_AUDIO),
                    textOptions = tracks.toOptions(C.TRACK_TYPE_TEXT),
                    textEnabled = !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
                        tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected },
                )
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
        val episode = _uiState.value.episode
        if (anime == null || episode == null) {
            _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "Nothing to play")
            diagnostics?.log(LogCategory.PLAYBACK, "Nothing to play")
            return
        }

        diagnostics?.log(LogCategory.PLAYBACK, "Resolving sources for ${anime.title} E${episode.episodeNumber} (ep id='${episode.id}')")
        loadSkipTimes(anime, episode)
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            var first = true
            val collected = mutableListOf<PlayableSource>()
            val onBatch: (List<PlayableSource>) -> Unit = { batch ->
                val fresh = batch.filter { b -> b.url.isBlank() || collected.none { it.url == b.url } }
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

    /** Looks up AniSkip's intro/outro times for the episode; the skip button simply stays away if there are none. */
    private fun loadSkipTimes(anime: SAnime, episode: SEpisode) {
        skipJob?.cancel()
        if (_uiState.value.skipSegments.isNotEmpty()) _uiState.value = _uiState.value.copy(skipSegments = emptyList())
        val anilistId = anime.anilistId ?: return
        val aniList = aniListRepository ?: return
        val aniSkip = aniSkipRepository ?: return
        skipJob = viewModelScope.launch {
            val malId = aniList.getMalId(anilistId) ?: return@launch
            val segments = aniSkip.getSkipTimes(malId, episode.episodeNumber.toInt())
            diagnostics?.log(LogCategory.PLAYBACK, "AniSkip: ${segments.size} segment(s) for ${anime.title} E${episode.episodeNumber}")
            if (_uiState.value.episode?.id == episode.id) {
                _uiState.value = _uiState.value.copy(skipSegments = segments)
            }
        }
    }

    /** Jumps past the intro/outro/recap segment being played. */
    fun skipCurrentSegment() {
        val state = _uiState.value
        val segment = state.skipSegments.firstOrNull { player.currentPosition in it.startMs until it.endMs } ?: return
        val duration = player.duration
        val reachesEnd = duration > 0 && segment.endMs >= duration - 2_000
        val next = nextEpisode()
        if (segment.type == com.ansu.anime.data.repository.SkipType.OUTRO && reachesEnd && next != null) {
            playEpisode(next)
        } else {
            player.seekTo(segment.endMs)
        }
    }

    private fun resumeIfSaved(anime: SAnime, episode: SEpisode) {
        viewModelScope.launch {
            val resumePoint = anime.anilistId?.let { continueWatchingRepository.resumePointFor(it) }
            val sameEpisode = resumePoint != null &&
                (resumePoint.episodeId == episode.id || resumePoint.episodeNumber == episode.episodeNumber)
            val finished = resumePoint != null && resumePoint.durationSeconds > 0 &&
                resumePoint.positionSeconds >= resumePoint.durationSeconds * 0.9
            if (resumePoint != null && sameEpisode && !finished && resumePoint.positionSeconds > 5) {
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
                            val total = java.util.concurrent.atomic.AtomicInteger()
                            try {
                                // Videos are delivered as each server answers, so a slow server never hides the rest,
                                // and what arrived before the timeout is kept.
                                kotlinx.coroutines.withTimeout(60_000L) {
                                    source.streamVideos(candidate, { line -> diagnostics?.log(LogCategory.PLAYBACK, line) }) { videos ->
                                        val mapped = videos.map { video -> video.toPlayable() }
                                        total.addAndGet(mapped.size)
                                        if (mapped.isNotEmpty()) withContext(Dispatchers.Main) { onBatch(mapped) }
                                    }
                                }
                                diagnostics?.log(LogCategory.PLAYBACK, "  ${source.name}: ${total.get()} video(s) for ep ${candidate.episodeNumber} (id='${candidate.id}')")
                            } catch (e: kotlinx.coroutines.CancellationException) {
                                if (e is kotlinx.coroutines.TimeoutCancellationException) {
                                    diagnostics?.log(LogCategory.PLAYBACK, "  ${source.name}: timeout (kept ${total.get()} video(s))")
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

    private fun com.ansu.anime.core.model.Video.toPlayable(): PlayableSource = PlayableSource(
        label = if (sourceLabel.isBlank() || quality.contains(sourceLabel, true)) quality else "$sourceLabel · $quality",
        url = url,
        headers = headers,
        subtitles = subtitleTracks,
        audioTracks = audioTracks,
        resolve = resolve?.let { pending -> suspend { pending()?.toPlayable() } },
    )

    private fun StremioStream.toPlayableSource(addonName: String): PlayableSource? {
        // Torrent-only and external-app streams carry no direct URL; ExoPlayer cannot play them.
        val streamUrl = url ?: return null
        val label = listOfNotNull(addonName, name ?: title?.lineSequence()?.firstOrNull()).joinToString(" · ")
        return PlayableSource(label = label, url = streamUrl, headers = behaviorHints?.proxyHeaders?.request.orEmpty())
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    fun selectSource(source: PlayableSource) {
        diagnostics?.log(LogCategory.PLAYBACK, "Source selected: ${source.label} → ${source.url.ifBlank { "(resolved when played)" }}")
        _uiState.value = _uiState.value.copy(selectedSource = source)

        // A URL that does not name its container (most extension streams) is asked what it is, instead of guessed.
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            val ready = resolveForPlayback(source) ?: return@launch
            val mimeType = detectMimeType(ready.url) ?: probeMimeType(ready.url, ready.headers)
            startPlayback(ready, mimeType)
        }
    }

    /**
     * A source the extension listed without resolving it (Aniyomi library 16) is resolved now, the way Aniyomi
     * resolves the video that is picked. When it cannot be, the next listed source is tried and null is returned.
     */
    private suspend fun resolveForPlayback(source: PlayableSource): PlayableSource? {
        val pending = source.resolve ?: return source
        val resolved = try {
            kotlinx.coroutines.withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { pending() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Throwable) {
            diagnostics?.log(LogCategory.PLAYBACK, "Resolving '${source.label}' failed: ${e::class.simpleName}: ${e.message}", e)
            null
        }
        if (resolved == null || resolved.url.isBlank()) {
            diagnostics?.log(LogCategory.PLAYBACK, "'${source.label}' could not be resolved")
            val list = _uiState.value.sources
            val index = list.indexOf(source)
            if (index in 0 until list.lastIndex) selectSource(list[index + 1])
            else _uiState.value = _uiState.value.copy(error = "Could not open '${source.label}'")
            return null
        }
        // The resolved video replaces the listed one, so picking it again is instant.
        _uiState.value = _uiState.value.copy(
            sources = _uiState.value.sources.map { if (it == source) resolved else it },
            selectedSource = resolved,
        )
        return resolved
    }

    private val probeClient: okhttp3.OkHttpClient by lazy {
        okhttp3.OkHttpClient.Builder()
            .connectTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(10, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .build()
    }

    /**
     * Asks the stream what it is: the first bytes (`#EXTM3U` is HLS, `<MPD` is DASH) and its Content-Type.
     * Returns null when it cannot tell, so ExoPlayer sniffs the container itself.
     */
    private suspend fun probeMimeType(url: String, headers: Map<String, String>): String? = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .apply { headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-1023")
                .build()
            probeClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                val head = response.peekBody(1024).string().trimStart('\uFEFF', ' ', '\n', '\r', '\t')
                val type = response.header("Content-Type")?.substringBefore(';')?.trim()?.lowercase().orEmpty()
                val mime = when {
                    head.startsWith("#EXTM3U") -> MimeTypes.APPLICATION_M3U8
                    head.contains("<MPD") -> MimeTypes.APPLICATION_MPD
                    "mpegurl" in type -> MimeTypes.APPLICATION_M3U8
                    "dash+xml" in type -> MimeTypes.APPLICATION_MPD
                    type == "video/mp4" -> MimeTypes.VIDEO_MP4
                    type == "video/webm" -> MimeTypes.VIDEO_WEBM
                    type == "video/x-matroska" -> MimeTypes.VIDEO_MATROSKA
                    type == "video/mp2t" -> MimeTypes.VIDEO_MP2T
                    else -> null
                }
                diagnostics?.log(LogCategory.PLAYBACK, "Probed ${url.substringBefore('?')}: Content-Type=$type → ${mime ?: "unknown"}")
                mime
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            diagnostics?.log(LogCategory.PLAYBACK, "Probe failed for ${url.substringBefore('?')}: ${e.message}")
            null
        }
    }

    @androidx.annotation.OptIn(UnstableApi::class)
    private fun startPlayback(source: PlayableSource, mimeType: String?) {
        val mediaItem = MediaItem.Builder()
            .setUri(source.url)
            .apply {
                // Detect container type from URL or explicit hints
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
        
        if (source.headers.isEmpty() && source.audioTracks.isEmpty()) {
            player.setMediaItem(mediaItem)
        } else {
            // Extension sites usually insist on a Referer/User-Agent; send what the source asked for.
            val dataSource = DefaultHttpDataSource.Factory()
                .setAllowCrossProtocolRedirects(true)
                .setDefaultRequestProperties(source.headers)
            val factory = DefaultMediaSourceFactory(dataSource)
            val main = factory.createMediaSource(mediaItem)
            // External audio streams (dubs) are merged into the video; the player lists them as audio tracks.
            val audio = source.audioTracks.map { track ->
                factory.createMediaSource(
                    MediaItem.Builder()
                        .setUri(track.url)
                        .apply { detectMimeType(track.url)?.let { setMimeType(it) } }
                        .build(),
                )
            }
            player.setMediaSource(if (audio.isEmpty()) main else MergingMediaSource(main, *audio.toTypedArray()))
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
            lowerUrl.contains(".m3u8") -> 
                MimeTypes.APPLICATION_M3U8
            
            // DASH streams
            pathOnly.endsWith(".mpd") || 
            lowerUrl.contains(".mpd") -> 
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
            
            // The URL does not name its container: selectSource asks the stream itself (probeMimeType).
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

    private fun Tracks.toOptions(type: Int): List<TrackOption> {
        val out = mutableListOf<TrackOption>()
        groups.forEachIndexed { groupIndex, group ->
            if (group.type != type) return@forEachIndexed
            for (trackIndex in 0 until group.length) {
                if (!group.isTrackSupported(trackIndex)) continue
                val format = group.getTrackFormat(trackIndex)
                val language = format.language
                    ?.takeIf { it.isNotBlank() && it != "und" }
                    ?.let { code -> java.util.Locale.forLanguageTag(code).displayLanguage.ifBlank { code } }
                val label = listOfNotNull(format.label, language).distinct().joinToString(" · ")
                    .ifBlank { "Track ${out.size + 1}" }
                out += TrackOption(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex))
            }
        }
        return out
    }

    fun selectAudioTrack(option: TrackOption) = overrideTrack(option)

    /** Picks a subtitle track, or turns subtitles off when [option] is null. */
    fun selectTextTrack(option: TrackOption?) {
        if (option == null) {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
            _uiState.value = _uiState.value.copy(textEnabled = false)
        } else {
            player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                .build()
            overrideTrack(option)
            _uiState.value = _uiState.value.copy(textEnabled = true)
        }
    }

    private fun overrideTrack(option: TrackOption) {
        val group = player.currentTracks.groups.getOrNull(option.groupIndex) ?: return
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, option.trackIndex))
            .build()
    }

    fun setSpeed(speed: Float) {
        player.setPlaybackSpeed(speed)
        _uiState.value = _uiState.value.copy(speed = speed)
    }

    /** Episodes ordered by number, whatever order the details screen listed them in. */
    private fun ordered(): List<SEpisode> = _uiState.value.episodes.sortedBy { it.episodeNumber }

    fun previousEpisode(): SEpisode? {
        val current = _uiState.value.episode ?: return null
        return ordered().lastOrNull { it.episodeNumber < current.episodeNumber }
    }

    fun nextEpisode(): SEpisode? {
        val current = _uiState.value.episode ?: return null
        return ordered().firstOrNull { it.episodeNumber > current.episodeNumber }
    }

    fun playEpisode(target: SEpisode) {
        val current = _uiState.value.episode
        if (current != null && current.id == target.id && current.episodeNumber == target.episodeNumber) return
        diagnostics?.log(LogCategory.CLICK, "Episode switched to E${target.episodeNumber}")
        loadJob?.cancel()
        playbackJob?.cancel()
        player.stop()
        player.clearMediaItems()
        selectionHolder.selectEpisode(target)
        _uiState.value = _uiState.value.copy(
            episode = target,
            sources = emptyList(),
            selectedSource = null,
            isLoadingSources = true,
            error = null,
            positionMs = 0L,
            durationMs = 0L,
            audioOptions = emptyList(),
            textOptions = emptyList(),
            skipSegments = emptyList(),
        )
        loadSources()
    }

    fun skipOutro() {
        val next = nextEpisode()
        if (next != null) playEpisode(next)
        else player.seekTo((player.duration - 1000).coerceAtLeast(0))
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
