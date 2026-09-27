package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.StreamHubColors

@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tintAlpha: Float = 0.4f,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        StreamHubColors.SurfaceGlassBase.copy(alpha = tintAlpha + 0.4f),
                        StreamHubColors.SurfaceGlassBase.copy(alpha = tintAlpha),
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
