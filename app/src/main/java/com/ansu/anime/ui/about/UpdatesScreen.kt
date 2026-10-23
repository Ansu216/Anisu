@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.BuildConfig
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.data.update.UpdateState
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.GlassDisclosureRow
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/**
 * Updates, laid out status-first: the answer to "is there something to install?", the installed build and
 * the one button that acts on it are the first thing on the screen, and everything that is a *setting*
 * (channel, automatic checks, notifications, storage) sits behind a single "Options" row that opens in
 * place. Opening Updates to take an update therefore needs no scrolling at all, which is what this screen
 * is opened for far more often than a setting is changed; the row names what is inside it so nothing is
 * hidden, and it stays folded so the page is three things long.
 *
 * The behaviour is the one it always had: it reads what the project publishes on GitHub, downloads the
 * APK through a resumable `.part` file and hands only a complete download to the system installer.
 */
@Composable
fun UpdatesScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val channel by container.updateManager.prefs.channel.collectAsStateWithLifecycle()
    val autoCheck by container.updateManager.prefs.autoCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var testResult by remember { mutableStateOf<String?>(null) }
    var offerNotificationSettings by remember { mutableStateOf(false) }
    // Read once and then refreshed after a clear, so the row shows the cache the moment it is emptied.
    var apkCount by remember { mutableIntStateOf(container.updateManager.cachedApkCount()) }
    // Kept across a rotation, so a half-changed setting never folds itself away under the user's finger.
    var optionsOpen by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.updateManager.check() }

    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Updates") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "Updates → check from the top bar")
                            container.updateManager.check()
                        },
                        enabled = !state.isChecking && !state.isDownloading,
                    ) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Check again")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        ) {
            StatusCard(
                state = state,
                onCheck = {
                    container.diagnostics.log(LogCategory.CLICK, "Updates → check")
                    container.updateManager.check()
                },
                onInstall = {
                    val version = (state.result as? UpdateCheckResult.Available)?.update?.versionName
                    container.diagnostics.log(LogCategory.CLICK, "Updates → download $version")
                    container.updateManager.downloadAndInstall()
                },
                modifier = Modifier.padding(top = 12.dp),
            )

            GlassGroup(modifier = Modifier.padding(top = 16.dp)) {
                GlassDisclosureRow(
                    icon = Icons.Filled.Menu,
                    title = "Options",
                    description = "Channel, checks and notifications",
                    expanded = optionsOpen,
                    onExpandedChange = { optionsOpen = it },
                    showDivider = false,
                ) {
                    GlassRow(
                        icon = Icons.Filled.Menu,
                        title = "Channel",
                        trailing = {
                            InlineChannelSelector(
                                channel = channel,
                                onChannelChange = { picked ->
                                    container.diagnostics.log(
                                        LogCategory.CLICK,
                                        "Updates → channel ${picked.name.lowercase()}",
                                    )
                                    container.updateManager.prefs.setChannel(picked)
                                },
                            )
                        },
                    )
                    // What the selected channel means, as one quiet line rather than a card of its own.
                    Text(
                        text = when (channel) {
                            UpdateChannel.RELEASES -> "Stable, tagged releases only."
                            UpdateChannel.NIGHTLY -> "Hourly builds from the apk-nightly branch — newest features, newest bugs."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextTertiary,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
                    )
                    GlassToggleRow(
                        icon = Icons.Filled.Update,
                        title = "Auto Check",
                        description = "On open and every 12 hours",
                        checked = autoCheck,
                        onCheckedChange = { container.updateManager.setAutoCheck(it) },
                    )
                    GlassRow(
                        icon = Icons.Filled.NotificationsActive,
                        title = "Notifications",
                        description = "Android notification settings",
                        trailing = {
                            GlassRowValue(if (container.updateManager.notificationsEnabled()) "On" else "Off")
                        },
                        onClick = { openNotificationSettings(context) },
                    )
                    GlassRow(
                        icon = Icons.Filled.Refresh,
                        title = "Check For Updates",
                        description = if (state.isChecking) "Checking…" else state.error ?: checkSummary(state),
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "Updates → check")
                            container.updateManager.check()
                        },
                    )
                    GlassRow(
                        icon = Icons.Filled.Notifications,
                        title = "Test Notification",
                        description = testResult ?: "Send a test notification",
                        trailing = {
                            TextButton(
                                onClick = {
                                    val sent = container.updateManager.notifyTest()
                                    val blocked = if (sent) null else container.updateManager.notificationBlockedReason()
                                    testResult = if (sent) {
                                        "Sent — it is in your notification shade like any other app's alert."
                                    } else {
                                        "Not sent: " + (blocked ?: "the system refused it.")
                                    }
                                    offerNotificationSettings = blocked != null
                                },
                            ) {
                                Text("Send", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
                            }
                        },
                    )
                    if (offerNotificationSettings) {
                        Box(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 14.dp)) {
                            TextButton(onClick = { openNotificationSettings(context) }) {
                                Text(
                                    text = "Open Anisu's notification settings",
                                    color = AnsuColors.Accent,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                    }
                    GlassRow(
                        icon = Icons.Filled.Delete,
                        title = "Clear Downloaded Updates",
                        description = "Free space used by update files",
                        trailing = {
                            GlassRowValue(
                                text = if (apkCount > 0) "$apkCount in the cache" else "Empty",
                                color = if (apkCount > 0) AnsuColors.Error else AnsuColors.TextSecondary,
                            )
                        },
                        onClick = {
                            val cleared = container.updateManager.clearCachedApks()
                            apkCount = container.updateManager.cachedApkCount()
                            container.diagnostics.log(LogCategory.CLICK, "Updates → clear downloaded APKs (cleared=$cleared)")
                            Toast.makeText(
                                context,
                                if (cleared) "Deleted successfully" else "Failed to delete some files",
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                    GlassRow(
                        icon = Icons.AutoMirrored.Filled.Article,
                        title = "Changelog",
                        description = "What's new in this build",
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "Updates → changelog opened")
                            openChangelog(context)
                        },
                    )
                }
            }

            Text(
                text = "Builds are published from the official Anisu repository " +
                    "(github.com/Ansu216/Anisu). Android verifies the signature before installing, " +
                    "so an update can only ever be Anisu itself.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(top = 18.dp, bottom = 24.dp, start = 4.dp, end = 4.dp),
            )
        }
    }
}

