/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.player

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.R

@Composable
fun PlaybackErrorDialog(
    error: PlaybackException,
    showLoginAction: Boolean,
    showPoTokenLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onLogin: () -> Unit,
    onPoTokenLogin: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val fallbackUnknown = stringResource(R.string.error_unknown)
    val fallbackNoInternet = stringResource(R.string.error_no_internet)
    val fallbackTimeout = stringResource(R.string.error_timeout)
    val fallbackNoStream = stringResource(R.string.error_no_stream)
    val fallbackMalformedStream = stringResource(R.string.error_malformed_stream)
    val retryText = stringResource(R.string.retry)
    val closeText = stringResource(R.string.close)
    val copyText = stringResource(R.string.copy)
    val copiedText = stringResource(R.string.copied)
    val loginText = stringResource(R.string.login)
    val poTokenLoginText = stringResource(R.string.playback_login_po_token)
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
    val onCopyClick =
        remember(clipboard, context, details, copiedText) {
            {
                clipboard.setText(AnnotatedString(details))
                Toast.makeText(context, copiedText, Toast.LENGTH_SHORT).show()
            }
        }

    BasicAlertDialog(
        onDismissRequest = {},
        properties =
            DialogProperties(
                dismissOnBackPress = false,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
            ),
    ) {
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.safeDrawing)
                    .padding(16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = PlaybackErrorDialogMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                BoxWithConstraints {
                    val useExpandedLayout =
                        maxWidth >= PlaybackErrorExpandedMinWidth &&
                            maxHeight >= PlaybackErrorExpandedMinHeight

                    if (useExpandedLayout) {
                        PlaybackErrorExpandedContent(
                            title = title,
                            reason = reason,
                            detailsLabel = detailsText,
                            details = details,
                            retryText = retryText,
                            closeText = closeText,
                            copyText = copyText,
                            loginText = loginText,
                            poTokenLoginText = poTokenLoginText,
                            showLoginAction = showLoginAction,
                            showPoTokenLoginAction = showPoTokenLoginAction,
                            onRetry = onRetry,
                            onClose = onClose,
                            onCopy = onCopyClick,
                            onLogin = onLogin,
                            onPoTokenLogin = onPoTokenLogin,
                        )
                    } else {
                        PlaybackErrorCompactContent(
                            title = title,
                            reason = reason,
                            detailsLabel = detailsText,
                            details = details,
                            retryText = retryText,
                            closeText = closeText,
                            copyText = copyText,
                            loginText = loginText,
                            poTokenLoginText = poTokenLoginText,
                            showLoginAction = showLoginAction,
                            showPoTokenLoginAction = showPoTokenLoginAction,
                            onRetry = onRetry,
                            onClose = onClose,
                            onCopy = onCopyClick,
                            onLogin = onLogin,
                            onPoTokenLogin = onPoTokenLogin,
                        )
                    }
                }
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
    closeText: String,
    copyText: String,
    loginText: String,
    poTokenLoginText: String,
    showLoginAction: Boolean,
    showPoTokenLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onLogin: () -> Unit,
    onPoTokenLogin: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
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
            closeText = closeText,
            copyText = copyText,
            loginText = loginText,
            poTokenLoginText = poTokenLoginText,
            showLoginAction = showLoginAction,
            showPoTokenLoginAction = showPoTokenLoginAction,
            onRetry = onRetry,
            onClose = onClose,
            onCopy = onCopy,
            onLogin = onLogin,
            onPoTokenLogin = onPoTokenLogin,
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
    closeText: String,
    copyText: String,
    loginText: String,
    poTokenLoginText: String,
    showLoginAction: Boolean,
    showPoTokenLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onLogin: () -> Unit,
    onPoTokenLogin: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        PlaybackErrorHeader(
            title = title,
            reason = reason,
            modifier = Modifier.weight(0.42f),
        )
        Column(
            modifier = Modifier.weight(0.58f),
            verticalArrangement = Arrangement.spacedBy(20.dp),
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
                closeText = closeText,
                copyText = copyText,
                loginText = loginText,
                poTokenLoginText = poTokenLoginText,
                showLoginAction = showLoginAction,
                showPoTokenLoginAction = showPoTokenLoginAction,
                onRetry = onRetry,
                onClose = onClose,
                onCopy = onCopy,
                onLogin = onLogin,
                onPoTokenLogin = onPoTokenLogin,
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
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(14.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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
            SelectionContainer(
                modifier = Modifier.verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorActions(
    retryText: String,
    closeText: String,
    copyText: String,
    loginText: String,
    poTokenLoginText: String,
    showLoginAction: Boolean,
    showPoTokenLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onLogin: () -> Unit,
    onPoTokenLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedButton(onClick = onClose) {
            PlaybackErrorActionContent(
                icon = R.drawable.close,
                label = closeText,
            )
        }
        if (showLoginAction) {
            OutlinedButton(onClick = onLogin) {
                PlaybackErrorActionContent(
                    icon = R.drawable.login,
                    label = loginText,
                )
            }
        }
        if (showPoTokenLoginAction) {
            OutlinedButton(onClick = onPoTokenLogin) {
                PlaybackErrorActionContent(
                    icon = R.drawable.token,
                    label = poTokenLoginText,
                )
            }
        }
        FilledTonalButton(onClick = onCopy) {
            PlaybackErrorActionContent(
                icon = R.drawable.select_all,
                label = copyText,
            )
        }
        Button(onClick = onRetry) {
            PlaybackErrorActionContent(
                icon = R.drawable.replay,
                label = retryText,
            )
        }
    }
}

@Composable
private fun PlaybackErrorActionContent(
    icon: Int,
    label: String,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
    Text(text = label)
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

private val PlaybackErrorDialogMaxWidth: Dp = 760.dp
private val PlaybackErrorExpandedMinWidth: Dp = 600.dp
private val PlaybackErrorExpandedMinHeight: Dp = 360.dp
private const val PlaybackErrorMaxCauseDepth = 6
