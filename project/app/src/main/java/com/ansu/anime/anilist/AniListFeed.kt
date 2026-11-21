package com.ansu.anime.anilist

private val SERIES_FORMATS = listOf("TV", "TV_SHORT", "ONA")
private val MOVIE_FORMATS = listOf("MOVIE")

/**
 * The AniList-backed catalogue rows on the home screen. Each one is an
 * endlessly paged query: [sort], an optional release [status] and an optional
 * set of [formats] (empty = any format).
 */
enum class AniListFeed(
    val title: String,
    val sort: String,
    val status: String? = null,
    val formats: List<String> = emptyList(),
) {
    TRENDING_NOW("Trending Now", "TRENDING_DESC"),
    CURRENTLY_AIRING("Currently Airing", "POPULARITY_DESC", status = "RELEASING"),
    TRENDING_MOVIES("Trending Movies", "TRENDING_DESC", formats = MOVIE_FORMATS),
    POPULAR_ANIME("All-Time Popular Anime", "POPULARITY_DESC", formats = SERIES_FORMATS),
    POPULAR_MOVIES("All-Time Popular Movies", "POPULARITY_DESC", formats = MOVIE_FORMATS),
    UPCOMING_ANIME("Upcoming Anime", "POPULARITY_DESC", status = "NOT_YET_RELEASED", formats = SERIES_FORMATS),
    UPCOMING_MOVIES("Upcoming Movies", "POPULARITY_DESC", status = "NOT_YET_RELEASED", formats = MOVIE_FORMATS),
    TOP_RATED_ANIME("Top Rated Anime", "SCORE_DESC", formats = SERIES_FORMATS),
    TOP_RATED_MOVIES("Top Rated Movies", "SCORE_DESC", formats = MOVIE_FORMATS),
}
