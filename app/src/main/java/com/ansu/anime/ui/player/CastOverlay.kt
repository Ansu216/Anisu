package com.ansu.anime.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cast
import androidx.compose.material.icons.rounded.CastConnected
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ansu.anime.cast.CastUiState
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.formatDuration
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** "Ep 12 : Episode name" for the casting screen; just "Ep 12" when the episode has no real name, null for a movie. */
internal fun castEpisodeLine(episode: SEpisode?, info: PausedInfo): String? {
    if (episode == null || info.episodeLabel == null) return null
    val number = episode.episodeNumber.formatEpisodeNumber()
    return info.episodeName?.let { "Ep $number : $it" } ?: "Ep $number"
}

/**
 * Shown in place of the player controls while the video plays on a Chromecast: the phone's own player stays paused,
 * and these controls drive the TV instead. From the sketch: the banner's colours flow with black
 * patches like water, a Cast badge and "Casting to <TV>" sit over it, then the title logo and "Ep 12 : name".
 * [compact] is the small 16:9 box of the portrait layout.
 */
@Composable
fun CastOverlay(
    state: CastUiState,
    title: String,
    episodeLine: String?,
    logoUrl: String?,
    backdropUrl: String?,
    seekSeconds: Int,
    onBack: () -> Unit,
    onDevice: () -> Unit,
    onPlayPause: () -> Unit,
    onSeekBy: (Long) -> Unit,
    onSeekTo: (Long) -> Unit,
    onDisconnect: () -> Unit,
    onSubtitle: (Long?) -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        CastGlowBackdrop(backdropUrl = backdropUrl)

        Row(
            modifier = Modifier.align(Alignment.TopStart).fillMaxWidth().padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Box(modifier = Modifier.weight(1f))
            IconButton(onClick = onDevice) {
                Icon(Icons.Rounded.CastConnected, contentDescription = "Cast device", tint = AnsuColors.Accent)
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = if (compact) (-18).dp else (-26).dp)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (!compact) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(15.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(15.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Cast, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(28.dp))
                }
            }
            Text(
                "Casting to ${state.deviceName ?: "your TV"}",
                color = Color.White,
                fontSize = if (compact) 13.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = if (compact) 0.dp else 10.dp),
            )
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            if (logoUrl != null) {
                AsyncImage(
                    model = logoUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .heightIn(max = if (compact) 34.dp else 64.dp)
                        .widthIn(max = if (compact) 170.dp else 300.dp),
                )
            } else {
                Text(
                    title,
                    color = Color.White,
                    fontSize = if (compact) 15.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (episodeLine != null) {
                Text(
                    episodeLine,
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = if (compact) 11.sp else 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp),
                )
            }
        }

        Column(modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(horizontal = 16.dp)) {
            var dragging by remember { mutableStateOf<Float?>(null) }
            val duration = state.durationMs.coerceAtLeast(1L)
            val fraction = (state.positionMs.toFloat() / duration).coerceIn(0f, 1f)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(((dragging ?: fraction) * duration).toLong() / 1000L),
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Slider(
                    value = dragging ?: fraction,
                    onValueChange = { dragging = it },
                    onValueChangeFinished = {
                        dragging?.let { onSeekTo((it * duration).toLong()) }
                        dragging = null
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = AnsuColors.Accent,
                        activeTrackColor = AnsuColors.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                    ),
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                Text(formatDuration(state.durationMs / 1000L), color = Color.White, fontSize = 12.sp)
            }
            // Subtitles on the left, Stop casting on the right and the transport buttons between them, so the
            // banner and title above keep the room.
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (state.subtitleTracks.isNotEmpty()) {
                        var menuOpen by remember { mutableStateOf(false) }
                        Box {
                            TextButton(onClick = { menuOpen = true }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                                Icon(Icons.Rounded.ClosedCaption, contentDescription = "Subtitles", tint = Color.White, modifier = Modifier.size(if (compact) 22.dp else 18.dp))
                                // The small portrait box has no room for the word, only the icon.
                                if (!compact) Text("  Subtitles", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text(if (state.activeSubtitleId == null) "Off  \u2713" else "Off") },
                                    onClick = {
                                        onSubtitle(null)
                                        menuOpen = false
                                    },
                                )
                                state.subtitleTracks.forEach { track ->
                                    DropdownMenuItem(
                                        text = { Text(if (track.id == state.activeSubtitleId) "${track.label}  \u2713" else track.label) },
                                        onClick = {
                                            onSubtitle(track.id)
                                            menuOpen = false
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(if (compact) 8.dp else 20.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { onSeekBy(-seekSeconds * 1000L) }) {
                        Icon(Icons.Rounded.FastRewind, contentDescription = "Back $seekSeconds seconds", tint = Color.White)
                    }
                    IconButton(onClick = onPlayPause, modifier = Modifier.size(if (compact) 44.dp else 52.dp)) {
                        if (state.buffering) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                        } else {
                            Icon(
                                if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                contentDescription = if (state.playing) "Pause" else "Play",
                                tint = Color.White,
                                modifier = Modifier.size(if (compact) 32.dp else 40.dp),
                            )
                        }
                    }
                    IconButton(onClick = { onSeekBy(seekSeconds * 1000L) }) {
                        Icon(Icons.Rounded.FastForward, contentDescription = "Forward $seekSeconds seconds", tint = Color.White)
                    }
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                    TextButton(onClick = onDisconnect, contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text("Stop casting", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, maxLines = 1)
                    }
                }
            }
        }
    }
}

