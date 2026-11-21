package com.ansu.anime.ui.player

import android.graphics.Bitmap
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.TextureView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * "Ambient mode" for the portrait player, like YouTube's: the area under the video glows in the colours of the
 * frames being played.
 *
 * The video is read a few times a second at 64x36 pixels (see [rememberAmbientColors]) and boiled down to a small
 * grid of colours. [AmbientGlowBackdrop] eases a drawn copy of that grid towards each new reading on every screen
 * frame (so the light drifts instead of stepping), stretches it into a soft picture under the video and blurs it,
 * then fades it into the black page.
 */
private const val COLS = 16
private const val ROWS = 6
private const val CELLS = COLS * ROWS
private const val GRID_W = 64
private const val GRID_H = 36
private const val SAMPLE_INTERVAL_MS = 250L
private const val MAX_FAILURES = 6

/** A row or column darker than this (0..255 brightness) is taken for a black bar around the picture. */
private const val BAR_LUMA = 10f

/** How long the glow takes to cover about two thirds of the way to a new reading. */
private const val SETTLE_SECONDS = 0.55f

/** The glow is redrawn at most this often while it moves (the blur is the costly part). */
private const val FRAME_STEP_SECONDS = 1f / 30f

private val mainHandler = Handler(Looper.getMainLooper())

/** The latest colours read from the video, as a [COLS] x [ROWS] grid; [AmbientGlowBackdrop] eases towards them. */
@Stable
class AmbientColors internal constructor() {
    internal val target = FloatArray(CELLS * 3)

    /** Bumped after every reading, so the backdrop knows when to start moving again. */
    internal var version by mutableStateOf(0)

    /** False until the first frame has been read (or when the system will not hand frames over): no glow. */
    var hasData by mutableStateOf(false)
        internal set
}

/** Where the picture sits inside the frame once the black bars (pillarbox or letterbox) are left out. */
private class PictureBounds {
    var left = GRID_W
    var right = -1
    var top = GRID_H
    var bottom = -1
}

/**
 * Reads the video's colours: every [SAMPLE_INTERVAL_MS] while [playing]; paused, once, so the glow matches the frame
 * on screen. A frame is copied from the video surface at 64x36 pixels, which costs next to nothing. If the system
 * will not hand frames over (a protected stream, say) the glow simply stays off.
 */
