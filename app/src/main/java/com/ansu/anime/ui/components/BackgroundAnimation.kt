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
import androidx.compose.ui.graphics.StrokeCap
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
        // These shapes drift a few pixels a second, so 30 fps looks exactly the same as 60. Publishing the clock
        // only every other frame halves the full-screen redraws the animation costs, which is what it does all
        // the time the app is on screen.
        suspend fun tick() {
            var last = 0L
            while (true) withFrameNanos { frame ->
                if (frame - last >= FRAME_INTERVAL_NANOS) {
                    last = frame
                    frameNanos.longValue = frame
                }
            }
        }
        if (owner == null) {
            tick()
        } else {
            owner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
                tick()
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
            // Falling things (petals, snow, ghosts) also sway side to side as they go.
            val x = wrap(item.x + item.vx * t + item.sway * sin(t * 0.6f + item.phase)) * size.width
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
                    BackgroundStyle.SAKURA -> drawPetal(paths[0], r, item, t)
                    BackgroundStyle.SNOW -> drawSnow(r, item, angle, density)
                    BackgroundStyle.HALLOWEEN -> drawHalloween(paths, r, item, angle, t)
                    BackgroundStyle.BREAD -> drawBread(paths[0], r, item, t)
                    BackgroundStyle.NONE -> Unit
                }
            }
        }
    }
}

/** One third of a second in nanoseconds: 30 frames a second, which is enough for shapes this slow. */
private const val FRAME_INTERVAL_NANOS = 33_000_000L

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
    /** Side-to-side sway as a fraction of the screen width (0 = none). */
    val sway: Float = 0f,
)

private const val PILL = 99

