package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * A "frosted glass" surface: a translucent fill over a soft 1dp light stroke.
 * True backdrop blur (sampling what's actually behind the card) needs either
 * Android 12+ RenderEffect plumbing or a blur library; this is the practical,
 * dependency-free approximation used throughout the app for pill buttons,
 * the top bar, and bottom navigation.
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tintAlpha: Float = 0.35f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(AnsuColors.SurfaceGlassBase.copy(alpha = tintAlpha))
            .border(width = 1.dp, color = AnsuColors.StrokeGlass, shape = shape),
    ) {
        content()
    }
}

/** Bottom-anchored gradient so overlaid title/button text stays legible on any art. */
@Composable
fun BottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.55f to Color.Black.copy(alpha = 0.35f),
                1f to AnsuColors.Background,
            ),
        ),
    )
}
