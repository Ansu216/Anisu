package com.ansu.anime.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.clipRect
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the video-surface gestures may do, straight from Settings > Player and streaming. */
data class PlayerGestureConfig(
    val doubleTapSeek: Boolean = true,
    val brightness: Boolean = true,
    val volume: Boolean = true,
    val skipSeconds: Int = 15,
    /** False while the controls are locked: only a plain tap works then. */
    val enabled: Boolean = true,
    /** Horizontal swipe scrubs the video (Settings > Swipe To Seek). */
    val swipeSeek: Boolean = false,
)

private fun Context.activityOrNull(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * The transparent layer over the video: tap toggles the controls, double tap on the left/right side skips,
 * and a vertical swipe on the left half changes brightness, on the right half volume.
 */
@Composable
fun PlayerGestureLayer(
    config: PlayerGestureConfig,
    onTap: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = remember(context) { context.activityOrNull()?.window }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableStateOf(0) }
    // The YouTube-style skip feedback: which side, the seconds added so far in this run of taps.
    var skipSide by remember { mutableStateOf(0) }
    var skipTotal by remember { mutableStateOf(0) }
    var skipTick by remember { mutableStateOf(0) }

    fun showHud(text: String) {
        hud = text
        hudTick++
    }
    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }
    LaunchedEffect(skipTick) {
        if (skipSide != 0) {
            delay(SKIP_SERIES_MS)
            skipSide = 0
            skipTotal = 0
        }
    }
    // Brightness is only overridden while the player is open.
    DisposableEffect(window) {
        onDispose {
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
        }
    }

    var brightnessLevel = 0f
    var volumeLevel = 0f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(config.enabled, config.doubleTapSeek, config.skipSeconds) {
                    // Our own tap detector: a double tap only counts when BOTH taps land on the same side.
                    // (detectTapGestures pairs any two quick taps, so left then right used to skip.)
                  coroutineScope {
                    val scope = this
                    val doubleTapMs = viewConfiguration.doubleTapTimeoutMillis
                    val seekOn = config.enabled && config.doubleTapSeek
                    var pending: Job? = null
                    var lastZone = 0
                    var lastAt = 0L
                    var seriesZone = 0
                    var seriesAt = 0L
                    fun zoneOf(x: Float): Int {
                        val third = size.width / 3f
                        return when {
                            x < third -> -1
                            x > third * 2 -> 1
                            else -> 0
                        }
                    }
                    fun skip(zone: Int, now: Long) {
                        seriesZone = zone
                        seriesAt = now
                        onSeekBy(zone * config.skipSeconds * 1000L)
                        if (skipSide != zone) skipTotal = 0
                        skipSide = zone
                        skipTotal += config.skipSeconds
                        skipTick++
                    }
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        // Null when the finger dragged (brightness/volume) or the gesture was taken over.
                        val up = waitForUpOrCancellation() ?: return@awaitEachGesture
                        if (up.uptimeMillis - down.uptimeMillis > viewConfiguration.longPressTimeoutMillis) {
                            return@awaitEachGesture
                        }
                        val now = up.uptimeMillis
                        val zone = zoneOf(up.position.x)
                        when {
                            // Already skipping: every further tap on the same side keeps skipping, like YouTube.
                            seekOn && zone != 0 && zone == seriesZone && now - seriesAt < SKIP_SERIES_MS -> {
                                pending?.cancel(); pending = null
                                skip(zone, now)
                            }
                            // Second tap, same side, quick enough: a real double tap.
                            seekOn && zone != 0 && pending != null && zone == lastZone && now - lastAt <= doubleTapMs -> {
                                pending?.cancel(); pending = null
                                skip(zone, now)
                            }
                            // Anything else starts over as a first tap (other side, centre, too slow).
                            else -> {
                                pending?.cancel(); pending = null
                                seriesZone = 0
                                lastZone = zone
                                lastAt = now
                                if (seekOn && zone != 0) {
                                    pending = scope.launch {
                                        delay(doubleTapMs)
                                        pending = null
                                        onTap()
                                    }
                                } else {
                                    onTap()
                                }
                            }
                        }
                    }
                  }
                }
                .pointerInput(config.enabled, config.swipeSeek) {
                    if (!config.enabled || !config.swipeSeek) return@pointerInput
                    var travelled = 0f
                    fun seekMs(): Long {
                        // A swipe across the whole width moves about two minutes, in whole seconds.
                        val seconds = (travelled / size.width.coerceAtLeast(1) * SWIPE_SEEK_FULL_WIDTH_S).roundToInt()
                        return seconds * 1000L
                    }
                    fun label(): String {
                        val s = seekMs() / 1000
                        return if (s >= 0) "+${s}s" else "${s}s"
                    }
                    detectHorizontalDragGestures(
                        onDragStart = { travelled = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            travelled += dragAmount
                            showHud(label())
                        },
                        onDragEnd = {
                            val ms = seekMs()
                            if (ms != 0L) onSeekBy(ms)
                            travelled = 0f
                        },
                        onDragCancel = { travelled = 0f },
                    )
                }
                .pointerInput(config.enabled, config.brightness, config.volume) {
                    if (!config.enabled || (!config.brightness && !config.volume)) return@pointerInput
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            leftSide = start.x < size.width / 2f
                            if (leftSide) {
                                val current = window?.attributes?.screenBrightness ?: -1f
                                brightnessLevel = if (current >= 0f) current else {
                                    runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
                                        .getOrDefault(0.5f)
                                }
                            } else {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                volumeLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // A full-height swipe moves the level by roughly its whole range.
                            val delta = -dragAmount / size.height.coerceAtLeast(1)
                            if (leftSide && config.brightness) {
                                change.consume()
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                window?.let {
                                    val lp = it.attributes
                                    lp.screenBrightness = brightnessLevel
                                    it.attributes = lp
                                }
                                showHud("Brightness ${(brightnessLevel * 100).roundToInt()}%")
                            } else if (!leftSide && config.volume) {
                                change.consume()
                                volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * max).roundToInt(), 0)
                                showHud("Volume ${(volumeLevel * 100).roundToInt()}%")
                            }
                        },
                    )
                },
        )
        if (skipSide != 0) {
            SkipFeedback(side = skipSide, seconds = skipTotal, tick = skipTick)
        }
        hud?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

