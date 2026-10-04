package com.ansu.anime.ui.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.data.update.UpdateState
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The in-app counterpart of the native "update available" notification: a compact glass banner pinned
 * over whatever screen is open. It renders nothing unless a check found a newer build. "Update" opens
 * the Updates screen; the close button dismisses it for that version.
 */
@Composable
fun UpdateBanner(
    state: UpdateState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val update = (state.result as? UpdateCheckResult.Available)?.update ?: return
    FrostedGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        tintAlpha = 0.92f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
