package com.ansu.anime.ui.components

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.ansu.anime.core.haptics.Haptics
import com.ansu.anime.core.haptics.softClickable
import com.ansu.anime.data.notify.NotificationEntry
import com.ansu.anime.ui.theme.AnsuColors

/**
 * The bell at the top right of the home banner. Collapsed it is a small glass circle (with a dot while something is
 * unread); a tap grows it, the way the search bar opens, into a panel listing every notification received so far,
 * newest first. A tap on a row opens that show; the cross (or Back) folds the panel again and marks everything read.
 */
@Composable
fun NotificationInbox(
    entries: List<NotificationEntry>,
    onOpenEntry: (NotificationEntry) -> Unit,
    onMarkAllRead: () -> Unit,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val unread = entries.count { !it.read }

    fun close() {
        expanded = false
        onMarkAllRead()
    }
    BackHandler(enabled = expanded) { close() }

    BoxWithConstraints(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        val width by animateDpAsState(
            targetValue = if (expanded) maxWidth else 38.dp,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "inboxWidth",
        )
        val corner by animateDpAsState(
            targetValue = if (expanded) 22.dp else 19.dp,
            animationSpec = tween(durationMillis = 320, easing = FastOutSlowInEasing),
            label = "inboxCorner",
        )
        FrostedGlassCard(
            modifier = Modifier
                .width(width)
                .animateContentSize(tween(durationMillis = 320, easing = FastOutSlowInEasing)),
            shape = RoundedCornerShape(corner),
            tintAlpha = if (expanded) 0.88f else 0.5f,
        ) {
            if (!expanded) {
                Box(modifier = Modifier.size(38.dp)) {
                    IconButton(
                        onClick = {
                            Haptics.tick()
                            expanded = true
                        },
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        Icon(
                            imageVector = if (unread > 0) Icons.Filled.Notifications else Icons.Outlined.NotificationsNone,
                            contentDescription = "Notifications",
                            tint = AnsuColors.TextPrimary,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    if (unread > 0) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(top = 8.dp, end = 8.dp)
                                .size(9.dp)
                                .clip(CircleShape)
                                .background(AnsuColors.Accent),
                        )
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = "Notifications",
                            color = AnsuColors.TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1f),
                        )
                        if (entries.isNotEmpty()) {
                            TextButton(onClick = { Haptics.tick(); onClear() }) {
                                Text("Clear", color = AnsuColors.TextSecondary, fontSize = 13.sp)
                            }
                        }
                        IconButton(onClick = { Haptics.tick(); close() }, modifier = Modifier.size(38.dp)) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Close notifications",
                                tint = AnsuColors.TextPrimary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }
                    if (entries.isEmpty()) {
                        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 20.dp)) {
                            Text("Nothing yet", color = AnsuColors.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                            Text(
                                text = "Releases and new episodes of shows you follow or are watching will show up here.",
                                color = AnsuColors.TextSecondary,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
                            items(entries, key = { it.key }) { entry ->
                                InboxRow(entry) {
                                    close()
                                    onOpenEntry(entry)
                                }
                            }
                        }
                        Spacer(Modifier.size(6.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun InboxRow(entry: NotificationEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .softClickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AsyncImage(
            model = entry.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(width = 40.dp, height = 56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(AnsuColors.BackgroundElevated),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.title,
                color = AnsuColors.TextPrimary,
                fontSize = 14.sp,
                fontWeight = if (entry.read) FontWeight.Medium else FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(entry.message, color = AnsuColors.TextSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                text = DateUtils.getRelativeTimeSpanString(
                    entry.timeMillis,
                    System.currentTimeMillis(),
                    DateUtils.MINUTE_IN_MILLIS,
                ).toString(),
                color = AnsuColors.TextTertiary,
                fontSize = 11.sp,
            )
        }
        if (!entry.read) {
            Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(AnsuColors.Accent))
        }
    }
}
