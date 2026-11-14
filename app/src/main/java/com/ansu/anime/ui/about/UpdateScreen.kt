@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.about

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import com.ansu.anime.ui.components.FadingTopAppBar
import com.ansu.anime.ui.components.withoutTop
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.BuildConfig
import com.ansu.anime.R
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.data.update.UpdateChannel
import com.ansu.anime.data.update.UpdateCheckResult
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.HeavyGlassCard
import com.ansu.anime.ui.theme.AnsuColors
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The build on offer: what it is, how far the download got, and the one action that applies to it —
 * Download while the file is missing, Install once it is on disk. The screen shows both ends of that
 * story with the same layout, so "up to date" and "a new build" are the same page with different words.
 */
@Composable
fun UpdateScreen(container: AppContainer, navController: NavHostController) {
    val state by container.updateManager.state.collectAsStateWithLifecycle()
    val channel by container.updateManager.prefs.channel.collectAsStateWithLifecycle()

    // Opening the screen is the check: the row that led here does not run one of its own.
    LaunchedEffect(Unit) { container.updateManager.check() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            FadingTopAppBar(
                title = { Text("Update") },
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
        val available = state.result as? UpdateCheckResult.Available
        val update = available?.update
        val downloaded = state.downloadedApk

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding.withoutTop())
                .verticalScroll(rememberScrollState())
                .padding(top = padding.calculateTopPadding())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.fillMaxWidth().padding(top = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                UpdaterBlobs(active = update != null, modifier = Modifier)
                // The app's own mark on the turning hexagons, the way vivi puts the icon at the top of its updater.
                Image(
                    painter = painterResource(R.drawable.splash_logo_a),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(72.dp),
                )
            }

            Text(
                text = update?.versionName ?: "Anisu ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.headlineMedium,
                color = AnsuColors.TextPrimary,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = when {
                    state.isChecking -> "looking for a newer build…"
                    update == null -> "you are on the latest ${channelLabel(channel)} build"
                    downloaded != null -> "downloaded · ready to install"
                    state.isDownloading -> "downloading…"
                    else -> "update available"
                },
                style = MaterialTheme.typography.bodySmall,
                color = AnsuColors.TextSecondary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 2.dp),
            )

            UpdateStatusPill(state = state, modifier = Modifier.padding(top = 10.dp))

            if (update != null) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = "installed ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextSecondary,
                    )
                    Text(
                        text = "  →  ",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextTertiary,
                    )
                    Text(
                        text = update.versionName,
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (state.isDownloading || downloaded != null) {
                val progress = if (downloaded != null) 1f else state.downloadProgress
                UpdateProgressBar(progress = progress, modifier = Modifier.padding(top = 16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = if (downloaded != null) "Downloaded" else "Downloading",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextSecondary,
                    )
                    Text(
                        text = "${(progress * 100).roundToInt()}%",
                        style = MaterialTheme.typography.labelSmall,
                        color = AnsuColors.TextSecondary,
                    )
                }
            }

            state.error?.let { message ->
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodySmall,
                    color = AnsuColors.Error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 14.dp),
                )
            }

            if (update != null) {
                Button(
                    onClick = {
                        if (downloaded != null) {
                            container.diagnostics.log(LogCategory.CLICK, "Update → install ${update.versionName}")
                            container.updateManager.installDownloaded()
                        } else {
                            container.diagnostics.log(LogCategory.CLICK, "Update → download ${update.versionName}")
                            container.updateManager.download()
                        }
                    },
                    enabled = !state.isDownloading,
                    shape = RoundedCornerShape(percent = 50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AnsuColors.Accent,
                        contentColor = AnsuColors.OnAccent,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).height(56.dp),
                ) {
                    Icon(
                        imageVector = if (downloaded != null) Icons.Filled.Check else Icons.Filled.Download,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp),
                    )
                    Text(
                        text = when {
                            state.isDownloading -> "Downloading… ${(state.downloadProgress * 100).roundToInt()}%"
                            downloaded != null -> "Install"
                            else -> "Download"
                        },
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }

                if (downloaded != null) {
                    GlassGroup(modifier = Modifier.padding(top = 12.dp)) {
                        GlassRow(
                            icon = Icons.Filled.Check,
                            title = "Downloaded",
                            description = "Signature verified · ready to install",
                            showDivider = false,
                        )
                    }
                }

                update.notes?.takeIf { it.isNotBlank() }?.let { notes ->
                    HeavyGlassCard(
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        shape = RoundedCornerShape(24.dp),
                        tintAlpha = 0.4f,
                    ) {
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "What's new?",
                                style = MaterialTheme.typography.titleSmall,
                                color = AnsuColors.TextPrimary,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                text = notes,
                                style = MaterialTheme.typography.bodySmall,
                                color = AnsuColors.TextSecondary,
                            )
                        }
                    }
                }
            } else {
                HeavyGlassCard(
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    shape = RoundedCornerShape(24.dp),
                    tintAlpha = 0.4f,
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = if (state.result is UpdateCheckResult.Failed) "The check did not go through" else "Nothing to install",
                            style = MaterialTheme.typography.titleSmall,
                            color = AnsuColors.TextPrimary,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = when (val result = state.result) {
                                is UpdateCheckResult.Failed -> result.message
                                is UpdateCheckResult.UpToDate ->
                                    "Checked the ${channelLabel(channel)} channel: ${result.latestVersionName} is the newest build. " +
                                        "A newer one shows up here by itself at the next check."
                                else -> "Checking the ${channelLabel(channel)} channel."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = AnsuColors.TextSecondary,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { container.updateManager.check() },
                enabled = !state.isChecking && !state.isDownloading,
                shape = RoundedCornerShape(percent = 50),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = AnsuColors.TextPrimary),
                modifier = Modifier.fillMaxWidth().padding(top = 10.dp).height(50.dp),
            ) {
                Text("Check for update", fontWeight = FontWeight.SemiBold)
            }

            Text(
                text = "Anisu ${BuildConfig.VERSION_NAME} · version code ${BuildConfig.VERSION_CODE} · " +
                    "${channelLabel(channel)} channel",
                style = MaterialTheme.typography.labelSmall,
                color = AnsuColors.TextTertiary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 14.dp),
            )
        }
    }
}

