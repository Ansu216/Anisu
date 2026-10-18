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
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
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
import com.ansu.anime.ui.components.GlassContentRow
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassSliderRow
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.theme.AnsuColors
import com.ansu.anime.extension.BUILT_IN_SOURCE_ID

@Composable
fun PlayerStreamingScreen(container: AppContainer, navController: NavHostController) {
    val prefs = container.playerPrefs
    val doubleTap by prefs.doubleTapSeek.collectAsStateWithLifecycle()
    val brightness by prefs.brightnessGesture.collectAsStateWithLifecycle()
    val volume by prefs.volumeGesture.collectAsStateWithLifecycle()
    val skipSeconds by prefs.skipSeconds.collectAsStateWithLifecycle()
    val pip by prefs.pipEnabled.collectAsStateWithLifecycle()
    val ambient by prefs.ambientMode.collectAsStateWithLifecycle()
    val pauseScreen by prefs.pauseScreen.collectAsStateWithLifecycle()
    val preferredQuality by prefs.preferredQuality.collectAsStateWithLifecycle()
    val preferredLanguage by prefs.preferredLanguage.collectAsStateWithLifecycle()
    val prefetch by prefs.prefetchEnabled.collectAsStateWithLifecycle()
    val subSize by prefs.subtitleSize.collectAsStateWithLifecycle()
    val subHeight by prefs.subtitleHeight.collectAsStateWithLifecycle()
    val subColor by prefs.subtitleColor.collectAsStateWithLifecycle()
    val subBgColor by prefs.subtitleBgColor.collectAsStateWithLifecycle()
    val subBgOpacity by prefs.subtitleBgOpacity.collectAsStateWithLifecycle()
    val subRoundedBg by prefs.subtitleRoundedBg.collectAsStateWithLifecycle()

    // The page scrolls; the source list asks it to scroll further while a row is dragged to its edge.
    val scrollState = rememberScrollState()
    val autoScroll = remember { DragAutoScroll() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Playback") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .onGloballyPositioned { autoScroll.viewport = it }
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            // Every block below is the same shape: the heading outside, a box, and a line between its rows.
            GlassGroupLabel("Gestures")
            GlassGroup {
                GlassContentRow(
                    title = "Skip Duration",
                    description = "Jump length for double tap and skip buttons",
                    showDivider = false,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PlayerPrefs.SKIP_OPTIONS.forEach { seconds ->
                            FilterChip(
                                selected = skipSeconds == seconds,
                                onClick = { prefs.setSkipSeconds(seconds) },
                                label = { Text("${seconds}s") },
                            )
                        }
                    }
                }
                GlassToggleRow(
                    title = "Double Tap To Seek",
                    description = "Left side rewinds, right side forwards",
                    checked = doubleTap,
                    onCheckedChange = prefs::setDoubleTapSeek,
                )
                GlassToggleRow(
                    title = "Swipe Controls",
                    description = "Swipe left half for brightness, right half for volume",
                    checked = brightness && volume,
                    onCheckedChange = { on ->
                        prefs.setBrightnessGesture(on)
                        prefs.setVolumeGesture(on)
                    },
                )
            }

            GlassGroupLabel("Preferred stream")
            GlassGroup {
                GlassContentRow(
                    title = "Preferred Quality",
                    description = "Playback starts on the closest stream to this resolution. Any starts the first stream that answers, in your language, so it begins sooner",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        PlayerPrefs.QUALITY_OPTIONS.forEach { height ->
                            FilterChip(
                                selected = preferredQuality == height,
                                onClick = { prefs.setPreferredQuality(height) },
                                label = { Text(if (height == PlayerPrefs.QUALITY_ANY) "Any" else "${height}p") },
                            )
                        }
                    }
                }
                GlassContentRow(
                    title = "Preferred Language",
                    description = "Sub or dub. Falls back to the other when a show has no match",
                    showDivider = false,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf(PlayerPrefs.LANGUAGE_SUB to "Sub", PlayerPrefs.LANGUAGE_DUB to "Dub").forEach { (value, label) ->
                            FilterChip(
                                selected = preferredLanguage == value,
                                onClick = { prefs.setPreferredLanguage(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                }
            }

            GlassGroupLabel("Pause screen")
            GlassGroup {
                GlassToggleRow(
                    title = "Pause Screen",
                    description = "Dim the video and show what you're watching when paused",
                    checked = pauseScreen,
                    onCheckedChange = prefs::setPauseScreen,
                    showDivider = false,
                )
            }

            GlassGroupLabel("Portrait player")
            GlassGroup {
                GlassToggleRow(
                    title = "Ambient Mode",
                    description = "The area under the video glows in the colours of what is playing",
                    checked = ambient,
                    onCheckedChange = prefs::setAmbientMode,
                    showDivider = false,
                )
            }

            GlassGroupLabel("Picture in picture")
            GlassGroup {
                GlassToggleRow(
                    title = "Picture In Picture",
                    description = "Keep playing in a floating window when you leave the app",
                    checked = pip,
                    onCheckedChange = prefs::setPipEnabled,
                    showDivider = false,
                )
            }

            GlassGroupLabel("Preload")
            GlassGroup {
                GlassToggleRow(
                    title = "Preload Last Episode",
                    description = "Loads a minute ahead for an instant start. Uses data",
                    checked = prefetch,
                    onCheckedChange = prefs::setPrefetchEnabled,
                    showDivider = false,
                )
            }

            GlassGroupLabel("Subtitles")
            GlassGroup {
                GlassContentRow(
                    title = "Live Preview",
                    description = "A sample that follows your changes",
                    showDivider = false,
                ) {
                    SubtitlePreview(size = subSize, height = subHeight, textColor = subColor, bgColor = subBgColor, bgOpacity = subBgOpacity, roundedBg = subRoundedBg)
                }
                GlassContentRow(title = "Text Colour", description = "Colour of the subtitle text") {
                    ColorSwatchRow(SubtitleTextColors, subColor, prefs::setSubtitleColor)
                }
                GlassContentRow(title = "Background Colour", description = "Box behind the text") {
                    ColorSwatchRow(SubtitleBgColors, subBgColor, prefs::setSubtitleBgColor)
                }
                GlassSliderRow(
                    title = "Background Opacity",
                    description = "0% is clear, 100% is solid",
                    valueText = "$subBgOpacity %",
                    value = subBgOpacity.toFloat(),
                    valueRange = 0f..100f,
                    onValueChange = { prefs.setSubtitleBgOpacity(it.roundToInt()) },
                )
                GlassToggleRow(
                    title = "Rounded Background",
                    description = "Soft bars behind each line. Off gives a plain box",
                    checked = subRoundedBg,
                    onCheckedChange = prefs::setSubtitleRoundedBg,
                )
                GlassSliderRow(
                    title = "Text Size",
                    description = "Subtitle text size",
                    valueText = "$subSize sp",
                    value = subSize.toFloat(),
                    valueRange = PlayerPrefs.SUBTITLE_SIZE_RANGE.first.toFloat()..PlayerPrefs.SUBTITLE_SIZE_RANGE.last.toFloat(),
                    onValueChange = { prefs.setSubtitleSize(it.roundToInt()) },
                )
                GlassSliderRow(
                    title = "Vertical Position",
                    description = "Distance from the bottom of the video",
                    valueText = "$subHeight %",
                    value = subHeight.toFloat(),
                    valueRange = PlayerPrefs.SUBTITLE_HEIGHT_RANGE.first.toFloat()..PlayerPrefs.SUBTITLE_HEIGHT_RANGE.last.toFloat(),
                    onValueChange = { prefs.setSubtitleHeight(it.roundToInt()) },
                )
                GlassRow(
                    title = "Reset Subtitle Style",
                    description = "Back to the default look",
                    onClick = prefs::resetSubtitleStyle,
                )
            }

            GlassGroupLabel("Source Priority")
            GlassGroup {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Hint(
                        "Drag to rank sources. The highest-ranked one with the episode plays first.",
                    )
                    SourcePriorityList(container, scrollState, autoScroll)
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
}

private data class RankedSource(val id: Long, val label: String, val enabled: Boolean)

/**
 * Where the scrolling page and the source list are on screen. Plain (non-state) fields: layout coordinates
 * change every frame while the page scrolls and must not recompose anything; they are read live by the
 * drag's auto-scroll loop.
 */
private class DragAutoScroll {
    var viewport: LayoutCoordinates? = null
    var list: LayoutCoordinates? = null
}

/** A dragged row within this distance of the page's top or bottom edge scrolls the page. */
private val AutoScrollEdge = 88.dp

/** Page scroll speed at the very edge, in dp per second; it ramps up from zero over [AutoScrollEdge]. */
private const val AutoScrollMaxDpPerSecond = 720f

private val RowHeight = 56.dp

/** Reorderable list of installed sources; the order is saved the moment a drag ends. */
@Composable
private fun SourcePriorityList(container: AppContainer, scrollState: ScrollState, autoScroll: DragAutoScroll) {
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

    // Swaps the dragged row with its neighbour once it has moved over half a row, keeping the leftover offset.
    fun reorder(id: Long) {
        val current = order.indexOfFirst { it.id == id }
        if (current < 0) return
        val target = (current + (dragOffset / rowPx).roundToInt()).coerceIn(0, order.lastIndex)
        if (target != current) {
            val item = order.removeAt(current)
            order.add(target, item)
            dragOffset -= (target - current) * rowPx
        }
    }

    // Auto-scroll: while a row is held near the top or bottom edge of the page, the page scrolls under it, faster
    // the closer to the edge. The scrolled distance is added to the drag offset so the row stays under the finger,
    // and the order is re-checked every frame because a held finger sends no move events of its own.
    val edgePx = with(density) { AutoScrollEdge.toPx() }
    val maxSpeedPx = with(density) { AutoScrollMaxDpPerSecond.dp.toPx() }
    LaunchedEffect(draggingId) {
        val id = draggingId ?: return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            val now = withFrameNanos { it }
            val seconds = (now - last) / 1_000_000_000f
            last = now
            val viewport = autoScroll.viewport?.takeIf { it.isAttached }?.boundsInRoot() ?: continue
            val listTop = autoScroll.list?.takeIf { it.isAttached }?.positionInRoot()?.y ?: continue
            val index = order.indexOfFirst { it.id == id }
            if (index < 0) continue
            val centre = listTop + index * rowPx + dragOffset + rowPx / 2f
            val topLine = viewport.top + edgePx
            val bottomLine = viewport.bottom - edgePx
            // -1..0 near the top edge, 0..1 near the bottom edge, 0 in between.
            val push = when {
                centre < topLine -> -((topLine - centre) / edgePx).coerceIn(0f, 1f)
                centre > bottomLine -> ((centre - bottomLine) / edgePx).coerceIn(0f, 1f)
                else -> 0f
            }
            if (push != 0f) {
                val moved = scrollState.scrollBy(push * maxSpeedPx * seconds)
                if (moved != 0f) {
                    dragOffset += moved
                    reorder(id)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxWidth().onGloballyPositioned { autoScroll.list = it }) {
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
                                        reorder(source.id)
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
