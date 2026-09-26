package com.ansu.anime.anilist

data class AniListViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
)

data class AniListMedia(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val bannerUrl: String?,
    val description: String?,
    val genres: List<String>,
    val averageScore: Int?,
    val episodes: Int?,
    val year: Int?,
)

/** One row of the signed-in user's list: a media plus their personal progress on it. */
data class AniListMediaListEntry(
    val mediaId: Int,
    val progress: Int,
    val status: String,
    val media: AniListMedia,
)
