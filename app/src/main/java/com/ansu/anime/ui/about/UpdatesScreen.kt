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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Numbers
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
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
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/**
 * Updates, rebuilt the way the settings apps people already know do it: a big page title, a header
 * that says what is installed and what is happening, then short groups of rows — install, check, the
 * installed version, the channel, the automatic-check options with the notification tools, and the
 * storage rows. Every card is the same black glass used across Anisu, and every row follows the same
 * 16dp grid, so this screen reads like the rest of the app.
 *
 * The behaviour is the one it always had: it reads what the project publishes on GitHub, downloads
 * the APK through a resumable `.part` file and hands only a complete download to the system installer.
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

    LaunchedEffect(Unit) { container.updateManager.check() }

    Scaffold(
        containerColor = AnsuColors.Background,
        topBar = {
            TopAppBar(
                title = {},
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
            Text(
                text = "Updates",
                style = MaterialTheme.typography.displayLarge,
                color = AnsuColors.TextPrimary,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )

            InstalledHeader(state = state, channel = channel, modifier = Modifier.padding(top = 16.dp))

            GlassGroupLabel("App")
            GlassGroup {
                val available = state.result as? UpdateCheckResult.Available
                when {
                    state.isDownloading -> DownloadingBlock(state = state)
                    available != null -> GlassRow(
                        icon = Icons.Filled.Download,
                        title = "Download & install",
                        description = channelLabel(available.update.channel),
                        showDivider = false,
                        onClick = {
                            container.diagnostics.log(LogCategory.CLICK, "Updates → download ${available.update.versionName}")
                            container.updateManager.downloadAndInstall()
                        },
                    )
                }
                available?.takeIf { !state.isDownloading }?.update?.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    Text(
                        text = notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        maxLines = 10,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    )
                }
                GlassRow(
                    icon = Icons.Filled.Refresh,
                    title = "Check for updates",
                    description = if (state.isChecking) "Checking…" else state.error ?: checkSummary(state),
                    showDivider = state.isDownloading || available != null,
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → check")
                        container.updateManager.check()
                    },
                )
                // No "Version" row here on purpose: the header above already names the installed
                // build, and repeating it is exactly the duplication About was cleaned of.
                GlassRow(
                    icon = Icons.Filled.Numbers,
                    title = "Version code",
                    trailing = { GlassRowValue(BuildConfig.VERSION_CODE.toString()) },
                )
            }

            GlassGroupLabel("Channel")
            GlassGroup {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    ChannelSelector(
                        channel = channel,
                        onChannelChange = { picked ->
                            container.diagnostics.log(LogCategory.CLICK, "Updates → channel ${picked.name.lowercase()}")
                            container.updateManager.prefs.setChannel(picked)
                        },
                    )
                }
            }

            GlassGroupLabel("Automatic checks")
            GlassGroup {
                GlassToggleRow(
                    icon = Icons.Filled.Update,
                    title = "Check automatically",
                    description = "On open and every 12 hours",
                    checked = autoCheck,
                    onCheckedChange = { container.updateManager.setAutoCheck(it) },
                    showDivider = false,
                )
                GlassRow(
                    icon = Icons.Filled.Notifications,
                    title = "Test notification",
                    description = testResult ?: "Post a real system notification now",
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
                    icon = Icons.Filled.NotificationsActive,
                    title = "Notification settings",
                    description = "Ansu's notifications in Android",
                    trailing = {
                        GlassRowValue(if (container.updateManager.notificationsEnabled()) "On" else "Off")
                    },
                    onClick = { openNotificationSettings(context) },
                )
            }

            GlassGroupLabel("Storage")
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.Delete,
                    title = "Clear downloaded updates",
                    description = "Frees the update APKs Anisu downloaded",
                    trailing = {
                        GlassRowValue(
                            text = if (apkCount > 0) "$apkCount in the cache" else "Empty",
                            color = if (apkCount > 0) AnsuColors.Error else AnsuColors.TextSecondary,
                        )
                    },
                    showDivider = false,
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
                    description = "What changed in this build",
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → changelog opened")
                        openChangelog(context)
                    },
                )
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

/** The header card: what is installed and what the updater is doing right now, over a soft accent glow. */
@Composable
private fun InstalledHeader(state: UpdateState, channel: UpdateChannel, modifier: Modifier = Modifier) {
    FrostedGlassCard(modifier = modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), tintAlpha = 0.4f) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        listOf(AnsuColors.Accent.copy(alpha = 0.20f), AnsuColors.BackgroundElevated),
                    ),
                )
                .padding(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.SystemUpdate,
                    contentDescription = null,
                    tint = AnsuColors.TextPrimary,
                    modifier = Modifier.size(34.dp),
                )
                Column(
                    modifier = Modifier.padding(start = 16.dp).weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = "Anisu ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleLarge,
                        color = AnsuColors.TextPrimary,
                        fontWeight = FontWeight.ExtraBold,
                    )
                    val status = heroStatus(state, channel)
                    Text(
                        text = status.first,
                        style = MaterialTheme.typography.labelSmall,
                        color = status.second,
                    )
                }
            }
        }
    }
}

