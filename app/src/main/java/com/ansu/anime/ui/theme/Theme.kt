package com.ansu.anime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
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
    // Pure black when the AMOLED switch in Appearance is on (state, so every screen redraws at once).
    private var amoledState by mutableStateOf(false)
    val Background: Color get() = if (amoledState) Color.Black else Color(0xFF07080B)
    val BackgroundElevated: Color get() = if (amoledState) Color(0xFF050506) else Color(0xFF0F1015)

    fun setAmoled(enabled: Boolean) {
        amoledState = enabled
    }
    val SurfaceGlassBase = Color(0xFF1B1D27)
    val StrokeGlass = Color(0xFFFFFFFF).copy(alpha = 0.08f)

    // The accent is state, so every screen reading it redraws the moment a new palette is picked.
    private var accentState by mutableStateOf(Color(0xFFFFFFFF))
    val Accent: Color get() = accentState
    val AccentSoft: Color get() = accentState.copy(alpha = 0.16f)

    /** Text and icons drawn on top of the accent: dark on the original white, white on every other colour. */
    val OnAccent: Color get() = if (accentState == Color.White) Background else Color.White

    fun setAccent(color: Color) {
        accentState = color
    }

    val TextPrimary = Color(0xFFF3F4F6)
    val TextSecondary = Color(0xFFA0A3B1)
    val TextTertiary = Color(0xFF6B6E7C)

    val ScoreGreen = Color(0xFF4CD137)
    val Error = Color(0xFFE0605A)
}

private fun ansuColorScheme(accent: Color) = darkColorScheme(
    primary = accent,
    onPrimary = AnsuColors.OnAccent,
    secondary = AnsuColors.TextSecondary,
    // See-through, so the animated background (drawn behind the screens by the nav host) shows in the empty space.
    // The nav host paints AnsuColors.Background under everything, so with no animation nothing changes.
    background = Color.Transparent,
    onBackground = AnsuColors.TextPrimary,
    surface = AnsuColors.BackgroundElevated,
    onSurface = AnsuColors.TextPrimary,
    surfaceVariant = AnsuColors.SurfaceGlassBase,
    onSurfaceVariant = AnsuColors.TextSecondary,
    outline = AnsuColors.StrokeGlass,
    error = AnsuColors.Error,
)

fun ansuTypography(family: FontFamily = FontFamily.Default): Typography = withFont(Typography(
    displayLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.ExtraBold, fontSize = 28.sp, lineHeight = 34.sp, letterSpacing = (-0.3).sp),
    headlineMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 15.sp, lineHeight = 21.sp),
    bodyMedium = TextStyle(fontFamily = family, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp),
    labelSmall = TextStyle(fontFamily = family, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.3.sp),
), family)

/** The styles the app does not define itself (titleLarge, bodySmall, labelLarge, ...) get the same font. */
private fun withFont(t: Typography, family: FontFamily) = t.copy(
    displayMedium = t.displayMedium.copy(fontFamily = family),
    displaySmall = t.displaySmall.copy(fontFamily = family),
    headlineLarge = t.headlineLarge.copy(fontFamily = family),
    headlineSmall = t.headlineSmall.copy(fontFamily = family),
    titleLarge = t.titleLarge.copy(fontFamily = family),
    bodySmall = t.bodySmall.copy(fontFamily = family),
    labelLarge = t.labelLarge.copy(fontFamily = family),
    labelMedium = t.labelMedium.copy(fontFamily = family),
)

/**
 * The app's own font, read from assets/fonts (see the README there). Only files that exist are used, so the app
 * never fails to start without them; with none it keeps the phone's system font.
 */
@Composable
private fun rememberAnsuFontFamily(): FontFamily {
    val context = LocalContext.current
    return remember {
        val assets = context.assets
        val present = runCatching { assets.list("fonts")?.toSet() }.getOrNull().orEmpty()
        fun file(name: String) = name.takeIf { it in present }
        val regular = file("ansu_sans_regular.ttf")
        if (regular == null) {
            FontFamily.Default
        } else {
            val medium = file("ansu_sans_medium.ttf") ?: regular
            val bold = file("ansu_sans_bold.ttf") ?: medium
            FontFamily(
                Font("fonts/$regular", assets, FontWeight.Normal),
                Font("fonts/$medium", assets, FontWeight.Medium),
                Font("fonts/$medium", assets, FontWeight.SemiBold),
                Font("fonts/$bold", assets, FontWeight.Bold),
                Font("fonts/$bold", assets, FontWeight.ExtraBold),
            )
        }
    }
}

val AnsuShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun AnsuTheme(content: @Composable () -> Unit) {
    val accent = AnsuColors.Accent
    // The font the person added in Appearance wins over the one shipped in assets.
    val family = AnsuFont.appFamily ?: rememberAnsuFontFamily()
    MaterialTheme(
        colorScheme = remember(accent, AnsuColors.Background) { ansuColorScheme(accent) },
        typography = remember(family) { ansuTypography(family) },
        shapes = AnsuShapes,
    ) {
        // Text outside a Surface would otherwise fall back to the system font; this makes every Text inherit the app font.
        androidx.compose.material3.ProvideTextStyle(MaterialTheme.typography.bodyLarge, content)
    }
}
