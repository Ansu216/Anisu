package com.ansu.anime.data.download

import android.app.Notification
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
        return DownloadNotifications.running(
            context = this,
            current = current?.let { repository.entryFor(it.request.id) },
            total = active.size,
            percent = current?.percentDownloaded ?: -1f,
            bytes = current?.bytesDownloaded ?: 0L,
            waitingForNetwork = notMetRequirements != 0,
        )
    }

    // Finished and failed episodes get their own notification: the running one goes away with the service.
    private val resultListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(downloadManager: DownloadManager, download: Download, finalException: Exception?) {
            val entry = (application as AnsuApp).container.downloads.entryFor(download.request.id) ?: return
            when (download.state) {
                Download.STATE_COMPLETED -> DownloadNotifications.completed(this@AnisuDownloadService, entry)
                Download.STATE_FAILED -> DownloadNotifications.failed(this@AnisuDownloadService, entry, finalException?.message)
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
