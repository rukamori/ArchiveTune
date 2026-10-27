/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.modules.ModuleAudioSelection
import moe.rukamori.archivetune.modules.ModuleDownloadRepository
import moe.rukamori.archivetune.modules.ModuleMediaStorage
import moe.rukamori.archivetune.modules.ResolveModuleAudioUseCase
import moe.rukamori.archivetune.modules.moduleMatchMetadata
import java.io.IOException

internal class ModuleMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val resolver: ResolveModuleAudioUseCase,
    private val bindings: ModuleDownloadRepository,
    private val storage: ModuleMediaStorage,
    private val completedDownload: (String) -> Boolean,
    private val selected: (String, ModuleAudioSelection?) -> Unit,
) : MediaSource.Factory by delegate {
    override fun setDrmSessionManagerProvider(drmSessionManagerProvider: DrmSessionManagerProvider): MediaSource.Factory {
        delegate.setDrmSessionManagerProvider(drmSessionManagerProvider)
        return this
    }

    override fun setLoadErrorHandlingPolicy(loadErrorHandlingPolicy: LoadErrorHandlingPolicy): MediaSource.Factory {
        delegate.setLoadErrorHandlingPolicy(loadErrorHandlingPolicy)
        return this
    }

    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val configuration = mediaItem.localConfiguration ?: return delegate.createMediaSource(mediaItem)
        if (configuration.uri.scheme in listOf("content", "file", "android.resource", "http", "https") ||
            mediaItem.metadata?.let { !it.moduleMatchMetadata().eligible } == true) return delegate.createMediaSource(mediaItem)
        return ModuleMediaSource(mediaItem, delegate, resolver, bindings, storage, completedDownload, selected)
    }
}

private class ModuleMediaSource(
    private val item: MediaItem,
    private val factory: MediaSource.Factory,
    private val resolver: ResolveModuleAudioUseCase,
    private val bindings: ModuleDownloadRepository,
    private val storage: ModuleMediaStorage,
    private val completedDownload: (String) -> Boolean,
    private val selected: (String, ModuleAudioSelection?) -> Unit,
) : CompositeMediaSource<Unit>() {
    private var scope: CoroutineScope? = null
    private var child: MediaSource? = null
    private var failure: IOException? = null

    override fun getMediaItem(): MediaItem = item

    override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
        super.prepareSourceInternal(mediaTransferListener)
        val owner = CoroutineScope(SupervisorJob() + Handler(checkNotNull(Looper.myLooper())).asCoroutineDispatcher())
        scope = owner
        owner.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    val completed = completedDownload(item.mediaId)
                    val download = if (completed) bindings.find(item.mediaId) else null
                    val audio = download ?: if (completed) null else resolver.resolve(item.mediaId, item.metadata?.moduleMatchMetadata())
                    if (audio != null) storage.record(audio)
                    audio to (download != null)
                }
                selected(item.mediaId, result.first)
                val source = result.first?.let { storage.mediaSource(item, it, result.second) } ?: factory.createMediaSource(item)
                child = source
                prepareChildSource(Unit, source)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { failure = IOException("Audio source could not be prepared", error) }
        }
    }

    override fun maybeThrowSourceInfoRefreshError() {
        failure?.let { throw it }
        super.maybeThrowSourceInfoRefreshError()
    }

    override fun onChildSourceInfoRefreshed(childSourceId: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
        refreshSourceInfo(object : ForwardingTimeline(newTimeline) {
            override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window {
                return super.getWindow(windowIndex, window, defaultPositionProjectionUs).apply { mediaItem = item }
            }
        })
    }

    override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
        checkNotNull(child).createPeriod(id, allocator, startPositionUs)

    override fun releasePeriod(mediaPeriod: MediaPeriod) { child?.releasePeriod(mediaPeriod) }

    override fun releaseSourceInternal() {
        scope?.cancel()
        scope = null
        super.releaseSourceInternal()
        child = null
        failure = null
    }
}
