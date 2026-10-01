package com.ansu.anime.data.update

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ansu.anime.AnsuApp
import com.ansu.anime.core.diagnostics.LogCategory
import java.util.concurrent.TimeUnit

/**
 * Re-checks for a new Ansu build while the app is closed, so the native "update available"
 * notification is not limited to the moment the app is opened. WorkManager runs it roughly every
 * twelve hours, only when there is a network, and without waking the device on a battery saver.
 *
 * The check itself is the same [UpdateManager.checkNow] the Updates screen uses, so the
 * once-per-build notification rule and the stored channel are shared instead of duplicated.
 */
class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? AnsuApp)?.container ?: return Result.success()
        // Respect the switch on the Updates screen even if a run was already queued.
        if (!container.updateManager.prefs.autoCheck.value) return Result.success()
        return try {
            container.updateManager.checkNow()
            Result.success()
        } catch (error: Exception) {
            container.diagnostics.log(LogCategory.UPDATE, "Background update check failed: ${error.message}")
            // A flaky network is not a reason to drop the schedule: ask WorkManager to try again.
            Result.retry()
        }
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ansu-update-check"
        private const val INTERVAL_HOURS = 12L

        /** Schedules the periodic check. Idempotent: an existing schedule is kept, not replaced. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build(),
                )
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** Removes the schedule when the user turns automatic checks off. */
        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
