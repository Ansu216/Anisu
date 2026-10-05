@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speaker
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material.icons.rounded.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.formatDuration
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.ui.theme.AnsuColors

/** Everything the player UI can ask for; the screen wires these to the view model. */
class PlayerActions(
    val onPlayPause: () -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onSeekTo: (Long) -> Unit,
    val onBack: () -> Unit,
    val onPrevEpisode: () -> Unit,
    val onNextEpisode: () -> Unit,
    val onLock: () -> Unit,
    val onRotate: () -> Unit,
    val onFit: () -> Unit,
    val onSpeed: () -> Unit,
    val onSubs: () -> Unit,
    val onAudio: () -> Unit,
    val onSources: () -> Unit,
    val onEpisodes: () -> Unit,
    val onSettings: () -> Unit,
    val onSkipOutro: () -> Unit,
    /** Seconds the back/forward buttons jump (Settings > Player and streaming). */
    val seekSeconds: Int = 15,
)

private val SeekTrackColor = Color(0xFF4A4660)
private val ScrimBrush = Brush.verticalGradient(
    colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Black.copy(alpha = 0.7f)),
)

/** "E16 • Unfair Woman", or just "E16" when the episode has no real title. */
internal fun episodeLine(episode: SEpisode?): String {
    episode ?: return ""
    val number = episode.episodeNumber.formatEpisodeNumber()
    val name = episode.name.trim()
    return if (name.isBlank() || name.equals("Episode $number", ignoreCase = true)) "E$number" else "E$number \u2022 $name"
}

// ---------------------------------------------------------------------------------------------
// Fullscreen (landscape) controls: the Netflix-style layout, plus the sketch's changes.
// ---------------------------------------------------------------------------------------------

@Composable
fun PlayerControlsOverlay(
    state: PlayerUiState,
    actions: PlayerActions,
    fitLabel: String,
    speedLabel: String,
    hasPrev: Boolean,
    hasNext: Boolean,
    showSkipOutro: Boolean,
    skipLabel: String = "Skip outro",
    skipProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(ScrimBrush)) {
        // Top: title / episode / source on the left, lock + back on the right.
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 32.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    state.anime?.title.orEmpty(),
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    episodeLine(state.episode),
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.selectedSource?.let {
                    Text(
                        it.label,
                        color = Color.White.copy(alpha = 0.65f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            IconButton(onClick = actions.onLock) {
                Icon(Icons.Rounded.Lock, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(26.dp))
            }
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp))
            }
        }

        TransportRow(
            isPlaying = state.isPlaying,
            actions = actions,
            hasPrev = hasPrev,
            hasNext = hasNext,
            iconScale = 1f,
            modifier = Modifier.align(Alignment.Center),
        )

        // Bottom: skip outro, seek bar, time chips, pill menu.
        Column(
            modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(start = 32.dp, end = 32.dp, bottom = 10.dp),
        ) {
            if (showSkipOutro) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp), horizontalArrangement = Arrangement.End) {
                    SkipOutroButton(label = skipLabel, progress = skipProgress, onClick = actions.onSkipOutro)
                }
            }
            PlayerSeekBar(positionMs = state.positionMs, durationMs = state.durationMs, onSeek = actions.onSeekTo)
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TimeChip(formatDuration(state.positionMs / 1000))
                TimeChip(formatDuration(state.durationMs / 1000))
            }
            Box(modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                ControlPill(
                    fitLabel = fitLabel,
                    speedLabel = speedLabel,
                    actions = actions,
                    modifier = Modifier.align(Alignment.Center),
                )
                IconButton(onClick = actions.onRotate, modifier = Modifier.align(Alignment.CenterEnd)) {
                    Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate screen", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}

/** Shown instead of the controls while the player is locked: only a way to unlock. */
@Composable
fun LockedOverlay(onUnlock: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        IconButton(onClick = onUnlock, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
            Icon(Icons.Rounded.Lock, contentDescription = "Unlock controls", tint = Color.White, modifier = Modifier.size(26.dp))
        }
    }
}

@Composable
private fun TransportRow(
    isPlaying: Boolean,
    actions: PlayerActions,
    hasPrev: Boolean,
    hasNext: Boolean,
    iconScale: Float,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy((36 * iconScale).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        EpisodeSkipButton(Icons.Rounded.SkipPrevious, "Previous episode", hasPrev, iconScale, actions.onPrevEpisode)
        SeekGlyph(forward = false, seconds = actions.seekSeconds, scale = iconScale, onClick = { actions.onSeekBy(-actions.seekSeconds * 1000L) })
        PlayPauseGlyph(isPlaying = isPlaying, scale = iconScale, onClick = actions.onPlayPause)
        SeekGlyph(forward = true, seconds = actions.seekSeconds, scale = iconScale, onClick = { actions.onSeekBy(actions.seekSeconds * 1000L) })
        EpisodeSkipButton(Icons.Rounded.SkipNext, "Next episode", hasNext, iconScale, actions.onNextEpisode)
    }
}

@Composable
private fun EpisodeSkipButton(icon: ImageVector, description: String, enabled: Boolean, scale: Float, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled, modifier = Modifier.size((48 * scale).dp)) {
        Icon(
            icon,
            contentDescription = description,
            tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f),
            modifier = Modifier.size((38 * scale).dp),
        )
    }
}

