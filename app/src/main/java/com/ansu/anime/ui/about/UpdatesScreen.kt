@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.ansu.anime.ui.components.FadingTopAppBar
import com.ansu.anime.ui.components.withoutTop
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.BuildConfig
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.data.update.UpdateState
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassAlertDialog
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Update Settings, mirroring vivi-music's `ui/screens/settings/UpdateSettings.kt`:
 *
 *  1. System update (opens the update subscreen) · App version (seven taps unlock Developer options)
 *  2. Automatic update check · Nightly Updates · Notification setting
 *  3. Clear downloaded updates
 *  4. Changelog (Version history) · Commits
 *
 * The two update subscreens vivi routes to are kept: the update screen itself ([UpdateScreen],
 * `Dest.UPDATE`) and the notification settings screen ([NotificationsScreen], new). Changelog opens
 * `Dest.CHANGELOG`; Commits opens the new [CommitsScreen], so both the changelog and the build commit
 * are visible inside the app.
 */
@Composable
fun UpdatesScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val channel by container.updateManager.prefs.channel.collectAsStateWithLifecycle()
    val autoCheck by container.updateManager.prefs.autoCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Read once and refreshed after a clear, so the row shows the cache the moment it is emptied.
    var apkCount by remember { mutableIntStateOf(container.updateManager.cachedApkCount()) }
    var showStorageInfo by rememberSaveable { mutableStateOf(false) }
    val notificationsOn = container.updateManager.notificationsEnabled()
    val developerUnlocked by container.appearancePrefs.developerUnlocked.collectAsStateWithLifecycle()
    // Seven quick taps on App version unlock Developer options, counted within two seconds.
    var versionTaps by remember { mutableStateOf(0) }
    var lastVersionTap by remember { mutableStateOf(0L) }

    LaunchedEffect(Unit) { container.updateManager.check() }

    if (showStorageInfo) {
        GlassAlertDialog(
            onDismissRequest = { showStorageInfo = false },
            title = { Text("Downloaded updates") },
            text = {
                Text(
                    text = "Anisu keeps the APKs it downloaded so an install can be retried without " +
                        "downloading them again. Old files are deleted by themselves, and this row " +
                        "frees the space now.",
                    color = AnsuColors.TextSecondary,
                )
            },
            confirmButton = {
                TextButton(onClick = { showStorageInfo = false }) {
                    Text("OK", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
                }
            },
        )
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FadingTopAppBar(
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
                .padding(padding.withoutTop())
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 16.dp)
                .padding(bottom = 28.dp),
        ) {
            Spacer(Modifier.height(16.dp))

            // 1 · the build
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "System update",
                    trailing = { UpdateTrailing(state) },
                    showDivider = false,
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → system update opened")
                        navController.navigate(Dest.UPDATE)
                    },
                )
                GlassRow(
                    icon = Icons.Filled.Info,
                    title = "App version",
                    trailing = { GlassRowValue(BuildConfig.VERSION_NAME) },
                    onClick = {
                        val now = android.os.SystemClock.elapsedRealtime()
                        versionTaps = if (now - lastVersionTap > 2_000L) 1 else versionTaps + 1
                        lastVersionTap = now
                        when {
                            versionTaps >= 7 -> {
                                versionTaps = 0
                                container.appearancePrefs.unlockDeveloperOptions()
                                container.diagnostics.log(LogCategory.CLICK, "Updates → developer options unlocked")
                                Toast.makeText(context, "Developer options unlocked", Toast.LENGTH_SHORT).show()
                            }
                            versionTaps >= 4 && !developerUnlocked ->
                                Toast.makeText(
                                    context,
                                    "${7 - versionTaps} more taps to unlock Developer options",
                                    Toast.LENGTH_SHORT,
                                ).show()
                        }
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            // 2 · checks and notifications
            GlassGroup {
                GlassToggleRow(
                    icon = Icons.Filled.Refresh,
                    title = "Automatic update check",
                    description = "Automatically check for updates.",
                    checked = autoCheck,
                    onCheckedChange = { container.updateManager.setAutoCheck(it) },
                    showDivider = false,
                )
                GlassToggleRow(
                    icon = Icons.Filled.Update,
                    title = "Nightly Updates",
                    description = "Receive nightly builds",
                    checked = channel == UpdateChannel.NIGHTLY,
                    onCheckedChange = { nightly ->
                        container.diagnostics.log(
                            LogCategory.CLICK,
                            "Updates → nightly channel ${if (nightly) "on" else "off"}",
                        )
                        container.updateManager.prefs.setChannel(
                            if (nightly) UpdateChannel.NIGHTLY else UpdateChannel.RELEASES,
                        )
                    },
                )
                GlassRow(
                    icon = Icons.Filled.NotificationsActive,
                    title = "Notification setting",
                    description = "Configure notification settings.",
                    trailing = { GlassRowValue(if (notificationsOn) "Enabled" else "Disabled") },
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → notification settings opened")
                        navController.navigate(Dest.UPDATE_NOTIFICATIONS)
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            // 3 · storage
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.Delete,
                    title = "Clear downloaded updates",
                    description = if (apkCount > 0) "$apkCount APKs found" else "No downloaded APKs",
                    trailing = {
                        IconButton(
                            onClick = { showStorageInfo = true },
                            modifier = Modifier.size(34.dp),
                        ) {
                            Icon(
                                Icons.Filled.Info,
                                contentDescription = "What is kept",
                                tint = AnsuColors.TextSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    },
                    showDivider = false,
                    onClick = {
                        val cleared = container.updateManager.clearCachedApks()
                        apkCount = container.updateManager.cachedApkCount()
                        container.diagnostics.log(
                            LogCategory.CLICK,
                            "Updates → clear downloaded APKs (cleared=$cleared)",
                        )
                        Toast.makeText(
                            context,
                            if (cleared) "Deleted successfully" else "Failed to delete some files",
                            Toast.LENGTH_SHORT,
                        ).show()
                    },
                )
            }

            Spacer(Modifier.height(10.dp))

            // 4 · history
            GlassGroup {
                GlassRow(
                    icon = Icons.Filled.History,
                    title = "Changelog",
                    description = "View version history",
                    showDivider = false,
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → changelog opened")
                        navController.navigate(Dest.CHANGELOG)
                    },
                )
                GlassRow(
                    icon = Icons.Filled.Code,
                    title = "Commits",
                    description = "View commit history",
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → commits opened")
                        navController.navigate(Dest.UPDATE_COMMITS)
                    },
                )
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "Builds are published from the official Anisu repository " +
                    "(github.com/Ansu216/Anisu). Android verifies the signature before installing, " +
                    "so an update can only ever be Anisu itself.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp),
            )
        }
    }
}

