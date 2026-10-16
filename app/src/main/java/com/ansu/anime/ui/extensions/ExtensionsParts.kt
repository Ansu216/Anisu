package com.ansu.anime.ui.extensions

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import coil.compose.AsyncImage
import com.ansu.anime.extension.ExtensionRepoEntry
import com.ansu.anime.extension.api.AnimeCatalogueSource
import com.ansu.anime.extension.aniyomi.AniyomiSourceAdapter
import com.ansu.anime.ui.components.BackdropState
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.backdropBlurSupported
import com.ansu.anime.ui.components.frostedBackdrop
import com.ansu.anime.ui.theme.AnsuColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

// ---------------------------------------------------------------------------------------------
// Models
// ---------------------------------------------------------------------------------------------

/** An extension from a repo plus the repo (index URL) it was listed in, so its APK can be located. */
internal data class AvailableExtension(val indexUrl: String, val entry: ExtensionRepoEntry)

/** An installed extension that has a newer build in one of the enabled repos. */
internal data class UpdateItem(
    val packageName: String,
    val name: String,
    val icon: Any?,
    val fromVersion: String,
    val available: AvailableExtension,
)

/** One line of the Installed tab: a single source, whichever kind it is. */
internal data class InstalledSource(
    val key: String,
    val title: String,
    val lang: String,
    val versionName: String?,
    val icon: Any?,
    val source: AnimeCatalogueSource?,
    /** Null for a built-in source, which cannot be deleted. */
    val packageName: String?,
    val sourcesInPackage: Int,
    val enabled: Boolean,
    val hasUpdate: Boolean,
    val error: String?,
    val settings: AniyomiSourceAdapter?,
)

/** The row that was held, plus where it sat on screen (window coordinates) when it was held. */
internal data class ExtensionActionTarget(val row: InstalledSource, val bounds: Rect)

internal enum class ExtensionSheet { Add, Update }

// ---------------------------------------------------------------------------------------------
// Small pieces
// ---------------------------------------------------------------------------------------------

private val CardShape = RoundedCornerShape(18.dp)

/** The outlined rounded-rectangle button from the sketches (Install, Update, Test, Add, Done ...). */
@Composable
internal fun OutlineAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    minWidth: androidx.compose.ui.unit.Dp = 78.dp,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = modifier
            .widthIn(min = minWidth)
            .height(40.dp)
            .clip(shape)
            .border(BorderStroke(1.dp, if (enabled) Color.White.copy(alpha = 0.28f) else AnsuColors.StrokeGlass), shape)
            .clickable(enabled = enabled && !loading, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = AnsuColors.Accent)
        } else {
            Text(
                text,
                style = MaterialTheme.typography.labelLarge,
                color = if (enabled) AnsuColors.TextPrimary else AnsuColors.TextTertiary,
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun IconSquare(model: Any?, fallback: String) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier.size(44.dp).clip(shape).border(BorderStroke(1.dp, AnsuColors.StrokeGlass), shape)
            .background(AnsuColors.SurfaceGlassBase),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            fallback.removePrefix("Aniyomi: ").firstOrNull()?.uppercase() ?: "?",
            color = AnsuColors.TextSecondary,
            style = MaterialTheme.typography.titleMedium,
        )
        if (model != null) AsyncImage(model = model, contentDescription = null, modifier = Modifier.fillMaxSize())
    }
}

/** A bordered row card: logo square, two text lines (plus optional extra) and a trailing slot. */
@Composable
internal fun ExtensionCard(
    icon: Any?,
    title: String,
    subtitle: androidx.compose.ui.text.AnnotatedString,
    modifier: Modifier = Modifier,
    extra: (@Composable () -> Unit)? = null,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(AnsuColors.BackgroundElevated)
            .border(BorderStroke(1.dp, AnsuColors.StrokeGlass), CardShape)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconSquare(model = icon, fallback = title)
        Column(modifier = Modifier.weight(1f).padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                title.removePrefix("Aniyomi: "),
                style = MaterialTheme.typography.titleSmall,
                color = AnsuColors.TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AnsuColors.TextSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            extra?.invoke()
        }
        trailing()
    }
}

