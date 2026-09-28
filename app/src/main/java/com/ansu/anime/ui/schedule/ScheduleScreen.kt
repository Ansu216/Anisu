package com.ansu.anime.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AppBottomBar
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class ScheduleTab { SCHEDULE, NEWS }

private val weekDayLabels: List<String> =
    listOf(
        DayOfWeek.MONDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.FRIDAY,
        DayOfWeek.SATURDAY,
        DayOfWeek.SUNDAY,
    ).map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }

/**
 * Schedule + News, sharing one screen: the pill toggle at the bottom switches
 * between AniList's real airing schedule (grouped by weekday) and the news feed.
 */
@Composable
fun ScheduleScreen(
    container: AppContainer,
    navController: NavHostController,
    onEntrySelected: (ScheduleEntry) -> Unit = {},
) {
    val viewModel: ScheduleViewModel = viewModel(
        factory = viewModelFactory { initializer { ScheduleViewModel(container.aniListRepository) } },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(ScheduleTab.NEWS) }
    var selectedDay by remember {
        mutableStateOf(LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()))
    }

    Scaffold(
        containerColor = AnsuColors.Background,
        bottomBar = { AppBottomBar(navController, Dest.SCHEDULE) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AnsuColors.Background),
        ) {
            Text(
                text = if (tab == ScheduleTab.NEWS) "Anime News" else "Schedule",
                color = AnsuColors.TextPrimary,
                fontSize = 30.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
            )

            Box(modifier = Modifier.weight(1f)) {
                if (state.isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AnsuColors.Accent)
                    }
                } else {
                    when (tab) {
                        ScheduleTab.NEWS -> NewsList(articles = state.news)
                        ScheduleTab.SCHEDULE -> ScheduleList(
                            entries = state.schedule,
                            selectedDay = selectedDay,
                            onSelectDay = { selectedDay = it },
                            onClick = onEntrySelected,
                        )
                    }
                }

                SubTabToggle(
                    selected = tab,
                    onSelect = { tab = it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp),
                )
            }
        }
    }
}

@Composable
private fun SubTabToggle(
    selected: ScheduleTab,
    onSelect: (ScheduleTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    FrostedGlassCard(
        modifier = modifier
            .width(210.dp)
            .height(46.dp),
        shape = RoundedCornerShape(23.dp),
        tintAlpha = 0.5f,
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
            ToggleSegment(
                text = "Schedule",
                isSelected = selected == ScheduleTab.SCHEDULE,
                onClick = { onSelect(ScheduleTab.SCHEDULE) },
                modifier = Modifier.weight(1f),
            )
            ToggleSegment(
                text = "News",
                isSelected = selected == ScheduleTab.NEWS,
                onClick = { onSelect(ScheduleTab.NEWS) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ToggleSegment(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(RoundedCornerShape(19.dp))
            .background(if (isSelected) AnsuColors.Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) AnsuColors.Background else AnsuColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun NewsList(articles: List<NewsArticle>) {
    if (articles.isEmpty()) {
        EmptyState(text = "No news yet — check back soon.")
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 72.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(articles, key = { it.id }) { article ->
            NewsCard(article = article)
        }
    }
}

@Composable
private fun NewsCard(article: NewsArticle) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = article.title,
                color = AnsuColors.TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 21.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (article.snippet.isNotBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = article.snippet,
                    color = AnsuColors.TextSecondary,
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = article.category,
                    color = AnsuColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = article.dateDisplay,
                    color = AnsuColors.TextTertiary,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ScheduleList(
    entries: List<ScheduleEntry>,
    selectedDay: String,
    onSelectDay: (String) -> Unit,
    onClick: (ScheduleEntry) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        DayChipRow(selectedDay = selectedDay, onSelectDay = onSelectDay)

        val dayEntries = entries.filter { it.dayOfWeek == selectedDay }
        if (dayEntries.isEmpty()) {
            EmptyState(text = "Nothing airing on $selectedDay.")
        } else {
            LazyColumn(
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 72.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(dayEntries, key = { "${it.animeId}-${it.episodeNumber}" }) { entry ->
                    ScheduleCard(entry = entry, onClick = { onClick(entry) })
                }
            }
        }
    }
}

@Composable
private fun DayChipRow(selectedDay: String, onSelectDay: (String) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(weekDayLabels) { day ->
            val isSelected = day == selectedDay
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isSelected) AnsuColors.Accent else AnsuColors.SurfaceGlassBase)
                    .clickable { onSelectDay(day) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = day.take(3).uppercase(),
                    color = if (isSelected) AnsuColors.Background else AnsuColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ScheduleCard(entry: ScheduleEntry, onClick: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(10.dp)),
            ) {
                if (entry.imageUrl != null) {
                    AsyncImage(
                        model = entry.imageUrl,
                        contentDescription = entry.animeTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Box(modifier = Modifier.fillMaxSize().background(AnsuColors.BackgroundElevated))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle,
                    color = AnsuColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Episode ${entry.episodeNumber}",
                    color = AnsuColors.TextTertiary,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = entry.airTimeLabel,
                color = AnsuColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = AnsuColors.TextTertiary, fontSize = 14.sp)
    }
}
