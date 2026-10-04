@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.BuildConfig
import com.ansu.anime.data.update.AvailableUpdate
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.data.update.UpdateState
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/**
 * The Updates screen: the app's own store page. It shows the installed build, lets the user pick a
 * channel (stable Releases or the hourly Nightly branch), checks what the project publishes on GitHub
 * and downloads + installs the APK in one tap. The download is written to a `.part` file first and only
 * becomes an installable APK when it completes, so an interrupted download can be resumed.
 */
@Composable
fun UpdatesScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val channel by container.updateManager.prefs.channel.collectAsStateWithLifecycle()
    val autoCheck by container.updateManager.prefs.autoCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    // Result of the last "send me a test notification" tap; nothing until it is pressed.
    var testResult by remember { mutableStateOf<String?>(null) }
    // Only when the system is the reason nothing was posted is there a setting to go and change.
    var offerNotificationSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.updateManager.check() }

    Scaffold(
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
                        onClick = { container.updateManager.check() },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            InstalledVersionCard()

            ChannelSelector(channel = channel, onChannelChange = { container.updateManager.prefs.setChannel(it) })

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Automatic checks", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                    Text(
                        text = "Look for a new build when the app opens and every 12 hours in the background",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                    )
                }
                Switch(
                    checked = autoCheck,
                    onCheckedChange = { container.updateManager.setAutoCheck(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = AnsuColors.Background,
                        checkedTrackColor = AnsuColors.Accent,
                    ),
                )
            }

            TestNotificationRow(
                result = testResult,
                onSend = {
                    val sent = container.updateManager.notifyTest()
                    val blocked = if (sent) null else container.updateManager.notificationBlockedReason()
                    testResult = if (sent) {
                        "Sent — it is in your notification shade like any other app's alert."
                    } else {
                        "Not sent: " + (blocked ?: "the system refused it.")
                    }
                    offerNotificationSettings = blocked != null
                },
                onOpenSettings = if (offerNotificationSettings) {
                    { openNotificationSettings(context) }
                } else {
                    null
                },
            )

            StatusSection(
                state = state,
                onCheck = { container.updateManager.check() },
                onInstall = { container.updateManager.downloadAndInstall() },
            )

            Text(
                text = "Builds are published from the official Anisu repository " +
                    "(github.com/Ansu216/Anisu). Android verifies the signature before installing, " +
                    "so an update can only ever be Anisu itself.",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
            )
        }
    }
}

/**
 * A real system notification on demand, so the user can check on their own device that Ansu alerts
 * arrive without waiting for a new build to be published. When nothing is posted, the reason the
 * system gave is shown, together with a way straight into the setting that would fix it — a missing
 * permission or a switched-off notification channel cannot be fixed from inside the app.
 */
@Composable
private fun TestNotificationRow(result: String?, onSend: () -> Unit, onOpenSettings: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Test notification", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
            Text(
                text = result ?: "Post a real system notification now",
                style = MaterialTheme.typography.bodyMedium,
                color = if (result == null) AnsuColors.TextSecondary else AnsuColors.TextPrimary,
            )
            if (onOpenSettings != null) {
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text(
                        text = "Open Anisu's notification settings",
                        color = AnsuColors.Accent,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        TextButton(onClick = onSend) {
            Text("Send", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Opens Ansu's own notification screen, which is where the runtime permission's switch and the
 * "App updates" channel both live. `ACTION_APP_NOTIFICATION_SETTINGS` exists since Android 8, which
 * is this app's `minSdk`, so no older fallback is needed.
 */
private fun openNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

@Composable
private fun InstalledVersionCard() {
    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
        Row(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Installed version", style = MaterialTheme.typography.labelSmall, color = AnsuColors.TextTertiary)
                Text(
                    text = BuildConfig.VERSION_NAME,
                    style = MaterialTheme.typography.titleMedium,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "Build ${BuildConfig.VERSION_CODE}",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextTertiary,
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(AnsuColors.AccentSoft)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("Anisu", color = AnsuColors.TextPrimary, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun ChannelSelector(channel: UpdateChannel, onChannelChange: (UpdateChannel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Channel", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth().height(46.dp),
            shape = RoundedCornerShape(23.dp),
            tintAlpha = 0.4f,
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
            color = if (isSelected) AnsuColors.Background else AnsuColors.TextSecondary,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun StatusSection(state: UpdateState, onCheck: () -> Unit, onInstall: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (state.isChecking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(color = AnsuColors.Accent, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                Text(
                    text = "Checking for updates…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }

        when (val result = state.result) {
            null -> Unit
            is UpdateCheckResult.UpToDate -> StatusLine(
                icon = Icons.Filled.CheckCircle,
                text = "Anisu is up to date (${result.latestVersionName}).",
                tint = AnsuColors.ScoreGreen,
            )
            is UpdateCheckResult.Failed -> StatusLine(
                icon = Icons.Filled.Warning,
                text = result.message,
                tint = AnsuColors.Error,
            )
            is UpdateCheckResult.Available -> AvailableUpdateCard(
                update = result.update,
                state = state,
                onInstall = onInstall,
                onRecheck = onCheck,
            )
        }

        state.error?.let { message -> StatusLine(icon = Icons.Filled.Warning, text = message, tint = AnsuColors.Error) }

        if (state.result !is UpdateCheckResult.Available) {
            Button(
                onClick = onCheck,
                enabled = !state.isChecking && !state.isDownloading,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.Background),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check for updates", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AvailableUpdateCard(
    update: AvailableUpdate,
    state: UpdateState,
    onInstall: () -> Unit,
    onRecheck: () -> Unit,
) {
    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Anisu ${update.versionName} is available",
                style = MaterialTheme.typography.titleSmall,
                color = AnsuColors.TextPrimary,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = if (update.channel == UpdateChannel.NIGHTLY) "Nightly build" else "Stable release",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(top = 2.dp),
            )

            update.notes?.let { notes ->
                Spacer(Modifier.height(10.dp))
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                    maxLines = 16,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(14.dp))

            if (state.isDownloading) {
                LinearProgressIndicator(
                    progress = { state.downloadProgress },
                    color = AnsuColors.Accent,
                    trackColor = AnsuColors.AccentSoft,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "Downloading… ${(state.downloadProgress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(top = 8.dp),
                )
            } else {
                Button(
                    onClick = onInstall,
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.Background),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Download & install", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                androidx.compose.material3.TextButton(onClick = onRecheck, modifier = Modifier.align(Alignment.End)) {
                    Text("Check again")
                }
            }
        }
    }
}

@Composable
private fun StatusLine(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, tint: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = tint,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
