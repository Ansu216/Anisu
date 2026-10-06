package com.ansu.anime.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.ui.theme.AnsuColors

/** The three small tabs that slide in from the right of the fullscreen player. */
internal enum class PlayerPanel { SOURCES, SUBS, AUDIO }

/** Share of the screen width the panel takes; the video shrinks into the rest. */
internal const val PANEL_WIDTH_FRACTION = 0.36f

/** Width of the panel in dp for a screen that is [screenWidthDp] wide (never too cramped, never too wide). */
internal fun panelWidthDp(screenWidthDp: Int): Dp = (screenWidthDp * PANEL_WIDTH_FRACTION).coerceIn(280f, 400f).dp

private val PanelBackground = Color(0xFF0C0D12)
private val PanelCard = Color(0xFF171923)
private val PanelCardBorder = Color.White.copy(alpha = 0.08f)
private val PanelMuted = Color.White.copy(alpha = 0.6f)
private val PanelCardShape = RoundedCornerShape(14.dp)

/**
 * The panel frame: slides in from the right as [progress] goes 0 -> 1 (the screen drives it with one animation that
 * also shrinks the video and drops the pill), with a title and a close button, and [content] below.
 */
@Composable
internal fun PlayerSidePanelFrame(
    title: String,
    progress: () -> Float,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    headerExtra: @Composable () -> Unit = {},
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxHeight()
            .graphicsLayer {
                val p = progress()
                translationX = (1f - p) * size.width
                alpha = if (p <= 0f) 0f else 1f
            }
            .background(PanelBackground)
            .border(BorderStroke(1.dp, PanelCardBorder))
            // Taps on the panel must not fall through to the video's tap layer behind it.
            .clickable(indication = null, interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }) {},
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 18.dp, end = 6.dp, top = 8.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            headerExtra()
            IconButton(onClick = onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        }
        content()
    }
}

