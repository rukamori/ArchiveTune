/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.stream

import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.innertube.utils.hasYtDlpYouTubeLoginCookies
import moe.rukamori.archivetune.utils.YTPlayerUtils
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YtDlpStreamRepository
    @Inject
    constructor(
        private val runtime: YtDlpRuntime,
    ) : AudioStreamRepository {
        override suspend fun resolve(request: AudioStreamRequest): ResolvedAudioStream {
            val authState = request.authState
            if (authState.hasLoginCookie && !hasYtDlpYouTubeLoginCookies(authState.cookie)) {
                throw YTPlayerUtils.InvalidPlaybackLoginContextException(
                    videoId = request.mediaId,
                    targetUrl = request.mediaUrl,
                    cause = IllegalStateException("YouTube login cookies are incomplete for yt-dlp"),
                )
            }

            return try {
                runtime.resolve(request, authState)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (throwable: Throwable) {
                if (throwable.isAgeVerificationRequired()) {
                    throw YTPlayerUtils.LoginRequiredForPlaybackException(
                        videoId = request.mediaId,
                        targetUrl = request.mediaUrl,
                        reason = throwable.message,
                    )
                }
                throw throwable
            }
        }

        private val AudioStreamRequest.mediaUrl: String
            get() = "https://music.youtube.com/watch?v=$mediaId"

        private fun Throwable.isAgeVerificationRequired(): Boolean {
            var current: Throwable? = this
            while (current != null) {
                if (current.message?.contains(AGE_VERIFICATION_ERROR, ignoreCase = true) == true) return true
                current = current.cause
            }
            return false
        }

        private companion object {
            const val AGE_VERIFICATION_ERROR = "Sign in to confirm your age"
        }
    }
