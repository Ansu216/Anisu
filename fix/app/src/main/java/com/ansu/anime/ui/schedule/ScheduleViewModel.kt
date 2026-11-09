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
    val newsLoading: Boolean = false,
)

class ScheduleViewModel(
    private val aniListRepository: AniListRepository,
    private val newsRepository: NewsRepository,
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
            _uiState.value = _uiState.value.copy(isLoading = false, schedule = schedule)
        }
        refreshNews(force = true)
    }

    /** Loads the news; without [force] it is a no-op while the copy in memory is still fresh. */
    fun refreshNews(force: Boolean = false) {
        if (_uiState.value.newsLoading && !force) return
        if (!force && newsRepository.isFresh() && _uiState.value.news.isNotEmpty()) return
        // Marked loading before the coroutine starts, so a second caller in the same moment sees it and backs off.
        _uiState.value = _uiState.value.copy(newsLoading = true)
        viewModelScope.launch {
            val news = newsRepository.getNews(force)
            _uiState.value = _uiState.value.copy(newsLoading = false, news = news)
        }
    }
}
