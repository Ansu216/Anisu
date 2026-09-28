@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.extensions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.di.AppContainer
import com.ansu.anime.extension.ExtensionRepoEntry
import com.ansu.anime.extension.InstalledExtension
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.launch

@Composable
fun ExtensionsScreen(container: AppContainer, navController: NavHostController) {
    val installed by container.extensionManager.extensions.collectAsStateWithLifecycle()
    var repoUrl by remember { mutableStateOf("") }
    var repoEntries by remember { mutableStateOf<List<ExtensionRepoEntry>>(emptyList()) }
    var repoError by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Installed",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                )
            }
            if (installed.isEmpty()) {
                item { Text("Only the built-in demo source is active. Install an extension APK to add real sources.", modifier = Modifier.padding(16.dp), color = AnsuColors.TextSecondary) }
            }
            items(installed) { ext -> InstalledExtensionRow(ext) }

            item {
                Button(
                    onClick = { container.extensionManager.reloadAll() },
                    modifier = Modifier.padding(16.dp),
                ) { Text("Rescan installed extensions") }
            }

            item { HorizontalDivider() }

            item {
                Text(
                    "Browse a repo",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp),
                )
                Text(
                    "Paste a Keiyoushi-style repo's index.min.json URL to see what it offers.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = repoUrl,
                        onValueChange = { repoUrl = it },
                        placeholder = { Text("https://.../index.min.json") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            repoError = null
                            container.extensionRepo.fetchIndex(repoUrl).fold(
                                onSuccess = { repoEntries = it },
                                onFailure = { repoError = it.message },
                            )
                        }
                    }) { Text("Load") }
                }
                repoError?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp)) }
            }

            items(repoEntries) { entry ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(entry.name, style = MaterialTheme.typography.titleSmall)
                        Text("${entry.lang} · v${entry.version}", style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
                    }
                    TextButton(onClick = {
                        scope.launch {
                            val base = repoUrl.substringBeforeLast('/')
                            container.extensionRepo.downloadAndInstall(base, entry)
                        }
                    }) { Text("Install") }
                }
            }
        }
    }
}

@Composable
private fun InstalledExtensionRow(ext: InstalledExtension) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (ext.isValid) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = if (ext.isValid) AnsuColors.Accent else MaterialTheme.colorScheme.error,
        )
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(ext.displayName, style = MaterialTheme.typography.titleSmall)
            Text(
                ext.loadError ?: "v${ext.versionName} · ${ext.sources.size} source(s)",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )
        }
    }
}