/**
 * The status card: what is installed, what the updater is doing, and the one button that acts on it, so the
 * screen answers its own question before anything else. The installed version code lives in the card's small
 * print instead of a row of its own, which is what keeps this to three things.
 */
@Composable
private fun StatusCard(
    state: UpdateState,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val available = state.result as? UpdateCheckResult.Available
    FrostedGlassCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tintAlpha = 0.4f) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(AnsuColors.Accent.copy(alpha = 0.20f), AnsuColors.BackgroundElevated),
                    ),
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(42.dp).clip(CircleShape).background(AnsuColors.AccentSoft),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.SystemUpdate,
                        contentDescription = null,
                        tint = AnsuColors.Accent,
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 14.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Anisu ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = AnsuColors.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    val status = heroStatus(state)
                    Text(text = status.first, style = MaterialTheme.typography.labelSmall, color = status.second)
                }
            }

            // The button follows the state instead of sitting in a row of its own: nothing to install means
            // it checks, something to install means it installs, and while the file comes down it is the
            // progress of that download.
            if (state.isDownloading) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress },
                    color = AnsuColors.Accent,
                    trackColor = AnsuColors.AccentSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                Button(
                    onClick = if (available != null) onInstall else onCheck,
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnsuColors.Accent,
                        contentColor = AnsuColors.OnAccent,
                    ),
                    modifier = Modifier.fillMaxWidth().height(44.dp),
                ) {
                    Icon(
                        imageVector = if (available != null) Icons.Filled.Download else Icons.Filled.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = if (available != null) "Download & install" else "Check for updates",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            // The notes of the build on offer belong to the button above them, so they sit in this card
            // instead of taking a block of their own further down the page.
            available?.takeIf { !state.isDownloading }?.update?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = AnsuColors.TextSecondary,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Text(
                text = statusSmallPrint(state),
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
            )
        }
    }
}

