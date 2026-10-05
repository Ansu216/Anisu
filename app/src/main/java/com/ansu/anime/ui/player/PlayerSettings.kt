@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ansu.anime.ui.theme.AnsuColors

data class SubtitleLanguage(
    val code: String,
    val name: String,
)

data class StreamInfo(
    val resolution: String,
    val source: String,
    val delay: Int, // milliseconds
)

@Composable
fun SubtitleSettingsSheet(
    languages: List<SubtitleLanguage>,
    selectedLanguage: SubtitleLanguage?,
    subtitleSize: SubtitleSize = SubtitleSize.MEDIUM,
    onLanguageSelect: (SubtitleLanguage) -> Unit,
    onSizeChange: (SubtitleSize) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Subtitles",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Language Section
            Text(
                "Language",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                color = Color.White.copy(alpha = 0.7f),
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                items(languages) { language ->
                    SubtitleLanguageChip(
                        label = language.name,
                        isSelected = language == selectedLanguage,
                        onClick = { onLanguageSelect(language) },
                    )
                }
                // OFF option
                item {
                    SubtitleLanguageChip(
                        label = "OFF",
                        isSelected = selectedLanguage == null,
                        onClick = { /* Handle OFF */ },
                    )
                }
            }

            // Size Section
            Text(
                "Style",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.White.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitleSize.entries.forEach { size ->
                    SubtitleSizeButton(
                        label = size.label,
                        isSelected = subtitleSize == size,
                        modifier = Modifier.weight(1f),
                        onClick = { onSizeChange(size) },
                    )
                }
            }

            // Background Section
            Text(
                "Background",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.White.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BackgroundOption(
                    label = "None",
                    isSelected = true,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
                BackgroundOption(
                    label = "Shadow",
                    isSelected = false,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
                BackgroundOption(
                    label = "Box",
                    isSelected = false,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
            }

            // Position Section
            Text(
                "Position",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.White.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SubtitlePositionButton(
                    label = "Low",
                    isSelected = true,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
                SubtitlePositionButton(
                    label = "High",
                    isSelected = false,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
            }

            // Delay Section
            Text(
                "Delay - In sync",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.White.copy(alpha = 0.7f),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Text("-", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
                Slider(
                    value = 0f,
                    onValueChange = { },
                    valueRange = -1000f..1000f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = AnsuColors.Accent,
                        activeTrackColor = AnsuColors.Accent,
                        inactiveTrackColor = Color.White.copy(alpha = 0.2f),
                    ),
                )
                IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                    Text("+", color = Color.White, style = MaterialTheme.typography.titleSmall)
                }
            }

            Text(
                "0.0s",
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                color = Color.White.copy(alpha = 0.7f),
            )
        }
    }
}

@Composable
fun StreamSelectionSheet(
    streams: List<StreamInfo>,
    selectedStream: StreamInfo?,
    onStreamSelect: (StreamInfo) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Select Stream",
                    style = MaterialTheme.typography.titleMedium,
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", modifier = Modifier.size(20.dp))
                }
            }

            // Language toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageToggleButton(
                    label = "Sub",
                    isSelected = true,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
                LanguageToggleButton(
                    label = "Dub",
                    isSelected = false,
                    modifier = Modifier.weight(1f),
                    onClick = { },
                )
            }

            // Streams list
            Text(
                "Streams",
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                color = Color.White.copy(alpha = 0.7f),
            )

            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp),
            ) {
                items(streams) { stream ->
                    StreamCard(
                        resolution = stream.resolution,
                        source = stream.source,
                        delay = stream.delay,
                        isSelected = stream == selectedStream,
                        onClick = { onStreamSelect(stream) },
                    )
                }
            }
        }
    }
}

@Composable
fun StreamCard(
    resolution: String,
    source: String,
    delay: Int,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = resolution,
                style = MaterialTheme.typography.labelMedium,
                color = if (isSelected) AnsuColors.OnAccent else Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            )
            Text(
                text = source,
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) AnsuColors.OnAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
            Text(
                text = "${delay}ms",
                style = MaterialTheme.typography.labelSmall,
                color = if (isSelected) AnsuColors.OnAccent.copy(alpha = 0.8f) else Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
fun SubtitleLanguageChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(12.dp),
            )
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AnsuColors.OnAccent else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SubtitleSizeButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AnsuColors.OnAccent else Color.White,
        )
    }
}

@Composable
fun BackgroundOption(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AnsuColors.OnAccent else Color.White,
        )
    }
}

@Composable
fun SubtitlePositionButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isSelected) AnsuColors.OnAccent else Color.White,
        )
    }
}

@Composable
fun LanguageToggleButton(
    label: String,
    isSelected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .background(
                color = if (isSelected) AnsuColors.Accent else Color.White.copy(alpha = 0.1f),
                shape = RoundedCornerShape(8.dp),
            )
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (isSelected) AnsuColors.OnAccent else Color.White,
        )
    }
}

enum class SubtitleSize(val label: String) {
    SMALL("S"),
    MEDIUM("M"),
    LARGE("L"),
}
