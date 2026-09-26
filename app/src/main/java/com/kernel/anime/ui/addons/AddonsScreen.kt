package com.kernel.anime.ui.addons

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.kernel.anime.di.AppContainer
import com.kernel.anime.ui.theme.KernelTextSecondary
import kotlinx.coroutines.launch

@Composable
fun AddonsScreen(container: AppContainer, navController: NavHostController) {
    val addons by container.addonManager.installedAddons.collectAsStateWithLifecycle(initialValue = emptyList())
    var manifestUrl by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Addons") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, null) } },
            )
        },
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
            item {
                Text(
                    "Add any Stremio or Nuvio Streams-compatible addon by its manifest URL — both speak the same protocol.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = KernelTextSecondary,
                    modifier = Modifier.padding(16.dp),
                )
                Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextField(
                        value = manifestUrl,
                        onValueChange = { manifestUrl = it },
                        placeholder = { Text("https://.../manifest.json") },
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = {
                        scope.launch {
                            error = null
                            container.addonManager.addAddon(manifestUrl).fold(
                                onSuccess = { manifestUrl = "" },
                                onFailure = { error = it.message },
                            )
                        }
                    }) { Text("Add") }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            }

            items(addons, key = { it.id }) { addon ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column {
                        Text(addon.name, style = MaterialTheme.typography.titleSmall)
                        Text(
                            "v${addon.version} · ${addon.types.joinToString(", ").ifEmpty { "no types declared" }}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = KernelTextSecondary,
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Switch(
                            checked = addon.enabled,
                            onCheckedChange = { checked -> scope.launch { container.addonManager.setEnabled(addon.id, checked) } },
                        )
                        IconButton(onClick = { scope.launch { container.addonManager.removeAddon(addon.id) } }) {
                            Icon(Icons.Filled.Delete, contentDescription = "Remove")
                        }
                    }
                }
            }
        }
    }
}
