package com.ansu.anime.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.core.model.Shelf
import com.ansu.anime.data.db.ContinueWatchingEntity
import com.ansu.anime.data.repository.CatalogRepository
import com.ansu.anime.data.repository.ContinueWatchingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val continueWatching: List<ContinueWatchingEntity> = emptyList(),
    val shelves: List<Shelf> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val catalogRepository: CatalogRepository,
    private val continueWatchingRepository: ContinueWatchingRepository,
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
            val shelves = runCatching { catalogRepository.buildHomeShelves() }
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                shelves = shelves.getOrDefault(emptyList()),
                error = shelves.exceptionOrNull()?.message,
            )
        }
    }
}
