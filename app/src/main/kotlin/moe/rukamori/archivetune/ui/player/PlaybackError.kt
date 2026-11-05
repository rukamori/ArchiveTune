/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
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
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.media3.common.PlaybackException
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R

@Composable
fun PlaybackErrorDialog(
    error: PlaybackException,
    showLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onLogin: () -> Unit,
) {
    val clipboard = LocalClipboardManager.current
    val fallbackUnknown = stringResource(R.string.playback_error_unknown)
    val fallbackNoInternet = stringResource(R.string.playback_error_no_internet)
    val fallbackTimeout = stringResource(R.string.error_timeout)
    val fallbackNoStream = stringResource(R.string.error_no_stream)
    val fallbackMalformedStream = stringResource(R.string.error_malformed_stream)
    val codeLabel = stringResource(R.string.playback_error_code)
    val httpLabel = stringResource(R.string.playback_error_http)
    val messageLabel = stringResource(R.string.playback_error_message)
    val causeLabel = stringResource(R.string.playback_error_cause)
    val appVersion =
        stringResource(
            R.string.playback_error_app_version,
            BuildConfig.VERSION_NAME,
            BuildConfig.VERSION_CODE,
        )
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
            PlaybackErrorKind.Decoder -> stringResource(R.string.playback_error_decoder_title)
            PlaybackErrorKind.Http -> stringResource(R.string.playback_error_http_title)
            PlaybackErrorKind.Unknown -> fallbackUnknown
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
    val guidance =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> {
                stringResource(R.string.playback_requires_youtube_music_login_refresh)
            }

            PlaybackErrorKind.ConfirmationRequired -> {
                stringResource(R.string.playback_requires_youtube_music_confirmation)
            }

            PlaybackErrorKind.NoInternet -> {
                stringResource(R.string.playback_error_hint_no_internet)
            }

            PlaybackErrorKind.Timeout -> {
                stringResource(R.string.playback_error_hint_timeout)
            }

            PlaybackErrorKind.NoStream -> {
                stringResource(R.string.playback_error_hint_no_stream)
            }

            PlaybackErrorKind.MalformedStream -> {
                stringResource(R.string.playback_error_hint_malformed_stream)
            }

            PlaybackErrorKind.Decoder -> {
                stringResource(R.string.playback_error_hint_decoder)
            }

            PlaybackErrorKind.Http -> {
                if (httpCode != null) {
                    stringResource(R.string.playback_error_hint_http, httpCode)
                } else {
                    stringResource(R.string.playback_error_hint_unknown)
                }
            }

            PlaybackErrorKind.Unknown -> {
                stringResource(R.string.playback_error_hint_unknown)
            }
        }
    @DrawableRes val icon =
        when (errorInfo.kind) {
            PlaybackErrorKind.LoginRefreshRequired -> R.drawable.login
            PlaybackErrorKind.ConfirmationRequired -> R.drawable.lock
            PlaybackErrorKind.Timeout -> R.drawable.timer
            PlaybackErrorKind.NoStream -> R.drawable.block
            else -> R.drawable.error
        }
    val details =
        remember(error, reason, appVersion, httpCode, codeLabel, httpLabel, messageLabel, causeLabel) {
            buildPlaybackErrorDetails(
                error = error,
                reason = reason,
                appVersion = appVersion,
                httpCode = httpCode,
                codeLabel = codeLabel,
                httpLabel = httpLabel,
                messageLabel = messageLabel,
                causeLabel = causeLabel,
            )
        }
    var detailsCopied by remember(details) { mutableStateOf(false) }
    var detailsExpanded by rememberSaveable(details) { mutableStateOf(false) }
    val onCopy = {
        clipboard.setText(AnnotatedString(details))
        detailsCopied = true
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
            val useExpandedLayout =
                maxWidth >= PlaybackErrorExpandedMinWidth &&
                    maxHeight >= PlaybackErrorExpandedMinHeight

            Surface(
                modifier =
                    Modifier
                        .widthIn(
                            max = if (useExpandedLayout) PlaybackErrorExpandedMaxWidth else PlaybackErrorCompactMaxWidth,
                        ).fillMaxWidth()
                        .heightIn(max = maxHeight),
                shape = AlertDialogDefaults.shape,
                color = AlertDialogDefaults.containerColor,
                tonalElevation = AlertDialogDefaults.TonalElevation,
            ) {
                if (useExpandedLayout) {
                    PlaybackErrorExpandedContent(
                        icon = icon,
                        title = title,
                        guidance = guidance,
                        details = details,
                        detailsCopied = detailsCopied,
                        showLoginAction = showLoginAction,
                        onRetry = onRetry,
                        onClose = onClose,
                        onCopy = onCopy,
                        onLogin = onLogin,
                    )
                } else {
                    PlaybackErrorCompactContent(
                        icon = icon,
                        title = title,
                        guidance = guidance,
                        details = details,
                        detailsCopied = detailsCopied,
                        detailsExpanded = detailsExpanded,
                        showLoginAction = showLoginAction,
                        onToggleDetails = { detailsExpanded = !detailsExpanded },
                        onRetry = onRetry,
                        onClose = onClose,
                        onCopy = onCopy,
                        onLogin = onLogin,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorCompactContent(
    @DrawableRes icon: Int,
    title: String,
    guidance: String,
    details: String,
    detailsCopied: Boolean,
    detailsExpanded: Boolean,
    showLoginAction: Boolean,
    onToggleDetails: () -> Unit,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onLogin: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(top = 28.dp, bottom = 20.dp),
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlaybackErrorBadge(icon = icon, size = 88.dp)
            Spacer(modifier = Modifier.size(20.dp))
            PlaybackErrorHeadline(
                title = title,
                guidance = guidance,
                textAlign = TextAlign.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            )
            Spacer(modifier = Modifier.size(12.dp))
            PlaybackErrorDetailsToggle(
                expanded = detailsExpanded,
                onToggle = onToggleDetails,
            )
            AnimatedVisibility(
                visible = detailsExpanded,
                enter =
                    fadeIn(motionScheme.defaultEffectsSpec()) +
                        expandVertically(motionScheme.defaultSpatialSpec()),
                exit =
                    fadeOut(motionScheme.fastEffectsSpec()) +
                        shrinkVertically(motionScheme.fastSpatialSpec()),
            ) {
                PlaybackErrorDetailsPanel(
                    details = details,
                    copied = detailsCopied,
                    onCopy = onCopy,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                )
            }
        }
        PlaybackErrorActions(
            showLoginAction = showLoginAction,
            onRetry = onRetry,
            onClose = onClose,
            onLogin = onLogin,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 24.dp, top = 20.dp),
        )
    }
}

@Composable
private fun PlaybackErrorExpandedContent(
    @DrawableRes icon: Int,
    title: String,
    guidance: String,
    details: String,
    detailsCopied: Boolean,
    showLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onCopy: () -> Unit,
    onLogin: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(24.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Column(modifier = Modifier.weight(0.44f)) {
            Column(
                modifier =
                    Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState()),
            ) {
                PlaybackErrorBadge(icon = icon, size = 72.dp)
                Spacer(modifier = Modifier.size(20.dp))
                PlaybackErrorHeadline(
                    title = title,
                    guidance = guidance,
                    textAlign = TextAlign.Start,
                    horizontalAlignment = Alignment.Start,
                )
            }
            PlaybackErrorActions(
                showLoginAction = showLoginAction,
                onRetry = onRetry,
                onClose = onClose,
                onLogin = onLogin,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
            )
        }
        PlaybackErrorDetailsPanel(
            details = details,
            copied = detailsCopied,
            onCopy = onCopy,
            modifier =
                Modifier
                    .weight(0.56f)
                    .verticalScroll(rememberScrollState()),
        )
    }
}

