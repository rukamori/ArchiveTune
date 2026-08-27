/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.preload

import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.ContentMetadata
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.di.DownloadCache
import moe.rukamori.archivetune.di.PlayerCache
import moe.rukamori.archivetune.playback.stream.AudioStreamRequest
import moe.rukamori.archivetune.playback.stream.ResolveAudioStreamUseCase
import timber.log.Timber
import java.util.concurrent.atomic.AtomicReference
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NextStreamPreloader
    @Inject
    constructor(
        private val resolveAudioStream: ResolveAudioStreamUseCase,
        @PlayerCache private val playerCache: Cache,
        @DownloadCache private val downloadCache: Cache,
    ) {
        private data class Target(
            val request: AudioStreamRequest,
            val runtimeRevision: String,
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()

        private var target: Target? = null
        private var job: Job? = null

        fun updateTarget(
            request: AudioStreamRequest,
            runtimeRevision: String,
        ) {
            val nextTarget = Target(request, runtimeRevision)
            lateinit var preloadFailure: AtomicReference<Throwable?>
            val jobToStart =
                synchronized(lock) {
                    if (target == nextTarget) return

                    val previousJob = job
                    preloadFailure = AtomicReference()
                    val nextJob =
                        scope.launch(start = CoroutineStart.LAZY) {
                            try {
                                preload(nextTarget)
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (throwable: Throwable) {
                                Timber.tag(LOG_TAG).w(
                                    throwable,
                                    "Failed to preload resolved stream for %s",
                                    request.mediaId,
                                )
                                preloadFailure.set(throwable)
                            }
                        }

                    target = nextTarget
                    job = nextJob
                    previousJob?.cancel()
                    nextJob
                }

            jobToStart.invokeOnCompletion { cause ->
                complete(nextTarget, jobToStart, preloadFailure.get() ?: cause)
            }
            jobToStart.start()
        }

        fun cancel() {
            val jobToCancel =
                synchronized(lock) {
                    target = null
                    job.also { job = null }
                }
            jobToCancel?.cancel()
        }

        private suspend fun preload(target: Target) {
            if (resolveAudioStream.peek(target.request) != null) return

            val cacheKey = target.request.mediaId
            if (playerCache.isFullyCached(cacheKey) || downloadCache.isFullyCached(cacheKey)) return

            resolveAudioStream.preload(target.request)
        }

        private fun complete(
            completedTarget: Target,
            completedJob: Job,
            cause: Throwable?,
        ) {
            synchronized(lock) {
                if (target != completedTarget || job !== completedJob) return

                job = null
                if (cause != null && cause !is CancellationException) {
                    target = null
                }
            }
        }

        private fun Cache.isFullyCached(key: String): Boolean {
            val contentLength = getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            return contentLength > 0L && isCached(key, 0L, contentLength)
        }

        private companion object {
            const val LOG_TAG = "AudioStreamResolver"
        }
    }
