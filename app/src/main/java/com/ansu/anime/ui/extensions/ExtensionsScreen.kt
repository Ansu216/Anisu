@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.extensions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.ansu.anime.di.AppContainer
import com.ansu.anime.extension.ExtensionRepoEntry
import com.ansu.anime.extension.InstalledExtension
import com.ansu.anime.extension.JsonUrlResult
import com.ansu.anime.extension.SourceTestResult
import com.ansu.anime.extension.TestTarget
import com.ansu.anime.extension.api.AnimeCatalogueSource
import com.ansu.anime.extension.aniyomi.AniyomiSourceAdapter
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** An extension from a repo plus the repo (index URL) it was listed in, so its APK can be located. */
private data class AvailableExtension(val indexUrl: String, val entry: ExtensionRepoEntry)

/** One line of the "Installed Sources" box, whichever kind of source it is. */
private data class InstalledRow(
    val key: String,
    val title: String,
    val subtitle: String,
    /** The logo square: an image URL, the extension's app icon, or null for a letter. */
    val icon: Any? = null,
    val error: String? = null,
    /** Null when the row has no on/off switch (an extension that failed to load). */
    val checked: Boolean? = null,
    val onCheckedChange: (Boolean) -> Unit = {},
    val onRemove: (() -> Unit)? = null,
    /** Opens the extension's own settings; null when the source declares none. */
    val onSettings: (() -> Unit)? = null,
)