/**
 * What the updater is doing, as the card's headline line. It never mentions the channel: the channel is a
 * setting, and it is named in the small print where the installed build already is.
 */
private fun heroStatus(state: UpdateState): Pair<String, Color> {
    val failure = state.error
    return when {
        state.isDownloading -> "Downloading ${(state.downloadProgress * 100).roundToInt()}%" to AnsuColors.Accent
        state.isChecking -> "Checking for updates…" to AnsuColors.TextSecondary
        failure != null -> failure to AnsuColors.Error
        else -> when (val result = state.result) {
            null -> "Tap to look for a new build" to AnsuColors.TextSecondary
            is UpdateCheckResult.UpToDate -> "Up to date (${result.latestVersionName})" to AnsuColors.ScoreGreen
            is UpdateCheckResult.Failed -> result.message to AnsuColors.Error
            is UpdateCheckResult.Available -> "${result.update.versionName} is ready to install" to AnsuColors.Accent
        }
    }
}

/** The card's small print: the installed build, its version code, and how far a download has got. */
private fun statusSmallPrint(state: UpdateState): String {
    val installed = "Anisu ${BuildConfig.VERSION_NAME} · version code ${BuildConfig.VERSION_CODE}"
    val channel = if (BuildConfig.RELEASE_CHANNEL == "nightly") "Nightly channel" else "Stable channel"
    return if (state.isDownloading) {
        "${(state.downloadProgress * 100).roundToInt()}% · $installed"
    } else {
        "$installed · $channel"
    }
}

/** What the last check found, as the "Check for updates" row describes it. */
private fun checkSummary(state: UpdateState): String = when (val result = state.result) {
    null -> "Tap to look for a new build"
    is UpdateCheckResult.UpToDate -> "Up to date (${result.latestVersionName})"
    is UpdateCheckResult.Failed -> result.message
    is UpdateCheckResult.Available -> "An update is available: ${result.update.versionName}"
}

/**
 * Opens Ansu's own notification screen, where the runtime permission's switch and the "App updates"
 * channel both live. `ACTION_APP_NOTIFICATION_SETTINGS` exists since Android 8, this app's `minSdk`.
 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

/** The changelog at this build's commit, read in the browser: it needs no screen of its own. */
private fun openChangelog(context: Context) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(CHANGELOG_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private const val CHANGELOG_URL = "https://github.com/Ansu216/Anisu/blob/main/CHANGELOG.md"

/**
 * The channel as a two-option switch that fits on a row's right, instead of a card of its own: picking the
 * channel is the setting people come here for, so it should not need a second screen or a whole card.
 */
@Composable
private fun InlineChannelSelector(channel: UpdateChannel, onChannelChange: (UpdateChannel) -> Unit) {
    FrostedGlassCard(
        modifier = Modifier.width(150.dp).height(34.dp),
        shape = RoundedCornerShape(17.dp),
        tintAlpha = 0.3f,
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(3.dp)) {
            ChannelSegment(
                text = "Releases",
                isSelected = channel == UpdateChannel.RELEASES,
                onClick = { onChannelChange(UpdateChannel.RELEASES) },
                modifier = Modifier.weight(1f),
            )
            ChannelSegment(
                text = "Nightly",
                isSelected = channel == UpdateChannel.NIGHTLY,
                onClick = { onChannelChange(UpdateChannel.NIGHTLY) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun ChannelSegment(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(percent = 50))
            .background(if (isSelected) AnsuColors.Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) AnsuColors.OnAccent else AnsuColors.TextSecondary,
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