/** The row that replaces the install row while an APK is coming down, with the live progress under it. */
@Composable
private fun DownloadingBlock(state: UpdateState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GlassRow(
            icon = Icons.Filled.Download,
            title = "Download & install",
            description = "Downloading… ${(state.downloadProgress * 100).roundToInt()}%",
            showDivider = false,
        )
        LinearProgressIndicator(
            progress = { state.downloadProgress },
            color = AnsuColors.Accent,
            trackColor = AnsuColors.AccentSoft,
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        )
    }
}

/** What the updater is doing, as the header's second line. */
private fun heroStatus(state: UpdateState, channel: UpdateChannel): Pair<String, Color> {
    val failure = state.error
    return when {
        state.isDownloading -> "Downloading ${(state.downloadProgress * 100).roundToInt()}%" to AnsuColors.Accent
        state.isChecking -> "Checking for updates…" to AnsuColors.TextSecondary
        failure != null -> failure to AnsuColors.Error
        else -> when (val result = state.result) {
            null -> "Channel: ${channelLabel(channel)}" to AnsuColors.TextSecondary
            is UpdateCheckResult.UpToDate -> "Up to date (${result.latestVersionName})" to AnsuColors.ScoreGreen
            is UpdateCheckResult.Failed -> result.message to AnsuColors.Error
            is UpdateCheckResult.Available -> "${result.update.versionName} is ready to install" to AnsuColors.Accent
        }
    }
}

/** What the last check found, as the "Check for updates" row describes it. */
private fun checkSummary(state: UpdateState): String = when (val result = state.result) {
    null -> "Tap to look for a new build"
    is UpdateCheckResult.UpToDate -> "Up to date (${result.latestVersionName})"
    is UpdateCheckResult.Failed -> result.message
    is UpdateCheckResult.Available -> "An update is available: ${result.update.versionName}"
}

private fun channelLabel(channel: UpdateChannel): String =
    if (channel == UpdateChannel.NIGHTLY) "Nightly build" else "Stable release"

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

@Composable
private fun ChannelSelector(channel: UpdateChannel, onChannelChange: (UpdateChannel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(23.dp),
            tintAlpha = 0.3f,
        ) {
            Row(modifier = Modifier.fillMaxSize().padding(4.dp)) {
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
        Text(
            text = when (channel) {
                UpdateChannel.RELEASES -> "Stable, tagged releases only."
                UpdateChannel.NIGHTLY -> "Hourly builds from the apk-nightly branch — newest features, newest bugs."
            },
            style = MaterialTheme.typography.labelSmall,
            color = AnsuColors.TextTertiary,
        )
    }
}

@Composable
private fun ChannelSegment(text: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(19.dp))
            .background(if (isSelected) AnsuColors.Accent else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = if (isSelected) AnsuColors.OnAccent else AnsuColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}
