/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.net.toUri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import moe.rukamori.archivetune.playback.ExoDownloadService

@Immutable
sealed interface HeaderDownloadState {
    data object None : HeaderDownloadState

    data object Completed : HeaderDownloadState

    @Immutable
    data class Partial(
        val progress: Float,
        val paused: Boolean,
    ) : HeaderDownloadState
}

@Immutable
data class HeaderDownloadItem(
    val id: String,
    val title: String,
)

fun headerDownloadState(
    songIds: List<String>,
    downloads: Map<String, Download>,
): HeaderDownloadState {
    if (songIds.isEmpty()) return HeaderDownloadState.None

    var completedCount = 0
    var progressTotal = 0f
    var hasAnyDownload = false
    var hasRunningDownload = false
    var hasPausedDownload = false

    val distinctSongIds = songIds.distinct()

    distinctSongIds.forEach { songId ->
        val download = downloads[songId]
        when (download?.state) {
            Download.STATE_COMPLETED -> {
                completedCount++
                progressTotal += 1f
                hasAnyDownload = true
            }

            Download.STATE_QUEUED,
            Download.STATE_DOWNLOADING,
            Download.STATE_RESTARTING,
            -> {
                val progress =
                    download.percentDownloaded
                        .takeIf { it >= 0f }
                        ?.div(100f)
                        ?: 0f
                progressTotal += progress.coerceIn(0f, 1f)
                hasAnyDownload = true
                hasRunningDownload = true
            }

            Download.STATE_STOPPED -> {
                if (download.stopReason != DOWNLOAD_STOP_REASON_NONE) {
                    val progress =
                        download.percentDownloaded
                            .takeIf { it >= 0f }
                            ?.div(100f)
                            ?: 0f
                    progressTotal += progress.coerceIn(0f, 1f)
                    hasAnyDownload = true
                    hasPausedDownload =
                        hasPausedDownload || download.stopReason == COLLECTION_PAUSE_STOP_REASON
                }
            }
        }
    }

    val distinctCount = distinctSongIds.size
    return when {
        completedCount == distinctCount -> {
            HeaderDownloadState.Completed
        }

        hasAnyDownload -> {
            HeaderDownloadState.Partial(
                progress = (progressTotal / distinctCount).coerceIn(0f, 1f),
                paused = hasPausedDownload && !hasRunningDownload,
            )
        }

        else -> {
            HeaderDownloadState.None
        }
    }
}

fun sendAddMissingDownloads(
    context: Context,
    songs: List<HeaderDownloadItem>,
    downloads: Map<String, Download>,
) {
    songs
        .distinctBy { it.id }
        .filter { item -> downloads[item.id]?.state.shouldRequestDownload() }
        .forEach { item ->
            val downloadRequest =
                DownloadRequest
                    .Builder(item.id, item.id.toUri())
                    .setCustomCacheKey(item.id)
                    .setData(item.title.toByteArray())
                    .build()
            DownloadService.sendAddDownload(
                context,
                ExoDownloadService::class.java,
                downloadRequest,
                false,
            )
        }
}

fun sendRemoveDownloads(
    context: Context,
    songIds: List<String>,
) {
    songIds.distinct().forEach { songId ->
        DownloadService.sendRemoveDownload(
            context,
            ExoDownloadService::class.java,
            songId,
            false,
        )
    }
}

fun sendPauseDownloads(
    context: Context,
    songIds: List<String>,
) {
    songIds.distinct().forEach { songId ->
        DownloadService.sendSetStopReason(
            context,
            ExoDownloadService::class.java,
            songId,
            COLLECTION_PAUSE_STOP_REASON,
            false,
        )
    }
}

fun sendResumeDownloads(
    context: Context,
    songIds: List<String>,
) {
    songIds.distinct().forEach { songId ->
        DownloadService.sendSetStopReason(
            context,
            ExoDownloadService::class.java,
            songId,
            DOWNLOAD_STOP_REASON_NONE,
            false,
        )
    }
}

fun hasActiveDownloads(
    songIds: List<String>,
    downloads: Map<String, Download>,
): Boolean =
    songIds.distinct().any { songId ->
        when (downloads[songId]?.state) {
            Download.STATE_QUEUED,
            Download.STATE_DOWNLOADING,
            Download.STATE_RESTARTING,
            -> true

            Download.STATE_STOPPED -> downloads[songId]?.stopReason != DOWNLOAD_STOP_REASON_NONE

            else -> false
        }
    }

private fun Int?.shouldRequestDownload(): Boolean =
    when (this) {
        Download.STATE_COMPLETED,
        Download.STATE_QUEUED,
        Download.STATE_DOWNLOADING,
        Download.STATE_RESTARTING,
        -> false

        else -> true
    }

private const val DOWNLOAD_STOP_REASON_NONE = 0
private const val COLLECTION_PAUSE_STOP_REASON = 1
