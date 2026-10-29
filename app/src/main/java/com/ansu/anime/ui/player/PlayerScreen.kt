package com.ansu.anime.ui.player

import android.app.Activity
import android.content.Context
import android.widget.Toast
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.util.TypedValue
import android.view.View
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.core.app.PictureInPictureModeChangedInfo
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ansu.anime.MainActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Box
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.fragment.app.FragmentActivity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.PlayerView
import com.ansu.anime.cast.CastController
import com.ansu.anime.cast.isCastableUrl
import androidx.media3.ui.SubtitleView
import androidx.navigation.NavHostController
import com.ansu.anime.data.repository.AnimeArtwork
import com.ansu.anime.di.AppContainer
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import kotlin.math.pow

private enum class PlayerSheet { SOURCES, SUBS, AUDIO, EPISODES, SETTINGS }

private val FitModes = listOf(
    "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
    "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)
/** How long the side panel takes to slide in and out; the video, the pill and the seek bar move in step with it. */
private const val PANEL_ANIMATION_MS = 380

/** How long the skip button stays on screen before it hides itself. */
private const val SKIP_BUTTON_SECONDS = 10

/** How long the video must sit paused before the pause screen fades in. */
private const val PAUSE_SCREEN_DELAY_MS = 2500L

private val Speeds = listOf(0.5f, 1f, 1.25f, 1.5f, 2f)

private fun Float.speedLabel(): String = if (this % 1f == 0f) "${toInt()}x" else "${this}x"

private fun Context.findActivity(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@Composable
fun PlayerScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    val viewModel: PlayerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                PlayerViewModel(
                    context.applicationContext,
                    container.extensionManager,
                    container.continueWatchingRepository,
                    container.selectionHolder,
                    container.diagnostics,
                    container.aniListRepository,
                    container.aniSkipRepository,
                    container.playerPrefs,
                    container.mediaCache,
                    container.episodeMetadataRepository,
                    container.downloads,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val castButtonEnabled by container.playerPrefs.castButton.collectAsStateWithLifecycle()
    val cast = remember { CastController(context.applicationContext) }
    val castState by cast.state.collectAsStateWithLifecycle()
    val playbackTarget by viewModel.playbackTarget.collectAsStateWithLifecycle()
    DisposableEffect(cast) {
        cast.attach()
        onDispose { cast.release() }
    }
    // A session is up: the phone's own player stays paused and the stream goes to the TV, again each time the stream
    // changes (another episode or source) while casting.
    LaunchedEffect(castState.connected, playbackTarget) {
        val target = playbackTarget
        if (!castState.connected || target == null) return@LaunchedEffect
        viewModel.player.pause()
        if (!isCastableUrl(target.url)) {
            Toast.makeText(context, "This stream is stored on your phone, so a TV can't play it", Toast.LENGTH_LONG).show()
            return@LaunchedEffect
        }
        if (target.headers.isNotEmpty()) {
            Toast.makeText(context, "This source may not play on a TV; if it doesn't, pick another source", Toast.LENGTH_LONG).show()
        }
        cast.load(
            url = target.url,
            mimeType = target.mimeType ?: "video/mp4",
            headers = target.headers,
            subtitles = target.subtitles,
            title = state.anime?.title.orEmpty(),
            subtitle = episodeLine(state.episode),
            imageUrl = state.anime?.posterUrl,
            positionMs = viewModel.player.currentPosition,
        ) { ok ->
            if (!ok) Toast.makeText(context, "The TV could not play this stream", Toast.LENGTH_LONG).show()
        }
    }
    LaunchedEffect(castState.connected, state.isPlaying) {
        if (castState.connected && state.isPlaying) viewModel.player.pause()
    }
    // The session ended (Stop casting, or the TV went away): carry on from where the TV was.
    var wasCasting by remember { mutableStateOf(false) }
    LaunchedEffect(castState.connected) {
        if (castState.connected) {
            wasCasting = true
        } else if (wasCasting) {
            wasCasting = false
            cast.takeResume()?.let { resume ->
                viewModel.seekTo(resume.positionMs)
                if (resume.playing) viewModel.player.play()
            }
        }
    }
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    // Landscape: Sources / Audio / Subtitles open as a small tab on the right instead of a bottom sheet.
    var panel by remember { mutableStateOf<PlayerPanel?>(null) }
    var lastPanel by remember { mutableStateOf(PlayerPanel.SOURCES) }
    val panelProgress = remember { Animatable(0f) }
    val panelVisible by remember { derivedStateOf { panelProgress.value > 0f } }
    val cues by viewModel.cues.collectAsStateWithLifecycle()
    val subtitleSize by container.playerPrefs.subtitleSize.collectAsStateWithLifecycle()
    val subtitleHeight by container.playerPrefs.subtitleHeight.collectAsStateWithLifecycle()
    val episodeGrid by container.playerPrefs.episodeGrid.collectAsStateWithLifecycle()
    val pipEnabled by container.playerPrefs.pipEnabled.collectAsStateWithLifecycle()
    val ambientMode by container.playerPrefs.ambientMode.collectAsStateWithLifecycle()
    val subtitleColor by container.playerPrefs.subtitleColor.collectAsStateWithLifecycle()
    val subtitleBgColor by container.playerPrefs.subtitleBgColor.collectAsStateWithLifecycle()
    val subtitleBgOpacity by container.playerPrefs.subtitleBgOpacity.collectAsStateWithLifecycle()
    val subtitleRoundedBg by container.playerPrefs.subtitleRoundedBg.collectAsStateWithLifecycle()
    val subtitleAutoScale by container.playerPrefs.subtitleAutoScale.collectAsStateWithLifecycle()
    val pauseScreenEnabled by container.playerPrefs.pauseScreen.collectAsStateWithLifecycle()
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()
    var locked by remember { mutableStateOf(false) }
    var fitIndex by remember { mutableIntStateOf(0) }
    val doubleTapSeek by container.playerPrefs.doubleTapSeek.collectAsStateWithLifecycle()
    val swipeSeek by container.playerPrefs.swipeSeek.collectAsStateWithLifecycle()
    val holdSpeed by container.playerPrefs.holdSpeed.collectAsStateWithLifecycle()
    val brightnessGesture by container.playerPrefs.brightnessGesture.collectAsStateWithLifecycle()
    val volumeGesture by container.playerPrefs.volumeGesture.collectAsStateWithLifecycle()
    val skipSeconds by container.playerPrefs.skipSeconds.collectAsStateWithLifecycle()
    val autoplayNext by container.playerPrefs.autoplayNext.collectAsStateWithLifecycle()
    val hideEpisodeButtons by container.playerPrefs.hideEpisodeButtons.collectAsStateWithLifecycle()
    val seekBarStyleKey by container.playerPrefs.seekBarStyle.collectAsStateWithLifecycle()
    val gestureConfig = PlayerGestureConfig(doubleTapSeek, brightnessGesture, volumeGesture, skipSeconds, enabled = !locked, swipeSeek = swipeSeek, holdSpeed = holdSpeed)
    // Hold to speed: remember the speed before the hold and put it back on release.
    var speedBeforeHold by remember { mutableStateOf<Float?>(null) }
    val onHoldSpeed: (Boolean) -> Unit = { active ->
        if (active) {
            if (speedBeforeHold == null) speedBeforeHold = state.speed
            viewModel.setSpeed(HOLD_SPEED)
        } else {
            speedBeforeHold?.let { viewModel.setSpeed(it) }
            speedBeforeHold = null
        }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val panelWidth = panelWidthDp(configuration.screenWidthDp)

    LaunchedEffect(panel) {
        panel?.let { lastPanel = it }
        panelProgress.animateTo(
            targetValue = if (panel != null) 1f else 0f,
            animationSpec = tween(durationMillis = PANEL_ANIMATION_MS, easing = FastOutSlowInEasing),
        )
    }
    // Rotating to portrait has no side panel: close it (the portrait layout keeps its bottom sheets).
    LaunchedEffect(isLandscape) { if (!isLandscape) panel = null }
    // Latencies are measured when the Sources panel opens and as new streams arrive while it is open.
    LaunchedEffect(panel, sheet, state.sources.size) {
        if (panel == PlayerPanel.SOURCES || sheet == PlayerSheet.SOURCES) viewModel.measurePings()
    }
    BackHandler(enabled = panel != null) { panel = null }

    // Picture-in-picture: whether the app is currently shrunk into the floating window.
    var inPip by remember { mutableStateOf(false) }
    DisposableEffect(activity) {
        val owner = activity as? ComponentActivity
        val listener = Consumer<PictureInPictureModeChangedInfo> { info ->
            inPip = info.isInPictureInPictureMode
            // Leaving picture-in-picture while the app is not on screen means the floating window was closed
            // (an expand would have brought the app back to the foreground): stop the video.
            if (!info.isInPictureInPictureMode && owner?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.STARTED) == false) {
                viewModel.onBackgrounded()
            }
        }
        owner?.addOnPictureInPictureModeChangedListener(listener)
        onDispose { owner?.removeOnPictureInPictureModeChangedListener(listener) }
    }
    // The floating window shows the bare video: close panels and sheets.
    LaunchedEffect(inPip) {
        if (inPip) {
            panel = null
            sheet = null
        }
    }
    // Home / the gesture enters picture-in-picture only while a video is actually playing and the setting is on.
    val pipEligible = pipEnabled && state.isPlaying && state.error == null
    DisposableEffect(pipEligible, state.isPlaying) {
        (activity as? MainActivity)?.setPipState(pipEligible, state.isPlaying)
        onDispose { (activity as? MainActivity)?.setPipState(false, false) }
    }
    // The floating window's play/pause button.
    DisposableEffect(activity) {
        (activity as? MainActivity)?.pipToggle = { viewModel.togglePlayPause() }
        onDispose { (activity as? MainActivity)?.pipToggle = null }
    }
    // Leaving the app (Home, switching apps, locking the screen) must not leave the video playing unheard in the
    // background. Picture-in-picture is the one way out that keeps playing, because its window is still visible.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, activity) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP && activity?.isInPictureInPictureMode != true) {
                viewModel.onBackgrounded()
            }
            // Back on screen: a video that was still loading when the app was left starts now.
            if (event == Lifecycle.Event.ON_START) viewModel.onForegrounded()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // The fullscreen layout is the main one, so the player opens in landscape; the rotate button
    // switches to the portrait layout (player on top, episode info and list below).
    DisposableEffect(Unit) {
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    // Immersive in landscape; normal system bars in portrait and when leaving.
    DisposableEffect(isLandscape) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (isLandscape) {
                controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {
            window?.let { WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars()) }
        }
    }

    // Keep the screen awake while a stream is playing (or buffering towards playing) so the phone's sleep timer
    // cannot blank it mid-episode. Once the video is paused, or the player is left, the flag is cleared and the
    // normal screen timeout applies again.
    val keepScreenOn = (state.isPlaying || state.isBuffering) && state.error == null
    DisposableEffect(keepScreenOn) {
        val window = activity?.window
        if (keepScreenOn) window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    // Auto-hide controls after a few seconds of inactivity (not while a bottom sheet is open). A side panel does not
    // keep them up: the video shrinks beside it and its controls leave after 3 seconds like normal.
    LaunchedEffect(state.showControls, state.isPlaying, sheet, panel) {
        if (state.showControls && state.isPlaying && sheet == null) {
            delay(3500)
            viewModel.toggleControls()
        }
    }

    // Pause screen: once the video has sat paused for a moment (and nothing else is open over it), the controls
    // leave and the picture dims under the title being watched. Touching the screen only dismisses it; anything that restarts
    // playback, a seek, an opened panel or any touch puts the timer back to zero. A touch on the pause screen only
    // takes it away (and shows the controls); it never starts the video, the play button does.
    val resting = !state.isPlaying && !state.isBuffering && !state.isLoadingSources && state.error == null && state.durationMs > 0L
    val pauseEligible = pauseScreenEnabled && resting && panel == null && sheet == null && !locked && !inPip && !castState.connected
    var pauseIdle by remember { mutableStateOf(false) }
    // Counts touches anywhere on the player (see the root layout): every one puts the pause screen's timer back to zero.
    var pauseTouchTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(pauseEligible, state.positionMs, pauseTouchTick) {
        pauseIdle = false
        if (pauseEligible) {
            delay(PAUSE_SCREEN_DELAY_MS)
            pauseIdle = true
        }
    }
    val showPauseScreen = pauseEligible && pauseIdle
    LaunchedEffect(showPauseScreen) {
        if (showPauseScreen && state.showControls) viewModel.toggleControls()
    }
    val anilistIdForArt = state.anime?.anilistId
    val pausedArtwork by produceState<AnimeArtwork?>(initialValue = null, anilistIdForArt, pauseScreenEnabled) {
        value = if (pauseScreenEnabled) anilistIdForArt?.let { container.artworkRepository.get(it) } else null
    }
    val pausedInfo = remember(state.anime, state.episode, pausedArtwork, titleLanguage) {
        buildPausedInfo(state.anime, state.episode, pausedArtwork, titleLanguage)
    }
    // Touching the pause screen brings the controls up instead of resuming; the timer restarts from the touch.
    val onPauseScreenTouch = { if (!state.showControls) viewModel.toggleControls() }
    // The buffering screen: shown from the moment a stream is chosen until its first picture is out.
    val startingArtwork by produceState<AnimeArtwork?>(initialValue = null, anilistIdForArt) {
        value = anilistIdForArt?.let { container.artworkRepository.get(it) }
    }
    val startingInfo = remember(state.anime, state.episode, startingArtwork, titleLanguage) {
        buildPausedInfo(state.anime, state.episode, startingArtwork, titleLanguage)
    }
    val startingBackdrop = startingBackdrop(startingArtwork, state.anime?.bannerUrl ?: state.anime?.posterUrl)
    val showStarting = !inPip && state.error == null && (state.isLoadingSources || state.isStarting)

    val hasPrev = remember(state.episode, state.episodes) { viewModel.previousEpisode() != null }
    val hasNext = remember(state.episode, state.episodes) { viewModel.nextEpisode() != null }
    // The skip button only appears inside an intro/outro/recap segment that AniSkip has timed. It shows for
    // 10 seconds (the bar in the button fills up over that time), then hides; tapping the screen brings it back.
    val activeSegment = state.skipSegments.firstOrNull { state.positionMs in it.startMs until it.endMs - 500 }
    var skipShown by remember { mutableStateOf(false) }
    var skipTapTick by remember { mutableIntStateOf(0) }
    val skipProgress = remember { Animatable(0f) }
    LaunchedEffect(activeSegment, skipTapTick) {
        if (activeSegment == null) {
            skipShown = false
            return@LaunchedEffect
        }
        skipShown = true
        skipProgress.snapTo(0f)
        skipProgress.animateTo(1f, tween(durationMillis = SKIP_BUTTON_SECONDS * 1000, easing = LinearEasing))
        skipShown = false
    }
    val showSkipOutro = activeSegment != null && skipShown
    val skipLabel = activeSegment?.type?.buttonLabel ?: "Skip"
    // A tap on the video never closes an open panel (only its close button and Back do); it just shows or hides controls.
    val onScreenTap = {
        viewModel.toggleControls()
        if (activeSegment != null) skipTapTick++
    }

    val fitLabel = FitModes[fitIndex].first
    val speedLabel = state.speed.speedLabel()

    val actions = PlayerActions(
        onPlayPause = viewModel::togglePlayPause,
        onSeekBy = viewModel::seekBy,
        onSeekTo = viewModel::seekTo,
        onBack = { navController.popBackStack() },
        onPrevEpisode = { viewModel.previousEpisode()?.let(viewModel::playEpisode) },
        onNextEpisode = { viewModel.nextEpisode()?.let(viewModel::playEpisode) },
        onLock = { locked = !locked },
        onRotate = {
            activity?.requestedOrientation = if (isLandscape) {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            } else {
                ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            }
        },
        onFit = { fitIndex = (fitIndex + 1) % FitModes.size },
        onSpeed = {
            val next = Speeds[(Speeds.indexOf(state.speed).takeIf { it >= 0 } ?: 1).plus(1) % Speeds.size]
            viewModel.setSpeed(next)
        },
        onSubs = { if (isLandscape) panel = PlayerPanel.SUBS else sheet = PlayerSheet.SUBS },
        onAudio = { if (isLandscape) panel = PlayerPanel.AUDIO else sheet = PlayerSheet.AUDIO },
        onSources = { if (isLandscape) panel = PlayerPanel.SOURCES else sheet = PlayerSheet.SOURCES },
        onEpisodes = { if (isLandscape) panel = PlayerPanel.EPISODES else sheet = PlayerSheet.EPISODES },
        onSettings = { sheet = PlayerSheet.SETTINGS },
        onSkipOutro = viewModel::skipCurrentSegment,
        seekSeconds = skipSeconds,
        autoplayNext = autoplayNext,
        onAutoplayNext = container.playerPrefs::setAutoplayNext,
        hideEpisodeButtons = hideEpisodeButtons,
        seekBarStyle = SeekBarStyle.fromKey(seekBarStyleKey),
        showCastButton = castButtonEnabled && castState.available,
        castConnected = castState.connected,
        onCast = { (activity as? FragmentActivity)?.let(cast::showDialog) },
    )

    // The video view of the portrait layout, read by the ambient glow for the colours under the video.
    var portraitPlayerView by remember { mutableStateOf<PlayerView?>(null) }
    val ambientOn = ambientMode && !isLandscape && !inPip
    val ambientColors = rememberAmbientColors(portraitPlayerView, ambientOn, state.isPlaying, state.episode?.id)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Watches every touch without taking it from anything below (initial pass, nothing consumed): each press
            // and release puts the pause screen's timer back to zero.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        if (event.type == PointerEventType.Press || event.type == PointerEventType.Release) pauseTouchTick++
                    }
                }
            },
    ) {
        if (isLandscape) {
            PlayerSurface(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel,
                state = state,
                resizeMode = FitModes[fitIndex].second,
                skipLabel = if (showSkipOutro) skipLabel else null,
                skipProgress = skipProgress.value,
                onSkip = actions.onSkipOutro,
                onTap = onScreenTap,
                onBack = actions.onBack,
                gestures = gestureConfig,
                onHoldSpeed = onHoldSpeed,
                onSeekBy = viewModel::seekBy,
                cues = cues,
                subtitleSize = subtitleSize,
                subtitleHeight = subtitleHeight,
                subtitleTextColor = subtitleColor,
                subtitleBgColor = subtitleBgColor,
                subtitleBgOpacity = subtitleBgOpacity,
                subtitleRoundedBg = subtitleRoundedBg,
                subtitleAutoScale = subtitleAutoScale,
                panelWidth = panelWidth,
                panelProgress = { panelProgress.value },
            ) {
                if (inPip) {
                    Unit
                } else if (castState.connected) {
                    CastOverlay(
                        state = castState,
                        title = state.anime?.title.orEmpty(),
                        episode = episodeLine(state.episode),
                        seekSeconds = skipSeconds,
                        onSubtitle = cast::selectSubtitle,
                        onBack = actions.onBack,
                        onDevice = actions.onCast,
                        onPlayPause = cast::togglePlayPause,
                        onSeekBy = cast::seekBy,
                        onSeekTo = cast::seekTo,
                        onDisconnect = cast::disconnect,
                        compact = false,
                    )
                } else if (locked) {
                    LockedOverlay(onUnlock = { locked = false }, isBuffering = state.isBuffering)
                } else {
                    PlayerControlsOverlay(
                        state = state,
                        actions = actions,
                        fitLabel = fitLabel,
                        speedLabel = speedLabel,
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        showSkipOutro = showSkipOutro,
                        skipLabel = skipLabel,
                        skipProgress = skipProgress.value,
                        panelProgress = { panelProgress.value },
                    )
                }
            }
        } else {
            if (ambientOn) {
                AmbientGlowBackdrop(
                    colors = ambientColors,
                    topInset = WindowInsets.statusBars.getTop(LocalDensity.current),
                )
            }
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                PlayerSurface(
                    modifier = Modifier.fillMaxSize(),
                    viewModel = viewModel,
                    state = state,
                    resizeMode = FitModes[fitIndex].second,
                skipLabel = if (showSkipOutro) skipLabel else null,
                skipProgress = skipProgress.value,
                onSkip = actions.onSkipOutro,
                    onTap = onScreenTap,
                    onBack = actions.onBack,
                    gestures = gestureConfig,
                    onHoldSpeed = onHoldSpeed,
                    onSeekBy = viewModel::seekBy,
                    cues = cues,
                    subtitleSize = subtitleSize,
                    subtitleHeight = subtitleHeight,
                    subtitleTextColor = subtitleColor,
                    subtitleBgColor = subtitleBgColor,
                    subtitleBgOpacity = subtitleBgOpacity,
                    subtitleRoundedBg = subtitleRoundedBg,
                    subtitleAutoScale = subtitleAutoScale,
                    onPlayerView = { portraitPlayerView = it },
                ) {
                    if (inPip) {
                        Unit
                    } else if (castState.connected) {
                        CastOverlay(
                            state = castState,
                            title = state.anime?.title.orEmpty(),
                            episode = episodeLine(state.episode),
                            seekSeconds = skipSeconds,
                            onSubtitle = cast::selectSubtitle,
                            onBack = actions.onBack,
                            onDevice = actions.onCast,
                            onPlayPause = cast::togglePlayPause,
                            onSeekBy = cast::seekBy,
                            onSeekTo = cast::seekTo,
                            onDisconnect = cast::disconnect,
                            compact = true,
                        )
                    } else if (locked) {
                        LockedOverlay(onUnlock = { locked = false }, isBuffering = state.isBuffering)
                    } else {
                        PlayerControlsCompact(
                            state = state,
                            actions = actions,
                            hasPrev = hasPrev,
                            hasNext = hasNext,
                            showSkipOutro = showSkipOutro,
                        skipLabel = skipLabel,
                        skipProgress = skipProgress.value,
                        )
                    }
                }
                PausedOverlay(visible = showPauseScreen, info = pausedInfo, onTouch = onPauseScreenTouch, compact = true)
                StartingScreen(
                    visible = showStarting,
                    backdropUrl = startingBackdrop,
                    logoUrl = startingInfo.logoUrl,
                    title = startingInfo.title,
                    onBack = { navController.popBackStack() },
                    compact = true,
                )
                }
                if (!inPip) {
                    EpisodeInfoPane(
                        state = state,
                        onPlayEpisode = viewModel::playEpisode,
                        downloads = container.downloads,
                        playerPrefs = container.playerPrefs,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        if (isLandscape) {
            PausedOverlay(visible = showPauseScreen, info = pausedInfo, onTouch = onPauseScreenTouch)
            StartingScreen(
                visible = showStarting,
                backdropUrl = startingBackdrop,
                logoUrl = startingInfo.logoUrl,
                title = startingInfo.title,
                onBack = { navController.popBackStack() },
            )
        }

        if (isLandscape && panelVisible) {
            val panelModifier = Modifier.align(Alignment.CenterEnd).width(panelWidth)
            val progress = { panelProgress.value }
            val close = { panel = null }
            when (panel ?: lastPanel) {
                PlayerPanel.SOURCES -> SourcesPanel(
                    state = state,
                    progress = progress,
                    onSelect = viewModel::pickSource,
                    onRetry = viewModel::retrySources,
                    onClose = close,
                    modifier = panelModifier,
                )
                PlayerPanel.SUBS -> SubtitlesPanel(
                    state = state,
                    subtitleSize = subtitleSize,
                    subtitleHeight = subtitleHeight,
                    progress = progress,
                    onToggle = { on ->
                        if (on) viewModel.selectTextTrack(state.textOptions.firstOrNull { it.selected } ?: state.textOptions.firstOrNull())
                        else viewModel.selectTextTrack(null)
                    },
                    onSelectTrack = viewModel::selectTextTrack,
                    onOffsetChange = viewModel::setSubtitleOffset,
                    onSizeChange = container.playerPrefs::setSubtitleSize,
                    onHeightChange = container.playerPrefs::setSubtitleHeight,
                    textColor = subtitleColor,
                    bgColor = subtitleBgColor,
                    bgOpacity = subtitleBgOpacity,
                    roundedBg = subtitleRoundedBg,
                    onRoundedBg = container.playerPrefs::setSubtitleRoundedBg,
                    onTextColor = container.playerPrefs::setSubtitleColor,
                    onBgColor = container.playerPrefs::setSubtitleBgColor,
                    onBgOpacity = container.playerPrefs::setSubtitleBgOpacity,
                    onClose = close,
                    modifier = panelModifier,
                )
                PlayerPanel.EPISODES -> EpisodesPanel(
                    state = state,
                    progress = progress,
                    grid = episodeGrid,
                    onGridChange = container.playerPrefs::setEpisodeGrid,
                    onPlayEpisode = {
                        viewModel.playEpisode(it)
                        panel = null
                    },
                    onClose = close,
                    modifier = panelModifier,
                )
                PlayerPanel.AUDIO -> AudioPanel(
                    options = state.audioOptions,
                    progress = progress,
                    onSelect = viewModel::selectAudioTrack,
                    onClose = close,
                    modifier = panelModifier,
                )
            }
        }

        when (sheet) {
            // Portrait: the same Sources / Subtitles / Audio content as the landscape panels, in a bottom sheet.
            PlayerSheet.SOURCES -> SourcesPanel(
                state = state,
                progress = { 1f },
                onSelect = { viewModel.pickSource(it); sheet = null },
                onRetry = viewModel::retrySources,
                onClose = { sheet = null },
                sheet = true,
            )
            PlayerSheet.SUBS -> SubtitlesPanel(
                state = state,
                subtitleSize = subtitleSize,
                subtitleHeight = subtitleHeight,
                progress = { 1f },
                onToggle = { on ->
                    if (on) viewModel.selectTextTrack(state.textOptions.firstOrNull { it.selected } ?: state.textOptions.firstOrNull())
                    else viewModel.selectTextTrack(null)
                },
                onSelectTrack = viewModel::selectTextTrack,
                onOffsetChange = viewModel::setSubtitleOffset,
                onSizeChange = container.playerPrefs::setSubtitleSize,
                onHeightChange = container.playerPrefs::setSubtitleHeight,
                textColor = subtitleColor,
                bgColor = subtitleBgColor,
                bgOpacity = subtitleBgOpacity,
                roundedBg = subtitleRoundedBg,
                onRoundedBg = container.playerPrefs::setSubtitleRoundedBg,
                onTextColor = container.playerPrefs::setSubtitleColor,
                onBgColor = container.playerPrefs::setSubtitleBgColor,
                onBgOpacity = container.playerPrefs::setSubtitleBgOpacity,
                onClose = { sheet = null },
                sheet = true,
            )
            PlayerSheet.AUDIO -> AudioPanel(
                options = state.audioOptions,
                progress = { 1f },
                onSelect = viewModel::selectAudioTrack,
                onClose = { sheet = null },
                sheet = true,
            )
            PlayerSheet.EPISODES -> EpisodeListSheet(
                state = state,
                onPlayEpisode = viewModel::playEpisode,
                onDismiss = { sheet = null },
            )
            PlayerSheet.SETTINGS -> SettingsMenuSheet(
                fitLabel = fitLabel,
                speedLabel = speedLabel,
                actions = actions,
                onDismiss = { sheet = null },
            )
            null -> Unit
        }
    }
}

/** The height (dp) of the video picture at which the chosen subtitle size is used as it is: a phone held sideways. */
private const val SUBTITLE_REFERENCE_HEIGHT_DP = 380f

/** How strongly subtitles follow the picture size; below 1 keeps small pictures (portrait) from getting tiny text. */
private const val SUBTITLE_SCALE_CURVE = 0.8

private const val SUBTITLE_MIN_SCALE = 0.5f
private const val SUBTITLE_MAX_SCALE = 1.6f

/** The video, the tap-to-toggle layer, loading/error/buffering states, and the controls [overlay] on top. */
@androidx.annotation.OptIn(UnstableApi::class)
@Composable
private fun PlayerSurface(
    modifier: Modifier,
    viewModel: PlayerViewModel,
    state: PlayerUiState,
    resizeMode: Int,
    onTap: () -> Unit,
    onBack: () -> Unit,
    gestures: PlayerGestureConfig = PlayerGestureConfig(),
    onHoldSpeed: (Boolean) -> Unit = {},
    onSeekBy: (Long) -> Unit = {},
    skipLabel: String? = null,
    skipProgress: Float = 0f,
    onSkip: () -> Unit = {},
    cues: List<Cue> = emptyList(),
    subtitleSize: Int = 18,
    subtitleHeight: Int = 8,
    subtitleTextColor: Int = 0xFFFFFFFF.toInt(),
    subtitleBgColor: Int = 0xFF000000.toInt(),
    subtitleBgOpacity: Int = 50,
    subtitleRoundedBg: Boolean = true,
    /** True: the subtitle text grows and shrinks with the size of the video picture on screen. */
    subtitleAutoScale: Boolean = true,
    /** Width of the side panel that slides in from the right; 0 for layouts that have none. */
    panelWidth: Dp = 0.dp,
    /** 0 = panel closed, 1 = open. Read inside layout/graphics blocks only, so animating it never recomposes. */
    panelProgress: () -> Float = { 0f },
    /** Called with the video view once it exists (the portrait layout reads colours from it for the ambient glow). */
    onPlayerView: (PlayerView) -> Unit = {},
    overlay: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.background(Color.Black)) {
        // The video (and its subtitles) shrink and slide left into the space the panel leaves, like YouTube does
        // when its comments open. It is a scale + translate of one layer, so the video surface is never re-laid out.
        Box(
            modifier = Modifier.fillMaxSize().graphicsLayer {
                val p = panelProgress()
                if (p > 0f) {
                    val width = size.width
                    val height = size.height
                    val videoSize = viewModel.player.videoSize
                    val aspect = if (videoSize.width > 0 && videoSize.height > 0) {
                        videoSize.width * videoSize.pixelWidthHeightRatio / videoSize.height
                    } else {
                        16f / 9f
                    }
                    // Fit leaves bars beside a 16:9 video; Fill and Zoom cover the whole box.
                    val shownWidth = if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) minOf(width, height * aspect) else width
                    val panelPx = panelWidth.toPx()
                    val target = minOf((width - panelPx) / shownWidth, 1f)
                    val scale = 1f + (target - 1f) * p
                    scaleX = scale
                    scaleY = scale
                    // The scaled box stays centred; move its centre to the middle of what the panel leaves free.
                    translationX = -panelPx * p / 2f
                }
            },
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        player = viewModel.player
                        this.resizeMode = resizeMode
                        // Subtitles are drawn by the SubtitleView below, so they can be delayed, resized and raised.
                        subtitleView?.visibility = View.GONE
                        onPlayerView(this)
                    }
                },
                update = { it.resizeMode = resizeMode },
                modifier = Modifier.fillMaxSize(),
            )
            // Most subtitle formats (WebVTT, ASS) give every cue its own line position, and the view then ignores its
            // bottom padding. So the chosen height is written into each text cue: its bottom edge sits at that
            // fraction of the video height above the bottom.
            var subtitleAreaHeightPx by remember { mutableIntStateOf(0) }
            var subtitleAreaWidthPx by remember { mutableIntStateOf(0) }
            // The video's shape, kept up to date as the stream reports it, to work out how big the picture is on screen.
            var videoAspect by remember { mutableFloatStateOf(16f / 9f) }
            DisposableEffect(viewModel.player) {
                val videoPlayer = viewModel.player
                fun update(size: androidx.media3.common.VideoSize) {
                    if (size.width > 0 && size.height > 0) videoAspect = size.width * size.pixelWidthHeightRatio / size.height
                }
                update(videoPlayer.videoSize)
                val listener = object : androidx.media3.common.Player.Listener {
                    override fun onVideoSizeChanged(videoSize: androidx.media3.common.VideoSize) = update(videoSize)
                }
                videoPlayer.addListener(listener)
                onDispose { videoPlayer.removeListener(listener) }
            }
            val density = androidx.compose.ui.platform.LocalDensity.current
            // The text size the person chose is for a phone held sideways. With scaling on, it follows the height of
            // the picture actually shown (small in the portrait player, bigger on a large screen), softened a little
            // so portrait subtitles stay readable. Letterbox bars do not count: only the picture does.
            val shownPictureHeightPx = if (subtitleAreaWidthPx > 0 && subtitleAreaHeightPx > 0) {
                if (resizeMode == AspectRatioFrameLayout.RESIZE_MODE_FIT) {
                    minOf(subtitleAreaHeightPx.toFloat(), subtitleAreaWidthPx / videoAspect)
                } else {
                    subtitleAreaHeightPx.toFloat()
                }
            } else {
                0f
            }
            val subtitleScale = if (subtitleAutoScale && shownPictureHeightPx > 0f) {
                val ratio = (shownPictureHeightPx / density.density) / SUBTITLE_REFERENCE_HEIGHT_DP
                ratio.toDouble().pow(SUBTITLE_SCALE_CURVE).toFloat().coerceIn(SUBTITLE_MIN_SCALE, SUBTITLE_MAX_SCALE)
            } else {
                1f
            }
            val subtitleTextSizePx = with(density) { subtitleSize.sp.toPx() } * subtitleScale
            val subtitleBackground = subtitleBackgroundArgb(subtitleBgColor, subtitleBgOpacity)
            val placedCues = remember(cues, subtitleHeight, subtitleTextSizePx, subtitleAreaHeightPx, subtitleBackground, subtitleRoundedBg) {
                // The background is drawn by the cues themselves (rounded, one bar per line), not by the view's square one.
                roundCueBackgrounds(
                    placeCues(cues, subtitleHeight, subtitleTextSizePx, subtitleAreaHeightPx),
                    subtitleBackground,
                    subtitleTextSizePx,
                    subtitleRoundedBg,
                )
            }
            val subtitleTypeface = com.ansu.anime.ui.theme.AnsuFont.subtitleTypeface
            AndroidView(
                factory = { ctx -> SubtitleView(ctx) },
                update = { view ->
                    // The colours below are the person's choice, so a subtitle file's own colours and sizes must not win.
                    // Embedded styles must stay on for the rounded background spans to survive; the cues were reduced to
                    // plain words (and their own window colour removed) in roundCueBackgrounds, so no file styling gets through.
                    view.setApplyEmbeddedStyles(true)
                    view.setApplyEmbeddedFontSizes(false)
                    view.setStyle(
                        CaptionStyleCompat(
                            subtitleTextColor,
                            // Transparent: the rounded bars are drawn by the cues. The view's own background would be square.
                            0,
                            0,
                            CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                            0xFF000000.toInt(),
                            // The font added in Appearance (null keeps the default subtitle font).
                            subtitleTypeface,
                        ),
                    )
                    view.setFixedTextSize(TypedValue.COMPLEX_UNIT_PX, subtitleTextSizePx)
                    view.setBottomPaddingFraction(0f)
                    view.setCues(placedCues)
                },
                modifier = Modifier
                    .fillMaxSize()
                    .onSizeChanged {
                        subtitleAreaHeightPx = it.height
                        subtitleAreaWidthPx = it.width
                    },
            )
        }

        // Everything else lives in the part of the screen the panel leaves free, so the title, the transport buttons
        // and the seek bar re-centre in the shrunken video area.
        Box(modifier = Modifier.fillMaxHeight().freeWidth(panelWidth, panelProgress)) {
            PlayerGestureLayer(config = gestures, onTap = onTap, onSeekBy = onSeekBy, onHoldSpeed = onHoldSpeed)

            when {
                state.isLoadingSources -> BufferingIndicator(buffering = true, modifier = Modifier.align(Alignment.Center))
                state.error != null -> Text(
                    text = state.error.orEmpty(),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.align(Alignment.Center).padding(24.dp),
                )
                state.showControls -> overlay()
            }

            // The controls carry their own skip button; with them hidden the intro/outro button still has to show.
            if (skipLabel != null && !state.showControls && !state.isLoadingSources && state.error == null) {
                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 28.dp)) {
                    SkipOutroButton(label = skipLabel, progress = skipProgress, onClick = onSkip)
                }
            }

            // While loading or failed there are no controls, so keep a way out.
            if (state.isLoadingSources || state.error != null) {
                IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }

            // With the controls up, the play/pause button itself turns into the spinner instead.
            if (!state.showControls && state.error == null && !state.isLoadingSources) {
                BufferingIndicator(buffering = state.isBuffering, modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

/** Takes the full width minus [panelWidth] * [progress]; the child is laid out at exactly that size every frame. */
private fun Modifier.freeWidth(panelWidth: Dp, progress: () -> Float): Modifier = layout { measurable, constraints ->
    val full = if (constraints.hasBoundedWidth) constraints.maxWidth else constraints.minWidth
    val width = (full - panelWidth.toPx() * progress().coerceIn(0f, 1f)).roundToInt().coerceAtLeast(0)
    val height = if (constraints.hasBoundedHeight) constraints.maxHeight else constraints.minHeight
    val placeable = measurable.measure(Constraints.fixed(width, height))
    layout(width, height) { placeable.placeRelative(0, 0) }
}
