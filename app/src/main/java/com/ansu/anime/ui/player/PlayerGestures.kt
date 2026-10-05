package com.ansu.anime.ui.player

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioManager
import android.provider.Settings
import android.view.WindowManager
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** What the video-surface gestures may do, straight from Settings > Player and streaming. */
data class PlayerGestureConfig(
    val doubleTapSeek: Boolean = true,
    val brightness: Boolean = true,
    val volume: Boolean = true,
    val skipSeconds: Int = 15,
    /** False while the controls are locked: only a plain tap works then. */
    val enabled: Boolean = true,
)

private fun Context.activityOrNull(): Activity? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

/**
 * The transparent layer over the video: tap toggles the controls, double tap on the left/right side skips,
 * and a vertical swipe on the left half changes brightness, on the right half volume.
 */
@Composable
fun PlayerGestureLayer(
    config: PlayerGestureConfig,
    onTap: () -> Unit,
    onSeekBy: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val window = remember(context) { context.activityOrNull()?.window }
    val audio = remember(context) { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var hud by remember { mutableStateOf<String?>(null) }
    var hudTick by remember { mutableStateOf(0) }

    fun showHud(text: String) {
        hud = text
        hudTick++
    }
    LaunchedEffect(hudTick) {
        if (hud != null) {
            delay(700)
            hud = null
        }
    }
    // Brightness is only overridden while the player is open.
    DisposableEffect(window) {
        onDispose {
            window?.let {
                val lp = it.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                it.attributes = lp
            }
        }
    }

    var brightnessLevel = 0f
    var volumeLevel = 0f

    Box(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(config.enabled, config.doubleTapSeek, config.skipSeconds) {
                    detectTapGestures(
                        onTap = { onTap() },
                        onDoubleTap = if (config.enabled && config.doubleTapSeek) {
                            { offset ->
                                val third = size.width / 3f
                                when {
                                    offset.x < third -> {
                                        onSeekBy(-config.skipSeconds * 1000L)
                                        showHud("-${config.skipSeconds}s")
                                    }
                                    offset.x > third * 2 -> {
                                        onSeekBy(config.skipSeconds * 1000L)
                                        showHud("+${config.skipSeconds}s")
                                    }
                                    else -> onTap()
                                }
                            }
                        } else {
                            null
                        },
                    )
                }
                .pointerInput(config.enabled, config.brightness, config.volume) {
                    if (!config.enabled || (!config.brightness && !config.volume)) return@pointerInput
                    var leftSide = true
                    detectVerticalDragGestures(
                        onDragStart = { start ->
                            leftSide = start.x < size.width / 2f
                            if (leftSide) {
                                val current = window?.attributes?.screenBrightness ?: -1f
                                brightnessLevel = if (current >= 0f) current else {
                                    runCatching { Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS) / 255f }
                                        .getOrDefault(0.5f)
                                }
                            } else {
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                volumeLevel = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
                            }
                        },
                        onVerticalDrag = { change, dragAmount ->
                            // A full-height swipe moves the level by roughly its whole range.
                            val delta = -dragAmount / size.height.coerceAtLeast(1)
                            if (leftSide && config.brightness) {
                                change.consume()
                                brightnessLevel = (brightnessLevel + delta).coerceIn(0.01f, 1f)
                                window?.let {
                                    val lp = it.attributes
                                    lp.screenBrightness = brightnessLevel
                                    it.attributes = lp
                                }
                                showHud("Brightness ${(brightnessLevel * 100).roundToInt()}%")
                            } else if (!leftSide && config.volume) {
                                change.consume()
                                volumeLevel = (volumeLevel + delta).coerceIn(0f, 1f)
                                val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                                audio.setStreamVolume(AudioManager.STREAM_MUSIC, (volumeLevel * max).roundToInt(), 0)
                                showHud("Volume ${(volumeLevel * 100).roundToInt()}%")
                            }
                        },
                    )
                },
        )
        hud?.let {
            Text(
                text = it,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 28.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}
