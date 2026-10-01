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
    /** AniList's raw format enum (TV, MOVIE, OVA, ...); null when a query did not ask for it. */
    val format: String? = null,
    val isAdult: Boolean = false,
)

/**
 * The filters of the Search screen's filter sheet. Every value is an AniList enum name or a
 * genre/tag name taken from a fixed list in the UI, never free user text.
 * A null [sort] means "best match" (or popularity when there is no search text).
 */
data class AniListSearchFilters(
    val sort: String? = null,
    val formats: Set<String> = emptySet(),
    val statuses: Set<String> = emptySet(),
    val genres: Set<String> = emptySet(),
    val tags: Set<String> = emptySet(),
    val season: String? = null,
    val year: Int? = null,
    val showAdult: Boolean = false,
) {
    /** How many things differ from the default; drives the badge on the filter button. */
    val activeCount: Int
        get() = formats.size + statuses.size + genres.size + tags.size +
            (if (sort != null) 1 else 0) +
            (if (season != null) 1 else 0) +
            (if (year != null) 1 else 0) +
            (if (showAdult) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}

/** One row of the signed-in user's list: a media plus their personal progress on it. */
data class AniListMediaListEntry(
    val mediaId: Int,
    val progress: Int,
    val status: String,
    val media: AniListMedia,
)

/** Everything the CornCastle-style details page needs beyond the basic card: stats, cast, crew, related shows. */
data class AniListMediaDetails(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    val bannerUrl: String?,
    val description: String?,
    val genres: List<String>,
    val averageScore: Int?,
    val episodes: Int?,
    val year: Int?,
    val format: String?,
    val isFavourite: Boolean,
    val characters: List<AniListCharacter>,
    val staff: List<AniListStaffMember>,
    val related: List<AniListMedia>,
    /** Other entries of the same franchise (sequels, prequels, movies, spin-offs), for "More from this Show". */
    val franchise: List<AniListRelation> = emptyList(),
    /** The signed-in user's AniList list status for this show (CURRENT/PLANNING/COMPLETED/...), or null. */
    val listStatus: String? = null,
    /** True when AniList lists an earlier anime entry before this one (this is a later season or a follow-up). */
    val hasPrequel: Boolean = false,
)

/** A show connected to another one on AniList, with how it connects (e.g. "Sequel", "Movie"). */
data class AniListRelation(
    val media: AniListMedia,
    val label: String,
)

/** The signed-in user's relationship to one show on AniList: heart plus list entry. */
data class AniListUserState(
    val isFavourite: Boolean,
    val listStatus: String?,
    val progress: Int,
)

data class AniListCharacter(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val role: String,
    val description: String?,
    val voiceActorName: String?,
    val voiceActorImageUrl: String?,
)

/** One episode airing in a given time window, for the Schedule tab. */
data class AniListAiringEntry(
    val airingAt: Long, // unix epoch seconds
    val episode: Int,
    val media: AniListMedia,
)

data class AniListStaffMember(
    val id: Int,
    val name: String,
    val imageUrl: String?,
    val role: String,
    val description: String?,
)

/** One page of a paged media query, plus whether AniList has more after it. */
data class AniListMediaPage(
    val media: List<AniListMedia>,
    val hasNextPage: Boolean,
)

/** One entry of AniList's `streamingEpisodes` for a show: a title and thumbnail supplied by its legal streaming site. */
data class AniListStreamingEpisode(
    val title: String,
    val thumbnailUrl: String?,
)
