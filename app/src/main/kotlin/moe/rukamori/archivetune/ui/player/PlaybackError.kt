/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.utils.openYouTubeMusicUrl

@Composable
fun PlaybackError(
    error: PlaybackException,
    retry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val fallbackUnknown = stringResource(R.string.error_unknown)
    val fallbackNoInternet = stringResource(R.string.error_no_internet)
    val fallbackTimeout = stringResource(R.string.error_timeout)
    val fallbackNoStream = stringResource(R.string.error_no_stream)
    val fallbackMalformedStream = stringResource(R.string.error_malformed_stream)
    val retryText = stringResource(R.string.retry)
    val copyText = stringResource(R.string.copy)
    val copiedText = stringResource(R.string.copied)
    val openYouTubeMusicText = stringResource(R.string.open_youtube_music)
    val loginText = stringResource(R.string.login)
    val couldNotOpenYouTubeMusicText = stringResource(R.string.could_not_open_youtube_music)
    val detailsText = stringResource(R.string.details)
    val codeLabel = stringResource(R.string.playback_error_code)
    val httpLabel = stringResource(R.string.playback_error_http)
    val messageLabel = stringResource(R.string.playback_error_message)
    val causeLabel = stringResource(R.string.playback_error_cause)
    val errorInfo = remember(error) { error.toPlaybackErrorInfo() }
    val httpCode = errorInfo.httpCode
    val title =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> stringResource(R.string.playback_login_refresh_required)
            PlaybackErrorKind.ConfirmationRequired -> stringResource(R.string.playback_confirmation_required)
            PlaybackErrorKind.NoInternet -> fallbackNoInternet
            PlaybackErrorKind.Timeout -> fallbackTimeout
            PlaybackErrorKind.NoStream -> fallbackNoStream
            PlaybackErrorKind.MalformedStream -> fallbackMalformedStream
            else -> fallbackUnknown
        }
    val reason =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> {
                stringResource(R.string.playback_requires_youtube_music_login_refresh)
            }

            PlaybackErrorKind.ConfirmationRequired -> {
                stringResource(R.string.playback_requires_youtube_music_confirmation)
            }

            PlaybackErrorKind.NoInternet -> {
                fallbackNoInternet
            }

            PlaybackErrorKind.Timeout -> {
                fallbackTimeout
            }

            PlaybackErrorKind.NoStream -> {
                fallbackNoStream
            }

            PlaybackErrorKind.MalformedStream -> {
                fallbackMalformedStream
            }

            PlaybackErrorKind.Decoder -> {
                "$fallbackUnknown ($codeLabel ${error.errorCode})"
            }

            PlaybackErrorKind.Http -> {
                "$fallbackUnknown ($httpLabel $httpCode)"
            }

            PlaybackErrorKind.Unknown -> {
                error.cause?.message?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                    ?: fallbackUnknown
            }
        }

    val details =
        remember(error, reason, httpCode, codeLabel, httpLabel, messageLabel, causeLabel) {
            buildPlaybackErrorDetails(
                error = error,
                reason = reason,
                httpCode = httpCode,
                codeLabel = codeLabel,
                httpLabel = httpLabel,
                messageLabel = messageLabel,
                causeLabel = causeLabel,
            )
        }
    val recoveryUrl = errorInfo.loginRecoveryUrl
    val recoveryAction = errorInfo.recoveryAction
    val recoveryActionText =
        when (recoveryAction) {
            PlaybackRecoveryAction.RefreshLogin -> loginText
            PlaybackRecoveryAction.OpenYouTubeMusic -> openYouTubeMusicText
            null -> null
        }
    val onRecoveryClick: (() -> Unit)? =
        remember(recoveryUrl, recoveryAction, context, couldNotOpenYouTubeMusicText) {
            if (recoveryUrl == null || recoveryAction == null) {
                null
            } else {
                {
                    when (recoveryAction) {
                        PlaybackRecoveryAction.RefreshLogin -> {
                            val deepLink = Uri.parse("archivetune://login?url=${Uri.encode(recoveryUrl)}")
                            val loginIntent =
                                Intent(Intent.ACTION_VIEW, deepLink, context, MainActivity::class.java).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
                                    addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                                }
                            runCatching { context.startActivity(loginIntent) }
                        }

                        PlaybackRecoveryAction.OpenYouTubeMusic -> {
                            if (!context.openYouTubeMusicUrl(recoveryUrl)) {
                                Toast.makeText(context, couldNotOpenYouTubeMusicText, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                    Unit
                }
            }
        }
    val onCopyClick =
        remember(clipboard, context, details, copiedText) {
            {
                clipboard.setText(AnnotatedString(details))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            }
        }

    BoxWithConstraints(
        modifier =
            modifier
                .widthIn(max = PlaybackErrorMaxWidth)
                .fillMaxWidth(),
    ) {
        val useExpandedLayout = maxWidth >= PlaybackErrorExpandedMinWidth && maxHeight >= PlaybackErrorExpandedMinHeight

        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = maxHeight),
        ) {
            if (useExpandedLayout) {
                PlaybackErrorExpandedContent(
                    title = title,
                    reason = reason,
                    detailsLabel = detailsText,
                    details = details,
                    retryText = retryText,
                    copyText = copyText,
                    recoveryActionText = recoveryActionText,
                    onRetryClick = retry,
                    onCopyClick = onCopyClick,
                    onRecoveryClick = onRecoveryClick,
                )
            } else {
                PlaybackErrorCompactContent(
                    title = title,
                    reason = reason,
                    detailsLabel = detailsText,
                    details = details,
                    retryText = retryText,
                    copyText = copyText,
                    recoveryActionText = recoveryActionText,
                    onRetryClick = retry,
                    onCopyClick = onCopyClick,
                    onRecoveryClick = onRecoveryClick,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorCompactContent(
    title: String,
    reason: String,
    detailsLabel: String,
    details: String,
    retryText: String,
    copyText: String,
    recoveryActionText: String?,
    onRetryClick: () -> Unit,
    onCopyClick: () -> Unit,
    onRecoveryClick: (() -> Unit)?,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PlaybackErrorHeader(
            title = title,
            reason = reason,
            modifier = Modifier.fillMaxWidth(),
        )

        PlaybackErrorDetails(
            label = detailsLabel,
            details = details,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false),
        )

        PlaybackErrorActions(
            retryText = retryText,
            copyText = copyText,
            recoveryActionText = recoveryActionText,
            onRetryClick = onRetryClick,
            onCopyClick = onCopyClick,
            onRecoveryClick = onRecoveryClick,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaybackErrorExpandedContent(
    title: String,
    reason: String,
    detailsLabel: String,
    details: String,
    retryText: String,
    copyText: String,
    recoveryActionText: String?,
    onRetryClick: () -> Unit,
    onCopyClick: () -> Unit,
    onRecoveryClick: (() -> Unit)?,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(20.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        PlaybackErrorHeader(
            title = title,
            reason = reason,
            modifier =
                Modifier
                    .weight(0.42f),
        )

        Column(
            modifier =
                Modifier
                    .weight(0.58f),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PlaybackErrorDetails(
                label = detailsLabel,
                details = details,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
            )

            PlaybackErrorActions(
                retryText = retryText,
                copyText = copyText,
                recoveryActionText = recoveryActionText,
                onRetryClick = onRetryClick,
                onCopyClick = onCopyClick,
                onRecoveryClick = onRecoveryClick,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun PlaybackErrorHeader(
    title: String,
    reason: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.10f),
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier =
                        Modifier
                            .padding(10.dp)
                            .size(28.dp),
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorDetails(
    label: String,
    details: String,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = details,
                modifier = Modifier.verticalScroll(rememberScrollState()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                softWrap = true,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

@Composable
private fun PlaybackErrorActions(
    retryText: String,
    copyText: String,
    recoveryActionText: String?,
    onRetryClick: () -> Unit,
    onCopyClick: () -> Unit,
    onRecoveryClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (recoveryActionText != null && onRecoveryClick != null) {
            Button(
                onClick = onRecoveryClick,
                modifier = Modifier.fillMaxWidth(),
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = recoveryActionText)
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (recoveryActionText == null) {
                Button(
                    onClick = onRetryClick,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = retryText)
                }
            } else {
                OutlinedButton(
                    onClick = onRetryClick,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = retryText)
                }
            }

            FilledTonalButton(
                onClick = onCopyClick,
                shapes = ButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.select_all),
                    contentDescription = null,
                )
                Spacer(Modifier.width(8.dp))
                Text(text = copyText)
            }
        }
    }
}

private fun buildPlaybackErrorDetails(
    error: PlaybackException,
    reason: String,
    httpCode: Int?,
    codeLabel: String,
    httpLabel: String,
    messageLabel: String,
    causeLabel: String,
): String =
    buildString {
        appendLine(reason)
        appendLine("$codeLabel: ${error.errorCode}")
        if (httpCode != null) appendLine("$httpLabel: $httpCode")

        val rootMessage = error.message?.trim().orEmpty()
        if (rootMessage.isNotBlank() && rootMessage != reason) {
            appendLine()
            appendLine("$messageLabel: $rootMessage")
        }

        var throwable: Throwable? = error.cause
        var depth = 0
        while (throwable != null && depth < PlaybackErrorMaxCauseDepth) {
            val name = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
            val message = throwable.message?.trim().orEmpty()
            appendLine()
            appendLine("$causeLabel: $name${if (message.isNotBlank()) ": $message" else ""}")
            throwable = throwable.cause
            depth++
        }
    }.trim()

private val PlaybackErrorMaxWidth: Dp = 760.dp
private val PlaybackErrorExpandedMinWidth: Dp = 600.dp
private val PlaybackErrorExpandedMinHeight: Dp = 360.dp
private const val PlaybackErrorMaxCauseDepth = 6
