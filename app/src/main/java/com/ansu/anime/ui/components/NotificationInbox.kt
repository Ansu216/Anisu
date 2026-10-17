package com.ansu.anime.ui.components

import android.text.format.DateUtils
import android.app.Activity
import android.graphics.Color as AndroidColor
import android.os.Build
import android.view.WindowManager
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import kotlin.math.PI
import kotlin.math.sin
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateDp
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
 * unread); a tap grows it out of its corner into a full-screen panel listing every notification received so far,
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
    // 0 = nothing covered, 1 = the four black waves have met and the whole screen is black.
    val cover = remember { Animatable(0f) }
    var showDialog by remember { mutableStateOf(false) }
    LaunchedEffect(expanded) {
        if (expanded) {
            showDialog = true
            cover.animateTo(1f, tween(WAVE_IN_MILLIS, easing = FastOutSlowInEasing))
        } else if (showDialog) {
            cover.animateTo(0f, tween(WAVE_OUT_MILLIS, easing = FastOutSlowInEasing))
            showDialog = false
        }
    }

    fun close() {
        expanded = false
        onMarkAllRead()
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
        FrostedGlassCard(
            modifier = Modifier.size(38.dp),
            shape = CircleShape,
            tintAlpha = 0.5f,
        ) {
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
        }
    }

    // Opened: black waves sweep in from all four sides until the screen is covered, then the list fades in.
    if (showDialog) {
        Dialog(
            onDismissRequest = { close() },
            properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
        ) {
            // Make the dialog window truly edge to edge: under the status bar and navigation bar, no dim layer.
            val view = LocalView.current
            SideEffect {
                val window = (view.parent as? DialogWindowProvider)?.window ?: return@SideEffect
                window.setDimAmount(0f)
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
                window.addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS)
                window.setLayout(WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT)
                window.setBackgroundDrawableResource(android.R.color.transparent)
                WindowCompat.setDecorFitsSystemWindows(window, false)
                window.statusBarColor = AndroidColor.TRANSPARENT
                window.navigationBarColor = AndroidColor.TRANSPARENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    window.attributes = window.attributes.apply {
                        layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                    }
                }
            }
            Box(modifier = Modifier.fillMaxSize()) {
                // The waves. Solid black once they have met, so nothing grey or see-through is left.
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val p = cover.value
                    if (p >= 0.995f) {
                        drawRect(AnsuColors.Background)
                    } else if (p > 0f) {
                        for (side in 0 until 4) {
                            // Each side starts a little after the one before it, so they roll in like a wave.
                            val local = ((p - side * 0.08f) / 0.68f).coerceIn(0f, 1f)
                            if (local <= 0f) continue
                            val dim = if (side % 2 == 0) size.height else size.width
                            val depth = dim * 0.62f * local
                            val amp = dim * 0.07f * sin(PI.toFloat() * local)
                            drawPath(wavePath(side, size.width, size.height, depth, amp, local * 5f), AnsuColors.Background)
                        }
                    }
                }
                // The list and header only appear once the screen is covered, and go first on close.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = if (expanded) ((cover.value - 0.72f) / 0.28f).coerceIn(0f, 1f) else ((cover.value - 0.9f) / 0.1f).coerceIn(0f, 1f) },
                ) {
                    Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "Notifications",
                                color = AnsuColors.TextPrimary,
                                fontSize = 22.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f),
                            )
                            if (entries.isNotEmpty()) {
                                TextButton(onClick = { Haptics.tick(); onClear() }) {
                                    Text("Clear", color = AnsuColors.TextSecondary, fontSize = 14.sp)
                                }
                            }
                            IconButton(onClick = { Haptics.tick(); close() }, modifier = Modifier.size(44.dp)) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Close notifications",
                                    tint = AnsuColors.TextPrimary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                        if (entries.isEmpty()) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text("Nothing yet", color = AnsuColors.TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Releases and new episodes of shows you follow or are watching will show up here.",
                                    color = AnsuColors.TextSecondary,
                                    fontSize = 14.sp,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 6.dp),
                                )
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(top = 8.dp)) {
                                items(entries, key = { it.key }) { entry ->
                                    InboxRow(entry) {
                                        close()
                                        onOpenEntry(entry)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val WAVE_IN_MILLIS = 700
private const val WAVE_OUT_MILLIS = 520

/**
 * One black bar entering from [side] (0 top, 1 right, 2 bottom, 3 left). Its leading edge is a sine wave of
 * height [amp] that travels with [phase], so the four bars read as waves washing over the screen.
 */
private fun wavePath(side: Int, w: Float, h: Float, depth: Float, amp: Float, phase: Float): Path {
    val along = if (side % 2 == 0) w else h
    fun at(u: Float, v: Float): Offset = when (side) {
        0 -> Offset(u, v)
        1 -> Offset(w - v, u)
        2 -> Offset(u, h - v)
        else -> Offset(v, u)
    }
    val steps = 48
    return Path().apply {
        moveTo(at(0f, 0f).x, at(0f, 0f).y)
        at(along, 0f).let { lineTo(it.x, it.y) }
        for (i in steps downTo 0) {
            val u = along * i / steps
            val v = depth + amp * sin(u / along * 2f * PI.toFloat() * 2f + phase)
            at(u, v).let { lineTo(it.x, it.y) }
        }
        close()
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
