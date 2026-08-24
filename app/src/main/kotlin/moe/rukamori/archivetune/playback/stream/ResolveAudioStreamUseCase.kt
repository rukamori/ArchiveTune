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
import kotlinx.coroutines.guava.future
import moe.rukamori.archivetune.morideobfuscator.ytdlp.YtDlpRuntimeStore
import moe.rukamori.archivetune.utils.YTPlayerUtils
import timber.log.Timber
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ResolveAudioStreamUseCase
    @Inject
    constructor(
        private val ytDlpRepository: YtDlpStreamRepository,
        private val nativeRepository: NativeStreamRepository,
    ) {
        private data class CacheKey(
            val mediaId: String,
            val quality: String,
            val networkMetered: Boolean,
            val purpose: StreamPurpose,
            val authFingerprint: String,
            val pinnedFormatId: Int?,
            val runtimeRevision: String,
        )

        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        private val cache = ConcurrentHashMap<CacheKey, ResolvedAudioStream>()
        private val inFlight = ConcurrentHashMap<CacheKey, Deferred<ResolvedAudioStream>>()

        suspend operator fun invoke(request: AudioStreamRequest): ResolvedAudioStream {
            val key = request.cacheKey()
            cache[key]?.takeIf(::isFresh)?.let { return it }
            cache.remove(key)

            val candidate =
                scope.async(start = CoroutineStart.LAZY) {
                    resolveUncached(request).also { resolved ->
                        storeResolvedStream(key, resolved)
                    }
                }
            val active = inFlight.putIfAbsent(key, candidate)
            if (active == null) {
                candidate.invokeOnCompletion { inFlight.remove(key, candidate) }
                candidate.start()
                return candidate.await()
            }
            candidate.cancel()
            return active.await()
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
            cache.keys.removeIf { it.mediaId == mediaId }
            inFlight.entries.forEach { (key, resolution) ->
                if (key.mediaId == mediaId && inFlight.remove(key, resolution)) {
                    resolution.cancel()
                }
            }
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
            cache.clear()
            inFlight.values.forEach { it.cancel() }
            inFlight.clear()
        }

        private suspend fun resolveUncached(request: AudioStreamRequest): ResolvedAudioStream {
            val ytDlpFailure =
                try {
                    val resolvedAuthState =
                        if (request.authState.hasLoginCookie) {
                            YTPlayerUtils.ensureYtDlpPoTokensForPlayback(
                                videoId = request.mediaId,
                                authState = request.authState,
                            )
                        } else {
                            request.authState
                        }
                    return ytDlpRepository.resolve(request.copy(authState = resolvedAuthState))
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (loginRequired: YTPlayerUtils.LoginRequiredForPlaybackException) {
                    throw loginRequired
                } catch (invalidLogin: YTPlayerUtils.InvalidPlaybackLoginContextException) {
                    throw invalidLogin
                } catch (ytDlpFailure: YtDlpExtractionException) {
                    throw ytDlpFailure
                } catch (throwable: Throwable) {
                    Timber.tag(TAG).w(
                        throwable,
                        "Local yt-dlp resolution failed for %s; using native fallback",
                        request.mediaId,
                    )
                    throwable
                }

            return try {
                nativeRepository.resolve(request)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (nativeFailure: Throwable) {
                nativeFailure.addSuppressed(ytDlpFailure)
                throw nativeFailure
            }
        }

        private fun AudioStreamRequest.cacheKey(): CacheKey =
            CacheKey(
                mediaId = mediaId,
                quality = quality.name,
                networkMetered = networkMetered,
                purpose = purpose,
                authFingerprint = authState.streamCacheFingerprint,
                pinnedFormatId = pinnedFormatId,
                runtimeRevision = YtDlpRuntimeStore.revision,
            )

        private fun storeResolvedStream(
            key: CacheKey,
            resolved: ResolvedAudioStream,
        ) {
            putResolvedStream(key, resolved)
            if (resolved.source == StreamSource.YT_DLP) {
                val alternatePurpose =
                    when (key.purpose) {
                        StreamPurpose.PLAYBACK -> StreamPurpose.DOWNLOAD
                        StreamPurpose.DOWNLOAD -> StreamPurpose.PLAYBACK
                    }
                putResolvedStream(key.copy(purpose = alternatePurpose), resolved)
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
                    runtimeRevision = YtDlpRuntimeStore.revision,
                ),
            ] = resolved
        }

        private fun isFresh(stream: ResolvedAudioStream): Boolean =
            stream.expiresAtMs > System.currentTimeMillis() + STREAM_EXPIRY_SAFETY_MS

        private companion object {
            const val TAG = "AudioStreamResolver"
            const val STREAM_EXPIRY_SAFETY_MS = 60_000L
            const val MAX_CACHE_ENTRIES = 256
            const val PLAYBACK_RESOLUTION_TIMEOUT_SECONDS = 120L
            const val DOWNLOAD_RESOLUTION_TIMEOUT_SECONDS = 180L
        }
    }