/** A circular "replay" arrow with the seek step written inside (Material has no 15-second icon). */
@Composable
private fun SeekGlyph(forward: Boolean, seconds: Int, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size((48 * scale).dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Replay,
            contentDescription = if (forward) "Forward $seconds seconds" else "Back $seconds seconds",
            tint = Color.White,
            modifier = Modifier.size((46 * scale).dp).graphicsLayer { scaleX = if (forward) -1f else 1f },
        )
        Text("$seconds", color = Color.White, fontSize = (11 * scale).sp, fontWeight = FontWeight.Bold)
    }
}

/** Two rounded bars for pause (as in the reference), a rounded triangle for play. */
@Composable
private fun PlayPauseGlyph(isPlaying: Boolean, scale: Float, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size((64 * scale).dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isPlaying) {
            Row(horizontalArrangement = Arrangement.spacedBy((8 * scale).dp)) {
                repeat(2) {
                    Box(
                        modifier = Modifier
                            .size((13 * scale).dp, (40 * scale).dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AnsuColors.Accent),
                    )
                }
            }
        } else {
            Icon(Icons.Rounded.PlayArrow, contentDescription = "Play", tint = AnsuColors.Accent, modifier = Modifier.size((64 * scale).dp))
        }
    }
}

@Composable
private fun TimeChip(text: String) {
    Box(
        modifier = Modifier
            .border(1.dp, Color.White.copy(alpha = 0.4f), RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 4.dp),
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
internal fun SkipOutroButton(label: String, progress: Float = 0f, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.55f))
            // The bar fills left to right over the 10 seconds the button stays on screen.
            .drawBehind {
                drawRect(AnsuColors.Accent.copy(alpha = 0.35f), size = Size(size.width * progress.coerceIn(0f, 1f), size.height))
            }
            .border(1.dp, AnsuColors.Accent.copy(alpha = 0.85f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ControlPill(fitLabel: String, speedLabel: String, actions: PlayerActions, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.45f))
            .border(1.dp, Color.White.copy(alpha = 0.25f), shape)
            .padding(horizontal = 22.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PillItem(Icons.Rounded.AspectRatio, fitLabel, actions.onFit)
        PillItem(Icons.Rounded.Speed, speedLabel, actions.onSpeed)
        PillItem(Icons.Rounded.ClosedCaption, "Subs", actions.onSubs)
        PillItem(Icons.Rounded.Speaker, "Audio", actions.onAudio)
        PillItem(Icons.Rounded.Cloud, "Sources", actions.onSources)
        PillItem(Icons.Rounded.VideoLibrary, "Episodes", actions.onEpisodes)
    }
}

@Composable
private fun PillItem(icon: ImageVector, label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

// ---------------------------------------------------------------------------------------------
// Portrait controls: the compact player that sits above the episode info and list.
// ---------------------------------------------------------------------------------------------

@Composable
fun PlayerControlsCompact(
    state: PlayerUiState,
    actions: PlayerActions,
    hasPrev: Boolean,
    hasNext: Boolean,
    showSkipOutro: Boolean,
    skipLabel: String = "Skip outro",
    skipProgress: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxSize().background(ScrimBrush)) {
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = actions.onLock) {
                Icon(Icons.Rounded.Lock, contentDescription = "Lock controls", tint = Color.White, modifier = Modifier.size(22.dp))
            }
            IconButton(onClick = actions.onSettings) {
                Icon(Icons.Rounded.Settings, contentDescription = "Player settings", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }

        TransportRow(
            isPlaying = state.isPlaying,
            actions = actions,
            hasPrev = hasPrev,
            hasNext = hasNext,
            iconScale = 0.7f,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 12.dp)) {
            if (showSkipOutro) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp), horizontalArrangement = Arrangement.End) {
                    SkipOutroButton(label = skipLabel, progress = skipProgress, onClick = actions.onSkipOutro)
                }
            }
            PlayerSeekBar(
                positionMs = state.positionMs,
                durationMs = state.durationMs,
                onSeek = actions.onSeekTo,
                trackHeight = 4.dp,
                thumbWidth = 12.dp,
                thumbHeight = 12.dp,
                showEndDot = false,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(formatDuration(state.positionMs / 1000), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(formatDuration(state.durationMs / 1000), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    IconButton(onClick = actions.onRotate, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Rounded.ScreenRotation, contentDescription = "Rotate screen", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Seek bar: wide rounded track, a white bar for the thumb (reference style); thin + round when compact.
// ---------------------------------------------------------------------------------------------

@Composable
fun PlayerSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 14.dp,
    thumbWidth: Dp = 6.dp,
    thumbHeight: Dp = 26.dp,
    showEndDot: Boolean = true,
) {
    var dragFraction by remember { mutableStateOf<Float?>(null) }
    var widthPx by remember { mutableStateOf(1f) }
    val fraction = dragFraction ?: if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
    val thumbPx = with(LocalDensity.current) { thumbWidth.toPx() }
    val tallest = if (thumbHeight > trackHeight) thumbHeight else trackHeight

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(tallest + 8.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(durationMs) {
                detectTapGestures { tap ->
                    if (durationMs > 0) onSeek(((tap.x / widthPx).coerceIn(0f, 1f) * durationMs).toLong())
                }
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { start -> dragFraction = (start.x / widthPx).coerceIn(0f, 1f) },
                    onHorizontalDrag = { change, _ -> dragFraction = (change.position.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = {
                        dragFraction?.let { if (durationMs > 0) onSeek((it * durationMs).toLong()) }
                        dragFraction = null
                    },
                    onDragCancel = { dragFraction = null },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(RoundedCornerShape(trackHeight / 2))
                .background(SeekTrackColor),
        ) {
            Box(modifier = Modifier.fillMaxWidth(fraction).fillMaxHeight().background(AnsuColors.Accent.copy(alpha = 0.85f)))
            if (showEndDot) {
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(AnsuColors.Accent),
                )
            }
        }
        Box(
            modifier = Modifier
                .offset {
                    val maxX = (widthPx - thumbPx).toInt().coerceAtLeast(0)
                    IntOffset((fraction * widthPx - thumbPx / 2).toInt().coerceIn(0, maxX), 0)
                }
                .size(thumbWidth, thumbHeight)
                .clip(RoundedCornerShape(thumbWidth / 2))
                .background(AnsuColors.Accent),
        )
    }
}

@Composable
fun SourceSelectSheet(
    sources: List<PlayableSource>,
    selected: PlayableSource?,
    onSelect: (PlayableSource) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Select source",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Horizontal scrollable sources
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                items(sources) { source ->
                    SourceChip(
                        label = source.label,
                        isSelected = source == selected,
                        onClick = {
                            onSelect(source)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

@Composable
fun SourceChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) AnsuColors.OnAccent else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
