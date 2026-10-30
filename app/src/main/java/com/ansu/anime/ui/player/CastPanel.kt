package com.ansu.anime.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.cast.CastDeviceOption
import com.ansu.anime.cast.CastUiState
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.delay

/** How long the panel says "Searching..." with nothing found before it says "No device found". */
private const val SEARCH_WINDOW_MS = 8_000L

/**
 * The "Casting" side panel (a bottom sheet in portrait), same frame as Sources / Audio / Subtitles. It has three
 * looks, from the sketch: searching (a Cast icon with a magnifier), no device found (a crossed-out Cast icon), and
 * the list of TVs found, one card each with a circle that fills in on the one the phone is connected to.
 */
@Composable
internal fun CastPanel(
    state: CastUiState,
    progress: () -> Float,
    onSelect: (CastDeviceOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    sheet: Boolean = false,
) {
    // The first seconds count as searching, so the panel never claims "No device found" before the network has answered.
    var searchedLongEnough by remember { mutableStateOf(false) }
    LaunchedEffect(state.devices.isEmpty(), state.ready) {
        searchedLongEnough = false
        if (state.devices.isEmpty() && state.ready) {
            delay(SEARCH_WINDOW_MS)
            searchedLongEnough = true
        }
    }

    PlayerSidePanelFrame(title = "Casting", progress = progress, onClose = onClose, modifier = modifier, sheet = sheet) {
        when {
            state.devices.isNotEmpty() -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.devices, key = { it.id }) { device -> CastDeviceCard(device, onClick = { onSelect(device) }) }
            }
            !state.ready -> CastStatus(
                searching = false,
                text = "Chromecast isn't available on this device",
                modifier = Modifier.fillMaxWidth().weight(1f),
            )
            searchedLongEnough -> CastStatus(searching = false, text = "No device found", modifier = Modifier.fillMaxWidth().weight(1f))
            else -> CastStatus(searching = true, text = "Searching\u2026", modifier = Modifier.fillMaxWidth().weight(1f))
        }
    }
}

@Composable
private fun CastDeviceCard(device: CastDeviceOption, onClick: () -> Unit) {
    val border = if (device.selected) AnsuColors.Accent else PanelCardBorder
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelCardShape)
            .background(PanelCard)
            .border(1.dp, border, PanelCardShape)
            .clickable(enabled = !device.selected && !device.connecting, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(device.name, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val detail = when {
                device.connecting -> "Connecting\u2026"
                device.selected -> "Connected"
                else -> device.detail
            }
            if (detail != null) {
                Text(detail, color = PanelMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        when {
            device.connecting -> CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
            device.selected -> Box(
                modifier = Modifier.size(22.dp).clip(CircleShape).background(AnsuColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Check, contentDescription = "Connected", tint = AnsuColors.OnAccent, modifier = Modifier.size(15.dp))
            }
            else -> Box(modifier = Modifier.size(22.dp).clip(CircleShape).border(2.dp, Color.White.copy(alpha = 0.35f), CircleShape))
        }
    }
}

/** The searching / nothing-found look: a rounded Cast icon (with a magnifier, or crossed out) and a line of text. */
@Composable
private fun CastStatus(searching: Boolean, text: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val pulse = rememberInfiniteTransition(label = "castSearch").animateFloat(
            initialValue = 0.35f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
            label = "castSearchPulse",
        )
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PanelCard)
                    .border(1.dp, PanelCardBorder, RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Rounded.Cast,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp).alpha(if (searching) pulse.value else 0.75f),
                )
                if (!searching) {
                    // Crossed out: a slash with a card-coloured edge so it cuts the icon cleanly.
                    Canvas(modifier = Modifier.size(60.dp)) {
                        val from = Offset(size.width * 0.28f, size.height * 0.26f)
                        val to = Offset(size.width * 0.72f, size.height * 0.74f)
                        drawLine(PanelCard, from, to, strokeWidth = 7.dp.toPx(), cap = StrokeCap.Round)
                        drawLine(Color.White.copy(alpha = 0.85f), from, to, strokeWidth = 2.5.dp.toPx(), cap = StrokeCap.Round)
                    }
                }
            }
            if (searching) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(AnsuColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Search, contentDescription = null, tint = AnsuColors.OnAccent, modifier = Modifier.size(15.dp))
                }
            }
        }
        Text(
            text,
            color = PanelMuted,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}
