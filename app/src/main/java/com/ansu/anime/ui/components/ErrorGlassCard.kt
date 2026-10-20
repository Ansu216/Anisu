package com.ansu.anime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The app's error card: the same black-glass surface as every other card (frosted fill, hairline
 * stroke, large rounded corners), with a soft red wash over the top edge, a red icon tile, a title,
 * a short message and an optional Retry button.
 *
 * [details] is for the technical reason (an HTTP code, an exception message). When it is given, a
 * small "Details" toggle expands it under the message, so the card stays calm for most people and
 * still gives a bug report something to copy. [onDismiss] adds a close button in the corner.
 *
 * Works full-width inside a screen, a list or a sheet; it does not fill the screen height, so the
 * caller decides where it sits.
 */
@Composable
fun ErrorGlassCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    retryLabel: String = "Retry",
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    blurRadius: androidx.compose.ui.unit.Dp = 12.dp,
) {
    var showDetails by remember { mutableStateOf(false) }
    val shape = MaterialTheme.shapes.large

    FrostedGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        tintAlpha = 0.85f,
        blurRadius = blurRadius,
    ) {
        // Red wash fading out downwards, so the card reads as an error without turning into a red box.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to AnsuColors.Error.copy(alpha = 0.16f),
                        0.7f to AnsuColors.Error.copy(alpha = 0f),
                    ),
                )
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(AnsuColors.Error.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.ErrorOutline,
                        contentDescription = null,
                        tint = AnsuColors.Error,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 12.dp, top = 2.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = AnsuColors.TextPrimary,
                    )
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
                if (onDismiss != null) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(32.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Dismiss",
                            tint = AnsuColors.TextTertiary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            if (details != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 12.dp)
                        .clip(MaterialTheme.shapes.small)
                        .clickable { showDetails = !showDetails }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Details",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = AnsuColors.TextTertiary,
                    )
                    Icon(
                        imageVector = Icons.Filled.KeyboardArrowDown,
                        contentDescription = null,
                        tint = AnsuColors.TextTertiary,
                        modifier = Modifier.size(18.dp).rotate(if (showDetails) 180f else 0f),
                    )
                }
                AnimatedVisibility(
                    visible = showDetails,
                    enter = expandVertically(tween(200)) + fadeIn(tween(200)),
                    exit = shrinkVertically(tween(160)) + fadeOut(tween(160)),
                ) {
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        color = AnsuColors.TextTertiary,
                        maxLines = 6,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(AnsuColors.Background.copy(alpha = 0.45f))
                            .padding(12.dp),
                    )
                }
            }

            if (onRetry != null) {
                OutlinedButton(
                    onClick = onRetry,
                    enabled = retryEnabled,
                    modifier = Modifier.padding(top = 14.dp),
                    shape = CircleShape,
                    border = BorderStroke(1.dp, AnsuColors.Error.copy(alpha = 0.55f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = AnsuColors.TextPrimary),
                ) {
                    Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(
                        text = retryLabel,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }
    }
}
