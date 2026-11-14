@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.components

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * A top app bar that is solid behind the status bar and the title and then fades out smoothly at its bottom edge,
 * like the top bar of a chat app, instead of ending in a hard line. The fade runs a little past the bar's own
 * height, so the page scrolls under it: pair it with [withoutTop] and the scaffold's top padding on the scrolling
 * content (see the settings screens).
 */
@Composable
fun FadingTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    fadeBelow: Dp = 36.dp,
    fadeInside: Dp = 8.dp,
) {
    val base = AnsuColors.Background
    TopAppBar(
        title = title,
        modifier = modifier.drawBehind {
            val below = fadeBelow.toPx()
            val total = size.height + below
            val start = ((size.height - fadeInside.toPx()) / total).coerceIn(0f, 1f)
            // An eased fade (not a straight line), so there is no visible band where it starts or ends.
            fun at(t: Float) = start + (1f - start) * t
            drawRect(
                brush = Brush.verticalGradient(
                    colorStops = arrayOf(
                        0f to base,
                        start to base,
                        at(0.30f) to base.copy(alpha = 0.80f),
                        at(0.60f) to base.copy(alpha = 0.36f),
                        at(0.85f) to base.copy(alpha = 0.08f),
                        1f to Color.Transparent,
                    ),
                    startY = 0f,
                    endY = total,
                ),
                size = Size(size.width, total),
            )
        },
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
        ),
    )
}

/** The same padding without its top, so scrolling content can start under a [FadingTopAppBar]. */
fun PaddingValues.withoutTop(): PaddingValues {
    val source = this
    return object : PaddingValues {
        override fun calculateLeftPadding(layoutDirection: LayoutDirection) = source.calculateLeftPadding(layoutDirection)
        override fun calculateTopPadding() = 0.dp
        override fun calculateRightPadding(layoutDirection: LayoutDirection) = source.calculateRightPadding(layoutDirection)
        override fun calculateBottomPadding() = source.calculateBottomPadding()
    }
}
