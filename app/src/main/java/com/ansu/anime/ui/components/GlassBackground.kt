package com.ansu.anime.ui.components

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Live-observable Pixel OS Frosted Glass tuning state.
 * Changing any property immediately recomposes all cards, bars, menus, bottom sheets, and dialogs!
 */
object GlassTuning {
    private const val PREFS_NAME = "ansu_glass_tuning"

    /** Radius of the backdrop blur behind all cards (0dp..60dp, Pixel OS default = 38dp). */
    var blurRadiusDp by mutableFloatStateOf(38f)

    /** Tonal surface opacity for cards and bars (0.10f..1.00f, Pixel OS default = 0.76f). */
    var cardTintAlpha by mutableFloatStateOf(0.76f)

    /** Tonal surface opacity for menus, sheets, and dialogs (0.20f..1.00f, Pixel OS default = 0.82f). */
    var popupTintAlpha by mutableFloatStateOf(0.82f)

    /** Cross-window blur radius behind popups, sheets, and dialogs (0dp..80dp, default = 42dp). */
    var windowBlurDp by mutableFloatStateOf(42f)

    /** Top specular highlight sheen (0.00f..0.30f, Pixel OS matte default = 0.04f). */
    var topSheenAlpha by mutableFloatStateOf(0.04f)

    /** 1dp hairline border opacity around cards and menus (0.00f..0.50f, default = 0.14f). */
    var borderAlpha by mutableFloatStateOf(0.14f)

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        blurRadiusDp = prefs.getFloat("blurRadiusDp", 38f)
        cardTintAlpha = prefs.getFloat("cardTintAlpha", 0.76f)
        popupTintAlpha = prefs.getFloat("popupTintAlpha", 0.82f)
        windowBlurDp = prefs.getFloat("windowBlurDp", 42f)
        topSheenAlpha = prefs.getFloat("topSheenAlpha", 0.04f)
        borderAlpha = prefs.getFloat("borderAlpha", 0.14f)
    }

    fun save(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putFloat("blurRadiusDp", blurRadiusDp)
            .putFloat("cardTintAlpha", cardTintAlpha)
            .putFloat("popupTintAlpha", popupTintAlpha)
            .putFloat("windowBlurDp", windowBlurDp)
            .putFloat("topSheenAlpha", topSheenAlpha)
            .putFloat("borderAlpha", borderAlpha)
            .apply()
    }

    fun resetToPixelOsDefaults(context: Context? = null) {
        blurRadiusDp = 38f
        cardTintAlpha = 0.76f
        popupTintAlpha = 0.82f
        windowBlurDp = 42f
        topSheenAlpha = 0.04f
        borderAlpha = 0.14f
        context?.let { save(it) }
    }
}

/**
 * Something that lies BEHIND glass cards and is recorded once, together with one blurred copy of it that every card
 * reads. One full-size blur is made per source and shared (not one per card), so the cost does not grow with the cards.
 */
internal class GlassBackground(val layer: GraphicsLayer, val blurred: GraphicsLayer) {
    /** Where the source sits in the window; observable so a card redraws if the source ever moves. */
    var origin: Offset by mutableStateOf(Offset.Zero)
}

internal val LocalGlassBackground = staticCompositionLocalOf<GlassBackground?> { null }

@Composable
internal fun rememberGlassBackground(radius: Dp = GlassTuning.blurRadiusDp.dp): GlassBackground {
    val layer = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    remember(blurred, radiusPx) {
        blurred.renderEffect = if (backdropBlurSupported && radiusPx > 0.5f) {
            BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
        } else {
            null
        }
    }
    return remember(layer, blurred) { GlassBackground(layer, blurred) }
}

/**
 * Records this element's content as the source behind glass cards. The element still draws normally. [base] is put
 * under the blurred copy only (the page colour for the see-through animated background), so the haze is even instead
 * of patchy.
 */
internal fun Modifier.recordGlassBackground(background: GlassBackground, base: Color?): Modifier =
    this
        .onGloballyPositioned { background.origin = it.positionInWindow() }
        .drawWithContent {
            background.layer.record { this@drawWithContent.drawContent() }
            if (backdropBlurSupported) {
                background.blurred.record {
                    if (base != null) drawRect(base)
                    drawLayer(background.layer)
                }
            }
            drawLayer(background.layer)
        }

/** What a card knows about itself: where it sits in the window. */
internal class GlassCardPlace {
    var origin: Offset by mutableStateOf(Offset.Zero)
}

/** Draws the part of [background]'s blurred copy that lies behind a card at [place]. */
internal fun DrawScope.drawGlassBackground(background: GlassBackground?, place: GlassCardPlace) {
    if (background == null || !backdropBlurSupported || GlassTuning.blurRadiusDp <= 0.5f) return
    val topLeft = place.origin - background.origin
    translate(-topLeft.x, -topLeft.y) { drawLayer(background.blurred) }
}