@Composable
fun ExtensionsScreen(container: AppContainer, navController: NavHostController) {
    val manager = container.extensionManager
    val repo = container.extensionRepo
    val scope = rememberCoroutineScope()
    val packageManager = LocalContext.current.packageManager

    val extensions by manager.extensions.collectAsStateWithLifecycle()
    val disabledIds by manager.disabledSourceIds.collectAsStateWithLifecycle()

    var jsonUrl by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    var addFailed by remember { mutableStateOf(false) }

    var repoUrls by remember { mutableStateOf(repo.savedRepoUrls()) }
    var availableByRepo by remember { mutableStateOf<Map<String, List<ExtensionRepoEntry>>>(emptyMap()) }
    var showRepos by remember { mutableStateOf(false) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installingPkgs by remember { mutableStateOf<Set<String>>(emptySet()) }
    var extensionQuery by remember { mutableStateOf("") }

    var settingsFor by remember { mutableStateOf<AniyomiSourceAdapter?>(null) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<SourceTestResult?>(null) }

    // Re-read every saved repo when the screen opens, so "Available Sources" survives a restart.
    LaunchedEffect(repoUrls) {
        val loaded = mutableMapOf<String, List<ExtensionRepoEntry>>()
        for (url in repoUrls) {
            val parsed = repo.resolve(url).getOrNull()
            if (parsed is JsonUrlResult.Repo) loaded[parsed.indexUrl] = parsed.entries
        }
        availableByRepo = loaded
    }

    // Coming back from Android's installer: pick up the extension that was just installed or removed.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { withContext(Dispatchers.IO) { manager.reloadAll() } }
    }

    val available = availableByRepo.flatMap { (indexUrl, entries) -> entries.map { AvailableExtension(indexUrl, it) } }
        .distinctBy { it.entry.pkg }
    // The search box filters the catalogue by extension name, source name, package or language,
    // so a long repo can be browsed by typing instead of scrolling.
    val query = extensionQuery.trim()
    val filteredAvailable = if (query.isEmpty()) available else available.filter { item ->
        item.entry.name.contains(query, ignoreCase = true) ||
            item.entry.pkg.contains(query, ignoreCase = true) ||
            item.entry.lang.contains(query, ignoreCase = true) ||
            item.entry.sources.any { it.name.contains(query, ignoreCase = true) }
    }
    val installedVersions = extensions.associate { it.packageName to it.versionCode }

    val extensionSources = extensions.flatMap { it.sources }
    val builtInSources = manager.allSourcesIncludingDisabled().filter { built -> extensionSources.none { it.id == built.id } }

    val installedRows = buildList {
        builtInSources.forEach { add(sourceRow(it, "Built-in", null, disabledIds, manager::setSourceEnabled, null)) }
        extensions.forEach { ext ->
            val icon = ext.icon ?: runCatching { packageManager.getApplicationIcon(ext.packageName) }.getOrNull()
            addAll(extensionRows(ext, icon, disabledIds, manager::setSourceEnabled, onSettings = { settingsFor = it }) { manager.uninstall(ext.packageName) })
        }
    }

    val targets: List<TestTarget> =
        (builtInSources + extensionSources).map { TestTarget.Extension(it) }
    val selected = targets.firstOrNull { it.key == selectedKey }

    settingsFor?.let { adapter -> ExtensionSettingsSheet(source = adapter, onDismiss = { settingsFor = null }) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Extensions") },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // --- Add Extension JSON URL ---
            SectionTitle("Add Extension JSON URL")
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = jsonUrl,
                    onValueChange = { jsonUrl = it },
                    placeholder = { Text("https://…/index.min.json") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.weight(1f),
                )
                Button(
                    enabled = !isAdding && jsonUrl.isNotBlank(),
                    onClick = {
                        scope.launch {
                            isAdding = true
                            addMessage = null
                            val outcome = repo.resolve(jsonUrl).mapCatching { parsed ->
                                when (parsed) {
                                    is JsonUrlResult.Repo -> {
                                        repo.saveRepoUrl(parsed.indexUrl)
                                        repoUrls = repo.savedRepoUrls()
                                        availableByRepo = availableByRepo + (parsed.indexUrl to parsed.entries)
                                        "Repo added: ${parsed.entries.size} extensions available"
                                    }
                                }
                            }
                            outcome.fold(
                                onSuccess = { addMessage = it; addFailed = false; jsonUrl = "" },
                                onFailure = { addMessage = it.message ?: "Could not add that URL"; addFailed = true },
                            )
                            isAdding = false
                        }
                    },
                ) {
                    if (isAdding) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Add")
                }
            }
            addMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = if (addFailed) MaterialTheme.colorScheme.error else AnsuColors.TextSecondary)
            }

            // --- Available Sources ---
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Available Sources (${filteredAvailable.size})", modifier = Modifier.weight(1f))
                TextButton(onClick = { showRepos = true }) { Text("Repos (${repoUrls.size})") }
            }
            OutlinedTextField(
                value = extensionQuery,
                onValueChange = { extensionQuery = it },
                placeholder = { Text("Search extensions by name or language") },
                singleLine = true,
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                shape = RoundedCornerShape(50),
                modifier = Modifier.fillMaxWidth(),
            )
            installError?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            ListBox {
                if (available.isEmpty()) {
                    EmptyHint("Nothing here yet. Add a repo's index.min.json above.")
                } else if (filteredAvailable.isEmpty()) {
                    EmptyHint("No extension matches \"$query\".")
                }
                filteredAvailable.forEachIndexed { index, item ->
                    if (index > 0) HorizontalDivider(color = AnsuColors.StrokeGlass)
                    val installedCode = installedVersions[item.entry.pkg]
                    val isInstalled = installedCode != null
                    val hasUpdate = installedCode != null && item.entry.code > installedCode
                    val isInstalling = item.entry.pkg in installingPkgs
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconSquare(model = repo.iconUrl(item.indexUrl, item.entry), fallback = item.entry.name)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(item.entry.name.removePrefix("Aniyomi: "), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOf(item.entry.lang, "v${item.entry.version}")
                                    .filter { !it.isNullOrBlank() && it != "v" }.joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall,
                                color = AnsuColors.TextSecondary,
                            )
                        }
                        OutlinedButton(
                            enabled = (!isInstalled || hasUpdate) && !isInstalling,
                            onClick = {
                                scope.launch {
                                    installError = null
                                    installingPkgs = installingPkgs + item.entry.pkg
                                    // Download, then store inside Ansu: no Android installer, nothing lands on the phone.
                                    repo.downloadApk(item.indexUrl, item.entry)
                                        .mapCatching { apk -> withContext(Dispatchers.IO) { manager.installPrivate(apk).getOrThrow() } }
                                        .onFailure { installError = it.message ?: "Install failed" }
                                    installingPkgs = installingPkgs - item.entry.pkg
                                }
                            },
                        ) {
                            if (isInstalling) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            else Text(if (hasUpdate) "Update" else if (isInstalled) "Installed" else "Install")
                        }
                    }
                }
            }

            // --- Installed Sources ---
            Row(modifier = Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                SectionTitle("Installed Sources (${installedRows.size})", modifier = Modifier.weight(1f))
                TextButton(onClick = { scope.launch { withContext(Dispatchers.IO) { manager.reloadAll() } } }) { Text("Rescan") }
            }
            ListBox {
                if (installedRows.isEmpty()) EmptyHint("No sources installed.")
                installedRows.forEachIndexed { index, row ->
                    if (index > 0) HorizontalDivider(color = AnsuColors.StrokeGlass)
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconSquare(model = row.icon, fallback = row.title)
                        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                row.error ?: row.subtitle,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (row.error != null) MaterialTheme.colorScheme.error else AnsuColors.TextSecondary,
                            )
                        }
                        if (row.checked != null) {
                            Switch(checked = row.checked, onCheckedChange = row.onCheckedChange)
                        }
                        row.onSettings?.let { openSettings ->
                            IconButton(onClick = openSettings) { Icon(Icons.Filled.Settings, contentDescription = "Settings for ${row.title}") }
                        }
                        row.onRemove?.let { remove ->
                            IconButton(onClick = remove) { Icon(Icons.Filled.Delete, contentDescription = "Remove ${row.title}") }
                        }
                    }
                }
            }

            // --- Test Source ---
            SectionTitle("Test Source", modifier = Modifier.padding(top = 8.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                var menuOpen by remember { mutableStateOf(false) }
                Box(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clip(RoundedCornerShape(50))
                            .border(BorderStroke(1.dp, AnsuColors.StrokeGlass), RoundedCornerShape(50))
                            .clickable { menuOpen = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            selected?.label ?: "Choose from installed Sources",
                            modifier = Modifier.weight(1f),
                            color = if (selected == null) AnsuColors.TextTertiary else AnsuColors.TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (targets.isEmpty()) DropdownMenuItem(text = { Text("No installed sources") }, onClick = { menuOpen = false })
                        targets.forEach { target ->
                            DropdownMenuItem(
                                text = { Text(target.label) },
                                onClick = { selectedKey = target.key; result = null; menuOpen = false },
                            )
                        }
                    }
                }
                Button(
                    enabled = selected != null && !testing,
                    onClick = {
                        val target = selected ?: return@Button
                        scope.launch {
                            testing = true
                            result = container.sourceTester.test(target)
                            testing = false
                        }
                    },
                ) {
                    if (testing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Test")
                }
            }
            TestResultBox(result = result, testing = testing)
            Spacer(Modifier.size(24.dp))
        }
    }

    if (showRepos) {
        AlertDialog(
            onDismissRequest = { showRepos = false },
            confirmButton = { TextButton(onClick = { showRepos = false }) { Text("Done") } },
            title = { Text("Added repos") },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (repoUrls.isEmpty()) Text("No repos added yet.", color = AnsuColors.TextSecondary)
                    repoUrls.forEach { url ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(url, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            IconButton(onClick = {
                                repo.removeRepoUrl(url)
                                repoUrls = repo.savedRepoUrls()
                            }) { Icon(Icons.Filled.Delete, contentDescription = "Remove repo") }
                        }
                    }
                }
            },
        )
    }
}

