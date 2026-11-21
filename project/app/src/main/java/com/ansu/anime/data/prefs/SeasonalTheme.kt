package com.ansu.anime.data.prefs

import androidx.annotation.DrawableRes
import com.ansu.anime.R
import java.time.LocalDate

/**
 * The hidden seasonal themes. Each one dresses the app in its own accent colour, background animation
 * and logo emblem, and comes back on its own every year:
 *
 *  - Christmas: 1 December to 6 January
 *  - Halloween: 20 October to 2 November
 *  - Easter:    the week before and after Easter Sunday
 *
 * They are not listed anywhere in Appearance. Developer options (seven taps on the version code in
 * About) can force one on at any time, which is how they are tested; the forced choice is stored, and
 * "Auto" goes back to following the calendar.
 */
enum class SeasonalTheme(
    val key: String,
    val label: String,
    val accent: AccentPalette,
    val background: BackgroundStyle,
    /**
     * What the Anisu logo wears while the theme is on, drawn as a vector: the Christmas and Halloween
     * hats sit on top of the badge, the Easter ears stand behind it, the way VLC's cone wears its hat.
     */
    @DrawableRes val emblemRes: Int,
) {
    CHRISTMAS("christmas", "Christmas", AccentPalette.RED, BackgroundStyle.SNOW, R.drawable.ic_emblem_christmas),
    HALLOWEEN("halloween", "Halloween", AccentPalette.ORANGE, BackgroundStyle.HALLOWEEN, R.drawable.ic_emblem_halloween),
    EASTER("easter", "Easter", AccentPalette.SAKURA, BackgroundStyle.EASTER, R.drawable.ic_emblem_easter);

    companion object {
        fun fromKey(key: String?): SeasonalTheme? = entries.firstOrNull { it.key == key }

        /** The season whose window contains [date], or null when no season is on. */
        fun activeOn(date: LocalDate): SeasonalTheme? = when {
            date.monthValue == 12 || (date.monthValue == 1 && date.dayOfMonth <= 6) -> CHRISTMAS
            (date.monthValue == 10 && date.dayOfMonth >= 20) ||
                (date.monthValue == 11 && date.dayOfMonth <= 2) -> HALLOWEEN
            isEasterWindow(date) -> EASTER
            else -> null
        }

        private fun isEasterWindow(date: LocalDate): Boolean {
            val easter = easterSunday(date.year)
            return !date.isBefore(easter.minusDays(7)) && !date.isAfter(easter.plusDays(7))
        }

        /** Easter Sunday, by the anonymous Gregorian (Meeus/Jones/Butcher) algorithm. */
        private fun easterSunday(year: Int): LocalDate {
            val a = year % 19
            val b = year / 100
            val c = year % 100
            val d = b / 4
            val e = b % 4
            val f = (b + 8) / 25
            val g = (b - f + 1) / 3
            val h = (19 * a + b - d - g + 15) % 30
            val i = c / 4
            val k = c % 4
            val l = (32 + 2 * e + 2 * i - h - k) % 7
            val m = (a + 11 * h + 22 * l) / 451
            val month = (h + l - 7 * m + 114) / 31
            val day = ((h + l - 7 * m + 114) % 31) + 1
            return LocalDate.of(year, month, day)
        }
    }
}
