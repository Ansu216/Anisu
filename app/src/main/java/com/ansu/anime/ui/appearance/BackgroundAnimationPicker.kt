package com.ansu.anime.ui.appearance

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.ansu.anime.data.prefs.BackgroundStyle
import com.ansu.anime.ui.components.BackgroundAnimation
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The background-animation chooser: a card with a two-column grid of small windows, each one playing its own
 * animation live, the current choice outlined in the accent. Tapping a window picks it and closes the card.
 */
@Composable
fun BackgroundAnimationPicker(
    selected: BackgroundStyle,
    onSelect: (BackgroundStyle) -> Unit,
    onDismiss: () -> Unit,
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(32.dp),
            color = Color(0xFF14161C),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp)) {
                BackgroundStyle.entries.chunked(2).forEach { rowStyles ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                        rowStyles.forEach { style ->
                            PreviewTile(
                                style = style,
                                selected = style == selected,
                                onClick = { onSelect(style); onDismiss() },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        // An odd count leaves one empty cell so the last tile keeps the same width as the others.
                        if (rowStyles.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", color = AnsuColors.Accent) }
                }
            }
        }
    }
}

@Composable
private fun PreviewTile(style: BackgroundStyle, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(20.dp)
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(if (selected) Color(0xFF2A2D38) else Color(0xFF1A1C24))
            .then(
                Modifier.clickable(onClick = onClick),
            ),
    ) {
        if (style == BackgroundStyle.NONE) {
            Icon(
                Icons.Filled.VisibilityOff,
                contentDescription = null,
                tint = AnsuColors.TextSecondary,
                modifier = Modifier.align(Alignment.Center).size(34.dp),
            )
        } else {
            // The same drawing the app uses behind its screens, just smaller and quicker.
            BackgroundAnimation(
                style = style,
                modifier = Modifier.matchParentSize(),
                sizeScale = 0.28f,
                speed = 6f,
                maxShapes = 6,
            )
        }
        Text(
            text = style.label,
            color = AnsuColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 14.dp),
        )
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(shape)
                .then(
                    Modifier.border(
                        border = if (selected) BorderStroke(2.dp, AnsuColors.Accent) else BorderStroke(1.dp, AnsuColors.StrokeGlass),
                        shape = shape,
                    ),
                ),
        )
    }
}
