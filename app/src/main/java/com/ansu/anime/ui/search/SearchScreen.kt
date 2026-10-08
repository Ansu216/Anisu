@file:OptIn(ExperimentalLayoutApi::class)

package com.ansu.anime.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListSearchFilters
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.util.ageRatingFor
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AgeRatingChip
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.backdropSource
import com.ansu.anime.ui.components.rememberBackdropState
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.posterTransitionOrigin
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.delay

private fun SAnime.resultKey(): String = id + origin.hashCode()

@Composable
fun SearchScreen(
    container: AppContainer,
    navController: NavHostController,
    onAnimeSelected: (SAnime) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(AniListSearchFilters()) }
    val isGrid by container.appearancePrefs.searchGrid.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    var results by remember { mutableStateOf<List<SAnime>>(emptyList()) }
    // The query and filters that produced [results]; "load more" must page through those, not
    // through whatever has been typed since.
    var activeQuery by remember { mutableStateOf("") }
    var activeFilters by remember { mutableStateOf(AniListSearchFilters()) }
    var page by remember { mutableIntStateOf(1) }
    var hasNext by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val gridState = rememberLazyGridState()
    val listState = rememberLazyListState()
    // With no text and no filters the screen shows trending anime instead of an empty prompt.
    val isTrending = query.trim().length < 2 && !filters.isActive

    // Restarts on every keystroke or filter change, which cancels the previous search: no stale
    // results. Text shorter than two characters counts as no text; with no text and no filters
    // the query resolves to AniList's trending list (30 titles per page).
    LaunchedEffect(query, filters, retryTick, titleLanguage) {
        val text = query.trim().takeIf { it.length >= 2 }.orEmpty()
        if (text.isNotEmpty()) delay(400) // debounce
        isLoading = true
        failed = false
        moreFailed = false
        val result = container.catalogRepository.search(text, filters, 1)
        results = result?.items.orEmpty().distinctBy { it.resultKey() }
        hasNext = result?.hasNextPage == true
        failed = result == null
        activeQuery = text
        activeFilters = filters
        page = 1
        isLoading = false
        gridState.scrollToItem(0)
        listState.scrollToItem(0)
    }

    val nearEnd by remember(isGrid) {
        derivedStateOf {
            if (isGrid) {
                val info = gridState.layoutInfo
                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 4
            } else {
                val info = listState.layoutInfo
                info.totalItemsCount > 0 && (info.visibleItemsInfo.lastOrNull()?.index ?: -1) >= info.totalItemsCount - 4
            }
        }
    }

    // Loads the next page when the user scrolls close to the end. A failed page shows a retry
    // button instead of looping.
    LaunchedEffect(nearEnd, results.size, hasNext, isLoading, moreFailed) {
        if (!nearEnd || !hasNext || isLoading || moreFailed) return@LaunchedEffect
        isLoadingMore = true
        try {
            val next = container.catalogRepository.search(activeQuery, activeFilters, page + 1)
            if (next == null) {
                moreFailed = true
            } else {
                results = (results + next.items).distinctBy { it.resultKey() }
                hasNext = next.hasNextPage
                page += 1
            }
        } finally {
            isLoadingMore = false
        }
    }

    val backdrop = rememberBackdropState()
    Scaffold(bottomBar = { AppBottomBar(navController, Dest.SEARCH, backdrop) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().backdropSource(backdrop).padding(top = padding.calculateTopPadding())) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SearchField(
                    value = query,
                    onValueChange = { query = it },
                    onClear = { query = "" },
                    onSearch = { focusManager.clearFocus() },
                    modifier = Modifier.weight(1f).height(52.dp),
                )
                // The icon shows the view you will switch TO: a grid while in list view, and vice versa.
                GlassIconButton(
                    icon = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                    contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view",
                    onClick = { container.appearancePrefs.setSearchGrid(!isGrid) },
                )
                GlassIconButton(
                    icon = Icons.Filled.Tune,
                    contentDescription = "Filters",
                    badgeCount = filters.activeCount,
                    onClick = {
                        focusManager.clearFocus()
                        showFilters = true
                    },
                )
            }

            val chips = activeFilterChips(filters)
            if (chips.isNotEmpty()) {
                LazyRow(
                    modifier = Modifier.padding(bottom = 6.dp),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(chips) { chip ->
                        RemovableChip(label = chip.label, onRemove = { filters = chip.remove(filters) })
                    }
                    item {
                        TextButton(onClick = { filters = AniListSearchFilters() }) {
                            Text("Clear all", color = AnsuColors.TextSecondary)
                        }
                    }
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val contentPadding = PaddingValues(
                    start = 12.dp,
                    top = 6.dp,
                    end = 12.dp,
                    bottom = 12.dp + padding.calculateBottomPadding(),
                )
                when {
                    isLoading -> CircularProgressIndicator(
                        color = AnsuColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.align(Alignment.Center).size(28.dp),
                    )
                    failed -> MessageState(
                        title = "Couldn't load results",
                        body = "Check your connection and try again.",
                        actionLabel = "Retry",
                        onAction = { retryTick += 1 },
                    )
                    results.isEmpty() -> MessageState(
                        title = "No results found",
                        body = "Try a different title or loosen some filters.",
                    )
                    isGrid -> LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Fixed(3),
                        contentPadding = contentPadding,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                            ResultsHeader(isTrending)
                        }
                        items(results, key = { it.resultKey() }) { anime ->
                            SearchGridCard(anime = anime, onClick = { onAnimeSelected(anime) })
                        }
                        if (hasNext || moreFailed) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                LoadMoreFooter(failed = moreFailed, onRetry = { moreFailed = false })
                            }
                        }
                    }
                    else -> LazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        item(key = "header") { ResultsHeader(isTrending) }
                        items(results, key = { it.resultKey() }) { anime ->
                            SearchListItem(anime = anime, onClick = { onAnimeSelected(anime) })
                        }
                        if (hasNext || moreFailed) {
                            item {
                                LoadMoreFooter(failed = moreFailed, onRetry = { moreFailed = false })
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) {
        SearchFilterSheet(
            filters = filters,
            onApply = {
                filters = it
                showFilters = false
            },
            onDismiss = { showFilters = false },
        )
    }
}

