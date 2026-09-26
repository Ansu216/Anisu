package com.ansu.anime.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
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

// Anisu's identity sits between its two references: CornCastle's warm,
// personal AniList-driven corner (the gold) and Nuvio's dark,
// playback-first media hub (everything else). One accent carries the
// brand; a muted teal is reserved for a single job — "new" badges — so it
// never competes with gold for attention.
val AnisuBackground = Color(0xFF101013)
val AnisuSurface = Color(0xFF17181C)
val AnisuSurfaceRaised = Color(0xFF212228)
val AnisuOutline = Color(0xFF2C2D34)
val AnisuGold = Color(0xFFE8A33D)
val AnisuGoldDim = Color(0xFF8A6329)
val AnisuTeal = Color(0xFF3FBF8F)
val AnisuTextPrimary = Color(0xFFF3F1EC)
val AnisuTextSecondary = Color(0xFFA6A5AD)
val AnisuError = Color(0xFFE0605A)

private val AnisuColorScheme = darkColorScheme(
    primary = AnisuGold,
    onPrimary = Color(0xFF241701),
    secondary = AnisuTeal,
    background = AnisuBackground,
    onBackground = AnisuTextPrimary,
    surface = AnisuSurface,
    onSurface = AnisuTextPrimary,
    surfaceVariant = AnisuSurfaceRaised,
    onSurfaceVariant = AnisuTextSecondary,
    outline = AnisuOutline,
    error = AnisuError,
)

// A deliberate scale rather than Material defaults: tight tracking on the
// small "eyebrow" shelf labels, a heavier display weight for the hero title.
val AnisuTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 38.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.2.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
)

// Posters stay sharp; continue-watching cards get a bit more radius so that
// row visually reads as "yours" against the plain catalogue rows.
val AnisuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun AnisuTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AnisuColorScheme,
        typography = AnisuTypography,
        shapes = AnisuShapes,
        content = content,
    )
}
