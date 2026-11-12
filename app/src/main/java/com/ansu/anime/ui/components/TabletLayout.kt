package com.ansu.anime.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Whether Tablet mode is on (Appearance, unlocked by tapping the logo in About seven times). Provided once in
 * MainActivity; every screen reads it from here, so nothing has to be passed down.
 */
val LocalTabletMode = staticCompositionLocalOf { false }

/** Tablet mode: the rail is on the right, so the screens leave room on the right and mirror their banner. */
val LocalTabletRailRight = staticCompositionLocalOf { false }

/** Width of the side navigation rail that replaces the bottom bar in Tablet mode. */
val TabletRailWidth = 88.dp

/** Titles per page of an endless Home row: 10 on a phone, 15 in Tablet mode (wider rows show more at once). */
const val PHONE_FEED_PAGE_SIZE = 10
const val TABLET_FEED_PAGE_SIZE = 15

/** [phone] normally, [tablet] in Tablet mode. */
@Composable
fun tabletDp(phone: Dp, tablet: Dp): Dp = if (LocalTabletMode.current) tablet else phone

/** Leaves room for the side rail on the left; the four tab screens use it, and it does nothing on a phone. */
@Composable
fun Modifier.tabletRailPadding(): Modifier =
    when {
        !LocalTabletMode.current -> this
        LocalTabletRailRight.current -> this.padding(end = TabletRailWidth)
        else -> this.padding(start = TabletRailWidth)
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
