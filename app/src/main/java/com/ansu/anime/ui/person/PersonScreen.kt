package com.ansu.anime.ui.person

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.ansu.anime.anilist.AniListMedia
import com.ansu.anime.core.util.BioBlock
import com.ansu.anime.core.util.PersonKind
import com.ansu.anime.core.util.PersonPreview
import com.ansu.anime.core.util.parseBio
import com.ansu.anime.data.repository.toSAnime
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.BouncyIconButton
import com.ansu.anime.ui.components.ErrorGlassCard
import com.ansu.anime.ui.components.posterTransitionOrigin
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** A small round picture-and-name entry of a horizontal row: a voice actor, a voiced character. */
private class SquareEntry(val id: Int, val kind: PersonKind, val name: String, val imageUrl: String?, val caption: String?)

/** A show of the grid, with the line under its title (the character's role there, or the staff member's credit). */
private class ShowEntry(val media: AniListMedia, val caption: String)

private class PersonPage(
    val name: String,
    val nativeName: String?,
    val imageUrl: String?,
    val subtitle: String?,
    val description: String?,
    val isFavourite: Boolean,
    val rowTitle: String,
    val row: List<SquareEntry>,
    val shows: List<ShowEntry>,
)

private sealed interface PageState {
    data object Loading : PageState
    data object Failed : PageState
    data class Ready(val page: PersonPage) : PageState
}

/**
 * A character's or staff member's page. Top: back, favourite heart and share; then the portrait over a slowly
 * flowing gradient made from the portrait's own colours, with the name and the Japanese name beside it; then the
 * short profile (facts card and bio). A character then lists its voice actors (picture, name, the language they
 * dubbed) and the shows it is in; a staff member lists the characters they voiced and the shows they dubbed or
 * worked on, in the same layout.
 */
