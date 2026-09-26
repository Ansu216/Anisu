package com.ansu.anime.ui.search

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AnimeCard
import com.ansu.anime.ui.components.AnisuBottomBar
import com.ansu.anime.ui.navigation.Dest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SAnime>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(bottomBar = { AnisuBottomBar(navController, Dest.SEARCH) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TextField(
                value = query,
                onValueChange = { newValue ->
                    query = newValue
                    scope.launch {
                        delay(400) // debounce
                        if (query == newValue && newValue.length >= 2) {
                            isLoading = true
                            results = container.catalogRepository.search(newValue)
                            isLoading = false
                        }
                    }
                },
                placeholder = { Text("Search across all your sources") },
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            )

            when {
                isLoading -> CircularProgressIndicator(modifier = Modifier.padding(16.dp))
                results.isEmpty() && query.length >= 2 -> Text(
                    "No results across your installed sources.",
                    modifier = Modifier.padding(16.dp),
                )
                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 110.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                ) {
                    items(results, key = { it.id + it.origin.hashCode() }) { anime ->
                        AnimeCard(anime = anime, onClick = { onAnimeSelected(anime) }, modifier = Modifier.padding(6.dp))
                    }
                }
            }
        }
    }
}
