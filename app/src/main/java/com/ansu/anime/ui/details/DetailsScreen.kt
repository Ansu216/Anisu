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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import com.ansu.anime.ui.components.BottomScrim
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.GenreChip
import com.ansu.anime.ui.components.PersonCard
import com.ansu.anime.ui.components.StatItem
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Details page — a JJK-reference-style layout: banner hero with back button,
 * white "Play Now" + frosted circular like button, score/episodes/year/format
 * stats, genre row, expandable synopsis, episode list, cast, crew, related
 * shows. Data comes from [DetailsViewModel]: episodes are resolved from the
 * anime's real extension/addon source, AniList details load in parallel.
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

    Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item {
                DetailsHero(
                    title = anime?.title.orEmpty(),
                    imageUrl = anime?.bannerUrl ?: details?.bannerUrl ?: anime?.posterUrl,
                    onBack = { navController.popBackStack() },
                )
            }

            item {
                val firstEpisode = state.episodes.firstOrNull()
                PlayLikeRow(
                    playLabel = firstEpisode?.let { "Play Ep. ${it.episodeNumber.formatEpisodeNumber()}" } ?: "No episodes yet",
                    playEnabled = firstEpisode != null,
                    onPlay = { firstEpisode?.let(onEpisodeSelected) },
                    isLiked = state.isFavourite,
                    onToggleLike = { viewModel.toggleFavourite() },
                )
            }

            item {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                    StatItem(Icons.Filled.Star, details?.averageScore?.let { "$it%" } ?: "—", "SCORE", tint = AnsuColors.ScoreGreen)
                    StatItem(Icons.Filled.ViewList, (details?.episodes ?: state.episodes.size.takeIf { it > 0 })?.toString() ?: "—", "EPISODES")
                    StatItem(Icons.Filled.CalendarToday, details?.year?.toString() ?: anime?.releaseYear?.toString() ?: "—", "YEAR")
                    StatItem(Icons.Filled.Tv, details?.format ?: "TV", "FORMAT")
                }
            }

            val genres = (details?.genres?.takeIf { it.isNotEmpty() } ?: anime?.genres).orEmpty()
            if (genres.isNotEmpty()) {
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(genres) { genre -> GenreChip(genre) }
                    }
                }
            }

            val synopsis = (details?.description ?: anime?.description)?.replace(Regex("<[^>]*>"), "")
            if (!synopsis.isNullOrBlank()) {
                item {
                    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                        Text(
                            text = synopsis,
                            color = AnsuColors.TextSecondary,
                            fontSize = 14.sp,
                            lineHeight = 21.sp,
                            maxLines = if (expandSynopsis) Int.MAX_VALUE else 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = if (expandSynopsis) "Show Less" else "Read More",
                            color = AnsuColors.TextPrimary,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(top = 6.dp).clickable { expandSynopsis = !expandSynopsis },
                        )
                    }
                }
            }

            item { DetailsSectionHeader("Episodes") }
            if (state.isLoading) {
                item { CircularProgressIndicator(color = AnsuColors.Accent, modifier = Modifier.padding(20.dp)) }
            } else if (state.episodes.isEmpty()) {
                item {
                    Text(
                        state.error ?: "No episodes found yet.",
                        color = AnsuColors.TextTertiary,
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                }
            } else {
                items(state.episodes, key = { it.id }) { episode ->
                    EpisodeRow(episode = episode, synopsis = synopsis, onClick = { onEpisodeSelected(episode) })
                    androidx.compose.foundation.layout.Spacer(Modifier.height(16.dp))
                }
            }

            details?.characters?.takeIf { it.isNotEmpty() }?.let { characters ->
                item { DetailsSectionHeader("Characters") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                item { DetailsSectionHeader("Staff") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
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
                item { DetailsSectionHeader("More Like This") }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(related, key = { it.id }) { media ->
                            RelatedPoster(
                                media = media,
                                onClick = {
                                    container.selectionHolder.selectAnime(media.toSAnime())
                                    navController.navigate(com.ansu.anime.ui.navigation.Dest.DETAILS)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    selectedPerson?.let { person ->
        CharacterStaffSheet(person = person, onDismiss = { selectedPerson = null })
    }
}

/** Banner hero with back button and overlaid title — sized like the reference (320dp). */
@Composable
private fun DetailsHero(title: String, imageUrl: String?, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        AsyncImage(
            model = imageUrl,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(AnsuColors.BackgroundElevated),
        )
        BottomScrim(modifier = Modifier.fillMaxSize())
        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.35f)),
        ) {
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = AnsuColors.TextPrimary)
        }
        Text(
            text = title.uppercase(),
            color = AnsuColors.TextPrimary,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

/** White "Play" CTA with a circular frosted-glass like button to its right. */
@Composable
private fun PlayLikeRow(playLabel: String, playEnabled: Boolean, onPlay: () -> Unit, isLiked: Boolean, onToggleLike: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            enabled = playEnabled,
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.Background),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = AnsuColors.Background)
            Text(playLabel, color = AnsuColors.Background, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
        }
        FrostedGlassCard(modifier = Modifier.size(50.dp), shape = CircleShape, tintAlpha = 0.5f) {
            IconButton(onClick = onToggleLike, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = "Favourite",
                    tint = if (isLiked) AnsuColors.Accent else AnsuColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun DetailsSectionHeader(title: String) {
    Text(
        text = title,
        color = AnsuColors.TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun EpisodeRow(episode: SEpisode, synopsis: String?, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(120.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(8.dp)).background(AnsuColors.BackgroundElevated),
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(model = episode.thumbnailUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = AnsuColors.TextPrimary,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Episode ${episode.episodeNumber.formatEpisodeNumber()}" +
                    episode.name.takeIf { it.isNotBlank() && !it.startsWith("Episode") }?.let { " - $it" }.orEmpty(),
                color = AnsuColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!synopsis.isNullOrBlank()) {
                Text(
                    text = synopsis,
                    color = AnsuColors.TextTertiary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun RelatedPoster(media: AniListMedia, onClick: () -> Unit) {
    Column(modifier = Modifier.width(120.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(AnsuColors.BackgroundElevated),
        ) {
            if (media.posterUrl != null) {
                AsyncImage(model = media.posterUrl, contentDescription = media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            text = media.title,
            color = AnsuColors.TextPrimary,
            fontSize = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
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