@Composable
fun rememberAmbientColors(playerView: PlayerView?, enabled: Boolean, playing: Boolean, episodeKey: Any?): AmbientColors {
    val colors = remember { AmbientColors() }
    // Kept for the whole episode (not per effect run), so pausing and resuming does not forget where the bars are.
    val bounds = remember(episodeKey) { PictureBounds() }
    LaunchedEffect(playerView, enabled, playing, episodeKey) {
        if (!enabled || playerView == null) {
            colors.hasData = false
            return@LaunchedEffect
        }
        // Not recycled afterwards: a copy still in flight when this is cancelled would be writing into it.
        val bitmap = Bitmap.createBitmap(GRID_W, GRID_H, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(GRID_W * GRID_H)
        var failures = 0
        while (isActive) {
            val copied = copyFrame(playerView, bitmap)
            if (copied) {
                failures = 0
                bitmap.getPixels(pixels, 0, GRID_W, 0, 0, GRID_W, GRID_H)
                writeTarget(pixels, bounds, colors.target)
                colors.version++
                colors.hasData = true
            } else if (++failures >= MAX_FAILURES) {
                break
            }
            if (!playing) break
            delay(SAMPLE_INTERVAL_MS)
        }
    }
    return colors
}

/** Asks the video surface for its current frame, scaled into [into]. False when no frame could be copied. */
private suspend fun copyFrame(playerView: PlayerView, into: Bitmap): Boolean {
    val view = playerView.videoSurfaceView
    if (view is TextureView) return view.isAvailable && view.getBitmap(into) != null
    val surface = view as? SurfaceView ?: return false
    if (surface.width <= 0 || !surface.holder.surface.isValid) return false
    return suspendCancellableCoroutine { continuation ->
        try {
            PixelCopy.request(surface, into, { result -> if (continuation.isActive) continuation.resume(result == PixelCopy.SUCCESS) }, mainHandler)
        } catch (e: IllegalArgumentException) {
            // The surface went away between the check and the request.
            if (continuation.isActive) continuation.resume(false)
        }
    }
}

/**
 * Turns a 64x36 frame into the colour grid: the picture's lower 60% (black bars left out), split into [COLS] x
 * [ROWS] cells, each the average of its pixels with a little of the whole picture mixed in so neighbours belong
 * together, then made livelier (more saturated, brighter in dim scenes), since a plain average is grey and muddy.
 */
private fun writeTarget(pixels: IntArray, bounds: PictureBounds, out: FloatArray) {
    val colLuma = FloatArray(GRID_W)
    val rowLuma = FloatArray(GRID_H)
    for (y in 0 until GRID_H) {
        for (x in 0 until GRID_W) {
            val p = pixels[y * GRID_W + x]
            val luma = 0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
            colLuma[x] += luma
            rowLuma[y] += luma
        }
    }
    var l = -1; var r = -1
    for (x in 0 until GRID_W) if (colLuma[x] / GRID_H > BAR_LUMA) { if (l < 0) l = x; r = x }
    var t = -1; var b = -1
    for (y in 0 until GRID_H) if (rowLuma[y] / GRID_W > BAR_LUMA) { if (t < 0) t = y; b = y }
    // The box only ever grows: a dark scene must not make the picture look smaller than the bars say it is.
    if (l >= 0) { bounds.left = min(bounds.left, l); bounds.right = max(bounds.right, r) }
    if (t >= 0) { bounds.top = min(bounds.top, t); bounds.bottom = max(bounds.bottom, b) }
    val x0 = if (bounds.right >= bounds.left) bounds.left else 0
    val x1 = if (bounds.right >= bounds.left) bounds.right else GRID_W - 1
    val y0 = if (bounds.bottom >= bounds.top) bounds.top else 0
    val y1 = if (bounds.bottom >= bounds.top) bounds.bottom else GRID_H - 1
    val gx = x1 - x0 + 1
    val gy = y1 - y0 + 1
    val startY = min(y0 + (gy * 0.4f).toInt(), y1)
    val sh = y1 - startY + 1

    var allR = 0f; var allG = 0f; var allB = 0f
    var allN = 0
    for (y in y0..y1) for (x in x0..x1) {
        val p = pixels[y * GRID_W + x]
        allR += (p shr 16) and 0xFF; allG += (p shr 8) and 0xFF; allB += p and 0xFF
        allN++
    }
    if (allN == 0) allN = 1
    allR /= allN; allG /= allN; allB /= allN

    val hsv = FloatArray(3)
    for (cy in 0 until ROWS) {
        val ya = min(startY + sh * cy / ROWS, y1)
        val yb = max(ya + 1, startY + sh * (cy + 1) / ROWS)
        for (cx in 0 until COLS) {
            val xa = min(x0 + gx * cx / COLS, x1)
            val xb = max(xa + 1, x0 + gx * (cx + 1) / COLS)
            var cr = 0f; var cg = 0f; var cb = 0f
            var n = 0
            for (y in ya until min(yb, GRID_H)) for (x in xa until min(xb, GRID_W)) {
                val p = pixels[y * GRID_W + x]
                cr += (p shr 16) and 0xFF; cg += (p shr 8) and 0xFF; cb += p and 0xFF
                n++
            }
            if (n == 0) n = 1
            cr = cr / n * 0.85f + allR * 0.15f
            cg = cg / n * 0.85f + allG * 0.15f
            cb = cb / n * 0.85f + allB * 0.15f
            android.graphics.Color.RGBToHSV(cr.toInt().coerceIn(0, 255), cg.toInt().coerceIn(0, 255), cb.toInt().coerceIn(0, 255), hsv)
            hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
            hsv[2] = (hsv[2] * 1.3f).coerceAtMost(0.95f)
            val argb = android.graphics.Color.HSVToColor(hsv)
            val i = (cy * COLS + cx) * 3
            out[i] = ((argb shr 16) and 0xFF) / 255f
            out[i + 1] = ((argb shr 8) and 0xFF) / 255f
            out[i + 2] = (argb and 0xFF) / 255f
        }
    }
}

/**
 * The glow itself, behind the episode info (so its text and list draw on top). [topInset] is the space above the
 * video (the status bar); the video is 16:9 and as wide as the screen. The colours glide on every frame towards the
 * latest reading and fade in and out as a whole, so nothing ever jumps.
 */
@Composable
fun AmbientGlowBackdrop(colors: AmbientColors, topInset: Int, modifier: Modifier = Modifier) {
    val bitmap = remember { Bitmap.createBitmap(COLS, ROWS, Bitmap.Config.ARGB_8888) }
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    val pixels = remember { IntArray(CELLS) }
    val shown = remember { FloatArray(CELLS * 3) }
    var frame by remember { mutableStateOf(0) }
    val visibility = remember { Animatable(0f) }

    LaunchedEffect(colors) {
        snapshotFlow { colors.hasData }.collectLatest { visibility.animateTo(if (it) 1f else 0f, tween(700)) }
    }

    LaunchedEffect(colors) {
        var seeded = false
        var lastNanos = 0L
        var carry = 0f
        while (isActive) {
            val now = withFrameNanos { it }
            val dt = if (lastNanos == 0L) 0f else ((now - lastNanos) / 1_000_000_000f).coerceAtMost(0.1f)
            lastNanos = now
            if (!colors.hasData) {
                // Nothing to show: sleep until the first reading arrives (the fade-out needs no stepping).
                snapshotFlow { colors.hasData }.first { it }
                lastNanos = 0L
                seeded = false
                continue
            }
            if (!seeded) {
                System.arraycopy(colors.target, 0, shown, 0, shown.size)
                seeded = true
                carry = FRAME_STEP_SECONDS
            }
            carry += dt
            if (carry < FRAME_STEP_SECONDS) continue
            val step = carry
            carry = 0f
            val k = 1f - exp(-step / SETTLE_SECONDS)
            var moved = 0f
            for (i in shown.indices) {
                val d = colors.target[i] - shown[i]
                shown[i] += d * k
                moved = max(moved, abs(d))
            }
            for (cell in 0 until CELLS) {
                val r = (shown[cell * 3] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val g = (shown[cell * 3 + 1] * 255f + 0.5f).toInt().coerceIn(0, 255)
                val b = (shown[cell * 3 + 2] * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels[cell] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
            }
            bitmap.setPixels(pixels, 0, COLS, 0, 0, COLS, ROWS)
            frame++
            if (moved < 0.003f) {
                // Settled on the reading: rest until the next one instead of redrawing an unchanged picture.
                val seen = colors.version
                snapshotFlow { colors.version }.first { it != seen }
                lastNanos = 0L
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val density = LocalDensity.current
        val widthPx = constraints.maxWidth.toFloat()
        val videoBottom = topInset + widthPx * 9f / 16f
        // The picture starts behind the video (hidden by it) so there is never a seam, and runs about one screen
        // width below it, where it has faded to black.
        val regionTop = max(0f, videoBottom - widthPx * 0.3f)
        val regionHeight = videoBottom - regionTop + widthPx * 0.95f
        val blurPx = with(density) { 26.dp.toPx() }
        Canvas(
            modifier = Modifier
                .offset { IntOffset(0, regionTop.roundToInt()) }
                .width(maxWidth)
                .height(with(density) { regionHeight.toDp() })
                .graphicsLayer {
                    renderEffect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        BlurEffect(blurPx, blurPx, TileMode.Clamp)
                    } else {
                        null
                    }
                },
        ) {
            val redraw = frame // read so the picture is redrawn whenever a new frame of colours is ready
            val glow = visibility.value
            if (glow <= 0.01f || redraw < 0) return@Canvas
            val localVideoBottom = videoBottom - regionTop
            clipRect(top = localVideoBottom) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(COLS, ROWS),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                    alpha = 0.85f * glow,
                    filterQuality = FilterQuality.Medium,
                )
                // Fades the picture into the black page: strong under the video, gone well before the end.
                drawRect(
                    brush = Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.0f),
                        0.45f to Color.Black.copy(alpha = 0.40f),
                        1f to Color.Black,
                        startY = localVideoBottom,
                        endY = size.height,
                    ),
                    topLeft = androidx.compose.ui.geometry.Offset(0f, localVideoBottom),
                    size = androidx.compose.ui.geometry.Size(size.width, size.height - localVideoBottom),
                )
            }
        }
    }
}
