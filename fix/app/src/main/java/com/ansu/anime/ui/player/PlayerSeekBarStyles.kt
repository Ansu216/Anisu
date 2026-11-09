package com.ansu.anime.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The looks the player's progress bar can have (Settings > Playback > Controls > Seek Bar Style).
 * [key] is what is stored in the preferences.
 */
enum class SeekBarStyle(val key: String, val label: String) {
    /** The original wide rounded bar with a vertical handle. */
    Classic("classic", "Classic"),

    /** A round track with a Material "cookie" shape as the handle, and a small shape sitting inside it. */
    Cookie("cookie", "Cookie"),

    /** A thick played part ending in a dot, a small gap, then a thin line for what is left. */
    Dot("dot", "Dot"),

    /** A thick played part inside an outlined track that stays hollow for what is left. */
    Outline("outline", "Outline");

    companion object {
        fun fromKey(key: String?): SeekBarStyle = entries.firstOrNull { it.key == key } ?: Classic
    }
}

private val TrackColor = Color(0xFF4A4660)

/** How tall the touch area of a non-classic bar is. */
internal fun styledSeekHeight(compact: Boolean): Dp = if (compact) 24.dp else 44.dp

/** A closed shape of [lobes] soft bumps around a circle, scaled so its farthest point sits at radius 1. */
private fun lobedPath(lobes: Int, amplitude: Float): Path {
    val steps = 180
    val radii = FloatArray(steps) { 1f + amplitude * cos(lobes * (it * 2 * PI / steps).toFloat()) }
    val maxR = radii.max()
    return Path().apply {
        for (i in 0 until steps) {
            val a = (i * 2 * PI / steps).toFloat()
            val x = cos(a) * radii[i] / maxR
            val y = sin(a) * radii[i] / maxR
            if (i == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
}

/**
 * Draws one of the non-classic bars. [fraction] is how far playback is (0..1) and [dragging] is true while a
 * finger is on the bar, which makes the handle grow a little and, for [SeekBarStyle.Cookie], drops the small
 * inner shape back into the handle.
 */
@Composable
internal fun StyledSeekVisual(
    style: SeekBarStyle,
    fraction: Float,
    dragging: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    val accent = AnsuColors.Accent
    val cookie = remember { lobedPath(lobes = 9, amplitude = 0.09f) }
    val flower = remember { lobedPath(lobes = 5, amplitude = 0.24f) }
    val pop by animateFloatAsState(
        targetValue = if (dragging) 1f else 0f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "seekPop",
    )
    // The inner shape starts above the handle each time a finger lands and drops into it.
    val drop = remember { Animatable(0f) }
    LaunchedEffect(dragging) {
        if (dragging) {
            drop.snapTo(-1.5f)
            drop.animateTo(0f, spring(dampingRatio = 0.45f, stiffness = 320f))
        }
    }
    val f = fraction.coerceIn(0f, 1f)

    Canvas(modifier = modifier) {
        val w = size.width
        val cy = size.height / 2f
        when (style) {
            SeekBarStyle.Cookie -> {
                val trackH = (if (compact) 8.dp else 18.dp).toPx()
                val r = (if (compact) 10.dp else 19.dp).toPx() * (1f + 0.14f * pop)
                val cx = r + f * (w - 2 * r)
                drawRoundRect(TrackColor, Offset(0f, cy - trackH / 2), Size(w, trackH), CornerRadius(trackH / 2))
                drawRoundRect(accent, Offset(0f, cy - trackH / 2), Size(cx.coerceAtLeast(trackH), trackH), CornerRadius(trackH / 2))
                // The handle turns as it travels, like a wheel.
                withTransform({
                    translate(cx, cy)
                    rotate(f * 1080f, Offset.Zero)
                    scale(r, r, Offset.Zero)
                }) {
                    drawPath(cookie, Color.White)
                }
                withTransform({
                    translate(cx, cy + drop.value * r * 1.6f)
                    rotate(-f * 720f, Offset.Zero)
                    scale(r * 0.46f, r * 0.46f, Offset.Zero)
                }) {
                    drawPath(flower, accent)
                }
            }

            SeekBarStyle.Dot -> {
                val thick = (if (compact) 6.dp else 12.dp).toPx() * (1f + 0.2f * pop)
                val thin = (if (compact) 1.5.dp else 3.dp).toPx()
                val dotR = thick * 0.62f
                val gap = (if (compact) 3.dp else 5.dp).toPx()
                val cx = (dotR + f * (w - 2 * dotR))
                val playedEnd = cx - dotR - gap
                if (playedEnd > 0f) {
                    drawRoundRect(accent, Offset(0f, cy - thick / 2), Size(playedEnd, thick), CornerRadius(thick / 2))
                }
                drawCircle(accent, dotR, Offset(cx, cy))
                val restStart = cx + dotR + gap
                if (restStart < w) {
                    drawLine(TrackColor, Offset(restStart, cy), Offset(w, cy), strokeWidth = thin, cap = StrokeCap.Round)
                }
            }

            SeekBarStyle.Outline -> {
                val h = (if (compact) 9.dp else 20.dp).toPx() * (1f + 0.2f * pop)
                val stroke = (if (compact) 1.dp else 1.5.dp).toPx()
                val inset = (if (compact) 2.dp else 3.dp).toPx()
                val top = cy - h / 2f
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.6f),
                    topLeft = Offset(stroke / 2, top + stroke / 2),
                    size = Size(w - stroke, h - stroke),
                    cornerRadius = CornerRadius(h / 2),
                    style = Stroke(width = stroke),
                )
                // The played part is the inner pill, cut off where playback is.
                val inner = Path().apply {
                    addRoundRect(
                        androidx.compose.ui.geometry.RoundRect(
                            left = inset, top = top + inset, right = w - inset, bottom = top + h - inset,
                            cornerRadius = CornerRadius((h - 2 * inset) / 2),
                        ),
                    )
                }
                val played = Path().apply { addRect(androidx.compose.ui.geometry.Rect(0f, 0f, f * w, size.height)) }
                val clipped = Path.combine(PathOperation.Intersect, inner, played)
                drawPath(clipped, accent)
            }

            SeekBarStyle.Classic -> Unit
        }
    }
}
