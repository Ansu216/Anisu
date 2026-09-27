package com.ansu.anime.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.ansu.anime.data.news.NewsArticle
import com.ansu.anime.data.news.ScheduleEntry
import com.ansu.anime.ui.components.BottomNavBar
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.NavDestination
import com.ansu.anime.ui.theme.StreamHubColors
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

private enum class NewsScheduleTab { SCHEDULE, NEWS }

private val weekDayLabels: List<String> =
    listOf(
        java.time.DayOfWeek.MONDAY,
        java.time.DayOfWeek.TUESDAY,
        java.time.DayOfWeek.WEDNESDAY,
        java.time.DayOfWeek.THURSDAY,
        java.time.DayOfWeek.FRIDAY,
        java.time.DayOfWeek.SATURDAY,
        java.time.DayOfWeek.SUNDAY,
    ).map { it.getDisplayName(TextStyle.FULL, Locale.getDefault()) }

@Composable
fun NewsScheduleScreen(
    news: List<NewsArticle>,
    schedule: List<ScheduleEntry>,
    onSelectNav: (NavDestination) -> Unit,
    onArticleClick: (NewsArticle) -> Unit = {},
    onScheduleEntryClick: (ScheduleEntry) -> Unit = {},
) {
    var subTab by remember { mutableStateOf(NewsScheduleTab.NEWS) }
    var selectedDay by remember {
        mutableStateOf(
            LocalDate.now().dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(StreamHubColors.Background),
    ) {
        Text(
            text = if (subTab == NewsScheduleTab.NEWS) "Anime News" else "Schedule",
            color = StreamHubColors.TextPrimary,
            fontSize = 30.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(start = 20.dp, top = 20.dp, bottom = 8.dp),
        )

        Box(modifier = Modifier.weight(1f)) {
            when (subTab) {
                NewsScheduleTab.NEWS -> NewsList(articles = news, onClick = onArticleClick)
                NewsScheduleTab.SCHEDULE -> ScheduleList(
                    entries = schedule,
                    selectedDay = selectedDay,
                    onSelectDay = { selectedDay = it },
                    onClick = onScheduleEntryClick,
                )
            }

            SubTabToggle(
                selected = subTab,
                onSelect = { subTab = it },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 16.dp),
            )
        }

        BottomNavBar(selected = NavDestination.Schedule, onSelect = onSelectNav)
    }
}

@Composable
private fun SubTabToggle(
    selected: NewsScheduleTab,
    onSelect: (NewsScheduleTab) -> Unit,
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
                isSelected = selected == NewsScheduleTab.SCHEDULE,
                onClick = { onSelect(NewsScheduleTab.SCHEDULE) },
                modifier = Modifier.weight(1f),
            )
            ToggleSegment(
                text = "News",
                isSelected = selected == NewsScheduleTab.NEWS,
                onClick = { onSelect(NewsScheduleTab.NEWS) },
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
            .background(if (isSelected) StreamHubColors.Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) StreamHubColors.Background else StreamHubColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun NewsList(articles: List<NewsArticle>, onClick: (NewsArticle) -> Unit) {
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
            NewsCard(article = article, onClick = { onClick(article) })
        }
    }
}

@Composable
private fun NewsCard(article: NewsArticle, onClick: () -> Unit) {
    FrostedGlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = article.title,
                color = StreamHubColors.TextPrimary,
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
                    color = StreamHubColors.TextSecondary,
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
                    color = StreamHubColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = article.dateDisplay,
                    color = StreamHubColors.TextTertiary,
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
                items(dayEntries, key = { it.id }) { entry ->
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
                    .background(if (isSelected) StreamHubColors.Accent else StreamHubColors.SurfaceGlassBase)
                    .clickable { onSelectDay(day) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Text(
                    text = day.take(3).uppercase(),
                    color = if (isSelected) StreamHubColors.Background else StreamHubColors.TextSecondary,
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
                    Box(modifier = Modifier.fillMaxSize().background(StreamHubColors.BackgroundElevated))
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.animeTitle,
                    color = StreamHubColors.TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "Episode ${entry.episodeNumber}",
                    color = StreamHubColors.TextTertiary,
                    fontSize = 12.sp,
                )
            }
            Text(
                text = entry.airTimeLabel,
                color = StreamHubColors.Accent,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyState(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = text, color = StreamHubColors.TextTertiary, fontSize = 14.sp)
    }
}
