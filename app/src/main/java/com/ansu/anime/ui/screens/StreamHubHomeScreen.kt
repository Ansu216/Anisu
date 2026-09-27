package com.ansu.anime.ui.screens


import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.anilist.AniListApi
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.ui.components.BottomScrim
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.theme.StreamHubColors

data class TitleCard(
    val id: String,
    val name: String,
    val subtitle: String = "",
    val progress: Float? = null,
    val coverImageUrl: String? = null,
    val isLiked: Boolean = false,
)

fun AniListMedia.toTitleCard(): TitleCard = TitleCard(
    id = id.toString(),
    name = displayTitle,
    subtitle = listOfNotNull(format?.let { when (it) { "TV" -> "TV"; "MOVIE" -> "Movie"; else -> it } }, episodes?.let { "$it eps" }).joinToString(" • "),
    coverImageUrl = coverImage?.extraLarge ?: coverImage?.large ?: bannerImage,
)

@Composable
fun StreamHubHomeScreen(
    continueWatching: List<TitleCard> = emptyList(),
    onViewDetails: (TitleCard) -> Unit = {},
) {
    var trending by remember { mutableStateOf<List<TitleCard>>(emptyList()) }
    var topPicks by remember { mutableStateOf<List<TitleCard>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var reloadTrigger by remember { mutableStateOf(0) }
    val likedIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(reloadTrigger) {
        isLoading = true
        errorMessage = null
        try {
            val now = java.util.Calendar.getInstance()
            val month = now.get(java.util.Calendar.MONTH) + 1
            val season = when (month) {
                12, 1, 2 -> "WINTER"
                3, 4, 5 -> "SPRING"
                6, 7, 8 -> "SUMMER"
                else -> "FALL"
            }
            val year = now.get(java.util.Calendar.YEAR)
            trending = AniListApi.getTrending().map { it.toTitleCard() }
            topPicks = AniListApi.getTopThisSeason(season, year).map { it.toTitleCard() }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            errorMessage = "Couldn't reach AniList."
        } finally {
            isLoading = false
        }
    }

    fun withLike(list: List<TitleCard>) = list.map { it.copy(isLiked = likedIds[it.id] == true) }

    Box(modifier = Modifier.fillMaxSize().background(StreamHubColors.Background)) {
        when {
            isLoading && trending.isEmpty() -> HomeLoadingState()
            errorMessage != null && trending.isEmpty() -> HomeErrorState(
                message = errorMessage.orEmpty(),
                onRetry = { reloadTrigger++ },
            )
            else -> StreamHubHomeScreenContent(
                heroTitles = withLike(trending.take(5)),
                continueWatching = continueWatching,
                trending = withLike(trending),
                topPicks = withLike(topPicks),
                onViewDetails = onViewDetails,
                onToggleLike = { title -> likedIds[title.id] = likedIds[title.id] != true },
            )
        }
    }
}

@Composable
private fun HomeLoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = StreamHubColors.Accent)
    }
}

@Composable
private fun HomeErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(message, color = StreamHubColors.TextSecondary, fontSize = 14.sp, textAlign = TextAlign.Center)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = StreamHubColors.Accent, contentColor = StreamHubColors.Background),
        ) {
            Text("Retry", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
fun StreamHubHomeScreenContent(
    heroTitles: List<TitleCard>,
    continueWatching: List<TitleCard>,
    trending: List<TitleCard>,
    topPicks: List<TitleCard>,
    onViewDetails: (TitleCard) -> Unit = {},
    onToggleLike: (TitleCard) -> Unit = {},
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StreamHubColors.Background),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 96.dp),
        ) {
            item { TopBar() }
            item { HeroCarouselSection(titles = heroTitles, onViewDetails = onViewDetails, onToggleLike = onToggleLike) }
            if (continueWatching.isNotEmpty()) {
                item { SectionHeader("Continue Watching") }
                item { MediaRow(items = continueWatching, cardWidth = 220.dp, cardHeight = 124.dp, onClick = onViewDetails) }
            }
            item { SectionHeader("Trending Now") }
            item { MediaRow(items = trending, cardWidth = 140.dp, cardHeight = 210.dp, onClick = onViewDetails) }
            item { SectionHeader("Top Picks For You") }
            item { TopPicksGrid(items = topPicks, onClick = onViewDetails) }
        }
    }
}