/** The channel the check runs on, as the small print names it. */
private fun channelLabel(channel: UpdateChannel): String = when (channel) {
    UpdateChannel.NIGHTLY -> "nightly"
    UpdateChannel.RELEASES -> "release"
}

/**
 * The three hexagons behind the version number, turning slowly in opposite directions. They are the
 * one decorative thing on the screen: brighter while there is a build to install, quieter when there
 * is nothing to do.
 */
@Composable
private fun UpdaterBlobs(active: Boolean, modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "updater blobs")
    val forward by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 26000, easing = LinearEasing), RepeatMode.Restart),
        label = "forward",
    )
    val backward by transition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(tween(durationMillis = 31000, easing = LinearEasing), RepeatMode.Restart),
        label = "backward",
    )
    val strong = if (active) 0.13f else 0.09f
    val medium = if (active) 0.09f else 0.06f
    val soft = if (active) 0.06f else 0.04f

    Canvas(modifier = modifier.fillMaxWidth().height(150.dp).padding(horizontal = 40.dp)) {
        hexagon(
            center = Offset(size.width / 2f, size.height / 2f),
            radius = size.minDimension * 0.34f,
            color = AnsuColors.Accent.copy(alpha = strong),
            rotation = forward,
        )
        hexagon(
            center = Offset(size.width * 0.28f, size.height * 0.32f),
            radius = size.minDimension * 0.24f,
            color = AnsuColors.Accent.copy(alpha = medium),
            rotation = backward,
        )
        hexagon(
            center = Offset(size.width * 0.73f, size.height * 0.70f),
            radius = size.minDimension * 0.19f,
            color = AnsuColors.Accent.copy(alpha = soft),
            rotation = forward,
        )
    }
}

/** One hexagon, drawn as a six-point path so it turns without a bitmap and without a shape library. */
private fun DrawScope.hexagon(center: Offset, radius: Float, color: Color, rotation: Float) {
    rotate(degrees = rotation, pivot = center) {
        val path = Path()
        repeat(6) { index ->
            val angle = (PI / 3.0 * index - PI / 2.0).toFloat()
            val x = center.x + radius * cos(angle)
            val y = center.y + radius * sin(angle)
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path, color)
    }
}

/**
 * Material 3 Expressive progress: a thick rounded track, an active indicator that stops short of the
 * end leaving the expressive gap, and the stop indicator on the right.
 */
@Composable
private fun UpdateProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animated by animateFloatAsState(
        targetValue = progress.coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220),
        label = "download progress",
    )
    Canvas(modifier = modifier.fillMaxWidth().height(14.dp)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = AnsuColors.AccentSoft, cornerRadius = radius)

        val stop = 4.dp.toPx()
        val gap = 6.dp.toPx()
        val usable = (size.width - gap - stop).coerceAtLeast(1f)
        val activeWidth = (usable * animated).coerceAtLeast(0f)
        if (activeWidth > 0f) {
            drawRoundRect(
                color = AnsuColors.Accent,
                size = Size(activeWidth.coerceAtLeast(size.height), size.height),
                cornerRadius = radius,
            )
        }
        drawRoundRect(
            color = AnsuColors.TextTertiary,
            topLeft = Offset(size.width - stop, (size.height - stop) / 2f),
            size = Size(stop, stop),
            cornerRadius = CornerRadius(stop / 2f),
        )
    }
}
