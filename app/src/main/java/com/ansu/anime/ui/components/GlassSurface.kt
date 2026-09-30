package com.ansu.anime.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * A "frosted glass" surface: a translucent fill over a soft 1dp light stroke.
 * True backdrop blur (sampling what's actually behind the card) needs either
 * Android 12+ RenderEffect plumbing or a blur library; this is the practical,
 * dependency-free approximation used throughout the app for pill buttons,
 * the top bar, and bottom navigation.
 *
 * When [blurRadius] is greater than zero the background layer gets a real platform blur on Android 12+
 * (API 31), while the card's content — icons and labels — is drawn unblurred on top. On Android 8–11,
 * which has no such effect, the radius is ignored and the card keeps its plain frosted look, so nothing
 * changes on older devices.
 */
@Composable
fun FrostedGlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    tintAlpha: Float = 0.35f,
    blurRadius: Dp = 0.dp,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.clip(shape)) {
        // Gradient fill with a touch of vertical structure, so a blur actually reads as softness
        // instead of acting on one flat colour (which would blur to itself, invisibly).
        val backgroundModifier = Modifier
            .matchParentSize()
            .then(
                if (blurRadius.value > 0f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    Modifier.blur(blurRadius, edgeTreatment = BlurredEdgeTreatment.Unbounded)
                } else {
                    Modifier
                },
            )
            .background(
                Brush.verticalGradient(
                    listOf(
                        AnsuColors.SurfaceGlassBase.copy(alpha = tintAlpha),
                        AnsuColors.SurfaceGlassBase.copy(alpha = (tintAlpha * 0.82f).coerceIn(0f, 1f)),
                    ),
                ),
            )
            .border(width = 1.dp, color = AnsuColors.StrokeGlass, shape = shape)
        Box(modifier = backgroundModifier)
        content()
    }
}

/**
 * Bottom-anchored gradient so overlaid title/button text stays legible on any art. [midAlpha] is the
 * black opacity reached at [midStop] (fraction of the height); lower values let more of the picture
 * show. The gradient always ends in the page background so the art melts into the content below.
 */
@Composable
fun BottomScrim(modifier: Modifier = Modifier, midAlpha: Float = 0.35f, midStop: Float = 0.55f) {
    Box(
        modifier = modifier.background(
            Brush.verticalGradient(
                0f to Color.Transparent,
                midStop to Color.Black.copy(alpha = midAlpha),
                1f to AnsuColors.Background,
            ),
        ),
    )
}
