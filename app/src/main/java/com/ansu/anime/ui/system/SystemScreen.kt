@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.system

import android.app.AlarmManager
import android.app.PendingIntent
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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import kotlinx.coroutines.delay

/**
 * Settings → System, rebuilt from scratch: a header, then one black-glass card for the diagnostics
 * tooling and one for backup and restore. Diagnostics can be shared or saved to the device; the
 * `.anisu` archive holds every setting and the library, and restoring one relaunches the app so
 * everything applies at once.
 */
@Composable
fun SystemScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val diagnostics = container.diagnostics
    var refresh by remember { mutableIntStateOf(0) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    // A restored backup has to be re-read from disk, and both the SharedPreferences and the Room
    // database are cached for the life of the process, so the app relaunches itself.
    var restartPending by remember { mutableStateOf(false) }
    LaunchedEffect(restartPending) {
        if (restartPending) {
            delay(1200)
            restartApp(context)
        }
    }

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
        backupMessage = if (saved) "Backup saved (${file.length() / 1024} KB)." else "Could not save the backup."
        if (saved) diagnostics.log(LogCategory.CLICK, "Settings backup saved (${file.length()} bytes)")
        refresh++
    }

    // Restore an .anisu archive, then relaunch so every setting is re-read.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The database file is overwritten by the restore, so close it first.
        runCatching { container.database.close() }
        container.backupManager.restoreFrom(uri).fold(
            onSuccess = { count ->
                backupMessage = "Restored $count files. Restarting Anisu to apply them…"
                diagnostics.log(LogCategory.CLICK, "Settings backup restored ($count files)")
            },
            onFailure = { error ->
                backupMessage = "Restore failed: ${error.message ?: "unknown error"}"
                toast(context, "Restore failed: ${error.message ?: "unknown error"}")
            },
        )
        // The database is closed before the restore runs, so the app has to relaunch either way.
        restartPending = true
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

            SectionCard("Backup & restore") {
                Text(
                    text = "Back up your settings, your watch progress and your local library into a single " +
                        ".anisu file you keep yourself. Restoring it brings everything back and Anisu restarts " +
                        "itself to apply it; the AniList sign-in is the only thing left out, because it is tied " +
                        "to this device.",
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

/**
 * Relaunches the app. A restored backup overwrites the settings files and the Room database, and both
 * are cached in memory for the life of the process, so starting a fresh process is what makes every
 * restored setting take effect without the user restarting the app by hand. The alarm is scheduled
 * first so the relaunch survives this process going away.
 */
private fun restartApp(context: Context) {
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    if (launch != null) {
        launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pending = PendingIntent.getActivity(
            context,
            0,
            launch,
            PendingIntent.FLAG_CANCEL_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.set(AlarmManager.RTC, System.currentTimeMillis() + 250L, pending)
    }
    android.os.Process.killProcess(android.os.Process.myPid())
    kotlin.system.exitProcess(0)
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
