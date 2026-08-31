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
import moe.rukamori.archivetune.constants.AudioQuality
import moe.rukamori.archivetune.innertube.NetworkGatekeeper
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.utils.hasCompleteYouTubeLoginCookies
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiAudioQuality
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiException
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiFailureKind
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiNetworkConfiguration
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiResolutionPriority
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiResolver
import moe.rukamori.archivetune.morideobfuscator.youtubei.YoutubeiStreamRequest
import moe.rukamori.archivetune.utils.YTPlayerUtils
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YoutubeiStreamRepository
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : AudioStreamRepository {
        private val resolver =
            YoutubeiResolver(context) {
                YoutubeiNetworkConfiguration(
                    proxy = YouTube.proxy,
                    proxyUsername = YouTube.proxyUsername,
                    proxyPassword = YouTube.proxyPassword,
                    dns = YouTube.dns,
                    interceptors = listOf(NetworkGatekeeper),
                )
            }

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
                                cookie = authState.cookie,
                                visitorData = authState.visitorData,
                                dataSyncId = authState.dataSyncId,
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
                    )
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (failure: YoutubeiException) {
                    if (failure.kind == YoutubeiFailureKind.LOGIN_REQUIRED) {
                        throw YTPlayerUtils.LoginRequiredForPlaybackException(
                            videoId = request.mediaId,
                            targetUrl = request.mediaUrl,
                            reason = failure.message,
                        )
                    }
                    throw failure
                }

            return ResolvedAudioStream(
                url = resolved.url,
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
    }

internal enum class StreamResolutionPriority {
    FOREGROUND,
    BACKGROUND,
}
