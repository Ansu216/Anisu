package com.ansu.anime.data.download

import android.app.Notification
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.ansu.anime.AnsuApp
import com.ansu.anime.R

/**
 * Keeps episode downloads running while the app is in the background, with a progress notification
 * ([DownloadNotifications]) naming the episode and showing its percent and size.
 * The downloads themselves live in [DownloadsRepository.downloadManager]; this service only hosts them.
 */
@OptIn(UnstableApi::class)
class AnisuDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    CHANNEL_ID,
    R.string.download_channel_name,
    R.string.download_channel_description,
) {
    override fun getDownloadManager(): DownloadManager =
        (application as AnsuApp).container.downloads.downloadManager

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification {
        val repository = (application as AnsuApp).container.downloads
        val active = downloads.filter { it.state == Download.STATE_DOWNLOADING || it.state == Download.STATE_QUEUED || it.state == Download.STATE_RESTARTING }
        val current = active.firstOrNull { it.state == Download.STATE_DOWNLOADING } ?: active.firstOrNull()
        val (speed, eta) = measure(current)
        return DownloadNotifications.running(
            context = this,
            current = current?.let { repository.entryFor(it.request.id) },
            total = active.size,
            percent = current?.percentDownloaded ?: -1f,
            bytes = current?.bytesDownloaded ?: 0L,
            speed = speed,
            etaSeconds = eta,
            waitingForNetwork = notMetRequirements != 0,
        )
    }

    // Speed is what was saved since the last notification, smoothed so the number does not jump about.
    private var measuredId: String? = null
    private var measuredBytes = 0L
    private var measuredAt = 0L
    private var smoothedSpeed = 0.0

    /** Bytes per second and seconds left (negative when unknown) for [current], from the change since the last call. */
    private fun measure(current: Download?): Pair<Long, Long> {
        if (current == null || current.state != Download.STATE_DOWNLOADING) {
            measuredId = null
            return 0L to -1L
        }
        val now = android.os.SystemClock.elapsedRealtime()
        val bytes = current.bytesDownloaded
        if (measuredId != current.request.id || bytes < measuredBytes) {
            measuredId = current.request.id
            smoothedSpeed = 0.0
        } else if (now > measuredAt) {
            val instant = (bytes - measuredBytes) * 1000.0 / (now - measuredAt)
            smoothedSpeed = if (smoothedSpeed == 0.0) instant else smoothedSpeed * 0.7 + instant * 0.3
        }
        measuredBytes = bytes
        measuredAt = now

        val speed = smoothedSpeed.toLong()
        val percent = current.percentDownloaded
        val eta = if (speed > 0L && percent >= 1f && bytes > 0L) {
            val remaining = (bytes * 100.0 / percent) - bytes
            (remaining / speed).toLong().coerceAtLeast(0L)
        } else {
            -1L
        }
        return speed to eta
    }

    // The notification's Cancel button: removes that download the same way the Downloads screen's bin does.
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == DownloadNotifications.ACTION_CANCEL) {
            intent.getStringExtra(DownloadNotifications.EXTRA_ID)?.let { id ->
                (application as AnsuApp).container.downloads.remove(id)
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    // Finished and failed episodes get their own notification: the running one goes away with the service.
    private val resultListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            val repository = (application as AnsuApp).container.downloads
            val entry = repository.entryFor(download.request.id) ?: return
            when (download.state) {
                Download.STATE_COMPLETED -> DownloadNotifications.completed(this@AnisuDownloadService, entry)
                // A failure that is about to be retried automatically is not worth a notification yet.
                Download.STATE_FAILED -> if (!repository.isRetrying(download.request.id)) {
                    DownloadNotifications.failed(this@AnisuDownloadService, entry, repository.describeFailure(finalException) ?: finalException?.message)
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        getDownloadManager().addListener(resultListener)
    }

    override fun onDestroy() {
        getDownloadManager().removeListener(resultListener)
        super.onDestroy()
    }

    private companion object {
        const val NOTIFICATION_ID = 7421
        const val CHANNEL_ID = DownloadNotifications.CHANNEL_RUNNING
    }
}
