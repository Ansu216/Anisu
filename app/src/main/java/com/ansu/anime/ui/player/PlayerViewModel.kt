package com.ansu.anime.ui.player

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.net.Uri
import androidx.media3.common.C
import com.ansu.anime.extension.StreamFailures
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
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.model.SubtitleTrack
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
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

/** A single playable option, whichever extension it came from - what the source-select sheet lists. */
data class PlayableSource(
    val label: String,
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitles: List<SubtitleTrack> = emptyList(),
    val audioTracks: List<SubtitleTrack> = emptyList(),
    /** Set for a source the extension listed without resolving it: [url] is empty until this returns the playable one. */
    val resolve: (suspend () -> PlayableSource?)? = null,
    /** The extension this stream came from; used to order streams by the person's source priority. */
    val sourceId: Long? = null,
    /** Name of the extension that listed this stream (shown as a chip in the Sources panel). */
    val extensionName: String? = null,
    /** The server (hoster) inside the extension, e.g. "HD-1"; the Sources list shows it next to [extensionName]. */
    val serverName: String? = null,
    /** Intro/outro/recap times the source reported for this stream; they win over AniSkip's. */
    val skipSegments: List<com.ansu.anime.data.repository.SkipSegment> = emptyList(),
)

/** How many other streams are opened next to the best one when sources are raced. */
private const val MAX_RACE_RIVALS = 2

/** How long a picked source may take to resolve before the next one is tried. */
private const val RESOLVE_TIMEOUT_MS = 30_000L

/** How long the first pick waits for a higher-priority source before playing what it already has. */
private const val PRIORITY_GRACE_MS = 3_000L

/** How long the first pick waits for a stream in the preferred language (a dub often answers later than a sub). */
private const val LANGUAGE_GRACE_MS = 15_000L

/** How long the first pick waits for the source this show last played from, before falling back to the ranking. */
private const val REMEMBERED_GRACE_MS = 8_000L

/** Resolution in pixels of a stream, read from its label ("1080p", "4K"); null when the label does not say. */
private fun PlayableSource.heightOrNull(): Int? = when (val chip = qualityChip()) {
    null -> null
    "4K" -> 2160
    "2K" -> 1440
    else -> chip.removeSuffix("p").toIntOrNull()
}

/** 0 when the stream is in the language asked for, 1 when it is not. A stream that does not say is taken as a sub. */
private fun PlayableSource.languageMiss(preferredLanguage: String): Int {
    // The label is not the only place a dub is announced: some extensions put it in the server name or the
    // extension's own name ("HD-2 (Dub)"), and a stream whose audio list offers an English track is a dub too.
    val isDub = kindChip() == "Dub" ||
        serverName?.contains("dub", ignoreCase = true) == true ||
        extensionName?.contains("dub", ignoreCase = true) == true ||
        audioTracks.any { it.lang.contains("dub", ignoreCase = true) }
    return if (isDub == (preferredLanguage == com.ansu.anime.data.prefs.PlayerPrefs.LANGUAGE_DUB)) 0 else 1
}

/**
 * How far a stream's resolution is from the one asked for; lower is better. The best stream at or below it
 * wins (1080p asked, 720p beats 480p); a higher one is used only when nothing at or below exists, and a
 * stream that names no resolution comes last.
 */
private fun PlayableSource.qualityMiss(preferredHeight: Int): Int {
    // "Any": every resolution is as good as another, so only language and the source order decide.
    if (preferredHeight == com.ansu.anime.data.prefs.PlayerPrefs.QUALITY_ANY) return 0
    val height = heightOrNull() ?: return Int.MAX_VALUE
    return if (height <= preferredHeight) preferredHeight - height else 10_000 + (height - preferredHeight)
}

/**
 * The stream to start with. The preferred language comes first (sub or dub, the other only when the show has
 * none), then the closest resolution to the preferred quality. Streams that tie are told apart by the source
 * this show last played from (its remembered server first), and after that by the list order, which is the
 * person's source priority.
 */
internal fun pickStart(
    list: List<PlayableSource>,
    remembered: com.ansu.anime.data.repository.LastPlayedSource?,
    preferredQuality: Int,
    preferredLanguage: String,
    manual: com.ansu.anime.data.repository.LastPlayedSource? = null,
): PlayableSource? {
    if (list.isEmpty()) return null
    // A stream the person picked by hand for this show beats the preference: the same stream if it is listed,
    // else the same server in the same language (Sub/Dub) at the closest resolution.
    if (manual != null) {
        val chosen = PlayableSource(label = manual.label.orEmpty(), url = "")
        val sameServer = list.filter { it.sourceId == manual.sourceId && it.serverName == manual.serverName }
        sameServer.firstOrNull { it.label == manual.label }?.let { return it }
        val wantedHeight = chosen.heightOrNull()
        sameServer.filter { it.kindChip() == chosen.kindChip() }
            .minByOrNull { source ->
                val height = source.heightOrNull()
                if (wantedHeight == null || height == null) Int.MAX_VALUE else kotlin.math.abs(height - wantedHeight)
            }
            ?.let { return it }
    }
    fun rememberedMiss(source: PlayableSource): Int = when {
        remembered == null || source.sourceId != remembered.sourceId -> 2
        source.serverName == remembered.serverName -> 0
        else -> 1
    }
    // minWith keeps the first of equals, so the priority order of the list settles whatever is left.
    return list.minWithOrNull(
        compareBy<PlayableSource> { it.languageMiss(preferredLanguage) }
            .thenBy { it.qualityMiss(preferredQuality) }
            .thenBy { rememberedMiss(it) },
    )
}

/** Largest subtitle shift, either way, the Subtitles panel offers. */
private const val MAX_SUBTITLE_OFFSET_MS = 10_000L

/**
 * Netflix-style buffering: the player keeps about five minutes of video in RAM on each side of the
 * playback point instead of a thin forward buffer, so seeking back and resuming are instant and the
 * busy system disk cache is not relied on. The byte cap keeps the window within a bounded amount of
 * memory on a device that has little to spare.
 */
private const val RAM_BUFFER_AHEAD_MS = 120_000
private const val RAM_BUFFER_BEHIND_MS = 60_000
private const val RAM_BUFFER_BYTES = 128 * 1024 * 1024

/** A stream that has not reached "ready" this long after it was opened is treated as stuck, not as slow. */
private const val START_STALL_MS = 25_000L

