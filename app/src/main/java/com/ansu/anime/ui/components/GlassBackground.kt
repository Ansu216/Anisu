package com.ansu.anime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
 * Something that lies BEHIND glass cards and is recorded once, together with one blurred copy of it that every card
 * reads. A card cannot blur the screen it is part of, but it can blur a layer that sits behind the whole screen: the
 * animated background, or a banner's artwork. Where a drifting shape or a picture is under the card the blur shows it
 * as a soft haze (frosted); where there is nothing but the page colour the card stays plain glass. Nothing is switched
 * by hand: what is behind the card decides how it looks.
 *
 * One full-size blur is made per source and shared (not one per card), so the cost does not grow with the cards.
 */
internal class GlassBackground(val layer: GraphicsLayer, val blurred: GraphicsLayer) {
    /** Where the source sits in the window; observable so a card redraws if the source ever moves. */
    var origin: Offset by mutableStateOf(Offset.Zero)
}

internal val LocalGlassBackground = staticCompositionLocalOf<GlassBackground?> { null }

/** The blur of a [GlassBackground]: strong enough to turn shapes into a haze, as frosted glass does. */
internal val GLASS_BACKGROUND_BLUR = 28.dp

@Composable
internal fun rememberGlassBackground(radius: Dp = GLASS_BACKGROUND_BLUR): GlassBackground {
    val layer = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    remember(blurred, radiusPx) {
        if (backdropBlurSupported) blurred.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
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
    if (background == null || !backdropBlurSupported) return
    val topLeft = place.origin - background.origin
    translate(-topLeft.x, -topLeft.y) { drawLayer(background.blurred) }
}