@Composable
fun PersonScreen(container: AppContainer, navController: NavHostController, kind: PersonKind, id: Int) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repo = container.aniListRepository
    val preview = remember(kind, id) { container.selectionHolder.personPreview(kind, id) }
    var retryTick by remember { mutableIntStateOf(0) }

    val state by produceState<PageState>(PageState.Loading, kind, id, retryTick) {
        value = PageState.Loading
        value = when (kind) {
            PersonKind.CHARACTER -> repo.getCharacterDetails(id)?.let { d ->
                PageState.Ready(
                    PersonPage(
                        name = d.name,
                        nativeName = d.nativeName,
                        imageUrl = d.imageUrl,
                        subtitle = null,
                        description = d.description,
                        isFavourite = d.isFavourite,
                        rowTitle = "Voice Actors",
                        row = d.voiceActors.map { SquareEntry(it.id, PersonKind.STAFF, it.name, it.imageUrl, it.language.ifBlank { null }) },
                        shows = d.shows.map { ShowEntry(it.media, it.role.lowercase().replaceFirstChar { c -> c.uppercase() }) },
                    ),
                )
            }
            PersonKind.STAFF -> repo.getStaffDetails(id)?.let { d ->
                PageState.Ready(
                    PersonPage(
                        name = d.name,
                        nativeName = d.nativeName,
                        imageUrl = d.imageUrl,
                        subtitle = (d.occupations.take(2) + listOfNotNull(d.language)).joinToString(" · ").ifBlank { null },
                        description = d.description,
                        isFavourite = d.isFavourite,
                        rowTitle = "Characters",
                        row = d.voicedCharacters.map { SquareEntry(it.id, PersonKind.CHARACTER, it.name, it.imageUrl, it.showTitle) },
                        shows = d.works.map { ShowEntry(it.media, it.credit) },
                    ),
                )
            }
        } ?: PageState.Failed
    }
    val page = (state as? PageState.Ready)?.page

    val name = page?.name ?: preview?.name.orEmpty()
    val imageUrl = page?.imageUrl ?: preview?.imageUrl
    val subtitle = page?.subtitle ?: preview?.subtitle
    val colors = rememberPortraitColors(imageUrl)

    // The heart starts from AniList's answer and then follows the taps.
    var favourite by remember(page) { mutableStateOf(page?.isFavourite == true) }
    val loggedIn by repo.isLoggedIn.collectAsStateWithLifecycle()

    val listState = rememberLazyListState()

    fun open(entry: SquareEntry) {
        navController.navigate(container.selectionHolder.openPerson(PersonPreview(entry.kind, entry.id, entry.name, entry.imageUrl, entry.caption)))
    }

    Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
        // The gradient is part of the page: it scrolls away with the header instead of staying behind the list.
        FlowingGradient(
            colors = colors,
            modifier = Modifier
                .fillMaxWidth()
                .height(520.dp)
                .graphicsLayer {
                    translationY = if (listState.firstVisibleItemIndex == 0) -listState.firstVisibleItemScrollOffset.toFloat() else -size.height
                },
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 40.dp)) {
            item(key = "bar") {
                Row(
                    modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundIconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.weight(1f))
                    RoundIconButton(
                        onClick = {
                            if (!loggedIn) {
                                Toast.makeText(context, "Log in to AniList to save favourites", Toast.LENGTH_SHORT).show()
                            } else if (page != null) {
                                val before = favourite
                                favourite = !before
                                scope.launch {
                                    val ok = if (kind == PersonKind.CHARACTER) repo.toggleFavouriteCharacter(id) else repo.toggleFavouriteStaff(id)
                                    if (!ok) favourite = before
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = if (favourite) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                            contentDescription = if (favourite) "Remove from favourites" else "Add to favourites",
                            tint = if (favourite) Color(0xFFFF5A7A) else Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Spacer(Modifier.width(10.dp))
                    RoundIconButton(
                        onClick = {
                            val path = if (kind == PersonKind.CHARACTER) "character" else "staff"
                            val send = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, "$name\nhttps://anilist.co/$path/$id")
                            }
                            context.startActivity(Intent.createChooser(send, null))
                        },
                    ) {
                        Icon(Icons.Filled.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }

            item(key = "header") {
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)) {
                    Box(
                        modifier = Modifier
                            .width(128.dp)
                            .aspectRatio(2f / 3f)
                            .clip(Squircle(18.dp))
                            .background(AnsuColors.BackgroundElevated),
                    ) {
                        if (imageUrl != null) {
                            AsyncImage(model = imageUrl, contentDescription = name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                        }
                    }
                    Column(modifier = Modifier.weight(1f).padding(start = 16.dp, top = 28.dp)) {
                        Text(
                            text = name,
                            color = Color.White,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        page?.nativeName?.let {
                            Text(it, color = Color.White.copy(alpha = 0.78f), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                        subtitle?.let {
                            Text(it, color = Color.White.copy(alpha = 0.6f), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                        }
                    }
                }
            }

            when (val s = state) {
                PageState.Loading -> item(key = "loading") {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(28.dp))
                    }
                }
                PageState.Failed -> item(key = "failed") {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), contentAlignment = Alignment.Center) {
                        ErrorGlassCard(
                            title = "Couldn't load this page",
                            message = "Check your connection and try again.",
                            onRetry = { retryTick += 1 },
                        )
                    }
                }
                is PageState.Ready -> {
                    val blocks = parseBio(s.page.description)
                    val facts = blocks.filterIsInstance<BioBlock.Fact>()
                    val paragraphs = blocks.filterIsInstance<BioBlock.Paragraph>()
                    item(key = "bio") { BioSection(facts, paragraphs) }

                    if (s.page.row.isNotEmpty()) {
                        item(key = "row-title") { SectionTitle(s.page.rowTitle) }
                        item(key = "row") {
                            LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                                itemsIndexed(s.page.row, key = { index, entry -> "${entry.kind}:${entry.id}#$index" }) { _, entry ->
                                    SquareCard(entry = entry, onClick = { open(entry) })
                                }
                            }
                        }
                    }

                    if (s.page.shows.isNotEmpty()) {
                        item(key = "shows-title") { SectionTitle("Shows") }
                        // Three posters a row; built from rows so no lazy grid sits inside the scrolling list.
                        val rows = s.page.shows.chunked(3)
                        itemsIndexed(rows, key = { index, _ -> "shows#$index" }) { _, rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 14.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                rowItems.forEach { show ->
                                    Box(modifier = Modifier.weight(1f)) {
                                        ShowCard(
                                            show = show,
                                            onClick = {
                                                container.selectionHolder.selectAnime(show.media.toSAnime())
                                                navController.navigate(Dest.DETAILS)
                                            },
                                        )
                                    }
                                }
                                repeat(3 - rowItems.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RoundIconButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    BouncyIconButton(
        onClick = onClick,
        modifier = Modifier.size(42.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.38f)),
        content = content,
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        color = AnsuColors.TextPrimary,
        fontSize = 19.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 12.dp),
    )
}

/** The facts card ("Height: 188 cm" rows) and the bio paragraphs, the same short profile the old sheet showed. */
@Composable
private fun BioSection(facts: List<BioBlock.Fact>, paragraphs: List<BioBlock.Paragraph>) {
    if (facts.isEmpty() && paragraphs.isEmpty()) {
        Text(
            "No further bio available.",
            style = MaterialTheme.typography.bodyMedium,
            color = AnsuColors.TextSecondary,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 12.dp),
        )
        return
    }
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(top = 14.dp)) {
        if (facts.isNotEmpty()) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color.Black.copy(alpha = 0.55f))
                    .padding(16.dp),
            ) {
                facts.forEach { fact ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(text = fact.label, color = AnsuColors.TextSecondary, fontSize = 13.sp, modifier = Modifier.weight(0.34f))
                        Text(text = fact.value, color = AnsuColors.TextPrimary, fontSize = 14.sp, lineHeight = 20.sp, modifier = Modifier.weight(0.66f))
                    }
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp), modifier = Modifier.fillMaxWidth().padding(top = 18.dp)) {
            paragraphs.forEach { paragraph ->
                Text(text = paragraph.text, color = AnsuColors.TextPrimary, fontSize = 14.sp, lineHeight = 22.sp)
            }
        }
    }
}

