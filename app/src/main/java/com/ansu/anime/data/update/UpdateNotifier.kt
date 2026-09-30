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

/**
 * Posts the native Android notification that tells the user a new Ansu build is ready. Tapping it
 * opens the app straight on the Updates screen. The channel is created lazily on Android 8+, and on
 * Android 13+ the notification is only shown when the runtime permission was granted, so nothing is
 * ever posted the user did not allow.
 */
class UpdateNotifier(private val context: Context) {

    @SuppressLint("MissingPermission")
    fun notifyUpdate(update: AvailableUpdate) {
        if (!canNotify()) return
        runCatching {
            ensureChannel()
            val manager = NotificationManagerCompat.from(context)
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra(MainActivity.EXTRA_OPEN_UPDATES, true)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                REQUEST_CODE,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
            val channelLabel = if (update.channel == UpdateChannel.NIGHTLY) "Nightly build" else "Stable release"
            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("Ansu ${update.versionName} is available")
                .setContentText("$channelLabel ready to install · tap to open")
                .setStyle(
                    NotificationCompat.BigTextStyle().bigText(
                        update.notes?.take(400)?.takeIf { it.isNotBlank() } ?: "$channelLabel ready to install.",
                    ),
                )
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(pendingIntent)
                .build()
            manager.notify(NOTIFICATION_ID, notification)
        }
    }

    /** Removes the notification once the user is up to date. */
    fun cancel() {
        runCatching { NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID) }
    }

    private fun canNotify(): Boolean {
        val enabled = runCatching { NotificationManagerCompat.from(context).areNotificationsEnabled() }.getOrDefault(false)
        if (!enabled) return false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        }
        return true
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
            description = "Tells you when a new Ansu build is available."
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        const val CHANNEL_ID = "ansu_updates"
        private const val NOTIFICATION_ID = 1001
        private const val REQUEST_CODE = 1002
    }
}
