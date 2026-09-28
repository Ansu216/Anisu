package com.ansu.anime.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
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
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.core.util.progressFraction
import com.ansu.anime.data.db.ContinueWatchingEntity
import com.ansu.anime.ui.theme.AnsuColors

@Composable
fun ShelfHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = AnsuColors.TextPrimary,
        modifier = modifier.padding(start = 20.dp, top = 26.dp, bottom = 12.dp),
    )
}

/** A poster-only catalogue row — the generic browse shelf used for extension/addon/AniList lists. */
@Composable
fun PosterRow(items: List<SAnime>, onClick: (SAnime) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.origin.hashCode().toLong() + it.id.hashCode() }) { anime ->
            AnimeCard(anime = anime, onClick = { onClick(anime) })
        }
    }
}

@Composable
fun AnimeCard(anime: SAnime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(130.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(AnsuColors.BackgroundElevated),
        )
        Text(
            text = anime.title,
            style = MaterialTheme.typography.bodyMedium,
            color = AnsuColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** Wide frosted-look continue-watching card: thumbnail with a burned-in progress bar. */
@Composable
fun ContinueWatchingCard(entry: ContinueWatchingEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(220.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(14.dp))
                .background(AnsuColors.BackgroundElevated),
        ) {
            AsyncImage(
                model = entry.bannerUrl ?: entry.posterUrl,
                contentDescription = entry.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
            Icon(
                imageVector = Icons.Filled.PlayArrow,
                contentDescription = "Resume",
                tint = AnsuColors.TextPrimary,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(AnsuColors.TextPrimary.copy(alpha = 0.2f)))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progressFraction(entry.positionSeconds, entry.durationSeconds))
                    .height(3.dp)
                    .background(AnsuColors.Accent),
            )
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            color = AnsuColors.TextPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "Episode ${entry.episodeNumber.formatEpisodeNumber()}",
            style = MaterialTheme.typography.labelSmall,
            color = AnsuColors.TextTertiary,
        )
    }
}

@Composable
fun ContinueWatchingRow(entries: List<ContinueWatchingEntity>, onClick: (ContinueWatchingEntity) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.anilistId }) { entry ->
            ContinueWatchingCard(entry = entry, onClick = { onClick(entry) })
        }
    }
}

/**
 * Swipeable hero carousel: art fills the frame, bottom scrim, centered title/genres,
 * and a centered "View Details" (white) + "Like" (frosted glass) button pair, with
 * page dots below. [onToggleFavourite] calls straight through to the real AniList
 * favourite mutation; since [SAnime] doesn't carry a persisted favourite flag at the
 * shelf level, the heart's fill state is a local, optimistic per-session toggle rather
 * than a synced one (the Details page's heart is the source of truth for that).
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(
    items: List<SAnime>,
    onClick: (SAnime) -> Unit,
    onToggleFavourite: (SAnime) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (items.isEmpty()) return
    val shown = items.take(8)
    val pagerState = rememberPagerState(pageCount = { shown.size })
    val likedIds = remember { mutableStateMapOf<String, Boolean>() }

    Column(modifier = modifier.fillMaxWidth()) {
        Box(modifier = Modifier.fillMaxWidth().height(440.dp)) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                val anime = shown[page]
                Box(modifier = Modifier.fillMaxSize()) {
                    AsyncImage(
                        model = anime.bannerUrl ?: anime.posterUrl,
                        contentDescription = anime.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().background(AnsuColors.BackgroundElevated),
                    )
                    BottomScrim(modifier = Modifier.fillMaxSize())
                }
            }

            val current = shown[pagerState.currentPage]
            val isLiked = likedIds[current.id] == true
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = current.title,
                    color = AnsuColors.TextPrimary,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                current.genres.take(3).takeIf { it.isNotEmpty() }?.let { genres ->
                    Text(
                        text = genres.joinToString(" • ") + current.releaseYear?.let { " • $it" }.orEmpty(),
                        color = AnsuColors.TextSecondary,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 16.dp),
                ) {
                    Button(
                        onClick = { onClick(current) },
                        colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.Background),
                        shape = RoundedCornerShape(24.dp),
                        contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                    ) {
                        Text("View Details", color = AnsuColors.Background, fontWeight = FontWeight.Bold)
                    }
                    FrostedGlassCard(modifier = Modifier.size(48.dp), shape = CircleShape, tintAlpha = 0.5f) {
                        IconButton(
                            onClick = {
                                likedIds[current.id] = !isLiked
                                onToggleFavourite(current)
                            },
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Icon(
                                imageVector = if (isLiked) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = if (isLiked) "Unlike" else "Like",
                                tint = AnsuColors.TextPrimary,
                            )
                        }
                    }
                }
            }
        }

        if (shown.size > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
                shown.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(if (selected) 18.dp else 6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) AnsuColors.Accent else AnsuColors.TextTertiary),
                    )
                }
            }
        }
    }
}

@Composable
fun GenreChip(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(AnsuColors.BackgroundElevated)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = AnsuColors.TextSecondary)
    }
}

@Composable
fun StatItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    tint: Color = AnsuColors.TextPrimary,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(value, style = MaterialTheme.typography.titleSmall, color = tint, modifier = Modifier.padding(top = 4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = AnsuColors.TextTertiary)
    }
}

/** A clickable person card used for both the Characters and Staff grids on the details page. */
@Composable
fun PersonCard(imageUrl: String?, name: String, role: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(88.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(88.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(AnsuColors.BackgroundElevated),
        ) {
            if (imageUrl != null) {
                AsyncImage(model = imageUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            name,
            style = MaterialTheme.typography.labelSmall,
            color = AnsuColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            role,
            style = MaterialTheme.typography.labelSmall,
            color = AnsuColors.TextTertiary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}