// ---------------------------------------------------------------------------------------------
// Subtitles
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SubtitlesPanel(
    state: PlayerUiState,
    subtitleSize: Int,
    subtitleHeight: Int,
    progress: () -> Float,
    onToggle: (Boolean) -> Unit,
    onSelectTrack: (TrackOption) -> Unit,
    onOffsetChange: (Long) -> Unit,
    onSizeChange: (Int) -> Unit,
    onHeightChange: (Int) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanelFrame(title = "Subtitle settings", progress = progress, onClose = onClose, modifier = modifier) {
        var languagesOpen by remember { mutableStateOf(false) }
        val active = state.textOptions.firstOrNull { it.selected }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Box 1: the toggle, where the subtitles come from, and the language.
            item {
                PanelCard(modifier = Modifier.animateContentSize(tween(220))) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Subtitles", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                        Switch(
                            checked = state.textEnabled,
                            onCheckedChange = onToggle,
                            enabled = state.textOptions.isNotEmpty(),
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = AnsuColors.OnAccent,
                                checkedTrackColor = AnsuColors.Accent,
                                uncheckedThumbColor = Color.White.copy(alpha = 0.7f),
                                uncheckedTrackColor = Color.White.copy(alpha = 0.15f),
                                uncheckedBorderColor = Color.Transparent,
                            ),
                        )
                    }
                    PanelDivider()
                    PanelValueRow(label = "Source", value = state.selectedSource?.extensionName ?: state.selectedSource?.label ?: "None")
                    PanelDivider()
                    PanelValueRow(
                        label = "Language",
                        value = when {
                            state.textOptions.isEmpty() -> "No subtitles"
                            !state.textEnabled -> "Off"
                            else -> active?.label ?: "Select"
                        },
                        expandable = state.textOptions.isNotEmpty(),
                        expanded = languagesOpen,
                        onClick = { if (state.textOptions.isNotEmpty()) languagesOpen = !languagesOpen },
                    )
                    AnimatedVisibility(visible = languagesOpen && state.textOptions.isNotEmpty()) {
                        Column(modifier = Modifier.padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            state.textOptions.forEach { option ->
                                PanelChoiceRow(
                                    text = option.label,
                                    selected = option.selected && state.textEnabled,
                                    onClick = {
                                        onSelectTrack(option)
                                        languagesOpen = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
            // Boxes 2-4: one box per adjustment, as in the sketch.
            item {
                PanelStepperCard(
                    title = "Subtitle offset",
                    valueText = if (state.subtitleOffsetMs == 0L) "0 ms" else "+${state.subtitleOffsetMs} ms",
                    canDecrease = state.subtitleOffsetMs > 0L,
                    canIncrease = state.subtitleOffsetMs < 10_000L,
                    onDecrease = { onOffsetChange(state.subtitleOffsetMs - OFFSET_STEP_MS) },
                    onIncrease = { onOffsetChange(state.subtitleOffsetMs + OFFSET_STEP_MS) },
                )
            }
            item {
                PanelStepperCard(
                    title = "Subtitle size",
                    valueText = "$subtitleSize sp",
                    canDecrease = subtitleSize > com.ansu.anime.data.prefs.PlayerPrefs.SUBTITLE_SIZE_RANGE.first,
                    canIncrease = subtitleSize < com.ansu.anime.data.prefs.PlayerPrefs.SUBTITLE_SIZE_RANGE.last,
                    onDecrease = { onSizeChange(subtitleSize - 1) },
                    onIncrease = { onSizeChange(subtitleSize + 1) },
                )
            }
            item {
                PanelStepperCard(
                    title = "Subtitle height",
                    valueText = "$subtitleHeight %",
                    canDecrease = subtitleHeight > com.ansu.anime.data.prefs.PlayerPrefs.SUBTITLE_HEIGHT_RANGE.first,
                    canIncrease = subtitleHeight < com.ansu.anime.data.prefs.PlayerPrefs.SUBTITLE_HEIGHT_RANGE.last,
                    onDecrease = { onHeightChange(subtitleHeight - 2) },
                    onIncrease = { onHeightChange(subtitleHeight + 2) },
                )
            }
        }
    }
}

private const val OFFSET_STEP_MS = 100L

// ---------------------------------------------------------------------------------------------
// Sources
// ---------------------------------------------------------------------------------------------

@Composable
internal fun SourcesPanel(
    state: PlayerUiState,
    progress: () -> Float,
    onSelect: (PlayableSource) -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // Chips come from the sources that actually answered: "All" plus one per extension or addon.
    val groups = remember(state.sources) { state.sources.mapNotNull { it.extensionName }.distinct() }
    var filter by remember { mutableStateOf<String?>(null) }
    val activeFilter = filter?.takeIf { it in groups }
    val shown = remember(state.sources, activeFilter) {
        if (activeFilter == null) state.sources else state.sources.filter { it.extensionName == activeFilter }
    }

    PlayerSidePanelFrame(
        title = "Choose sources",
        progress = progress,
        onClose = onClose,
        modifier = modifier,
        headerExtra = {
            IconButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Retry", tint = Color.White, modifier = Modifier.size(22.dp))
            }
        },
    ) {
        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PanelChip(text = "All", selected = activeFilter == null, onClick = { filter = null }) }
            items(groups) { name -> PanelChip(text = name, selected = activeFilter == name, onClick = { filter = name }) }
        }
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (state.isLoadingSources) "Looking for sources\u2026" else "No sources found. Tap retry.",
                        color = PanelMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(shown) { source ->
                SourceCard(
                    source = source,
                    selected = source == state.selectedSource,
                    ping = state.pings[source.url],
                    measuring = state.isMeasuringPings && state.pings[source.url] == null && source.url.isNotBlank(),
                    onClick = { onSelect(source) },
                )
            }
        }
    }
}

@Composable
private fun SourceCard(source: PlayableSource, selected: Boolean, ping: Long?, measuring: Boolean, onClick: () -> Unit) {
    val quality = remember(source.label) { source.qualityChip() }
    val kind = remember(source.label) { source.kindChip() }
    val title = source.extensionName ?: source.label
    val border = if (selected) AnsuColors.Accent else PanelCardBorder
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PanelCardShape)
            .background(PanelCard)
            .border(1.dp, border, PanelCardShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = "Playing", tint = AnsuColors.Accent, modifier = Modifier.size(18.dp))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (quality != null) PanelTag(quality)
                if (kind != null) PanelTag(kind)
                // A stream whose label says nothing recognisable still shows what the source called it.
                if (quality == null && kind == null && source.extensionName != null) PanelTag(source.label)
            }
            LatencyText(ping = ping, measuring = measuring, unresolved = source.url.isBlank())
        }
    }
}

