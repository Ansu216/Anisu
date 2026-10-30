package com.ansu.anime.ui.player

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * and these controls drive the TV instead. From the sketch: the banner's colours flow over black
 * like water, a Cast badge and "Casting to <TV>" sit over it, then the title logo and "Ep 12 : name".
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
 * The casting screen's background: black with the banner's own colours flowing through it like water. Six colours are
 * read from the banner (see [bannerColors]) and each becomes a big soft light that drifts on its own slow looping path,
 * swelling and shrinking, with the lights adding up where they cross. No blur is used, so it looks the same on every
 * Android version. With no banner (or none readable) the lights take the app's accent colour.
 */
@Composable
private fun CastGlowBackdrop(backdropUrl: String?) {
    val context = LocalContext.current
    val sampled by produceState<List<Color>?>(null, backdropUrl) {
        value = backdropUrl?.let { bannerColors(context, it) }
    }
    val palette = sampled ?: List(FLOW_LIGHTS.size) { AnsuColors.Accent }
    val flow = rememberInfiniteTransition(label = "castFlow").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(FLOW_LOOP_MS, easing = LinearEasing), RepeatMode.Restart),
        label = "castFlowTime",
    )
    // Clipped, because the lights drift past the edges and must not bleed into the page around the video.
    Canvas(modifier = Modifier.fillMaxSize().clipToBounds()) {
        drawRect(Color.Black)
        // Read here, in the draw step, so the animation redraws only this canvas and never recomposes anything.
        val t = flow.value * TWO_PI
        val reach = maxOf(size.width, size.height)
        FLOW_LIGHTS.forEachIndexed { index, light ->
            val color = palette[index % palette.size]
            val center = Offset(
                size.width * (light.x + light.driftX * cos(t * light.speedX + light.phase)),
                size.height * (light.y + light.driftY * sin(t * light.speedY + light.phase * 1.7f)),
            )
            val radius = reach * (light.size + 0.07f * sin(t * light.speedSize + light.phase))
            drawCircle(
                brush = Brush.radialGradient(
                    0f to color.copy(alpha = 0.7f),
                    0.55f to color.copy(alpha = 0.26f),
                    1f to Color.Transparent,
                    center = center,
                    radius = radius,
                ),
                radius = radius,
                center = center,
                blendMode = BlendMode.Screen,
            )
        }
        // Darker towards the bottom, where the seek bar and buttons need to stay readable.
        drawRect(
            Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.15f),
                0.55f to Color.Black.copy(alpha = 0.3f),
                1f to Color.Black.copy(alpha = 0.78f),
            ),
        )
    }
}

private const val TWO_PI = 6.2831855f

/** One loop of the flow; every light's speed is a whole number of turns per loop, so the loop joins without a jump. */
private const val FLOW_LOOP_MS = 26_000

/** Where a light starts (fractions of the width and height), how far it drifts, how fast (turns per loop), its size. */
private class FlowLight(
    val x: Float, val y: Float,
    val driftX: Float, val driftY: Float,
    val speedX: Int, val speedY: Int, val speedSize: Int,
    val size: Float, val phase: Float,
)

/** Six lights, one per region of the banner (3 across, 2 down), each starting over its own region and then wandering. */
private val FLOW_LIGHTS = listOf(
    FlowLight(0.17f, 0.25f, 0.30f, 0.30f, 1, 2, 2, 0.50f, 0.0f),
    FlowLight(0.50f, 0.25f, 0.28f, 0.35f, 2, 1, 1, 0.46f, 1.3f),
    FlowLight(0.83f, 0.25f, 0.30f, 0.30f, 1, 3, 3, 0.50f, 2.6f),
    FlowLight(0.17f, 0.75f, 0.32f, 0.28f, 3, 1, 2, 0.48f, 3.9f),
    FlowLight(0.50f, 0.75f, 0.28f, 0.32f, 1, 2, 1, 0.52f, 5.1f),
    FlowLight(0.83f, 0.75f, 0.30f, 0.30f, 2, 1, 3, 0.48f, 0.7f),
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
        hsv[2] = (hsv[2] * 1.25f).coerceIn(0.4f, 0.95f)
        Color(android.graphics.Color.HSVToColor(hsv))
    }
}

private const val BANNER_W = 48
private const val BANNER_H = 28