@Composable
private fun TopBar() {
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
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "StreamHub",
                color = StreamHubColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.ExtraBold,
            )
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = "Search",
                tint = StreamHubColors.TextPrimary,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HeroCarouselSection(
    titles: List<TitleCard>,
    onViewDetails: (TitleCard) -> Unit,
    onToggleLike: (TitleCard) -> Unit,
) {
    if (titles.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { titles.size })
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(460.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val title = titles[page]
                Box(modifier = Modifier.fillMaxSize()) {
                    if (title.coverImageUrl != null) {
                        AsyncImage(
                            model = title.coverImageUrl,
                            contentDescription = title.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.verticalGradient(
                                        listOf(Color(0xFF2A2D3A), StreamHubColors.Background),
                                    ),
                                ),
                        )
                    }
                    BottomScrim(modifier = Modifier.fillMaxSize())
                }
            }
            val current = titles[pagerState.currentPage]
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = current.name,
                    color = StreamHubColors.TextPrimary,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (current.subtitle.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = current.subtitle,
                        color = StreamHubColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = { onViewDetails(current) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StreamHubColors.Accent,
                            contentColor = StreamHubColors.Background,
                        ),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    ) {
                        Text("View Details", color = StreamHubColors.Background, fontWeight = FontWeight.Bold)
                    }
                    FrostedGlassCard(
                        modifier = Modifier.size(48.dp),
                        shape = CircleShape,
                        tintAlpha = 0.5f,
                    ) {
                        IconButton(onClick = { onToggleLike(current) }, modifier = Modifier.fillMaxSize()) {
                            Icon(
                                imageVector = if (current.isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (current.isLiked) "Unlike" else "Like",
                                tint = StreamHubColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }
        if (titles.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                titles.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (selected) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) StreamHubColors.Accent else StreamHubColors.TextTertiary),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        color = StreamHubColors.TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp),
    )
}

@Composable
private fun MediaRow(
    items: List<TitleCard>,
    cardWidth: androidx.compose.ui.unit.Dp,
    cardHeight: androidx.compose.ui.unit.Dp,
    onClick: (TitleCard) -> Unit = {},
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            FrostedGlassCard(
                modifier = Modifier
                    .width(cardWidth)
                    .height(cardHeight)
                    .clickable { onClick(item) },
                shape = RoundedCornerShape(14.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.coverImageUrl != null) {
                        AsyncImage(
                            model = item.coverImageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(StreamHubColors.BackgroundElevated),
                        )
                    }
                    item.progress?.let { pct ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .fillMaxWidth(pct.coerceIn(0f, 1f))
                                .height(4.dp)
                                .background(StreamHubColors.Accent),
                        )
                    }
                    Text(
                        text = item.name,
                        color = StreamHubColors.TextPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopPicksGrid(items: List<TitleCard>, onClick: (TitleCard) -> Unit = {}) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 900.dp)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.id }) { item ->
            FrostedGlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(2f / 3f)
                    .clickable { onClick(item) },
                shape = RoundedCornerShape(10.dp),
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (item.coverImageUrl != null) {
                        AsyncImage(
                            model = item.coverImageUrl,
                            contentDescription = item.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(StreamHubColors.BackgroundElevated),
                        )
                    }
                    Text(
                        text = item.name,
                        color = StreamHubColors.TextPrimary,
                        fontSize = 11.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(6.dp),
                    )
                }
            }
        }
    }
}