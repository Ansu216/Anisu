@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
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
import androidx.compose.material3.TopAppBar
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
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Updates, as a settings page rather than a status card: the same grouped rows the rest of the app's
 * settings use, with the state of the installed build as the first row's trailing pill.
 *
 * The work is the one it always was — read what the project publishes on GitHub, download the APK
 * through a resumable `.part` file, hand a complete download to the system installer — split over two
 * screens: this page holds the settings, and [UpdateScreen] the build on offer with its download and
 * install. Tapping "System update" runs the check itself, so there is no separate "check" row.
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
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = "Updates",
                style = MaterialTheme.typography.headlineLarge,
                color = AnsuColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
            )

            GlassGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "System update",
                    description = "Anisu ${BuildConfig.VERSION_NAME} · version code ${BuildConfig.VERSION_CODE}",
                    trailing = { UpdateStatusPill(state) },
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
                )
            }

            GlassGroupLabel("Checks")
            GlassGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassToggleRow(
                    icon = Icons.Filled.Refresh,
                    title = "Auto check",
                    description = "On open and every 12 hours",
                    checked = autoCheck,
                    onCheckedChange = { container.updateManager.setAutoCheck(it) },
                    showDivider = false,
                )
                GlassToggleRow(
                    icon = Icons.Filled.Update,
                    title = "Nightly channel",
                    description = if (channel == UpdateChannel.NIGHTLY) {
                        "Every apk-nightly build, newer than the release"
                    } else {
                        "Off: tagged releases only. Turn on to follow the nightlies"
                    },
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
                    title = "Notifications",
                    description = "Android notification settings",
                    trailing = {
                        GlassRowValue(if (container.updateManager.notificationsEnabled()) "On" else "Off")
                    },
                    onClick = { openNotificationSettings(context) },
                )
            }

            GlassGroupLabel("Storage")
            GlassGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassRow(
                    icon = Icons.Filled.Delete,
                    title = "Clear downloaded updates",
                    description = if (apkCount > 0) "$apkCount APKs in the cache" else "No downloaded APKs",
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

            GlassGroupLabel("History")
            GlassGroup(modifier = Modifier.padding(horizontal = 16.dp)) {
                GlassRow(
                    icon = Icons.AutoMirrored.Filled.Article,
                    title = "Changelog",
                    description = "What changed in this build",
                    showDivider = false,
                    onClick = {
                        container.diagnostics.log(LogCategory.CLICK, "Updates → changelog opened")
                        navController.navigate(Dest.CHANGELOG)
                    },
                )
            }

            Text(
                text = "Builds are published from the official Anisu repository " +
                    "(github.com/Ansu216/Anisu). Android verifies the signature before installing, " +
                    "so an update can only ever be Anisu itself.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(top = 18.dp, start = 20.dp, end = 20.dp),
            )
        }
    }
}

/** The first row's trailing pill: the one thing the page has to say before anything else. */
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
