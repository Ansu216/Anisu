package com.ansu.anime.ui.details

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.core.model.MediaOrigin
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AnimeCard
import com.ansu.anime.ui.components.GenreChip
import com.ansu.anime.ui.components.PersonCard
import com.ansu.anime.ui.components.StatItem
import com.ansu.anime.ui.theme.AnisuGold
import com.ansu.anime.ui.theme.AnisuSurfaceRaised
import com.ansu.anime.ui.theme.AnisuTextSecondary

/**
 * The details page, laid out the CornCastle way: a tall vertical scroll with
 * a banner, big play action, quick stats, synopsis, then episodes, cast and
 * crew grids, and related shows - as opposed to a shallow "poster + play
 * button" page. Playback itself still opens Anisu's Nuvio-style player.
 */
@Composable
fun DetailsScreen(
    container: AppContainer,
    navController: NavHostController,
    onEpisodeSelected: (SEpisode) -> Unit,
) {
    val viewModel: DetailsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                DetailsViewModel(container.extensionManager, container.addonManager, container.aniListRepository, container.selectionHolder)
            }
        },
    )
    val anime by viewModel.anime.collectAsStateWithLifecycle()
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var expandSynopsis by remember { mutableStateOf(false) }
    var selectedPerson by remember { mutableStateOf<PersonDetail?>(null) }

    val details = state.aniListDetails

    Scaffold { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Box(modifier = Modifier.fillMaxWidth().aspectRatio(3f / 4f)) {
                    AsyncImage(
                        model = anime?.bannerUrl ?: details?.bannerUrl ?: anime?.posterUrl,
                        contentDescription = anime?.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(AnisuSurfaceRaised),
                    )
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Transparent,
                                0.55f to Color.Black.copy(alpha = 0.35f),
                                1f to MaterialTheme.colorScheme.background,
                            ),
                        ),
                    )
                    IconButton(onClick = { navController.popBackStack() }, modifier = Modifier.padding(8.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                    Text(
                        text = anime?.title.orEmpty(),
                        style = MaterialTheme.typography.displayLarge,
                        color = Color.White,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(horizontal = 20.dp, vertical = 16.dp),
                    )
                }
            }

            item {
                val firstEpisode = state.episodes.firstOrNull()
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = { firstEpisode?.let(onEpisodeSelected) },
                        enabled = firstEpisode != null,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                        Text(
                            text = firstEpisode?.let { "Play Ep. ${it.episodeNumber.formatEpisodeNumber()}" } ?: "No episodes yet",
                            modifier = Modifier.padding(start = 4.dp),
                        )
                    }
                    IconButton(
                        onClick = { viewModel.toggleFavourite() },
                        modifier = Modifier.padding(start = 12.dp).size(48.dp).clip(MaterialTheme.shapes.medium).background(AnisuSurfaceRaised),
                    ) {
                        Icon(
                            imageVector = if (state.isFavourite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favourite",
                            tint = if (state.isFavourite) AnisuGold else Color.White,
                        )
                    }
                }
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(Icons.Filled.Star, details?.averageScore?.let { "$it%" } ?: "—", "Score", tint = androidx.compose.ui.graphics.Color(0xFF3FBF8F))
                    StatItem(Icons.AutoMirrored.Filled.ViewList, (details?.episodes ?: anime?.let { state.episodes.size })?.toString() ?: "—", "Episodes")
                    StatItem(Icons.Filled.CalendarToday, details?.year?.toString() ?: anime?.releaseYear?.toString() ?: "—", "Year")
                    StatItem(Icons.Filled.Tv, details?.format ?: "TV", "Format")
                }
            }

            val genres = (details?.genres?.takeIf { it.isNotEmpty() } ?: anime?.genres).orEmpty()
            if (genres.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { genre -> GenreChip(genre) }
                    }
                }
            }

            val synopsis = (details?.description ?: anime?.description)?.replace(Regex("<[^>]*>"), "")
            if (!synopsis.isNullOrBlank()) {
                item {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = synopsis,
                            style = MaterialTheme.typography.bodyMedium,
                            color = AnisuTextSecondary,
                            maxLines = if (expandSynopsis) Int.MAX_VALUE else 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (expandSynopsis) "Show Less" else "Read More",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White,
                            modifier = Modifier.padding(top = 6.dp).clickable { expandSynopsis = !expandSynopsis },
                        )
                    }
                }
            }

            item { SectionHeader("Episodes") }
            if (state.isLoading) {
                item { CircularProgressIndicator(modifier = Modifier.padding(16.dp)) }
            } else if (state.episodes.isEmpty()) {
                item { Text(state.error ?: "No episodes found yet.", color = AnisuTextSecondary, modifier = Modifier.padding(horizontal = 16.dp)) }
            } else {
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeRow(episode = episode, synopsis = synopsis, onClick = { onEpisodeSelected(episode) })
                }
            }

            details?.characters?.takeIf { it.isNotEmpty() }?.let { characters ->
                item { SectionHeader("Characters") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(characters, key = { it.id }) { character ->
                            PersonCard(
                                imageUrl = character.imageUrl,
                                name = character.name,
                                role = character.role.lowercase().replaceFirstChar { it.uppercase() },
                                onClick = { selectedPerson = PersonDetail.Character(character) },
                            )
                        }
                    }
                }
            }

            details?.staff?.takeIf { it.isNotEmpty() }?.let { staff ->
                item { SectionHeader("Staff") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(staff, key = { it.id }) { member ->
                            PersonCard(
                                imageUrl = member.imageUrl,
                                name = member.name,
                                role = member.role,
                                onClick = { selectedPerson = PersonDetail.Staff(member) },
                            )
                        }
                    }
                }
            }

            details?.related?.takeIf { it.isNotEmpty() }?.let { related ->
                item { SectionHeader("More Like This") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        items(related, key = { it.id }) { media ->
                            AnimeCard(
                                anime = media.toSAnime(),
                                onClick = {
                                    container.selectionHolder.selectAnime(media.toSAnime())
                                    navController.navigate(com.ansu.anime.ui.navigation.Dest.DETAILS)
                                },
                            )
                        }
                    }
                }
                item { Box(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    selectedPerson?.let { person ->
        CharacterStaffSheet(person = person, onDismiss = { selectedPerson = null })
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

@Composable
private fun EpisodeRow(episode: SEpisode, synopsis: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.small)
                .background(AnisuSurfaceRaised),
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(model = episode.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(28.dp),
            )
        }
        Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
            Text(
                text = "Episode ${episode.episodeNumber.formatEpisodeNumber()}" + episode.name.takeIf { it.isNotBlank() && !it.startsWith("Episode") }?.let { " - $it" }.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!synopsis.isNullOrBlank()) {
                Text(
                    text = synopsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnisuTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

private fun AniListMedia.toSAnime(): SAnime = SAnime(
    id = id.toString(),
    title = title,
    posterUrl = posterUrl,
    bannerUrl = bannerUrl,
    description = description,
    genres = genres,
    releaseYear = year,
    rating = averageScore?.div(10.0),
    anilistId = id,
    origin = MediaOrigin.Extension(sourceId = 1L, urlPath = id.toString()),
)
