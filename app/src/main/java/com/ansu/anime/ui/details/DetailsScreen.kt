package com.ansu.anime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.anilist.toEpisodeItems
import com.ansu.anime.anilist.toRelatedCards
import com.ansu.anime.anilist.toRecommendationCards
import com.ansu.anime.anilist.toCharacterCards
import com.ansu.anime.anilist.toStaffCards
import com.ansu.anime.ui.theme.StreamHubColors
import com.ansu.anime.ui.components.BottomScrim
import com.ansu.anime.ui.components.FrostedGlassCard

data class EpisodeItem(
    val number: Int,
    val title: String,
    val description: String,
    val thumbnailUrl: String?,
)

data class RelatedCard(
    val id: Int,
    val title: String,
    val imageUrl: String?,
    val badge: String? = null,
)

data class PersonCard(
    val id: Int,
    val name: String,
    val role: String,
    val imageUrl: String?,
)

@Composable
fun DetailsScreen(
    media: com.ansu.anime.anilist.AniListMedia,
    isLiked: Boolean,
    onBack: () -> Unit,
    onPlay: () -> Unit,
    onToggleLike: () -> Unit,
    onEpisodeClick: (EpisodeItem) -> Unit = {},
    onTrailerClick: (com.ansu.anime.anilist.AniListTrailer) -> Unit = {},
    onRelatedClick: (Int) -> Unit = {},
    onCharacterClick: (Int) -> Unit = {},
    onStaffClick: (Int) -> Unit = {},
) {
    val episodes = remember(media) { media.toEpisodeItems() }
    val related = remember(media) { media.toRelatedCards() }
    val recommendations = remember(media) { media.toRecommendationCards() }
    val characters = remember(media) { media.toCharacterCards() }
    val staffCards = remember(media) { media.toStaffCards() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(StreamHubColors.Background)
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 40.dp)
        ) {
            item { DetailsHero(media = media, onBack = onBack) }
            item { PlayLikeRow(onPlay = onPlay, isLiked = isLiked, onToggleLike = onToggleLike) }
            item { StatsRow(media = media) }

            if (media.genres.isNotEmpty()) {
                item { GenreRow(genres = media.genres) }
            }
            if (media.plainDescription.isNotBlank()) {
                item { DescriptionSection(text = media.plainDescription) }
            }
            if (episodes.isNotEmpty()) {
                item { DetailsSectionHeader("Episodes") }
                item { EpisodesSection(episodes = episodes, onEpisodeClick = onEpisodeClick) }
            }
            media.trailer?.let { trailer ->
                item { DetailsSectionHeader("Trailers & More") }
                item { TrailerRow(trailer = trailer, onClick = { onTrailerClick(trailer) }) }
            }
            if (related.isNotEmpty()) {
                item { DetailsSectionHeader("Related Content") }
                item { PosterRow(items = related, onClick = onRelatedClick) }
            }
            if (characters.isNotEmpty()) {
                item { DetailsSectionHeader("Characters") }
                item { PersonRow(items = characters, onClick = onCharacterClick) }
            }
            if (staffCards.isNotEmpty()) {
                item { DetailsSectionHeader("Staff") }
                item { PersonRow(items = staffCards, onClick = onStaffClick) }
            }
            if (recommendations.isNotEmpty()) {
                item { DetailsSectionHeader("More Like This") }
                item { PosterRow(items = recommendations, onClick = onRelatedClick) }
            }
        }
    }
}

@Composable
private fun DetailsHero(media: com.ansu.anime.anilist.AniListMedia, onBack: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().height(320.dp)) {
        val imageUrl = media.bannerImage ?: media.coverImage?.extraLarge ?: media.coverImage?.large
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = media.displayTitle,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color(0xFF2A2D3A), StreamHubColors.Background),
                        ),
                    ),
            )
        }
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
            Icon(
                Icons.Filled.ArrowBack,
                contentDescription = "Back",
                tint = StreamHubColors.TextPrimary,
            )
        }
        Text(
            text = media.displayTitle.uppercase(),
            color = StreamHubColors.TextPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = 20.dp, vertical = 16.dp),
        )
    }
}

@Composable
private fun PlayLikeRow(onPlay: () -> Unit, isLiked: Boolean, onToggleLike: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = onPlay,
            modifier = Modifier.weight(1f).height(50.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = StreamHubColors.Accent,
                contentColor = StreamHubColors.Background,
            ),
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = StreamHubColors.Background)
            Spacer(Modifier.width(8.dp))
            Text("Play Now", color = StreamHubColors.Background, fontWeight = FontWeight.Bold)
        }
        FrostedGlassCard(
            modifier = Modifier.size(50.dp),
            shape = CircleShape,
            tintAlpha = 0.5f,
        ) {
            IconButton(onClick = onToggleLike, modifier = Modifier.fillMaxSize()) {
                Icon(
                    imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                    contentDescription = if (isLiked) "Unlike" else "Like",
                    tint = StreamHubColors.TextPrimary,
                )
            }
        }
    }
}