/**
 * The casting screen's background, in the style of a music player's now-playing screen: a soft mesh of the banner's
 * own colours spread over the whole screen, with black patches wandering through it, so colour and black keep flowing
 * into each other like water. Six colours are read from the banner (see [bannerColors]); each is a big soft light and
 * three black patches move over them.
 *
 * Nothing repeats, so no pattern can be picked out. Every light and patch moves on two sine waves whose speeds are
 * not whole multiples of each other (so the path never closes), its shape is an ellipse that keeps turning and
 * changing proportions, and every cast session starts from a different random phase, speed and shape. Time just runs
 * on, with no loop to restart. No blur is used, so it looks the same on every Android version. The colours cross-fade
 * when they change, and with no banner (or none readable) the lights take the app's accent colour.
 */
@Composable
private fun CastGlowBackdrop(backdropUrl: String?) {
    val context = LocalContext.current
    val sampled by produceState<List<Color>?>(null, backdropUrl) {
        value = backdropUrl?.let { bannerColors(context, it) }
    }
    val palette = sampled ?: List(FLOW_LIGHTS.size) { AnsuColors.Accent }
    // Each colour eases to its new value, so the banner's colours fade in over the accent, and a new picture fades too.
    val colors = palette.mapIndexed { index, color ->
        animateColorAsState(color, tween<Color>(COLOR_FADE_MS, easing = LinearEasing), label = "castFlowColor$index")
    }
    // A fresh random start for every light and patch each time the casting screen appears.
    val seeds = remember {
        List(FLOW_LIGHTS.size + FLOW_DARKS.size) {
            FlowSeed(
                phase = Random.nextFloat() * TWO_PI,
                speed = 0.75f + Random.nextFloat() * 0.5f,
                stretch = 0.7f + Random.nextFloat() * 0.8f,
            )
        }
    }
    // Seconds since the screen appeared; it only ever grows. Read in the draw step below, so each frame redraws just
    // this canvas and recomposes nothing.
    var seconds by remember { mutableStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) seconds += ((now - last) / 1_000_000_000f).coerceAtMost(0.1f)
                last = now
            }
        }
    }
    // Clipped, because the lights drift past the edges and must not bleed into the page around the video.
    Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
        drawRect(Color.Black)
        val t = seconds
        val reach = maxOf(size.width, size.height)
        FLOW_LIGHTS.forEachIndexed { index, light ->
            val color = colors[index % colors.size].value
            val seed = seeds[index]
            val center = light.centerAt(t, seed, size.width, size.height)
            val radius = reach * light.sizeAt(t, seed)
            withTransform({
                rotate(light.turnAt(t, seed), pivot = center)
                scale(light.stretchAt(t, seed), 1f / light.stretchAt(t, seed), pivot = center)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to color.copy(alpha = 0.6f),
                        0.6f to color.copy(alpha = 0.28f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                    blendMode = BlendMode.Screen,
                )
            }
        }
        // The black patches, drawn over the colour: where one passes, the colour sinks into black and comes back after.
        FLOW_DARKS.forEachIndexed { index, dark ->
            val seed = seeds[FLOW_LIGHTS.size + index]
            val center = dark.centerAt(t, seed, size.width, size.height)
            val radius = reach * dark.sizeAt(t, seed)
            withTransform({
                rotate(dark.turnAt(t, seed), pivot = center)
                scale(dark.stretchAt(t, seed), 1f / dark.stretchAt(t, seed), pivot = center)
            }) {
                drawCircle(
                    brush = Brush.radialGradient(
                        0f to Color.Black.copy(alpha = 0.88f),
                        0.6f to Color.Black.copy(alpha = 0.4f),
                        1f to Color.Transparent,
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                    center = center,
                )
            }
        }
        // A little darker at the bottom, where the seek bar and buttons need to stay readable.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.6f to Color.Black.copy(alpha = 0.12f),
                1f to Color.Black.copy(alpha = 0.5f),
            ),
        )
    }
}

private const val TWO_PI = 6.2831855f

/** How long a colour takes to ease into its new value. */
private const val COLOR_FADE_MS = 1_000

/** What is random about one light or patch in one casting session: where it is in its movement, how fast, how stretched. */
private class FlowSeed(val phase: Float, val speed: Float, val stretch: Float)