/** Small heading above the results: "Trending now" on the landing state, otherwise "Results". */
@Composable
private fun ResultsHeader(isTrending: Boolean) {
    Text(
        text = if (isTrending) "Trending now" else "Results",
        style = MaterialTheme.typography.titleMedium,
        color = AnsuColors.TextPrimary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
    )
}

/** The pill-shaped glass search field: search icon, hint, and a clear button while there is text. */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = AnsuColors.TextPrimary),
        cursorBrush = SolidColor(AnsuColors.Accent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        modifier = modifier,
        decorationBox = { innerTextField ->
            FrostedGlassCard(modifier = Modifier.fillMaxSize(), shape = CircleShape, tintAlpha = 0.55f) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 16.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = AnsuColors.TextSecondary,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                        contentAlignment = Alignment.CenterStart,
                    ) {
                        if (value.isEmpty()) {
                            Text(
                                text = "Search anime...",
                                style = MaterialTheme.typography.bodyLarge,
                                color = AnsuColors.TextTertiary,
                                maxLines = 1,
                            )
                        }
                        innerTextField()
                    }
                    if (value.isNotEmpty()) {
                        IconButton(onClick = onClear, modifier = Modifier.size(36.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear search",
                                tint = AnsuColors.TextSecondary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
        },
    )
}

/** A rounded-square glass button; [badgeCount] above zero adds a small white count badge. */
@Composable
private fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badgeCount: Int = 0,
) {
    Box(modifier = modifier.size(52.dp)) {
        FrostedGlassCard(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(18.dp), tintAlpha = 0.55f) {
            Box(
                modifier = Modifier.fillMaxSize().clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Icon(imageVector = icon, contentDescription = contentDescription, tint = AnsuColors.TextPrimary)
            }
        }
        if (badgeCount > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = 4.dp, y = (-4).dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(AnsuColors.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = badgeCount.toString(),
                    color = AnsuColors.OnAccent,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun RemovableChip(label: String, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AnsuColors.AccentSoft)
            .clickable(onClick = onRemove)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = AnsuColors.TextPrimary, fontSize = 12.sp, maxLines = 1)
        Icon(
            imageVector = Icons.Filled.Close,
            contentDescription = "Remove $label",
            tint = AnsuColors.TextSecondary,
            modifier = Modifier.padding(start = 6.dp).size(14.dp),
        )
    }
}

/** Grid mode: poster with the age-rating chip top-left and the title over a scrim at the bottom. */
@Composable
private fun SearchGridCard(anime: SAnime, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(2f / 3f)
            .posterTransitionOrigin(anime.posterUrl, 14.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(AnsuColors.BackgroundElevated)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(76.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
        )
        AgeRatingChip(
            rating = anime.ageRating ?: ageRatingFor(anime.genres, isAdult = false),
            modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
        )
        Text(
            text = anime.title,
            color = AnsuColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 8.dp, vertical = 8.dp),
        )
    }
}

/** List mode: poster on the left; name, episodes and year, genre tags and format on the right. */
@Composable
private fun SearchListItem(anime: SAnime, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(AnsuColors.BackgroundElevated)
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .aspectRatio(2f / 3f)
                .posterTransitionOrigin(anime.posterUrl, 12.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(AnsuColors.Background),
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AgeRatingChip(
                rating = anime.ageRating ?: ageRatingFor(anime.genres, isAdult = false),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anime.title,
                style = MaterialTheme.typography.titleSmall,
                color = AnsuColors.TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val episodesText = anime.episodes?.let { if (it == 1) "1 episode" else "$it episodes" }
            val meta = listOfNotNull(episodesText, anime.releaseYear?.toString()).joinToString(" \u2022 ")
            if (meta.isNotEmpty()) {
                Text(
                    text = meta,
                    fontSize = 12.sp,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            if (anime.genres.isNotEmpty()) {
                FlowRow(
                    modifier = Modifier.padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    anime.genres.take(3).forEach { genre -> TagPill(genre) }
                }
            }
            anime.format?.let { format ->
                Text(
                    text = format,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = AnsuColors.TextPrimary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun TagPill(text: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AnsuColors.SurfaceGlassBase.copy(alpha = 0.7f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = AnsuColors.TextSecondary, maxLines = 1)
    }
}

@Composable
private fun LoadMoreFooter(failed: Boolean, onRetry: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), contentAlignment = Alignment.Center) {
        if (failed) {
            TextButton(onClick = onRetry) {
                Text("Couldn't load more. Tap to retry", color = AnsuColors.TextSecondary)
            }
        } else {
            CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(24.dp))
        }
    }
}

/** Centered empty / error / hint state with an optional action button. */
@Composable
private fun MessageState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    showIcon: Boolean = false,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (showIcon) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AnsuColors.TextTertiary,
                modifier = Modifier.size(44.dp).padding(bottom = 4.dp),
            )
        }
        Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary, textAlign = TextAlign.Center)
        Text(
            body,
            style = MaterialTheme.typography.bodyMedium,
            color = AnsuColors.TextTertiary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (actionLabel != null) {
            Button(
                onClick = onAction,
                modifier = Modifier.padding(top = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
            ) {
                Text(actionLabel, fontWeight = FontWeight.Bold)
            }
        }
    }
}
