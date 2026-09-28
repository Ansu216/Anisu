package com.ansu.anime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.Shelf
import com.ansu.anime.data.db.ContinueWatchingEntity
import com.ansu.anime.data.repository.CatalogRepository
import com.ansu.anime.data.repository.ContinueWatchingRepository
import com.ansu.anime.data.repository.toSAnime
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import java.time.LocalDate

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<ContinueWatchingEntity> = emptyList(),
    val trending: List<SAnime> = emptyList(),
    val topPicks: List<SAnime> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
    private val aniListRepository: AniListRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState

    init {
        continueWatchingRepository.entries
            .onEach { list -> _uiState.value = _uiState.value.copy(continueWatching = list) }
            .launchIn(viewModelScope)
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            // The catalogue feed, AniList trending and the season picks are three
            // independent network calls, so they are fetched in parallel.
            val shelves = async { runCatching { catalogRepository.buildHomeShelves() } }
            val trending = async { aniListRepository.getTrending() }
            val topPicks = async {
                val (season, seasonYear) = currentSeason()
                aniListRepository.getTopThisSeason(season, seasonYear)
            }

            val shelfResult = shelves.await()
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                shelves = shelfResult.getOrDefault(emptyList()),
                error = shelfResult.exceptionOrNull()?.message,
                trending = trending.await().map { it.toSAnime() },
                topPicks = topPicks.await().map { it.toSAnime() },
            )
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
