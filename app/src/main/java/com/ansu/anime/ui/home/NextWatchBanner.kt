package com.ansu.anime.ui.home

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.core.haptics.softClickable
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.ui.components.ShelfHeader
import com.ansu.anime.ui.components.posterTransitionOrigin
import com.ansu.anime.ui.components.rememberIsResumed
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.abs

/** How long a card rests before the banner slides on to the next one on its own. */
private const val AUTO_ADVANCE_MS = 4500L

/** How long the slide to the next card takes. */
private const val SLIDE_MS = 750

/**
 * "Your Next Watch": a horizontal banner of titles picked from what the user watches. Each wide card
 * shows the title on top and, at the bottom, the show of theirs it was suggested by.
 *
 * The cards peek in from both sides and the banner loops endlessly. It slides on by itself every few
 * seconds: the centred card is full size, its neighbours are slightly smaller and dimmer, and the
 * text on a card lags behind the picture a little, so it appears to slide in after it.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun NextWatchBanner(
    picks: List<WatchPick>,
    onClick: (SAnime) -> Unit,
    modifier: Modifier = Modifier,
    header: String = "Your Next Watch",
    /** Shown before [WatchPick.becauseOf] on the bottom line; empty when that text already says it all. */
    captionPrefix: String = "Because you watched ",
) {
    if (picks.isEmpty()) return
    val loops = picks.size > 1
    // Same trick as the hero banner: an enormous page count, page N shows pick N % size, so it never ends.
    val startPage = remember(picks.size) { (Int.MAX_VALUE / 2).let { it - it % picks.size } }
    val pagerState = rememberPagerState(
        initialPage = if (loops) startPage else 0,
        pageCount = { if (loops) Int.MAX_VALUE else picks.size },
    )

    // The timer restarts after every settled page, so a manual swipe gets a full rest, and it never fires
    // while a finger is on the banner.
    // It also waits for the home screen to be in front, so a slide never starts while a details page opens over it.
    val resumed = rememberIsResumed()
    LaunchedEffect(picks, pagerState.settledPage) {
        if (!loops) return@LaunchedEffect
        delay(AUTO_ADVANCE_MS)
        if (!resumed.value) {
            snapshotFlow { resumed.value }.first { it }
            delay(1500)
        }
        if (!pagerState.isScrollInProgress) {
            pagerState.animateScrollToPage(
                pagerState.currentPage + 1,
                animationSpec = tween(durationMillis = SLIDE_MS, easing = FastOutSlowInEasing),
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ShelfHeader(header)
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 28.dp),
            pageSpacing = 12.dp,
        ) { page ->
            val pick = picks[page % picks.size]
            // 0 on the centred card; -1 / +1 when a card is a whole page to the right / left.
            val offset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
            NextWatchCard(
                pick = pick,
                captionPrefix = captionPrefix,
                offset = { offset },
                onClick = { onClick(pick.anime) },
            )
        }
    }
}

@Composable
private fun NextWatchCard(
    pick: WatchPick,
    captionPrefix: String,
    offset: () -> Float,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(176.dp)
            .graphicsLayer {
                val distance = abs(offset()).coerceIn(0f, 1f)
                val scale = 1f - 0.08f * distance
                scaleX = scale
                scaleY = scale
                alpha = 1f - 0.45f * distance
            }
            // Tapping a card opens its details with black bars growing out of the card's top and bottom edges
            // until the screen is covered (not the poster-window morph the other cards use).
            .posterTransitionOrigin(posterUrl = pick.anime.bannerUrl ?: pick.anime.posterUrl, cornerRadius = 20.dp, bars = true)
            .clip(shape)
            .background(AnsuColors.BackgroundElevated)
            .softClickable(onClick = onClick),
    ) {
        AsyncImage(
            model = pick.anime.bannerUrl ?: pick.anime.posterUrl,
            contentDescription = pick.anime.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        // Darkens the top (the title) and the bottom (the reason) so both stay readable on any picture.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.62f),
                        0.45f to Color.Black.copy(alpha = 0.10f),
                        1f to Color.Black.copy(alpha = 0.72f),
                    ),
                ),
        )
        // The text lags behind the picture: it is pushed the way the card is moving away from, then slides home.
        val lag: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
            translationX = -offset() * size.width * 0.3f
        }
        Text(
            text = pick.anime.title,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                .graphicsLayer(lag),
        )
        Text(
            text = buildAnnotatedString {
                if (captionPrefix.isNotEmpty()) {
                    withStyle(SpanStyle(color = Color.White.copy(alpha = 0.72f))) { append(captionPrefix) }
                }
                withStyle(SpanStyle(color = Color.White, fontWeight = FontWeight.SemiBold)) { append(pick.becauseOf) }
            },
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp)
                .graphicsLayer(lag),
        )
    }
}
