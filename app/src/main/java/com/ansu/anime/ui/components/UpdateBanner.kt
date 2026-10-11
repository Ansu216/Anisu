package com.ansu.anime.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.data.update.UpdateState
import com.ansu.anime.ui.theme.AnsuColors

private val BannerShape = RoundedCornerShape(16.dp)

/**
 * The in-app counterpart of the native "update available" notification: a compact glass bar pinned
 * over whatever screen is open. It renders nothing unless a check found a newer build. "Update" opens
 * the Updates screen; the close button dismisses it for that version. A hairline border around the
 * message empties from the right over [dismissAfterMs], so the bar shows when it will hide itself.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    dismissAfterMs: Long,
    modifier: Modifier = Modifier,
) {
    val update = (state.result as? UpdateCheckResult.Available)?.update ?: return
    // 1f -> 0f over the bar's own lifetime, drawn as the depleting outline below. It restarts whenever a
    // new version is announced.
    val remaining = remember { Animatable(1f) }
    LaunchedEffect(update.versionName, dismissAfterMs) {
        remaining.snapTo(1f)
        remaining.animateTo(
            targetValue = 0f,
            animationSpec = tween(durationMillis = dismissAfterMs.toInt(), easing = LinearEasing),
        )
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .drawWithContent {
                drawContent()
                val stroke = 1.5.dp.toPx()
                val inset = stroke / 2f
                clipRect(right = size.width * remaining.value.coerceIn(0f, 1f)) {
                    drawRoundRect(
                        color = AnsuColors.Accent.copy(alpha = 0.85f),
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - stroke, size.height - stroke),
                        cornerRadius = CornerRadius(16.dp.toPx(), 16.dp.toPx()),
                        style = Stroke(width = stroke),
                    )
                }
            },
    ) {
        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = BannerShape,
            tintAlpha = 0.92f,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = AnsuColors.Accent)
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Update available",
                        style = MaterialTheme.typography.titleSmall,
                        color = AnsuColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Anisu ${update.versionName} · " +
                            if (update.channel == UpdateChannel.NIGHTLY) "Nightly" else "Release",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextSecondary,
                    )
                }
                TextButton(onClick = onOpen) {
                    Text("Update", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = AnsuColors.TextTertiary)
                }
            }
        }
    }
}
