@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.playersettings

import androidx.compose.foundation.layout.Arrangement
import kotlinx.coroutines.launch
import kotlinx.coroutines.CoroutineStart
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.key
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.material3.SliderDefaults
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.data.prefs.PlayerPrefs
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.player.ColorSwatchRow
import com.ansu.anime.ui.player.SubtitleBgColors
import com.ansu.anime.ui.player.SubtitlePreview
import com.ansu.anime.ui.player.SubtitleTextColors
import com.ansu.anime.ui.theme.AnsuColors
import com.ansu.anime.extension.BUILT_IN_SOURCE_ID

@Composable
fun PlayerStreamingScreen(container: AppContainer, navController: NavHostController) {
    val prefs = container.playerPrefs
    val doubleTap by prefs.doubleTapSeek.collectAsStateWithLifecycle()
    val brightness by prefs.brightnessGesture.collectAsStateWithLifecycle()
    val volume by prefs.volumeGesture.collectAsStateWithLifecycle()
    val skipSeconds by prefs.skipSeconds.collectAsStateWithLifecycle()
    val subSize by prefs.subtitleSize.collectAsStateWithLifecycle()
    val subHeight by prefs.subtitleHeight.collectAsStateWithLifecycle()
    val subColor by prefs.subtitleColor.collectAsStateWithLifecycle()
    val subBgColor by prefs.subtitleBgColor.collectAsStateWithLifecycle()
    val subBgOpacity by prefs.subtitleBgOpacity.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Player and streaming") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ===== SKIPPING BOX =====
            SettingsBox(title = "Skipping") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Skip amount", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PlayerPrefs.SKIP_OPTIONS.forEach { seconds ->
                            FilterChip(
                                selected = skipSeconds == seconds,
                                onClick = { prefs.setSkipSeconds(seconds) },
                                label = { Text("${seconds}s") },
                            )
                        }
                    }
                    Hint("How far a double tap and the back/forward buttons on the player controls jump.")
                    ToggleRow(
                        title = "Double tap to skip",
                        subtitle = "Double tap the left side to go back, the right side to go forward",
                        checked = doubleTap,
                        onChange = prefs::setDoubleTapSeek,
                    )
                }
            }

            // ===== GESTURES BOX =====
            SettingsBox(title = "Gestures") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    ToggleRow(
                        title = "Brightness gesture",
                        subtitle = "Swipe up or down on the left half of the video",
                        checked = brightness,
                        onChange = prefs::setBrightnessGesture,
                    )
                    ToggleRow(
                        title = "Volume gesture",
                        subtitle = "Swipe up or down on the right half of the video",
                        checked = volume,
                        onChange = prefs::setVolumeGesture,
                    )
                }
            }

            // ===== SUBTITLES BOX: look =====
            SettingsBox(title = "Subtitles") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Live Preview", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextSecondary)
                    SubtitlePreview(size = subSize, height = subHeight, textColor = subColor, bgColor = subBgColor, bgOpacity = subBgOpacity)
                    Text("Text colour", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                    ColorSwatchRow(SubtitleTextColors, subColor, prefs::setSubtitleColor)
                    Text("Background colour", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                    ColorSwatchRow(SubtitleBgColors, subBgColor, prefs::setSubtitleBgColor)
                    SliderRow("Background density", "$subBgOpacity %", subBgOpacity.toFloat(), 0f..100f) { prefs.setSubtitleBgOpacity(it.roundToInt()) }
                }
            }

            // ===== SUBTITLES BOX: size and position =====
            SettingsBox(title = "Subtitle Size and Position") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SliderRow("Size", "$subSize sp", subSize.toFloat(), PlayerPrefs.SUBTITLE_SIZE_RANGE.first.toFloat()..PlayerPrefs.SUBTITLE_SIZE_RANGE.last.toFloat()) {
                        prefs.setSubtitleSize(it.roundToInt())
                    }
                    SliderRow("Height", "$subHeight %", subHeight.toFloat(), PlayerPrefs.SUBTITLE_HEIGHT_RANGE.first.toFloat()..PlayerPrefs.SUBTITLE_HEIGHT_RANGE.last.toFloat()) {
                        prefs.setSubtitleHeight(it.roundToInt())
                    }
                    Hint("Height is how far the subtitles sit above the bottom edge of the video. The player's Subtitles tab has the same options.")
                    TextButton(onClick = prefs::resetSubtitleStyle, modifier = Modifier.align(Alignment.End)) { Text("Reset subtitle style") }
                }
            }

            // ===== STREAMING PRIORITY BOX =====
            SettingsBox(title = "Streaming Priority") {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Hint(
                        "Drag the handle to rank your sources. The player lists streams in this order and starts with " +
                            "the highest-ranked source that has the episode.",
                    )
                    SourcePriorityList(container)
                }
            }
        }
    }
}

/** Rounded, bordered container that groups related settings, same look as the Appearance screen's boxes. */
@Composable
private fun SettingsBox(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AnsuColors.BackgroundElevated)
            .border(1.dp, AnsuColors.StrokeGlass, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
        content()
    }
}

