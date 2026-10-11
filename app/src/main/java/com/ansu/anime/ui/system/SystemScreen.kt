@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.system

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.theme.AnsuColors
import java.io.File

/**
 * Settings → System. It holds the diagnostics tooling — exporting every crash, playback, click,
 * navigation and network event as a single text report the user can share **or** save to their own
 * storage — plus a full backup and restore of the app's settings and library as an `.anisu` file.
 */
@Composable
fun SystemScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val diagnostics = container.diagnostics
    var refresh by remember { mutableIntStateOf(0) }
    var backupMessage by remember { mutableStateOf<String?>(null) }

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

    // Back up settings + library into an .anisu archive.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = container.backupManager.buildBackupFile()
        if (file == null) {
            backupMessage = "Could not build the backup."
            return@rememberLauncherForActivityResult
        }
        val saved = writeFileTo(context, uri, file)
        backupMessage = if (saved) {
            "Backup saved (${file.length() / 1024} KB)."
        } else {
            "Could not save the backup."
        }
        if (saved) diagnostics.log(LogCategory.CLICK, "Settings backup saved (${file.length()} bytes)")
        refresh++
    }

    // Restore an .anisu archive.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The database file is overwritten by the restore, so close it first.
        runCatching { container.database.close() }
        container.backupManager.restoreFrom(uri).fold(
            onSuccess = { count ->
                backupMessage = "Restored $count files. Restart Anisu to apply every setting."
                diagnostics.log(LogCategory.CLICK, "Settings backup restored ($count files)")
            },
            onFailure = { error ->
                backupMessage = "Restore failed: ${error.message ?: "unknown error"}"
            },
        )
        refresh++
    }

    Scaffold(
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
            Text("Diagnostics", style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary)
            Text(
                text = "Anisu keeps a local log of crashes, playback, taps, navigation and network errors. " +
                    "Nothing is ever uploaded: the report is written on this device, and you choose whether " +
                    "to share it or save it yourself.",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )

            DiagnosticsCard(
                sessionCount = diagnostics.sessionEntries().size,
                sizeBytes = diagnostics.logSizeBytes(),
                lastCrashAt = diagnostics.lastCrashAt,
                refreshKey = refresh,
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
                    runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Export Anisu logs")) }
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

            Text("Backup", style = MaterialTheme.typography.titleMedium, color = AnsuColors.TextPrimary, modifier = Modifier.padding(top = 8.dp))
            Text(
                text = "Back up your settings, your watch progress and your local library into a single " +
                    ".anisu file you keep yourself. Restoring it brings everything back; the AniList sign-in " +
                    "is the only thing left out, because it is tied to this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = AnsuColors.TextSecondary,
            )

            Button(
                onClick = { backupLauncher.launch("Anisu-backup-${timestampName()}.anisu") },
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AnsuColors.Accent, contentColor = AnsuColors.OnAccent),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Create backup (.anisu)", fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
            }

            OutlinedButton(
                onClick = { restoreLauncher.launch(arrayOf("*/*")) },
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                Text("Restore from backup", modifier = Modifier.padding(start = 8.dp))
            }

            backupMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = AnsuColors.TextSecondary)
            }
        }
    }
}

@Composable
private fun DiagnosticsCard(sessionCount: Int, sizeBytes: Long, lastCrashAt: Long, refreshKey: Int) {
    // refreshKey is read so the card recomposes after an export/clear without a full screen reload.
    val stamp = remember(refreshKey) { System.currentTimeMillis() }
    FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BugReport, contentDescription = null, tint = AnsuColors.TextSecondary, modifier = Modifier.size(18.dp))
                Text(
                    "Log status",
                    style = MaterialTheme.typography.titleSmall,
                    color = AnsuColors.TextPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            InfoLine("Entries this session", sessionCount.toString())
            InfoLine("On-disk log size", formatBytes(sizeBytes))
            InfoLine("Last captured crash", if (lastCrashAt > 0L) relativeStamp(lastCrashAt, stamp) else "None")
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
