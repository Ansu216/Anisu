package com.ansu.anime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object StreamHubColors {
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
}

private val StreamHubDarkScheme = darkColorScheme(
    background = StreamHubColors.Background,
    surface = StreamHubColors.BackgroundElevated,
    primary = StreamHubColors.Accent,
    onPrimary = StreamHubColors.Background,
    onBackground = StreamHubColors.TextPrimary,
    onSurface = StreamHubColors.TextPrimary,
)

@Composable
fun StreamHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = StreamHubDarkScheme,
        content = content,
    )
}
