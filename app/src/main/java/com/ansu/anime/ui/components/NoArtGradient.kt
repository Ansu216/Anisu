package com.ansu.anime.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path

/** Three stops of one colour family, dark at the top, lit in the middle, deeper at the bottom. */
private class ArtPalette(val top: Color, val mid: Color, val bottom: Color)

private val ArtPalettes = listOf(
    ArtPalette(Color(0xFF3A1210), Color(0xFF7F3326), Color(0xFF5A2A1E)), // ember
    ArtPalette(Color(0xFF10261A), Color(0xFF2F6B3A), Color(0xFF1F4A2B)), // forest
    ArtPalette(Color(0xFF2B1C0E), Color(0xFF76562F), Color(0xFF4E3A20)), // amber
    ArtPalette(Color(0xFF0C2428), Color(0xFF1F6B72), Color(0xFF164A52)), // lagoon
    ArtPalette(Color(0xFF14163A), Color(0xFF3B3F9A), Color(0xFF292C70)), // indigo
    ArtPalette(Color(0xFF2A1032), Color(0xFF7A3A8C), Color(0xFF52275F)), // plum
    ArtPalette(Color(0xFF121A28), Color(0xFF3C5A86), Color(0xFF29405F)), // steel
    ArtPalette(Color(0xFF2E0E18), Color(0xFF8A2C46), Color(0xFF5F1E30)), // crimson
)

/**
 * The picture for a title that has no banner (or cover) yet: a soft colour gradient with one big
 * translucent circle, a darker band along the bottom and a slanted slab, and nothing written on it.
 *
 * The colours and the circle's position come from [seed] (the title's id or name), so a title always
 * looks the same and neighbouring titles differ. It fills whatever size it is given and is meant to be
 * put where an image would be, with the usual fade and title drawn over it.
 */
@Composable
fun NoArtGradient(seed: String, modifier: Modifier = Modifier) {
    val hash = remember(seed) { seed.hashCode() }
    val palette = remember(hash) { ArtPalettes[Math.floorMod(hash, ArtPalettes.size)] }
    // 0..1, steady for this seed: nudges the circle so two titles in one palette still differ.
    val nudge = remember(hash) { Math.floorMod(hash / 7, 100) / 100f }
    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        drawRect(Brush.verticalGradient(0f to palette.top, 0.5f to palette.mid, 1f to palette.bottom))
        drawCircle(
            color = Color.White.copy(alpha = 0.10f),
            radius = w * 0.46f,
            center = Offset(w * (0.70f + 0.16f * nudge), h * (0.36f + 0.10f * nudge)),
        )
        drawRect(
            color = Color.Black.copy(alpha = 0.22f),
            topLeft = Offset(0f, h * 0.72f),
            size = Size(w, h * 0.28f),
        )
        val slab = Path().apply {
            moveTo(w * 0.74f, h * 0.72f)
            lineTo(w * 0.93f, h * 0.72f)
            lineTo(w * 0.80f, h)
            lineTo(w * 0.62f, h)
            close()
        }
        drawPath(slab, Color.White.copy(alpha = 0.05f))
    }
}
