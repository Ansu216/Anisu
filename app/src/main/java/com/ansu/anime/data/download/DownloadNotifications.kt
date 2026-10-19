package com.ansu.anime.data.download

import android.Manifest
import android.app.Notification
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
 * Every notification an episode download shows: the running one (episode name, percent, size, a real progress
 * bar), a short "Preparing" one while the stream is still being found, and a result when it finishes or fails.
 * The running one is the service's foreground notification; the others are posted under their own tag.
 */
object DownloadNotifications {
    /** Channel of the running notification; the same id the download service declares, so it is one channel. */
    const val CHANNEL_RUNNING = "anisu_downloads"

    /** Channel of the "finished" and "failed" results, which deserve a sound unlike a progress bar. */
    private const val CHANNEL_RESULT = "anisu_downloads_result"

    private const val TAG_PREPARING = "download-preparing"
    private const val TAG_RESULT = "download-result"

    private fun episodeText(entry: DownloadEntry): String {
        val number = if (entry.episodeNumber % 1f == 0f) entry.episodeNumber.toInt().toString() else entry.episodeNumber.toString()
        return "${entry.animeTitle} \u00b7 E$number"
    }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_RUNNING) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RUNNING, context.getString(R.string.download_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                    description = context.getString(R.string.download_channel_description)
                },
            )
        }
        if (manager.getNotificationChannel(CHANNEL_RESULT) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_RESULT, "Download results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Tells you when an episode has finished downloading or could not be downloaded."
                },
            )
        }
    }

    private fun canNotify(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    /**
     * The running notification. [current] is the episode being downloaded (or the next queued one), [total] how many
     * downloads are active or waiting, [percent] its progress 0-100 or negative when unknown, [bytes] what is saved.
     */
    fun running(
        context: Context,
        current: DownloadEntry?,
        total: Int,
        percent: Float,
        bytes: Long,
        waitingForNetwork: Boolean,
    ): Notification {
        ensureChannels(context)
        val title = current?.let { episodeText(it) } ?: "Downloading"
        val shown = if (percent >= 0f) percent.coerceIn(0f, 100f) else -1f
        val text = when {
            waitingForNetwork -> "Waiting for a connection\u2026"
            shown >= 0f -> "${shown.toInt()}% \u00b7 ${formatSize(bytes)}"
            bytes > 0L -> "Downloading \u00b7 ${formatSize(bytes)}"
            else -> "Starting\u2026"
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setProgress(100, shown.toInt().coerceAtLeast(0), shown < 0f)
        if (total > 1) builder.setSubText("$total episodes in the queue")
        return builder.build()
    }

    /** "Preparing download": shown while the app is still finding the stream, which can take a while. */
    fun preparing(context: Context, entry: DownloadEntry) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_RUNNING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(episodeText(entry))
            .setContentText("Preparing download\u2026")
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setProgress(0, 0, true)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(TAG_PREPARING, entry.id.hashCode(), notification) }
    }

    /** Takes the "Preparing" notification away: the download now shows its own, or it failed. */
    fun clearPreparing(context: Context, id: String) {
        runCatching { NotificationManagerCompat.from(context).cancel(TAG_PREPARING, id.hashCode()) }
    }

    fun completed(context: Context, entry: DownloadEntry) = result(context, entry, "Download complete")

    fun failed(context: Context, entry: DownloadEntry, reason: String?) =
        result(context, entry, reason?.takeIf { it.isNotBlank() }?.let { "Download failed: $it" } ?: "Download failed")

    private fun result(context: Context, entry: DownloadEntry, text: String) {
        if (!canNotify(context)) return
        ensureChannels(context)
        val notification = NotificationCompat.Builder(context, CHANNEL_RESULT)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(episodeText(entry))
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(TAG_RESULT, entry.id.hashCode(), notification) }
    }

    private fun formatSize(bytes: Long): String = when {
        bytes >= 1_000_000_000L -> String.format(java.util.Locale.US, "%.2f GB", bytes / 1_000_000_000.0)
        bytes >= 1_000_000L -> String.format(java.util.Locale.US, "%.0f MB", bytes / 1_000_000.0)
        else -> String.format(java.util.Locale.US, "%.0f KB", bytes / 1_000.0)
    }
}
