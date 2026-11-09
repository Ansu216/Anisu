package com.ansu.anime.data.repository

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
import com.ansu.anime.ui.search.TitleMatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Builds the home screen's feed by pulling from every content source the app
 * currently knows about: installed extensions and (if logged in) the user's own
 * AniList lists. Nothing here is hardcoded to one provider — add an extension and
 * a new shelf shows up.
 */
class CatalogRepository(
    private val extensionManager: ExtensionManager,
    private val aniList: AniListRepository,
) {
    suspend fun buildHomeShelves(): List<Shelf> = coroutineScope {
        val extensionShelves = async { buildExtensionShelves() }
        val listShelves = async { buildAniListShelves() }

        listShelves.await() + extensionShelves.await()
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
        val sourceId = defaultSource()?.id ?: 1L
        val items = aniListPage?.media.orEmpty().map { it.toSAnime(sourceId = sourceId) }
        if (aniListPage == null) return null
        // AniList matches spelled-out words, so a typo ("narto", "shingeki no kyojn") finds nothing, or only loose
        // matches. When the first page has no close title, the query is retried in shorter forms and every
        // candidate is ranked by how close it is to what was typed.
        if (page == 1 && query.isNotBlank() && items.none { TitleMatcher.scoreAny(query, it.title, it.altTitles) >= FUZZY_GOOD_ENOUGH }) {
            val fuzzy = fuzzyCandidates(query, filters, sourceId)
            if (fuzzy.isNotEmpty()) {
                val ids = fuzzy.map { it.id }.toSet()
                return SearchPage(fuzzy + items.filterNot { it.id in ids }, aniListPage.hasNextPage)
            }
        }
        return SearchPage(items, aniListPage.hasNextPage)
    }

    /**
     * Retries a query that found no close title with two shortened forms: the longest typed word and the whole phrase,
     * each with its last letters cut (so a wrong last letter or two stops mattering). What comes back is ranked with [TitleMatcher], and
     * only titles close enough to the typed text are kept, best first.
     */
    private suspend fun fuzzyCandidates(query: String, filters: AniListSearchFilters, sourceId: Long): List<SAnime> {
        val whole = TitleMatcher.normalize(query)
        val longest = whole.split(' ').filter { it.length >= 3 }.maxByOrNull { it.length } ?: return emptyList()
        // Two tries at most: the longest word with its last letters cut, then the whole phrase cut the same way
        // (or the word cut harder when the phrase is one short word).
        val attempts = LinkedHashSet<String>()
        attempts += longest.take(maxOf(3, longest.length - 2))
        attempts += if (whole.length > 5 && whole != longest) whole.dropLast(2) else longest.take(maxOf(3, (longest.length * 3 + 3) / 5))
        attempts.remove(whole)
        val found = coroutineScope {
            attempts.take(FUZZY_MAX_ATTEMPTS).map { attempt ->
                async {
                    try {
                        aniList.searchPage(attempt, filters, 1)?.media.orEmpty()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Throwable) {
                        emptyList()
                    }
                }
            }.awaitAll()
        }.flatten().distinctBy { it.id }
        return found
            .map { media -> media to TitleMatcher.scoreAny(query, media.title, media.altTitles) }
            .filter { it.second >= TitleMatcher.MIN_SCORE }
            .sortedByDescending { it.second }
            .map { it.first.toSAnime(sourceId = sourceId) }
    }

    /**
     * Searches one installed source (an empty [query] lists what the source has on its popular page) and turns what
     * it finds into AniList titles, so every result opens the same details page as an ordinary search result. A
     * title the source has that AniList cannot be matched to is left out. The results point at [sourceId], so that
     * source is the one asked first for the streams. Returns null when the source could not be searched.
     */
    suspend fun searchInSource(sourceId: Long, query: String, page: Int = 1): SearchPage? {
        val source = extensionManager.getSource(sourceId) ?: return null
        val listed = try {
            if (query.isBlank()) source.getPopularAnime(page) else source.getSearchAnime(page, query, source.getFilterList())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            return null
        }
        // The page's titles are matched in a handful of combined AniList requests instead of one request each:
        // the request gate spaces calls 350 ms apart, so one-by-one used to take several seconds per page.
        val found = listed.animes.take(SOURCE_SEARCH_LIMIT)
        val byTitle: Map<String, List<AniListMedia>> = coroutineScope {
            found.chunked(SEARCH_BATCH_SIZE)
                .map { chunk -> async { aniList.searchTitlesBatch(chunk.map { it.title }) } }
                .awaitAll()
                .flatMap { batch -> batch.entries }
                .associate { entry -> entry.key to entry.value }
        }
        val matched: List<AniListMedia?> = found.map { result ->
            byTitle[result.title].orEmpty()
                .map { media -> media to TitleMatcher.scoreAny(result.title, media.title, media.altTitles) }
                .filter { scored -> scored.second >= SOURCE_MATCH_MIN }
                .maxByOrNull { scored -> scored.second }
                ?.first
        }
        val items = matched.filterNotNull()
            .distinctBy { it.id }
            .map { it.toSAnime(sourceId = source.id) }
            .filterNot { ContentFilter.isAdultGenres(it.genres) }
        return SearchPage(items, listed.hasNextPage)
    }

    /** The default source to resolve an AniList-only entry against until real per-title source matching exists. */
    private fun defaultSource(): AnimeCatalogueSource? = extensionManager.getSource(1L)

    private fun AniListMediaListEntry.toSAnime(): SAnime =
        media.toSAnime(sourceId = defaultSource()?.id ?: 1L)

    private companion object {
        /** A source's page is matched to AniList title by title; this many per page keeps that to one quick burst. */
        const val SOURCE_SEARCH_LIMIT = 20

        /** How close a source's title must be to an AniList title (0..1) to count as the same show. */
        const val SOURCE_MATCH_MIN = 0.85f

        /** Titles per combined AniList search; keeps the query well inside AniList's complexity limit. */
        const val SEARCH_BATCH_SIZE = 5

        /** A search result this close (0..1) to the typed text means AniList understood the query; no retry. */
        const val FUZZY_GOOD_ENOUGH = 0.75f

        /** The most shortened queries tried for one typo'd search; the request gate spaces them out. */
        const val FUZZY_MAX_ATTEMPTS = 2
    }
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
    altTitles = altTitles,
    origin = MediaOrigin.Extension(sourceId = sourceId, urlPath = id.toString()),
)

/** One page of Search results plus whether AniList has more after it. */
data class SearchPage(val items: List<SAnime>, val hasNextPage: Boolean)
