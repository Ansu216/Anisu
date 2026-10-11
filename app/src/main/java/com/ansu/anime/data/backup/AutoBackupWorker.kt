package com.ansu.anime.data.backup

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ansu.anime.AnsuApp
import com.ansu.anime.core.diagnostics.LogCategory
import java.util.concurrent.TimeUnit

/**
 * Writes the automatic `.anisu` backup while the app is closed, so someone who never taps
 * "Create backup" still has a recent copy of their settings and library. WorkManager runs it
 * roughly once a day; it needs no network and no permission, because the archive is written into
 * the app's own private storage, and it keeps only the newest few (see
 * [BackupManager.MAX_AUTO_BACKUPS]).
 */
class AutoBackupWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? AnsuApp)?.container ?: return Result.success()
        // Respect the switch on the Backup screen even if a run was already queued.
        if (!container.backupManager.prefs.autoBackup.value) return Result.success()
        val file = runCatching { container.backupManager.writeAutoBackup() }.getOrNull()
        if (file != null) {
            container.diagnostics.log(LogCategory.APP, "Automatic backup written (${file.length()} bytes)")
            return Result.success()
        }
        container.diagnostics.log(LogCategory.APP, "Automatic backup failed")
        // A full disk or a filesystem hiccup is not a reason to drop the schedule: try again later.
        return Result.retry()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ansu-auto-backup"
        private const val INTERVAL_HOURS = 24L

        /** Schedules the daily backup. Idempotent: an existing schedule is kept, not replaced. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<AutoBackupWorker>(INTERVAL_HOURS, TimeUnit.HOURS).build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Removes the schedule when the user turns automatic backups off. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
