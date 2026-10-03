package com.ansu.anime.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.ansu.anime.di.AppContainer
import kotlinx.coroutines.delay

private enum class PlayerSheet { SOURCES, SUBS, AUDIO, EPISODES, SETTINGS }

private val FitModes = listOf(
    "Fit" to AspectRatioFrameLayout.RESIZE_MODE_FIT,
    "Fill" to AspectRatioFrameLayout.RESIZE_MODE_FILL,
    "Zoom" to AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
)
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
                    container.addonManager,
                    container.continueWatchingRepository,
                    container.selectionHolder,
                    container.diagnostics,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var sheet by remember { mutableStateOf<PlayerSheet?>(null) }
    var locked by remember { mutableStateOf(false) }
    var fitIndex by remember { mutableIntStateOf(0) }

    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE

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

    // Auto-hide controls after a few seconds of inactivity (not while a sheet is open).
    LaunchedEffect(state.showControls, state.isPlaying, sheet) {
        if (state.showControls && state.isPlaying && sheet == null) {
            delay(3500)
            viewModel.toggleControls()
        }
    }

    val hasPrev = remember(state.episode, state.episodes) { viewModel.previousEpisode() != null }
    val hasNext = remember(state.episode, state.episodes) { viewModel.nextEpisode() != null }
    // No chapter data from the sources, so "Skip outro" appears for the last two minutes of an episode.
    val remainingMs = state.durationMs - state.positionMs
    val showSkipOutro = hasNext && state.durationMs > 0 && remainingMs in 3_000L..120_000L

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
        onSubs = { sheet = PlayerSheet.SUBS },
        onAudio = { sheet = PlayerSheet.AUDIO },
        onSources = { sheet = PlayerSheet.SOURCES },
        onEpisodes = { sheet = PlayerSheet.EPISODES },
        onSettings = { sheet = PlayerSheet.SETTINGS },
        onSkipOutro = viewModel::skipOutro,
    )

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (isLandscape) {
            PlayerSurface(
                modifier = Modifier.fillMaxSize(),
                viewModel = viewModel,
                state = state,
                resizeMode = FitModes[fitIndex].second,
                onTap = viewModel::toggleControls,
                onBack = actions.onBack,
            ) {
                if (locked) {
                    LockedOverlay(onUnlock = { locked = false })
                } else {
                    PlayerControlsOverlay(
                        state = state,
                        actions = actions,
                        fitLabel = fitLabel,
                        speedLabel = speedLabel,
                        hasPrev = hasPrev,
                        hasNext = hasNext,
                        showSkipOutro = showSkipOutro,
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding()) {
                PlayerSurface(
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f),
                    viewModel = viewModel,
                    state = state,
                    resizeMode = FitModes[fitIndex].second,
                    onTap = viewModel::toggleControls,
                    onBack = actions.onBack,
                ) {
                    if (locked) {
                        LockedOverlay(onUnlock = { locked = false })
                    } else {
                        PlayerControlsCompact(
                            state = state,
                            actions = actions,
                            hasPrev = hasPrev,
                            hasNext = hasNext,
                            showSkipOutro = showSkipOutro,
                        )
                    }
                }
                EpisodeInfoPane(
                    state = state,
                    onPlayEpisode = viewModel::playEpisode,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        when (sheet) {
            PlayerSheet.SOURCES -> SourceSelectSheet(
                sources = state.sources,
                selected = state.selectedSource,
                onSelect = viewModel::selectSource,
                onDismiss = { sheet = null },
            )
            PlayerSheet.SUBS -> TrackSheet(
                title = "Subtitles",
                options = state.textOptions,
                offSelected = !state.textEnabled,
                onSelect = viewModel::selectTextTrack,
                onOff = { viewModel.selectTextTrack(null) },
                onDismiss = { sheet = null },
            )
            PlayerSheet.AUDIO -> TrackSheet(
                title = "Audio",
                options = state.audioOptions,
                offSelected = null,
                onSelect = viewModel::selectAudioTrack,
                onOff = {},
                onDismiss = { sheet = null },
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
    overlay: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier.background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = viewModel.player
                    this.resizeMode = resizeMode
                }
            },
            update = { it.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { MutableInteractionSource() }) { onTap() },
        )

        when {
            state.isLoadingSources -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Text(
                text = state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center).padding(24.dp),
            )
            state.showControls -> overlay()
        }

        // While loading or failed there are no controls, so keep a way out.
        if (state.isLoadingSources || state.error != null) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.TopStart).padding(8.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
        }

        if (state.isBuffering && state.error == null && !state.isLoadingSources) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}