@Composable
private fun PlaybackErrorBadge(
    @DrawableRes icon: Int,
    size: Dp,
    modifier: Modifier = Modifier,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress = remember { Animatable(0f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(icon) {
        progress.snapTo(0f)
        progress.animateTo(targetValue = 1f, animationSpec = entranceSpec)
    }
    val morphProgress = progress.value.coerceIn(0f, 1f)

    Box(
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    val scale = lerp(PlaybackErrorBadgeStartScale, 1f, progress.value)
                    scaleX = scale
                    scaleY = scale
                    rotationZ = lerp(-PlaybackErrorBadgeStartRotation, 0f, progress.value)
                }.background(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = EditorialMorphShape(morph, morphProgress),
                ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onErrorContainer,
            modifier =
                Modifier
                    .size(size * PlaybackErrorBadgeIconRatio)
                    .graphicsLayer {
                        rotationZ = lerp(PlaybackErrorBadgeStartRotation, 0f, progress.value)
                    },
        )
    }
}

@Composable
private fun PlaybackErrorHeadline(
    title: String,
    guidance: String,
    textAlign: TextAlign,
    horizontalAlignment: Alignment.Horizontal,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = textAlign,
            modifier = Modifier.semantics { heading() },
        )
        Text(
            text = guidance,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = textAlign,
        )
    }
}

