/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.NetworkGatekeeper
import moe.rukamori.archivetune.innertube.PlaybackAuthState
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasCompleteYouTubeLoginCookies
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiAudioQuality
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiException
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiFailureKind
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiResolutionPriority
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiResolver
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiStreamRequest
import moe.rukamori.archivetune.utils.YTPlayerUtils
import okhttp3.Call
import okhttp3.Callback
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import timber.log.Timber
import java.io.IOException
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

@Singleton
class YoutubeiStreamRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AudioStreamRepository {
        private val resolver =
            YoutubeiResolver(
                context = context,
                diagnostics = { message -> Timber.tag(LOG_TAG).d(message) },
            ) {
                playbackNetworkConfiguration(context).copy(
                    interceptors = listOf(NetworkGatekeeper),
                )
            }
        private val streamProbeClient =
            PlaybackHttpClient(
                context,
                OkHttpClient
                    .Builder()
                    .addInterceptor(NetworkGatekeeper)
                    .connectTimeout(STREAM_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .readTimeout(STREAM_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .callTimeout(STREAM_PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                    .build(),
            )
        private val gvsPoTokenBindings = ConcurrentHashMap<String, GvsPoTokenBindingState>()

        override suspend fun resolve(request: AudioStreamRequest): ResolvedAudioStream =
            resolve(
                request = request,
                priority =
                    when (request.purpose) {
                        StreamPurpose.PLAYBACK -> StreamResolutionPriority.FOREGROUND
                        StreamPurpose.DOWNLOAD -> StreamResolutionPriority.BACKGROUND
                    },
            )

        internal suspend fun resolve(
            request: AudioStreamRequest,
            priority: StreamResolutionPriority,
        ): ResolvedAudioStream {
            val authState = request.authState
            if (authState.hasLoginCookie && !hasCompleteYouTubeLoginCookies(authState.cookie)) {
                throw YTPlayerUtils.InvalidPlaybackLoginContextException(
                    videoId = request.mediaId,
                    targetUrl = request.mediaUrl,
                    cause = IllegalStateException("YouTube login cookies are incomplete"),
                )
            }

            val locale = YouTube.locale
            val resolved =
                try {
                    resolver.resolve(
                        request =
                            YoutubeiStreamRequest(
                                mediaId = request.mediaId,
                                quality = request.quality.toYoutubeiQuality(),
                                networkMetered = request.networkMetered,
                                authFingerprint = authState.streamCacheFingerprint,
                                pinnedItag = request.pinnedFormatId,
                                requiresSongMetadata = request.requiresSongMetadata,
                                cookie = authState.cookie.takeIf { authState.hasLoginCookie },
                                visitorData = authState.visitorData,
                                dataSyncId = authState.dataSyncId.takeIf { authState.hasLoginCookie },
                                sessionPoToken = authState.poTokenGvsSession,
                                videoPoToken =
                                    authState.poTokenGvs?.takeIf {
                                        authState.poTokenGvsVideoId == request.mediaId
                                    },
                                language = locale.hl,
                                location = locale.gl,
                                timezone = TimeZone.getDefault().id,
                            ),
                        priority = priority.toYoutubeiPriority(),
                        videoPoTokenProvider = { replacementMediaId ->
                            val replacementAuthState =
                                YTPlayerUtils.ensureYoutubeiPoTokensForPlayback(
                                    videoId = replacementMediaId,
                                    authState = authState,
                                )
                            replacementAuthState.poTokenGvs?.takeIf {
                                replacementAuthState.poTokenGvsVideoId == replacementMediaId
                            }
                        },
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: YoutubeiException) {
                    if (failure.kind == YoutubeiFailureKind.PO_TOKEN ||
                        failure.kind == YoutubeiFailureKind.LOGIN_REQUIRED &&
                        YTPlayerUtils.isBotDetectionError(failure.message.orEmpty())
                    ) {
                        throw YTPlayerUtils.BotDetectionPlaybackException(
                            videoId = request.mediaId,
                            clients = setOf(if (authState.hasLoginCookie) "WEB_CREATOR" else "MWEB"),
                            cause = failure,
                        )
                    }
                    if (failure.kind == YoutubeiFailureKind.LOGIN_REQUIRED ||
                        failure.kind == YoutubeiFailureKind.HTTP && failure.httpStatus == 401
                    ) {
                        if (!failure.requiresContentConfirmation()) {
                            if (!authState.hasLoginCookie) {
                                throw YTPlayerUtils.BadStreamPlayerResponseException(
                                    videoId = request.mediaId,
                                    failedClients = setOf("MWEB"),
                                    cause = failure,
                                )
                            }
                            throw YTPlayerUtils.InvalidPlaybackLoginContextException(
                                videoId = request.mediaId,
                                targetUrl = request.mediaUrl,
                                cause = failure,
                            )
                        }
                        throw YTPlayerUtils.LoginRequiredForPlaybackException(
                            videoId = request.mediaId,
                            targetUrl = request.mediaUrl,
                            reason = failure.message,
                            cause = failure,
                        )
                    }
                    throw failure
                }

            val resolvedUrl = resolved.url.toHttpUrlOrNull()
            val selectedStream =
                selectGvsPoTokenStream(
                    mediaId = request.mediaId,
                    authState = authState,
                    resolvedUrl = resolvedUrl,
                    requestHeaders = resolved.requestHeaders,
                )
            val streamUrl = selectedStream?.url ?: resolvedUrl
            val tokenBinding =
                selectedStream?.binding?.name?.lowercase()
                    ?: if (streamUrl?.queryParameter("pot").isNullOrBlank()) "missing" else "other"
            Timber.tag(LOG_TAG).d(
                "Stream contract signedIn=%s accountSession=%s format=%d tokenBinding=%s nPresent=%s nError=%s " +
                    "signaturePresent=%s userAgentPresent=%s extractionProxy=%s streamProxy=%s",
                authState.hasLoginCookie,
                authState.hasPlaybackLoginContext,
                resolved.formatId,
                tokenBinding,
                !streamUrl?.queryParameter("n").isNullOrBlank(),
                streamUrl?.queryParameter("n")?.startsWith("enhanced_except_") == true,
                !streamUrl?.queryParameter("sig").isNullOrBlank() ||
                    !streamUrl?.queryParameter("signature").isNullOrBlank(),
                resolved.requestHeaders.any { (name, value) -> name.equals("User-Agent", ignoreCase = true) && value.isNotBlank() },
                YouTube.streamOkHttpProxy.type().name,
                YouTube.streamOkHttpProxy.type().name,
            )
            return ResolvedAudioStream(
                url = streamUrl?.toString() ?: resolved.url,
                requestHeaders = resolved.requestHeaders,
                formatId = resolved.formatId,
                mimeType = resolved.mimeType,
                codecs = resolved.codecs,
                bitrate = resolved.bitrate,
                sampleRate = resolved.sampleRate,
                contentLength = resolved.contentLength,
                expiresAtMs = resolved.expiresAtMs,
                authFingerprint = authState.streamCacheFingerprint,
                source = StreamSource.YOUTUBEI,
                runtimeVersion = resolved.runtimeVersion,
                title = resolved.title,
                durationSeconds = resolved.durationSeconds,
                thumbnailUrl = resolved.thumbnailUrl,
                loudnessDb = resolved.loudnessDb,
                perceptualLoudnessDb = resolved.perceptualLoudnessDb,
                playbackTrackingUrl = resolved.playbackTrackingUrl,
                gvsPoTokenBinding = selectedStream?.binding,
            )
        }

        private suspend fun selectGvsPoTokenStream(
            mediaId: String,
            authState: PlaybackAuthState,
            resolvedUrl: HttpUrl?,
            requestHeaders: Map<String, String>,
        ): GvsPoTokenStream? {
            val authFingerprint = authState.streamCacheFingerprint
            val bindingState = gvsPoTokenBindings[authFingerprint]
            val preferredBinding = bindingState?.binding ?: GvsPoTokenBinding.VIDEO
            val candidates =
                gvsPoTokenStreams(resolvedUrl, authState.poTokenGvsSession)
                    .sortedByDescending { it.binding == preferredBinding }
            val preferredStream = candidates.firstOrNull() ?: return null
            if (bindingState?.confirmed == true && preferredStream.binding == bindingState.binding) {
                return preferredStream
            }
            for (candidate in candidates) {
                when (probeStream(candidate, requestHeaders)) {
                    StreamProbeResult.ACCEPTED -> {
                        gvsPoTokenBindings[authFingerprint] =
                            GvsPoTokenBindingState(binding = candidate.binding, confirmed = true)
                        return candidate
                    }
                    StreamProbeResult.INCONCLUSIVE -> return candidate
                    StreamProbeResult.REJECTED -> Unit
                }
            }
            gvsPoTokenBindings.remove(authFingerprint)
            throw YoutubeiException(
                kind = YoutubeiFailureKind.PO_TOKEN,
                message = "YouTube rejected every playback token binding for $mediaId",
                httpStatus = 403,
            )
        }

        private fun gvsPoTokenStreams(
            resolvedUrl: HttpUrl?,
            sessionPoToken: String?,
        ): List<GvsPoTokenStream> {
            val streamToken = resolvedUrl?.queryParameter("pot")?.takeIf(String::isNotBlank) ?: return emptyList()
            val sessionToken = sessionPoToken?.takeIf(String::isNotBlank)
            return listOfNotNull(
                GvsPoTokenStream(GvsPoTokenBinding.VIDEO, resolvedUrl).takeIf { streamToken != sessionToken },
                sessionToken?.let { token ->
                    GvsPoTokenStream(
                        binding = GvsPoTokenBinding.SESSION,
                        url = resolvedUrl.newBuilder().setQueryParameter("pot", token).build(),
                    )
                },
            )
        }

        private suspend fun probeStream(
            stream: GvsPoTokenStream,
            requestHeaders: Map<String, String>,
        ): StreamProbeResult {
            val request =
                Request
                    .Builder()
                    .url(stream.url)
                    .apply { requestHeaders.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=0-0")
                    .build()
            val statusCode =
                try {
                    streamProbeClient.newCall(request).awaitStatusCode()
                } catch (failure: IOException) {
                    Timber.tag(LOG_TAG).w(
                        "Stream probe binding=%s failed type=%s",
                        stream.binding,
                        failure.javaClass.simpleName,
                    )
                    return StreamProbeResult.INCONCLUSIVE
                }
            Timber.tag(LOG_TAG).d("Stream probe binding=%s status=%d", stream.binding, statusCode)
            return when (statusCode) {
                403 -> StreamProbeResult.REJECTED
                in 200..299 -> StreamProbeResult.ACCEPTED
                else -> StreamProbeResult.INCONCLUSIVE
            }
        }

        private suspend fun Call.awaitStatusCode(): Int =
            suspendCancellableCoroutine { continuation ->
                continuation.invokeOnCancellation { cancel() }
                enqueue(
                    object : Callback {
                        override fun onFailure(
                            call: Call,
                            e: IOException,
                        ) {
                            if (continuation.isActive) continuation.resumeWithException(e)
                        }

                        override fun onResponse(
                            call: Call,
                            response: Response,
                        ) {
                            val statusCode = response.use { it.code }
                            if (continuation.isActive) continuation.resume(statusCode)
                        }
                    },
                )
            }

        internal fun reportRejectedGvsPoToken(
            authFingerprint: String,
            rejectedBinding: GvsPoTokenBinding,
        ) {
            val updatedState =
                gvsPoTokenBindings.compute(authFingerprint) { _, current ->
                    if (current == null || current.binding == rejectedBinding) {
                        GvsPoTokenBindingState(binding = rejectedBinding.alternate(), confirmed = false)
                    } else {
                        current
                    }
                }
            Timber.tag(LOG_TAG).w(
                "Stream rejected %s-bound PO token; next resolution prefers %s-bound token",
                rejectedBinding,
                updatedState?.binding,
            )
        }

        suspend fun preWarm() {
            resolver.preWarm()
        }

        suspend fun invalidateSessions() {
            resolver.invalidateSessions()
        }

        fun trimMemory(level: Int) {
            resolver.trimMemory(level)
        }

        private fun AudioQuality.toYoutubeiQuality(): YoutubeiAudioQuality =
            when (this) {
                AudioQuality.LOW -> YoutubeiAudioQuality.LOW
                AudioQuality.HIGH -> YoutubeiAudioQuality.HIGH
                AudioQuality.HIGHEST -> YoutubeiAudioQuality.HIGHEST
                AudioQuality.AUTO -> YoutubeiAudioQuality.AUTO
            }

        private fun StreamResolutionPriority.toYoutubeiPriority(): YoutubeiResolutionPriority =
            when (this) {
                StreamResolutionPriority.FOREGROUND -> YoutubeiResolutionPriority.FOREGROUND
                StreamResolutionPriority.BACKGROUND -> YoutubeiResolutionPriority.BACKGROUND
            }

        private val AudioStreamRequest.mediaUrl: String
            get() = "https://music.youtube.com/watch?v=$mediaId"

        private fun YoutubeiException.requiresContentConfirmation(): Boolean {
            if (httpStatus == 401) return false
            val reason = message.orEmpty()
            return CONTENT_CONFIRMATION_REASONS.any { reason.contains(it, ignoreCase = true) }
        }

        private data class GvsPoTokenBindingState(
            val binding: GvsPoTokenBinding,
            val confirmed: Boolean,
        )

        private data class GvsPoTokenStream(
            val binding: GvsPoTokenBinding,
            val url: HttpUrl,
        )

        private enum class StreamProbeResult {
            ACCEPTED,
            REJECTED,
            INCONCLUSIVE,
        }

        private fun GvsPoTokenBinding.alternate(): GvsPoTokenBinding =
            when (this) {
                GvsPoTokenBinding.VIDEO -> GvsPoTokenBinding.SESSION
                GvsPoTokenBinding.SESSION -> GvsPoTokenBinding.VIDEO
            }

        private companion object {
            const val LOG_TAG = "YoutubeiResolver"
            const val STREAM_PROBE_TIMEOUT_SECONDS = 10L
            val CONTENT_CONFIRMATION_REASONS =
                listOf(
                    "confirm your age",
                    "verify your age",
                    "age-restricted",
                    "age restricted",
                    "age verification",
                    "AGE_CHECK_REQUIRED",
                    "AGE_VERIFICATION_REQUIRED",
                    "CONTENT_CHECK_REQUIRED",
                    "inappropriate for some users",
                    "mature audiences",
                    "private video",
                    "members-only",
                    "members only",
                )
        }
    }

internal enum class StreamResolutionPriority {
    FOREGROUND,
    BACKGROUND,
}
