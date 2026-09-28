package com.ansu.anime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Ansu's palette: deep near-black surfaces with a single white accent for CTAs,
 * progress bars and focus states — replaces Kernel's gold/teal identity as part
 * of the redesign. Surfaces are base colors WITHOUT alpha baked in; alpha is
 * applied at the composable level (see GlassSurface.kt) so one token works at
 * different opacities for cards, sheets, and nav bars.
 */
object AnsuColors {
    val Background = Color(0xFF07080B)
    val BackgroundElevated = Color(0xFF0F1015)
    val SurfaceGlassBase = Color(0xFF1B1D27)
    val StrokeGlass = Color(0xFFFFFFFF).copy(alpha = 0.08f)

    val Accent = Color(0xFFFFFFFF)
    val AccentSoft = Color(0xFFFFFFFF).copy(alpha = 0.16f)

    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFFA0A3B1)
    val TextTertiary = Color(0xFF6B6E7C)

    val ScoreGreen = Color(0xFF4CD137)
    val Error = Color(0xFFE0605A)
}

private val AnsuColorScheme = darkColorScheme(
    primary = AnsuColors.Accent,
    onPrimary = AnsuColors.Background,
    secondary = AnsuColors.TextSecondary,
    background = AnsuColors.Background,
    onBackground = AnsuColors.TextPrimary,
    surface = AnsuColors.BackgroundElevated,
    onSurface = AnsuColors.TextPrimary,
    surfaceVariant = AnsuColors.SurfaceGlassBase,
    onSurfaceVariant = AnsuColors.TextSecondary,
    outline = AnsuColors.StrokeGlass,
    error = AnsuColors.Error,
)

val AnsuTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
)

val AnsuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AnsuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnsuColorScheme,
        typography = AnsuTypography,
        shapes = AnsuShapes,
        content = content,
    )
}
