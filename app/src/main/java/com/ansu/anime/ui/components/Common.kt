package com.ansu.anime.ui.components

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import kotlin.math.abs
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.util.ageRatingFor
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

/**
 * A poster row that never runs dry: when the user scrolls to within a few posters of
 * the end it calls [onLoadMore], and a trailing spinner shows while the next page is
 * fetched. If a page fails the spinner becomes a retry button (no automatic retry loop).
 */
@Composable
fun PagedPosterRow(
    items: List<SAnime>,
    hasMore: Boolean,
    isLoadingMore: Boolean,
    loadFailed: Boolean,
    onLoadMore: () -> Unit,
    onClick: (SAnime) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()
    val nearEnd by remember(hasMore) {
        derivedStateOf {
            val info = listState.layoutInfo
            val lastVisible = info.visibleItemsInfo.lastOrNull()?.index ?: -1
            hasMore && lastVisible >= info.totalItemsCount - 3
        }
    }
    // Keyed on the list size and loading flag too, so a page that lands while the row
    // is still near the end immediately triggers the next one.
    LaunchedEffect(nearEnd, items.size, isLoadingMore, loadFailed) {
        if (nearEnd && !isLoadingMore && !loadFailed) onLoadMore()
    }

    LazyRow(
        state = listState,
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(items, key = { it.id }) { anime ->
            AnimeCard(anime = anime, onClick = { onClick(anime) })
        }
        if (hasMore) {
            item(key = "paged-row-footer") {
                Box(
                    modifier = Modifier.width(56.dp).height(195.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    if (loadFailed) {
                        IconButton(onClick = onLoadMore) {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Retry loading more",
                                tint = AnsuColors.TextPrimary,
                            )
                        }
                    } else {
                        CircularProgressIndicator(
                            color = AnsuColors.Accent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AnimeCard(anime: SAnime, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(modifier = modifier.width(130.dp).clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(12.dp))
                .background(AnsuColors.BackgroundElevated),
        ) {
            AsyncImage(
                model = anime.posterUrl,
                contentDescription = anime.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            AgeRatingChip(
                rating = anime.ageRating ?: ageRatingFor(anime.genres, isAdult = false),
                modifier = Modifier.align(Alignment.TopStart).padding(6.dp),
            )
        }
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

/**
 * The small frosted-glass age-rating chip pinned to a poster's top-left corner. Real backdrop
 * blur is not available without extra plumbing (see [FrostedGlassCard]), so the glass is a dark
 * tint with a white sheen and a light stroke, which stays legible on both bright and dark art.
 */
@Composable
fun AgeRatingChip(rating: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.32f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f))))
            .border(width = 0.75.dp, color = Color.White.copy(alpha = 0.30f), shape = shape)
            .padding(horizontal = 6.dp, vertical = 2.dp),
    ) {
        Text(
            text = rating,
            color = AnsuColors.TextPrimary,
            fontSize = 10.sp,
            lineHeight = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Frosted-glass label pinned along a poster's bottom edge (e.g. "Season 2", "Sequel · Movie"),
 * styled like [AgeRatingChip] so the top-left corner stays free for the age rating.
 */
@Composable
fun PosterGlassLabel(text: String, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(8.dp)
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.Black.copy(alpha = 0.32f))
            .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.06f))))
            .border(width = 0.75.dp, color = Color.White.copy(alpha = 0.30f), shape = shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = AnsuColors.TextPrimary,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
 * Swipeable hero carousel: the poster fills the screen top to bottom, bottom scrim, centered title/genres,
 * and a centered "View Details" (white) + "Like" (frosted glass) button pair, with
 * page dots below. Auto-rotates every 3 seconds. [onToggleFavourite] calls straight through to the real AniList
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
    /** Resolves the title-logo image URL for an AniList id; null (or no logo) shows the plain text title. */
    logoFor: suspend (Int) -> String? = { null },
) {
    if (items.isEmpty()) return
    val shown = items.take(8)
    val pagerState = rememberPagerState(pageCount = { shown.size })
    val likedIds = remember { mutableStateMapOf<String, Boolean>() }
    // AniList id -> logo URL. A key with a null value means "looked up, no logo", so it is not asked twice.
    val logos = remember { mutableStateMapOf<Int, String?>() }
    
    // Auto-rotate: the timer restarts after every settled page (so a manual swipe gets a full pause)
    // and never fires while a finger is on the pager.
    LaunchedEffect(shown, pagerState.settledPage) {
        kotlinx.coroutines.delay(4000)
        if (!pagerState.isScrollInProgress && shown.size > 1) {
            val nextPage = (pagerState.currentPage + 1) % shown.size
            pagerState.animateScrollToPage(nextPage, animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing))
        }
    }

    LaunchedEffect(shown) {
        shown.forEach { anime ->
            val id = anime.anilistId ?: return@forEach
            if (!logos.containsKey(id)) launch { logos[id] = logoFor(id) }
        }
    }

    // One fixed height for every slide: the old fillMaxSize inside a scrolling list let each poster's own
    // image size decide the banner height, so it jumped while swiping and while images loaded.
    val screenHeight = LocalConfiguration.current.screenHeightDp
    val heroHeight = (screenHeight * 0.68f).dp.coerceIn(460.dp, 620.dp)

    Column(modifier = modifier.fillMaxWidth()) {
        // Draws from the very top of the window, behind the status bar (no inset padding above it).
        Box(modifier = Modifier.fillMaxWidth().height(heroHeight)) {
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                val anime = shown[page]
                // 0 when this page is centred, -1/+1 when it is fully off to one side.
                val offset = ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                val isLiked = likedIds[anime.id] == true
                Box(modifier = Modifier.fillMaxSize()) {
                    Box(modifier = Modifier.fillMaxSize().clipToBounds()) {
                        // AniList's own portrait cover (extraLarge); the banner is only a fallback.
                        // Slightly oversized and shifted against the swipe, so the art trails the page (parallax).
                        AsyncImage(
                            model = anime.posterUrl ?: anime.bannerUrl,
                            contentDescription = anime.title,
                            contentScale = ContentScale.Crop,
                            colorFilter = HeroBrightness,
                            modifier = Modifier
                                .fillMaxSize()
                                .background(AnsuColors.BackgroundElevated)
                                .graphicsLayer {
                                    scaleX = 1.16f
                                    scaleY = 1.16f
                                    translationX = offset * size.width * 0.07f
                                },
                        )
                    }
                    // Fade into the page colour: the banner melts into "Continue Watching" below.
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    0.45f to Color.Transparent,
                                    0.72f to AnsuColors.Background.copy(alpha = 0.7f),
                                    1f to AnsuColors.Background,
                                ),
                            ),
                    )
                    // Title, genres and buttons belong to the page, so they slide with it.
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 24.dp)
                            .padding(bottom = 12.dp)
                            .graphicsLayer { alpha = 1f - abs(offset) * 0.6f },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TitleLogo(
                            title = anime.title,
                            logoUrl = anime.anilistId?.let { logos[it] },
                            // Wait for the lookup before falling back to text, so the lettering does not flash.
                            lookupDone = anime.anilistId == null || logos.containsKey(anime.anilistId),
                            alignment = Alignment.Center,
                            textAlign = TextAlign.Center,
                            fontSize = 27.sp,
                            uppercase = false,
                            maxLogoWidth = 320.dp,
                            maxLogoHeight = 130.dp,
                        )
                        anime.genres.take(3).takeIf { it.isNotEmpty() }?.let { genres ->
                            Text(
                                text = genres.joinToString(" • ") + anime.releaseYear?.let { " • $it" }.orEmpty(),
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
                                onClick = { onClick(anime) },
                                colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                                shape = RoundedCornerShape(24.dp),
                                contentPadding = PaddingValues(horizontal = 28.dp, vertical = 12.dp),
                            ) {
                                Text("View Details", color = AnsuColors.OnAccent, fontWeight = FontWeight.Bold)
                            }
                            FrostedGlassCard(modifier = Modifier.size(48.dp), shape = CircleShape, tintAlpha = 0.5f) {
                                IconButton(
                                    onClick = {
                                        likedIds[anime.id] = !isLiked
                                        onToggleFavourite(anime)
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
            }

            // Keeps the status-bar icons legible over bright artwork.
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(96.dp)
                    .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.4f), Color.Transparent))),
            )
        }

        if (shown.size > 1) {
            Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.Center) {
                shown.indices.forEach { index ->
                    val selected = index == pagerState.currentPage
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 18.dp else 6.dp,
                        animationSpec = tween(durationMillis = 300),
                        label = "heroDot",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .height(6.dp)
                            .width(dotWidth)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (selected) AnsuColors.Accent else AnsuColors.TextTertiary),
                    )
                }
            }
        }
    }
}

