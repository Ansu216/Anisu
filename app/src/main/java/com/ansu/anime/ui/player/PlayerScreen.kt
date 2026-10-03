package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.ui.PlayerView
import androidx.navigation.NavHostController
import com.ansu.anime.di.AppContainer
import kotlinx.coroutines.delay

@Composable
fun PlayerScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
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
    var showSourceSheet by remember { mutableStateOf(false) }
    var showSubtitleSheet by remember { mutableStateOf(false) }
    var showStreamSheet by remember { mutableStateOf(false) }

    // Auto-hide controls after a few seconds of inactivity, like Nuvio's player.
    LaunchedEffect(state.showControls, state.isPlaying) {
        if (state.showControls && state.isPlaying) {
            delay(3500)
            viewModel.toggleControls()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    useController = false
                    player = viewModel.player
                }
            },
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {
                    viewModel.toggleControls()
                },
        )

        when {
            state.isLoadingSources -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            state.error != null -> Text(
                text = state.error.orEmpty(),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.align(Alignment.Center),
            )
            state.showControls -> PlayerControlsOverlay(
                state = state,
                onPlayPause = viewModel::togglePlayPause,
                onSeekBy = viewModel::seekBy,
                onSeekTo = viewModel::seekTo,
                onBack = { navController.popBackStack() },
                onSelectSourceClick = { showSourceSheet = true },
            )
        }

        if (state.isBuffering && state.error == null && !state.isLoadingSources) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }

        if (showSourceSheet) {
            SourceSelectSheet(
                sources = state.sources,
                selected = state.selectedSource,
                onSelect = {
                    viewModel.selectSource(it)
                    showSourceSheet = false
                },
                onDismiss = { showSourceSheet = false },
            )
        }

        if (showSubtitleSheet) {
            SubtitleSettingsSheet(
                languages = listOf(
                    SubtitleLanguage("en", "English"),
                    SubtitleLanguage("es", "Spanish"),
                    SubtitleLanguage("de", "German"),
                    SubtitleLanguage("fr", "French"),
                    SubtitleLanguage("ja", "Japanese"),
                ),
                selectedLanguage = null,
                onLanguageSelect = { /* Handle selection */ },
                onSizeChange = { /* Handle size change */ },
                onDismiss = { showSubtitleSheet = false },
            )
        }

        if (showStreamSheet) {
            StreamSelectionSheet(
                streams = listOf(
                    StreamInfo("1080p", "Source 1", 84),
                    StreamInfo("720p", "Source 1", 84),
                    StreamInfo("1080p", "Source 2", 122),
                    StreamInfo("720p", "Source 3", 310),
                ),
                selectedStream = null,
                onStreamSelect = { /* Handle selection */ },
                onDismiss = { showStreamSheet = false },
            )
        }
    }
}
