package com.ansu.anime.ui.schedule

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

private enum class ScheduleTab { SCHEDULE, NEWS }

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
    var tab by remember { mutableStateOf(ScheduleTab.SCHEDULE) }

    Scaffold(
        containerColor = AnsuColors.Background,
        bottomBar = { AppBottomBar(navController, Dest.SCHEDULE) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).background(AnsuColors.Background)) {
            SegmentedToggle(current = tab, onSelect = { tab = it })

            if (state.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AnsuColors.Accent)
                }
                return@Scaffold
            }

            when (tab) {
                ScheduleTab.SCHEDULE -> ScheduleList(entries = state.schedule, onClick = onEntrySelected)
                ScheduleTab.NEWS -> NewsList(articles = state.news)
            }
        }
    }
}

@Composable
private fun SegmentedToggle(current: ScheduleTab, onSelect: (ScheduleTab) -> Unit) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        shape = RoundedCornerShape(20.dp),
        tintAlpha = 0.4f,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(4.dp)) {
            ScheduleTab.values().forEach { option ->
                val selected = option == current
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selected) AnsuColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
                        .clickable { onSelect(option) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (option == ScheduleTab.SCHEDULE) "Schedule" else "News",
                        color = if (selected) AnsuColors.Background else AnsuColors.TextSecondary,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ScheduleList(entries: List<ScheduleEntry>, onClick: (ScheduleEntry) -> Unit) {
    if (entries.isEmpty()) {
        EmptyState("Nothing airing this week according to AniList.")
        return
    }
    val grouped = entries.groupBy { it.dayOfWeek }
    LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
        grouped.forEach { (day, dayEntries) ->
            item {
                Text(
                    text = day,
                    color = AnsuColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp),
                )
            }
            items(dayEntries, key = { it.animeId.toString() + it.episodeNumber }) { entry ->
                ScheduleRow(entry = entry, onClick = { onClick(entry) })
            }
        }
    }
}

@Composable
private fun ScheduleRow(entry: ScheduleEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.width(56.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(8.dp)).background(AnsuColors.BackgroundElevated),
        ) {
            if (entry.imageUrl != null) {
                AsyncImage(model = entry.imageUrl, contentDescription = entry.animeTitle, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(entry.animeTitle, color = AnsuColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("Episode ${entry.episodeNumber}", color = AnsuColors.TextTertiary, fontSize = 12.sp)
        }
        Text(entry.airTimeLabel, color = AnsuColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun NewsList(articles: List<NewsArticle>) {
    if (articles.isEmpty()) {
        EmptyState("No news right now.")
        return
    }
    LazyColumn(contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp)) {
        items(articles, key = { it.id }) { article ->
            Column(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
                Text(article.category.uppercase(), color = AnsuColors.TextTertiary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(article.title, color = AnsuColors.TextPrimary, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
                Text(article.snippet, color = AnsuColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                Text(article.dateDisplay, color = AnsuColors.TextTertiary, fontSize = 11.sp, modifier = Modifier.padding(top = 6.dp))
            }
        }
    }
}

@Composable
private fun EmptyState(message: String) {
    Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Text(message, color = AnsuColors.TextSecondary, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
