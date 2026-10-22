package com.ansu.anime.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The app's error card, laid out as one centred column: a caution-triangle badge, a bold title, a grey
 * message and a wide "Try again" button. It sits on the same frosted black glass as every other card
 * (blurred fill, hairline stroke, big rounded corners) with a soft red wash over the top edge.
 *
 * [details] is the technical reason (an HTTP code, an exception message). When given, a small "Details"
 * toggle expands it under the message, so the card stays calm for most people and a bug report still
 * has something to copy. [onDismiss] adds a close button in the top corner.
 *
 * [compact] shrinks the badge, text and button for cards that sit in a list (a Home row) instead of
 * taking the middle of a screen. The card never fills the screen height; the caller decides where it sits.
 */
@Composable
fun ErrorGlassCard(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    details: String? = null,
    retryLabel: String = "Try again",
    onRetry: (() -> Unit)? = null,
    retryEnabled: Boolean = true,
    onDismiss: (() -> Unit)? = null,
    blurRadius: androidx.compose.ui.unit.Dp = 12.dp,
    compact: Boolean = false,
) {
    var showDetails by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(if (compact) 24.dp else 32.dp)
    val badge = if (compact) 52.dp else 88.dp
    val badgeIcon = if (compact) 26.dp else 40.dp

    FrostedGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        tintAlpha = 0.88f,
        blurRadius = blurRadius,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Red wash fading out downwards, so the card reads as an error without turning into a red box.
                .background(
                    Brush.verticalGradient(
                        0f to AnsuColors.Error.copy(alpha = 0.18f),
                        0.65f to AnsuColors.Error.copy(alpha = 0f),
                    ),
                ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = if (compact) 20.dp else 24.dp, vertical = if (compact) 20.dp else 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(badge)
                        .clip(CircleShape)
                        .background(AnsuColors.Error.copy(alpha = 0.14f))
                        .border(1.dp, AnsuColors.Error.copy(alpha = 0.45f), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = AnsuColors.Error,
                        modifier = Modifier.size(badgeIcon),
                    )
                }
                Text(
                    text = title,
                    style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = AnsuColors.TextPrimary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = if (compact) 12.dp else 22.dp),
                )
                Text(
                    text = message,
                    style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
                    color = AnsuColors.TextSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = if (compact) 4.dp else 8.dp),
                )

                if (details != null) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .clip(MaterialTheme.shapes.small)
                            .clickable { showDetails = !showDetails }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
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
                            textAlign = TextAlign.Center,
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
                    Button(
                        onClick = onRetry,
                        enabled = retryEnabled,
                        shape = RoundedCornerShape(if (compact) 16.dp else 20.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color.White.copy(alpha = 0.16f),
                            contentColor = AnsuColors.TextPrimary,
                            disabledContainerColor = Color.White.copy(alpha = 0.08f),
                            disabledContentColor = AnsuColors.TextTertiary,
                        ),
                        modifier = Modifier
                            .padding(top = if (compact) 16.dp else 26.dp)
                            .fillMaxWidth()
                            .height(if (compact) 46.dp else 58.dp),
                    ) {
                        Text(
                            text = retryLabel,
                            style = if (compact) MaterialTheme.typography.titleSmall else MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            if (onDismiss != null) {
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Dismiss",
                        tint = AnsuColors.TextTertiary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}
