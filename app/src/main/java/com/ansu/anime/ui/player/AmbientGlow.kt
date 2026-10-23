package com.ansu.anime.ui.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * "Ambient mode" for the portrait player, like YouTube's: the area under the video glows in the colours of the
 * frames being played. The colours are read from the video itself a few times a second (see [rememberAmbientColors])
 * and painted as soft light spreading down from the video's bottom edge (see [AmbientGlowBackdrop]).
 */
private const val ZONES = 5
private const val GRID_W = 64
private const val GRID_H = 36
private const val SAMPLE_INTERVAL_MS = 350L
private const val MAX_FAILURES = 6

private val NoGlow: List<Color> = List(ZONES) { Color.Transparent }
private val mainHandler = Handler(Looper.getMainLooper())

/**
 * The glow colours, left to right, read from the lower part of the video frame. While [playing] it samples every
 * [SAMPLE_INTERVAL_MS]; paused, it samples once so the glow matches the frame on screen. A frame is copied from
 * the video surface at 64x36 pixels, which costs next to nothing. If the system will not hand frames over (a
 * protected stream, say) the glow simply stays off.
 */
@Composable
fun rememberAmbientColors(playerView: PlayerView?, enabled: Boolean, playing: Boolean, episodeKey: Any?): State<List<Color>> {
    val colors = remember { mutableStateOf(NoGlow) }
    LaunchedEffect(playerView, enabled, playing, episodeKey) {
        if (!enabled || playerView == null) {
            colors.value = NoGlow
            return@LaunchedEffect
        }
        // Not recycled afterwards: a copy still in flight when this is cancelled would be writing into it.
        val bitmap = Bitmap.createBitmap(GRID_W, GRID_H, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(GRID_W * GRID_H)
        var failures = 0
        var smoothed: FloatArray? = null
        run {
            while (isActive) {
                val surface = playerView.videoSurfaceView as? SurfaceView
                val copied = surface != null && surface.width > 0 && surface.holder.surface.isValid && copyFrame(surface, bitmap)
                if (copied) {
                    failures = 0
                    bitmap.getPixels(pixels, 0, GRID_W, 0, 0, GRID_W, GRID_H)
                    val target = zoneColors(pixels)
                    // Blend with the previous reading so a cut or a flash does not make the glow flicker.
                    val previous = smoothed
                    val blended = if (previous == null) target else FloatArray(target.size) { previous[it] * 0.45f + target[it] * 0.55f }
                    smoothed = blended
                    colors.value = List(ZONES) { zone ->
                        Color(red = blended[zone * 3], green = blended[zone * 3 + 1], blue = blended[zone * 3 + 2])
                    }
                } else if (++failures >= MAX_FAILURES) {
                    break
                }
                if (!playing) break
                delay(SAMPLE_INTERVAL_MS)
            }
        }
    }
    return colors
}

/** Asks the video surface for its current frame, scaled into [into]. False when no frame could be copied. */
private suspend fun copyFrame(view: SurfaceView, into: Bitmap): Boolean = suspendCancellableCoroutine { continuation ->
    try {
        PixelCopy.request(view, into, { result -> if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS) }, mainHandler)
    } catch (e: IllegalArgumentException) {
        // The surface went away between the check and the request.
        if (continuation.isActive) continuation.resume(false)
    }
}

/**
 * Five colours (r, g, b as floats, 15 values) for the zones left to right: the average of the lower 45% of the
 * frame in each zone, a little of the whole frame mixed in so neighbours belong together, then made livelier
 * (more saturated, brighter in dim scenes), since the plain average of a picture is grey and muddy.
 */
private fun zoneColors(pixels: IntArray): FloatArray {
    val out = FloatArray(ZONES * 3)
    var allR = 0f; var allG = 0f; var allB = 0f
    for (p in pixels) {
        allR += ((p shr 16) and 0xFF); allG += ((p shr 8) and 0xFF); allB += (p and 0xFF)
    }
    val total = pixels.size.toFloat()
    allR /= total; allG /= total; allB /= total
    val firstRow = (GRID_H * 0.55f).toInt()
    val hsv = FloatArray(3)
    for (zone in 0 until ZONES) {
        val x0 = zone * GRID_W / ZONES
        val x1 = (zone + 1) * GRID_W / ZONES
        var r = 0f; var g = 0f; var b = 0f
        var n = 0
        for (y in firstRow until GRID_H) {
            for (x in x0 until x1) {
                val p = pixels[y * GRID_W + x]
                r += ((p shr 16) and 0xFF); g += ((p shr 8) and 0xFF); b += (p and 0xFF)
                n++
            }
        }
        if (n == 0) n = 1
        r = r / n * 0.7f + allR * 0.3f
        g = g / n * 0.7f + allG * 0.3f
        b = b / n * 0.7f + allB * 0.3f
        android.graphics.Color.RGBToHSV(r.toInt().coerceIn(0, 255), g.toInt().coerceIn(0, 255), b.toInt().coerceIn(0, 255), hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.35f).coerceAtMost(0.95f)
        val argb = android.graphics.Color.HSVToColor(hsv)
        out[zone * 3] = ((argb shr 16) and 0xFF) / 255f
        out[zone * 3 + 1] = ((argb shr 8) and 0xFF) / 255f
        out[zone * 3 + 2] = (argb and 0xFF) / 255f
    }
    return out
}

/**
 * The glow itself: one soft light per zone centred on the video's bottom edge, fading out below it. It sits
 * behind the episode info, so the text and list draw on top. [topInset] is the space above the video (the status
 * bar); the video is 16:9 and as wide as the screen. Colours change gradually rather than jumping.
 */
@Composable
fun AmbientGlowBackdrop(colors: State<List<Color>>, topInset: Int, modifier: Modifier = Modifier) {
    val fade = tween<Color>(durationMillis = 900, easing = LinearEasing)
    val animated = List(ZONES) { zone ->
        animateColorAsState(colors.value.getOrElse(zone) { Color.Transparent }, fade, label = "ambient$zone")
    }
    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val videoBottom = topInset + width * 9f / 16f
        clipRect(top = videoBottom) {
            animated.forEachIndexed { zone, state ->
                val color = state.value
                if (color.alpha > 0.01f) {
                    val center = Offset(width * (zone + 0.5f) / ZONES, videoBottom)
                    val radius = width * 0.8f
                    drawCircle(
                        brush = Brush.radialGradient(
                            0f to color.copy(alpha = 0.55f),
                            0.5f to color.copy(alpha = 0.2f),
                            1f to Color.Transparent,
                            center = center,
                            radius = radius,
                        ),
                        radius = radius,
                        center = center,
                    )
                }
            }
        }
    }
}
