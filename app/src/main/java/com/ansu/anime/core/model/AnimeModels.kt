package com.ansu.anime.core.model

/**
 * A show as reported by a source (an installed extension) or normalized from
 * an addon's catalog/meta response. [origin] records where it came from so
 * the rest of the app knows how to fetch episodes / streams for it later.
 */
data class SAnime(
    val id: String,
    val title: String,
    val posterUrl: String? = null,
    val bannerUrl: String? = null,
    val description: String? = null,
    val genres: List<String> = emptyList(),
    val releaseYear: Int? = null,
    val rating: Double? = null,
    val anilistId: Int? = null,
    val episodes: Int? = null,
    /** Display label such as "TV", "Movie", "OVA"; null when the source did not say. */
    val format: String? = null,
    /** Age-rating chip text ("PG", "PG-13", "R-17+", "R+", "Rx", "NR"); null means derive it from [genres]. */
    val ageRating: String? = null,
    val origin: MediaOrigin,
)

/** Where a piece of media came from, and enough info to go fetch more of it. */
sealed class MediaOrigin {
    data class Extension(val sourceId: Long, val urlPath: String) : MediaOrigin()
    data class Addon(val addonId: String, val addonBaseUrl: String, val type: String, val stremioId: String) : MediaOrigin()
}

data class SEpisode(
    val id: String,
    val name: String,
    val episodeNumber: Float,
    val thumbnailUrl: String? = null,
    val dateUpload: Long = 0L,
    val description: String? = null,
    val airDate: String? = null,
)

data class Video(
    val url: String,
    val quality: String,
    val sourceLabel: String,
    val headers: Map<String, String> = emptyMap(),
    val subtitleTracks: List<SubtitleTrack> = emptyList(),
)

data class SubtitleTrack(val url: String, val lang: String)

data class AnimesPage(val animes: List<SAnime>, val hasNextPage: Boolean)

sealed class AnimeFilter {
    abstract val displayName: String

    data class Text(override val displayName: String, var state: String = "") : AnimeFilter()
    data class Select(override val displayName: String, val values: List<String>, var state: Int = 0) : AnimeFilter()
    data class Checkbox(override val displayName: String, var state: Boolean = false) : AnimeFilter()
}

typealias AnimeFilterList = List<AnimeFilter>

/** A shelf of media to show on the home screen, e.g. "Trending Now" or "From Nuvio Streams". */
data class Shelf(
    val title: String,
    val items: List<SAnime>,
)
