package com.ansu.anime.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Says that what is on screen came from the cache on disk, not from AniList, so a screen that opened
 * without a connection never passes saved content off as live. It goes away by itself the moment a
 * request reaches AniList again.
 */
@Composable
fun SavedDataNotice(modifier: Modifier = Modifier) {
    FrostedGlassCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        tintAlpha = 0.92f,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.CloudOff,
                contentDescription = null,
                tint = AnsuColors.TextSecondary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = "Showing saved data · cached copy",
                style = MaterialTheme.typography.labelMedium,
                color = AnsuColors.TextPrimary,
            )
        }
    }
}