private fun sourceRow(
    source: AnimeCatalogueSource,
    kind: String,
    icon: Any?,
    disabledIds: Set<Long>,
    setEnabled: (Long, Boolean) -> Unit,
    onRemove: (() -> Unit)?,
    onSettings: (() -> Unit)? = null,
) = InstalledRow(
    key = "src:${source.id}",
    title = source.name,
    subtitle = listOf(kind, source.lang.ifBlank { null }).filterNotNull().joinToString(" · "),
    icon = icon,
    checked = source.id !in disabledIds,
    onCheckedChange = { setEnabled(source.id, it) },
    onRemove = onRemove,
    onSettings = onSettings,
)

private fun extensionRows(
    ext: InstalledExtension,
    icon: Any?,
    disabledIds: Set<Long>,
    setEnabled: (Long, Boolean) -> Unit,
    onSettings: (AniyomiSourceAdapter) -> Unit,
    onRemove: () -> Unit,
): List<InstalledRow> {
    if (ext.sources.isEmpty()) {
        return listOf(
            InstalledRow(
                key = "ext:${ext.packageName}",
                title = ext.displayName,
                subtitle = "v${ext.versionName}",
                icon = icon,
                error = ext.loadError ?: "No sources found",
                onRemove = onRemove,
            ),
        )
    }
    return ext.sources.mapIndexed { index, source ->
        val settings: (() -> Unit)? = (source as? AniyomiSourceAdapter)?.takeIf { it.hasSettings }?.let { adapter -> { onSettings(adapter) } }
        sourceRow(source, "v${ext.versionName}", icon, disabledIds, setEnabled, if (index == 0) onRemove else null, settings)
    }
}

