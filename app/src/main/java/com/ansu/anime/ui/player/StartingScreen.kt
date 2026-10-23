package com.ansu.anime.ui.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.data.repository.AnimeArtwork

/** The picture behind the buffering screen: the show's widest artwork, falling back to the details banner. */
fun startingBackdrop(artwork: AnimeArtwork?, fallback: String?): String? =
    artwork?.tmdbBackdropUrl ?: artwork?.backdropUrl ?: artwork?.bannerUrl ?: artwork?.entryCoverUrl ?: fallback

/**
 * Shown from the moment a stream is chosen until its first picture is out: the show's artwork dimmed, its title
 * logo in the middle and "Buffering\u2026" under it, with a back button. It covers the video box completely and
 * swallows taps, so nothing under it can be touched while it is up.
 *
 * [compact] is the portrait layout, where the video is a small 16:9 box: everything is smaller and there is no
 * back button (the portrait page has its own).
 */
@Composable
fun StartingScreen(
    visible: Boolean,
    backdropUrl: String?,
    logoUrl: String?,
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut(), modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .pointerInput(Unit) { detectTapGestures { } },
        ) {
            if (backdropUrl != null) {
                AsyncImage(model = backdropUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            }
            Box(
                modifier = Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.45f),
                        0.5f to Color.Black.copy(alpha = 0.55f),
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                ),
            )
            Column(modifier = Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                if (logoUrl != null) {
                    AsyncImage(
                        model = logoUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .heightIn(max = if (compact) 44.dp else 110.dp)
                            .widthIn(max = if (compact) 190.dp else 420.dp),
                    )
                } else {
                    Text(
                        text = title,
                        color = Color.White,
                        fontSize = if (compact) 18.sp else 34.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                Spacer(Modifier.height(if (compact) 18.dp else 70.dp))
                Text(
                    text = "Buffering\u2026",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = if (compact) 12.sp else 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                )
            }
            if (!compact) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 20.dp, end = 28.dp)
                        .size(46.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable(onClick = onBack),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                }
            }
        }
    }
}
