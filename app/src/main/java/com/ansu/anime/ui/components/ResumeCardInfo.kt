package com.ansu.anime.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.ansu.anime.data.db.ContinueWatchingEntity
import kotlinx.coroutines.delay

/** An episode this close to its end counts as finished: the card moves on to what comes next. */
const val FINISHED_WITHIN_SECONDS = 30L

/**
 * What a Continue Watching card shows besides the show's picture.
 * A card with no info (null) looks like it always did.
 */
sealed interface ResumeCardInfo {
    /** The episode is still going: show how much of it is left. */
    data class Remaining(val secondsLeft: Long) : ResumeCardInfo

    /** The episode is (almost) over and the next one is out: show its number, name and thumbnail. */
    data class NextUp(
        val episodeNumber: Float,
        val title: String?,
        val thumbnailUrl: String?,
    ) : ResumeCardInfo

    /** The episode is over and the next one has not aired: count down to it. */
    data class Countdown(val episodeNumber: Float, val airAtMs: Long) : ResumeCardInfo

    /** The episode is over and there is nothing further, nor anything scheduled. */
    data object CaughtUp : ResumeCardInfo
}

/** Seconds left in the entry's episode, or null when its length is not known yet. */
fun ContinueWatchingEntity.secondsLeft(): Long? =
    if (durationSeconds > 0) (durationSeconds - positionSeconds).coerceAtLeast(0) else null

/** True once the episode is within [FINISHED_WITHIN_SECONDS] of its end. */
fun ContinueWatchingEntity.isFinished(): Boolean = secondsLeft()?.let { it <= FINISHED_WITHIN_SECONDS } == true

/**
 * The info a card should show. [next] is what the home screen worked out for a finished episode
 * (null while that is still loading, so the card keeps its plain look for a moment instead of flickering).
 */
fun resumeInfoFor(entry: ContinueWatchingEntity, next: ResumeCardInfo?): ResumeCardInfo? {
    val left = entry.secondsLeft() ?: return null
    if (entry.isMovie) return if (left > FINISHED_WITHIN_SECONDS) ResumeCardInfo.Remaining(left) else null
    return if (left > FINISHED_WITHIN_SECONDS) ResumeCardInfo.Remaining(left) else next
}

/** "12m left", "1h 5m left", "45s left". */
fun formatTimeLeft(seconds: Long): String {
    val s = seconds.coerceAtLeast(0)
    return when {
        s >= 3600 -> "${s / 3600}h ${(s % 3600) / 60}m left"
        s >= 60 -> "${(s + 30) / 60}m left"
        else -> "${s}s left"
    }
}

/**
 * "Airs in 3 days" while more than a day away, then a live HH:MM:SS countdown for the last 24 hours.
 * Ticks once a second only inside the last day (once a minute before that), so a row of cards stays cheap.
 */
@Composable
fun rememberAirText(airAtMs: Long): String {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    val inLastDay = airAtMs - now < DAY_MS
    LaunchedEffect(airAtMs, inLastDay) {
        while (true) {
            delay(if (inLastDay) 1_000L else 60_000L)
            now = System.currentTimeMillis()
        }
    }
    val diff = airAtMs - now
    if (diff <= 0) return "Airing now"
    if (diff >= DAY_MS) {
        // Round up so 1.2 days reads "2 days" and the "1 day" step is the one that flips to the clock.
        val days = ((diff + DAY_MS - 1) / DAY_MS).toInt()
        return if (days == 1) "Airs in 1 day" else "Airs in $days days"
    }
    val h = diff / 3_600_000
    val m = (diff / 60_000) % 60
    val sec = (diff / 1000) % 60
    return "Airs in %02d:%02d:%02d".format(h, m, sec)
}

private const val DAY_MS = 24L * 60 * 60 * 1000