@Composable
private fun SliderRow(title: String, valueText: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.labelMedium, color = AnsuColors.TextSecondary)
        }
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = AnsuColors.Accent,
                activeTrackColor = AnsuColors.Accent,
                inactiveTrackColor = AnsuColors.AccentSoft,
            ),
        )
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall, color = AnsuColors.TextTertiary)
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

private data class RankedSource(val id: Long, val label: String, val enabled: Boolean)

private val RowHeight = 56.dp

/** Reorderable list of installed sources; the order is saved the moment a drag ends. */
@Composable
private fun SourcePriorityList(container: AppContainer) {
    val prefs = container.playerPrefs
    val extensions by container.extensionManager.extensions.collectAsStateWithLifecycle()
    val disabled by container.extensionManager.disabledSourceIds.collectAsStateWithLifecycle()
    val saved by prefs.sourcePriority.collectAsStateWithLifecycle()

    val installed = remember(extensions, disabled) {
        container.extensionManager.allSourcesIncludingDisabled()
            .filter { it.id != BUILT_IN_SOURCE_ID }
            .map { RankedSource(it.id, "${it.name} (${it.lang})", it.id !in disabled) }
    }
    if (installed.isEmpty()) {
        Hint("No sources installed yet. Install an extension and it will show up here.")
        return
    }

    // Ranked sources first in the saved order, then any the person has not ranked yet.
    val order = remember { mutableStateListOf<RankedSource>() }
    LaunchedEffect(installed, saved) {
        val byId = installed.associateBy { it.id }
        val next = saved.mapNotNull { byId[it] } + installed.filter { it.id !in saved }
        if (next != order.toList()) {
            order.clear()
            order.addAll(next)
        }
    }

    val density = LocalDensity.current
    val rowPx = with(density) { RowHeight.toPx() }
    var draggingId by remember { mutableStateOf<Long?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    Column(modifier = Modifier.fillMaxWidth()) {
        order.forEachIndexed { index, source ->
            // Keyed by id so a row keeps its composition (and its running drag gesture) when it changes
            // position; without the key the gesture restarted on every swap, which made dragging stutter.
            key(source.id) {
                val dragging = draggingId == source.id
                val rowScope = rememberCoroutineScope()
                // Slides a row from its old slot to its new one whenever the order changes under it.
                val slide = remember { Animatable(0f) }
                var lastIndex by remember { mutableIntStateOf(index) }
                LaunchedEffect(index) {
                    if (lastIndex != index) {
                        if (!dragging) {
                            slide.snapTo((lastIndex - index) * rowPx)
                            slide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 600f))
                        }
                        lastIndex = index
                    }
                }
                val release: (Boolean) -> Unit = { save ->
                    val residual = dragOffset
                    // Hand the leftover finger offset to the slide so the row settles into its slot instead of jumping.
                    rowScope.launch(start = CoroutineStart.UNDISPATCHED) {
                        slide.snapTo(residual)
                        draggingId = null
                        dragOffset = 0f
                        slide.animateTo(0f, spring(dampingRatio = 0.85f, stiffness = 600f))
                    }
                    if (save) prefs.setSourcePriority(order.map { it.id })
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(RowHeight)
                        .zIndex(if (dragging) 1f else 0f)
                        .graphicsLayer {
                            // Read only while drawing, so following the finger never recomposes the list.
                            translationY = if (dragging) dragOffset else slide.value
                            val lift = if (dragging) 1.02f else 1f
                            scaleX = lift
                            scaleY = lift
                            shadowElevation = if (dragging) 8.dp.toPx() else 0f
                            shape = RoundedCornerShape(12.dp)
                            clip = false
                        }
                        .background(if (dragging) AnsuColors.AccentSoft else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "${index + 1}",
                        style = MaterialTheme.typography.titleSmall,
                        color = AnsuColors.TextTertiary,
                        modifier = Modifier.width(28.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(source.label, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary, maxLines = 1)
                        if (!source.enabled) {
                            Text("Switched off in Extensions", style = MaterialTheme.typography.labelSmall, color = AnsuColors.TextTertiary)
                        }
                    }
                    Icon(
                        Icons.Filled.DragHandle,
                        contentDescription = "Drag to reorder ${source.label}",
                        tint = AnsuColors.TextSecondary,
                        modifier = Modifier
                            .size(40.dp)
                            .padding(8.dp)
                            .pointerInput(source.id) {
                                detectDragGestures(
                                    onDragStart = { draggingId = source.id; dragOffset = 0f },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount.y
                                        // Swap with the neighbour once the row has moved over half its height.
                                        val current = order.indexOfFirst { it.id == source.id }
                                        val target = (current + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
                                        if (target != current) {
                                            val item = order.removeAt(current)
                                            order.add(target, item)
                                            dragOffset -= (target - current) * rowPx
                                        }
                                    },
                                    onDragEnd = { release(true) },
                                    onDragCancel = { release(false) },
                                )
                            },
                    )
                }
            }
        }
        if (saved.isNotEmpty()) {
            TextButton(onClick = { prefs.setSourcePriority(emptyList()) }) { Text("Reset order") }
        }
    }
}