internal fun plainLine(vararg parts: String?): androidx.compose.ui.text.AnnotatedString =
    buildAnnotatedString { append(parts.filter { !it.isNullOrBlank() }.joinToString(" · ")) }

/** "v17.16 · all · Update available" with the update note in the accent colour. */
internal fun installedLine(row: InstalledSource): androidx.compose.ui.text.AnnotatedString = buildAnnotatedString {
    if (row.error != null) {
        withStyle(SpanStyle(color = AnsuColors.Error)) { append(row.error) }
        return@buildAnnotatedString
    }
    append(listOfNotNull(row.versionName?.let { "v$it" } ?: "Built-in", row.lang.ifBlank { null }).joinToString(" · "))
    if (row.hasUpdate) {
        append(" · ")
        withStyle(SpanStyle(color = AnsuColors.Accent)) { append("Update available") }
    }
    if (!row.enabled) {
        append(" · ")
        withStyle(SpanStyle(color = AnsuColors.TextTertiary)) { append("Disabled") }
    }
}

/** An installed source's card, with its Test button and (once tested) a one-line result. */
@Composable
internal fun InstalledSourceCard(
    row: InstalledSource,
    testing: Boolean,
    resultLine: Pair<String, Boolean>?,
    onTest: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ExtensionCard(
        icon = row.icon,
        title = row.title,
        subtitle = installedLine(row),
        modifier = modifier.graphicsLayer { alpha = if (row.enabled) 1f else 0.6f },
        extra = resultLine?.let { (text, ok) ->
            {
                Text(
                    text,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (ok) AnsuColors.ScoreGreen else AnsuColors.Error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailing = {
            if (row.source != null) OutlineAction("Test", onClick = onTest, loading = testing)
        },
    )
}

// ---------------------------------------------------------------------------------------------
// The two cards that rise from the bottom
// ---------------------------------------------------------------------------------------------

/** The frosted card shell shared by "Add extension" and "Update sources". */
@Composable
internal fun BottomCard(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    FrostedGlassCard(
        modifier = modifier
            .fillMaxWidth()
            // Swallow taps so touching the card never reaches the dimmed backdrop behind it.
            .pointerInput(Unit) { detectTapGestures { } },
        shape = RoundedCornerShape(28.dp),
        tintAlpha = 0.74f,
    ) {
        Column(modifier = Modifier.padding(20.dp)) { content() }
    }
}

@Composable
internal fun AddExtensionCardContent(
    url: String,
    onUrlChange: (String) -> Unit,
    adding: Boolean,
    message: String?,
    failed: Boolean,
    onAdd: () -> Unit,
    repoUrls: List<String>,
    disabledRepos: Set<String>,
    onRepoEnabled: (String, Boolean) -> Unit,
    onRepoRemove: (String) -> Unit,
    onDone: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Add extension", style = MaterialTheme.typography.titleLarge, color = AnsuColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedTextField(
                value = url,
                onValueChange = onUrlChange,
                placeholder = { Text("https://…/index.min.json") },
                singleLine = true,
                shape = RoundedCornerShape(50),
                modifier = Modifier.weight(1f),
            )
            OutlineAction("Add", onClick = onAdd, enabled = url.isNotBlank(), loading = adding, minWidth = 64.dp)
        }
        Text(
            message ?: "Make sure you trust the repo.",
            style = MaterialTheme.typography.bodySmall,
            color = if (failed) AnsuColors.Error else AnsuColors.TextSecondary,
            modifier = Modifier.padding(start = 6.dp, top = 8.dp),
        )
        Spacer(Modifier.height(18.dp))
        Text("Your extensions", style = MaterialTheme.typography.labelLarge, color = AnsuColors.TextSecondary, modifier = Modifier.padding(start = 6.dp))
        Spacer(Modifier.height(8.dp))
        val shape = RoundedCornerShape(20.dp)
        Column(
            modifier = Modifier.fillMaxWidth().clip(shape).border(BorderStroke(1.dp, AnsuColors.StrokeGlass), shape),
        ) {
            if (repoUrls.isEmpty()) {
                Text("No repos added yet.", color = AnsuColors.TextSecondary, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(16.dp))
            }
            repoUrls.forEachIndexed { index, repoUrl ->
                if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AnsuColors.StrokeGlass))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        repoUrl,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AnsuColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Switch(
                        checked = repoUrl !in disabledRepos,
                        onCheckedChange = { onRepoEnabled(repoUrl, it) },
                        modifier = Modifier.padding(horizontal = 6.dp),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = Color(0xFF2F6BFF),
                        ),
                    )
                    Box(
                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).clickable { onRepoRemove(repoUrl) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Delete, contentDescription = "Remove repo", tint = AnsuColors.TextSecondary, modifier = Modifier.size(22.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            OutlineAction("Done", onClick = onDone, minWidth = 96.dp)
        }
    }
}

