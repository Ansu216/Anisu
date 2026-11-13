package com.ansu.anime.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.offset
import kotlin.math.roundToInt

/**
 * Whether Tablet mode is on (Appearance, unlocked by tapping the logo in About seven times). Provided once in
 * MainActivity; every screen reads it from here, so nothing has to be passed down.
 */
val LocalTabletMode = staticCompositionLocalOf { false }

/** Tablet mode: the rail is on the right, so the screens leave room on the right and mirror their banner. */
val LocalTabletRailRight = staticCompositionLocalOf { false }

/** Width of the side navigation rail that replaces the bottom bar in Tablet mode. */
val TabletRailWidth = 88.dp

/**
 * The rail's motion while it is dragged or flung to the other side. [exit] is 0 while the rail sits fully on screen
 * at its side and 1 once it has been pushed completely off that edge. The rail slides by it, and every tab screen
 * gives up or takes back the same amount of room (see [tabletRailPadding]), so the screen stretches after the rail
 * as it leaves and is pushed along when the rail comes in from the other edge.
 */
object TabletRailMotion {
    val exit = Animatable(0f)
}

/** Titles per page of an endless Home row: 10 on a phone, 15 in Tablet mode (wider rows show more at once). */
const val PHONE_FEED_PAGE_SIZE = 10
const val TABLET_FEED_PAGE_SIZE = 15

/** [phone] normally, [tablet] in Tablet mode. */
@Composable
fun tabletDp(phone: Dp, tablet: Dp): Dp = if (LocalTabletMode.current) tablet else phone

/**
 * Leaves room for the side rail (on the left or the right, as set) and nothing on a phone. The room follows
 * [TabletRailMotion] while the rail is dragged or moves to the other side. It is read while measuring, so a moving
 * rail only re-measures the screen each frame and never recomposes it.
 */
@Composable
fun Modifier.tabletRailPadding(): Modifier {
    if (!LocalTabletMode.current) return this
    val right = LocalTabletRailRight.current
    val railPx = with(LocalDensity.current) { TabletRailWidth.roundToPx() }
    return this.layout { measurable, constraints ->
        // Slightly above 1 is allowed so the rail's little settle-bounce pushes the screen along with it.
        val shown = (1f - TabletRailMotion.exit.value).coerceIn(0f, 1.2f)
        val inset = (railPx * shown).roundToInt().coerceIn(0, constraints.maxWidth)
        val placeable = measurable.measure(constraints.offset(horizontal = -inset))
        layout(placeable.width + inset, placeable.height) {
            placeable.place(if (right) 0 else inset, 0)
        }
    }
}

/**
 * Keeps a page at a readable width and centres it in Tablet mode (Settings and the other plain pages would
 * otherwise stretch their rows across the whole tablet). On a phone it simply draws [content].
 */
@Composable
fun TabletCentered(maxWidth: Dp = 720.dp, content: @Composable () -> Unit) {
    if (!LocalTabletMode.current) {
        content()
        return
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        Box(modifier = Modifier.widthIn(max = maxWidth).fillMaxHeight()) { content() }
    }
}