/** Slight lift for the hero artwork: about +12% brightness, so dark posters are not swallowed by the page. */
private val HeroBrightness = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            1.12f, 0f, 0f, 0f, 6f,
            0f, 1.12f, 0f, 0f, 6f,
            0f, 0f, 1.12f, 0f, 6f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

/** Result of preparing a title logo: still loading, unusable (not a real logo), or ready to draw. */
private sealed interface LogoState {
    data object Loading : LogoState
    data object Unusable : LogoState
    class Ready(val bitmap: ImageBitmap) : LogoState {
        val ratio: Float get() = bitmap.width.toFloat() / bitmap.height.toFloat()
    }
}

/**
 * Downloads [url], crops away its transparent margins (many logos ship with a lot of empty padding,
 * which made them render tiny) and rejects images that are not logos at all: an opaque picture such
 * as a square poster has no transparent background, so it is reported [LogoState.Unusable] and the
 * caller shows the text title instead.
 */
@Composable
private fun rememberLogoState(url: String?): LogoState {
    val context = LocalContext.current
    val state by produceState<LogoState>(if (url == null) LogoState.Unusable else LogoState.Loading, url) {
        if (url == null) {
            value = LogoState.Unusable
            return@produceState
        }
        value = withContext(Dispatchers.Default) {
            val result = context.imageLoader.execute(
                ImageRequest.Builder(context).data(url).allowHardware(false).size(900).build(),
            )
            val source = (result as? SuccessResult)?.drawable?.let { (it as? BitmapDrawable)?.bitmap }
                ?: return@withContext LogoState.Unusable
            trimLogo(source)?.let { LogoState.Ready(it.asImageBitmap()) } ?: LogoState.Unusable
        }
    }
    return state
}

