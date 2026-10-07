package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.ui.theme.AnsuColors

/** Text colours offered for subtitles (ARGB). */
internal val SubtitleTextColors: List<Int> = listOf(
    0xFFFFFFFF, // white
    0xFFFFE066, // soft yellow
    0xFF7DF9FF, // cyan
    0xFF8CFF98, // mint green
    0xFFFFA6C9, // pink
    0xFFFFB347, // orange
).map { it.toInt() }

/** Background colours offered behind subtitles (ARGB); the density slider decides how solid they are. */
internal val SubtitleBgColors: List<Int> = listOf(
    0xFF000000, // black
    0xFF2B2B33, // charcoal
    0xFF14213D, // navy
    0xFF3B1F5C, // purple
    0xFF5C1F2B, // maroon
    0xFFFFFFFF, // white
).map { it.toInt() }

/** [bgColor] with [opacity] percent applied to its alpha. */
internal fun subtitleBackgroundArgb(bgColor: Int, opacity: Int): Int =
    ((opacity.coerceIn(0, 100) * 255 / 100) shl 24) or (bgColor and 0x00FFFFFF)

/** A row of round colour swatches; the picked one has an accent ring and a tick. */
@Composable
internal fun ColorSwatchRow(colors: List<Int>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.horizontalScroll(rememberScrollState()).padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        colors.forEach { argb ->
            val isSelected = argb == selected
            val color = Color(argb)
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) AnsuColors.Accent else Color.Gray.copy(alpha = 0.6f),
                        shape = CircleShape,
                    )
                    .clickable { onSelect(argb) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) {
                    Icon(
                        Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = if (color.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
        }
    }
}

/**
 * Live preview of the subtitle style on a stand-in video frame. The text is drawn a little smaller than on a
 * full-screen video because the preview box is smaller; colours, background and height follow the settings.
 */
@Composable
internal fun SubtitlePreview(
    size: Int,
    height: Int,
    textColor: Int,
    bgColor: Int,
    bgOpacity: Int,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(12.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF3F5A8C), Color(0xFF1B2236)))),
    ) {
        Text(
            text = "This is how your subtitles will look",
            color = Color(textColor),
            fontSize = (size * 0.6f).sp,
            textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(color = Color.Black, blurRadius = 4f)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = maxHeight * (height / 100f), start = 12.dp, end = 12.dp)
                .background(Color(bgColor).copy(alpha = bgOpacity / 100f), RoundedCornerShape(4.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}
