package com.ansu.anime.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.ContinueWatchingRow
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.HeroCarousel
import com.ansu.anime.ui.components.PosterRow
import com.ansu.anime.ui.components.ShelfHeader
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    container.catalogRepository,
                    container.continueWatchingRepository,
                    container.aniListRepository,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    Scaffold(
        containerColor = AnsuColors.Background,
        bottomBar = { AppBottomBar(navController, Dest.HOME) },
    ) { padding ->
        if (state.isLoading && state.trending.isEmpty() && state.shelves.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .background(AnsuColors.Background),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = AnsuColors.Accent, modifier = Modifier.padding(top = 80.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AnsuColors.Background),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item { HomeTopBar(onSearch = { navController.navigate(Dest.SEARCH) }) }

            // The hero mirrors what's airing/trending right now; if AniList is
            // unreachable it falls back to the first catalogue shelf.
            val heroSource = state.trending.ifEmpty { state.shelves.firstOrNull()?.items.orEmpty() }
            if (heroSource.isNotEmpty()) {
                item {
                    HeroCarousel(
                        items = heroSource,
                        onClick = onAnimeSelected,
                        onToggleFavourite = { anime ->
                            anime.anilistId?.let { id ->
                                scope.launch { container.aniListRepository.toggleFavourite(id) }
                            }
                        },
                    )
                }
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

            if (state.trending.isNotEmpty()) {
                item { ShelfHeader("Trending Now") }
                item { PosterRow(items = state.trending, onClick = onAnimeSelected) }
            }

            if (state.topPicks.isNotEmpty()) {
                item { ShelfHeader("Top Picks For You") }
                item { TopPicksGrid(items = state.topPicks.take(9), onClick = onAnimeSelected) }
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

/** Frosted header holding the app name and a shortcut to Search. */
@Composable
private fun HomeTopBar(onSearch: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 12.dp),
        shape = RoundedCornerShape(20.dp),
        tintAlpha = 0.35f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Ansu",
                color = AnsuColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            IconButton(onClick = onSearch) {
                Icon(
                    imageVector = Icons.Filled.Search,
                    contentDescription = "Search",
                    tint = AnsuColors.TextPrimary,
                )
            }
        }
    }
}

/**
 * A 3-column poster grid. Deliberately not a LazyVerticalGrid: this lives inside
 * the home LazyColumn, and nesting two vertical lazy layouts would need an
 * explicit height (and would break scroll chaining).
 */
@Composable
private fun TopPicksGrid(
    items: List<SAnime>,
    onClick: (SAnime) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { anime ->
                    TopPickCard(
                        anime = anime,
                        onClick = { onClick(anime) },
                        modifier = Modifier.weight(1f),
                    )
                }
                repeat(3 - row.size) { Spacer(modifier = Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun TopPickCard(anime: SAnime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .aspectRatio(2f / 3f)
            .clip(RoundedCornerShape(12.dp))
            .background(AnsuColors.BackgroundElevated)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}
