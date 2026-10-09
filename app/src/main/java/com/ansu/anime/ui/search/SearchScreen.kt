@file:OptIn(ExperimentalLayoutApi::class)

package com.ansu.anime.ui.search

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NorthWest
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.util.lerp
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
    // Held by the app rather than the screen, so opening a result and backing out finds everything as it was.
    val session = container.searchSession
    var query by session::query
    var filters by session::filters
    val isGrid by container.appearancePrefs.searchGrid.collectAsStateWithLifecycle()
    var showFilters by remember { mutableStateOf(false) }

    var results by session::results
    // The query and filters that produced [results]; "load more" must page through those, not
    // through whatever has been typed since.
    var activeQuery by session::activeQuery
    var activeFilters by session::activeFilters
    var page by session::page
    var hasNext by session::hasNext
    var isLoading by remember { mutableStateOf(false) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var moreFailed by remember { mutableStateOf(false) }
    var retryTick by remember { mutableIntStateOf(0) }
    val titleLanguage by container.appearancePrefs.titleLanguage.collectAsStateWithLifecycle()

    val focusManager = LocalFocusManager.current
    val gridState = session.gridState
    val listState = session.listState
    // With no text and no filters the screen shows trending anime instead of an empty prompt.
    val isTrending = query.trim().length < 2 && !filters.isActive

    // Restarts on every keystroke or filter change, which cancels the previous search: no stale
    // results. Text shorter than two characters counts as no text; with no text and no filters
    // the query resolves to AniList's trending list (30 titles per page).
    LaunchedEffect(query, filters, retryTick, titleLanguage) {
        val text = query.trim().takeIf { it.length >= 2 }.orEmpty()
        val key = Triple(text, filters, titleLanguage)
        // Coming back to the same search (after a details page, say): keep the results and the scroll position.
        if (retryTick == 0 && session.loadedKey == key) return@LaunchedEffect
        if (text.isNotEmpty()) delay(400) // debounce
        isLoading = true
        failed = false
        moreFailed = false
        val result = container.catalogRepository.search(text, filters, 1)
        results = result?.items.orEmpty().distinctBy { it.resultKey() }
        hasNext = result?.hasNextPage == true
        failed = result == null
        session.loadedKey = if (result == null) null else key
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

    // ---- Search history and the expanding search panel ----
    val history by container.searchHistory.items.collectAsStateWithLifecycle()
    // True while the search bar is open: the dark panel has grown out of it to the screen's edges and shows the history.
    var searching by remember { mutableStateOf(false) }
    val expand = remember { Animatable(0f) }
    LaunchedEffect(searching) {
        if (searching) {
            expand.animateTo(1f, tween(420, easing = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)))
        } else {
            expand.animateTo(0f, tween(320, easing = FastOutSlowInEasing))
        }
    }
    val panelVisible by remember { derivedStateOf { expand.value > 0.001f } }
    // Where the bar sits when closed, and where the area it grows into sits, both in window coordinates.
    var barBounds by remember { mutableStateOf(Rect.Zero) }
    var areaBounds by remember { mutableStateOf(Rect.Zero) }

    fun closeSearch() {
        focusManager.clearFocus()
        searching = false
    }

    // Runs a search for [text] (default: what is typed): remembers it and folds the panel back into the bar.
    fun submit(text: String = query) {
        if (text != query) query = text
        container.searchHistory.add(text)
        closeSearch()
    }
    BackHandler(enabled = searching) { closeSearch() }

    val openResult: (SAnime) -> Unit = { anime ->
        container.searchHistory.add(query)
        onAnimeSelected(anime)
    }

    val backdrop = rememberBackdropState()
    Scaffold(bottomBar = { AppBottomBar(navController, Dest.SEARCH, backdrop) }) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .padding(top = padding.calculateTopPadding())
                .onGloballyPositioned { areaBounds = it.boundsInRoot() },
        ) {
          // Filter chips and results sit under the bar row, which is drawn last so the panel can slide beneath it.
          Column(modifier = Modifier.fillMaxSize().padding(top = SearchBarRowHeight)) {
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
                            SearchGridCard(anime = anime, onClick = { openResult(anime) })
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
                            SearchListItem(anime = anime, onClick = { openResult(anime) })
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

          if (panelVisible) {
              SearchPanel(
                  expand = { expand.value },
                  barBounds = { barBounds },
                  areaBounds = { areaBounds },
                  history = history,
                  filter = query.trim(),
                  bottomPadding = padding.calculateBottomPadding(),
                  onPick = { submit(it) },
                  onFill = { query = it },
                  onRemove = container.searchHistory::remove,
                  onClearAll = container.searchHistory::clear,
              )
          }

          Row(
              modifier = Modifier
                  .fillMaxWidth()
                  .height(SearchBarRowHeight)
                  .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 10.dp),
              verticalAlignment = Alignment.CenterVertically,
          ) {
              SearchField(
                  value = query,
                  onValueChange = { query = it },
                  onClear = { query = "" },
                  onSearch = { submit() },
                  searching = searching,
                  onBack = { closeSearch() },
                  onFocused = { searching = true },
                  modifier = Modifier
                      .weight(1f)
                      .height(52.dp)
                      .onGloballyPositioned { if (!searching && expand.value == 0f) barBounds = it.boundsInRoot() },
              )
              // While the bar is open it takes the whole row; the two buttons fold away and come back with it.
              AnimatedVisibility(
                  visible = !searching,
                  enter = fadeIn() + expandHorizontally(),
                  exit = fadeOut() + shrinkHorizontally(),
              ) {
                  // The icon shows the view you will switch TO: a grid while in list view, and vice versa.
                  GlassIconButton(
                      icon = if (isGrid) Icons.AutoMirrored.Filled.ViewList else Icons.Filled.GridView,
                      contentDescription = if (isGrid) "Switch to list view" else "Switch to grid view",
                      onClick = { container.appearancePrefs.setSearchGrid(!isGrid) },
                      modifier = Modifier.padding(start = 10.dp),
                  )
              }
              AnimatedVisibility(
                  visible = !searching,
                  enter = fadeIn() + expandHorizontally(),
                  exit = fadeOut() + shrinkHorizontally(),
              ) {
                  GlassIconButton(
                      icon = Icons.Filled.Tune,
                      contentDescription = "Filters",
                      badgeCount = filters.activeCount,
                      onClick = {
                          focusManager.clearFocus()
                          showFilters = true
                      },
                      modifier = Modifier.padding(start = 10.dp),
                  )
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
    searching: Boolean,
    onBack: () -> Unit,
    onFocused: () -> Unit,
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
        modifier = modifier.onFocusChanged { if (it.isFocused) onFocused() },
        decorationBox = { innerTextField ->
            FrostedGlassCard(modifier = Modifier.fillMaxSize(), shape = CircleShape, tintAlpha = 0.55f) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(start = 8.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // The magnifier turns into a back arrow while the bar is open.
                    Crossfade(targetState = searching, label = "searchLeadingIcon") { open ->
                        if (open) {
                            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Close search",
                                    tint = AnsuColors.TextPrimary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        } else {
                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = AnsuColors.TextSecondary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        }
                    }
                    Box(
                        modifier = Modifier.weight(1f).padding(start = 2.dp, end = 10.dp),
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

/** The height of the bar row: 12dp above the 52dp bar and 10dp below it. */
private val SearchBarRowHeight = 74.dp

/** Clips whatever it is applied to to [rect] with rounded corners, so a full-size panel can be revealed from a small one. */
private class RevealShape(private val rect: Rect, private val radius: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(RoundRect(rect, CornerRadius(radius)))
}

/**
 * The dark panel that grows out of the search bar to the edges of the screen, with the search history inside.
 * It is laid out at full size the whole time and only the clip grows (from the bar's rectangle and rounded corners
 * to the whole area with square corners), so the list never reflows mid-animation; it fades in as the panel opens.
 */
@Composable
private fun SearchPanel(
    expand: () -> Float,
    barBounds: () -> Rect,
    areaBounds: () -> Rect,
    history: List<String>,
    filter: String,
    bottomPadding: Dp,
    onPick: (String) -> Unit,
    onFill: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClearAll: () -> Unit,
) {
    // While something is typed the history narrows to the matching entries.
    val shown = if (filter.isEmpty()) history else history.filter { it.contains(filter, ignoreCase = true) }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = expand()
                val area = areaBounds()
                val from = barBounds().translate(-area.topLeft)
                val rect = Rect(
                    left = lerp(from.left, 0f, p),
                    top = lerp(from.top, 0f, p),
                    right = lerp(from.right, size.width, p),
                    bottom = lerp(from.bottom, size.height, p),
                )
                shape = RevealShape(rect, lerp(26.dp.toPx(), 0f, p))
                clip = true
            }
            .drawBehind {
                // Fully dark almost at once, so the see-through bar never shows a dark patch under it at the start.
                drawRect(AnsuColors.Background.copy(alpha = (expand() * 8f).coerceAtMost(1f)))
            }
            // Taps on the empty part of the panel must not reach the results underneath.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .graphicsLayer {
                    val p = expand()
                    alpha = ((p - 0.25f) / 0.6f).coerceIn(0f, 1f)
                    translationY = (1f - p) * 24.dp.toPx()
                },
            contentPadding = PaddingValues(top = SearchBarRowHeight + 4.dp, bottom = 12.dp + bottomPadding),
        ) {
            if (shown.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = if (history.isEmpty()) "Your searches will show up here." else "No matching searches.",
                        color = AnsuColors.TextTertiary,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                item(key = "header") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Search history",
                            style = MaterialTheme.typography.titleSmall,
                            color = AnsuColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (filter.isEmpty()) {
                            TextButton(onClick = onClearAll) { Text("Clear all", color = AnsuColors.TextSecondary) }
                        }
                    }
                }
                items(shown, key = { it }) { entry ->
                    SearchHistoryRow(
                        text = entry,
                        onPick = { onPick(entry) },
                        onFill = { onFill(entry) },
                        onRemove = { onRemove(entry) },
                    )
                }
            }
        }
    }
}

/** One remembered search: tap to run it, the arrow to copy it into the bar for editing, the cross to forget it. */
@Composable
private fun SearchHistoryRow(text: String, onPick: () -> Unit, onFill: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPick)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.History,
            contentDescription = null,
            tint = AnsuColors.TextTertiary,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = text,
            color = AnsuColors.TextPrimary,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f).padding(horizontal = 16.dp, vertical = 14.dp),
        )
        IconButton(onClick = onRemove, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Remove from history",
                tint = AnsuColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
        IconButton(onClick = onFill, modifier = Modifier.size(40.dp)) {
            Icon(
                imageVector = Icons.Filled.NorthWest,
                contentDescription = "Use in search bar",
                tint = AnsuColors.TextSecondary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
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
