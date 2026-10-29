package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.cast.CastUiState
import com.ansu.anime.core.util.formatDuration
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Shown in place of the player controls while the video plays on a Chromecast: the phone's own player stays paused,
 * and these controls drive the TV instead.
 */
@Composable
fun CastOverlay(
    state: CastUiState,
    title: String,
    episode: String,
    seekSeconds: Int,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onDisconnect: () -> Unit,
    onSubtitle: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onDevice) {
                Icon(Icons.Rounded.CastConnected, contentDescription = "Cast device", tint = AnsuColors.Accent)
            }
        }

        Column(
            modifier = Modifier.align(Alignment.Center).padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) {
                Icon(Icons.Rounded.Cast, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(36.dp))
            }
            Text(
                "Casting to ${state.deviceName ?: "your TV"}",
                color = Color.White,
                fontSize = if (compact) 14.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (compact) 0.dp else 8.dp),
            )
            if (!compact) {
                Text(title, color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(episode, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Row(
                modifier = Modifier.padding(top = if (compact) 4.dp else 16.dp),
                horizontalArrangement = Arrangement.spacedBy(if (compact) 20.dp else 32.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { onSeekBy(-seekSeconds * 1000L) }) {
                    Icon(Icons.Rounded.FastRewind, contentDescription = "Back $seekSeconds seconds", tint = Color.White)
                }
                IconButton(onClick = onPlayPause, modifier = Modifier.size(if (compact) 44.dp else 56.dp)) {
                    if (state.buffering) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    } else {
                        Icon(
                            if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            contentDescription = if (state.playing) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(if (compact) 32.dp else 44.dp),
                        )
                    }
                }
                IconButton(onClick = { onSeekBy(seekSeconds * 1000L) }) {
                    Icon(Icons.Rounded.FastForward, contentDescription = "Forward $seekSeconds seconds", tint = Color.White)
                }
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp)) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            val duration = state.durationMs.coerceAtLeast(1L)
            val fraction = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(((dragging ?: fraction) * duration).toLong() / 1000L),
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Slider(
                    value = dragging ?: fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AnsuColors.Accent,
                        activeTrackColor = AnsuColors.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(state.durationMs / 1000L), color = Color.White, fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (state.subtitleTracks.isNotEmpty()) {
                    var menuOpen by remember { mutableStateOf(false) }
                    Box {
                        TextButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Rounded.ClosedCaption, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("  Subtitles", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(if (state.activeSubtitleId == null) "Off  \u2713" else "Off") },
                                onClick = {
                                    onSubtitle(null)
                                    menuOpen = false
                                },
                            )
                            state.subtitleTracks.forEach { track ->
                                DropdownMenuItem(
                                    text = { Text(if (track.id == state.activeSubtitleId) "${track.label}  \u2713" else track.label) },
                                    onClick = {
                                        onSubtitle(track.id)
                                        menuOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
                TextButton(onClick = onDisconnect) {
                    Text("Stop casting", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }
        }
    }
}
