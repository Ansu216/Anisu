package com.ansu.anime.data.news

import java.time.DayOfWeek
import java.time.ZoneId
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters

/**
 * Display model for a single News tab card — screen-local, same pattern as
 * EpisodeItem/RelatedCard in DetailsScreen.kt. [dateDisplay] is pre-formatted
 * ("Sun, 27 Sep 2026") so the screen never needs its own date formatter.
 */
data class NewsArticle(
    val id: String,
    val title: String,
    val snippet: String,
    val category: String, // "Anime", "Manga", "People"...
    val dateDisplay: String,
    val url: String? = null, // optional deep link to the full article
)

/**
 * A single airing slot for the Schedule tab — mapped from AniList's real
 * `airingSchedules` data. [dayOfWeek] and [airTimeLabel] are pre-formatted in
 * the device's local time zone.
 */
data class ScheduleEntry(
    val id: Int, // the airing-schedule entry's own id (unique per episode/slot)
    val animeTitle: String,
    val episodeNumber: Int,
    val airTimeLabel: String,
    val dayOfWeek: String,
    val imageUrl: String?,
)

object NewsScheduleModels {
    private val scheduleTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

    /**
     * Current week bounds (Monday 00:00 to Sunday 23:59) in the device's local
     * time zone, used to drive the Schedule query window.
     */
    fun currentWeekBounds(): Pair<Long, Long> {
        val now = ZonedDateTime.now()
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .toLocalDate()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant()
            .epochSecond
        val sunday = monday + 7 * 24 * 60 * 60
        return monday to (sunday - 1)
    }

    fun scheduleTimeFormatter(): DateTimeFormatter = scheduleTimeFormatter

    fun dayOfWeekLabel(dayOfWeek: DayOfWeek): String =
        dayOfWeek.getDisplayName(TextStyle.FULL, java.util.Locale.getDefault())
}