@Composable
private fun LatencyText(ping: Long?, measuring: Boolean, unresolved: Boolean) {
    val (text, color) = when {
        unresolved -> "\u2014" to PanelMuted
        ping == null && measuring -> "\u2026" to PanelMuted
        ping == null -> "\u2014" to PanelMuted
        ping < 0 -> "offline" to AnsuColors.Error
        ping < 300 -> "$ping ms" to AnsuColors.ScoreGreen
        ping < 800 -> "$ping ms" to Color(0xFFF1C40F)
        else -> "$ping ms" to AnsuColors.Error
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(color))
        Text(text, color = Color.White.copy(alpha = 0.85f), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
    }
}

private val QualityRegex = Regex("""(\d{3,4})\s*p""", RegexOption.IGNORE_CASE)

/** "1080p" read out of the stream label, or null when the label has no resolution. */
internal fun PlayableSource.qualityChip(): String? =
    QualityRegex.find(label)?.let { "${it.groupValues[1]}p" }
        ?: Regex("""\b(4K|2K)\b""", RegexOption.IGNORE_CASE).find(label)?.value?.uppercase()

/** "Sub", "Dub", "Hardsub" or "Softsub" when the label says so, else null. */
internal fun PlayableSource.kindChip(): String? {
    val text = label.lowercase()
    return when {
        "hardsub" in text || "hard sub" in text -> "Hardsub"
        "softsub" in text || "soft sub" in text -> "Softsub"
        "dub" in text -> "Dub"
        "sub" in text -> "Sub"
        else -> null
    }
}

// ---------------------------------------------------------------------------------------------
// Audio
// ---------------------------------------------------------------------------------------------

@Composable
internal fun AudioPanel(
    options: List<TrackOption>,
    progress: () -> Float,
    onSelect: (TrackOption) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    PlayerSidePanelFrame(title = "Audio", progress = progress, onClose = onClose, modifier = modifier) {
        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (options.isEmpty()) {
                item {
                    Text(
                        "No audio tracks to choose from for this source.",
                        color = PanelMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 12.dp),
                    )
                }
            }
            items(options) { option ->
                val border = if (option.selected) AnsuColors.Accent else PanelCardBorder
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PanelCardShape)
                        .background(PanelCard)
                        .border(1.dp, border, PanelCardShape)
                        .clickable { onSelect(option) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(option.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (option.detail.isNotBlank()) {
                            Text(option.detail, color = PanelMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    if (option.selected) {
                        Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = AnsuColors.Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Building blocks
// ---------------------------------------------------------------------------------------------

@Composable
private fun PanelCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(PanelCardShape)
            .background(PanelCard)
            .border(1.dp, PanelCardBorder, PanelCardShape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        content()
    }
}

@Composable
private fun PanelDivider() {
    Spacer(modifier = Modifier.fillMaxWidth().height(1.dp).background(PanelCardBorder))
}

@Composable
private fun PanelValueRow(
    label: String,
    value: String,
    expandable: Boolean = false,
    expanded: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = PanelMuted, fontSize = 13.sp, modifier = Modifier.width(78.dp))
        Text(
            value,
            color = Color.White,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
        if (expandable) {
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.padding(start = 4.dp).size(20.dp).graphicsLayer { rotationZ = if (expanded) 180f else 0f },
            )
        }
    }
}

@Composable
private fun PanelChoiceRow(text: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) AnsuColors.Accent.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.05f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color.White, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", tint = AnsuColors.Accent, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PanelStepperCard(
    title: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
) {
    PanelCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            StepperButton(Icons.Rounded.Remove, "Decrease", canDecrease, onDecrease)
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .width(68.dp)
                    .clip(RoundedCornerShape(50))
                    .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
                    .padding(vertical = 5.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(valueText, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            StepperButton(Icons.Rounded.Add, "Increase", canIncrease, onIncrease)
        }
    }
}

@Composable
private fun StepperButton(icon: androidx.compose.ui.graphics.vector.ImageVector, description: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, Color.White.copy(alpha = if (enabled) 0.3f else 0.12f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White.copy(alpha = if (enabled) 1f else 0.3f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun PanelChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AnsuColors.Accent else Color.Transparent)
            .border(1.dp, if (selected) AnsuColors.Accent else Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text,
            color = if (selected) AnsuColors.OnAccent else Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun PanelTag(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .border(1.dp, Color.White.copy(alpha = 0.3f), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(text, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
