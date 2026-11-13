@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.theme.AnsuColors

private const val NOTIFICATION_PREFS = "update_notifications"

/**
 * The notification settings subscreen, mirroring vivi-music's `NotificationPermission`: the description,
 * the big "Enable Notifications" capsule that opens Android's notification screen, and the per-kind
 * switches. vivi's "new release" / "taste based" rows are music-specific, so the anime versions are
 * "Update Notifications" and "Download Notifications", both stored in Anisu's own prefs.
 */
@Composable
fun NotificationsScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(NOTIFICATION_PREFS, Context.MODE_PRIVATE) }
    var updatesEnabled by remember { mutableStateOf(prefs.getBoolean("updates", true)) }
    var downloadsEnabled by remember { mutableStateOf(prefs.getBoolean("downloads", true)) }
    var nightlyEnabled by remember { mutableStateOf(prefs.getBoolean("nightly", true)) }
    val systemEnabled = container.updateManager.notificationsEnabled()
    val releaseEnabled by container.notificationPrefs.enabled.collectAsStateWithLifecycle()
    val watchingAlerts by container.notificationPrefs.newEpisodes.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text("Notification setting") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = "Configure and manage notifications for app updates, nightly releases and download " +
                    "progress. Disabling notifications stops background status updates from appearing.",
                style = MaterialTheme.typography.bodySmall,
                color = AnsuColors.TextSecondary,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(if (systemEnabled) AnsuColors.AccentSoft else AnsuColors.BackgroundElevated)
                    .clickable { openNotificationSettings(context) }
                    .padding(start = 24.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Enable Notifications",
                    style = MaterialTheme.typography.titleMedium,
                    color = AnsuColors.TextPrimary,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = systemEnabled,
                    onCheckedChange = { openNotificationSettings(context) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AnsuColors.OnAccent,
                        checkedTrackColor = AnsuColors.Accent,
                        uncheckedThumbColor = AnsuColors.TextSecondary,
                    ),
                    thumbContent = {
                        Icon(
                            imageVector = if (systemEnabled) Icons.Filled.Check else Icons.Filled.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            GlassGroupLabel("Notifications")
            GlassGroup {
                GlassToggleRow(
                    title = "Update Notifications",
                    description = "Notify when updates are available.",
                    checked = updatesEnabled && systemEnabled,
                    enabled = systemEnabled,
                    showDivider = false,
                    onCheckedChange = {
                        updatesEnabled = it
                        prefs.edit().putBoolean("updates", it).apply()
                    },
                )
                GlassToggleRow(
                    title = "Download Notifications",
                    description = "Show progress when downloading update.",
                    checked = downloadsEnabled && systemEnabled,
                    enabled = systemEnabled,
                    onCheckedChange = {
                        downloadsEnabled = it
                        prefs.edit().putBoolean("downloads", it).apply()
                    },
                )
                GlassToggleRow(
                    title = "Nightly Notifications",
                    description = "Notify when a new nightly build is published.",
                    checked = nightlyEnabled && systemEnabled,
                    enabled = systemEnabled,
                    onCheckedChange = {
                        nightlyEnabled = it
                        prefs.edit().putBoolean("nightly", it).apply()
                    },
                )
            }

            Spacer(Modifier.height(20.dp))
            GlassGroupLabel("Release notifications")
            GlassGroup {
                GlassToggleRow(
                    title = "Release Notifications",
                    description = "New releases and episodes of shows you follow.",
                    checked = releaseEnabled,
                    showDivider = false,
                    onCheckedChange = { on ->
                        container.notificationPrefs.setEnabled(on)
                        if (on) {
                            com.ansu.anime.data.notify.ReleaseCheckWorker.schedule(context)
                        } else {
                            com.ansu.anime.data.notify.ReleaseCheckWorker.cancel(context)
                        }
                    },
                )
                GlassToggleRow(
                    title = "Watching List Alerts",
                    description = "New episodes of your whole Watching list.",
                    checked = watchingAlerts && releaseEnabled,
                    enabled = releaseEnabled,
                    onCheckedChange = { container.notificationPrefs.setNewEpisodes(it) },
                )
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "The switch above opens Android's notification settings; the rows above choose " +
                    "which notifications Anisu asks the system to show.",
                style = MaterialTheme.typography.bodySmall,
                color = AnsuColors.TextTertiary,
            )
        }
    }
}
