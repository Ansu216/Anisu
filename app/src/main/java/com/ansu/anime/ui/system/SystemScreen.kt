@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.system

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.theme.AnsuColors
import java.io.File

/**
 * Settings → System, rebuilt from scratch: a header, then one black-glass card for the diagnostics
 * tooling, whose report can be shared or saved to the device. Backup and restore used to be the
 * second card here; it has its own screen now, one row above in Settings.
 */
@Composable
fun SystemScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val diagnostics = container.diagnostics
    var refresh by remember { mutableIntStateOf(0) }

    // Save the diagnostics report to a location the user picks (Downloads, Drive, …), not only share it.
    val saveLogsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = diagnostics.buildExportFile()
        if (file == null) {
            toast(context, "Could not build the diagnostics report")
            return@rememberLauncherForActivityResult
        }
        val saved = writeFileTo(context, uri, file)
        toast(context, if (saved) "Report saved" else "Could not save the report")
        if (saved) diagnostics.log(LogCategory.CLICK, "Diagnostics report saved to device (${file.length()} bytes)")
        refresh++
    }

    Scaffold(
        containerColor = AnsuColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("System") },
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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(
                sessionCount = diagnostics.sessionEntries().size,
                sizeBytes = diagnostics.logSizeBytes(),
                lastCrashAt = diagnostics.lastCrashAt,
                refreshKey = refresh,
            )

            SectionCard("Diagnostics") {
                Text(
                    text = "Anisu keeps a local log of crashes, playback, taps, navigation and network " +
                        "errors. Nothing is ever uploaded: the report is written on this device, and you " +
                        "choose whether to share it or save it yourself.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AnsuColors.TextSecondary,
                )
                Button(
                    onClick = {
                        val file = diagnostics.buildExportFile()
                        if (file == null) {
                            toast(context, "Could not build the diagnostics report")
                            return@Button
                        }
                        val intent = diagnostics.exportIntent(file)
                        if (intent == null) {
                            toast(context, "No app available to share the report")
                            return@Button
                        }
                        diagnostics.log(LogCategory.CLICK, "Export logs shared (${file.length()} bytes)")
                        runCatching { context.startActivity(Intent.createChooser(intent, "Export Anisu logs")) }
                        refresh++
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Share logs", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = { saveLogsLauncher.launch("Anisu-diagnostics-${timestampName()}.txt") },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Save logs to device", modifier = Modifier.padding(start = 8.dp))
                }
                OutlinedButton(
                    onClick = {
                        container.diagnostics.clear()
                        container.diagnostics.log(LogCategory.CLICK, "Diagnostics log cleared by the user")
                        refresh++
                    },
                    shape = RoundedCornerShape(20.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Filled.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text("Clear logs", modifier = Modifier.padding(start = 8.dp))
                }
            }

        }
    }
}

/** Header card: what the diagnostics log currently holds, over a soft accent glow. */
@Composable
private fun HeaderCard(sessionCount: Int, sizeBytes: Long, lastCrashAt: Long, refreshKey: Int) {
    val stamp = remember(refreshKey) { System.currentTimeMillis() }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(
                Brush.verticalGradient(
                    listOf(AnsuColors.Accent.copy(alpha = 0.18f), AnsuColors.BackgroundElevated),
                ),
            )
            .padding(20.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Shield, contentDescription = null, tint = AnsuColors.TextPrimary, modifier = Modifier.size(28.dp))
                Text(
                    "Private by design",
                    style = MaterialTheme.typography.titleLarge,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
            InfoLine("Entries this session", sessionCount.toString())
            InfoLine("On-disk log size", formatBytes(sizeBytes))
            InfoLine("Last captured crash", if (lastCrashAt > 0L) relativeStamp(lastCrashAt, stamp) else "None")
        }
    }
}

/** A black-glass card with a heading. */
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
private fun InfoLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextSecondary)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = AnsuColors.TextPrimary, fontWeight = FontWeight.SemiBold)
    }
}

/** Copies [source] into the document the user chose through the system file picker. */
private fun writeFileTo(context: Context, uri: Uri, source: File): Boolean = runCatching {
    context.contentResolver.openOutputStream(uri)?.use { output ->
        source.inputStream().use { it.copyTo(output) }
    } ?: error("The system returned no writable file")
    true
}.getOrDefault(false)

private fun toast(context: Context, message: String) {
    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
}

private fun timestampName(): String =
    java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())

private fun formatBytes(bytes: Long): String = when {
    bytes <= 0L -> "0 KB"
    bytes < 1024L * 1024L -> "${bytes / 1024} KB"
    else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
}

private fun relativeStamp(millis: Long, now: Long): String {
    val minutes = ((now - millis) / 60_000L).coerceAtLeast(0L)
    return when {
        minutes < 1L -> "just now"
        minutes < 60L -> "$minutes min ago"
        minutes < 60L * 24L -> "${minutes / 60L} h ago"
        else -> "${minutes / (60L * 24L)} d ago"
    }
}
