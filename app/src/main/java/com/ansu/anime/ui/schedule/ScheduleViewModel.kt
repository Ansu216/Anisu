package com.ansu.anime.ui.schedule

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ansu.anime.anilist.AniListRepository
import com.ansu.anime.data.news.NewsRepository
import com.ansu.anime.data.prefs.TitleLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ScheduleUiState(
    val isLoading: Boolean = true,
    val schedule: List<ScheduleEntry> = emptyList(),
    val news: List<NewsArticle> = emptyList(),
)

class ScheduleViewModel(
    private val aniListRepository: AniListRepository,
    titleLanguage: StateFlow<TitleLanguage>,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScheduleUiState())
    val uiState: StateFlow<ScheduleUiState> = _uiState

    init {
        refresh()
        titleLanguage.drop(1)
            .onEach { refresh() }
            .launchIn(viewModelScope)
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val (from, to) = scheduleBounds()
            val schedule = aniListRepository.weeklySchedule(from, to).map { it.toScheduleEntry() }
            val news = NewsRepository.getNews()
            _uiState.value = ScheduleUiState(isLoading = false, schedule = schedule, news = news)
        }
    }
}
