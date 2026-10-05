@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
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
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.ui.theme.AnsuColors

private fun airedText(episode: SEpisode): String? =
    episode.airDate?.takeIf { it.isNotBlank() }
        ?: episode.dateUpload.takeIf { it > 0 }?.let {
            java.text.SimpleDateFormat("MMM d, yyyy", java.util.Locale.getDefault()).format(java.util.Date(it))
        }

private fun isSameEpisode(a: SEpisode?, b: SEpisode): Boolean =
    a != null && a.id == b.id && a.episodeNumber == b.episodeNumber

/** One "E1 · Episode name · Aired date" card, as in the sketch's episode list. */
@Composable
fun EpisodeRow(episode: SEpisode, current: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val number = episode.episodeNumber.formatEpisodeNumber()
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (current) Color.White.copy(alpha = 0.08f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.White.copy(alpha = 0.08f))
                .then(if (current) Modifier.border(1.5.dp, com.ansu.anime.ui.theme.AnsuColors.Accent, RoundedCornerShape(8.dp)) else Modifier),
        ) {
            if (!episode.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color.Black.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text("E$number", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (current) {
                Icon(
                    Icons.Rounded.PlayArrow,
                    contentDescription = "Now playing",
                    tint = Color.White,
                    modifier = Modifier.align(Alignment.Center).size(32.dp),
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                episode.name.ifBlank { "Episode $number" },
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            airedText(episode)?.let {
                Text(it, color = AnsuColors.TextSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

/** Portrait layout below the video: episode name + description, then "More episodes". */
@Composable
fun EpisodeInfoPane(
    state: PlayerUiState,
    onPlayEpisode: (SEpisode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val episodes = remember(state.episodes) { state.episodes.sortedBy { it.episodeNumber } }
    val currentIndex = episodes.indexOfFirst { isSameEpisode(state.episode, it) }
    val listState = rememberLazyListState()
    // The header is item 0, so episode i is item i + 1.
    LaunchedEffect(currentIndex, episodes.size) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex + 1)
    }

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)) {
                Text(
                    state.episode?.name?.takeIf { it.isNotBlank() } ?: episodeLine(state.episode),
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    listOfNotNull(state.anime?.title, episodeLine(state.episode).takeIf { it.isNotBlank() }).joinToString(" \u2022 "),
                    color = AnsuColors.TextSecondary,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                state.selectedSource?.let {
                    Text(it.label, color = AnsuColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                state.episode?.description?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 14.sp,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                if (episodes.isNotEmpty()) {
                    Text(
                        "More episodes",
                        color = Color.White,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp),
                    )
                }
            }
        }
        itemsIndexed(episodes, key = { index, episode -> "${episode.id}#$index" }) { _, episode ->
            EpisodeRow(
                episode = episode,
                current = isSameEpisode(state.episode, episode),
                onClick = { onPlayEpisode(episode) },
            )
        }
    }
}

/** Landscape: the "Episodes" pill item opens the same list in a sheet. */
@Composable
fun EpisodeListSheet(
    state: PlayerUiState,
    onPlayEpisode: (SEpisode) -> Unit,
    onDismiss: () -> Unit,
) {
    val episodes = remember(state.episodes) { state.episodes.sortedBy { it.episodeNumber } }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Text(
            "Episodes",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        if (episodes.isEmpty()) {
            Text(
                "No other episodes available.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp),
            )
        } else {
            val listState = rememberLazyListState()
            val currentIndex = episodes.indexOfFirst { isSameEpisode(state.episode, it) }
            LaunchedEffect(currentIndex) { if (currentIndex >= 0) listState.scrollToItem(currentIndex) }
            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                itemsIndexed(episodes, key = { index, episode -> "${episode.id}#$index" }) { _, episode ->
                    EpisodeRow(
                        episode = episode,
                        current = isSameEpisode(state.episode, episode),
                        onClick = {
                            onPlayEpisode(episode)
                            onDismiss()
                        },
                    )
                }
            }
        }
    }
}

/** Audio / subtitle picker. [offSelected] non-null adds an "Off" row (subtitles). */
@Composable
fun TrackSheet(
    title: String,
    options: List<TrackOption>,
    offSelected: Boolean?,
    onSelect: (TrackOption) -> Unit,
    onOff: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            if (options.isEmpty() && offSelected == null) {
                Text(
                    "No tracks available for this source.",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            if (offSelected != null) {
                SheetRow("Off", selected = offSelected, onClick = { onOff(); onDismiss() })
            }
            options.forEach { option ->
                SheetRow(option.label, selected = option.selected && offSelected != true, onClick = { onSelect(option); onDismiss() })
            }
        }
    }
}

/** The gear menu in the portrait player. */
@Composable
fun SettingsMenuSheet(
    fitLabel: String,
    speedLabel: String,
    actions: PlayerActions,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Player settings",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            SheetRow("Subtitles", onClick = { onDismiss(); actions.onSubs() })
            SheetRow("Audio", onClick = { onDismiss(); actions.onAudio() })
            SheetRow("Sources", onClick = { onDismiss(); actions.onSources() })
            SheetRow("Playback speed", trailing = speedLabel, onClick = actions.onSpeed)
            SheetRow("Video fit", trailing = fitLabel, onClick = actions.onFit)
        }
    }
}

@Composable
private fun SheetRow(label: String, selected: Boolean = false, trailing: String? = null, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, style = MaterialTheme.typography.bodyMedium)
        if (selected) Icon(Icons.Rounded.Check, contentDescription = "Selected", modifier = Modifier.size(20.dp))
    }
}
