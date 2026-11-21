package com.ansu.anime.core.util

import java.util.Locale
import kotlin.math.roundToInt

/** Formats seconds as "1:04:12" or "12:04" depending on length. */
fun formatDuration(totalSeconds: Long): String {
    val safe = totalSeconds.coerceAtLeast(0)
    val hours = safe / 3600
    val minutes = (safe % 3600) / 60
    val seconds = safe % 60
    return if (hours > 0) {
        String.format(Locale.US, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.US, "%d:%02d", minutes, seconds)
    }
}

/** 0f..1f progress fraction for a continue-watching card, clamped. */
fun progressFraction(positionSeconds: Long, durationSeconds: Long): Float {
    if (durationSeconds <= 0) return 0f
    return (positionSeconds.toFloat() / durationSeconds.toFloat()).coerceIn(0f, 1f)
}

fun Float.formatEpisodeNumber(): String =
    if (this == roundToInt().toFloat()) roundToInt().toString() else this.toString()
