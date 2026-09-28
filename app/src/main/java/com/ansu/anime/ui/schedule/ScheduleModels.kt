package com.ansu.anime.ui.schedule

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

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
    val dayOfWeek: String,
    val imageUrl: String?,
)

/** Current week's [start, end) bounds as unix-epoch seconds, in the device's local time zone. */
fun currentWeekBounds(): Pair<Long, Long> {
    val zone = ZoneId.systemDefault()
    val now = Instant.now().atZone(zone)
    val startOfWeek = now.toLocalDate().minusDays((now.dayOfWeek.value - 1).toLong()).atStartOfDay(zone)
    val endOfWeek = startOfWeek.plusDays(7)
    return startOfWeek.toEpochSecond() to endOfWeek.toEpochSecond()
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
        dayOfWeek = zoned.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        imageUrl = media.posterUrl ?: media.bannerUrl,
    )
}
