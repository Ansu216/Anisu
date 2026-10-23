package com.ansu.anime.anilist

data class AniListViewer(
    val id: Int,
    val name: String,
    val avatarUrl: String?,
    /** The banner picture of the AniList profile; null when the account has none. */
    val bannerUrl: String? = null,
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
    /** Month and day of the release date when AniList knows them (1-12 / 1-31); the list cards show the full date. */
    val startMonth: Int? = null,
    val startDay: Int? = null,
    /** AniList's raw format enum (TV, MOVIE, OVA, ...); null when a query did not ask for it. */
    val format: String? = null,
    val isAdult: Boolean = false,
    /** Romaji, English, native and synonym titles, so a source's copy of the show can be recognised in any language. */
    val altTitles: List<String> = emptyList(),
    /** Episodes the user has watched: AniList's list progress when signed in, the device's own record otherwise. 0 when unknown. */
    val progress: Int = 0,
    /** Episodes aired so far for a show still airing, when AniList knows the next one; stands in for an unknown [episodes] total. */
    val airedEpisodes: Int? = null,
)

/** A title that premiered recently, with the day it did (AniList's start date). */
data class AniListNewRelease(val media: AniListMedia, val releasedOn: java.time.LocalDate)

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
) {
    /** How many things differ from the default; drives the badge on the filter button. */
    val activeCount: Int
        get() = formats.size + statuses.size + genres.size + tags.size +
            (if (sort != null) 1 else 0) +
            (if (season != null) 1 else 0) +
            (if (year != null) 1 else 0)

    val isActive: Boolean get() = activeCount > 0
}

/** What the notification worker needs to know about a show: where it is in its release and its newest aired episode. */
data class AniListReleaseInfo(
    val id: Int,
    val title: String,
    val posterUrl: String?,
    /** FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED or HIATUS. */
    val status: String,
    val episodes: Int?,
    /** The episode that airs next, or null when none is scheduled. */
    val nextEpisode: Int?,
) {
    /** Newest episode that has already aired, when it can be worked out (next - 1, or the total for a finished show). */
    val airedEpisodes: Int?
        get() = nextEpisode?.let { it - 1 } ?: if (status == "FINISHED") episodes else null
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
    /** AniList release status: FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED or HIATUS. */
    val status: String? = null,
    /** Number of the next episode that has not aired yet while the show is RELEASING, else null. */
    val nextAiringEpisode: Int? = null,
    /** YouTube id of the trailer AniList lists for this show; the Trailers row's fallback when Jikan has none. */
    val trailerYoutubeId: String? = null,
    /** Minutes per episode (for a movie, its running time) as AniList lists it, or null. */
    val durationMinutes: Int? = null,
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

/** The small per-show facts fetched together in one AniList request; see [AniListApi.getMediaCore]. */
data class AniListMediaCore(
    val malId: Int?,
    val titles: List<String>,
    val episodes: List<AniListStreamingEpisode>,
)

/** What the Continue Watching card needs to know about a show's episodes: how many exist and when the next one airs. */
data class AniListAiringInfo(
    /** AniList release status: FINISHED, RELEASING, NOT_YET_RELEASED, CANCELLED or HIATUS. */
    val status: String?,
    /** Total episode count when AniList knows it. */
    val episodes: Int?,
    /** Number of the next episode that has not aired yet, or null. */
    val nextEpisode: Int?,
    /** When that episode airs, unix epoch seconds, or null. */
    val nextAiringAt: Long?,
)

/** A forum thread about a show on AniList (the place its comments live). */
data class AniListThread(
    val id: Int,
    val title: String,
    val replyCount: Int,
    val userName: String?,
    val isLocked: Boolean,
)

/**
 * One comment in an AniList thread, with the replies under it already nested. [text] is AniList's own markdown;
 * the comments screen turns it into plain text for display.
 */
data class AniListComment(
    val id: Int,
    val userId: Int?,
    val userName: String,
    val avatarUrl: String?,
    val text: String,
    val likeCount: Int,
    val isLiked: Boolean,
    /** Seconds since the epoch. */
    val createdAt: Long,
    val replies: List<AniListComment> = emptyList(),
)

/** One page of a thread's top-level comments. */
data class AniListCommentPage(val comments: List<AniListComment>, val hasNextPage: Boolean)
