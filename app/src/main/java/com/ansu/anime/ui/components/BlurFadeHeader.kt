package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.getValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * A tab's title area with a frosted top that melts away at its bottom edge, like the top of HyperOS apps: whatever
 * scrolls underneath is blurred behind the title and fades out smoothly instead of ending in a hard line.
 *
 * Place it as a SIBLING of the content marked with `backdropSource(backdrop)` (an overlay on top of it), and give the
 * scrolling content a top padding of this header's height so it starts below the title and scrolls up under it.
 * Below Android 12 (no blur) it is a soft dark fade instead.
 */
@Composable
fun BlurFadeHeader(
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
    fade: Dp = 28.dp,
    blurRadius: Dp = 20.dp,
    content: @Composable () -> Unit,
) {
    val base = AnsuColors.Background
    Box(
        modifier = modifier
            .fillMaxWidth()
            // Everything inside (the blurred copy, the tint and the title) is drawn into one layer and then
            // faded together by the mask below.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithContent {
                drawContent()
                val fadePx = fade.toPx()
                val start = ((size.height - fadePx) / size.height).coerceIn(0f, 1f)
                fun at(t: Float) = start + (1f - start) * t
                // Eased, not a straight line, so there is no visible band where the fade starts or ends.
                drawRect(
                    brush = Brush.verticalGradient(
                        colorStops = arrayOf(
                            0f to Color.Black,
                            start to Color.Black,
                            at(0.30f) to Color.Black.copy(alpha = 0.80f),
                            at(0.60f) to Color.Black.copy(alpha = 0.36f),
                            at(0.85f) to Color.Black.copy(alpha = 0.08f),
                            1f to Color.Transparent,
                        ),
                        startY = 0f,
                        endY = size.height,
                    ),
                    blendMode = BlendMode.DstIn,
                )
            }
            .frostedBackdrop(backdrop, enabled = true, shape = RectangleShape, radius = blurRadius)
            .background(base.copy(alpha = if (backdropBlurSupported) 0.30f else 0.88f)),
    ) {
        Box(modifier = Modifier.padding(bottom = fade)) { content() }
    }
}

/**
 * Just the status-bar part of [BlurFadeHeader]: content that scrolls up under the status bar is blurred and fades out
 * below it, so the clock and icons never sit on top of sharp text. [visible] lets full-bleed screens (a banner at the
 * top) switch it on only once the page has scrolled; it fades in and out. Place it as a sibling above the content that
 * is marked with `backdropSource(backdrop)`.
 */
@Composable
fun StatusBarBlurFade(
    backdrop: BackdropState,
    modifier: Modifier = Modifier,
    visible: Boolean = true,
    fade: Dp = 24.dp,
) {
    val alpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = androidx.compose.animation.core.tween(durationMillis = 180),
        label = "statusBarBlurFade",
    )
    if (alpha <= 0f) return
    val statusBarHeight = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    BlurFadeHeader(
        backdrop = backdrop,
        modifier = modifier.graphicsLayer { this.alpha = alpha },
        fade = fade,
    ) {
        Spacer(Modifier.height(statusBarHeight))
    }
}
