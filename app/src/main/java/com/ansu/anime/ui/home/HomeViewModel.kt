package com.ansu.anime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListFeed
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.Shelf
import com.ansu.anime.data.db.ContinueWatchingEntity
import com.ansu.anime.data.prefs.TitleLanguage
import com.ansu.anime.data.repository.CatalogRepository
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.data.repository.LocalListRepository
import com.ansu.anime.data.repository.toSAnime
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

/** How many titles each endless row fetches per page. */
private const val FEED_PAGE_SIZE = 10

/** Paging state of one endless catalogue row. */
data class FeedState(
    val feed: AniListFeed,
    val items: List<SAnime> = emptyList(),
    val nextPage: Int = 1,
    val hasNextPage: Boolean = true,
    val isLoading: Boolean = false,
    /** True once at least one page came back successfully. */
    val hasLoaded: Boolean = false,
    /** The last page request failed; cleared when a retry starts. */
    val loadFailed: Boolean = false,
)

/** One "Your Next Watch" card: a title worth watching, and the show of the user's it was suggested by. */
data class WatchPick(val anime: SAnime, val becauseOf: String)

/** A show the user has been watching, which recommendations are drawn from. */
private data class PickSeed(val id: Int, val title: String)

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<ContinueWatchingEntity> = emptyList(),
    val feeds: List<FeedState> = AniListFeed.entries.map { FeedState(it) },
    val topPicks: List<SAnime> = emptyList(),
    /** Recommendations built from what the user watches; empty until there is something to base them on. */
    val picks: List<WatchPick> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val error: String? = null,
) {
    fun feed(feed: AniListFeed): FeedState = feeds.first { it.feed == feed }
}

