package com.ansu.anime.ui.schedule

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class NewsArticle(
    val id: String,
    val title: String,
    val snippet: String,
    val category: String,
    val dateDisplay: String,
)

data class ScheduleEntry(
    val animeId: Int,
    val animeTitle: String,
    val episodeNumber: Int,
    val airTimeLabel: String,
    val date: LocalDate,
    val imageUrl: String?,
)

/** Number of days the Schedule tab covers, starting today. */
const val SCHEDULE_DAYS = 7

/** The next [SCHEDULE_DAYS] days from today's midnight as [start, end) unix-epoch seconds, in the device's local time zone. */
fun scheduleBounds(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val start = LocalDate.now(zone).atStartOfDay(zone)
    return start.toEpochSecond() to start.plusDays(SCHEDULE_DAYS.toLong()).toEpochSecond()
}

private val scheduleTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")

/** [com.ansu.anime.anilist.AniListAiringEntry] -> UI-local [ScheduleEntry], in the device's time zone. */
fun com.ansu.anime.anilist.AniListAiringEntry.toScheduleEntry(): ScheduleEntry {
    val zoned = Instant.ofEpochSecond(airingAt).atZone(ZoneId.systemDefault())
    return ScheduleEntry(
        animeId = media.id,
        animeTitle = media.title,
        episodeNumber = episode,
        airTimeLabel = zoned.format(scheduleTimeFormatter),
        date = zoned.toLocalDate(),
        imageUrl = media.posterUrl ?: media.bannerUrl,
    )
}