/** Squircle picture, name, and a caption (the dub language, or the show a voiced character is from). */
@Composable
private fun SquareCard(entry: SquareEntry, onClick: () -> Unit) {
    Column(modifier = Modifier.width(92.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.size(92.dp).clip(Squircle(12.dp)).background(AnsuColors.BackgroundElevated)) {
            if (entry.imageUrl != null) {
                AsyncImage(model = entry.imageUrl, contentDescription = entry.name, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
        }
        Text(
            entry.name,
            style = MaterialTheme.typography.labelMedium,
            color = AnsuColors.TextPrimary,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 6.dp),
        )
        entry.caption?.let {
            Text(
                it,
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** A show's poster with its title and the caption under it; opens the details page out of the poster. */
@Composable
private fun ShowCard(show: ShowEntry, onClick: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .posterTransitionOrigin(show.media.posterUrl, 14.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(AnsuColors.BackgroundElevated),
        ) {
            AsyncImage(model = show.media.posterUrl, contentDescription = show.media.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Text(
            show.media.title,
            color = AnsuColors.TextPrimary,
            fontSize = 12.sp,
            lineHeight = 15.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (show.caption.isNotBlank()) {
            Text(
                show.caption,
                color = AnsuColors.TextTertiary,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/** Rounded square with "continuous" corners, the shape the app's posters and cast pictures use. */
private class Squircle(private val radius: Dp) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val r = with(density) { radius.toPx() }
        val half = min(size.width, size.height) / 2f
        val e = (r * 1.28f).coerceAtMost(half)
        val k = e * 0.36f
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(e, 0f)
            lineTo(w - e, 0f)
            cubicTo(w - k, 0f, w, k, w, e)
            lineTo(w, h - e)
            cubicTo(w, h - k, w - k, h, w - e, h)
            lineTo(e, h)
            cubicTo(k, h, 0f, h - k, 0f, h - e)
            lineTo(0f, e)
            cubicTo(0f, k, k, 0f, e, 0f)
            close()
        }
        return Outline.Generic(path)
    }
}

/**
 * Three soft blobs of the portrait's colours drifting slowly over a tinted base, fading into the black page at
 * the bottom. The drift is read in the draw phase only, so it never recomposes.
 */
@Composable
private fun FlowingGradient(colors: List<Color>, modifier: Modifier = Modifier) {
    val c0 by animateColorAsState(colors[0], tween(700), label = "personGradient0")
    val c1 by animateColorAsState(colors[1], tween(700), label = "personGradient1")
    val c2 by animateColorAsState(colors[2], tween(700), label = "personGradient2")
    val transition = rememberInfiniteTransition(label = "personGradientFlow")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "personGradientPhase",
    )
    val page = AnsuColors.Background
    Canvas(modifier = modifier) {
        drawRect(c0.copy(alpha = 0.32f))
        val radius = size.width * 0.9f
        listOf(c0, c1, c2).forEachIndexed { i, color ->
            val a = phase + i * 2.1f
            val center = Offset(
                x = size.width * (0.5f + 0.45f * cos(a)),
                y = size.height * (0.32f + 0.2f * sin(a * 0.8f + i)),
            )
            drawCircle(
                brush = Brush.radialGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0f)), center = center, radius = radius),
                radius = radius,
                center = center,
            )
        }
        drawRect(Brush.verticalGradient(0f to Color.Transparent, 1f to page))
    }
}

private val colorCache = HashMap<String, List<Color>>()

/** The three main colours of the picture at [url] (a calm blue-violet set while it loads or when it has none). */
@Composable
private fun rememberPortraitColors(url: String?): List<Color> {
    val context = LocalContext.current
    val fallback = remember { listOf(Color(0xFF3B3F9A), Color(0xFF7A3A8C), Color(0xFF1F6B72)) }
    val colors by produceState(initialValue = url?.let { colorCache[it] } ?: fallback, url) {
        if (url == null) return@produceState
        colorCache[url]?.let {
            value = it
            return@produceState
        }
        val found = withContext(Dispatchers.Default) {
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(url).allowHardware(false).size(96).build())
            val bitmap = ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            bitmap?.let { dominantColors(it) }
        }
        if (found != null) {
            colorCache[url] = found
            value = found
        }
    }
    return colors
}

/**
 * Picks three distinct, vivid colours from [source]: pixels are grouped into twelve hue bands, weighted by how
 * saturated and bright they are (so a white or black background does not win), and the three strongest bands that
 * are far enough apart in hue become the gradient. Bands that are missing are made from the first one, shifted in hue.
 */
private fun dominantColors(source: Bitmap): List<Color>? {
    val small = Bitmap.createScaledBitmap(source, 32, 32, true)
    val weight = FloatArray(12)
    val hueSum = FloatArray(12)
    val satSum = FloatArray(12)
    val valSum = FloatArray(12)
    val hsv = FloatArray(3)
    for (y in 0 until small.height) {
        for (x in 0 until small.width) {
            val px = small.getPixel(x, y)
            if ((px ushr 24) < 128) continue
            android.graphics.Color.colorToHSV(px, hsv)
            val s = hsv[1]
            val v = hsv[2]
            if (v < 0.18f || s < 0.2f) continue
            val band = (hsv[0] / 30f).toInt().coerceIn(0, 11)
            val w = s * s * v + 0.02f
            weight[band] += w
            hueSum[band] += hsv[0] * w
            satSum[band] += s * w
            valSum[band] += v * w
        }
    }
    val order = (0 until 12).filter { weight[it] > 0f }.sortedByDescending { weight[it] }
    if (order.isEmpty()) return null
    val hues = ArrayList<Float>()
    val result = ArrayList<Color>()
    fun add(h: Float, s: Float, v: Float) {
        result.add(Color(android.graphics.Color.HSVToColor(floatArrayOf((h + 360f) % 360f, s.coerceIn(0.45f, 0.9f), v.coerceIn(0.55f, 0.95f)))))
        hues.add((h + 360f) % 360f)
    }
    for (band in order) {
        if (result.size == 3) break
        val h = hueSum[band] / weight[band]
        val far = hues.all { other -> min(abs(h - other), 360f - abs(h - other)) >= 40f }
        if (far) add(h, satSum[band] / weight[band], valSum[band] / weight[band])
    }
    val baseHue = hues.first()
    var shift = 28f
    while (result.size < 3) {
        add(baseHue + shift, 0.6f, 0.75f)
        shift = -shift - 12f
    }
    return result
}
