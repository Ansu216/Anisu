package com.ansu.anime.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import com.ansu.anime.ui.theme.AnsuColors

/** One piece of a comment: a run of words, or a picture the writer attached. A spoiler piece sat inside AniList's `~!...!~`. */
internal sealed interface CommentPart {
    data class Words(val text: String, val spoiler: Boolean = false) : CommentPart
    data class Picture(val url: String, val spoiler: Boolean = false) : CommentPart
}

// The three ways a picture reaches a comment on AniList: its own img(link) / img220(link), markdown, and an HTML tag.
private val PictureMarkup = Regex(
    """(?<![A-Za-z])img\d*%?\(\s*([^)\s]+)\s*\)|!\[[^\]]*]\(\s*([^)\s]+)[^)]*\)|<img\b[^>]*?\bsrc\s*=\s*["']([^"']+)["'][^>]*>""",
    RegexOption.IGNORE_CASE,
)

private val SpoilerMarkup = Regex("~!([\\s\\S]*?)!~")

/** Splits a comment into its words and its pictures, in order, each marked when it sits inside a spoiler. */
internal fun commentParts(raw: String): List<CommentPart> {
    val parts = mutableListOf<CommentPart>()
    fun addSegment(segment: String, spoiler: Boolean) {
        var last = 0
        fun addWords(chunk: String) {
            val clean = plainComment(chunk)
            if (clean.isNotEmpty()) parts += CommentPart.Words(clean, spoiler)
        }
        for (match in PictureMarkup.findAll(segment)) {
            addWords(segment.substring(last, match.range.first))
            val link = (match.groups[1] ?: match.groups[2] ?: match.groups[3])?.value.orEmpty()
            if (link.startsWith("http", ignoreCase = true)) {
                // Cleartext pictures are refused by many hosts; every one that matters also answers on https.
                parts += CommentPart.Picture(link.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://"), spoiler)
            }
            last = match.range.last + 1
        }
        addWords(segment.substring(last))
    }
    var last = 0
    for (spoiler in SpoilerMarkup.findAll(raw)) {
        addSegment(raw.substring(last, spoiler.range.first), false)
        addSegment(spoiler.groupValues[1], true)
        last = spoiler.range.last + 1
    }
    addSegment(raw.substring(last), false)
    return parts
}

/** The link of the comment's first picture that is not a spoiler, or null. */
internal fun firstOpenPicture(raw: String): String? =
    commentParts(raw).filterIsInstance<CommentPart.Picture>().firstOrNull { !it.spoiler }?.url

/**
 * The text of a comment with its pictures shown. A picture opens full screen when tapped, and is kept to a width that
 * still fits a narrow, indented reply.
 */
@Composable
internal fun CommentBody(raw: String, modifier: Modifier = Modifier) {
    val parts = remember(raw) { commentParts(raw) }
    var viewing by remember { mutableStateOf<String?>(null) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        parts.forEach { part ->
            when (part) {
                is CommentPart.Words -> {
                    var revealed by remember(part.text) { mutableStateOf(!part.spoiler) }
                    if (revealed) {
                        Text(
                            text = part.text,
                            color = AnsuColors.TextPrimary,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                            modifier = if (part.spoiler) Modifier.clickable { revealed = false } else Modifier,
                        )
                    } else {
                        Text(
                            text = "Spoiler, tap to show",
                            color = AnsuColors.TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AnsuColors.BackgroundElevated)
                                .clickable { revealed = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
                is CommentPart.Picture -> {
                    var revealed by remember(part.url) { mutableStateOf(!part.spoiler) }
                    if (revealed) {
                        AsyncImage(
                            model = part.url,
                            contentDescription = "Picture in the comment",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .widthIn(max = 280.dp)
                                .heightIn(min = 80.dp, max = 340.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AnsuColors.BackgroundElevated)
                                .clickable { viewing = part.url },
                        )
                    } else {
                        // A spoiler picture is not even downloaded until it is tapped.
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier
                                .size(width = 200.dp, height = 110.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(AnsuColors.BackgroundElevated)
                                .clickable { revealed = true },
                        ) {
                            Text("Spoiler picture, tap to show", color = AnsuColors.TextSecondary, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }
    viewing?.let { url -> FullScreenPicture(url) { viewing = null } }
}

@Composable
private fun FullScreenPicture(url: String, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)).clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = url,
                contentDescription = "Picture",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
    }
}