private fun HomeUiState.withFeed(feed: AniListFeed, change: (FeedState) -> FeedState): HomeUiState =
    copy(feeds = feeds.map { if (it.feed == feed) change(it) else it })

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val aniListRepository: AniListRepository,
    titleLanguage: StateFlow<TitleLanguage>,
    private val localListRepository: LocalListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    private val _favouriteIds = MutableStateFlow<Set<Int>>(emptySet())

    /** AniList ids of the titles the user has hearted (AniList's favourites when signed in, the device's otherwise); drives the hero banner's heart. */
    val favouriteIds: StateFlow<Set<Int>> = _favouriteIds

    init {
        continueWatchingRepository.entries
            .onEach { list -> _uiState.update { it.copy(continueWatching = list) } }
            .launchIn(viewModelScope)
        viewModelScope.launch {
            // Re-read whenever the account changes or a heart is toggled on the device.
            combine(aniListRepository.isLoggedIn, localListRepository.favourites) { loggedIn, local -> loggedIn to local }
                .collectLatest { (loggedIn, local) ->
                    _favouriteIds.value =
                        if (loggedIn) aniListRepository.getLikedIds() else local.map { it.id }.toSet()
                }
        }
        viewModelScope.launch { observePicks() }
        refresh()
        // Titles are fetched in the chosen language, so a change in Settings reloads the rows.
        titleLanguage.drop(1)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    /**
     * Rebuilds the "Your Next Watch" picks whenever the set of shows the user watches changes: what is in
     * Continue Watching, plus their Watching and Completed lists (AniList's when signed in, the device's otherwise).
     * Progress ticks do not change the seed ids, so the picks are not rebuilt while an episode plays.
     */
    private suspend fun observePicks() {
        combine(
            // Progress is saved every second while an episode plays; only a change of shows should restart the picks.
            continueWatchingRepository.entries
                .map { list -> list.take(4).map { PickSeed(it.anilistId, it.title) } }
                .distinctUntilChanged(),
            aniListRepository.isLoggedIn,
            localListRepository.withStatus("CURRENT"),
            localListRepository.withStatus("COMPLETED"),
        ) { fromHistory, loggedIn, localCurrent, localCompleted ->
            val fromLists = if (loggedIn) {
                (aniListRepository.getCurrentlyWatching().take(4) + aniListRepository.getCompleted().take(4))
                    .map { PickSeed(it.mediaId, it.media.title) }
            } else {
                (localCurrent.take(4) + localCompleted.take(4)).map { PickSeed(it.id, it.title) }
            }
            (fromHistory + fromLists).distinctBy { it.id }.take(6)
        }
            .distinctUntilChanged()
            .collectLatest { seeds -> _uiState.update { it.copy(picks = buildPicks(seeds)) } }
    }

    private suspend fun buildPicks(seeds: List<PickSeed>): List<WatchPick> {
        if (seeds.isEmpty()) return emptyList()
        val perSeed = coroutineScope {
            seeds.map { seed -> async { seed to aniListRepository.getRecommendations(seed.id) } }.awaitAll()
        }
        val seen = seeds.map { it.id }.toMutableSet()
        val picks = mutableListOf<WatchPick>()
        // One suggestion from each show in turn, so a single favourite cannot fill the whole banner.
        for (rank in 0 until 4) {
            for ((seed, recs) in perSeed) {
                val media = recs.getOrNull(rank) ?: continue
                if (picks.size >= 8 || !seen.add(media.id)) continue
                picks += WatchPick(media.toSAnime(), seed.title)
            }
        }
        return picks
    }

    /** Hearts or un-hearts [anime]. The heart changes at once; a signed-in change that AniList refuses is put back. */
    fun toggleFavourite(anime: SAnime) {
        val id = anime.anilistId ?: return
        viewModelScope.launch {
            if (aniListRepository.isLoggedIn.value) {
                val before = _favouriteIds.value
                _favouriteIds.value = if (id in before) before - id else before + id
                if (!aniListRepository.toggleFavourite(id)) _favouriteIds.value = before
            } else {
                // The device's own list is observed above, so the heart follows the database.
                localListRepository.toggleFavourite(anime)
            }
        }
    }

    fun removeContinueWatching(anilistId: Int) {
        viewModelScope.launch { continueWatchingRepository.remove(anilistId) }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update {
                it.copy(isLoading = true, error = null, feeds = AniListFeed.entries.map { f -> FeedState(f) })
            }

            // The catalogue feed, the first Trending page and the season picks are
            // independent network calls, so they are fetched in parallel. The other
            // rows load lazily, when they first scroll into view.
            val shelves = async { runCatching { catalogRepository.buildHomeShelves() } }
            val trending = loadMore(AniListFeed.TRENDING_NOW)

            val shelfResult = shelves.await()
            trending?.join()
            _uiState.update {
                it.copy(
                    isLoading = false,
                    shelves = shelfResult.getOrDefault(emptyList()),
                    error = shelfResult.exceptionOrNull()?.message,
                )
            }
        }
    }

    /**
     * Fetches the next [FEED_PAGE_SIZE] titles of [feed] — the first page if nothing
     * is loaded yet. A no-op while a request for that row is in flight or once the
     * row is exhausted. Returns the loading job, or null if nothing was started.
     */
    fun loadMore(feed: AniListFeed): Job? {
        var started = false
        _uiState.update { state ->
            started = false
            val row = state.feed(feed)
            if (row.isLoading || !row.hasNextPage) {
                state
            } else {
                started = true
                state.withFeed(feed) { it.copy(isLoading = true, loadFailed = false) }
            }
        }
        if (!started) return null

        val page = _uiState.value.feed(feed).nextPage
        return viewModelScope.launch {
            val result = aniListRepository.getFeedPage(feed, page, FEED_PAGE_SIZE)
            _uiState.update { state ->
                state.withFeed(feed) { row ->
                    if (result == null) {
                        row.copy(isLoading = false, loadFailed = true)
                    } else {
                        row.copy(
                            // AniList's ordering can shift between requests, so drop repeats.
                            items = (row.items + result.media.map { it.toSAnime() }).distinctBy { it.id },
                            nextPage = row.nextPage + 1,
                            hasNextPage = result.hasNextPage,
                            isLoading = false,
                            hasLoaded = true,
                            loadFailed = false,
                        )
                    }
                }
            }
        }
    }
}

/** The AniList season enum value + year for "right now", in the device's local time zone. */
private fun currentSeason(): Pair<String, Int> {
    val now = LocalDate.now()
    val season = when (now.monthValue) {
        12, 1, 2 -> "WINTER"
        3, 4, 5 -> "SPRING"
        6, 7, 8 -> "SUMMER"
        else -> "FALL"
    }
    return season to now.year
}
