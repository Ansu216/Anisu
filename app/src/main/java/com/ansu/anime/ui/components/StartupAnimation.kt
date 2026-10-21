package com.ansu.anime.ui.components

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.R
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** The logo's A, cropped tight, so its width is this much of its height. */
private const val LOGO_ASPECT = 1.0363f

/** The rest of the name, after the logo that stands in for its A. */
private const val WORD = "nisu"

/** Height of the logo's A. The other letters are set a little smaller than it. */
private val LogoHeight = 64.dp

/** The letters are sized so the tops of "n", "s" and "u" sit well under the A and the dot of the "i" just under its tip. */
private const val LETTER_SIZE_PER_LOGO = 1.18f

/** Each letter starts this much later (of the reveal) than the one before it. */
private const val LETTER_STAGGER = 0.16f

/**
 * The start-up animation: the logo's A fades in alone on black, then slides left as the letters of the name appear
 * one after another to its right, in bold white, and the whole thing fades out into the app. It sits over the app
 * while the app loads underneath, takes every touch while it plays, and is skipped when the system's animations are off.
 */
@Composable
fun StartupAnimation(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val logoAlpha = remember { Animatable(0f) }
    val logoScale = remember { Animatable(0.82f) }
    val reveal = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val animationsOff = runCatching {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
        }.getOrDefault(false)
        if (animationsOff) {
            onFinished()
            return@LaunchedEffect
        }
        // The A comes in on its own...
        coroutineScope {
            launch { logoAlpha.animateTo(1f, tween(450, easing = FastOutSlowInEasing)) }
            launch { logoScale.animateTo(1f, tween(600, easing = FastOutSlowInEasing)) }
        }
        delay(200)
        // ...then the letters follow, which moves the A left because the row stays centred as it grows.
        reveal.animateTo(1f, tween(1000, easing = LinearEasing))
        delay(450)
        fade.animateTo(0f, tween(300))
        onFinished()
    }

    val density = LocalDensity.current
    val letterSize = with(density) { (LogoHeight.value * LETTER_SIZE_PER_LOGO).dp.toSp() }
    val slidePx = with(density) { 18.dp.toPx() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(AnsuColors.Background)
            // Nothing underneath can be touched while the animation plays.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent().changes.forEach { it.consume() }
                    }
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        Row {
            Image(
                painter = painterResource(R.drawable.splash_logo_a),
                contentDescription = null,
                modifier = Modifier
                    .height(LogoHeight)
                    .aspectRatio(LOGO_ASPECT)
                    // The bottom of the A is the line the letters stand on.
                    .alignBy { it.measuredHeight }
                    .graphicsLayer {
                        alpha = logoAlpha.value
                        scaleX = logoScale.value
                        scaleY = logoScale.value
                    },
            )
            WORD.forEachIndexed { index, letter ->
                RevealLetter(
                    letter = letter,
                    index = index,
                    reveal = { reveal.value },
                    fontSize = letterSize,
                    slidePx = slidePx,
                    // A little air between the A and the first letter, inside that letter so the A starts centred.
                    leadingGap = if (index == 0) 6.dp else 0.dp,
                )
            }
        }
    }
}

/** How far letter [index] has come in (0..1) when the whole reveal is at [t]; the letters start one after another. */
private fun letterProgress(t: Float, index: Int): Float {
    val start = index * LETTER_STAGGER
    val span = 1f - (WORD.length - 1) * LETTER_STAGGER
    return FastOutSlowInEasing.transform(((t - start) / span).coerceIn(0f, 1f))
}

/**
 * One letter of the name. It takes up room as it comes in (which is what pushes the A to the left), fades in and slides
 * out from behind its neighbour on the left. [reveal] is read while laying out and drawing only, so the animation never
 * recomposes anything.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.RevealLetter(
    letter: Char,
    index: Int,
    reveal: () -> Float,
    fontSize: TextUnit,
    slidePx: Float,
    leadingGap: androidx.compose.ui.unit.Dp,
) {
    Text(
        text = letter.toString(),
        color = Color.White,
        fontSize = fontSize,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .alignByBaseline()
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0, maxWidth = Constraints.Infinity))
                val p = letterProgress(reveal(), index)
                val width = (placeable.width * p).roundToInt()
                // The baseline is passed on, or the row could not stand the letters on the same line as the A.
                layout(width, placeable.height, mapOf(LastBaseline to placeable[LastBaseline])) {
                    placeable.placeRelative(0, 0)
                }
            }
            .graphicsLayer {
                val p = letterProgress(reveal(), index)
                alpha = p
                translationX = (1f - p) * -slidePx
            }
            .padding(start = leadingGap),
    )
}