/**
 * One light or patch: it wanders around its home ([x], [y], fractions of the width and height) by up to [driftX] and
 * [driftY]. [rate] is its base speed in radians per second. Each movement is two sine waves, the second at an
 * irrational multiple of the first's speed, so the path never closes and never repeats.
 */
private class FlowLight(
    val x: Float, val y: Float,
    val driftX: Float, val driftY: Float,
    val rate: Float,
    val size: Float,
) {
    fun centerAt(t: Float, seed: FlowSeed, width: Float, height: Float): Offset {
        val a = t * rate * seed.speed
        val dx = 0.62f * sin(a + seed.phase) + 0.38f * sin(a * 1.618f + seed.phase * 2.3f)
        val dy = 0.62f * cos(a * 1.272f + seed.phase * 1.7f) + 0.38f * sin(a * 2.236f + seed.phase * 0.6f)
        return Offset(width * (x + driftX * dx), height * (y + driftY * dy))
    }

    fun sizeAt(t: Float, seed: FlowSeed): Float = size * (1f + 0.12f * sin(t * rate * seed.speed * 1.414f + seed.phase * 1.3f))

    /** How far the ellipse is rotated, in degrees; it keeps turning, slowly and for ever. */
    fun turnAt(t: Float, seed: FlowSeed): Float = Math.toDegrees((t * rate * seed.speed * 0.37f + seed.phase).toDouble()).toFloat()

    /** Width over height of the ellipse, always changing between about 0.55 and 1.8 (its height is the inverse). */
    fun stretchAt(t: Float, seed: FlowSeed): Float = seed.stretch * (1f + 0.25f * sin(t * rate * seed.speed * 0.83f + seed.phase * 2.9f))
}

/** Six colour lights, one per region of the banner (3 across, 2 down), each starting near its own region and then wandering. */
private val FLOW_LIGHTS = listOf(
    FlowLight(0.17f, 0.25f, 0.34f, 0.32f, 0.27f, 0.70f),
    FlowLight(0.50f, 0.25f, 0.30f, 0.36f, 0.33f, 0.66f),
    FlowLight(0.83f, 0.25f, 0.34f, 0.32f, 0.24f, 0.70f),
    FlowLight(0.17f, 0.75f, 0.36f, 0.30f, 0.31f, 0.68f),
    FlowLight(0.50f, 0.75f, 0.30f, 0.34f, 0.22f, 0.72f),
    FlowLight(0.83f, 0.75f, 0.34f, 0.32f, 0.29f, 0.68f),
)

/** Three black patches that wander over the colour faster than the lights move. */
private val FLOW_DARKS = listOf(
    FlowLight(0.15f, 0.85f, 0.50f, 0.42f, 0.52f, 0.50f),
    FlowLight(0.85f, 0.80f, 0.50f, 0.46f, 0.61f, 0.46f),
    FlowLight(0.55f, 0.15f, 0.55f, 0.38f, 0.47f, 0.42f),
)

/**
 * Six colours from the banner, one for each region (3 across, 2 down), in the order of [FLOW_LIGHTS]; null when the
 * picture cannot be loaded. Each is the average of its region made livelier (more saturated, and never too dark to
 * glow), since the plain average of a picture is muddy.
 */
private suspend fun bannerColors(context: Context, url: String): List<Color>? = withContext(Dispatchers.Default) {
    val request = ImageRequest.Builder(context).data(url).size(BANNER_W, BANNER_H).allowHardware(false).build()
    val drawable = (context.imageLoader.execute(request) as? SuccessResult)?.drawable ?: return@withContext null
    val pixels = IntArray(BANNER_W * BANNER_H)
    runCatching { drawable.toBitmap(BANNER_W, BANNER_H, Bitmap.Config.ARGB_8888).getPixels(pixels, 0, BANNER_W, 0, 0, BANNER_W, BANNER_H) }
        .onFailure { return@withContext null }
    val hsv = FloatArray(3)
    List(6) { region ->
        val col = region % 3
        val row = region / 3
        var r = 0L; var g = 0L; var b = 0L; var n = 0
        for (y in row * BANNER_H / 2 until (row + 1) * BANNER_H / 2) {
            for (x in col * BANNER_W / 3 until (col + 1) * BANNER_W / 3) {
                val p = pixels[y * BANNER_W + x]
                r += (p shr 16) and 0xFF; g += (p shr 8) and 0xFF; b += p and 0xFF
                n++
            }
        }
        if (n == 0) n = 1
        android.graphics.Color.RGBToHSV((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsv)
        hsv[1] = (hsv[1] * 1.3f).coerceAtMost(1f)
        hsv[2] = (hsv[2] * 1.25f).coerceIn(0.4f, 0.8f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

private const val BANNER_W = 48
private const val BANNER_H = 28
