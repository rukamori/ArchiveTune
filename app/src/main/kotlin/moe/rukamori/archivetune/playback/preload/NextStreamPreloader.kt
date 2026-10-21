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
import java.util.concurrent.atomic.AtomicBoolean
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
        private data class Target(val request: AudioStreamRequest)

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val lock = Any()

        private val jobs = mutableMapOf<Target, Job>()
        private val completedTargets = mutableSetOf<Target>()

        fun updateTargets(requests: List<AudioStreamRequest>) {
            val targets = requests.map(::Target).toSet()
            val jobsToStart =
                synchronized(lock) {
                    jobs.keys.filterNot(targets::contains).toList().forEach { target ->
                        jobs.remove(target)?.cancel()
                    }
                    completedTargets.retainAll(targets)

                    targets.mapNotNull { target ->
                        if (target in jobs || target in completedTargets) {
                            null
                        } else {
                            val preloadSucceeded = AtomicBoolean()
                            val job =
                                scope.launch(start = CoroutineStart.LAZY) {
                                    try {
                                        preload(target)
                                        preloadSucceeded.set(true)
                                    } catch (cancellation: CancellationException) {
                                        throw cancellation
                                    } catch (throwable: Throwable) {
                                        Timber.tag(LOG_TAG).w(
                                            throwable,
                                            "Failed to preload resolved stream for %s",
                                            target.request.mediaId,
                                        )
                                    }
                                }
                            jobs[target] = job
                            job.invokeOnCompletion { cause ->
                                synchronized(lock) {
                                    if (jobs[target] === job) {
                                        jobs.remove(target)
                                        if (cause == null && preloadSucceeded.get()) {
                                            completedTargets.add(target)
                                        }
                                    }
                                }
                            }
                            job
                        }
                    }
                }

            jobsToStart.forEach(Job::start)
        }

        fun cancel() {
            val jobsToCancel =
                synchronized(lock) {
                    jobs.values.toList().also {
                        jobs.clear()
                        completedTargets.clear()
                    }
                }
            jobsToCancel.forEach(Job::cancel)
        }

        private suspend fun preload(target: Target) {
            if (resolveAudioStream.peek(target.request) != null) return

            val cacheKey = target.request.mediaId
            if (playerCache.isFullyCached(cacheKey) || downloadCache.isFullyCached(cacheKey)) return

            resolveAudioStream.preload(target.request)
        }

        private fun Cache.isFullyCached(key: String): Boolean {
            val contentLength = getContentMetadata(key).get(ContentMetadata.KEY_CONTENT_LENGTH, -1L)
            return contentLength > 0L && isCached(key, 0L, contentLength)
        }

        private companion object {
            const val LOG_TAG = "AudioStreamResolver"
        }
    }
