package com.ansu.anime.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.ContinueWatchingRow
import com.ansu.anime.ui.components.HeroCarousel
import com.ansu.anime.ui.components.AnisuBottomBar
import com.ansu.anime.ui.components.PosterRow
import com.ansu.anime.ui.components.ShelfHeader
import com.ansu.anime.ui.navigation.Dest

@Composable
fun HomeScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HomeViewModel(container.catalogRepository, container.continueWatchingRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Anisu", color = Color.White) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        bottomBar = { AnisuBottomBar(navController, Dest.HOME) },
    ) { padding ->
        if (state.isLoading && state.shelves.isEmpty()) {
            Column(modifier = Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(modifier = Modifier.padding(top = 80.dp))
            }
            return@Scaffold
        }

        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            val heroSource = state.shelves.firstOrNull()?.items.orEmpty()
            if (heroSource.isNotEmpty()) {
                item { HeroCarousel(items = heroSource, onClick = onAnimeSelected) }
            }

            if (state.continueWatching.isNotEmpty()) {
                item { ShelfHeader("Continue Watching") }
                item {
                    ContinueWatchingRow(
                        entries = state.continueWatching,
                        onClick = { entry ->
                            val origin = when {
                                entry.originAddonId != null && entry.originAddonBaseUrl != null ->
                                    com.ansu.anime.core.model.MediaOrigin.Addon(
                                        addonId = entry.originAddonId,
                                        addonBaseUrl = entry.originAddonBaseUrl,
                                        type = "series",
                                        stremioId = entry.anilistId.toString(),
                                    )
                                else -> com.ansu.anime.core.model.MediaOrigin.Extension(
                                    sourceId = entry.originExtensionSourceId ?: 1L,
                                    urlPath = entry.anilistId.toString(),
                                )
                            }
                            val anime = SAnime(
                                id = entry.anilistId.toString(),
                                title = entry.title,
                                posterUrl = entry.posterUrl,
                                bannerUrl = entry.bannerUrl,
                                anilistId = entry.anilistId,
                                origin = origin,
                            )
                            val episode = com.ansu.anime.core.model.SEpisode(
                                id = entry.episodeId,
                                name = entry.episodeName,
                                episodeNumber = entry.episodeNumber,
                            )
                            container.selectionHolder.selectAnime(anime)
                            container.selectionHolder.selectEpisode(episode)
                            navController.navigate(Dest.PLAYER)
                        },
                    )
                }
            }

            items(state.shelves, key = { it.title }) { shelf ->
                Column {
                    ShelfHeader(shelf.title)
                    PosterRow(items = shelf.items, onClick = onAnimeSelected)
                }
            }

            state.error?.let { error ->
                item {
                    Text(
                        text = "Some sources didn't load: $error",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
        }
    }
}
