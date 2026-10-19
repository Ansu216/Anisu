@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)

package com.ansu.anime.ui.sources

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
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
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.haptics.Haptics
import com.ansu.anime.core.haptics.tickClickable
import com.ansu.anime.data.download.DownloadPhase
import com.ansu.anime.data.download.downloadKey
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassDropdownMenu
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.player.PlayableSource
import com.ansu.anime.ui.player.kindChip
import com.ansu.anime.ui.player.qualityChip
import com.ansu.anime.ui.theme.AnsuColors

/** Name used for a stream whose extension did not say who it is. */
private const val UNKNOWN_EXTENSION = "Source"

/**
 * Opens when an episode is tapped: lists every stream every installed extension found for it, grouped by
 * extension. Tap a stream to play it; hold one for the Download option.
 */
@Composable
fun SourcePickerScreen(container: AppContainer, navController: NavHostController) {
    val viewModel: SourcePickerViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                SourcePickerViewModel(
                    container.extensionManager,
                    container.selectionHolder,
                    container.playerPrefs,
                    container.aniListRepository,
                    container.diagnostics,
                )
            }
        },
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val downloadItems by container.downloads.items.collectAsStateWithLifecycle()
    val context = LocalContext.current

    val anime = state.anime
    val episode = state.episode
    // null = the "All" chip.
    var filter by remember { mutableStateOf<String?>(null) }
    var menuFor by remember { mutableStateOf<PlayableSource?>(null) }

    val groups = remember(state.sources) {
        state.sources.groupBy { it.extensionName ?: UNKNOWN_EXTENSION }
    }
    // A chip for an extension that vanished after a refresh must not keep an empty list on screen.
    val activeFilter = filter?.takeIf { it in groups }

    fun play(source: PlayableSource) {
        container.diagnostics.log(LogCategory.CLICK, "Source picked: ${source.extensionName} ${source.serverName.orEmpty()} ${source.label}")
        viewModel.pick(source)
        // Back from the player goes to the details page, not to a list that belongs to an older episode.
        navController.navigate(Dest.PLAYER) { popUpTo(Dest.SOURCES) { inclusive = true } }
    }

    fun download(source: PlayableSource) {
        if (anime == null || episode == null) return
        val id = anime.anilistId?.let { downloadKey(it, episode.episodeNumber) }
        val existing = id?.let { key -> downloadItems.firstOrNull { it.entry.id == key }?.status }
        val message = when (existing?.phase) {
            DownloadPhase.Completed -> "Already downloaded"
            DownloadPhase.Preparing, DownloadPhase.Queued, DownloadPhase.Downloading -> "Already downloading"
            else -> {
                container.diagnostics.log(LogCategory.CLICK, "Download from picker: ${anime.title} E${episode.episodeNumber} via ${source.extensionName}")
                container.downloads.enqueue(anime, episode, source, state.sources)
                "Download started"
            }
        }
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                PickerHero(
                    imageUrl = episode?.thumbnailUrl ?: anime?.bannerUrl ?: anime?.posterUrl,
                    episodeLabel = episode?.let { "E${formatNumber(it.episodeNumber)}" }.orEmpty(),
                    title = episode?.name?.takeIf { it.isNotBlank() && !it.startsWith("Episode") } ?: anime?.title.orEmpty(),
                    subtitle = anime?.title.orEmpty(),
                    onBack = { navController.popBackStack() },
                )
            }
            item(key = "chips") {
                Column {
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RefreshChip(loading = state.isLoading, onClick = { viewModel.refresh() })
                        FilterChip(text = "All", selected = activeFilter == null, onClick = { filter = null })
                        groups.keys.forEach { name ->
                            FilterChip(text = name, selected = activeFilter == name, onClick = { filter = name })
                        }
                    }
                    if (state.sources.isNotEmpty()) {
                        Text(
                            text = "Tap a source to play it. Hold one to download it.",
                            color = AnsuColors.TextTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp),
                        )
                    }
                }
            }
            if (state.sources.isEmpty()) {
                item(key = "status") {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 28.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.5.dp, color = AnsuColors.Accent)
                            Text("Looking for sources\u2026", color = AnsuColors.TextSecondary, fontSize = 15.sp)
                        } else {
                            Text(state.error ?: "No playable sources found", color = AnsuColors.TextSecondary, fontSize = 15.sp)
                        }
                    }
                }
            }
            groups.forEach { (extension, list) ->
                if (activeFilter != null && activeFilter != extension) return@forEach
                item(key = "header:$extension") {
                    Text(
                        text = extension,
                        color = AnsuColors.TextPrimary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 10.dp),
                    )
                }
                list.forEachIndexed { index, source ->
                    item(key = "source:$extension:$index:${source.serverName}:${source.label}") {
                        Box(modifier = Modifier.padding(horizontal = 16.dp).padding(bottom = 12.dp)) {
                            SourceCard(
                                source = source,
                                onClick = {
                                    Haptics.soft()
                                    play(source)
                                },
                                onLongClick = {
                                    Haptics.hold()
                                    menuFor = source
                                },
                            )
                            GlassDropdownMenu(expanded = menuFor === source, onDismissRequest = { menuFor = null }) {
                                val key = anime?.anilistId?.let { id -> episode?.let { downloadKey(id, it.episodeNumber) } }
                                val done = key != null && downloadItems.any { it.entry.id == key && it.status.phase == DownloadPhase.Completed }
                                DropdownMenuItem(
                                    text = { Text(if (done) "Downloaded" else "Download", color = AnsuColors.TextPrimary) },
                                    leadingIcon = {
                                        Icon(
                                            if (done) Icons.Filled.DownloadDone else Icons.Filled.Download,
                                            contentDescription = null,
                                            tint = AnsuColors.TextPrimary,
                                        )
                                    },
                                    onClick = {
                                        menuFor = null
                                        download(source)
                                    },
                                )
                            }
                        }
                    }
                }
            }
            item(key = "end") { Spacer(Modifier.navigationBarsPadding().height(28.dp)) }
        }
    }
}

