package com.ansu.anime.ui.components

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** True when the platform can blur a layer (RenderEffect, Android 12+); older versions keep the plain frosted glass. */
val backdropBlurSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S

/**
 * A screen's content, recorded into a layer so a floating bar can draw a blurred copy of whatever is
 * scrolling behind it. The bar must be a sibling of the content (the Scaffold's bottomBar slot), never
 * inside it, otherwise it would try to draw itself into itself.
 */
class BackdropState internal constructor(internal val layer: GraphicsLayer) {
    internal var coordinates: LayoutCoordinates? = null
}

@Composable
fun rememberBackdropState(): BackdropState {
    val layer = rememberGraphicsLayer()
    return remember(layer) { BackdropState(layer) }
}

/** Marks the full-size content behind a floating bar as the source of its blurred backdrop. */
fun Modifier.backdropSource(state: BackdropState): Modifier = this
    .onGloballyPositioned { state.coordinates = it }
    .drawWithContent {
        state.layer.record { this@drawWithContent.drawContent() }
        drawLayer(state.layer)
    }

/** A plain (non-state) holder: layout coordinates change every frame while scrolling and must not recompose. */
private class BackdropBarHolder {
    var coordinates: LayoutCoordinates? = null
}

/**
 * Draws, inside [shape], the part of [state]'s content that sits behind this element, blurred by [radius].
 * Does nothing when there is no [state], when [enabled] is false or below Android 12, so the element simply
 * keeps its normal frosted look there.
 */
@Composable
fun Modifier.frostedBackdrop(state: BackdropState?, enabled: Boolean, shape: Shape, radius: Dp = 22.dp): Modifier {
    if (state == null || !enabled || !backdropBlurSupported) return this
    val blurLayer = rememberGraphicsLayer()
    val holder = remember { BackdropBarHolder() }
    val radiusPx = with(LocalDensity.current) { radius.toPx() }
    return this
        .onGloballyPositioned { holder.coordinates = it }
        .clip(shape)
        .drawBehind {
            val source = state.coordinates ?: return@drawBehind
            val me = holder.coordinates ?: return@drawBehind
            if (!source.isAttached || !me.isAttached) return@drawBehind
            val topLeft = source.localPositionOf(me, Offset.Zero)
            blurLayer.renderEffect = BlurEffect(radiusPx, radiusPx, TileMode.Clamp)
            blurLayer.record {
                translate(-topLeft.x, -topLeft.y) { drawLayer(state.layer) }
            }
            drawLayer(blurLayer)
        }
}
