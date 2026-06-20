/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.media3.common.PlaybackException
import androidx.media3.datasource.HttpDataSource
import moe.rukamori.archivetune.utils.YTPlayerUtils

internal enum class PlaybackErrorKind {
    LoginRefreshRequired,
    ConfirmationRequired,
    NoInternet,
    Timeout,
    NoStream,
    MalformedStream,
    Decoder,
    Http,
    Unknown,
}

internal enum class PlaybackRecoveryAction {
    RefreshLogin,
    OpenYouTubeMusic,
}

internal data class PlaybackErrorInfo(
    val kind: PlaybackErrorKind,
    val httpCode: Int?,
    val loginRecoveryUrl: String?,
    val recoveryAction: PlaybackRecoveryAction?,
)

internal fun PlaybackException.toPlaybackErrorInfo(): PlaybackErrorInfo {
    val httpCode = httpStatusCodeOrNull()
    val invalidPlaybackLoginContextUrl = invalidPlaybackLoginContextUrl()
    val externalLoginRecoveryUrl = loginRecoveryUrl()
    val loginRecoveryUrl = invalidPlaybackLoginContextUrl ?: externalLoginRecoveryUrl
    val recoveryAction =
        when {
            invalidPlaybackLoginContextUrl != null -> PlaybackRecoveryAction.RefreshLogin
            externalLoginRecoveryUrl != null -> PlaybackRecoveryAction.OpenYouTubeMusic
            else -> null
        }
    val kind =
        when {
            invalidPlaybackLoginContextUrl != null -> PlaybackErrorKind.LoginRefreshRequired

            externalLoginRecoveryUrl != null -> PlaybackErrorKind.ConfirmationRequired

            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED -> PlaybackErrorKind.NoInternet

            errorCode == PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> PlaybackErrorKind.Timeout

            YTPlayerUtils.isBotDetectionException(this) -> PlaybackErrorKind.NoStream

            YTPlayerUtils.isBadStreamPlayerResponseException(this) -> PlaybackErrorKind.NoStream

            httpCode in setOf(403, 404, 410, 416) -> PlaybackErrorKind.NoStream

            errorCode == PlaybackException.ERROR_CODE_PARSING_CONTAINER_MALFORMED -> PlaybackErrorKind.MalformedStream

            errorCode in
                setOf(
                    PlaybackException.ERROR_CODE_PARSING_CONTAINER_UNSUPPORTED,
                    PlaybackException.ERROR_CODE_DECODING_FAILED,
                    PlaybackException.ERROR_CODE_DECODING_FORMAT_UNSUPPORTED,
                )
            -> PlaybackErrorKind.Decoder

            httpCode != null -> PlaybackErrorKind.Http

            else -> PlaybackErrorKind.Unknown
        }

    return PlaybackErrorInfo(
        kind = kind,
        httpCode = httpCode,
        loginRecoveryUrl = loginRecoveryUrl,
        recoveryAction = recoveryAction,
    )
}

internal fun PlaybackException.httpStatusCodeOrNull(): Int? {
    var throwable: Throwable? = cause
    while (throwable != null) {
        if (throwable is HttpDataSource.InvalidResponseCodeException) return throwable.responseCode
        throwable = throwable.cause
    }
    return null
}

internal fun PlaybackException.invalidPlaybackLoginContextUrl(): String? =
    findCause<YTPlayerUtils.InvalidPlaybackLoginContextException>()?.targetUrl

internal fun PlaybackException.loginRecoveryUrl(): String? {
    findCause<YTPlayerUtils.LoginRequiredForPlaybackException>()?.let { return it.targetUrl }

    return null
}

private inline fun <reified T : Throwable> Throwable.findCause(): T? {
    var throwable: Throwable? = this
    while (throwable != null) {
        if (throwable is T) return throwable
        throwable = throwable.cause
    }
    return null
}
