@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.ansu.anime.ui.backup

import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.di.AppContainer
import com.ansu.anime.ui.components.FrostedGlassCard
import com.ansu.anime.ui.components.GlassGroup
import com.ansu.anime.ui.components.GlassGroupLabel
import com.ansu.anime.ui.components.GlassRow
import com.ansu.anime.ui.components.GlassRowValue
import com.ansu.anime.ui.components.GlassToggleRow
import com.ansu.anime.ui.theme.AnsuColors
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * Settings → Backup. The top card is the user's own backup: they write an `.anisu` archive through
 * the system file picker and read one back, and the app restarts so the restored settings apply.
 *
 * Under it sits the automatic backup, which is what makes the feature work for someone who never
 * opens this screen: a daily job writes an archive into the app's own storage and keeps only the
 * newest few, and each of those can be restored from the list, with the same restart as a picked
 * file. The screen reads the switch and the list through `BackupManager`, so the scheduled job and
 * this page never disagree.
 */
@Composable
fun BackupScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val diagnostics = container.diagnostics
    val backupManager = container.backupManager
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var restartPending by remember { mutableStateOf(false) }
    var autoFiles by remember { mutableStateOf(backupManager.autoBackupFiles()) }

    val autoBackup by backupManager.prefs.autoBackup.collectAsStateWithLifecycle()
    val lastRun by backupManager.prefs.lastAutoBackupAt.collectAsStateWithLifecycle()
    val lastRunText = remember(lastRun) {
        if (lastRun == 0L) "Never yet" else SimpleDateFormat(TIME_FORMAT, Locale.US).format(Date(lastRun))
    }

    LaunchedEffect(autoBackup) {
        // The list is a plain directory read, so it is refreshed whenever the switch changes too.
        autoFiles = backupManager.autoBackupFiles()
    }
    LaunchedEffect(restartPending) {
        if (restartPending) {
            delay(1200)
            restartApp(context)
        }
    }

    /** Applies a finished restore: the database is closed by the caller, and the app relaunches. */
    val applyRestore: (Result<Int>) -> Unit = { result ->
        result.fold(
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
    }

    /** Restores one of the automatic backups, exactly like a file the user picked. */
    val restoreAutoBackup: (File) -> Unit = { file ->
        runCatching { container.database.close() }
        diagnostics.log(LogCategory.CLICK, "Automatic backup restored (${file.name})")
        applyRestore(backupManager.restoreFrom(file))
    }

    // Back up settings + library into an .anisu archive.
    val backupLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val file = backupManager.buildBackupFile()
        if (file == null) {
            backupMessage = "Could not build the backup."
            return@rememberLauncherForActivityResult
        }
        val saved = writeFileTo(context, uri, file)
        backupMessage = if (saved) "Backup saved (${file.length() / 1024} KB)." else "Could not save the backup."
        if (saved) diagnostics.log(LogCategory.CLICK, "Settings backup saved (${file.length()} bytes)")
    }

    // Restore an .anisu archive, then relaunch so every setting is re-read.
    val restoreLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        // The database file is overwritten by the restore, so close it first.
        runCatching { container.database.close() }
        applyRestore(backupManager.restoreFrom(uri))
    }

    Scaffold(
        containerColor = AnsuColors.Background,
        topBar = {
            TopAppBar(
                title = { Text("Backup") },
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
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
        ) {
            FrostedGlassCard(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                shape = MaterialTheme.shapes.large,
                tintAlpha = 0.4f,
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        "Backup & restore",
                        style = MaterialTheme.typography.titleSmall,
                        color = AnsuColors.TextPrimary,
                        fontWeight = FontWeight.Bold,
                    )
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

            GlassGroupLabel("Automatic")
            GlassGroup {
                GlassToggleRow(
                    icon = Icons.Filled.Schedule,
                    title = "Automatic backup",
                    description = "Write an .anisu copy every day and keep the last 3",
                    checked = autoBackup,
                    onCheckedChange = { enabled ->
                        backupManager.setAutoBackup(enabled)
                        diagnostics.log(LogCategory.CLICK, "Automatic backup ${if (enabled) "on" else "off"}")
                    },
                    showDivider = false,
                )
                GlassRow(
                    icon = Icons.Filled.Save,
                    title = "Back up now",
                    description = "Write one right away, without waiting for the daily run",
                    onClick = {
                        val file = backupManager.writeAutoBackup()
                        autoFiles = backupManager.autoBackupFiles()
                        backupMessage = if (file == null) {
                            "Could not write the backup."
                        } else {
                            "Automatic backup saved (${file.length() / 1024} KB)."
                        }
                        diagnostics.log(
                            LogCategory.CLICK,
                            if (file == null) "Automatic backup failed" else "Automatic backup written by hand",
                        )
                    },
                )
                GlassRow(
                    title = "Last automatic backup",
                    description = if (autoBackup) "The daily job runs even while Anisu is closed" else "Turned off",
                    trailing = { GlassRowValue(lastRunText) },
                )
            }

            GlassGroupLabel("Saved on this device")
            GlassGroup {
                if (autoFiles.isEmpty()) {
                    GlassRow(
                        icon = Icons.Filled.Backup,
                        title = "No automatic backups yet",
                        description = "Switch automatic backups on and the first one lands within a day",
                        showDivider = false,
                    )
                } else {
                    autoFiles.forEachIndexed { index, file ->
                        GlassRow(
                            icon = Icons.Filled.Backup,
                            title = remember(file.lastModified()) {
                                SimpleDateFormat(TIME_FORMAT, Locale.US).format(Date(file.lastModified()))
                            },
                            description = "${file.length() / 1024} KB · ${file.name}",
                            showDivider = index != 0,
                            trailing = {
                                TextButton(onClick = { restoreAutoBackup(file) }) { Text("Restore") }
                            },
                        )
                    }
                }
            }
        }
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
        val pending = android.app.PendingIntent.getActivity(
            context,
            0,
            launch,
            android.app.PendingIntent.FLAG_CANCEL_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE,
        )
        (context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager)
            ?.set(AlarmManager.RTC, System.currentTimeMillis() + 250L, pending)
    }
    android.os.Process.killProcess(android.os.Process.myPid())
    kotlin.system.exitProcess(0)
}

/** The date and time a backup was written, in the app's English formatting. */
private const val TIME_FORMAT = "d MMM, HH:mm"

private fun timestampName(): String =
    SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
