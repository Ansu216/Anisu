@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnsuColors

/**
 * Settings is organized as a stack of category cards, the same shape used by the rest of the app:
 * a small section label over a frosted-glass group whose rows drill into a single sub-screen. The
 * groups read top to bottom as the things a user actually looks for — their account, how the app
 * looks, where content comes from, the app itself, and finally the credits.
 */
@Composable
fun SettingsScreen(container: AppContainer, navController: NavHostController) {
    val viewer by container.aniListRepository.viewer.collectAsStateWithLifecycle()
    val isLoggedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SettingsGroup(title = "Account") {
                AccountRow(
                    name = viewer?.name,
                    avatarUrl = viewer?.avatarUrl,
                    isLoggedIn = isLoggedIn,
                    onConnect = { navController.navigate(Dest.ANILIST_LOGIN) },
                    onLogout = { container.aniListRepository.logout() },
                )
            }

            SettingsGroup(title = "Personalization") {
                SettingsRow(
                    icon = Icons.Filled.Palette,
                    title = "Appearance",
                    subtitle = "Floating bar roundness, opacity and blur",
                    showDivider = false,
                    onClick = { navController.navigate(Dest.APPEARANCE) },
                )
            }

            SettingsGroup(title = "Playback") {
                SettingsRow(
                    icon = Icons.Filled.PlayCircle,
                    title = "Player and streaming",
                    subtitle = "Double tap skip, brightness and volume gestures",
                    showDivider = false,
                    onClick = { navController.navigate(Dest.PLAYER_STREAMING) },
                )
            }

            SettingsGroup(title = "Content & sources") {
                SettingsRow(
                    icon = Icons.Filled.Extension,
                    title = "Extensions",
                    subtitle = "Manage installed anime source extensions",
                    showDivider = false,
                    onClick = { navController.navigate(Dest.EXTENSIONS) },
                )
                SettingsRow(
                    icon = Icons.Filled.Hub,
                    title = "Addons",
                    subtitle = "Manage Stremio/Nuvio-protocol addons",
                    onClick = { navController.navigate(Dest.ADDONS) },
                )
            }

            SettingsGroup(title = "App") {
                SettingsRow(
                    icon = Icons.Filled.SystemUpdate,
                    title = "Updates",
                    subtitle = "Channel, automatic checks and install",
                    showDivider = false,
                    onClick = { navController.navigate(Dest.UPDATES) },
                )
                SettingsRow(
                    icon = Icons.Filled.BugReport,
                    title = "System",
                    subtitle = "Diagnostics and export logs",
                    onClick = { navController.navigate(Dest.SYSTEM) },
                )
            }

            SettingsGroup(title = "Info") {
                SettingsRow(
                    icon = Icons.Filled.Info,
                    title = "About",
                    subtitle = "Version, credits and links",
                    showDivider = false,
                    onClick = { navController.navigate(Dest.ABOUT) },
                )
            }
        }
    }
}

/** One category: a small label above a frosted card holding its rows. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Text(
            text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = AnsuColors.TextTertiary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(start = 10.dp, bottom = 8.dp),
        )
        FrostedGlassCard(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            tintAlpha = 0.5f,
        ) {
            Column { content() }
        }
    }
}

/** The AniList account block, kept at the top of Settings because it is the one live-status row. */
@Composable
private fun AccountRow(
    name: String?,
    avatarUrl: String?,
    isLoggedIn: Boolean,
    onConnect: () -> Unit,
    onLogout: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        if (avatarUrl != null) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.size(44.dp).clip(CircleShape),
            )
        } else {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(AnsuColors.AccentSoft),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(name ?: "Not connected", style = MaterialTheme.typography.titleSmall)
            Text(
                if (isLoggedIn) "AniList connected" else "Connect to sync progress",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )
        }
        if (isLoggedIn) {
            TextButton(onClick = onLogout) { Text("Log out") }
        } else {
            Button(onClick = onConnect) { Text("Connect") }
        }
    }
}

/** A single tappable row inside a group. [showDivider] is false for the first row of each group. */
@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    showDivider: Boolean = true,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (showDivider) {
            HorizontalDivider(color = AnsuColors.StrokeGlass)
        }
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).background(AnsuColors.AccentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = AnsuColors.Accent, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f).padding(start = 14.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = AnsuColors.TextPrimary)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = AnsuColors.TextTertiary)
        }
    }
}
