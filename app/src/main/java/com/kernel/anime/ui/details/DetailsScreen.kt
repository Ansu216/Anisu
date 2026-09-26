package com.kernel.anime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.kernel.anime.core.model.SEpisode
import com.kernel.anime.core.util.formatEpisodeNumber
import com.kernel.anime.di.AppContainer
import com.kernel.anime.ui.theme.KernelGold
import com.kernel.anime.ui.theme.KernelSurfaceRaised
import com.kernel.anime.ui.theme.KernelTextSecondary

@Composable
fun DetailsScreen(
    container: AppContainer,
    navController: NavHostController,
    onEpisodeSelected: (SEpisode) -> Unit,
) {
    val viewModel: DetailsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { DetailsViewModel(container.extensionManager, container.addonManager, container.selectionHolder) }
        },
    )
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val episodes by viewModel.episodes.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    AsyncImage(
                        model = anime?.bannerUrl ?: anime?.posterUrl,
                        contentDescription = anime?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(KernelSurfaceRaised),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)),
                        ),
                    )
                    IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                }
            }

            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(anime?.title.orEmpty(), style = MaterialTheme.typography.headlineMedium)
                    anime?.genres?.takeIf { it.isNotEmpty() }?.let {
                        Text(it.joinToString(" · "), style = MaterialTheme.typography.bodyMedium, color = KernelTextSecondary)
                    }
                    anime?.description?.let {
                        Text(
                            text = it.replace(Regex("<[^>]*>"), ""),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                Text(
                    "Episodes",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (isLoading) {
                item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
            } else if (episodes.isEmpty()) {
                item {
                    Text(
                        error ?: "No episodes found for this title yet.",
                        color = KernelTextSecondary,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            } else {
                items(episodes, key = { it.id }) { episode ->
                    EpisodeRow(episode = episode, onClick = { onEpisodeSelected(episode) })
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: SEpisode, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = KernelGold, modifier = Modifier.size(28.dp))
        Column {
            Text("Episode ${episode.episodeNumber.formatEpisodeNumber()}", style = MaterialTheme.typography.titleSmall)
            Text(episode.name, style = MaterialTheme.typography.bodyMedium, color = KernelTextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
