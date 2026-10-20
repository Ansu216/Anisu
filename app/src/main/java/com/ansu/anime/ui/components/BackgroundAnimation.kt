package com.ansu.anime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ansu.anime.data.prefs.BackgroundStyle
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.random.Random

/**
 * Slow shapes that float behind the screens (Appearance > Background animation). They are drawn once, at the
 * very back of the app: screens, posters and cards stay fully opaque and simply sit in front of them, so the
 * shapes only show in the empty space between content. The loop stops whenever the app is not on screen.
 */
@Composable
fun BackgroundAnimation(
    style: BackgroundStyle,
    modifier: Modifier = Modifier,
    /** Shrinks every shape; the picker's small preview tiles use a fraction so the whole look fits in them. */
    sizeScale: Float = 1f,
    /** Plays the motion faster, so a small tile shows movement at a glance. */
    speed: Float = 1f,
    /** Draws only the first few shapes (a tile has little room). */
    maxShapes: Int = Int.MAX_VALUE,
) {
    if (style == BackgroundStyle.NONE) return

    // Frame time in nanoseconds. It is only read inside the draw block, so a new frame redraws the canvas
    // without recomposing anything.
    val frameNanos = remember { mutableLongStateOf(0L) }
    val owner = LocalContext.current as? LifecycleOwner
    LaunchedEffect(style, owner) {
        if (owner == null) {
            while (true) withFrameNanos { frameNanos.longValue = it }
        } else {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (true) withFrameNanos { frameNanos.longValue = it }
            }
        }
    }

    val items = remember(style, maxShapes) { makeItems(style).take(maxShapes) }
    val paths = remember(style) { unitPaths(style) }
    val accent = AnsuColors.Accent

    Canvas(modifier = modifier.fillMaxSize()) {
        val t = frameNanos.longValue / 1_000_000_000f * speed
        val density = this.density
        for (item in items) {
            val x = wrap(item.x + item.vx * t) * size.width
            val y = wrap(item.y + item.vy * t) * size.height
            val radius = item.sizeDp * density / 2f
            val angle = item.rot + item.spin * t
            val pulse = 1f + 0.05f * sin(t * 0.35f + item.phase)
            val r = radius * pulse * sizeScale
            translate(x, y) {
                when (style) {
                    BackgroundStyle.CIRCLES -> drawCircle(accent.copy(alpha = item.alpha), radius = r)
                    BackgroundStyle.TRIANGLES -> rotate(angle, Offset.Zero) {
                        drawUnit(paths[0], r, accent, item.alpha, strokeAlpha = item.alpha * 2.6f, density = density)
                    }
                    BackgroundStyle.SHAPES -> rotate(angle, Offset.Zero) {
                        if (item.kind == PILL) {
                            val w = r * 2f
                            val h = r * 1.05f
                            drawRoundRect(
                                accent.copy(alpha = item.alpha),
                                topLeft = Offset(-w / 2f, -h / 2f),
                                size = Size(w, h),
                                cornerRadius = CornerRadius(h / 2f),
                            )
                        } else {
                            drawUnit(paths[item.kind % paths.size], r, accent, item.alpha, strokeAlpha = 0f, density = density)
                        }
                    }
                    BackgroundStyle.NONE -> Unit
                }
            }
        }
    }
}

private fun DrawScope.drawUnit(path: Path, radius: Float, color: Color, fillAlpha: Float, strokeAlpha: Float, density: Float) {
    scale(radius, radius, Offset.Zero) {
        drawPath(path, color.copy(alpha = fillAlpha))
        if (strokeAlpha > 0f) {
            drawPath(path, color.copy(alpha = strokeAlpha.coerceAtMost(0.4f)), style = Stroke(width = density / radius))
        }
    }
}

/** Keeps a position in [-0.2, 1.2) so a shape leaves one edge fully before it comes back in at the other. */
private fun wrap(v: Float): Float {
    val span = 1.4f
    return v - floor(v / span) * span - 0.2f
}

private class Item(
    val x: Float, val y: Float, val vx: Float, val vy: Float,
    val sizeDp: Float, val rot: Float, val spin: Float, val phase: Float,
    val alpha: Float, val kind: Int,
)

private const val PILL = 99

private fun makeItems(style: BackgroundStyle): List<Item> {
    val rnd = Random(
        when (style) {
            BackgroundStyle.CIRCLES -> 11
            BackgroundStyle.TRIANGLES -> 23
            else -> 37
        },
    )
    fun between(a: Float, b: Float) = a + rnd.nextFloat() * (b - a)
    fun sign() = if (rnd.nextBoolean()) 1f else -1f
    return when (style) {
        BackgroundStyle.CIRCLES -> List(9) {
            Item(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                vx = between(0.004f, 0.012f) * sign(), vy = between(0.004f, 0.012f) * sign(),
                sizeDp = between(150f, 380f), rot = 0f, spin = 0f, phase = between(0f, 6.28f),
                alpha = between(0.045f, 0.085f), kind = 0,
            )
        }
        BackgroundStyle.TRIANGLES -> List(16) {
            Item(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                vx = between(0.002f, 0.01f) * sign(), vy = between(-0.03f, -0.008f),
                sizeDp = between(60f, 170f), rot = between(0f, 360f), spin = between(4f, 14f) * sign(),
                phase = between(0f, 6.28f), alpha = between(0.03f, 0.055f), kind = 0,
            )
        }
        else -> List(14) { i ->
            Item(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                vx = between(0.003f, 0.011f) * sign(), vy = between(0.003f, 0.011f) * sign(),
                sizeDp = between(80f, 190f), rot = between(0f, 360f), spin = between(3f, 11f) * sign(),
                phase = between(0f, 6.28f), alpha = between(0.05f, 0.09f),
                kind = if (i % 7 == 6) PILL else i % 6,
            )
        }
    }
}

/** Outlines at unit radius: one triangle for [BackgroundStyle.TRIANGLES], six rounded Material-style shapes for SHAPES. */
private fun unitPaths(style: BackgroundStyle): List<Path> = when (style) {
    BackgroundStyle.TRIANGLES -> listOf(
        Path().apply {
            for (i in 0 until 3) {
                val a = (-PI / 2 + i * 2 * PI / 3).toFloat()
                if (i == 0) moveTo(cos(a), sin(a)) else lineTo(cos(a), sin(a))
            }
            close()
        },
    )
    BackgroundStyle.SHAPES -> listOf(
        polar { 1f + 0.09f * cos(9f * it) },            // cookie: a circle with soft scallops
        polar { 0.72f + 0.28f * cos(6f * it) },         // flower: six round petals
        polar { 0.62f + 0.38f * cos(4f * it) },         // clover: four leaves
        polar { 0.8f + 0.2f * cos(8f * it) },           // sunny: a rounded eight-point burst
        polar { 0.86f + 0.14f * cos(12f * it) },        // burst: twelve gentle points
        polar { 0.78f + 0.22f * abs(cos(2.5f * it)) },  // pebble: lumpy and organic
    )
    else -> emptyList()
}

/** A closed curve r(theta), scaled so its farthest point sits at radius 1. */
private fun polar(r: (Float) -> Float): Path {
    val steps = 180
    val radii = FloatArray(steps) { r((it * 2 * PI / steps).toFloat()) }
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
