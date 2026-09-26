package com.ansu.anime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.AnisuBottomBar
import com.ansu.anime.ui.navigation.Dest
import com.ansu.anime.ui.theme.AnisuTextSecondary

@Composable
fun SettingsScreen(container: AppContainer, navController: NavHostController) {
    val viewer by container.aniListRepository.viewer.collectAsStateWithLifecycle()
    val isLoggedIn by container.aniListRepository.isLoggedIn.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        bottomBar = { AnisuBottomBar(navController, Dest.SETTINGS) },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (viewer?.avatarUrl != null) {
                        AsyncImage(
                            model = viewer?.avatarUrl,
                            contentDescription = null,
                            modifier = Modifier.size(44.dp).clip(CircleShape),
                        )
                    }
                    Column(modifier = Modifier.padding(start = 12.dp)) {
                        Text(viewer?.name ?: "Not connected", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (isLoggedIn) "AniList connected" else "Connect to sync progress",
                            style = MaterialTheme.typography.bodyMedium,
                            color = AnisuTextSecondary,
                        )
                    }
                }
                if (isLoggedIn) {
                    TextButton(onClick = { container.aniListRepository.logout() }) { Text("Log out") }
                } else {
                    Button(onClick = { navController.navigate(Dest.ANILIST_LOGIN) }) { Text("Connect") }
                }
            }

            HorizontalDivider()

            SettingsRow(
                icon = Icons.Filled.Extension,
                title = "Extensions",
                subtitle = "Manage installed anime source extensions",
                onClick = { navController.navigate(Dest.EXTENSIONS) },
            )
            SettingsRow(
                icon = Icons.Filled.Hub,
                title = "Addons",
                subtitle = "Manage Stremio/Nuvio-protocol addons",
                onClick = { navController.navigate(Dest.ADDONS) },
            )
        }
    }
}

@Composable
private fun SettingsRow(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Column(modifier = Modifier.padding(start = 16.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = AnisuTextSecondary)
            }
        }
        Icon(Icons.Filled.ChevronRight, contentDescription = null)
    }
}
