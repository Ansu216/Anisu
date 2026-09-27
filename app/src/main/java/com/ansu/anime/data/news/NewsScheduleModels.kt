package com.ansu.anime.ui.screens

import com.ansu.anime.data.news.ScheduleEntry
import com.ansu.anime.data.news.NewsArticle
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

object NewsScheduleModels {
    private val weekDayLabels: List<String> =
        listOf(
            DayOfWeek.MONDAY,
            DayOfWeek.TUESDAY,
            DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY,
            DayOfWeek.FRIDAY,
            DayOfWeek.SATURDAY,
            DayOfWeek.SUNDAY,
        ).map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }

    fun weekDayLabels(): List<String> = weekDayLabels

    fun currentWeekBounds(): Pair<Long, Long> {
        val now = LocalDate.now()
        val monday = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val weekStart = monday.atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().epochSecond
        val weekEnd = monday.plusDays(7).atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().epochSecond
        return weekStart to weekEnd
    }

    fun scheduleTimeFormatter(): java.time.format.DateTimeFormatter =
        java.time.format.DateTimeFormatter.ofPattern("h:mm a")

    fun dayOfWeekLabel(dayOfWeek: DayOfWeek): String =
        dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
}
