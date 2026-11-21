package com.ansu.anime.core.util

private val ADULT_GENRES = setOf("hentai")
private val NUDITY_GENRES = setOf("ecchi")
private val VIOLENCE_GENRES = setOf("horror", "thriller", "psychological")
private val TEEN_GENRES = setOf("action", "drama", "mystery", "romance", "supernatural", "sci-fi", "mecha")

/**
 * The text of the age-rating chip on a poster, in the MyAnimeList style: PG, PG-13, R-17+, R+
 * (mild nudity) and Rx (hentai). AniList has no such rating, only an adult flag and genres, so
 * this is an estimate: the adult flag or Hentai is "Rx", Ecchi is "R+", Horror, Thriller or
 * Psychological is "R-17+", the darker mainstream genres (Action, Drama, Mystery, Romance,
 * Supernatural, Sci-Fi, Mecha) are "PG-13", and any other title is "PG". A title with no genre
 * information at all is "NR" (not rated) instead of a guess.
 */
fun ageRatingFor(genres: List<String>, isAdult: Boolean): String {
    val lower = genres.map { it.lowercase() }
    return when {
        isAdult || lower.any { it in ADULT_GENRES } -> "Rx"
        lower.any { it in NUDITY_GENRES } -> "R+"
        lower.any { it in VIOLENCE_GENRES } -> "R-17+"
        lower.any { it in TEEN_GENRES } -> "PG-13"
        lower.isEmpty() -> "NR"
        else -> "PG"
    }
}

/** Turns AniList's format enum (TV_SHORT, MOVIE, ...) into the label shown in the UI. */
fun formatLabel(raw: String?): String? = when (raw) {
    null -> null
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "MOVIE" -> "Movie"
    "SPECIAL" -> "Special"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> "Music"
    else -> raw.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
}