@Composable
private fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = modifier)
}

@Composable
private fun EmptyHint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary, modifier = Modifier.padding(16.dp))
}

/** The rounded, scrollable frame the sketch draws around each list. */
@Composable
private fun ListBox(content: @Composable () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier.fillMaxWidth()
            .heightIn(max = 320.dp)
            .clip(shape)
            .background(AnsuColors.BackgroundElevated)
            .border(BorderStroke(1.dp, AnsuColors.StrokeGlass), shape)
            .verticalScroll(rememberScrollState()),
    ) { content() }
}

@Composable
private fun IconSquare(model: Any?, fallback: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier.size(40.dp).clip(shape).background(AnsuColors.SurfaceGlassBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(fallback.removePrefix("Aniyomi: ").firstOrNull()?.uppercase() ?: "?", color = AnsuColors.TextSecondary)
        if (model != null) AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

@Composable
private fun TestResultBox(result: SourceTestResult?, testing: Boolean) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier.fillMaxWidth().clip(shape).background(AnsuColors.BackgroundElevated)
            .border(BorderStroke(1.dp, AnsuColors.StrokeGlass), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Test Result", style = MaterialTheme.typography.titleSmall)
        when {
            testing -> Text("Testing…", color = AnsuColors.TextSecondary)
            result == null -> Text("Pick a source and press Test to see ping, latency and what it returns.", color = AnsuColors.TextSecondary)
            else -> {
                Text(
                    if (result.ok) "${result.target}: working" else "${result.target}: failed",
                    color = if (result.ok) AnsuColors.ScoreGreen else AnsuColors.Error,
                    style = MaterialTheme.typography.titleSmall,
                )
                ResultLine("Ping", result.pingMs?.let { "$it ms" + (result.httpStatus?.let { s -> " (HTTP $s)" } ?: "") } ?: "unreachable")
                ResultLine("Latency", result.latencyMs?.let { "$it ms" } ?: "—")
                ResultLine("Results", result.itemCount?.toString() ?: "—")
                result.sampleTitle?.let { ResultLine("Sample", it) }
                result.streamNote?.let { ResultLine("Streams", it) }
                result.error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        }
    }
}

@Composable
private fun ResultLine(label: String, value: String) {
    Row {
        Text(label, modifier = Modifier.width(80.dp), color = AnsuColors.TextSecondary, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
