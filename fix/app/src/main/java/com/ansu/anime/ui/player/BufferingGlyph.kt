package com.ansu.anime.ui.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The pause bars turning into a loading spinner and back.
 *
 * Buffering starts: the two bars squeeze thin, curl into the two halves of a ring and start spinning.
 * Buffering ends: the ring slows to upright while it unrolls into bars again, which spring back to full width
 * with a little puff. All three values are drawn straight from the draw phase, so nothing recomposes per frame
 * except the cheap [active] check.
 */
@Stable
class BufferingMorph {
    /** 0 = full bars, 1 = thin. Dips below 0 for an instant on the way back: that is the puff. */
    val squeeze = Animatable(0f)

    /** 0 = straight bars, 1 = ring. */
    val bend = Animatable(0f)

    /** Rotation in degrees; a multiple of 360 means upright. */
    val spin = Animatable(0f)

    /** True from the first squeeze until the bars are fully back, i.e. while the glyph is not just the bars. */
    val active: Boolean get() = abs(squeeze.value) > 0.002f || bend.value > 0.001f

    suspend fun run(buffering: Boolean) = coroutineScope {
        if (buffering) {
            squeeze.animateTo(1f, tween(220, easing = FastOutSlowInEasing))
            val spinner = launch {
                while (true) spin.animateTo(spin.value + 360f, tween(900, easing = LinearEasing))
            }
            bend.animateTo(1f, tween(420, easing = FastOutSlowInEasing))
            spinner.join() // keeps spinning until the next run() cancels this one
        } else {
            val upright = ceil(spin.value / 360f - 0.0001f) * 360f
            launch { spin.animateTo(upright, tween(420, easing = FastOutSlowInEasing)) }
            bend.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            squeeze.animateTo(0f, spring(dampingRatio = 0.5f, stiffness = 380f))
            spin.snapTo(0f)
        }
    }
}

@Composable
fun rememberBufferingMorph(buffering: Boolean): BufferingMorph {
    val morph = remember { BufferingMorph() }
    LaunchedEffect(buffering) { morph.run(buffering) }
    return morph
}

/** Draws the glyph centred in the 64dp (times [scale]) box. */
fun DrawScope.drawBufferingMorph(morph: BufferingMorph, scale: Float, color: Color) {
    val unit = 1.dp.toPx() * scale
    val squeeze = morph.squeeze.value
    val bend = morph.bend.value
    val strokeWidth = ((13f + (4f - 13f) * squeeze) * unit).coerceAtLeast(unit)
    val barOffset = 10.5f * unit
    val halfLength = ((40f * unit) - strokeWidth) / 2f
    val radius = 17f * unit
    val sweep = 150f // each half covers 150 degrees, leaving the two gaps that make it read as a spinner
    val center = Offset(size.width / 2f, size.height / 2f)
    val steps = 28
    rotate(morph.spin.value, center) {
        for (side in intArrayOf(-1, 1)) {
            val path = Path()
            for (i in 0..steps) {
                val t = i / steps.toFloat()
                val lineX = side * barOffset
                val lineY = -halfLength + 2f * halfLength * t
                val degrees = if (side < 0) 180f + sweep / 2f - sweep * t else -sweep / 2f + sweep * t
                val angle = Math.toRadians(degrees.toDouble())
                val arcX = (radius * cos(angle)).toFloat()
                val arcY = (radius * sin(angle)).toFloat()
                val x = center.x + lineX + (arcX - lineX) * bend
                val y = center.y + lineY + (arcY - lineY) * bend
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = color,
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
}

/**
 * The same glyph on its own, for when the transport controls are hidden or still loading. It fades out after the
 * bars have puffed back, since there is nothing to show while the video plays with the controls hidden.
 */
@Composable
fun BufferingIndicator(buffering: Boolean, modifier: Modifier = Modifier, scale: Float = 1f) {
    val morph = rememberBufferingMorph(buffering)
    val alpha by animateFloatAsState(
        targetValue = if (buffering) 1f else 0f,
        animationSpec = tween(durationMillis = 220, delayMillis = if (buffering) 0 else 450),
        label = "bufferingAlpha",
    )
    if (alpha > 0.001f || morph.active) {
        Canvas(modifier = modifier.size((64 * scale).dp).graphicsLayer { this.alpha = alpha }) {
            drawBufferingMorph(morph, scale, AnsuColors.Accent)
        }
    }
}
