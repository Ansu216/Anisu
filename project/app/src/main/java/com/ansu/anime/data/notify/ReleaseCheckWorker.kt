package com.ansu.anime.data.notify

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.ansu.anime.AnsuApp
import com.ansu.anime.core.diagnostics.LogCategory
import java.util.concurrent.TimeUnit

/**
 * Looks for what the user asked to be told about while the app is closed: a followed show that has just released,
 * and a new episode of a followed show or of anything on the Watching list. WorkManager runs it about every three
 * hours, only with a network.
 *
 * The first time a show is seen it is only recorded (its newest aired episode becomes the starting point), so
 * following or watching a show never fires a notification for episodes that were already out.
 */
class ReleaseCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val container = (applicationContext as? AnsuApp)?.container ?: return Result.success()
        val prefs = container.notificationPrefs
        if (!prefs.enabled.value) return Result.success()
        return try {
            check(container)
            Result.success()
        } catch (error: Exception) {
            container.diagnostics.log(LogCategory.UPDATE, "Release check failed: ${error.message}")
            Result.retry()
        }
    }

    private suspend fun check(container: com.ansu.anime.di.AppContainer) {
        val prefs = container.notificationPrefs
        val notifier = container.releaseNotifier
        val followed = prefs.followedIds.value
        val watching = if (prefs.newEpisodes.value) watchingIds(container) else emptySet()
        val ids = followed + watching
        if (ids.isEmpty()) return

        container.aniListApi.getReleaseInfo(ids).forEach { info ->
            val isFollowed = info.id in followed
            val previousStatus = prefs.lastStatus(info.id)

            // An unreleased show that came out since the last look.
            if (isFollowed && previousStatus == "NOT_YET_RELEASED" && info.status != "NOT_YET_RELEASED") {
                // Keep the old status when the notification could not be posted, so the next check tries again.
                if (notifier.notifyReleased(info.id, info.title, info.posterUrl)) prefs.setLastStatus(info.id, info.status)
            } else {
                prefs.setLastStatus(info.id, info.status)
            }

            // A new episode: only for shows that are airing (or just started), never for a finished back-catalogue.
            val aired = info.airedEpisodes ?: return@forEach
            if (info.status == "NOT_YET_RELEASED") return@forEach
            val last = prefs.lastEpisode(info.id)
            when {
                last == null -> prefs.setLastEpisode(info.id, aired)
                aired > last && info.status == "RELEASING" -> {
                    // Remember the episode only once the notification was really handed over, so a check that ran
                    // before the Android 13 permission was granted does not swallow it.
                    if (notifier.notifyEpisode(info.id, info.title, aired, info.posterUrl)) prefs.setLastEpisode(info.id, aired)
                }
                aired > last -> prefs.setLastEpisode(info.id, aired)
            }
        }
    }

    /** AniList ids of everything being watched: the signed-in Watching list plus the on-device one. */
    private suspend fun watchingIds(container: com.ansu.anime.di.AppContainer): Set<Int> {
        val remote = runCatching { container.aniListRepository.getCurrentlyWatching().map { it.mediaId } }
            .getOrDefault(emptyList())
        val local = runCatching {
            container.localListRepository.snapshot().filter { it.status == "CURRENT" }.map { it.anilistId }
        }.getOrDefault(emptyList())
        return (remote + local).toSet()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "ansu-release-check"
        private const val RUN_NOW_NAME = "ansu-release-check-now"
        private const val INTERVAL_HOURS = 3L

        private val constraints = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Schedules the periodic check. Idempotent: an existing schedule is kept, not replaced. */
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<ReleaseCheckWorker>(INTERVAL_HOURS, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        /** One check right now (when a show is followed), so its starting point is recorded without waiting hours. */
        fun runSoon(context: Context) {
            val request = OneTimeWorkRequestBuilder<ReleaseCheckWorker>().setConstraints(constraints).build()
            WorkManager.getInstance(context).enqueueUniqueWork(RUN_NOW_NAME, ExistingWorkPolicy.REPLACE, request)
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