@Composable
private fun StatsRow(media: com.ansu.anime.anilist.AniListMedia) {
    val stats = buildList {
        media.averageScore?.let { add(Stat(Icons.Filled.Star, "$it%", "SCORE", StreamHubColors.ScoreGreen)) }
        media.episodes?.let { add(Stat(Icons.Filled.List, "$it EP", "EPISODES")) }
        media.seasonYear?.let { add(Stat(Icons.Filled.DateRange, "$it", "YEAR")) }
        media.format?.let { add(Stat(Icons.Filled.Info, formatLabel(it), "FORMAT")) }
    }
    if (stats.isEmpty()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        stats.forEachIndexed { index, stat ->
            StatItem(stat)
            if (index != stats.lastIndex) {
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(30.dp)
                        .background(StreamHubColors.TextTertiary.copy(alpha = 0.4f)),
                )
            }
        }
    }
}

private data class Stat(
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val value: String,
    val label: String,
    val tint: Color = StreamHubColors.TextPrimary,
)

@Composable
private fun StatItem(stat: Stat) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(stat.icon, contentDescription = null, tint = stat.tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.height(4.dp))
        Text(stat.value, color = stat.tint, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Text(stat.label, color = StreamHubColors.TextTertiary, fontSize = 10.sp)
    }
}

private fun formatLabel(format: String): String = when (format) {
    "TV" -> "TV"
    "TV_SHORT" -> "TV Short"
    "MOVIE" -> "Movie"
    "SPECIAL" -> "Special"
    "OVA" -> "OVA"
    "ONA" -> "ONA"
    "MUSIC" -> "Music"
    else -> format
}

@Composable
private fun GenreRow(genres: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        genres.take(3).forEachIndexed { index, genre ->
            if (index > 0) {
                Box(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .width(1.dp)
                        .height(14.dp)
                        .background(StreamHubColors.TextTertiary),
                )
            }
            Text(
                text = genre,
                color = StreamHubColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun DescriptionSection(text: String) {
    var expanded by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = StreamHubColors.TextSecondary,
            fontSize = 14.sp,
            lineHeight = 21.sp,
            maxLines = if (expanded) Int.MAX_VALUE else 4,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (expanded) "Show Less" else "Read More",
            color = StreamHubColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable { expanded = !expanded },
        )
    }
}

@Composable
private fun DetailsSectionHeader(text: String) {
    Text(
        text = text,
        color = StreamHubColors.TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 22.dp, bottom = 12.dp),
    )
}

@Composable
private fun EpisodesSection(episodes: List<EpisodeItem>, onEpisodeClick: (EpisodeItem) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) episodes else episodes.take(4)
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
        visible.forEach { episode ->
            EpisodeRow(episode = episode, onClick = { onEpisodeClick(episode) })
            Spacer(Modifier.height(16.dp))
        }
        if (episodes.size > 4) {
            FrostedGlassCard(
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .clickable { expanded = !expanded },
                shape = RoundedCornerShape(20.dp),
                tintAlpha = 0.4f,
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (expanded) "View Less" else "View More",
                        color = StreamHubColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = StreamHubColors.TextPrimary,
                    )
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(episode: EpisodeItem, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(120.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(8.dp)),
        ) {
            if (episode.thumbnailUrl != null) {
                AsyncImage(
                    model = episode.thumbnailUrl,
                    contentDescription = episode.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(StreamHubColors.BackgroundElevated))
            }
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = StreamHubColors.TextPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(18.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                color = StreamHubColors.TextPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(StreamHubColors.SurfaceGlassBase)
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            ) {
                Text(
                    "E${episode.number}",
                    color = StreamHubColors.TextSecondary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = episode.description,
                color = StreamHubColors.TextTertiary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun TrailerRow(trailer: com.ansu.anime.anilist.AniListTrailer, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .padding(horizontal = 20.dp)
            .clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .width(220.dp)
                .height(124.dp)
                .clip(RoundedCornerShape(12.dp)),
        ) {
            if (trailer.thumbnail != null) {
                AsyncImage(
                    model = trailer.thumbnail,
                    contentDescription = "Trailer",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(modifier = Modifier.fillMaxSize().background(StreamHubColors.BackgroundElevated))
            }
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play trailer", tint = StreamHubColors.TextPrimary)
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Official Trailer",
            color = StreamHubColors.TextPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun PosterRow(items: List<RelatedCard>, onClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { item ->
            Column(modifier = Modifier.width(120.dp).clickable { onClick(item.id) }) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(2f / 3f)
                        .clip(RoundedCornerShape(10.dp)),
                ) {
                    if (item.imageUrl != null) {
                        AsyncImage(
                            model = item.imageUrl,
                            contentDescription = item.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Box(modifier = Modifier.fillMaxSize().background(StreamHubColors.BackgroundElevated))
                    }
                    item.badge?.let { badge ->
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(6.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.Black.copy(alpha = 0.65f))
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        ) {
                            Text(badge, color = StreamHubColors.TextPrimary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = item.title,
                    color = StreamHubColors.TextPrimary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun PersonRow(items: List<PersonCard>, onClick: (Int) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        items(items, key = { it.id }) { person ->
            Column(
                modifier = Modifier.width(88.dp).clickable { onClick(person.id) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(StreamHubColors.BackgroundElevated),
                ) {
                    if (person.imageUrl != null) {
                        AsyncImage(
                            model = person.imageUrl,
                            contentDescription = person.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = person.name,
                    color = StreamHubColors.TextPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                if (person.role.isNotBlank()) {
                    Text(
                        text = person.role,
                        color = StreamHubColors.TextTertiary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
