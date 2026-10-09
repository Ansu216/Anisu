package com.ansu.anime.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ViewList
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.ui.theme.AnsuColors

private fun sameEpisode(a: SEpisode?, b: SEpisode): Boolean =
    a != null && a.id == b.id && a.episodeNumber == b.episodeNumber

/**
 * The landscape Episodes panel. It slides in like Sources / Subtitles / Audio (the video shrinks to make room),
 * with a style button beside the close button that flips between a list of cards (thumbnail, name, air date)
 * and a grid of numbered tiles (E1, E2, E3 ... for every episode the show has). The choice is remembered.
 * Picking an episode plays it and closes the panel.
 */
@Composable
internal fun EpisodesPanel(
    state: PlayerUiState,
    progress: () -> Float,
    grid: Boolean,
    onGridChange: (Boolean) -> Unit,
    onPlayEpisode: (SEpisode) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes = remember(state.episodes) { state.episodes.sortedBy { it.episodeNumber } }
    val currentIndex = episodes.indexOfFirst { sameEpisode(state.episode, it) }
    val listState = rememberLazyListState()
    val gridState = rememberLazyGridState()
    // Open on the episode being watched, in whichever style is showing.
    LaunchedEffect(currentIndex, grid) {
        if (currentIndex >= 0) {
            if (grid) gridState.scrollToItem(currentIndex) else listState.scrollToItem(currentIndex)
        }
    }

    PlayerSidePanelFrame(
        title = "Episodes",
        progress = progress,
        onClose = onClose,
        modifier = modifier,
        headerExtra = {
            // Shows the style you would switch to.
            IconButton(onClick = { onGridChange(!grid) }) {
                Icon(
                    imageVector = if (grid) Icons.AutoMirrored.Rounded.ViewList else Icons.Rounded.GridView,
                    contentDescription = if (grid) "Show as list" else "Show as grid",
                    tint = Color.White,
                    modifier = Modifier.size(22.dp),
                )
            }
        },
    ) {
        if (episodes.isEmpty()) {
            Text(
                "No other episodes available.",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 18.dp, vertical = 12.dp),
            )
            return@PlayerSidePanelFrame
        }
        Crossfade(
            targetState = grid,
            animationSpec = tween(220),
            modifier = Modifier.fillMaxWidth().weight(1f),
            label = "episodeStyle",
        ) { showGrid ->
            if (showGrid) {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Adaptive(64.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(episodes, key = { index, episode -> "${episode.id}#$index" }) { _, episode ->
                        EpisodeTile(
                            episode = episode,
                            current = sameEpisode(state.episode, episode),
                            onClick = { onPlayEpisode(episode) },
                        )
                    }
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    itemsIndexed(episodes, key = { index, episode -> "${episode.id}#$index" }) { _, episode ->
                        EpisodeRow(
                            episode = episode,
                            current = sameEpisode(state.episode, episode),
                            onClick = { onPlayEpisode(episode) },
                        )
                    }
                }
            }
        }
    }
}

/** One numbered tile of the grid style; the episode being watched is filled with the accent colour. */
@Composable
private fun EpisodeTile(episode: SEpisode, current: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier
            .height(44.dp)
            .clip(shape)
            .background(if (current) AnsuColors.Accent else Color.White.copy(alpha = 0.07f))
            .border(1.dp, if (current) AnsuColors.Accent else Color.White.copy(alpha = 0.18f), shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "E${episode.episodeNumber.formatEpisodeNumber()}",
            color = if (current) AnsuColors.OnAccent else Color.White,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}
