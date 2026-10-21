package com.ansu.anime.ui.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.core.model.SAnime
import com.ansu.anime.core.model.SEpisode
import com.ansu.anime.data.prefs.TitleLanguage
import com.ansu.anime.data.repository.AnimeArtwork
import com.ansu.anime.data.repository.logoFor

/** What the pause screen says about the paused video. */
data class PausedInfo(
    /** The show's title, used as plain text when there is no logo for it. */
    val title: String,
    /** The show's title logo image; null when it has none. */
    val logoUrl: String?,
    /** "E5", or null for a movie. */
    val episodeLabel: String?,
    /** The episode's own name; for a movie, or an episode without a real name, null. */
    val episodeName: String?,
    /** The episode's synopsis when a source has one, otherwise the show's. */
    val synopsis: String?,
)

/**
 * The pause screen, in the style of Nuvio: the frozen video stays visible, dimmed, and the paused title is
 * written over it at the bottom left — "You're watching", the show's logo, the episode number, the episode
 * name and its synopsis. It fades in while the text rises a little into place. A touch on it never resumes the video:
 * it only takes the screen away ([onTouch] shows the controls), and the screen comes back after the usual pause.
 *
 * [compact] is for the portrait layout, where the video is a small 16:9 box and everything is drawn smaller.
 */
@Composable
fun PausedOverlay(
    visible: Boolean,
    info: PausedInfo,
    onTouch: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val fade by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 450 else 220, easing = FastOutSlowInEasing),
        label = "pauseFade",
    )
    // The text arrives a beat after the dimming, so the screen settles first and the words follow.
    val rise by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = if (visible) 650 else 220, delayMillis = if (visible) 120 else 0, easing = FastOutSlowInEasing),
        label = "pauseRise",
    )
    if (fade <= 0f && !visible) return

    val startPadding = if (compact) 16.dp else 48.dp
    val bottomPadding = if (compact) 14.dp else 44.dp

    Box(
        modifier = modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade }
            // Taps must not fall through to the gesture layer below while the screen is up.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onTouch),
    ) {
        // Dims the whole picture a little, darker on the left where the words sit, and darker again at the bottom.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        0f to Color.Black.copy(alpha = 0.82f),
                        0.55f to Color.Black.copy(alpha = 0.50f),
                        1f to Color.Black.copy(alpha = 0.30f),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.5f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.55f),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = startPadding, end = startPadding, bottom = bottomPadding)
                .fillMaxWidth(if (compact) 0.92f else 0.62f)
                .graphicsLayer {
                    alpha = rise
                    translationY = (1f - rise) * 28.dp.toPx()
                },
        ) {
            Text(
                text = "You're watching",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = if (compact) 11.sp else 15.sp,
            )
            Spacer(Modifier.height(if (compact) 6.dp else 12.dp))
            if (info.logoUrl != null) {
                AsyncImage(
                    model = info.logoUrl,
                    contentDescription = info.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.CenterStart,
                    modifier = Modifier
                        .heightIn(max = if (compact) 30.dp else 64.dp)
                        .widthIn(max = if (compact) 150.dp else 300.dp),
                )
            } else {
                Text(
                    text = info.title,
                    color = Color.White,
                    fontSize = if (compact) 18.sp else 32.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            info.episodeLabel?.let { label ->
                Spacer(Modifier.height(if (compact) 8.dp else 22.dp))
                Text(
                    text = label,
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = if (compact) 12.sp else 18.sp,
                )
            }
            info.episodeName?.takeIf { it.isNotBlank() }?.let { name ->
                Spacer(Modifier.height(if (compact) 2.dp else 6.dp))
                Text(
                    text = name,
                    color = Color.White,
                    fontSize = if (compact) 16.sp else 34.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            info.synopsis?.takeIf { it.isNotBlank() }?.let { text ->
                Spacer(Modifier.height(if (compact) 4.dp else 12.dp))
                Text(
                    text = text,
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = if (compact) 11.sp else 16.sp,
                    lineHeight = if (compact) 15.sp else 23.sp,
                    maxLines = if (compact) 2 else 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private val SeasonInTitle = Regex("""(\d+)(?:st|nd|rd|th)\s+Season|Season\s+(\d+)""", RegexOption.IGNORE_CASE)
private val GenericEpisodeName = Regex("""^\s*(?:episode|ep\.?)\s*\d+(?:\.\d+)?\s*$""", RegexOption.IGNORE_CASE)
private val HtmlTag = Regex("<[^>]*>")

/**
 * Gathers what the pause screen shows. A series episode reads "S2E5" (the season is TMDB's when known, else the
 * "2nd Season" in the title, else 1) and a movie has no episode line at all. An episode a source only
 * names "Episode 5" gets no name line, since the label already says so.
 */
fun buildPausedInfo(
    anime: SAnime?,
    episode: SEpisode?,
    artwork: AnimeArtwork?,
    language: TitleLanguage,
): PausedInfo {
    val isMovie = anime?.format.equals("Movie", ignoreCase = true)
    val number = episode?.episodeNumber?.let { n -> if (n % 1f == 0f) n.toInt().toString() else n.toString() }
    val label = when {
        isMovie || number == null -> null
        anime?.format == null || anime.format.startsWith("TV", ignoreCase = true) -> {
            val fromTitle = SeasonInTitle.find(anime?.title.orEmpty())?.groupValues?.drop(1)
                ?.firstOrNull { it.isNotEmpty() }?.toIntOrNull()
            "S${artwork?.season ?: fromTitle ?: 1}E$number"
        }
        else -> "E$number"
    }
    val name = episode?.name?.takeIf { !isMovie && it.isNotBlank() && !GenericEpisodeName.matches(it) }
    val synopsis = (episode?.description?.takeIf { it.isNotBlank() && !isMovie } ?: anime?.description)
        ?.replace("<br>", " ", ignoreCase = true)
        ?.replace(HtmlTag, "")
        ?.replace("&amp;", "&")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
    return PausedInfo(
        title = anime?.title.orEmpty(),
        logoUrl = artwork?.logoFor(language),
        episodeLabel = label,
        episodeName = name,
        synopsis = synopsis,
    )
}
