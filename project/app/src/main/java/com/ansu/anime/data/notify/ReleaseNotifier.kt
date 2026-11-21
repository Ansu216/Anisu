package com.ansu.anime.data.notify

import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.ansu.anime.MainActivity
import com.ansu.anime.R
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory
import com.ansu.anime.core.util.NotificationIcons
import com.ansu.anime.data.update.UpdateNotifier

/**
 * Posts the real system notifications for anime: "X has released" and "Episode N of X is out". They use their own
 * "New episodes & releases" channel, so the user can silence them in Android settings without touching the
 * app-update notifications. A tap opens that show's details page.
 *
 * Whether the system would deliver anything (the Android 13 permission, notifications switched off for the app) is
 * answered by [UpdateNotifier.blockedReason], the same check the update notification uses; every skipped post is
 * written to the diagnostics log.
 */
class ReleaseNotifier(
    private val context: Context,
    private val history: NotificationHistory,
    private val diagnostics: Diagnostics? = null,
) {

    private val blocker = UpdateNotifier(context, diagnostics)

    /** An unreleased show has just come out. Returns whether the system was really handed the notification. */
    fun notifyReleased(anilistId: Int, title: String, posterUrl: String?): Boolean {
        val text = "The anime you were waiting for is out · tap to open"
        record("release:$anilistId", anilistId, title, "Has released", posterUrl)
        return post(anilistId = anilistId, title = "$title has released", text = text, logTitle = title)
    }

    /** A new episode of a show on the Watching list (or followed) has aired. */
    fun notifyEpisode(anilistId: Int, title: String, episode: Int, posterUrl: String?): Boolean {
        record("episode:$anilistId:$episode", anilistId, title, "Episode $episode is out", posterUrl)
        return post(
            anilistId = anilistId,
            title = title,
            text = "Episode $episode is out · tap to watch",
            logTitle = "$title E$episode",
        )
    }

    /** Lists the event in the bell's inbox even when the system cannot show it (the key keeps retries from repeating it). */
    private fun record(key: String, anilistId: Int, title: String, message: String, posterUrl: String?) {
        history.add(NotificationEntry(key, anilistId, title, message, posterUrl, System.currentTimeMillis()))
    }

    fun canNotify(): Boolean = blocker.canNotify()

    @SuppressLint("MissingPermission")
    private fun post(anilistId: Int, title: String, text: String, logTitle: String): Boolean {
        val blocked = blocker.blockedReason()
        if (blocked != null) {
            diagnostics?.log(LogCategory.UPDATE, "Release notification \"$logTitle\" not posted: $blocked")
            return false
        }
        return runCatching {
            ensureChannel()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setLargeIcon(NotificationIcons.largeIcon(context))
                .setContentTitle(title)
                .setContentText(text)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openDetailsIntent(anilistId, title))
                .build()
            // One slot per show: a newer episode replaces the older notification instead of stacking.
            NotificationManagerCompat.from(context).notify(ID_BASE + anilistId, notification)
            diagnostics?.log(LogCategory.UPDATE, "Posted release notification \"$logTitle\"")
            true
        }.getOrElse { error ->
            diagnostics?.log(LogCategory.UPDATE, "Posting release notification \"$logTitle\" failed: ${error.message}")
            false
        }
    }

    private fun openDetailsIntent(anilistId: Int, title: String): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_ANIME_ID, anilistId)
            putExtra(MainActivity.EXTRA_OPEN_ANIME_TITLE, title)
        }
        return PendingIntent.getActivity(
            context,
            ID_BASE + anilistId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "New episodes & releases", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when an anime you follow releases or a new episode of your Watching list airs."
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "ansu_releases"

        /** Keeps these ids clear of the update notifications (1001-1003). */
        private const val ID_BASE = 100_000
    }
}
