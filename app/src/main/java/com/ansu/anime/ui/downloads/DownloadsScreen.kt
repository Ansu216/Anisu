@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.core.haptics.softClickable
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.data.download.DownloadItem
import com.ansu.anime.data.download.DownloadPhase
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

/** "Your downloads": every episode saved on this device, with its progress, playable without a connection. */
@Composable
fun DownloadsScreen(container: AppContainer, navController: NavHostController) {
    val items by container.downloads.items.collectAsStateWithLifecycle()
    Scaffold(
        containerColor = AnsuColors.Background,
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Text(
                text = "Your downloads",
                style = MaterialTheme.typography.displayLarge,
                color = AnsuColors.TextPrimary,
                modifier = Modifier.padding(start = 20.dp, top = 8.dp),
            )
            Text(
                text = "Saved on this device with every subtitle the source offers. They play without a connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            )
            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp), contentAlignment = Alignment.TopCenter) {
                    Text(
                        text = "Nothing downloaded yet. Open a show and tap the download icon next to an episode.",
                        color = AnsuColors.TextSecondary,
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
                ) {
                    items(items, key = { it.entry.id }) { item ->
                        DownloadCard(
                            item = item,
                            onPlay = {
                                val entry = item.entry
                                val anime = SAnime(
                                    id = entry.anilistId.toString(),
                                    title = entry.animeTitle,
                                    posterUrl = entry.posterUrl,
                                    bannerUrl = entry.bannerUrl,
                                    anilistId = entry.anilistId,
                                    origin = MediaOrigin.Extension(sourceId = entry.sourceId ?: 1L, urlPath = entry.anilistId.toString()),
                                )
                                val episode = SEpisode(id = entry.episodeId, name = entry.episodeName, episodeNumber = entry.episodeNumber)
                                container.selectionHolder.selectOffline(anime, episode, entry.id)
                                navController.navigate(Dest.PLAYER)
                            },
                            onDelete = { container.downloads.remove(item.entry.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadCard(item: DownloadItem, onPlay: () -> Unit, onDelete: () -> Unit) {
    val entry = item.entry
    val status = item.status
    val playable = status.phase == DownloadPhase.Completed
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth().then(if (playable) Modifier.softClickable(onClick = onPlay) else Modifier),
        shape = RoundedCornerShape(16.dp),
        tintAlpha = 0.4f,
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            AsyncImage(
                model = entry.posterUrl,
                contentDescription = entry.animeTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(64.dp).height(92.dp).clip(RoundedCornerShape(10.dp)).background(AnsuColors.BackgroundElevated),
            )
            Column(modifier = Modifier.padding(start = 14.dp).weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = entry.animeTitle,
                    color = AnsuColors.TextPrimary,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val episodeLabel = "Episode ${entry.episodeNumber.let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }}"
                Text(text = episodeLabel, color = AnsuColors.TextSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                val detail = listOfNotNull(
                    entry.sourceLabel.takeIf { it.isNotBlank() },
                    entry.subtitles.size.takeIf { it > 0 }?.let { "$it subtitle${if (it == 1) "" else "s"}" },
                ).joinToString(" · ")
                if (detail.isNotEmpty()) {
                    Text(text = detail, color = AnsuColors.TextTertiary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                StatusLine(status.phase, status.percent, status.bytes, status.message)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete download", tint = AnsuColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun StatusLine(phase: DownloadPhase, percent: Float, bytes: Long, message: String?) {
    val text = when (phase) {
        DownloadPhase.Preparing -> "Finding the best stream…"
        DownloadPhase.Queued -> "Waiting to download"
        DownloadPhase.Downloading -> if (percent >= 0f) "Downloading ${percent.toInt()}% · ${formatSize(bytes)}" else "Downloading · ${formatSize(bytes)}"
        DownloadPhase.Completed -> "Ready to watch offline · ${formatSize(bytes)}"
        DownloadPhase.Failed -> message ?: "The download failed. Delete it and try again."
    }
    Text(
        text = text,
        color = if (phase == DownloadPhase.Failed) MaterialTheme.colorScheme.error else AnsuColors.TextSecondary,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
    )
    if (phase == DownloadPhase.Downloading || phase == DownloadPhase.Preparing || phase == DownloadPhase.Queued) {
        Box(modifier = Modifier.padding(top = 2.dp).fillMaxWidth().height(4.dp).clip(CircleShape).background(AnsuColors.AccentSoft)) {
            if (phase == DownloadPhase.Downloading && percent >= 0f) {
                Box(modifier = Modifier.fillMaxWidth((percent / 100f).coerceIn(0f, 1f)).height(4.dp).clip(CircleShape).background(AnsuColors.Accent))
            } else {
                Box(modifier = Modifier.fillMaxWidth(0.25f).height(4.dp).clip(CircleShape).background(AnsuColors.Accent.copy(alpha = 0.5f)))
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(java.util.Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(java.util.Locale.US, "%d MB", bytes / (1L shl 20))
    else -> String.format(java.util.Locale.US, "%d KB", bytes / 1024)
}
