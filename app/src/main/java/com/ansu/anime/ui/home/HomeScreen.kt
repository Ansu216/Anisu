package com.ansu.anime.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ansu.anime.data.repository.logoFor
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListFeed
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.ContinueWatchingRow
import com.ansu.anime.ui.components.HeroCarousel
import com.ansu.anime.ui.components.PagedPosterRow
import com.ansu.anime.ui.components.PosterRow
import com.ansu.anime.ui.components.posterTransitionOrigin
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
                    container.appearancePrefs.titleLanguage,
                )
            }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()
    // Stable until the language changes, so the carousel re-resolves its logos exactly then.
    val logoLookup: suspend (Int) -> String? = remember(titleLanguage) {
        { id -> container.artworkRepository.get(id).logoFor(titleLanguage) }
    }
    val trending = state.feed(AniListFeed.TRENDING_NOW).items
    // Every way into a title goes through here, so one tap line is logged instead of five.
    val selectAnime: (SAnime) -> Unit = { anime ->
        container.diagnostics.log(LogCategory.CLICK, "Anime opened: ${anime.title}")
        onAnimeSelected(anime)
    }

    Scaffold(
        containerColor = AnsuColors.Background,
        bottomBar = { AppBottomBar(navController, Dest.HOME) },
    ) { padding ->
        if (state.isLoading && trending.isEmpty() && state.shelves.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
                    .background(AnsuColors.Background),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = AnsuColors.Accent, modifier = Modifier.padding(top = 80.dp))
            }
            return@Scaffold
        }

        LazyColumn(
            // No top padding: the hero banner runs behind the status bar, edge to edge.
            modifier = Modifier
                .fillMaxSize()
                .background(AnsuColors.Background),
            // Content runs behind the floating bar; the padding lets the last row scroll clear of it.
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 24.dp),
        ) {
            // The hero mirrors what's airing/trending right now; if AniList is
            // unreachable it falls back to the first catalogue shelf.
            val heroSource = trending.ifEmpty { state.shelves.firstOrNull()?.items.orEmpty() }
            if (heroSource.isNotEmpty()) {
                item {
                    HeroCarousel(
                        items = heroSource,
                        onClick = selectAnime,
                        logoFor = logoLookup,
                        onToggleFavourite = { anime ->
                            anime.anilistId?.let { id ->
                                scope.launch {
                                    if (container.aniListRepository.isLoggedIn.value) {
                                        container.aniListRepository.toggleFavourite(id)
                                    } else {
                                        // Signed out: keep the favourite on this device.
                                        container.localListRepository.toggleFavourite(anime)
                                    }
                                }
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

            item(key = "feed:${AniListFeed.TRENDING_NOW.name}") {
                FeedShelf(
                    row = state.feed(AniListFeed.TRENDING_NOW),
                    onLoadMore = { viewModel.loadMore(AniListFeed.TRENDING_NOW) },
                    onClick = selectAnime,
                )
            }

            // Every other endless row loads its first page when it scrolls into view.
            items(
                items = state.feeds.filter { it.feed != AniListFeed.TRENDING_NOW },
                key = { "feed:${it.feed.name}" },
            ) { row ->
                FeedShelf(
                    row = row,
                    onLoadMore = { viewModel.loadMore(row.feed) },
                    onClick = selectAnime,
                )
            }

            // Two shelves can share a title (the same addon serving both series and movies, or an
            // extension and an addon with the same name), so the key is the title plus its position.
            itemsIndexed(state.shelves, key = { index, shelf -> "${shelf.title}#$index" }) { _, shelf ->
                Column {
                    ShelfHeader(shelf.title)
                    PosterRow(items = shelf.items, onClick = selectAnime)
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

/**
 * One endless catalogue row: header, a placeholder until the first page arrives, then
 * a [PagedPosterRow] that keeps fetching ten titles at a time as it is scrolled.
 * Hidden entirely if the feed loaded but had nothing in it.
 */
@Composable
private fun FeedShelf(
    row: FeedState,
    onLoadMore: () -> Unit,
    onClick: (SAnime) -> Unit,
) {
    // Runs when the row first composes (i.e. scrolls into view) and again if it
    // leaves and re-enters composition after a failed first load.
    LaunchedEffect(row.feed) { if (!row.hasLoaded) onLoadMore() }

    if (row.hasLoaded && row.items.isEmpty()) return

    Column {
        ShelfHeader(row.feed.title)
        if (row.items.isEmpty()) {
            if (row.loadFailed) {
                Text(
                    text = "Couldn't load. Tap to retry.",
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier
                        .padding(horizontal = 20.dp)
                        .height(195.dp)
                        .clickable(onClick = onLoadMore),
                )
            } else {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(2f / 3f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AnsuColors.BackgroundElevated),
                        )
                    }
                }
            }
        } else {
            PagedPosterRow(
                items = row.items,
                hasMore = row.hasNextPage,
                isLoadingMore = row.isLoading,
                loadFailed = row.loadFailed,
                onLoadMore = onLoadMore,
                onClick = onClick,
            )
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
            .posterTransitionOrigin(anime.posterUrl, 12.dp)
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