@Composable
private fun PlaybackErrorDetailsToggle(
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "playbackErrorDetailsChevron",
    )

    TextButton(
        onClick = onToggle,
        shapes = ButtonDefaults.shapes(),
        modifier = modifier,
    ) {
        Text(
            text =
                stringResource(
                    if (expanded) R.string.playback_error_hide_details else R.string.playback_error_show_details,
                ),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Icon(
            painter = painterResource(R.drawable.expand_more),
            contentDescription = null,
            modifier =
                Modifier
                    .size(ButtonDefaults.IconSize)
                    .graphicsLayer { rotationZ = chevronRotation },
        )
    }
}

@Composable
private fun PlaybackErrorDetailsPanel(
    details: String,
    copied: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.playback_error_details),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            SelectionContainer {
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Icon(
                    painter = painterResource(R.drawable.info),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = stringResource(R.string.playback_error_reporting_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
            FilledTonalButton(
                onClick = onCopy,
                shapes = ButtonDefaults.shapes(),
                modifier =
                    Modifier
                        .align(Alignment.End)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                PlaybackErrorActionContent(
                    icon = if (copied) R.drawable.check else R.drawable.copy,
                    label = stringResource(if (copied) R.string.copied else R.string.copy),
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorActions(
    showLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onLogin: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val primaryHeight = ButtonDefaults.MediumContainerHeight

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = if (showLoginAction) onLogin else onRetry,
            shapes = ButtonDefaults.shapesFor(primaryHeight),
            contentPadding = ButtonDefaults.contentPaddingFor(primaryHeight, hasStartIcon = true),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = primaryHeight),
        ) {
            Icon(
                painter = painterResource(if (showLoginAction) R.drawable.login else R.drawable.replay),
                contentDescription = null,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(primaryHeight)),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(primaryHeight)))
            Text(
                text = stringResource(if (showLoginAction) R.string.action_login else R.string.retry),
                style = ButtonDefaults.textStyleFor(primaryHeight),
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (showLoginAction) {
                OutlinedButton(
                    onClick = onRetry,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.weight(1f),
                ) {
                    PlaybackErrorActionContent(
                        icon = R.drawable.replay,
                        label = stringResource(R.string.retry),
                    )
                }
            }
            TextButton(
                onClick = onClose,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.weight(1f),
            ) {
                Text(text = stringResource(R.string.close))
            }
        }
    }
}

@Composable
private fun PlaybackErrorActionContent(
    @DrawableRes icon: Int,
    label: String,
) {
    Icon(
        painter = painterResource(icon),
        contentDescription = null,
        modifier = Modifier.size(ButtonDefaults.IconSize),
    )
    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
    Text(text = label)
}

private fun buildPlaybackErrorDetails(
    error: PlaybackException,
    reason: String,
    appVersion: String,
    httpCode: Int?,
    codeLabel: String,
    httpLabel: String,
    messageLabel: String,
    causeLabel: String,
): String =
    buildString {
        appendLine(appVersion)
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

private val PlaybackErrorCompactMaxWidth: Dp = 440.dp
private val PlaybackErrorExpandedMaxWidth: Dp = 820.dp
private val PlaybackErrorExpandedMinWidth: Dp = 600.dp
private val PlaybackErrorExpandedMinHeight: Dp = 360.dp
private const val PlaybackErrorBadgeStartScale = 0.6f
private const val PlaybackErrorBadgeStartRotation = 45f
private const val PlaybackErrorBadgeIconRatio = 0.42f
private const val PlaybackErrorMaxCauseDepth = 6
