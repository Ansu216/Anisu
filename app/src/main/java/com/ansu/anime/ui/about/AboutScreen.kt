@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.BuildConfig
import com.ansu.anime.R
import com.ansu.anime.data.update.AvailableUpdate
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.data.update.UpdateChecker
import com.ansu.anime.data.update.UpdateState
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.roundToInt

/** The lead developer, i.e. the owner of the GitHub repository this app is published from. */
private const val DEVELOPER_NAME = "Ansuman Sahu"

/** GitHub account of the developer above — used for the avatar and the profile link. */
private const val DEVELOPER_HANDLE = "Ansu216"

/**
 * App info, the built-in updater and the developer credit — the last row of
 * Settings. The updater checks what this project publishes on GitHub (tagged
 * releases or the `apk-nightly` branch) and can download + hand the APK to
 * Android's installer, the same way extensions are sideloaded.
 */
@Composable
fun AboutScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val channel by container.updateManager.prefs.channel.collectAsStateWithLifecycle()
    val autoCheck by container.updateManager.prefs.autoCheck.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Re-check on entry so the answer on screen is never stale.
    LaunchedEffect(Unit) { container.updateManager.check() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            AppIdentityCard()

            UpdatesSection(
                state = state,
                channel = channel,
                autoCheck = autoCheck,
                onChannelChange = { container.updateManager.prefs.setChannel(it) },
                onAutoCheckChange = { container.updateManager.prefs.setAutoCheck(it) },
                onCheck = { container.updateManager.check() },
                onInstall = { container.updateManager.downloadAndInstall() },
            )

            DeveloperSection(onOpenUrl = { url -> openUrl(context, url) })

            LegalSection()
        }
    }
}

@Composable
private fun AppIdentityCard() {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tintAlpha = 0.35f,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(AnsuColors.BackgroundElevated),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_launcher_foreground),
                    contentDescription = null,
                    tint = AnsuColors.Accent,
                    modifier = Modifier.size(64.dp),
                )
            }
            Text(
                text = stringResource(R.string.app_name),
                color = AnsuColors.TextPrimary,
                fontSize = 24.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(top = 12.dp),
            )
            Text(
                text = stringResource(R.string.app_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )
            Text(
                text = "Version ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
    }
}

@Composable
private fun UpdatesSection(
    state: UpdateState,
    channel: UpdateChannel,
    autoCheck: Boolean,
    onChannelChange: (UpdateChannel) -> Unit,
    onAutoCheckChange: (Boolean) -> Unit,
    onCheck: () -> Unit,
    onInstall: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Updates")

        ChannelSelector(channel = channel, onChannelChange = onChannelChange)

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Automatic checks", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                Text(
                    text = "Look for a new build when the app opens",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                )
            }
            Switch(
                checked = autoCheck,
                onCheckedChange = onAutoCheckChange,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = AnsuColors.Background,
                    checkedTrackColor = AnsuColors.Accent,
                ),
            )
        }

        if (state.isChecking) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(
                    color = AnsuColors.Accent,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp),
                )
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
                text = "Ansu is up to date (${result.latestVersionName}).",
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

        state.error?.let { message ->
            StatusLine(icon = Icons.Filled.Warning, text = message, tint = AnsuColors.Error)
        }

        if (state.result !is UpdateCheckResult.Available) {
            Button(
                onClick = onCheck,
                enabled = !state.isChecking && !state.isDownloading,
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = AnsuColors.Accent,
                    contentColor = AnsuColors.Background,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Check for updates", fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** Two-segment pill in the app's glass style: stable releases vs hourly nightlies. */
@Composable
private fun ChannelSelector(channel: UpdateChannel, onChannelChange: (UpdateChannel) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Channel", style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
        FrostedGlassCard(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
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
    }
}

@Composable
private fun ChannelSegment(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(19.dp))
            .background(if (isSelected) AnsuColors.Accent else androidx.compose.ui.graphics.Color.Transparent)
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
private fun AvailableUpdateCard(
    update: AvailableUpdate,
    state: UpdateState,
    onInstall: () -> Unit,
    onRecheck: () -> Unit,
) {
    FrostedGlassCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        tintAlpha = 0.4f,
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                text = "Ansu ${update.versionName} is available",
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
                    maxLines = 14,
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
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnsuColors.Accent,
                        contentColor = AnsuColors.Background,
                    ),
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
}

@Composable
private fun StatusLine(icon: ImageVector, text: String, tint: androidx.compose.ui.graphics.Color) {
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

@Composable
private fun DeveloperSection(onOpenUrl: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionTitle("Lead developer")

        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            tintAlpha = 0.4f,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(AnsuColors.BackgroundElevated),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = AnsuColors.TextTertiary,
                        modifier = Modifier.size(30.dp),
                    )
                    AsyncImage(
                        model = "https://github.com/$DEVELOPER_HANDLE.png",
                        contentDescription = "Developer avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Column(modifier = Modifier.padding(start = 16.dp).weight(1f)) {
                    Text(
                        text = DEVELOPER_NAME,
                        style = MaterialTheme.typography.titleMedium,
                        color = AnsuColors.TextPrimary,
                    )
                    Text(
                        text = "Lead developer · @$DEVELOPER_HANDLE",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextSecondary,
                    )
                }
            }
        }

        LinkRow("GitHub profile", "https://github.com/$DEVELOPER_HANDLE", onOpenUrl)
        LinkRow("Repository", UpdateChecker.REPO_URL, onOpenUrl)
        LinkRow("Report an issue", "${UpdateChecker.REPO_URL}/issues", onOpenUrl)
        LinkRow("Releases", "${UpdateChecker.REPO_URL}/releases", onOpenUrl)
    }
}

@Composable
private fun LinkRow(title: String, url: String, onOpenUrl: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onOpenUrl(url) }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
            Text(
                text = url.removePrefix("https://"),
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AnsuColors.TextTertiary)
    }
}

@Composable
private fun LegalSection() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle("Credits")
        Text(
            text = "Ansu aggregates metadata from the public AniList API and plays media through " +
                "third-party sources, extensions and Stremio addons. It is not affiliated with, " +
                "endorsed by or sponsored by AniList or by any provider you connect.",
            style = MaterialTheme.typography.bodyMedium,
            color = AnsuColors.TextSecondary,
        )
        Text(
            text = "Anime and manga metadata © AniList. Package ${BuildConfig.APPLICATION_ID}.",
            style = MaterialTheme.typography.labelSmall,
            color = AnsuColors.TextTertiary,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
}

private fun openUrl(context: Context, url: String) {
    runCatching {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
