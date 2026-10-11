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
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Restore
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.delay

/**
 * Settings → Backup, on its own rather than as a card at the bottom of System: saving and restoring
 * everything Anisu keeps locally is a thing users go looking for, so it gets a row of its own in
 * Settings, next to System. The card holds the two actions — write an `.anisu` archive and read one
 * back — and the same message line the old section had.
 *
 * The archive holds every setting and the local library; restoring one relaunches the app, because
 * both the `SharedPreferences` and the Room database are cached for the life of the process.
 */
@Composable
fun BackupScreen(container: AppContainer, navController: NavHostController) {
    val context = LocalContext.current
    val diagnostics = container.diagnostics
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var restartPending by remember { mutableStateOf(false) }
    LaunchedEffect(restartPending) {
        if (restartPending) {
            delay(1200)
            restartApp(context)
        }
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            FrostedGlassCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, tintAlpha = 0.4f) {
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

private fun timestampName(): String =
    java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date())