/**
 * Crops [source] to its visible pixels; null when it is blank or a picture rather than a logo.
 *
 * Faint glow/shadow pixels (alpha up to 48) are ignored when measuring the crop box, so a soft halo
 * no longer keeps the logo's empty margin and makes it render small. A picture is rejected when the
 * cropped box is almost solid (a poster or screenshot, even one with a thin transparent border) or
 * when its four corners are all opaque (a rectangle, which lettering never is).
 */
private fun trimLogo(source: Bitmap): Bitmap? {
    val w = source.width
    val h = source.height
    if (w < 2 || h < 2) return null
    val pixels = IntArray(w * h)
    source.getPixels(pixels, 0, w, 0, 0, w, h)
    var minX = w; var minY = h; var maxX = -1; var maxY = -1
    for (y in 0 until h) {
        for (x in 0 until w) {
            if ((pixels[y * w + x] ushr 24) > 48) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return null
    val bw = maxX - minX + 1
    val bh = maxY - minY + 1
    if (bw < 8 || bh < 8) return null

    var solid = 0
    for (y in minY..maxY) {
        for (x in minX..maxX) {
            if ((pixels[y * w + x] ushr 24) > 240) solid++
        }
    }
    val fill = solid.toFloat() / (bw * bh)
    // Lettering leaves plenty of see-through gaps inside its box; a picture fills it.
    if (fill > 0.82f) return null

    fun opaqueAt(x: Int, y: Int): Boolean = (pixels[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)] ushr 24) > 200
    val inset = (minOf(bw, bh) * 0.03f).toInt().coerceAtLeast(1)
    val cornersOpaque = opaqueAt(minX + inset, minY + inset) && opaqueAt(maxX - inset, minY + inset) &&
        opaqueAt(minX + inset, maxY - inset) && opaqueAt(maxX - inset, maxY - inset)
    if (cornersOpaque) return null

    // A near-square or tall box is a poster/emblem, not a title line; drawn at logo size it reads as
    // a tiny thumbnail where the title should be.
    val ratio = bw.toFloat() / bh
    if (ratio < 0.9f && fill > 0.6f) return null

    return Bitmap.createBitmap(source, minX, minY, bw, bh)
}

/**
 * The show's title in its own poster lettering ([logoUrl], a transparent logo image). The logo is
 * trimmed of empty margins and scaled to fill [maxLogoWidth] x [maxLogoHeight] as far as its shape
 * allows, so wide and tall logos both read at a good size. Falls back to the plain text [title] when
 * there is no logo, when it is not a real (transparent) logo, when it fails to load, or - once
 * [lookupDone] - when the lookup found none. While the lookup is still running nothing is drawn.
 */
@Composable
fun TitleLogo(
    title: String,
    logoUrl: String?,
    lookupDone: Boolean,
    alignment: Alignment,
    textAlign: TextAlign,
    fontSize: TextUnit,
    uppercase: Boolean,
    maxLogoWidth: Dp,
    maxLogoHeight: Dp,
    modifier: Modifier = Modifier,
) {
    val logo = rememberLogoState(logoUrl)
    when {
        logo is LogoState.Ready -> {
            // Size by visual area instead of fitting a fixed box: a squarish logo is allowed to be
            // taller, a very wide one wider, so every title reads at a similar weight rather than
            // some filling the box and others shrinking to a sliver.
            val ratio = logo.ratio
            val targetArea = maxLogoWidth.value * maxLogoHeight.value * 0.75f
            var width = kotlin.math.sqrt(targetArea * ratio)
            var height = width / ratio
            if (width > maxLogoWidth.value) { width = maxLogoWidth.value; height = width / ratio }
            if (height > maxLogoHeight.value) { height = maxLogoHeight.value; width = height * ratio }
            Image(
                bitmap = logo.bitmap,
                contentDescription = title,
                contentScale = ContentScale.Fit,
                alignment = alignment,
                modifier = modifier.size(width = width.dp, height = height.dp),
            )
        }
        logo is LogoState.Unusable && (lookupDone || logoUrl != null) -> Text(
            text = if (uppercase) title.uppercase() else title,
            color = AnsuColors.TextPrimary,
            fontSize = fontSize,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = textAlign,
            modifier = modifier,
        )
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
