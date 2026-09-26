package com.kernel.anime.ui.theme

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

// Kernel's identity sits between its two references: CornCastle's warm,
// personal AniList-driven corner (the gold) and Nuvio's dark,
// playback-first media hub (everything else). One accent carries the
// brand; a muted teal is reserved for a single job — "new" badges — so it
// never competes with gold for attention.
val KernelBackground = Color(0xFF101013)
val KernelSurface = Color(0xFF17181C)
val KernelSurfaceRaised = Color(0xFF212228)
val KernelOutline = Color(0xFF2C2D34)
val KernelGold = Color(0xFFE8A33D)
val KernelGoldDim = Color(0xFF8A6329)
val KernelTeal = Color(0xFF3FBF8F)
val KernelTextPrimary = Color(0xFFF3F1EC)
val KernelTextSecondary = Color(0xFFA6A5AD)
val KernelError = Color(0xFFE0605A)

private val KernelColorScheme = darkColorScheme(
    primary = KernelGold,
    onPrimary = Color(0xFF241701),
    secondary = KernelTeal,
    background = KernelBackground,
    onBackground = KernelTextPrimary,
    surface = KernelSurface,
    onSurface = KernelTextPrimary,
    surfaceVariant = KernelSurfaceRaised,
    onSurfaceVariant = KernelTextSecondary,
    outline = KernelOutline,
    error = KernelError,
)

// A deliberate scale rather than Material defaults: tight tracking on the
// small "eyebrow" shelf labels, a heavier display weight for the hero title.
val KernelTypography = Typography(
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
val KernelShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(6.dp),
    medium = RoundedCornerShape(10.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)

@Composable
fun KernelTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = KernelColorScheme,
        typography = KernelTypography,
        shapes = KernelShapes,
        content = content,
    )
}
