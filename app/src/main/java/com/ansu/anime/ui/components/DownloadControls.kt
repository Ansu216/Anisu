package com.ansu.anime.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.data.download.DownloadPhase
import com.ansu.anime.data.download.DownloadStatus
import com.ansu.anime.data.prefs.PlayerPrefs
import com.ansu.anime.ui.theme.AnsuColors

/** The download icon beside an episode: an arrow to start, a ring while it downloads, a tick when it is saved. */
@Composable
fun EpisodeDownloadButton(status: DownloadStatus?, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val phase = status?.phase
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        when (phase) {
            DownloadPhase.Completed ->
                Icon(Icons.Filled.DownloadDone, contentDescription = "Downloaded", tint = AnsuColors.Accent, modifier = Modifier.size(22.dp))
            DownloadPhase.Preparing, DownloadPhase.Queued ->
                CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
            DownloadPhase.Downloading -> {
                val percent = status?.percent ?: -1f
                if (percent >= 0f) {
                    CircularProgressIndicator(
                        progress = { (percent / 100f).coerceIn(0f, 1f) },
                        color = AnsuColors.Accent,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                } else {
                    CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                }
            }
            else ->
                Icon(Icons.Filled.Download, contentDescription = "Download episode", tint = AnsuColors.TextSecondary, modifier = Modifier.size(22.dp))
        }
    }
}

/** A row of choices where one is selected (used for the picture quality and the language). */
@Composable
private fun <T> OptionPills(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(options.size) { index ->
            val (value, label) = options[index]
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) AnsuColors.Accent else AnsuColors.AccentSoft)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = label,
                    color = if (isSelected) Color.White else AnsuColors.TextPrimary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}

/**
 * Asks how to download: the picture quality (Any, 1080p ... 360p), the language (Sub or Dub) and, for the batch,
 * which episodes: two boxes, "From ep" and "To ep". [bulk] adds those boxes and a "Default" quality that follows
 * the setting. [initialFrom] is the episode the person is on and [maxEpisode] the last one the show has.
 * For a single episode, [onConfirm] gets 0 for both ends of the range.
 */
@Composable
fun DownloadOptionsDialog(
    bulk: Boolean,
    defaultQuality: Int,
    defaultLanguage: String,
    onDismiss: () -> Unit,
    onConfirm: (quality: Int?, from: Int, to: Int, language: String) -> Unit,
    initialFrom: Int = 1,
    maxEpisode: Int = 1,
) {
    var fromText by remember { mutableStateOf(initialFrom.toString()) }
    var toText by remember { mutableStateOf(minOf(initialFrom + 2, maxOf(maxEpisode, initialFrom)).toString()) }
    var quality by remember { mutableStateOf<Int?>(if (bulk) null else defaultQuality) }
    var language by remember { mutableStateOf(if (defaultLanguage == PlayerPrefs.LANGUAGE_DUB) PlayerPrefs.LANGUAGE_DUB else PlayerPrefs.LANGUAGE_SUB) }
    val qualityOptions: List<Pair<Int?, String>> =
        (if (bulk) listOf<Pair<Int?, String>>(null to "Default") else emptyList()) +
            listOf<Pair<Int?, String>>(0 to "Any", 1080 to "1080p", 720 to "720p", 480 to "480p", 360 to "360p")
    val from = fromText.toIntOrNull()
    val to = toText.toIntOrNull()
    val rangeValid = !bulk || (from != null && to != null && from <= to)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AnsuColors.BackgroundElevated,
        title = {
            Text(if (bulk) "Download next episodes" else "Download episode", color = AnsuColors.TextPrimary)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (bulk) {
                    Text("Which episodes (from and to, both included)", color = AnsuColors.TextSecondary, fontSize = 13.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        EpisodeNumberField(
                            label = "From ep",
                            value = fromText,
                            onChange = { fromText = it },
                            error = from == null || (to != null && from > to),
                            modifier = Modifier.weight(1f),
                        )
                        EpisodeNumberField(
                            label = "To ep",
                            value = toText,
                            onChange = { toText = it },
                            error = to == null || (from != null && from > to),
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (maxEpisode > 0) {
                        Text("This show has episodes up to $maxEpisode", color = AnsuColors.TextTertiary, fontSize = 12.sp)
                    }
                }
                Text("Language", color = AnsuColors.TextSecondary, fontSize = 13.sp)
                OptionPills(
                    listOf(PlayerPrefs.LANGUAGE_SUB to "Sub", PlayerPrefs.LANGUAGE_DUB to "Dub"),
                    language,
                ) { language = it }
                Text("Quality", color = AnsuColors.TextSecondary, fontSize = 13.sp)
                OptionPills(qualityOptions, quality) { quality = it }
            }
        },
        confirmButton = {
            TextButton(
                enabled = rangeValid,
                onClick = { onConfirm(quality, from ?: 0, to ?: 0, language) },
            ) {
                Text("Download", color = if (rangeValid) AnsuColors.Accent else AnsuColors.TextTertiary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = AnsuColors.TextSecondary)
            }
        },
    )
}

@Composable
private fun EpisodeNumberField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    error: Boolean,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { input -> onChange(input.filter { it.isDigit() }.take(5)) },
        modifier = modifier,
        singleLine = true,
        isError = error,
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = AnsuColors.TextPrimary,
            unfocusedTextColor = AnsuColors.TextPrimary,
            focusedBorderColor = AnsuColors.Accent,
            unfocusedBorderColor = Color.White.copy(alpha = 0.18f),
            focusedLabelColor = AnsuColors.Accent,
            unfocusedLabelColor = AnsuColors.TextSecondary,
            cursorColor = AnsuColors.Accent,
        ),
    )
}
