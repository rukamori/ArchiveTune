/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import android.os.Looper
import androidx.annotation.WorkerThread
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.guava.future
import moe.rukamori.archivetune.utils.YTPlayerUtils
import timber.log.Timber
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.coroutineContext

@Singleton
class ResolveAudioStreamUseCase
    @Inject
    constructor(
        private val youtubeiRepository: YoutubeiStreamRepository,
    ) {
        private data class CacheKey(
            val mediaId: String,
            val quality: String,
            val networkMetered: Boolean,
            val purpose: StreamPurpose,
            val authFingerprint: String,
            val pinnedFormatId: Int?,
            val requiresSongMetadata: Boolean,
        )

        private data class InFlightKey(
            val cacheKey: CacheKey,
            val priority: StreamResolutionPriority,
        )

        private enum class ResolutionConsumer {
            PLAYBACK,
            PRELOAD,
        }

        private class InFlightResolution(
            val deferred: Deferred<ResolvedAudioStream>,
            var playbackOwners: Int = 0,
            var preloadOwners: Int = 0,
        )

        private sealed interface ResolutionLease {
            data class Cached(val stream: ResolvedAudioStream) : ResolutionLease

            data class Active(
                val key: InFlightKey,
                val resolution: InFlightResolution,
            ) : ResolutionLease
        }

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cache = ConcurrentHashMap<CacheKey, ResolvedAudioStream>()
        private val inFlightLock = Any()
        private val inFlight = mutableMapOf<InFlightKey, InFlightResolution>()

        suspend operator fun invoke(request: AudioStreamRequest): ResolvedAudioStream =
            resolve(request, ResolutionConsumer.PLAYBACK)

        suspend fun preload(request: AudioStreamRequest) {
            resolve(request, ResolutionConsumer.PRELOAD)
        }

        private suspend fun resolve(
            request: AudioStreamRequest,
            consumer: ResolutionConsumer,
        ): ResolvedAudioStream {
            val key = request.cacheKey()
            val priority = request.resolutionPriority(consumer)
            val lease = acquireResolution(key, request, consumer, priority)
            if (lease is ResolutionLease.Cached) return lease.stream

            val activeLease = lease as ResolutionLease.Active
            val resolution = activeLease.resolution
            return try {
                resolution.deferred.start()
                resolution.deferred.await()
            } finally {
                releaseResolution(activeLease.key, resolution, consumer)
            }
        }

        private fun acquireResolution(
            key: CacheKey,
            request: AudioStreamRequest,
            consumer: ResolutionConsumer,
            priority: StreamResolutionPriority,
        ): ResolutionLease =
            synchronized(inFlightLock) {
                cache[key]?.let { cached ->
                    if (isFresh(cached)) return@synchronized ResolutionLease.Cached(cached)
                    cache.remove(key, cached)
                }

                val requestedInFlightKey = InFlightKey(key, priority)
                val reusableInFlightKey =
                    if (priority == StreamResolutionPriority.BACKGROUND) {
                        InFlightKey(key, StreamResolutionPriority.FOREGROUND)
                            .takeIf(inFlight::containsKey)
                            ?: requestedInFlightKey
                    } else {
                        requestedInFlightKey
                    }
                inFlight[reusableInFlightKey]?.let { resolution ->
                    resolution.addOwner(consumer)
                    return@synchronized ResolutionLease.Active(reusableInFlightKey, resolution)
                }

                lateinit var resolution: InFlightResolution
                val deferred =
                    scope.async(start = CoroutineStart.LAZY) {
                        val resolved =
                            resolveUncached(
                                request = request,
                                priority = request.resolutionPriority(consumer),
                            )
                        val resolutionContext = coroutineContext
                        resolutionContext.ensureActive()
                        synchronized(inFlightLock) {
                            resolutionContext.ensureActive()
                            if (
                                inFlight[requestedInFlightKey] !== resolution ||
                                !resolution.hasOwners()
                            ) {
                                throw CancellationException(
                                    "Audio stream resolution no longer has active consumers",
                                )
                            }
                            storeResolvedStream(key, resolved)
                        }
                        resolved
                    }

                resolution = InFlightResolution(deferred)
                resolution.addOwner(consumer)
                deferred.invokeOnCompletion {
                    synchronized(inFlightLock) {
                        if (inFlight[requestedInFlightKey] === resolution) {
                            inFlight.remove(requestedInFlightKey)
                        }
                    }
                }
                inFlight[requestedInFlightKey] = resolution
                ResolutionLease.Active(requestedInFlightKey, resolution)
            }

        private fun releaseResolution(
            key: InFlightKey,
            resolution: InFlightResolution,
            consumer: ResolutionConsumer,
        ) {
            val deferredToCancel =
                synchronized(inFlightLock) {
                    resolution.removeOwner(consumer)
                    if (
                        resolution.playbackOwners == 0 &&
                        resolution.preloadOwners == 0 &&
                        !resolution.deferred.isCompleted &&
                        inFlight[key] === resolution
                    ) {
                        inFlight.remove(key)
                        resolution.deferred
                    } else {
                        null
                    }
                }
            deferredToCancel?.cancel()
        }

        private fun InFlightResolution.addOwner(consumer: ResolutionConsumer) {
            when (consumer) {
                ResolutionConsumer.PLAYBACK -> playbackOwners += 1
                ResolutionConsumer.PRELOAD -> preloadOwners += 1
            }
        }

        private fun InFlightResolution.hasOwners(): Boolean =
            playbackOwners > 0 || preloadOwners > 0

        private fun InFlightResolution.removeOwner(consumer: ResolutionConsumer) {
            when (consumer) {
                ResolutionConsumer.PLAYBACK -> playbackOwners -= 1
                ResolutionConsumer.PRELOAD -> preloadOwners -= 1
            }
        }

        @WorkerThread
        fun resolveBlocking(request: AudioStreamRequest): ResolvedAudioStream {
            check(Looper.myLooper() != Looper.getMainLooper())
            val timeoutSeconds =
                if (request.purpose == StreamPurpose.DOWNLOAD) {
                    DOWNLOAD_RESOLUTION_TIMEOUT_SECONDS
                } else {
                    PLAYBACK_RESOLUTION_TIMEOUT_SECONDS
                }
            val future = scope.future { invoke(request) }
            return try {
                future.get(timeoutSeconds, TimeUnit.SECONDS)
            } catch (throwable: TimeoutException) {
                future.cancel(true)
                throw SocketTimeoutException(
                    "Audio stream resolution timed out after $timeoutSeconds seconds",
                ).apply { initCause(throwable) }
            } catch (throwable: ExecutionException) {
                future.cancel(true)
                throw throwable.cause ?: throwable
            } catch (throwable: Throwable) {
                future.cancel(true)
                throw throwable
            }
        }

        fun invalidate(mediaId: String) {
            val deferredsToCancel =
                synchronized(inFlightLock) {
                    cache.keys.removeIf { it.mediaId == mediaId }
                    inFlight.keys
                        .filter { it.cacheKey.mediaId == mediaId }
                        .mapNotNull { inFlight.remove(it)?.deferred }
                }
            deferredsToCancel.forEach { it.cancel() }
        }

        fun invalidateUrl(url: String) {
            cache.entries.removeIf { it.value.url == url }
        }

        fun peek(request: AudioStreamRequest): ResolvedAudioStream? {
            val key = request.cacheKey()
            val resolved = cache[key] ?: return null
            if (isFresh(resolved)) return resolved
            cache.remove(key, resolved)
            return null
        }

        fun clear() {
            val deferredsToCancel =
                synchronized(inFlightLock) {
                    cache.clear()
                    inFlight.values.map(InFlightResolution::deferred).also { inFlight.clear() }
                }
            deferredsToCancel.forEach { it.cancel() }
        }

        private suspend fun resolveUncached(
            request: AudioStreamRequest,
            priority: StreamResolutionPriority,
        ): ResolvedAudioStream {
            val resolvedAuthState =
                if (request.authState.hasLoginCookie) {
                    YTPlayerUtils.ensureYoutubeiPoTokensForPlayback(
                        videoId = request.mediaId,
                        authState = request.authState,
                    )
                } else {
                    request.authState
                }
            return youtubeiRepository.resolve(
                request = request.copy(authState = resolvedAuthState),
                priority = priority,
            )
        }

        private fun AudioStreamRequest.resolutionPriority(
            consumer: ResolutionConsumer,
        ): StreamResolutionPriority =
            if (consumer == ResolutionConsumer.PLAYBACK && purpose == StreamPurpose.PLAYBACK) {
                StreamResolutionPriority.FOREGROUND
            } else {
                StreamResolutionPriority.BACKGROUND
            }

        private fun AudioStreamRequest.cacheKey(): CacheKey =
            CacheKey(
                mediaId = mediaId,
                quality = quality.name,
                networkMetered = networkMetered,
                purpose = purpose,
                authFingerprint = authState.streamCacheFingerprint,
                pinnedFormatId = pinnedFormatId,
                requiresSongMetadata = requiresSongMetadata,
            )

        private fun storeResolvedStream(
            key: CacheKey,
            resolved: ResolvedAudioStream,
        ) {
            putResolvedStream(key, resolved)
            if (resolved.source == StreamSource.YOUTUBEI) {
                val alternatePurpose =
                    when (key.purpose) {
                        StreamPurpose.PLAYBACK -> StreamPurpose.DOWNLOAD
                        StreamPurpose.DOWNLOAD -> StreamPurpose.PLAYBACK
                    }
                putResolvedStream(
                    key.copy(
                        purpose = alternatePurpose,
                        requiresSongMetadata = false,
                    ),
                    resolved,
                )
            }
            Timber.tag(TAG).d(
                "Resolved %s via %s (%s)",
                key.mediaId,
                resolved.source,
                resolved.runtimeVersion ?: "native",
            )
            if (cache.size <= MAX_CACHE_ENTRIES) return
            cache.entries.removeIf { !isFresh(it.value) }
            val excess = cache.size - MAX_CACHE_ENTRIES
            if (excess <= 0) return
            cache.entries
                .sortedBy { it.value.expiresAtMs }
                .take(excess)
                .forEach { entry -> cache.remove(entry.key, entry.value) }
        }

        private fun putResolvedStream(
            key: CacheKey,
            resolved: ResolvedAudioStream,
        ) {
            cache[key] = resolved
            cache[
                key.copy(
                    authFingerprint = resolved.authFingerprint,
                ),
            ] = resolved
        }

        private fun isFresh(stream: ResolvedAudioStream): Boolean =
            stream.expiresAtMs > System.currentTimeMillis() + STREAM_EXPIRY_SAFETY_MS

        private companion object {
            const val TAG = "AudioStreamResolver"
            const val STREAM_EXPIRY_SAFETY_MS = 60_000L
            const val MAX_CACHE_ENTRIES = 256
            const val PLAYBACK_RESOLUTION_TIMEOUT_SECONDS = 30L
            const val DOWNLOAD_RESOLUTION_TIMEOUT_SECONDS = 180L
        }
    }