/** One selectable audio or subtitle track the player reported. */
data class TrackOption(
    val groupIndex: Int,
    val trackIndex: Int,
    val label: String,
    val selected: Boolean,
    /** Channels and bitrate when the player reports them, e.g. "Stereo · 128 kbps"; shown under the name in the Audio panel. */
    val detail: String = "",
)

data class PlayerUiState(
    val isLoadingSources: Boolean = true,
    val anime: SAnime? = null,
    val episode: SEpisode? = null,
    val sources: List<PlayableSource> = emptyList(),
    val selectedSource: PlayableSource? = null,
    /** The Sources-list entry behind [selectedSource] (the list keeps the unresolved copy of a stream that was resolved to play); the panel highlights it. */
    val selectedListed: PlayableSource? = null,
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
    /** Subtitle delay in milliseconds (Subtitles panel); 0 = in sync. */
    val subtitleOffsetMs: Long = 0L,
    /** Measured response time per stream url in ms; -1 = unreachable, missing = not measured yet. */
    val pings: Map<String, Long> = emptyMap(),
    val isMeasuringPings: Boolean = false,
    /** True from the moment a stream is chosen until its first picture (or sound) is out: the buffering screen shows meanwhile. */
    val isStarting: Boolean = true,
)

class PlayerViewModel(
    context: Context,
    private val extensionManager: ExtensionManager,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val selectionHolder: SelectionHolder,
    private val diagnostics: Diagnostics? = null,
    private val aniListRepository: com.ansu.anime.anilist.AniListRepository? = null,
    private val aniSkipRepository: com.ansu.anime.data.repository.AniSkipRepository? = null,
    private val playerPrefs: com.ansu.anime.data.prefs.PlayerPrefs? = null,
    private val mediaCache: com.ansu.anime.data.player.MediaCache? = null,
    private val episodeMetadataRepository: com.ansu.anime.data.repository.EpisodeMetadataRepository? = null,
    private val downloads: com.ansu.anime.data.download.DownloadsRepository? = null,
) : ViewModel() {

    // Large buffers + a small start threshold: playback starts after a few seconds of data and the
    // rest keeps loading ahead in chunks, like other streaming apps (matters for big movie files).
    @androidx.annotation.OptIn(UnstableApi::class)
    private val subtitleLead = SubtitleLeadRenderersFactory(context)

    // Netflix-style RAM window: up to five minutes ahead and five behind are held in memory, so a seek
    // back or a resume is instant. The byte cap bounds how much the window can cost on a tight device.
    @androidx.annotation.OptIn(UnstableApi::class)
    val player: ExoPlayer = ExoPlayer.Builder(context, subtitleLead)
        .setLoadControl(
            androidx.media3.exoplayer.DefaultLoadControl.Builder()
                // Start after 2 s of data, but after a stall wait for 6 s: resuming on 3 s made a weak connection
                // stall again within seconds, which showed up as constant buffering. The byte cap now really bounds
                // the window (time no longer overrides it), so a big window cannot squeeze memory and stutter.
                .setBufferDurationsMs(30_000, RAM_BUFFER_AHEAD_MS, 2_000, 6_000)
                .setBackBuffer(RAM_BUFFER_BEHIND_MS, true)
                .setTargetBufferBytes(RAM_BUFFER_BYTES)
                .setPrioritizeTimeOverSizeThresholds(false)
                .build(),
        )
        // Read through the shared cache, so an episode pre-loaded at launch plays from disk and every
        // segment fetched during playback is kept for the next time it is opened.
        .setMediaSourceFactory(
            DefaultMediaSourceFactory(
                mediaCache?.dataSourceFactory() ?: androidx.media3.datasource.DefaultDataSource.Factory(context),
            ),
        )
        .build()

    /** True while the app is out of sight (not in picture-in-picture): a stream that finishes loading then must not start by itself. */
    @Volatile
    private var backgrounded = false

    /** A video was meant to start (still loading when the app was left): it starts when the person comes back. */
    private var playOnReturn = false

    /** The app left the screen: pause, and remember a start that had not happened yet so it is not lost. */
    fun onBackgrounded() {
        backgrounded = true
        val waiting = player.mediaItemCount == 0 || (player.playWhenReady && !player.isPlaying && _uiState.value.isStarting)
        if (waiting) playOnReturn = true
        player.pause()
    }

    /** The app is back on screen: a start that was held back while it was away goes ahead now. */
    fun onForegrounded() {
        backgrounded = false
        if (playOnReturn) {
            playOnReturn = false
            if (player.mediaItemCount > 0) player.playWhenReady = true
        }
    }

    /** Starts the prepared media, unless the app is out of sight; then it waits for [onForegrounded]. */
    private fun playWhenVisible() {
        if (backgrounded) {
            playOnReturn = true
            player.playWhenReady = false
        } else {
            player.playWhenReady = true
        }
    }

    // Declared before init{}: loadSources() runs from init and assigns these.
    private var loadJob: kotlinx.coroutines.Job? = null

    private var startWatchdog: kotlinx.coroutines.Job? = null

    /** True while the stream being opened was picked by hand (Sources panel or picker): a stuck one is reported, not swapped. */
    private var userPicked = false

    /**
     * The entry of the Sources list that was last selected. A source listed without a URL is resolved when it plays,
     * and the resolved copy becomes [PlayerUiState.selectedSource], while the list keeps (or gets back, when more
     * sources arrive) the unresolved entry. Looking the playing stream up in the list by the resolved copy found
     * nothing, so the fall-through to the next source never ran and automatic mode gave up on the first source.
     */
    private var listedSelection: PlayableSource? = null

    /** The subtitle tracks the current stream was started with, and how many of them have been given up on after a crash. */
    private var subtitleOptions: List<MediaItem.SubtitleConfiguration> = emptyList()
    private var subtitleStage = 0
    private var rebuildPlayback: ((List<MediaItem.SubtitleConfiguration>, Long) -> Unit)? = null

    /** Where the playing stream sits in [list], whether the list holds its listed entry or its resolved copy; -1 if neither. */
    private fun currentIndexIn(list: List<PlayableSource>): Int {
        val selected = _uiState.value.selectedSource
        val listed = listedSelection
        return list.indexOfFirst { it == selected || (listed != null && it == listed) }
    }

    /**
     * The stream to try after the one at [index] failed: the next one on a different server (a server that is down
     * fails every quality it lists), else simply the next one. Null when the failed one is the last in the list.
     */
    private fun nextAfter(list: List<PlayableSource>, index: Int): PlayableSource? {
        if (index !in 0 until list.lastIndex) return null
        val failed = list[index]
        StreamFailures.mark(failed.sourceId, failed.serverName)
        val tail = list.subList(index + 1, list.size)
        // Servers that failed a moment ago (on this episode or an earlier one) are tried last.
        val healthy = tail.filterNot { StreamFailures.isRecentlyFailed(it.sourceId, it.serverName) }
        val pool = healthy.ifEmpty { tail }
        return pool.firstOrNull { it.sourceId != failed.sourceId || it.serverName != failed.serverName } ?: pool.first()
    }

    /** The one subtitle track to keep when all of them together broke playback: English if there is one, else the first. */
    private fun preferredSubtitleOnly(options: List<MediaItem.SubtitleConfiguration>): List<MediaItem.SubtitleConfiguration> {
        val english = options.firstOrNull { o ->
            val name = (o.language ?: o.label).orEmpty()
            name.equals("en", true) || name.contains("english", true)
        }
        return listOfNotNull(english ?: options.firstOrNull())
    }

    /**
     * Where the next stream should start, in ms. Set from the saved continue-watching point when an episode opens,
     * and from the live position when the source is switched, so neither a resume nor a source change restarts
     * the episode. It is handed to the player together with the media (a seek issued before the media is set
     * is thrown away by the player) and cleared once used.
     */
    private var startPositionMs: Long = 0L
    private var graceJob: kotlinx.coroutines.Job? = null

    /** Sources asked for the current episode; lets the first pick wait briefly for a higher-priority one. */
    @Volatile
    private var expectedSourceIds: List<Long> = emptyList()

    private fun rankOf(sourceId: Long?): Int = playerPrefs?.rankOf(sourceId) ?: 0
    private var playbackJob: kotlinx.coroutines.Job? = null
    private var skipJob: kotlinx.coroutines.Job? = null

    /** AniSkip's times and the playing stream's own times; [mergeSkipSegments] combines them (the source's win). */
    private var aniSkipSegments: List<com.ansu.anime.data.repository.SkipSegment> = emptyList()
    private var sourceSkipSegments: List<com.ansu.anime.data.repository.SkipSegment> = emptyList()

    private fun mergeSkipSegments() {
        val covered = sourceSkipSegments.map { it.type }.toSet()
        val merged = (sourceSkipSegments + aniSkipSegments.filter { it.type !in covered }).sortedBy { it.startMs }
        if (merged != _uiState.value.skipSegments) _uiState.value = _uiState.value.copy(skipSegments = merged)
    }
    private var pingRuns = 0

    private val _uiState = MutableStateFlow(
        PlayerUiState(
            anime = selectionHolder.currentAnime.value,
            episode = selectionHolder.currentEpisode.value,
            episodes = selectionHolder.episodes.value,
        ),
    )
    val uiState: StateFlow<PlayerUiState> = _uiState

    private val _cues = MutableStateFlow<List<Cue>>(emptyList())

    /** Subtitle cues to draw, already delayed by the subtitle offset. The video view's own subtitle layer is hidden. */
    val cues: StateFlow<List<Cue>> = _cues

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                // The stream got going after all: a "not responding" message left over from the watchdog is stale.
                val stale = isPlaying && _uiState.value.error?.contains("is not responding") == true
                _uiState.value = _uiState.value.copy(
                    isPlaying = isPlaying,
                    isStarting = if (isPlaying) false else _uiState.value.isStarting,
                    error = if (stale) null else _uiState.value.error,
                )
                diagnostics?.log(LogCategory.PLAYBACK, if (isPlaying) "Playback started" else "Playback paused")
            }

            override fun onRenderedFirstFrame() {
                _uiState.value = _uiState.value.copy(isStarting = false)
            }

            override fun onTracksChanged(tracks: Tracks) {
                val textOptions = tracks.toOptions(C.TRACK_TYPE_TEXT)
                _uiState.value = _uiState.value.copy(
                    audioOptions = tracks.toOptions(C.TRACK_TYPE_AUDIO),
                    textOptions = textOptions,
                    textEnabled = !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT) &&
                        tracks.groups.any { it.type == C.TRACK_TYPE_TEXT && it.isSelected },
                )
                applySessionSubtitleChoice(textOptions)
            }

            override fun onCues(cueGroup: CueGroup) {
                val offset = _uiState.value.subtitleOffsetMs
                if (offset <= 0L) {
                    _cues.value = cueGroup.cues
                } else {
                    // Every cue change is held back by the same delay, so their order is kept.
                    viewModelScope.launch {
                        delay(offset)
                        _cues.value = cueGroup.cues
                    }
                }
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_READY) {
                    startWatchdog?.cancel()
                    _uiState.value.selectedSource?.let { StreamFailures.clear(it.sourceId, it.serverName) }
                }
                if (state == Player.STATE_ENDED && playerPrefs?.autoplayNext?.value != false && !backgrounded) {
                    nextEpisode()?.let { playEpisode(it) }
                }
                val stale = state == Player.STATE_READY && _uiState.value.error?.contains("is not responding") == true
                // Once the stream is ready, list every subtitle track it has now, in case a late one was missed.
                val readyText = if (state == Player.STATE_READY) player.currentTracks.toOptions(C.TRACK_TYPE_TEXT) else emptyList()
                _uiState.value = _uiState.value.copy(
                    textOptions = if (readyText.size > _uiState.value.textOptions.size) readyText else _uiState.value.textOptions,
                    isBuffering = state == Player.STATE_BUFFERING,
                    isStarting = if (state == Player.STATE_READY && player.isPlaying) false else _uiState.value.isStarting,
                    error = if (stale) null else _uiState.value.error,
                )
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                diagnostics?.log(LogCategory.PLAYBACK, "Player error: ${error.errorCodeName}", error)
                // A subtitle track that Media3 cannot load (it throws from inside the track's loader) must not take
                // the video down with it, and the video is not at fault, so the next source is not the answer either:
                // start the same stream again with one subtitle track, then with none.
                val rebuild = rebuildPlayback
                if (error.errorCode == androidx.media3.common.PlaybackException.ERROR_CODE_FAILED_RUNTIME_CHECK &&
                    rebuild != null && subtitleOptions.isNotEmpty() && subtitleStage < 2
                ) {
                    subtitleStage += 1
                    val keep = if (subtitleStage == 1) preferredSubtitleOnly(subtitleOptions) else emptyList()
                    val tracks = subtitleOptions.joinToString { "${it.language ?: it.label}:${it.mimeType}" }
                    diagnostics?.log(
                        LogCategory.PLAYBACK,
                        "A subtitle track broke playback (${subtitleOptions.size} track(s): $tracks); starting again with " +
                            if (keep.isEmpty()) "no subtitles" else "one subtitle track",
                    )
                    val position = player.currentPosition
                    subtitleChoiceApplied = false
                    rebuild(keep, if (position > 0L) position else C.TIME_UNSET)
                    return
                }
                // Fall through to the next listed source instead of leaving a black screen.
                val list = _uiState.value.sources
                val next = nextAfter(list, currentIndexIn(list))
                if (next != null) { userPicked = false; selectSource(next) }
                else _uiState.value = _uiState.value.copy(error = "Playback failed (${error.errorCodeName})")
            }
        })
        loadSources()
        loadMissingEpisodes()
        startProgressLoop()
    }

    /**
     * Opened straight from Continue Watching, the player has an episode but no list of the show's episodes:
     * only the details page builds one, and it was never opened. Without it there is no episode picker and
     * no previous/next. So the list is fetched here, the way the details page builds it: AniList's own
     * episode count first, and the sources' (cached) episode lists when AniList cannot say.
     */
    private fun loadMissingEpisodes() {
        val anime = _uiState.value.anime ?: return
        if (_uiState.value.episodes.isNotEmpty()) return
        val anilistId = anime.anilistId ?: return
        viewModelScope.launch {
            val details = runCatching { aniListRepository?.getMediaDetails(anilistId) }.getOrNull()
            val meta = runCatching { episodeMetadataRepository?.getEpisodeMeta(anilistId) }.getOrNull().orEmpty()
            var list = com.ansu.anime.ui.details.aniListEpisodePlaceholders(anime, details, meta)
            if (list.isEmpty()) {
                // AniList has no count: use the sources' own list for this show, when the details page saved one.
                list = com.ansu.anime.extension.EpisodeListCache.get("ep-$anilistId").orEmpty()
            }
            if (list.isEmpty()) return@launch
            val named = list.map { episode ->
                val info = episode.episodeNumber.takeIf { it % 1f == 0f }?.toInt()?.let { meta[it] } ?: return@map episode
                episode.copy(
                    name = info.title ?: episode.name,
                    thumbnailUrl = episode.thumbnailUrl ?: info.thumbnailUrl,
                    description = info.overview ?: episode.description,
                    airDate = episode.airDate ?: info.airDate,
                )
            }.distinctBy { it.id }
            // A details page opened meanwhile may already have handed over its own list; keep that one.
            if (_uiState.value.episodes.isNotEmpty()) return@launch
            selectionHolder.selectEpisodes(named)
            _uiState.value = _uiState.value.copy(episodes = named)
            diagnostics?.log(LogCategory.PLAYBACK, "Episode list loaded for ${anime.title}: ${named.size} episode(s)")
        }
    }

    private fun loadSources() {
        val anime = _uiState.value.anime
        val episode = _uiState.value.episode
        if (anime == null || episode == null) {
            _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "Nothing to play")
            diagnostics?.log(LogCategory.PLAYBACK, "Nothing to play")
            return
        }

        loadSkipTimes(anime, episode)
        // A downloaded episode plays from the device, with no source lookup at all.
        val offlineId = selectionHolder.offlineDownloadId
        if (offlineId != null && downloads != null) {
            playOffline(anime, episode, offlineId, downloads)
            return
        }

        // The source picker already asked every extension and the person chose a stream: play it, no second lookup.
        val picked = selectionHolder.pickedStream
        selectionHolder.pickedStream = null
        if (picked != null) {
            diagnostics?.log(LogCategory.PLAYBACK, "Playing the stream picked in the source picker: ${picked.selected.label}")
            loadJob?.cancel()
            graceJob?.cancel()
            expectedSourceIds = emptyList()
            loadJob = viewModelScope.launch {
                startPositionMs = savedPositionMs(anime, episode)
                // The person may have moved to another episode while the saved point was being read.
                if (_uiState.value.episode?.id != episode.id) return@launch
                _uiState.value = _uiState.value.copy(isLoadingSources = false, sources = picked.sources, error = null)
                userPicked = true
                selectSource(picked.selected)
            }
            return
        }

        diagnostics?.log(LogCategory.PLAYBACK, "Resolving sources for ${anime.title} E${episode.episodeNumber} (ep id='${episode.id}')")
        loadJob?.cancel()
        graceJob?.cancel()
        expectedSourceIds = emptyList()
        // The source this show last played from (kept 3 days): it is tried first, ahead of the ranking.
        // A stream picked by hand for this show comes first, and its source is the one waited for.
        val manualPick = anime.anilistId?.let { playerPrefs?.manualPickFor(it) }
        val remembered = manualPick
            ?: sessionSource?.takeIf { it.first == anime.anilistId }?.second
            ?: anime.anilistId?.let { continueWatchingRepository.lastSourceFor(it) }
        loadJob = viewModelScope.launch {
            var first = true
            val collected = mutableListOf<PlayableSource>()
            fun startNow() {
                graceJob?.cancel()
                first = false
                viewModelScope.launch {
                    startPositionMs = savedPositionMs(anime, episode)
                    // The person may have moved to another episode while the saved point was being read.
                    if (_uiState.value.episode?.id != episode.id || collected.isEmpty()) return@launch
                    userPicked = false
                    selectSource(
                        pickStart(
                            collected,
                            remembered,
                            playerPrefs?.preferredQuality?.value ?: com.ansu.anime.data.prefs.PlayerPrefs.DEFAULT_QUALITY,
                            playerPrefs?.preferredLanguage?.value ?: com.ansu.anime.data.prefs.PlayerPrefs.LANGUAGE_SUB,
                            manualPick,
                        ) ?: collected.first(),
                    )
                }
            }
            // With quality on "Any", speed is the point: the first stream in the preferred language that answers
            // starts playing, without waiting for a better-ranked source or a better resolution.
            val anyQuality = playerPrefs?.preferredQuality?.value == com.ansu.anime.data.prefs.PlayerPrefs.QUALITY_ANY
            val wantedLanguage = playerPrefs?.preferredLanguage?.value ?: com.ansu.anime.data.prefs.PlayerPrefs.LANGUAGE_SUB
            val onBatch: (List<PlayableSource>) -> Unit = { batch ->
                val fresh = batch.filter { b -> b.url.isBlank() || collected.none { it.url == b.url } }
                if (fresh.isNotEmpty()) {
                    collected += fresh
                    // The list follows the person's source priority; within one source, arrival order is kept.
                    collected.sortWith(compareBy<PlayableSource> { rankOf(it.sourceId) })
                    // Read before it is cleared just below: it used to be read after, so it was never set and a later
                    // source was never tried once the first ones had failed.
                    val hadError = _uiState.value.error != null
                    _uiState.value = _uiState.value.copy(isLoadingSources = false, sources = collected.toList(), error = null)
                    if (!first && hadError && !userPicked) {
                        // Earlier streams all failed; a later source just answered, so try it.
                        _uiState.value = _uiState.value.copy(error = null)
                        selectSource(fresh.first())
                    }
                    val rememberedId = remembered?.sourceId
                    // Nothing in the preferred language has answered yet (a dub usually arrives after the sub): do not start
                    // on the other language, wait for it. It plays as soon as one appears, once every source has answered,
                    // or after LANGUAGE_GRACE_MS, so a show with no dub still starts.
                    val languageReady = manualPick != null || collected.any { it.languageMiss(wantedLanguage) == 0 }
                    if (first && !languageReady) {
                        if (graceJob?.isActive != true) {
                            diagnostics?.log(LogCategory.PLAYBACK, "Waiting for a $wantedLanguage stream before starting")
                            graceJob = viewModelScope.launch {
                                delay(LANGUAGE_GRACE_MS)
                                if (first && collected.isNotEmpty()) startNow()
                            }
                        }
                    } else if (first && anyQuality && manualPick == null && collected.any { it.languageMiss(wantedLanguage) == 0 }) {
                        diagnostics?.log(LogCategory.PLAYBACK, "Quality is Any: starting on the first stream in the preferred language")
                        startNow()
                    } else if (first && rememberedId != null && collected.any { it.sourceId == rememberedId }) {
                        diagnostics?.log(LogCategory.PLAYBACK, "Resuming with the source this show last played from")
                        startNow()
                    } else if (first && rememberedId != null && rememberedId in expectedSourceIds) {
                        // That source was asked and has not answered yet: give it a little longer than the ranking would.
                        if (graceJob?.isActive != true) {
                            graceJob = viewModelScope.launch {
                                delay(REMEMBERED_GRACE_MS)
                                if (first && collected.isNotEmpty()) startNow()
                            }
                        }
                    } else if (first) {
                        // Start at once when the best-ranked source being asked has answered. Otherwise give
                        // it a few seconds to catch up, so a fast low-priority source does not win by speed alone.
                        val bestWanted = expectedSourceIds.minOfOrNull { rankOf(it) }
                        val haveBest = bestWanted == null || rankOf(collected.first().sourceId) <= bestWanted
                        if (haveBest) {
                            startNow()
                        } else {
                            graceJob?.cancel()
                            graceJob = viewModelScope.launch {
                                delay(PRIORITY_GRACE_MS)
                                if (first && collected.isNotEmpty()) startNow()
                            }
                        }
                    }
                }
            }
            try {
                resolveSources(anime, episode, onBatch)
                // Every source has answered: if the first pick was waiting (for a dub that never came), start now.
                if (first && collected.isNotEmpty()) startNow()
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

    /** Plays the download stored under [downloadId]: its saved video tracks and every saved subtitle file. */
    @androidx.annotation.OptIn(UnstableApi::class)
    private fun playOffline(
        anime: SAnime,
        episode: SEpisode,
        downloadId: String,
        repository: com.ansu.anime.data.download.DownloadsRepository,
    ) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val item = repository.offlineMediaItem(downloadId)
            if (item == null) {
                _uiState.value = _uiState.value.copy(isLoadingSources = false, error = "This download could not be found")
                return@launch
            }
            diagnostics?.log(LogCategory.PLAYBACK, "Playing download for ${anime.title} E${episode.episodeNumber}")
            subtitleChoiceApplied = false
            startPositionMs = savedPositionMs(anime, episode)
            val startMs = if (startPositionMs > 0L) startPositionMs else C.TIME_UNSET
            startPositionMs = 0L
            val label = repository.entryFor(downloadId)?.sourceLabel.orEmpty().ifBlank { "Downloaded" }
            val offline = PlayableSource(label = "Downloaded · $label", url = item.localConfiguration?.uri?.toString().orEmpty())
            _uiState.value = _uiState.value.copy(isLoadingSources = false, sources = listOf(offline), selectedSource = offline, error = null)
            val factory = DefaultMediaSourceFactory(repository.offlineDataSourceFactory)
            val subtitleList = item.localConfiguration?.subtitleConfigurations.orEmpty()
            val build: (List<MediaItem.SubtitleConfiguration>, Long) -> Unit = { subtitles, positionMs ->
                player.setMediaSource(factory.createMediaSource(item.buildUpon().setSubtitleConfigurations(subtitles).build()), positionMs)
                player.prepare()
                playWhenVisible()
            }
            subtitleOptions = subtitleList
            subtitleStage = 0
            rebuildPlayback = build
            build(subtitleList, startMs)
        }
    }

    /** Looks up AniSkip's intro/outro times for the episode; the skip button simply stays away if there are none. */
    private fun loadSkipTimes(anime: SAnime, episode: SEpisode) {
        skipJob?.cancel()
        aniSkipSegments = emptyList()
        sourceSkipSegments = emptyList()
        if (_uiState.value.skipSegments.isNotEmpty()) _uiState.value = _uiState.value.copy(skipSegments = emptyList())
        val anilistId = anime.anilistId ?: return
        val aniList = aniListRepository ?: return
        val aniSkip = aniSkipRepository ?: return
        skipJob = viewModelScope.launch {
            val malId = aniList.getMalId(anilistId)
            if (malId == null) {
                diagnostics?.log(LogCategory.PLAYBACK, "AniSkip: ${anime.title} has no MyAnimeList id on AniList, so there is nothing to look up")
                return@launch
            }
            val number = episode.episodeNumber.toInt()
            // First without the episode length (not known yet); if AniSkip has nothing, ask again once the
            // player knows how long the video is, because AniSkip uses it to pick the matching submission.
            var segments = aniSkip.getSkipTimes(malId, number)
            if (segments.isEmpty()) {
                var seconds = 0.0
                for (attempt in 0 until 60) {
                    seconds = player.duration.takeIf { it > 0 }?.let { it / 1000.0 } ?: 0.0
                    if (seconds > 0) break
                    kotlinx.coroutines.delay(1_000L)
                }
                if (seconds > 0) segments = aniSkip.getSkipTimes(malId, number, seconds)
            }
            diagnostics?.log(LogCategory.PLAYBACK, "AniSkip: ${segments.size} segment(s) for ${anime.title} E${episode.episodeNumber} (MAL $malId)")
            if (_uiState.value.episode?.id == episode.id) {
                aniSkipSegments = segments
                mergeSkipSegments()
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

    /** The saved position for this episode in ms, or 0 when it was not started, is finished, or is another episode. */
    private suspend fun savedPositionMs(anime: SAnime, episode: SEpisode): Long {
        val resumePoint = anime.anilistId?.let { continueWatchingRepository.resumePointFor(it) } ?: return 0L
        val sameEpisode = resumePoint.episodeId == episode.id || resumePoint.episodeNumber == episode.episodeNumber
        val finished = resumePoint.durationSeconds > 0 && resumePoint.positionSeconds >= resumePoint.durationSeconds * 0.9
        return if (sameEpisode && !finished && resumePoint.positionSeconds > 5) resumePoint.positionSeconds * 1000 else 0L
    }

    private suspend fun resolveSources(anime: SAnime, episode: SEpisode, onBatch: (List<PlayableSource>) -> Unit) {
        when (val origin = anime.origin) {
            is MediaOrigin.Extension -> {
                // If the episode came from continue-watching and has no alternates, re-match all sources
                // to find every possible stream.
                val enrichedEpisode = if (episode.alternates.isEmpty() && episode.id.isNotBlank()) {
                    try {
                        val allEpisodes = extensionManager.findEpisodesByTitle(
                            anime,
                            extraTitlesProvider = { anime.anilistId?.let { id -> aniListRepository?.getAllTitles(id) }.orEmpty() },
                        )
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
                expectedSourceIds = candidates.map { it.sourceId ?: origin.sourceId }
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
                                        val mapped = videos.map { video -> video.toPlayable().copy(sourceId = source.id, extensionName = source.name) }
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
        }
    }

    private fun com.ansu.anime.core.model.Video.toPlayable(): PlayableSource = PlayableSource(
        label = if (sourceLabel.isBlank() || quality.contains(sourceLabel, true)) quality else "$sourceLabel · $quality",
        url = url,
        headers = headers,
        subtitles = subtitleTracks,
        audioTracks = audioTracks,
        resolve = resolve?.let { pending -> suspend { pending()?.toPlayable() } },
        serverName = serverName,
        skipSegments = skipSegments,
    )

    @androidx.annotation.OptIn(UnstableApi::class)
    /**
     * The source picked in this session (by the person or by a fallback), kept so the next episode starts on the
     * same extension and server instead of going back to the ranking. Used ahead of the 3 day memory.
     */
    private var sessionSource: Pair<Int?, com.ansu.anime.data.repository.LastPlayedSource>? = null

    /** The person chose [source] in the Sources panel: it plays now and is kept for this show's next episodes. */
    fun pickSource(source: PlayableSource) {
        val anilistId = _uiState.value.anime?.anilistId
        val sourceId = source.sourceId
        if (anilistId != null && sourceId != null) playerPrefs?.setManualPick(anilistId, sourceId, source.serverName, source.label)
        // Chosen inside the player, the video is already on screen: no buffering screen over it.
        userPicked = true
        selectSource(source, showStartingScreen = false)
    }

    /**
     * A stream that never errors and never gets ready (a dead segment host, a link that hangs) used to leave the
     * buffering screen up forever, because only errors moved on to another source. After [START_STALL_MS] without
     * reaching "ready" the next listed source is tried, or, for a stream the person picked, they are told.
     */
    private fun armStartWatchdog(source: PlayableSource) {
        startWatchdog?.cancel()
        startWatchdog = viewModelScope.launch {
            delay(START_STALL_MS)
            if (player.playbackState == Player.STATE_READY || backgrounded) return@launch
            diagnostics?.log(LogCategory.PLAYBACK, "'${source.label}' did not get ready in ${START_STALL_MS / 1000}s")
            val list = _uiState.value.sources
            val next = nextAfter(list, currentIndexIn(list))
            if (!userPicked && next != null) {
                selectSource(next)
            } else {
                // Telling the person the source failed while it keeps loading (and then plays behind the message)
                // is the bug: stop it so the message and the player agree.
                player.stop()
                _uiState.value = _uiState.value.copy(
                    isStarting = false,
                    isBuffering = false,
                    error = "'${source.label}' is not responding. Pick another source.",
                )
            }
        }
    }

    fun selectSource(source: PlayableSource, showStartingScreen: Boolean = true) {
        startWatchdog?.cancel()
        listedSelection = source
        diagnostics?.log(LogCategory.PLAYBACK, "Source selected: ${source.label} → ${source.url.ifBlank { "(resolved when played)" }}")
        source.sourceId?.let { id ->
            sessionSource = _uiState.value.anime?.anilistId to com.ansu.anime.data.repository.LastPlayedSource(id, source.serverName, source.label)
        }
        // Already playing this very source (a re-select after it started): nothing is starting, so don't raise the screen.
        val alreadyPlaying = player.isPlaying && player.playbackState == Player.STATE_READY && _uiState.value.selectedSource?.url == source.url
        _uiState.value = _uiState.value.copy(
            selectedSource = source,
            selectedListed = source,
            isStarting = if (alreadyPlaying) false else (showStartingScreen || _uiState.value.isStarting),
        )
        // Switching source (or falling back to the next one) carries on from where the video is now.
        if (player.mediaItemCount > 0 && player.currentPosition > 2_000L) startPositionMs = player.currentPosition

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
        // An automatic pick opens the best few candidates at once and plays whichever answers first; a stream the
        // person picked by hand is resolved on its own.
        val rivals = if (!userPicked && playerPrefs?.raceSources?.value != false) raceRivals(source) else emptyList()
        var origin = source
        val resolved: PlayableSource? = if (rivals.isEmpty()) {
            try {
                kotlinx.coroutines.withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { pending() }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Throwable) {
                diagnostics?.log(LogCategory.PLAYBACK, "Resolving '${source.label}' failed: ${e::class.simpleName}: ${e.message}", e)
                null
            }
        } else {
            diagnostics?.log(LogCategory.PLAYBACK, "Racing ${rivals.size + 1} sources: ${(listOf(source) + rivals).joinToString { it.label }}")
            val won = raceResolve(source, rivals)
            if (won != null) origin = won.first
            won?.second
        }
        if (resolved == null || resolved.url.isBlank()) {
            diagnostics?.log(LogCategory.PLAYBACK, "'${source.label}' could not be resolved")
            (listOf(source) + rivals).forEach { StreamFailures.mark(it.sourceId, it.serverName) }
            val list = _uiState.value.sources
            val next = nextAfter(list, list.indexOf(source))
            if (next != null) { userPicked = false; selectSource(next) }
            else _uiState.value = _uiState.value.copy(error = "Could not open '${source.label}'")
            return null
        }
        if (origin !== source) {
            diagnostics?.log(LogCategory.PLAYBACK, "'${origin.label}' answered first and plays")
            listedSelection = origin
            _uiState.value = _uiState.value.copy(selectedListed = origin)
            origin.sourceId?.let { id ->
                sessionSource = _uiState.value.anime?.anilistId to com.ansu.anime.data.repository.LastPlayedSource(id, origin.serverName, origin.label)
            }
        }
        // The resolved video replaces the listed one, so picking it again is instant. It keeps the extension and
        // server names of the listed one, so its card in the Sources list does not change when it is picked.
        val kept = resolved.copy(
            sourceId = origin.sourceId ?: resolved.sourceId,
            extensionName = origin.extensionName ?: resolved.extensionName,
            serverName = origin.serverName ?: resolved.serverName,
        )
        _uiState.value = _uiState.value.copy(
            sources = _uiState.value.sources.map { if (it == origin) kept else it },
            selectedSource = kept,
            selectedListed = kept,
        )
        return kept
    }

    /**
     * Up to two more streams worth opening alongside [source]: not yet resolved, on another server, in the same
     * language, at least as close to the preferred quality, and not one that failed a moment ago.
     */
    private fun raceRivals(source: PlayableSource): List<PlayableSource> {
        val language = playerPrefs?.preferredLanguage?.value ?: com.ansu.anime.data.prefs.PlayerPrefs.LANGUAGE_SUB
        val quality = playerPrefs?.preferredQuality?.value ?: com.ansu.anime.data.prefs.PlayerPrefs.DEFAULT_QUALITY
        val anyQuality = quality == com.ansu.anime.data.prefs.PlayerPrefs.QUALITY_ANY
        val ownMiss = source.qualityMiss(quality)
        return _uiState.value.sources
            .asSequence()
            .filter { it !== source && it.resolve != null && it.url.isBlank() }
            .filter { it.sourceId != source.sourceId || it.serverName != source.serverName }
            .filter { it.languageMiss(language) == source.languageMiss(language) }
            .filter { anyQuality || it.qualityMiss(quality) <= ownMiss }
            .filterNot { StreamFailures.isRecentlyFailed(it.sourceId, it.serverName) }
            .distinctBy { it.sourceId to it.serverName }
            .take(MAX_RACE_RIVALS)
            .toList()
    }

    /**
     * Resolves [primary] and [rivals] at the same time. The first one to give a playable link wins and the rest are
     * cancelled; null when none of them did. They run outside this coroutine's scope on purpose: an extension that
     * blocks on the network cannot be interrupted, and waiting for it would hold the winner back.
     */
    private suspend fun raceResolve(
        primary: PlayableSource,
        rivals: List<PlayableSource>,
    ): Pair<PlayableSource, PlayableSource>? {
        val entries = listOf(primary) + rivals
        val winner = kotlinx.coroutines.CompletableDeferred<Pair<PlayableSource, PlayableSource>?>()
        val remaining = java.util.concurrent.atomic.AtomicInteger(entries.size)
        val jobs = entries.map { candidate ->
            viewModelScope.launch(Dispatchers.IO) {
                val result = try {
                    kotlinx.coroutines.withTimeoutOrNull(RESOLVE_TIMEOUT_MS) { candidate.resolve?.invoke() }
                } catch (e: kotlinx.coroutines.CancellationException) {
                    throw e
                } catch (e: Throwable) {
                    diagnostics?.log(LogCategory.PLAYBACK, "Resolving '${candidate.label}' failed: ${e::class.simpleName}: ${e.message}")
                    null
                }
                if (result != null && result.url.isNotBlank()) {
                    winner.complete(candidate to result)
                } else {
                    StreamFailures.mark(candidate.sourceId, candidate.serverName)
                    if (remaining.decrementAndGet() == 0) winner.complete(null)
                }
            }
        }
        return try {
            winner.await()
        } finally {
            jobs.forEach { it.cancel() }
        }
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
        // A new stream: its subtitle tracks get the person's session choice again once they are reported.
        subtitleChoiceApplied = false
        // The stream's own intro/outro times (when its source gives them) win over AniSkip's.
        sourceSkipSegments = source.skipSegments
        if (source.skipSegments.isNotEmpty()) {
            diagnostics?.log(LogCategory.PLAYBACK, "Skip times from the source itself: ${source.skipSegments.joinToString { it.type.name.lowercase() }}")
        }
        mergeSkipSegments()
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
        
        val startMs = if (startPositionMs > 0L) startPositionMs else C.TIME_UNSET
        startPositionMs = 0L
        val subtitleList = mediaItem.localConfiguration?.subtitleConfigurations.orEmpty()
        // Built by a function so a subtitle track that crashes the player can be dropped and the stream started again.
        val build: (List<MediaItem.SubtitleConfiguration>, Long) -> Unit = { subtitles, positionMs ->
            val item = mediaItem.buildUpon().setSubtitleConfigurations(subtitles).build()
            if (source.headers.isEmpty() && source.audioTracks.isEmpty()) {
                player.setMediaItem(item, positionMs)
            } else {
                // Extension sites usually insist on a Referer/User-Agent; send what the source asked for.
                val upstream = mediaCache?.upstreamWithHeaders(source.headers) ?: DefaultHttpDataSource.Factory()
                    .setAllowCrossProtocolRedirects(true)
                    .setConnectTimeoutMs(15_000)
                    .setReadTimeoutMs(15_000)
                    .setDefaultRequestProperties(source.headers)
                // Read through the shared cache too, so a warmed episode plays from disk here as well.
                val dataSource = mediaCache?.dataSourceFactory(upstream) ?: upstream
                val factory = DefaultMediaSourceFactory(dataSource)
                val main = factory.createMediaSource(item)
                // External audio streams (dubs) are merged into the video; the player lists them as audio tracks.
                val audio = source.audioTracks.map { track ->
                    factory.createMediaSource(
                        MediaItem.Builder()
                            .setUri(track.url)
                            .apply { detectMimeType(track.url)?.let { setMimeType(it) } }
                            .build(),
                    )
                }
                player.setMediaSource(if (audio.isEmpty()) main else MergingMediaSource(main, *audio.toTypedArray()), positionMs)
            }
            player.prepare()
            playWhenVisible()
        }
        subtitleOptions = subtitleList
        subtitleStage = 0
        rebuildPlayback = build
        build(subtitleList, startMs)
        armStartWatchdog(source)
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
                val channels = when (format.channelCount) {
                    1 -> "Mono"
                    2 -> "Stereo"
                    6 -> "5.1"
                    8 -> "7.1"
                    else -> null
                }
                val bitrate = format.bitrate.takeIf { it > 0 }?.let { "${it / 1000} kbps" }
                val detail = if (type == C.TRACK_TYPE_AUDIO) listOfNotNull(channels, bitrate).joinToString(" \u00B7 ") else ""
                out += TrackOption(groupIndex, trackIndex, label, group.isTrackSelected(trackIndex), detail)
            }
        }
        return out
    }

    fun selectAudioTrack(option: TrackOption) = overrideTrack(option)

    /**
     * Subtitles follow the person's choice for as long as the app runs: on (with the track they last picked) unless they turned
     * them off, and on by default the first time a stream has subtitles. Done once per stream, when its tracks are first listed.
     */
    private var subtitleChoiceApplied = false

    private fun applySessionSubtitleChoice(options: List<TrackOption>) {
        if (subtitleChoiceApplied || options.isEmpty()) return
        subtitleChoiceApplied = true
        if (sessionSubtitlesOn == false) {
            showTextTrack(null)
            return
        }
        val pick = sessionSubtitleLabel?.let { label -> options.firstOrNull { it.label == label } }
            ?: options.firstOrNull { it.label.contains("english", ignoreCase = true) }
            ?: options.first()
        showTextTrack(pick)
    }

    /** Picks a subtitle track, or turns subtitles off when [option] is null; the choice is remembered for the session. */
    fun selectTextTrack(option: TrackOption?) {
        sessionSubtitlesOn = option != null
        if (option != null) sessionSubtitleLabel = option.label
        showTextTrack(option)
    }

    private fun showTextTrack(option: TrackOption?) {
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

    /**
     * Moves the subtitles by [offsetMs]: positive = later (cues are held back), negative = earlier (the text renderer
     * is told the position is ahead, see [SubtitleLeadRenderersFactory]).
     */
    fun setSubtitleOffset(offsetMs: Long) {
        val offset = offsetMs.coerceIn(-MAX_SUBTITLE_OFFSET_MS, MAX_SUBTITLE_OFFSET_MS)
        subtitleLead.leadMs = (-offset).coerceAtLeast(0L)
        _uiState.value = _uiState.value.copy(subtitleOffsetMs = offset)
    }

    /** Times a tiny request to every listed stream that has not been timed yet, for the latency shown in the Sources panel. */
    fun measurePings() {
        val known = _uiState.value.pings
        val targets = _uiState.value.sources.filter { it.url.isNotBlank() && it.url !in known }
        if (targets.isEmpty()) return
        pingRuns++
        _uiState.value = _uiState.value.copy(isMeasuringPings = true)
        viewModelScope.launch {
            try {
                kotlinx.coroutines.coroutineScope {
                    targets.map { source ->
                        async {
                            val ms = pingOf(source)
                            _uiState.value = _uiState.value.copy(pings = _uiState.value.pings + (source.url to ms))
                        }
                    }.awaitAll()
                }
            } finally {
                pingRuns--
                _uiState.value = _uiState.value.copy(isMeasuringPings = pingRuns > 0)
            }
        }
    }

    /** Retry in the Sources panel: asks for the latencies again; when no stream was found at all, resolves the sources again. */
    fun retrySources() {
        if (_uiState.value.sources.isEmpty() && !_uiState.value.isLoadingSources) {
            _uiState.value = _uiState.value.copy(isLoadingSources = true, error = null)
            loadSources()
        } else {
            _uiState.value = _uiState.value.copy(pings = emptyMap())
            measurePings()
        }
    }

    private suspend fun pingOf(source: PlayableSource): Long = withContext(Dispatchers.IO) {
        try {
            val request = okhttp3.Request.Builder()
                .url(source.url)
                .apply { source.headers.forEach { (name, value) -> header(name, value) } }
                .header("Range", "bytes=0-0")
                .build()
            val started = System.nanoTime()
            probeClient.newCall(request).execute().use { response ->
                if (response.code >= 500) -1L else (System.nanoTime() - started) / 1_000_000L
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            -1L
        }
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
        startPositionMs = 0L
        selectionHolder.selectEpisode(target)
        _uiState.value = _uiState.value.copy(
            episode = target,
            sources = emptyList(),
            selectedSource = null,
            selectedListed = null,
            isLoadingSources = true,
            error = null,
            positionMs = 0L,
            durationMs = 0L,
            audioOptions = emptyList(),
            textOptions = emptyList(),
            skipSegments = emptyList(),
            isStarting = true,
        )
        aniSkipSegments = emptyList()
        sourceSkipSegments = emptyList()
        loadSources()
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
                // Safety net: if the stream is playing (audio is coming out) the buffering screen must not stay up,
                // even when the first-frame / is-playing callback fired before the screen was flagged as starting.
                val reallyPlaying = player.isPlaying && player.playbackState == Player.STATE_READY
                _uiState.value = _uiState.value.copy(
                    positionMs = player.currentPosition,
                    durationMs = duration,
                    isStarting = if (reallyPlaying) false else _uiState.value.isStarting,
                )

                val anime = _uiState.value.anime
                val episode = _uiState.value.episode
                if (anime != null && episode != null && duration > 0) {
                    val playing = _uiState.value.selectedSource
                    val anilistId = anime.anilistId
                    if (player.isPlaying && playing?.sourceId != null && anilistId != null) {
                        continueWatchingRepository.rememberSource(anilistId, playing.sourceId, playing.serverName, playing.label)
                    }
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

    private companion object {
        /** The person's subtitle choice for this run of the app: null = never chosen (subtitles default to on). */
        @Volatile
        var sessionSubtitlesOn: Boolean? = null

        /** The label of the subtitle track they last picked, used again on the next stream that offers it. */
        @Volatile
        var sessionSubtitleLabel: String? = null
    }
}
