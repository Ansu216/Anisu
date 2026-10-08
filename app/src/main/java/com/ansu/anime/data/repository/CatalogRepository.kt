package com.ansu.anime.data.repository

import com.ansu.anime.addon.AddonManager
import com.ansu.anime.addon.model.StremioMeta
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.anilist.AniListMediaListEntry
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.anilist.AniListSearchFilters
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.Shelf
import com.ansu.anime.core.util.ContentFilter
import com.ansu.anime.core.util.ageRatingFor
import com.ansu.anime.core.util.formatLabel
import com.ansu.anime.extension.ExtensionManager
import com.ansu.anime.extension.api.AnimeCatalogueSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Builds the home screen's feed by pulling from every content source the app
 * currently knows about: installed extensions, installed addons, and (if
 * logged in) the user's own AniList lists. Nothing here is hardcoded to one
 * provider — add an extension or an addon and a new shelf shows up.
 */
class CatalogRepository(
    private val extensionManager: ExtensionManager,
    private val addonManager: AddonManager,
    private val aniList: AniListRepository,
) {
    suspend fun buildHomeShelves(): List<Shelf> = coroutineScope {
        val extensionShelves = async { buildExtensionShelves() }
        val addonShelves = async { buildAddonShelves() }
        val listShelves = async { buildAniListShelves() }

        listShelves.await() + extensionShelves.await() + addonShelves.await()
    }

    private suspend fun buildExtensionShelves(): List<Shelf> = coroutineScope {
        extensionManager.allSources().map { source ->
            async {
                val label = if (source.id == 1L) "Trending Now" else "From ${source.name}"
                val page = runCatching { source.getPopularAnime(1) }.getOrNull()
                Shelf(title = label, items = page?.animes.orEmpty().filterNot { ContentFilter.isAdultGenres(it.genres) })
            }
        }.map { it.await() }.filter { it.items.isNotEmpty() }
    }

    private suspend fun buildAddonShelves(): List<Shelf> = coroutineScope {
        val seriesShelves = async { addonManager.getShelvesForType("series") }
        val movieShelves = async { addonManager.getShelvesForType("movie") }
        // Kitsu-style anime addons declare their own "anime" type rather than "series".
        val animeShelves = async { addonManager.getShelvesForType("anime") }
        val combined = seriesShelves.await() + movieShelves.await() + animeShelves.await()
        combined.map { (addon, metas) ->
            Shelf(
                title = "From ${addon.name}",
                items = metas.map { it.toSAnime(addon.id, addon.baseUrl) }.filterNot { ContentFilter.isAdultGenres(it.genres) },
            )
        }.filter { it.items.isNotEmpty() }
    }

    private suspend fun buildAniListShelves(): List<Shelf> {
        if (!aniList.isLoggedIn.value) return emptyList()
        val watching = aniList.getCurrentlyWatching()
        val planning = aniList.getPlanning()
        return listOfNotNull(
            watching.takeIf { it.isNotEmpty() }?.let { Shelf("Continue Your List", it.map { e -> e.toSAnime() }) },
            planning.takeIf { it.isNotEmpty() }?.let { Shelf("Planning To Watch", it.map { e -> e.toSAnime() }) },
        )
    }

    /**
     * Searches AniList only. Installed extensions are not searched: every result is an AniList title, and its
     * streams are gathered from the installed sources when it is opened.
     * [query] may be empty when [filters] alone drive the search (browse by genre, format, ...).
     * Returns null when nothing could be loaded because the request failed.
     */
    suspend fun search(
        query: String,
        filters: AniListSearchFilters = AniListSearchFilters(),
        page: Int = 1,
    ): SearchPage? {
        val aniListPage = aniList.searchPage(query, filters, page)
        val items = aniListPage?.media.orEmpty().map { it.toSAnime(sourceId = defaultSource()?.id ?: 1L) }
        return if (aniListPage == null) null else SearchPage(items, aniListPage.hasNextPage)
    }

    /** The default source to resolve an AniList-only entry against until real per-title source matching exists. */
    private fun defaultSource(): AnimeCatalogueSource? = extensionManager.getSource(1L)

    private fun AniListMediaListEntry.toSAnime(): SAnime =
        media.toSAnime(sourceId = defaultSource()?.id ?: 1L)

    private fun StremioMeta.toSAnime(addonId: String, addonBaseUrl: String): SAnime = SAnime(
        id = id,
        title = name,
        posterUrl = poster,
        bannerUrl = background,
        description = description,
        genres = genres.orEmpty(),
        releaseYear = releaseInfo?.take(4)?.toIntOrNull(),
        rating = imdbRating?.toDoubleOrNull(),
        anilistId = null,
        format = if (type == "movie") "Movie" else "TV",
        ageRating = ageRatingFor(genres.orEmpty(), isAdult = false),
        origin = MediaOrigin.Addon(addonId = addonId, addonBaseUrl = addonBaseUrl, type = type, stremioId = id),
    )
}

/**
 * Normalises an AniList-only entry (a list row, a trending or season pick) into
 * [SAnime]. Until per-title source matching exists it points at [sourceId] — by
 * default the built-in demo source — so the details page and playback have
 * something to resolve against.
 */
fun AniListMedia.toSAnime(sourceId: Long = 1L): SAnime = SAnime(
    id = id.toString(),
    title = title,
    posterUrl = posterUrl,
    bannerUrl = bannerUrl,
    description = description,
    genres = genres,
    releaseYear = year,
    rating = averageScore?.div(10.0),
    anilistId = id,
    episodes = episodes,
    format = formatLabel(format),
    ageRating = ageRatingFor(genres, isAdult),
    origin = MediaOrigin.Extension(sourceId = sourceId, urlPath = id.toString()),
)

/** One page of Search results plus whether AniList has more after it. */
data class SearchPage(val items: List<SAnime>, val hasNextPage: Boolean)
