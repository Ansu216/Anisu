package com.ansu.anime.ui.search

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.ansu.anime.anilist.AniListSearchFilters
import com.ansu.anime.core.model.SAnime

/**
 * Everything the Search tab shows, kept for the life of the app instead of the life of the screen.
 *
 * Opening a result's details removes the Search screen from composition, and plain `remember` state went with it,
 * so backing out landed on an empty search. The screen now reads and writes this holder, so the query, filters,
 * results, paging and scroll position are all still there when it comes back.
 */
class SearchSession {
    var query by mutableStateOf("")
    var filters by mutableStateOf(AniListSearchFilters())
    var results by mutableStateOf<List<SAnime>>(emptyList())

    /** The query and filters that produced [results]; "load more" pages through those. */
    var activeQuery by mutableStateOf("")
    var activeFilters by mutableStateOf(AniListSearchFilters())
    var page by mutableIntStateOf(1)
    var hasNext by mutableStateOf(false)

    /** What [results] were loaded for. A returning screen that finds the same key keeps them instead of reloading. */
    var loadedKey: Any? = null

    val gridState = LazyGridState()
    val listState = LazyListState()

    /**
     * Every title the search has shown this session, keyed by title, oldest first. The typo-tolerant suggestions are
     * ranked from these, so a title that came up once still counts when a later search is mistyped.
     */
    private val titlePool = LinkedHashMap<String, SAnime>()

    /** Changes whenever [titlePool] does, so the screen knows to rank its suggestions again. */
    var poolVersion by mutableIntStateOf(0)
        private set

    fun remember(items: List<SAnime>) {
        if (items.isEmpty()) return
        for (anime in items) {
            val key = anime.title.lowercase()
            titlePool.remove(key)
            titlePool[key] = anime
        }
        while (titlePool.size > MAX_POOL) titlePool.remove(titlePool.keys.first())
        poolVersion++
    }

    fun pool(): List<SAnime> = titlePool.values.toList()

    companion object {
        private const val MAX_POOL = 400
    }
}
