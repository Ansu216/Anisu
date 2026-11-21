package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** The one fixed gradient every title without a wide banner gets (the same for all of them, so it reads as "missing"). */
private val NoImageBrush = Brush.linearGradient(listOf(Color(0xFF232842), Color(0xFF3A2F63), Color(0xFF161827)))

/**
 * The picture for a title that has no horizontal banner: a static gradient with "No image" written on it. It fills
 * whatever size it is given and sits where the banner would be, with the usual fade and title drawn over it.
 *
 * [textBias] moves the text up (-1) or down (1) from the middle, for cards that have something else there (the
 * resume card's play button); [scale] sizes the text, like the enlarged copy of a card that is shown on long-press.
 */
@Composable
fun NoImageBanner(modifier: Modifier = Modifier, textBias: Float = 0f, scale: Float = 1f) {
    Box(
        modifier = modifier.fillMaxSize().background(NoImageBrush),
        contentAlignment = BiasAlignment(0f, textBias),
    ) {
        Text(
            text = "No image",
            color = Color.White.copy(alpha = 0.55f),
            fontSize = 13.sp * scale,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.6.sp,
        )
    }
}
