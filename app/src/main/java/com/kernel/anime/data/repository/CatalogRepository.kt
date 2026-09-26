package com.kernel.anime.data.repository

import com.kernel.anime.addon.AddonManager
import com.kernel.anime.addon.model.StremioMeta
import com.kernel.anime.anilist.AniListMediaListEntry
import com.kernel.anime.anilist.AniListRepository
import com.kernel.anime.core.model.MediaOrigin
import com.kernel.anime.core.model.SAnime
import com.kernel.anime.core.model.Shelf
import com.kernel.anime.extension.ExtensionManager
import com.kernel.anime.extension.api.AnimeCatalogueSource
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
                Shelf(title = label, items = page?.animes.orEmpty())
            }
        }.map { it.await() }.filter { it.items.isNotEmpty() }
    }

    private suspend fun buildAddonShelves(): List<Shelf> = coroutineScope {
        val seriesShelves = async { addonManager.getShelvesForType("series") }
        val movieShelves = async { addonManager.getShelvesForType("movie") }
        val combined = seriesShelves.await() + movieShelves.await()
        combined.map { (addon, metas) ->
            Shelf(
                title = "From ${addon.name}",
                items = metas.map { it.toSAnime(addon.id, addon.baseUrl) },
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

    suspend fun search(query: String): List<SAnime> = coroutineScope {
        val extensionResults = extensionManager.allSources().map { source ->
            async { runCatching { source.getSearchAnime(1, query, source.getFilterList()) }.getOrNull()?.animes.orEmpty() }
        }.flatMap { it.await() }
        extensionResults
    }

    /** The default source to resolve an AniList-only entry against until real per-title source matching exists. */
    private fun defaultSource(): AnimeCatalogueSource? = extensionManager.allSources().firstOrNull()

    private fun AniListMediaListEntry.toSAnime(): SAnime {
        val sourceId = defaultSource()?.id ?: 1L
        return SAnime(
            id = media.id.toString(),
            title = media.title,
            posterUrl = media.posterUrl,
            bannerUrl = media.bannerUrl,
            description = media.description,
            genres = media.genres,
            releaseYear = media.year,
            rating = media.averageScore?.div(10.0),
            anilistId = media.id,
            origin = MediaOrigin.Extension(sourceId = sourceId, urlPath = media.id.toString()),
        )
    }

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
        origin = MediaOrigin.Addon(addonId = addonId, addonBaseUrl = addonBaseUrl, type = type, stremioId = id),
    )
}
