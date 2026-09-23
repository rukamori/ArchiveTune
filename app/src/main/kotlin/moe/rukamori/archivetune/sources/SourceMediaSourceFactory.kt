package moe.rukamori.archivetune.sources

import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Timeline
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.drm.DrmSessionManagerProvider
import androidx.media3.exoplayer.source.CompositeMediaSource
import androidx.media3.exoplayer.source.ForwardingTimeline
import androidx.media3.exoplayer.source.MediaPeriod
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.upstream.Allocator
import androidx.media3.exoplayer.upstream.LoadErrorHandlingPolicy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.android.asCoroutineDispatcher
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive
import java.io.IOException

class SourceMediaSourceFactory(
    private val delegate: MediaSource.Factory,
    private val resolve: suspend (MediaItem) -> MediaSource,
) : MediaSource.Factory {
    override fun setDrmSessionManagerProvider(provider: DrmSessionManagerProvider): MediaSource.Factory = apply {
        delegate.setDrmSessionManagerProvider(provider)
    }
    override fun setLoadErrorHandlingPolicy(policy: LoadErrorHandlingPolicy): MediaSource.Factory = apply {
        delegate.setLoadErrorHandlingPolicy(policy)
    }
    override fun getSupportedTypes(): IntArray = delegate.supportedTypes
    override fun createMediaSource(mediaItem: MediaItem): MediaSource {
        val scheme = mediaItem.localConfiguration?.uri?.scheme
        return if (scheme in listOf("http", "https", "content", "file", "android.resource")) delegate.createMediaSource(mediaItem)
            else ResolvingSource(mediaItem, resolve)
    }

    private class ResolvingSource(
        private val original: MediaItem,
        private val resolve: suspend (MediaItem) -> MediaSource,
    ) : CompositeMediaSource<Unit>() {
        private var scope: CoroutineScope? = null
        private var child: MediaSource? = null
        private var failure: IOException? = null

        override fun getMediaItem(): MediaItem = original

        override fun prepareSourceInternal(mediaTransferListener: TransferListener?) {
            super.prepareSourceInternal(mediaTransferListener)
            val owner = CoroutineScope(SupervisorJob() + Handler(checkNotNull(Looper.myLooper())).asCoroutineDispatcher())
            scope = owner
            owner.launch {
                try {
                    val source = resolve(original)
                    child = source
                    prepareChildSource(Unit, source)
                } catch (cancelled: CancellationException) {
                    if (owner.isActive) failure = IOException("Source selection was cancelled", cancelled)
                    else throw cancelled
                } catch (error: Exception) {
                    failure = error as? IOException ?: IOException("Unable to resolve audio source", error)
                }
            }
        }

        override fun onChildSourceInfoRefreshed(childSourceId: Unit, mediaSource: MediaSource, newTimeline: Timeline) {
            refreshSourceInfo(object : ForwardingTimeline(newTimeline) {
                override fun getWindow(windowIndex: Int, window: Timeline.Window, defaultPositionProjectionUs: Long): Timeline.Window =
                    super.getWindow(windowIndex, window, defaultPositionProjectionUs).apply { mediaItem = original }
            })
        }

        override fun maybeThrowSourceInfoRefreshError() {
            failure?.let { throw it }
            super.maybeThrowSourceInfoRefreshError()
        }

        override fun createPeriod(id: MediaSource.MediaPeriodId, allocator: Allocator, startPositionUs: Long): MediaPeriod =
            checkNotNull(child).createPeriod(id, allocator, startPositionUs)

        override fun releasePeriod(mediaPeriod: MediaPeriod) = checkNotNull(child).releasePeriod(mediaPeriod)

        override fun releaseSourceInternal() {
            scope?.cancel()
            scope = null
            super.releaseSourceInternal()
            child = null
            failure = null
        }
    }
}