private fun makeItems(style: BackgroundStyle): List<Item> {
    val rnd = Random(
        when (style) {
            BackgroundStyle.CIRCLES -> 11
            BackgroundStyle.TRIANGLES -> 23
            BackgroundStyle.SAKURA -> 51
            BackgroundStyle.SNOW -> 67
            BackgroundStyle.HALLOWEEN -> 83
            BackgroundStyle.BREAD -> 97
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
        BackgroundStyle.SAKURA -> List(22) { i ->
            Item(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                vx = between(0.004f, 0.012f), vy = between(0.018f, 0.04f),
                sizeDp = between(20f, 42f), rot = between(0f, 360f), spin = between(15f, 45f) * sign(),
                phase = between(0f, 6.28f), alpha = between(0.45f, 0.75f), kind = i % 3,
                sway = 0.025f,
            )
        }
        BackgroundStyle.SNOW -> List(34) { i ->
            val flake = i % 4 == 0
            Item(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                vx = between(-0.004f, 0.004f), vy = between(0.02f, 0.05f),
                sizeDp = if (flake) between(16f, 34f) else between(4f, 9f),
                rot = between(0f, 360f), spin = if (flake) between(5f, 18f) * sign() else 0f,
                phase = between(0f, 6.28f), alpha = if (flake) between(0.3f, 0.5f) else between(0.35f, 0.7f),
                kind = if (flake) 1 else 0, sway = 0.02f,
            )
        }
        BackgroundStyle.HALLOWEEN -> List(12) { i ->
            when (i % 3) {
                // Pumpkins hover and tilt a little.
                0 -> Item(
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    vx = between(-0.004f, 0.004f), vy = between(-0.012f, -0.004f),
                    sizeDp = between(70f, 110f), rot = between(-14f, 14f), spin = 0f,
                    phase = between(0f, 6.28f), alpha = 1f, kind = 0, sway = 0.01f,
                )
                // Bats cross the screen, flapping.
                1 -> Item(
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    vx = between(0.012f, 0.03f) * sign(), vy = between(-0.004f, 0.004f),
                    sizeDp = between(50f, 90f), rot = 0f, spin = 0f,
                    phase = between(0f, 6.28f), alpha = 1f, kind = 1, sway = 0.012f,
                )
                // Ghosts drift upward and wobble.
                else -> Item(
                    x = rnd.nextFloat(), y = rnd.nextFloat(),
                    vx = between(-0.004f, 0.004f), vy = between(-0.015f, -0.006f),
                    sizeDp = between(60f, 100f), rot = 0f, spin = 0f,
                    phase = between(0f, 6.28f), alpha = 1f, kind = 2, sway = 0.03f,
                )
            }
        }
        // Slices of toast and round buns with sleepy little faces, drifting up and wobbling; two of the fourteen are sandwiches.
        BackgroundStyle.BREAD -> List(14) { i ->
            Item(
                x = rnd.nextFloat(), y = rnd.nextFloat(),
                vx = between(-0.004f, 0.004f), vy = between(-0.014f, -0.005f),
                sizeDp = between(64f, 104f), rot = between(-12f, 12f), spin = 0f,
                phase = between(0f, 6.28f), alpha = 1f, kind = if (i == 6 || i == 13) 2 else i % 2, sway = 0.02f,
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
    BackgroundStyle.SAKURA -> listOf(
        // A petal with the little notch at its tip.
        Path().apply {
            moveTo(0f, 0.9f)
            cubicTo(-0.55f, 0.55f, -0.95f, -0.2f, -0.55f, -0.8f)
            quadraticTo(-0.25f, -1.0f, 0f, -0.7f)
            quadraticTo(0.25f, -1.0f, 0.55f, -0.8f)
            cubicTo(0.95f, -0.2f, 0.55f, 0.55f, 0f, 0.9f)
            close()
        },
    )
    BackgroundStyle.HALLOWEEN -> listOf(
        // 0: bat
        Path().apply {
            moveTo(0f, -0.3f)
            lineTo(0.12f, -0.6f); lineTo(0.2f, -0.28f)
            cubicTo(0.5f, -0.55f, 0.85f, -0.5f, 1.0f, -0.2f)
            quadraticTo(0.85f, -0.05f, 0.7f, 0.1f)
            quadraticTo(0.62f, -0.05f, 0.5f, 0.15f)
            quadraticTo(0.42f, 0.0f, 0.25f, 0.3f)
            quadraticTo(0.1f, 0.5f, 0f, 0.55f)
            quadraticTo(-0.1f, 0.5f, -0.25f, 0.3f)
            quadraticTo(-0.42f, 0.0f, -0.5f, 0.15f)
            quadraticTo(-0.62f, -0.05f, -0.7f, 0.1f)
            quadraticTo(-0.85f, -0.05f, -1.0f, -0.2f)
            cubicTo(-0.85f, -0.5f, -0.5f, -0.55f, -0.2f, -0.28f)
            lineTo(-0.12f, -0.6f)
            close()
        },
        // 1: ghost
        Path().apply {
            moveTo(-0.7f, 0.8f)
            lineTo(-0.7f, -0.2f)
            cubicTo(-0.7f, -0.95f, 0.7f, -0.95f, 0.7f, -0.2f)
            lineTo(0.7f, 0.8f)
            quadraticTo(0.47f, 0.55f, 0.23f, 0.8f)
            quadraticTo(0f, 0.55f, -0.23f, 0.8f)
            quadraticTo(-0.47f, 0.55f, -0.7f, 0.8f)
            close()
        },
        // 2: jack-o'-lantern face (eyes, nose, jagged mouth)
        Path().apply {
            moveTo(-0.55f, -0.1f); lineTo(-0.2f, -0.1f); lineTo(-0.38f, -0.42f); close()
            moveTo(0.2f, -0.1f); lineTo(0.55f, -0.1f); lineTo(0.38f, -0.42f); close()
            moveTo(0f, 0.02f); lineTo(-0.1f, 0.18f); lineTo(0.1f, 0.18f); close()
            moveTo(-0.6f, 0.3f); lineTo(-0.35f, 0.2f); lineTo(-0.2f, 0.4f); lineTo(0f, 0.25f)
            lineTo(0.2f, 0.4f); lineTo(0.35f, 0.2f); lineTo(0.6f, 0.3f)
            lineTo(0.4f, 0.6f); lineTo(0f, 0.7f); lineTo(-0.4f, 0.6f); close()
        },
    )
    BackgroundStyle.BREAD -> listOf(
        // 0: a slice of toast with the two puffy shoulders.
        Path().apply {
            moveTo(-0.65f, 0.85f)
            lineTo(0.65f, 0.85f)
            quadraticTo(0.8f, 0.85f, 0.8f, 0.7f)
            lineTo(0.8f, -0.05f)
            cubicTo(1.05f, -0.1f, 1.05f, -0.85f, 0.5f, -0.85f)
            lineTo(-0.5f, -0.85f)
            cubicTo(-1.05f, -0.85f, -1.05f, -0.1f, -0.8f, -0.05f)
            lineTo(-0.8f, 0.7f)
            quadraticTo(-0.8f, 0.85f, -0.65f, 0.85f)
            close()
        },
    )
    else -> emptyList()
}

// ---- Cherry blossom, snow and Halloween ----
// These keep their own colours (pink, white, orange/purple) instead of following the accent, because the look is the point.

private val PetalColors = listOf(Color(0xFFF7B9D0), Color(0xFFF2A0BF), Color(0xFFE57BA3))
private val SnowColor = Color(0xFFEAF3FF)
// Halloween shapes are solid and dim (pre-blended against the dark page), so overlapping parts never add up brighter.
private val PumpkinOuter = Color(0xFF7A3A0C)
private val PumpkinCentre = Color(0xFF9A4A10)
private val PumpkinStem = Color(0xFF2E5A24)
private val BatColor = Color(0xFF4B3578)
private val GhostColor = Color(0xFF3F4252)

/** A petal that spins and tumbles (its width squeezes and opens like it is turning in the air). */
private fun DrawScope.drawPetal(path: Path, r: Float, item: Item, t: Float) {
    val tumble = 0.25f + 0.75f * abs(cos(t * 0.9f + item.phase))
    rotate(item.rot + item.spin * t, Offset.Zero) {
        scale(r * tumble, r, Offset.Zero) {
            drawPath(path, PetalColors[item.kind % PetalColors.size].copy(alpha = item.alpha))
        }
    }
}

/** Round flakes are plain dots; the bigger ones are six-armed crystals with small side branches. */
private fun DrawScope.drawSnow(r: Float, item: Item, angle: Float, density: Float) {
    if (item.kind == 0) {
        drawCircle(SnowColor.copy(alpha = item.alpha), radius = r)
        return
    }
    val color = SnowColor.copy(alpha = item.alpha)
    val stroke = 1.3f * density
    rotate(angle, Offset.Zero) {
        for (i in 0 until 6) {
            rotate(i * 60f, Offset.Zero) {
                drawLine(color, Offset.Zero, Offset(0f, -r), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0f, -r * 0.55f), Offset(-r * 0.24f, -r * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
                drawLine(color, Offset(0f, -r * 0.55f), Offset(r * 0.24f, -r * 0.8f), strokeWidth = stroke, cap = StrokeCap.Round)
            }
        }
    }
}

private fun DrawScope.drawHalloween(paths: List<Path>, r: Float, item: Item, angle: Float, t: Float) {
    when (item.kind) {
        0 -> rotate(angle, Offset.Zero) {
            scale(r, r, Offset.Zero) {
                drawOval(PumpkinOuter, topLeft = Offset(-1f, -0.75f), size = Size(1.1f, 1.5f))
                drawOval(PumpkinOuter, topLeft = Offset(-0.1f, -0.75f), size = Size(1.1f, 1.5f))
                drawOval(PumpkinCentre, topLeft = Offset(-0.55f, -0.85f), size = Size(1.1f, 1.7f))
                drawRoundRect(PumpkinStem, topLeft = Offset(-0.1f, -1.1f), size = Size(0.2f, 0.3f), cornerRadius = CornerRadius(0.06f))
                drawPath(paths[2], AnsuColors.Background)
            }
        }
        1 -> {
            // Wings beat: the bat squashes and stretches vertically.
            val flap = 0.7f + 0.3f * cos(t * 4f + item.phase)
            scale(r, r * flap, Offset.Zero) { drawPath(paths[0], BatColor) }
        }
        else -> scale(r, r, Offset.Zero) {
            drawPath(paths[1], GhostColor)
            drawOval(AnsuColors.Background, topLeft = Offset(-0.36f, -0.3f), size = Size(0.16f, 0.26f))
            drawOval(AnsuColors.Background, topLeft = Offset(0.2f, -0.3f), size = Size(0.16f, 0.26f))
            drawOval(AnsuColors.Background, topLeft = Offset(-0.1f, 0.12f), size = Size(0.2f, 0.26f))
        }
    }
}

// Bread is drawn in solid, dim browns (like the Halloween shapes) so overlaps never add up brighter.
private val BreadCrust = Color(0xFF6A4220)
private val BreadCrumb = Color(0xFF93703F)
private val BreadFace = Color(0xFF3A2410)
private val BreadCheek = Color(0xFFA0555F)
private val SandwichLettuce = Color(0xFF4A7A3A)
private val SandwichCheese = Color(0xFFA88B2A)
private val SandwichTomato = Color(0xFF8E3A33)

/** A toast slice (kind 0), a round bun (kind 1) or, rarely, a sandwich (kind 2) with a kawaii face; it wobbles and squishes as it floats. */
private fun DrawScope.drawBread(toast: Path, r: Float, item: Item, t: Float) {
    val wobble = 6f * sin(t * 0.8f + item.phase)
    val squish = 1f + 0.04f * sin(t * 1.6f + item.phase)
    rotate(item.rot + wobble, Offset.Zero) {
        scale(r / squish, r * squish, Offset.Zero) {
            if (item.kind == 2) {
                drawSandwichBody()
            } else if (item.kind == 0) {
                drawPath(toast, BreadCrust)
                scale(0.8f, 0.8f, Offset(0f, 0.05f)) { drawPath(toast, BreadCrumb) }
            } else {
                drawOval(BreadCrust, topLeft = Offset(-0.95f, -0.72f), size = Size(1.9f, 1.55f))
                drawOval(BreadCrumb, topLeft = Offset(-0.78f, -0.6f), size = Size(1.56f, 1.26f))
            }
            drawBreadFace(if (item.kind == 0) 0.05f else if (item.kind == 1) 0.0f else -0.4f, item, t)
        }
    }
}

/** Eyes (they blink now and then), pink cheeks and a small smile, in unit coordinates, eyes at [eyeY]. */
private fun DrawScope.drawBreadFace(eyeY: Float, item: Item, t: Float) {
    val blink = if (sin(t * 0.9f + item.phase) > 0.96f) 0.15f else 1f
    drawOval(BreadFace, topLeft = Offset(-0.32f, eyeY - 0.08f * blink), size = Size(0.14f, 0.16f * blink))
    drawOval(BreadFace, topLeft = Offset(0.18f, eyeY - 0.08f * blink), size = Size(0.14f, 0.16f * blink))
    drawOval(BreadCheek, topLeft = Offset(-0.58f, eyeY + 0.1f), size = Size(0.22f, 0.13f))
    drawOval(BreadCheek, topLeft = Offset(0.36f, eyeY + 0.1f), size = Size(0.22f, 0.13f))
    val smile = Path().apply {
        moveTo(-0.1f, eyeY + 0.14f)
        quadraticTo(0f, eyeY + 0.3f, 0.1f, eyeY + 0.14f)
    }
    drawPath(smile, BreadFace, style = Stroke(width = 0.05f, cap = StrokeCap.Round))
}

/** The rare sandwich: bread, lettuce, cheese and tomato, with the face on the top slice. */
private fun DrawScope.drawSandwichBody() {
    drawRoundRect(BreadCrust, topLeft = Offset(-0.85f, 0.3f), size = Size(1.7f, 0.42f), cornerRadius = CornerRadius(0.16f))
    drawRoundRect(BreadCrumb, topLeft = Offset(-0.78f, 0.3f), size = Size(1.56f, 0.3f), cornerRadius = CornerRadius(0.12f))
    var x = -0.8f
    while (x <= 0.81f) {
        drawCircle(SandwichLettuce, radius = 0.11f, center = Offset(x, 0.27f))
        x += 0.2f
    }
    drawRoundRect(SandwichCheese, topLeft = Offset(-0.92f, 0.1f), size = Size(1.84f, 0.13f), cornerRadius = CornerRadius(0.04f))
    drawRoundRect(SandwichTomato, topLeft = Offset(-0.8f, -0.03f), size = Size(1.6f, 0.14f), cornerRadius = CornerRadius(0.07f))
    drawRoundRect(BreadCrust, topLeft = Offset(-0.85f, -0.78f), size = Size(1.7f, 0.78f), cornerRadius = CornerRadius(0.42f, 0.42f))
    drawRoundRect(BreadCrumb, topLeft = Offset(-0.77f, -0.7f), size = Size(1.54f, 0.64f), cornerRadius = CornerRadius(0.34f, 0.34f))
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
