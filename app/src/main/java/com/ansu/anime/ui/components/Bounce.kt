package com.ansu.anime.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonColors
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width

/** How far a pressed button shrinks; it springs back past 1 a little, which is the bounce. */
private const val PRESSED_SCALE = 0.86f

/**
 * A small bounce on press: the element dips while a finger is down and springs back with a slight overshoot when it
 * lifts. The touch is only watched (never consumed), so the element's own click handling is untouched.
 */
fun Modifier.pressBounce(): Modifier = composed {
    var pressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESSED_SCALE else 1f,
        animationSpec = spring(dampingRatio = 0.38f, stiffness = Spring.StiffnessMedium),
        label = "pressBounce",
    )
    this
        .pointerInput(Unit) {
            awaitEachGesture {
                awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                pressed = true
                do {
                    val event = awaitPointerEvent(PointerEventPass.Initial)
                } while (event.changes.any { it.pressed })
                pressed = false
            }
        }
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
}

/** [IconButton] with [pressBounce]: every tap, close and back button in the app bounces the same way. */
@Composable
fun BouncyIconButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: MutableInteractionSource? = null,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.pressBounce(),
        enabled = enabled,
        colors = colors,
        interactionSource = interactionSource,
        content = content,
    )
}

/**
 * Buttons that sit side by side and react to each other, like Material 3 Expressive button groups: pressing one makes
 * it swell and squeezes its neighbours a little, then everything springs back with a slight overshoot.
 *
 * Give the row one [rememberPressGroup]. Fixed-size buttons use [pressGroupWidth]; a button that stretches to fill the
 * row (a `weight(1f)` one) only needs [pressGroupMember], and simply gives up or takes back the room the others use.
 */
@Stable
class PressGroup {
    /** Index of the member under a finger right now, or -1. */
    var pressed by mutableIntStateOf(-1)
}

@Composable
fun rememberPressGroup(): PressGroup = remember { PressGroup() }

/** Reports presses on this element to [group] as member [index]. Touches are only watched, never consumed. */
fun Modifier.pressGroupMember(group: PressGroup, index: Int): Modifier = pointerInput(group, index) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        group.pressed = index
        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
        } while (event.changes.any { it.pressed })
        group.pressed = -1
    }
}

/** A fixed-width member of [group]: [base] wide at rest, wider while pressed, narrower while a neighbour is pressed. */
@Composable
fun Modifier.pressGroupWidth(
    group: PressGroup,
    index: Int,
    base: Dp,
    expand: Dp = 14.dp,
    squeeze: Dp = 6.dp,
): Modifier {
    val target = when {
        group.pressed == index -> base + expand
        group.pressed >= 0 -> base - squeeze
        else -> base
    }
    val width by animateDpAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMediumLow),
        label = "pressGroupWidth",
    )
    return this.width(width).pressGroupMember(group, index)
}
