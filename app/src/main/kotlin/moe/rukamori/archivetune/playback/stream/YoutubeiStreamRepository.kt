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
import moe.rukamori.archivetune.utils.potoken.BotGuardTokenGenerator
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
        private val gvsPoTokenVerifier =
            GvsPoTokenVerifier(
                maxMinterAttempts = MAX_TOKEN_GENERATOR_ATTEMPTS,
                replaceMinter = BotGuardTokenGenerator::discardMinter,
                probeStatus = ::probeStreamStatus,
            )

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
            var streamMediaId = request.mediaId
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
                            streamMediaId = replacementMediaId
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
            val verifiedStream =
                resolvedUrl?.let { url ->
                    verifyGvsPoToken(
                        mediaId = request.mediaId,
                        streamMediaId = streamMediaId,
                        authState = authState,
                        streamUrl = url,
                        contentLength = resolved.contentLength,
                        requestHeaders = resolved.requestHeaders,
                    )
                }
            val streamUrl = verifiedStream?.url ?: resolvedUrl
            val tokenBinding =
                verifiedStream?.binding?.name?.lowercase()
                    ?: if (streamUrl?.queryParameter("pot").isNullOrBlank()) "missing" else "unverified"
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
                gvsPoTokenBinding = verifiedStream?.binding,
                gvsPoTokenMinterGeneration = verifiedStream?.minterGeneration,
            )
        }

        private suspend fun verifyGvsPoToken(
            mediaId: String,
            streamMediaId: String,
            authState: PlaybackAuthState,
            streamUrl: HttpUrl,
            contentLength: Long,
            requestHeaders: Map<String, String>,
        ): VerifiedGvsStream? {
            if (streamUrl.queryParameter("pot").isNullOrBlank() || streamUrl.queryParameter("sabr") == "1") return null
            return try {
                gvsPoTokenVerifier.verify(
                    streamUrl = streamUrl,
                    contentLength = contentLength,
                    mint = {
                        val tokens = YTPlayerUtils.mintYoutubeiPoTokens(streamMediaId, authState)
                        MintedGvsPoTokens(
                            videoToken = tokens.playerToken,
                            sessionToken = tokens.sessionToken,
                            minterGeneration = tokens.minterGeneration,
                        )
                    },
                    requestHeaders = requestHeaders,
                )
            } catch (failure: YoutubeiException) {
                if (failure.kind != YoutubeiFailureKind.PO_TOKEN) throw failure
                throw YTPlayerUtils.BotDetectionPlaybackException(
                    videoId = mediaId,
                    clients = setOf(if (authState.hasLoginCookie) "WEB_CREATOR" else "MWEB"),
                    cause = failure,
                )
            }
        }

        private suspend fun probeStreamStatus(
            url: HttpUrl,
            byteRange: LongRange,
            requestHeaders: Map<String, String>,
        ): Int? {
            val request =
                Request
                    .Builder()
                    .url(url)
                    .apply { requestHeaders.forEach { (name, value) -> header(name, value) } }
                    .header("Range", "bytes=${byteRange.first}-${byteRange.last}")
                    .build()
            return try {
                streamProbeClient.newCall(request).awaitStatusCode().also { statusCode ->
                    Timber.tag(LOG_TAG).d("Stream token check status=%d", statusCode)
                }
            } catch (failure: IOException) {
                Timber.tag(LOG_TAG).w("Stream token check failed type=%s", failure.javaClass.simpleName)
                null
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
            minterGeneration: Long,
            binding: GvsPoTokenBinding,
        ) {
            gvsPoTokenVerifier.reportRejected(minterGeneration, binding)
            Timber.tag(LOG_TAG).w(
                "Stream rejected %s-bound PO token from token generator %d; next resolution checks it again",
                binding,
                minterGeneration,
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

        private companion object {
            const val LOG_TAG = "YoutubeiResolver"
            const val STREAM_PROBE_TIMEOUT_SECONDS = 10L
            const val MAX_TOKEN_GENERATOR_ATTEMPTS = 3
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
