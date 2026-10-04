package com.ansu.anime.data.update

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.ansu.anime.MainActivity
import com.ansu.anime.R
import com.ansu.anime.core.diagnostics.Diagnostics
import com.ansu.anime.core.diagnostics.LogCategory

/**
 * Posts the **real** system-level Android notification that tells the user a new Ansu build is ready:
 * a `NotificationManagerCompat.notify()` into the "App updates" channel, so it lands in the status bar
 * and the notification shade like any other app's alert, with the app closed and without any screen
 * being drawn.
 *
 * This class is the OS notification only. The glass banner pinned over the screens is a different
 * thing entirely, a plain composable (`ui/components/UpdateBanner.kt`); the two are independent, and
 * the banner is not what this posts.
 *
 * On Android 13+ nothing is delivered until the runtime `POST_NOTIFICATIONS` permission is granted,
 * so [blockedReason] reports exactly what is in the way and every skipped post is written to the
 * diagnostics log (Settings → System) instead of failing silently.
 */
class UpdateNotifier(
    private val context: Context,
    private val diagnostics: Diagnostics? = null,
) {

    /**
     * Posts the "update available" notification and reports whether it was really handed to the
     * system, so the caller only remembers a build as announced when the user could see it (a check
     * that runs before the Android 13 permission is granted must not silence that build for good).
     */
    fun notifyUpdate(update: AvailableUpdate): Boolean {
        val channelLabel = if (update.channel == UpdateChannel.NIGHTLY) "Nightly build" else "Stable release"
        return post(
            id = NOTIFICATION_ID,
            title = "Anisu ${update.versionName} is available",
            text = "$channelLabel ready to install · tap to open",
            bigText = update.notes?.take(400)?.takeIf { it.isNotBlank() } ?: "$channelLabel ready to install.",
        )
    }

    /**
     * Posts the same kind of notification on demand, with no update involved. It exists so the user
     * can confirm on their own device that Ansu notifications really arrive, instead of having to
     * wait for a new build to be published.
     */
    fun notifyTest(): Boolean = post(
        id = TEST_NOTIFICATION_ID,
        title = "Anisu notifications are working",
        text = "Real system notification · new builds will arrive like this",
        bigText = "This is a real Android notification, posted by the system through the \"App updates\" " +
            "channel. A new Anisu build is announced the same way, even when the app is closed.",
    )

    /** Removes the "update available" notification once the user is up to date. */
    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    /** Whether the system would deliver a notification right now. */
    fun canNotify(): Boolean = blockedReason() == null

    /**
     * Why a notification would not be delivered, or `null` when nothing is in the way. Phrased for
     * the user: the Updates screen shows it when the test notification cannot be posted.
     */
    fun blockedReason(): String? {
        val enabled = runCatching {
            NotificationManagerCompat.from(context).areNotificationsEnabled()
        }.getOrDefault(false)
        if (!enabled) return "notifications are turned off for Anisu in Android settings"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return "the notification permission has not been granted"
        }
        return null
    }

    @SuppressLint("MissingPermission")
    private fun post(id: Int, title: String, text: String, bigText: String): Boolean {
        val blocked = blockedReason()
        if (blocked != null) {
            diagnostics?.log(LogCategory.UPDATE, "Notification \"$title\" not posted: $blocked")
            return false
        }
        return runCatching {
            ensureChannel()
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle(title)
                .setContentText(text)
                .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(openUpdatesIntent())
                .build()
            NotificationManagerCompat.from(context).notify(id, notification)
            diagnostics?.log(LogCategory.UPDATE, "Posted system notification \"$title\"")
            true
        }.getOrElse { error ->
            diagnostics?.log(LogCategory.UPDATE, "Posting notification \"$title\" failed: ${error.message}")
            false
        }
    }

    /** A tap on the notification opens the app straight on the Updates screen. */
    private fun openUpdatesIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
        }
        return PendingIntent.getActivity(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            "App updates",
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = "Tells you when a new Anisu build is available."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ansu_updates"
        private const val NOTIFICATION_ID = 1001
        private const val TEST_NOTIFICATION_ID = 1003
        private const val REQUEST_CODE = 1002
    }
}