@Composable
internal fun UpdateCardContent(
    updates: List<UpdateItem>,
    updatingPkgs: Set<String>,
    onUpdate: (UpdateItem) -> Unit,
    onUpdateAll: () -> Unit,
    onNotNow: () -> Unit,
) {
    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
        Text("Update sources", style = MaterialTheme.typography.titleLarge, color = AnsuColors.TextPrimary, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            updates.forEach { item ->
                ExtensionCard(
                    icon = item.icon,
                    title = item.name,
                    subtitle = plainLine("v${item.fromVersion} → v${item.available.entry.version}", item.available.entry.lang),
                    trailing = {
                        OutlineAction(
                            "Update",
                            onClick = { onUpdate(item) },
                            loading = item.packageName in updatingPkgs,
                            enabled = updatingPkgs.isEmpty(),
                        )
                    },
                )
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlineAction("Not now", onClick = onNotNow, modifier = Modifier.weight(1f))
            OutlineAction("Update all", onClick = onUpdateAll, enabled = updatingPkgs.isEmpty(), modifier = Modifier.weight(1f))
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Long-press overlay (the Continue Watching treatment, for a source row)
// ---------------------------------------------------------------------------------------------

private enum class OverlayPhase { Menu, Removing, Closing }

private class MenuEntry(val label: String, val icon: ImageVector, val tint: Color, val onClick: () -> Unit)

private val OverlayMenuRowHeight = 55.dp
private val OverlayGap = 16.dp
private val OverlayLift = 16.dp

/**
 * The screen frosts over (a live blur of the real list), the held row floats to the middle with a small
 * menu under it, and a tap anywhere else (or Back) floats it home. "Delete" dissolves the card, removes the
 * extension while the blur is still up, then lifts the blur so the list closes the gap.
 */
@Composable
internal fun ExtensionActionOverlay(
    target: ExtensionActionTarget,
    backdrop: BackdropState,
    onConfigure: (InstalledSource) -> Unit,
    onToggleEnabled: (InstalledSource) -> Unit,
    onDelete: (InstalledSource) -> Unit,
    onFinished: () -> Unit,
) {
    val row = target.row
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var phase by remember { mutableStateOf(OverlayPhase.Menu) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }

    val open = remember { Animatable(0f) }
    val menu = remember { Animatable(0f) }
    val bg = remember { Animatable(0f) }
    val vanish = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        launch { bg.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
        launch { open.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 330f)) }
        launch {
            delay(90)
            menu.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 420f))
        }
    }

    fun close(then: () -> Unit = {}) {
        if (phase != OverlayPhase.Menu) return
        phase = OverlayPhase.Closing
        scope.launch {
            launch { menu.animateTo(0f, tween(140, easing = FastOutLinearInEasing)) }
            launch { bg.animateTo(0f, tween(300, easing = FastOutSlowInEasing)) }
            open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 420f))
            onFinished()
            then()
        }
    }

    fun startDelete() {
        if (phase != OverlayPhase.Menu) return
        phase = OverlayPhase.Removing
        scope.launch {
            launch { menu.animateTo(0f, tween(170, easing = FastOutLinearInEasing)) }
            delay(120)
            val wrapUp = launch {
                delay(300)
                onDelete(row)
                bg.animateTo(0f, tween(420, easing = FastOutSlowInEasing))
            }
            vanish.animateTo(1f, tween(560, easing = FastOutSlowInEasing))
            wrapUp.join()
            onFinished()
        }
    }

    BackHandler(enabled = true) { close() }

    val entries = buildList {
        if (row.settings != null) add(MenuEntry("Configuration", Icons.Outlined.Settings, AnsuColors.TextPrimary) { close { onConfigure(row) } })
        add(
            MenuEntry(
                if (row.enabled) "Disable" else "Enable",
                if (row.enabled) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                AnsuColors.TextPrimary,
            ) { close { onToggleEnabled(row) } },
        )
        if (row.packageName != null) {
            val label = if (row.sourcesInPackage > 1) "Delete extension (${row.sourcesInPackage} sources)" else "Delete source"
            add(MenuEntry(label, Icons.Outlined.Delete, AnsuColors.Error) { startDelete() })
        }
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { rootOrigin = it.positionInRoot() }
            .pointerInput(Unit) { detectTapGestures { close() } },
    ) {
        val cardWidth = with(density) { target.bounds.width.toDp() }
        val cardHeight = with(density) { target.bounds.height.toDp() }
        val menuHeight = OverlayMenuRowHeight * entries.size + (entries.size - 1).coerceAtLeast(0).dp
        val stackHeight = cardHeight + OverlayGap + menuHeight
        val targetCenterX = with(density) { (maxWidth / 2).toPx() }
        val targetCenterY = with(density) { ((maxHeight - stackHeight) / 2 - OverlayLift + cardHeight / 2).toPx() }
        val popPx = with(density) { 12.dp.toPx() }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = bg.value.coerceIn(0f, 1f) }
                .frostedBackdrop(backdrop, enabled = true, shape = RectangleShape, radius = 26.dp)
                .background(Color.Black.copy(alpha = if (backdropBlurSupported) 0.42f else 0.82f)),
        )

        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(y = -OverlayLift)
                .width(cardWidth)
                .height(stackHeight),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(cardHeight)
                    .graphicsLayer {
                        val p = open.value
                        val v = vanish.value
                        val s = lerp(1f, 1.04f, p) * (1f - 0.1f * v)
                        scaleX = s
                        scaleY = s
                        val startCenter = Offset(target.bounds.center.x - rootOrigin.x, target.bounds.center.y - rootOrigin.y)
                        translationX = (startCenter.x - targetCenterX) * (1f - p)
                        translationY = (startCenter.y - targetCenterY) * (1f - p) - popPx * v
                        alpha = 1f - v
                        if (backdropBlurSupported && v > 0.02f) {
                            val r = v * 18.dp.toPx()
                            renderEffect = BlurEffect(r, r, TileMode.Decal)
                        }
                    },
            ) {
                InstalledSourceCard(row = row, testing = false, resultLine = null, onTest = {}, modifier = Modifier.fillMaxSize())
            }

            Spacer(Modifier.height(OverlayGap))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = menu.value
                        alpha = p.coerceIn(0f, 1f)
                        translationY = (1f - p) * 22f * density.density
                        val s = 0.94f + 0.06f * p
                        scaleX = s
                        scaleY = s
                    },
            ) {
                FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), tintAlpha = 0.66f) {
                    Column {
                        entries.forEachIndexed { index, entry ->
                            if (index > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(AnsuColors.StrokeGlass))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(OverlayMenuRowHeight)
                                    .clickable(onClick = entry.onClick)
                                    .padding(horizontal = 20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(entry.label, style = MaterialTheme.typography.bodyLarge, color = entry.tint, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(entry.icon, contentDescription = null, tint = entry.tint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}
