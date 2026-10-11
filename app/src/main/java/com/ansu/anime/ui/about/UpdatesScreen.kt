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
import androidx.compose.material.icons.filled.SystemUpdate
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
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
 * Updates: the app's store page, rebuilt from scratch. A header card leads with the installed build and
 * the channel, then the channel picker, the automatic-check options and the live status each get their
 * own black-glass card. It shows what the project publishes on GitHub and downloads + installs the APK
 * in one tap, through a resumable `.part` file that only becomes installable when it completes.
 */
@Composable
fun UpdatesScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val channel by container.updateManager.prefs.channel.collectAsStateWithLifecycle()
    val autoCheck by container.updateManager.prefs.autoCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var testResult by remember { mutableStateOf<String?>(null) }
    var offerNotificationSettings by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { container.updateManager.check() }

    Scaffold(
        containerColor = AnsuColors.Background,
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            InstalledHeader()

            SectionCard("Channel") {
                ChannelSelector(channel = channel, onChannelChange = { container.updateManager.prefs.setChannel(it) })
            }

            SectionCard("Automatic checks") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "Look for a new build on open and every 12 hours",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = autoCheck,
                        onCheckedChange = { container.updateManager.setAutoCheck(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = AnsuColors.OnAccent,
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
            }

            SectionCard("Status") {
                StatusSection(
                    state = state,
                    onCheck = { container.updateManager.check() },
                    onInstall = { container.updateManager.downloadAndInstall() },
                )
            }

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

/** The header card: what is installed, which channel it came from, over a soft accent glow. */
@Composable
private fun InstalledHeader() {
    val channel = BuildConfig.RELEASE_CHANNEL.replaceFirstChar { it.uppercase() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(AnsuColors.Accent.copy(alpha = 0.20f), AnsuColors.BackgroundElevated),
                ),
            )
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.SystemUpdate, contentDescription = null, tint = AnsuColors.TextPrimary, modifier = Modifier.size(34.dp))
            Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                Text(
                    text = "Anisu ${BuildConfig.VERSION_NAME}",
                    style = MaterialTheme.typography.titleLarge,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "Build ${BuildConfig.VERSION_CODE} · $channel channel",
                    style = MaterialTheme.typography.labelSmall,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

/** A black-glass card with a heading, so every block on the page reads as its own little section. */
@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary, fontWeight = FontWeight.Bold)
            content()
        }
    }
}

@Composable
private fun TestNotificationRow(result: String?, onSend: () -> Unit, onOpenSettings: (() -> Unit)?) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Test notification", style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextPrimary)
            Text(
                text = result ?: "Post a real system notification now",
                style = MaterialTheme.typography.bodySmall,
                color = if (result == null) AnsuColors.TextSecondary else AnsuColors.TextPrimary,
            )
            if (onOpenSettings != null) {
                TextButton(
                    onClick = onOpenSettings,
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.padding(top = 2.dp),
                ) {
                    Text("Open Anisu's notification settings", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
                }
            }
        }
        TextButton(onClick = onSend) {
            Text("Send", color = AnsuColors.Accent, fontWeight = FontWeight.Bold)
        }
    }
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
            is UpdateCheckResult.Failed -> StatusLine(icon = Icons.Filled.Warning, text = result.message, tint = AnsuColors.Error)
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
                colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check for updates", fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun AvailableUpdateCard(update: AvailableUpdate, state: UpdateState, onInstall: () -> Unit, onRecheck: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
                colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Download & install", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }
            TextButton(onClick = onRecheck, modifier = Modifier.align(Alignment.End)) {
                Text("Check again")
            }
        }
    }
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, tint: Color) {
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