private const val SKIP_SERIES_MS = 700L
private const val SWIPE_SEEK_FULL_WIDTH_S = 120f

/** The YouTube-style double-tap feedback: a soft half-disc on the tapped side, running chevrons and "N seconds". */
@Composable
private fun BoxScope.SkipFeedback(side: Int, seconds: Int, tick: Int) {
    val forward = side > 0
    val pulse = remember { Animatable(0f) }
    LaunchedEffect(tick) {
        pulse.snapTo(0f)
        pulse.animateTo(1f, tween(350))
    }
    val fade = remember { Animatable(0f) }
    LaunchedEffect(Unit) { fade.animateTo(1f, tween(120)) }

    Box(
        modifier = Modifier
            .align(if (forward) Alignment.CenterEnd else Alignment.CenterStart)
            .fillMaxHeight()
            .fillMaxWidth(0.42f)
            .alpha(fade.value),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // A big circle centred on the screen edge; only the half inside this box is visible, so the curved
            // edge faces the middle of the video.
            val cx = if (forward) size.width else 0f
            val radius = size.width * (0.88f + 0.12f * pulse.value)
            clipRect {
                drawCircle(
                    color = Color.White.copy(alpha = 0.18f - 0.06f * pulse.value),
                    radius = radius,
                    center = Offset(cx, size.height / 2f),
                )
            }
        }
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val transition = rememberInfiniteTransition(label = "skipChevrons")
            Row {
                repeat(3) { i ->
                    // Chevrons light up one after another, towards the direction of the skip.
                    val order = if (forward) i else 2 - i
                    // The animated alpha is read in the layer, not while composing, so a chevron lighting up
                    // costs a repaint of that icon instead of a recomposition of the skip overlay on every frame.
                    val a = transition.animateFloat(
                        initialValue = 0.25f,
                        targetValue = 0.25f,
                        animationSpec = infiniteRepeatable(
                            animation = keyframes {
                                durationMillis = 750
                                0.25f at order * 150 using LinearEasing
                                1f at order * 150 + 150 using LinearEasing
                                0.25f at order * 150 + 300 using LinearEasing
                            },
                            repeatMode = RepeatMode.Restart,
                        ),
                        label = "chevron$i",
                    )
                    Icon(
                        imageVector = Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .size(26.dp)
                            .rotate(if (forward) 0f else 180f)
                            .graphicsLayer { alpha = a.value },
                    )
                }
            }
            Text(
                text = "$seconds seconds",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