/** The trailing line of "System update", worded as vivi's updater words it: "Update" / "Up to date". */
@Composable
private fun UpdateTrailing(state: UpdateState) {
    val (label, color) = when {
        state.isChecking -> "Checking…" to AnsuColors.TextSecondary
        state.isDownloading -> "Downloading" to AnsuColors.Accent
        state.result is UpdateCheckResult.Available -> "Update" to AnsuColors.Error
        state.result is UpdateCheckResult.Failed -> "Check failed" to AnsuColors.Error
        else -> "Up to date" to AnsuColors.ScoreGreen
    }
    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = color,
        fontWeight = FontWeight.Medium,
    )
}

/** The first row's trailing pill used by [UpdateScreen]: the one thing the page has to say first. */
@Composable
internal fun UpdateStatusPill(state: UpdateState, modifier: Modifier = Modifier) {
    val (label, color) = updateStatus(state)
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

/** What the updater is doing, as a label and the colour that belongs to it. */
internal fun updateStatus(state: UpdateState): Pair<String, Color> {
    val failure = state.error
    return when {
        state.isDownloading -> "Downloading" to AnsuColors.Accent
        state.isChecking -> "Checking…" to AnsuColors.TextSecondary
        failure != null -> "Download failed" to AnsuColors.Error
        else -> when (val result = state.result) {
            null -> "Not checked" to AnsuColors.TextSecondary
            is UpdateCheckResult.UpToDate -> "Up to date" to AnsuColors.ScoreGreen
            is UpdateCheckResult.Failed -> "Check failed" to AnsuColors.Error
            is UpdateCheckResult.Available -> "Update available" to AnsuColors.Error
        }
    }
}

/**
 * Opens Anisu's own notification screen, where the runtime permission's switch and the "App updates"
 * channel both live. `ACTION_APP_NOTIFICATION_SETTINGS` exists since Android 8, this app's `minSdk`.
 */
internal fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}
