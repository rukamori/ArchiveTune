/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.media3.common.C
import androidx.media3.common.util.NotificationUtil
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import dagger.hilt.android.AndroidEntryPoint
import moe.rukamori.archivetune.R
import javax.inject.Inject

@AndroidEntryPoint
class ExoDownloadService :
    DownloadService(
        NOTIFICATION_ID,
        1000L,
        CHANNEL_ID,
        R.string.downloading,
        0,
    ) {
    @Inject
    lateinit var downloadUtil: DownloadUtil

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        if (intent?.action == REMOVE_ALL_PENDING_DOWNLOADS) {
            downloadManager.currentDownloads.forEach { download ->
                downloadManager.removeDownload(download.request.id)
            }
        }
        return try {
            super.onStartCommand(intent, flags, startId)
        } catch (_: Exception) {
            START_NOT_STICKY
        }
    }

    override fun getDownloadManager() = downloadUtil.downloadManager

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int,
    ): Notification {
        val activeDownloads = downloads.filter { it.state != Download.STATE_REMOVING }
        val totalPercentage =
            activeDownloads
                .sumOf { download ->
                    if (download.getPercentDownloaded() != C.PERCENTAGE_UNSET.toFloat()) {
                        download.getPercentDownloaded().toDouble()
                    } else {
                        0.0
                    }
                }.toInt()
        val hasKnownProgress = activeDownloads.any { it.getPercentDownloaded() != C.PERCENTAGE_UNSET.toFloat() }
        val contentText =
            if (downloads.size == 1) {
                Util.fromUtf8Bytes(downloads[0].request.data)
            } else {
                resources.getQuantityString(R.plurals.n_song, downloads.size, downloads.size)
            }
        return Notification
            .Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.download)
            .setContentTitle(getString(R.string.downloading))
            .setContentText(contentText)
            .setProgress(
                100 * activeDownloads.size,
                totalPercentage,
                !hasKnownProgress && activeDownloads.isNotEmpty(),
            ).setOngoing(true)
            .setShowWhen(false)
            .addAction(
                Notification.Action
                    .Builder(
                        Icon.createWithResource(this, R.drawable.close),
                        getString(android.R.string.cancel),
                        PendingIntent.getService(
                            this,
                            0,
                            Intent(this, ExoDownloadService::class.java).setAction(REMOVE_ALL_PENDING_DOWNLOADS),
                            PendingIntent.FLAG_IMMUTABLE,
                        ),
                    ).build(),
            ).build()
    }

    /**
     * This helper will outlive the lifespan of a single instance of [ExoDownloadService]
     */
    class TerminalStateNotificationHelper(
        private val context: Context,
        private val notificationHelper: DownloadNotificationHelper,
        private var nextNotificationId: Int,
    ) : DownloadManager.Listener {
        override fun onDownloadChanged(
            downloadManager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            if (download.state == Download.STATE_FAILED) {
                val notification =
                    notificationHelper.buildDownloadFailedNotification(
                        context,
                        R.drawable.error,
                        null,
                        Util.fromUtf8Bytes(download.request.data),
                    )
                NotificationUtil.setNotification(context, nextNotificationId++, notification)
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "download"
        const val NOTIFICATION_ID = 1
        const val JOB_ID = 1
        const val REMOVE_ALL_PENDING_DOWNLOADS = "REMOVE_ALL_PENDING_DOWNLOADS"
    }
}