@Composable
private fun PickerHero(imageUrl: String?, episodeLabel: String, title: String, subtitle: String, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
        if (imageUrl != null) {
            AsyncImage(model = imageUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.35f),
                    0.55f to Color.Black.copy(alpha = 0.15f),
                    1f to AnsuColors.Background,
                ),
            ),
        )
        Box(
            modifier = Modifier
                .statusBarsPadding()
                .padding(start = 14.dp, top = 8.dp)
                .size(44.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.45f))
                .tickClickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
        }
        Column(modifier = Modifier.align(Alignment.BottomStart).padding(start = 20.dp, end = 20.dp, bottom = 6.dp)) {
            if (episodeLabel.isNotBlank()) {
                Text(episodeLabel, color = AnsuColors.Accent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            }
            Text(title, color = AnsuColors.TextPrimary, fontSize = 28.sp, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle.isNotBlank() && subtitle != title) {
                Text(subtitle, color = AnsuColors.TextSecondary, fontSize = 17.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun RefreshChip(loading: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(AnsuColors.BackgroundElevated)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = AnsuColors.TextPrimary)
        } else {
            Icon(Icons.Rounded.Refresh, contentDescription = "Refresh sources", tint = AnsuColors.TextPrimary, modifier = Modifier.size(22.dp))
        }
    }
}

@Composable
private fun FilterChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .height(46.dp)
            .clip(RoundedCornerShape(23.dp))
            .background(if (selected) AnsuColors.Accent else AnsuColors.BackgroundElevated)
            .tickClickable(onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (selected) AnsuColors.OnAccent else AnsuColors.TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
    }
}

@Composable
private fun SourceCard(source: PlayableSource, onClick: () -> Unit, onLongClick: () -> Unit) {
    val title = listOfNotNull(source.extensionName, source.serverName).joinToString(" \u00b7 ").ifBlank { source.label }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AnsuColors.BackgroundElevated)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(title, color = AnsuColors.TextPrimary, fontSize = 19.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(source.label.ifBlank { "Stream" }, color = AnsuColors.TextSecondary, fontSize = 14.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        val chips = listOfNotNull(
            source.qualityChip(),
            source.kindChip(),
            source.subtitles.size.takeIf { it > 0 }?.let { "$it subs" },
            source.audioTracks.size.takeIf { it > 0 }?.let { "$it audio" },
        )
        if (chips.isNotEmpty()) {
            Row(modifier = Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                chips.forEach { chip ->
                    Text(
                        text = chip.uppercase(),
                        color = AnsuColors.TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                    )
                }
            }
        }
    }
}

/** 7 for 7.0, 7.5 for 7.5. */
private fun formatNumber(value: Float): String =
    if (value % 1f == 0f) value.toInt().toString() else value.toString()
