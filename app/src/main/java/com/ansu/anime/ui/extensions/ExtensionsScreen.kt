package com.ansu.anime.ui.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.di.AppContainer
import com.ansu.anime.extension.ExtensionRepoEntry
import com.ansu.anime.extension.JsonUrlResult
import com.ansu.anime.extension.SourceTestResult
import com.ansu.anime.extension.TestTarget
import com.ansu.anime.extension.aniyomi.AniyomiSourceAdapter
import com.ansu.anime.ui.components.backdropBlurSupported
import com.ansu.anime.ui.components.backdropSource
import com.ansu.anime.ui.components.frostedBackdrop
import com.ansu.anime.ui.components.rememberBackdropState
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extensions: a frosted Available / Installed bar floats at the bottom with a circle beside it. The circle
 * adds a repo (a card rises from under the screen), or, on Installed when something is outdated, updates
 * sources. Installed rows have a Test button, and holding one floats it over a live blur with Configuration,
 * Enable / Disable and Delete.
 */
@Composable
fun ExtensionsScreen(container: AppContainer, navController: NavHostController) {
    val manager = container.extensionManager
    val repo = container.extensionRepo
    val scope = rememberCoroutineScope()
    val packageManager = LocalContext.current.packageManager
    val density = LocalDensity.current

    val extensions by manager.extensions.collectAsStateWithLifecycle()
    val disabledIds by manager.disabledSourceIds.collectAsStateWithLifecycle()

    val backdrop = rememberBackdropState()
    var tab by rememberSaveable { mutableStateOf(ExtensionsTab.Available) }
    var sheet by remember { mutableStateOf<ExtensionSheet?>(null) }
    var shownSheet by remember { mutableStateOf<ExtensionSheet?>(null) }
    val sheetProgress = remember { Animatable(0f) }

    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    var jsonUrl by remember { mutableStateOf("") }
    var isAdding by remember { mutableStateOf(false) }
    var addMessage by remember { mutableStateOf<String?>(null) }
    var addFailed by remember { mutableStateOf(false) }

    var repoUrls by remember { mutableStateOf(repo.savedRepoUrls()) }
    var disabledRepos by remember { mutableStateOf(repo.disabledRepoUrls()) }
    var availableByRepo by remember { mutableStateOf<Map<String, List<ExtensionRepoEntry>>>(emptyMap()) }
    var installError by remember { mutableStateOf<String?>(null) }
    var installingPkgs by remember { mutableStateOf<Set<String>>(emptySet()) }

    var testingKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var results by remember { mutableStateOf<Map<String, SourceTestResult>>(emptyMap()) }

    var actionTarget by remember { mutableStateOf<ExtensionActionTarget?>(null) }
    var settingsFor by remember { mutableStateOf<AniyomiSourceAdapter?>(null) }

    // Re-read every saved repo, so the Available list survives a restart. An index is fetched once and kept
    // while its repo is only switched off, so flipping the switch back is instant.
    LaunchedEffect(repoUrls) {
        val loaded = availableByRepo.filterKeys { it in repoUrls }.toMutableMap()
        for (url in repoUrls) {
            if (url in loaded) continue
            val parsed = repo.resolve(url).getOrNull()
            if (parsed is JsonUrlResult.Repo) loaded[parsed.indexUrl] = parsed.entries
        }
        availableByRepo = loaded
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { withContext(Dispatchers.IO) { manager.reloadAll() } }
    }

    val available = availableByRepo
        .filterKeys { it !in disabledRepos }
        .flatMap { (indexUrl, entries) -> entries.map { AvailableExtension(indexUrl, it) } }
        .sortedByDescending { it.entry.code }
        .distinctBy { it.entry.pkg }
    val installedCodes = extensions.associate { it.packageName to it.versionCode }

    val extensionSources = extensions.flatMap { it.sources }
    val builtInSources = manager.allSourcesIncludingDisabled().filter { built -> extensionSources.none { it.id == built.id } }

    val updates = extensions.mapNotNull { ext ->
        val newer = available.firstOrNull { it.entry.pkg == ext.packageName && it.entry.code > ext.versionCode } ?: return@mapNotNull null
        UpdateItem(
            packageName = ext.packageName,
            name = ext.displayName,
            icon = ext.icon ?: runCatching { packageManager.getApplicationIcon(ext.packageName) }.getOrNull(),
            fromVersion = ext.versionName,
            available = newer,
        )
    }
    val updatePkgs = updates.map { it.packageName }.toSet()

    val installedRows = buildList {
        builtInSources.forEach { s ->
            add(
                InstalledSource(
                    key = "src:${s.id}", title = s.name, lang = s.lang, versionName = null, icon = null, source = s,
                    packageName = null, sourcesInPackage = 1, enabled = s.id !in disabledIds, hasUpdate = false,
                    error = null, settings = null,
                ),
            )
        }
        extensions.forEach { ext ->
            val icon = ext.icon ?: runCatching { packageManager.getApplicationIcon(ext.packageName) }.getOrNull()
            if (ext.sources.isEmpty()) {
                add(
                    InstalledSource(
                        key = "ext:${ext.packageName}", title = ext.displayName, lang = "", versionName = ext.versionName,
                        icon = icon, source = null, packageName = ext.packageName, sourcesInPackage = 0, enabled = true,
                        hasUpdate = ext.packageName in updatePkgs, error = ext.loadError ?: "No sources found", settings = null,
                    ),
                )
            } else {
                ext.sources.forEach { s ->
                    add(
                        InstalledSource(
                            key = "src:${s.id}", title = s.name, lang = s.lang, versionName = ext.versionName, icon = icon,
                            source = s, packageName = ext.packageName, sourcesInPackage = ext.sources.size,
                            enabled = s.id !in disabledIds, hasUpdate = ext.packageName in updatePkgs, error = null,
                            settings = (s as? AniyomiSourceAdapter)?.takeIf { it.hasSettings },
                        ),
                    )
                }
            }
        }
    }

    val q = query.trim()
    val shownAvailable = if (q.isEmpty()) available else available.filter { item ->
        item.entry.name.contains(q, true) || item.entry.pkg.contains(q, true) || item.entry.lang.contains(q, true) ||
            item.entry.sources.any { it.name.contains(q, true) }
    }
    val shownInstalled = if (q.isEmpty()) installedRows else installedRows.filter {
        it.title.contains(q, true) || it.lang.contains(q, true)
    }

    // Download, then store inside Anisu: no Android installer, nothing lands on the phone.
    suspend fun installOne(item: AvailableExtension) {
        installingPkgs = installingPkgs + item.entry.pkg
        repo.downloadApk(item.indexUrl, item.entry)
            .mapCatching { apk -> withContext(Dispatchers.IO) { manager.installPrivate(apk).getOrThrow() } }
            .onFailure { installError = it.message ?: "Install failed" }
        installingPkgs = installingPkgs - item.entry.pkg
    }

    val action = when {
        sheet != null -> ExtensionsAction.Close
        tab == ExtensionsTab.Installed && updates.isNotEmpty() -> ExtensionsAction.Download
        else -> ExtensionsAction.Add
    }

    LaunchedEffect(sheet) {
        if (sheet != null) {
            shownSheet = sheet
            sheetProgress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 380f))
        } else {
            sheetProgress.animateTo(0f, tween(260, easing = FastOutLinearInEasing))
        }
    }
    // Nothing left to update: the card has done its job.
    LaunchedEffect(updates.isEmpty(), sheet) {
        if (sheet == ExtensionSheet.Update && updates.isEmpty()) sheet = null
    }
    BackHandler(enabled = sheet != null && actionTarget == null) { sheet = null }

    settingsFor?.let { adapter -> ExtensionSettingsSheet(source = adapter, onDismiss = { settingsFor = null }) }

    val barBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val availableState = rememberLazyListState()
    val installedState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize().background(AnsuColors.Background)) {
        // ---------------- Content (what the bar and the cards blur) ----------------
        Column(
            modifier = Modifier
                .fillMaxSize()
                .backdropSource(backdrop)
                .background(AnsuColors.Background)
                .statusBarsPadding(),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp, top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                Text(
                    "Extensions",
                    style = MaterialTheme.typography.titleLarge,
                    color = AnsuColors.TextPrimary,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                IconButton(onClick = { searching = !searching; if (!searching) query = "" }) {
                    Icon(Icons.Filled.Search, "Search")
                }
            }
            AnimatedVisibility(
                visible = searching,
                enter = fadeIn(tween(160)) + expandVertically(tween(220)),
                exit = fadeOut(tween(120)) + shrinkVertically(tween(180)),
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search by name or language") },
                    singleLine = true,
                    shape = RoundedCornerShape(50),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }

            val listPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = barBottom + ExtensionsBarHeight + 40.dp)
            Crossfade(targetState = tab, animationSpec = tween(220), modifier = Modifier.weight(1f), label = "extensionsTab") { current ->
                when (current) {
                    ExtensionsTab.Available -> LazyColumn(
                        state = availableState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listPadding,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        installError?.let { message ->
                            item(key = "install-error") { Text(message, color = AnsuColors.Error, style = MaterialTheme.typography.bodySmall) }
                        }
                        if (shownAvailable.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (available.isEmpty()) "Nothing here yet. Tap + to add a repo." else "No extension matches \"$q\".",
                                    color = AnsuColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        items(shownAvailable, key = { it.entry.pkg }) { item ->
                            val installedCode = installedCodes[item.entry.pkg]
                            val isInstalled = installedCode != null
                            val hasUpdate = installedCode != null && item.entry.code > installedCode
                            ExtensionCard(
                                icon = repo.iconUrl(item.indexUrl, item.entry),
                                title = item.entry.name,
                                subtitle = plainLine(item.entry.version.takeIf { it.isNotBlank() }?.let { "v$it" }, item.entry.lang),
                                trailing = {
                                    OutlineAction(
                                        text = if (hasUpdate) "Update" else if (isInstalled) "Installed" else "Install",
                                        enabled = !isInstalled || hasUpdate,
                                        loading = item.entry.pkg in installingPkgs,
                                        onClick = { scope.launch { installError = null; installOne(item) } },
                                    )
                                },
                            )
                        }
                    }

                    ExtensionsTab.Installed -> LazyColumn(
                        state = installedState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = listPadding,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (shownInstalled.isEmpty()) {
                            item(key = "empty") {
                                Text(
                                    if (installedRows.isEmpty()) "No sources installed." else "No source matches \"$q\".",
                                    color = AnsuColors.TextSecondary,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.padding(vertical = 24.dp),
                                )
                            }
                        }
                        items(shownInstalled, key = { it.key }) { row ->
                            val holder = remember { arrayOf(Rect.Zero) }
                            val result = results[row.key]
                            val line = result?.let {
                                val text = if (it.ok) {
                                    listOfNotNull(
                                        "Working",
                                        it.pingMs?.let { ms -> "ping $ms ms" },
                                        it.latencyMs?.let { ms -> "$ms ms" },
                                        it.itemCount?.let { n -> "$n results" },
                                    ).joinToString(" · ")
                                } else {
                                    "Failed" + (it.error?.let { e -> " · $e" } ?: "")
                                }
                                text to it.ok
                            }
                            InstalledSourceCard(
                                row = row,
                                testing = row.key in testingKeys,
                                resultLine = line,
                                onTest = {
                                    val source = row.source
                                    if (source != null) {
                                        scope.launch {
                                            testingKeys = testingKeys + row.key
                                            val r = container.sourceTester.test(TestTarget.Extension(source))
                                            results = results + (row.key to r)
                                            testingKeys = testingKeys - row.key
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .onGloballyPositioned { holder[0] = it.boundsInWindow() }
                                    .pointerInput(row.key) {
                                        detectTapGestures(onLongPress = {
                                            actionTarget = ExtensionActionTarget(row, holder[0])
                                        })
                                    },
                            )
                        }
                    }
                }
            }
        }

        // ---------------- Frosted scrim behind the card ----------------
        if (sheet != null || sheetProgress.isRunning) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = sheetProgress.value.coerceIn(0f, 1f) }
                    .frostedBackdrop(backdrop, enabled = true, shape = RectangleShape, radius = 26.dp)
                    .background(Color.Black.copy(alpha = if (backdropBlurSupported) 0.42f else 0.82f))
                    .pointerInput(Unit) { detectTapGestures { sheet = null } },
            )
        }

        // ---------------- The card that rises from under the screen ----------------
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val containerHeightPx = with(density) { maxHeight.toPx() }
            val card = shownSheet
            if (card != null && (sheet != null || sheetProgress.isRunning)) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .navigationBarsPadding()
                        .padding(start = 16.dp, end = 16.dp, bottom = ExtensionsBarHeight + 8.dp + 16.dp)
                        .imePadding()
                        .graphicsLayer {
                            translationY = (1f - sheetProgress.value) * containerHeightPx
                            alpha = (sheetProgress.value * 3f).coerceIn(0f, 1f)
                        },
                ) {
                    BottomCard(modifier = Modifier.heightIn(max = maxHeight * 0.68f)) {
                        when (card) {
                            ExtensionSheet.Add -> AddExtensionCardContent(
                                url = jsonUrl,
                                onUrlChange = { jsonUrl = it },
                                adding = isAdding,
                                message = addMessage,
                                failed = addFailed,
                                onAdd = {
                                    scope.launch {
                                        isAdding = true
                                        addMessage = null
                                        val outcome = repo.resolve(jsonUrl).mapCatching { parsed ->
                                            when (parsed) {
                                                is JsonUrlResult.Repo -> {
                                                    repo.saveRepoUrl(parsed.indexUrl)
                                                    repo.setRepoEnabled(parsed.indexUrl, true)
                                                    disabledRepos = repo.disabledRepoUrls()
                                                    availableByRepo = availableByRepo + (parsed.indexUrl to parsed.entries)
                                                    repoUrls = repo.savedRepoUrls()
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
                                repoUrls = repoUrls,
                                disabledRepos = disabledRepos,
                                onRepoEnabled = { url, on ->
                                    repo.setRepoEnabled(url, on)
                                    disabledRepos = repo.disabledRepoUrls()
                                },
                                onRepoRemove = { url ->
                                    repo.removeRepoUrl(url)
                                    repoUrls = repo.savedRepoUrls()
                                    disabledRepos = repo.disabledRepoUrls()
                                },
                                onDone = { sheet = null },
                            )

                            ExtensionSheet.Update -> UpdateCardContent(
                                updates = updates,
                                updatingPkgs = installingPkgs,
                                onUpdate = { item -> scope.launch { installError = null; installOne(item.available) } },
                                onUpdateAll = {
                                    val queue = updates.map { it.available }
                                    scope.launch { queue.forEach { installOne(it) } }
                                },
                                onNotNow = { sheet = null },
                            )
                        }
                    }
                }
            }
        }

        // ---------------- Floating bar + circle ----------------
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ExtensionsTabBar(tab = tab, onTabSelected = { tab = it; query = ""; searching = false }, backdrop = backdrop)
            ExtensionsActionButton(
                action = action,
                backdrop = backdrop,
                onClick = {
                    sheet = when (action) {
                        ExtensionsAction.Close -> null
                        ExtensionsAction.Download -> ExtensionSheet.Update
                        ExtensionsAction.Add -> ExtensionSheet.Add
                    }
                },
            )
        }

        // ---------------- Long-press menu for an installed row ----------------
        actionTarget?.let { target ->
            key(target.row.key) {
                ExtensionActionOverlay(
                    target = target,
                    backdrop = backdrop,
                    onConfigure = { row -> settingsFor = row.settings },
                    onToggleEnabled = { row -> row.source?.let { manager.setSourceEnabled(it.id, !row.enabled) } },
                    onDelete = { row ->
                        row.packageName?.let { pkg -> scope.launch(Dispatchers.IO) { manager.uninstall(pkg) } }
                    },
                    onFinished = { actionTarget = null },
                )
            }
        }
    }
}
