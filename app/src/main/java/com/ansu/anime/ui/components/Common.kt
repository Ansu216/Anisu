package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.util.formatEpisodeNumber
import com.ansu.anime.core.util.progressFraction
import com.ansu.anime.data.db.ContinueWatchingEntity
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnisuGold
import com.ansu.anime.ui.theme.AnisuSurfaceRaised
import com.ansu.anime.ui.theme.AnisuTextSecondary

@Composable
fun ShelfHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp),
    )
}

/** A poster-only catalogue row - the generic browse shelf used for extension/addon/AniList lists. */
@Composable
fun PosterRow(items: List<SAnime>, onClick: (SAnime) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(items, key = { it.origin.hashCode().toLong() + it.id.hashCode() }) { anime ->
            AnimeCard(anime = anime, onClick = { onClick(anime) })
        }
    }
}

@Composable
fun AnimeCard(anime: SAnime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(120.dp).clickable(onClick = onClick)) {
        AsyncImage(
            model = anime.posterUrl,
            contentDescription = anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.small)
                .background(AnisuSurfaceRaised),
        )
        Text(
            text = anime.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/**
 * The CornCastle-style continue-watching card: a wide thumbnail with a
 * bottom progress bar burned onto the image and the episode label under it,
 * distinct in shape from the plain poster cards elsewhere on the page.
 */
@Composable
fun ContinueWatchingCard(entry: ContinueWatchingEntity, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(200.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(MaterialTheme.shapes.medium)
                .background(AnisuSurfaceRaised),
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
                tint = Color.White,
                modifier = Modifier.align(Alignment.Center).size(36.dp),
            )
            // Progress bar burned onto the bottom edge of the thumbnail.
            Box(modifier = Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f)))
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(progressFraction(entry.positionSeconds, entry.durationSeconds))
                    .height(3.dp)
                    .background(AnisuGold),
            )
        }
        Text(
            text = entry.title,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        Text(
            text = "Episode ${entry.episodeNumber.formatEpisodeNumber()}",
            style = MaterialTheme.typography.labelSmall,
            color = AnisuTextSecondary,
        )
    }
}

@Composable
fun ContinueWatchingRow(entries: List<ContinueWatchingEntity>, onClick: (ContinueWatchingEntity) -> Unit, modifier: Modifier = Modifier) {
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(entries, key = { it.anilistId }) { entry ->
            ContinueWatchingCard(entry = entry, onClick = { onClick(entry) })
        }
    }
}

/** The Nuvio-style hero banner at the top of the home screen. */
@Composable
fun HeroCarousel(items: List<SAnime>, onClick: (SAnime) -> Unit, modifier: Modifier = Modifier) {
    if (items.isEmpty()) return
    val pagerState = rememberPagerState(pageCount = { items.size.coerceAtMost(8) })

    HorizontalPager(state = pagerState, modifier = modifier.fillMaxWidth().height(220.dp)) { page ->
        val anime = items[page]
        Box(modifier = Modifier.fillMaxSize().clickable { onClick(anime) }) {
            AsyncImage(
                model = anime.bannerUrl ?: anime.posterUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().background(AnisuSurfaceRaised),
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)))),
            )
            Column(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Text(
                    text = anime.title,
                    style = MaterialTheme.typography.displayLarge.copy(fontSize = 24.sp),
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                anime.genres.take(3).takeIf { it.isNotEmpty() }?.let { genres ->
                    Text(
                        text = genres.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.8f),
                    )
                }
            }
        }
    }
}

@Composable
fun AnisuBottomBar(navController: NavHostController, currentRoute: String?) {
    data class BarItem(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

    val items = listOf(
        BarItem(Dest.HOME, "Home", Icons.Filled.Home),
        BarItem(Dest.SEARCH, "Search", Icons.Filled.Search),
        BarItem(Dest.LIBRARY, "Library", Icons.Filled.VideoLibrary),
        BarItem(Dest.SETTINGS, "Settings", Icons.Filled.Settings),
    )

    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
        items.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = {
                    navController.navigate(item.route) {
                        popUpTo(Dest.HOME) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) },
                colors = androidx.compose.material3.NavigationBarItemDefaults.colors(
                    selectedIconColor = AnisuGold,
                    selectedTextColor = AnisuGold,
                    indicatorColor = AnisuSurfaceRaised,
                ),
            )
        }
    }
}
