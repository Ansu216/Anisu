package com.ansu.anime.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Blurs this element while [active], easing the blur in and out. Put it on the page content under a full-screen
 * panel (welcome, error, links) so the page is a soft haze behind the frosted card instead of sharp text.
 * Does nothing below Android 12, where the panel's scrim is darker instead.
 */
@Composable
fun Modifier.blurWhile(active: Boolean, radius: Dp = 22.dp): Modifier {
    val blurRadius by animateDpAsState(
        targetValue = if (active && backdropBlurSupported) radius else 0.dp,
        animationSpec = tween(durationMillis = 280),
        label = "overlayBlur",
    )
    return if (blurRadius.value > 0.5f) this.blur(blurRadius) else this
}
