/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package moe.rukamori.archivetune.ui.player

import android.provider.Settings
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.media3.common.PlaybackException
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.modules.ModuleCheckStatus
import moe.rukamori.archivetune.playback.ConsultedModule
import moe.rukamori.archivetune.playback.PlaybackFailure
import moe.rukamori.archivetune.playback.PlaybackFailureSource
import moe.rukamori.archivetune.ui.screens.settings.ModuleProviderIcon
import java.text.DecimalFormat

internal enum class PlaybackFailureStep {
    Source,
    Connection,
    Device,
}

private enum class PlaybackPathNodeState {
    Neutral,
    Passed,
    Failed,
    Unreached,
    Skipped,
}

private data class PlaybackErrorCopy(
    val title: String,
    val guidance: String,
    val reason: String,
    @param:DrawableRes val icon: Int,
)

private data class PlaybackSourcePresentation(
    val name: String,
    val subtitle: String?,
    @param:DrawableRes val icon: Int,
    val verdict: String,
    val blamesSource: Boolean,
)

private data class PlaybackErrorDetailRow(
    val key: String,
    val label: String,
    val value: String,
)

@Composable
fun PlaybackErrorDialog(
    failure: PlaybackFailure,
    showLoginAction: Boolean,
    onRetry: () -> Unit,
    onClose: () -> Unit,
    onLogin: () -> Unit,
) {
    val error = failure.error
    val source = failure.source
    val clipboard = LocalClipboardManager.current
    val animate = rememberPlaybackErrorMotionEnabled()
    val errorInfo = remember(error) { error.toPlaybackErrorInfo() }
    val step = remember(errorInfo.kind, source) { playbackFailureStep(errorInfo.kind, source) }
    val copy = playbackErrorCopy(errorInfo.kind, errorInfo.httpCode, error)
    val presentation = source?.let { playbackSourcePresentation(it, step) }
    val codeLabel = stringResource(R.string.playback_error_code)
    val httpLabel = stringResource(R.string.playback_error_http)
    val messageLabel = stringResource(R.string.playback_error_message)
    val causeLabel = stringResource(R.string.playback_error_cause)
    val sourceLabel = stringResource(R.string.source)
    val appVersionLabel = stringResource(R.string.playback_error_app_version_label)
    val appVersionValue =
        stringResource(R.string.playback_error_app_version_value, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    val appVersionLine =
        stringResource(R.string.playback_error_app_version, BuildConfig.VERSION_NAME, BuildConfig.VERSION_CODE)
    val sourceSummary = presentation?.let { listOfNotNull(it.name, it.subtitle).joinToString(" · ") }
    val codeValue = "${error.errorCodeName} (${error.errorCode})"
    val detailRows =
        remember(codeValue, errorInfo.httpCode, sourceSummary, appVersionValue, codeLabel, httpLabel, sourceLabel, appVersionLabel) {
            buildList {
                add(PlaybackErrorDetailRow("code", codeLabel, codeValue))
                errorInfo.httpCode?.let { add(PlaybackErrorDetailRow("http", httpLabel, it.toString())) }
                sourceSummary?.let { add(PlaybackErrorDetailRow("source", sourceLabel, it)) }
                add(PlaybackErrorDetailRow("version", appVersionLabel, appVersionValue))
            }
        }
    val causeChain = remember(error, messageLabel, causeLabel) { playbackErrorCauseChain(error, messageLabel, causeLabel) }
    val report =
        remember(error, copy.reason, appVersionLine, codeValue, source, step, causeChain) {
            buildString {
                appendLine(appVersionLine)
                appendLine(copy.reason)
                appendLine("$codeLabel: $codeValue")
                errorInfo.httpCode?.let { appendLine("$httpLabel: $it") }
                source?.let { appendLine("$sourceLabel: ${playbackFailureSourceReport(it)}") }
                appendLine("step=${step?.name ?: "UNKNOWN"}")
                causeChain.forEach {
                    appendLine()
                    appendLine(it)
                }
            }.trim()
        }
    var copiedKey by remember(report) { mutableStateOf<String?>(null) }
    val onCopy: (String, String) -> Unit = { key, value ->
        clipboard.setText(AnnotatedString(value))
        copiedKey = key
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
            val expandedLayout =
                maxWidth >= PlaybackErrorExpandedMinWidth && maxHeight >= PlaybackErrorExpandedMinHeight
            Surface(
                modifier =
                    Modifier
                        .widthIn(max = if (expandedLayout) PlaybackErrorExpandedMaxWidth else PlaybackErrorCompactMaxWidth)
                        .fillMaxWidth()
                        .heightIn(max = maxHeight),
                shape = MaterialTheme.shapes.extraLargeIncreased,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                val header: @Composable () -> Unit = {
                    PlaybackErrorReveal(order = 0, animate = animate) {
                        PlaybackErrorHeader(
                            copy = copy,
                            trackTitle = failure.trackTitle,
                            trackArtists = failure.trackArtists,
                            animate = animate,
                        )
                    }
                }
                val path: @Composable () -> Unit = {
                    PlaybackErrorReveal(order = 1, animate = animate) {
                        PlaybackSignalPath(
                            sourceName = presentation?.name ?: stringResource(R.string.source),
                            sourceIcon = presentation?.icon ?: R.drawable.music_note,
                            step = step,
                            connectionSkipped =
                                source is PlaybackFailureSource.LocalFile ||
                                    (source is PlaybackFailureSource.Module && source.offline),
                            animate = animate,
                        )
                    }
                }
                val sourceCard: @Composable () -> Unit = {
                    if (source != null && presentation != null) {
                        PlaybackErrorReveal(order = 2, animate = animate) {
                            PlaybackSourceCard(source = source, presentation = presentation)
                        }
                    }
                }
                val actions: @Composable (Modifier) -> Unit = { modifier ->
                    PlaybackErrorActions(
                        showLoginAction = showLoginAction,
                        onRetry = onRetry,
                        onClose = onClose,
                        onLogin = onLogin,
                        modifier = modifier,
                    )
                }

                if (expandedLayout) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp),
                    ) {
                        Column(modifier = Modifier.weight(0.5f)) {
                            Column(
                                modifier =
                                    Modifier
                                        .weight(1f, fill = false)
                                        .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(PlaybackErrorSectionGap),
                            ) {
                                header()
                                path()
                                sourceCard()
                            }
                            actions(Modifier.padding(top = 20.dp))
                        }
                        Column(
                            modifier =
                                Modifier
                                    .weight(0.5f)
                                    .verticalScroll(rememberScrollState()),
                        ) {
                            PlaybackErrorReveal(order = 3, animate = animate) {
                                PlaybackErrorDiagnostics(
                                    rows = detailRows,
                                    causeChain = causeChain,
                                    report = report,
                                    copiedKey = copiedKey,
                                    onCopy = onCopy,
                                )
                            }
                        }
                    }
                } else {
                    Column(modifier = Modifier.padding(top = 24.dp, bottom = 20.dp)) {
                        Column(
                            modifier =
                                Modifier
                                    .weight(1f, fill = false)
                                    .verticalScroll(rememberScrollState())
                                    .padding(horizontal = 20.dp),
                            verticalArrangement = Arrangement.spacedBy(PlaybackErrorSectionGap),
                        ) {
                            header()
                            path()
                            sourceCard()
                            PlaybackErrorReveal(order = 3, animate = animate) {
                                PlaybackErrorDiagnostics(
                                    rows = detailRows,
                                    causeChain = causeChain,
                                    report = report,
                                    copiedKey = copiedKey,
                                    onCopy = onCopy,
                                )
                            }
                        }
                        actions(
                            Modifier
                                .padding(horizontal = 20.dp)
                                .padding(top = 16.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberPlaybackErrorMotionEnabled(): Boolean {
    val context = LocalContext.current
    return remember(context) {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
    }
}

@Composable
private fun PlaybackErrorReveal(
    order: Int,
    animate: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val progress = remember { Animatable(if (animate) 0f else 1f) }
    val spatialSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(animate) {
        if (!animate) {
            progress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(order * PlaybackErrorRevealStaggerMs)
        progress.animateTo(1f, spatialSpec)
    }
    Box(
        modifier =
            modifier.graphicsLayer {
                val value = progress.value
                alpha = value.coerceIn(0f, 1f)
                translationY = (1f - value) * PlaybackErrorRevealOffset.toPx()
            },
    ) {
        content()
    }
}

@Composable
private fun PlaybackErrorHeader(
    copy: PlaybackErrorCopy,
    trackTitle: String?,
    trackArtists: String?,
    animate: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PlaybackErrorGlyph(icon = copy.icon, animate = animate)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = stringResource(R.string.playback_error_overline),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.error,
                )
                if (trackTitle != null) {
                    Text(
                        text = trackTitle,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trackArtists != null) {
                    Text(
                        text = trackArtists,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = copy.title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = copy.guidance,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlaybackErrorGlyph(
    @DrawableRes icon: Int,
    animate: Boolean,
) {
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val entrance = remember { Animatable(if (animate) 0f else 1f) }
    val entranceSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(icon, animate) {
        if (!animate) {
            entrance.snapTo(1f)
            return@LaunchedEffect
        }
        entrance.snapTo(0f)
        entrance.animateTo(1f, entranceSpec)
    }
    val haloRotation: State<Float> =
        if (animate) {
            rememberInfiniteTransition(label = "playbackErrorHalo").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(PlaybackErrorHaloRotationMs, easing = LinearEasing)),
                label = "playbackErrorHaloRotation",
            )
        } else {
            remember { mutableFloatStateOf(0f) }
        }
    val haloShape = MaterialShapes.SoftBurst.toShape()
    val haloColor = MaterialTheme.colorScheme.error.copy(alpha = PlaybackErrorHaloAlpha)

    Box(
        modifier =
            Modifier
                .size(PlaybackErrorGlyphSize)
                .clearAndSetSemantics {},
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(PlaybackErrorGlyphSize)
                    .graphicsLayer {
                        val value = entrance.value
                        scaleX = lerp(PlaybackErrorGlyphStartScale, 1f, value)
                        scaleY = lerp(PlaybackErrorGlyphStartScale, 1f, value)
                        alpha = value.coerceIn(0f, 1f)
                        rotationZ = haloRotation.value
                    }.background(haloColor, haloShape),
        )
        Box(
            modifier =
                Modifier
                    .size(PlaybackErrorGlyphSize * PlaybackErrorGlyphCoreRatio)
                    .graphicsLayer {
                        val value = entrance.value
                        scaleX = lerp(PlaybackErrorGlyphStartScale, 1f, value)
                        scaleY = lerp(PlaybackErrorGlyphStartScale, 1f, value)
                        rotationZ = lerp(-PlaybackErrorGlyphStartRotation, 0f, value)
                    }.background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = EditorialMorphShape(morph, entrance.value.coerceIn(0f, 1f)),
                    ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier =
                    Modifier
                        .size(PlaybackErrorGlyphIconSize)
                        .graphicsLayer {
                            rotationZ = lerp(PlaybackErrorGlyphStartRotation, 0f, entrance.value)
                        },
            )
        }
    }
}

@Composable
private fun PlaybackSignalPath(
    sourceName: String,
    @DrawableRes sourceIcon: Int,
    step: PlaybackFailureStep?,
    connectionSkipped: Boolean,
    animate: Boolean,
) {
    val connectionName = stringResource(R.string.playback_error_path_connection)
    val deviceName = stringResource(R.string.playback_error_path_device)
    val names = listOf(sourceName, connectionName, deviceName)
    val failedName = step?.let { names[it.ordinal] }
    val outcomeDescription =
        if (failedName != null) {
            stringResource(R.string.playback_error_path_failed, failedName)
        } else {
            stringResource(R.string.playback_error_path_unknown)
        }
    val skippedDescription = stringResource(R.string.playback_error_path_skipped, connectionName)
    val description =
        if (connectionSkipped && step != PlaybackFailureStep.Connection) {
            "$outcomeDescription $skippedDescription"
        } else {
            outcomeDescription
        }

    val travelTarget = step?.ordinal?.toFloat() ?: 0f
    val travel = remember { Animatable(if (animate) 0f else travelTarget) }
    val impactProgress = remember { Animatable(if (animate) 0f else 1f) }
    val travelSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    val impactSpec = MaterialTheme.motionScheme.defaultSpatialSpec<Float>()
    LaunchedEffect(travelTarget, animate) {
        if (!animate) {
            travel.snapTo(travelTarget)
            impactProgress.snapTo(1f)
            return@LaunchedEffect
        }
        delay(PlaybackErrorRevealStaggerMs * 2)
        travel.animateTo(travelTarget, travelSpec)
        impactProgress.animateTo(1f, impactSpec)
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.playback_error_path_title),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clearAndSetSemantics { contentDescription = description },
            verticalAlignment = Alignment.Top,
        ) {
            PlaybackFailureStep.entries.forEachIndexed { index, node ->
                val state =
                    when {
                        node == PlaybackFailureStep.Connection && connectionSkipped && step != node -> PlaybackPathNodeState.Skipped
                        step == null -> PlaybackPathNodeState.Neutral
                        node == step -> PlaybackPathNodeState.Failed
                        node.ordinal < step.ordinal -> PlaybackPathNodeState.Passed
                        else -> PlaybackPathNodeState.Unreached
                    }
                PlaybackPathNode(
                    name = names[index],
                    icon =
                        when (node) {
                            PlaybackFailureStep.Source -> sourceIcon
                            PlaybackFailureStep.Connection -> R.drawable.wifi
                            PlaybackFailureStep.Device -> R.drawable.graphic_eq
                        },
                    state = state,
                    impact = { if (state == PlaybackPathNodeState.Failed) impactProgress.value else 0f },
                    animate = animate,
                    modifier = Modifier.weight(1f),
                )
                if (index < PlaybackFailureStep.entries.lastIndex) {
                    PlaybackPathConnector(
                        fill = { (travel.value - index).coerceIn(0f, 1f) },
                        reached = step != null && index < step.ordinal,
                        modifier = Modifier.weight(PlaybackErrorConnectorWeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackPathNode(
    name: String,
    @DrawableRes icon: Int,
    state: PlaybackPathNodeState,
    impact: () -> Float,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val effects = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val container by animateColorAsState(
        targetValue =
            when (state) {
                PlaybackPathNodeState.Failed -> colors.errorContainer
                PlaybackPathNodeState.Passed -> colors.primaryContainer
                PlaybackPathNodeState.Neutral -> colors.surfaceContainerHighest
                PlaybackPathNodeState.Unreached -> colors.surfaceContainer
                PlaybackPathNodeState.Skipped -> Color.Transparent
            },
        animationSpec = effects,
        label = "playbackPathNodeContainer",
    )
    val content by animateColorAsState(
        targetValue =
            when (state) {
                PlaybackPathNodeState.Failed -> colors.onErrorContainer
                PlaybackPathNodeState.Passed -> colors.onPrimaryContainer
                PlaybackPathNodeState.Neutral -> colors.onSurfaceVariant
                PlaybackPathNodeState.Unreached, PlaybackPathNodeState.Skipped -> colors.outline
            },
        animationSpec = effects,
        label = "playbackPathNodeContent",
    )
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie6Sided) }
    val pulse: State<Float> =
        if (animate && state == PlaybackPathNodeState.Failed) {
            rememberInfiniteTransition(label = "playbackPathPulse").animateFloat(
                initialValue = 1f,
                targetValue = PlaybackErrorPulseScale,
                animationSpec = infiniteRepeatable(tween(PlaybackErrorPulseMs), RepeatMode.Reverse),
                label = "playbackPathPulseScale",
            )
        } else {
            remember { mutableFloatStateOf(1f) }
        }
    val skippedBorder = if (state == PlaybackPathNodeState.Skipped) colors.outlineVariant else Color.Transparent

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(PlaybackErrorNodeSize)
                    .graphicsLayer {
                        val hit = impact()
                        val scale = pulse.value * lerp(1f, PlaybackErrorImpactScale, hit * (1f - hit) * 4f)
                        scaleX = scale
                        scaleY = scale
                        rotationZ = lerp(0f, PlaybackErrorImpactRotation, hit)
                    }.background(container, EditorialMorphShape(morph, impact().coerceIn(0f, 1f)))
                    .border(1.dp, skippedBorder, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = content,
                modifier =
                    Modifier
                        .size(PlaybackErrorNodeIconSize)
                        .graphicsLayer { rotationZ = -lerp(0f, PlaybackErrorImpactRotation, impact()) },
            )
        }
        Text(
            text = name,
            style =
                if (state == PlaybackPathNodeState.Failed) {
                    MaterialTheme.typography.labelMediumEmphasized
                } else {
                    MaterialTheme.typography.labelMedium
                },
            color =
                when (state) {
                    PlaybackPathNodeState.Failed -> colors.error
                    PlaybackPathNodeState.Unreached, PlaybackPathNodeState.Skipped -> colors.outline
                    else -> colors.onSurface
                },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackPathConnector(
    fill: () -> Float,
    reached: Boolean,
    modifier: Modifier = Modifier,
) {
    val track = MaterialTheme.colorScheme.outlineVariant
    val active = MaterialTheme.colorScheme.primary
    Box(
        modifier =
            modifier
                .height(PlaybackErrorNodeSize)
                .padding(horizontal = 2.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(PlaybackErrorConnectorThickness)
                    .clip(CircleShape)
                    .background(track),
        )
        if (reached) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(PlaybackErrorConnectorThickness)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = fill()
                        }.clip(CircleShape)
                        .background(active),
            )
        }
    }
}

@Composable
private fun PlaybackSourceCard(
    source: PlaybackFailureSource,
    presentation: PlaybackSourcePresentation,
) {
    val colors = MaterialTheme.colorScheme
    val avatarShape: Shape =
        when (source) {
            is PlaybackFailureSource.Module, PlaybackFailureSource.ModulePipeline -> MaterialShapes.Cookie9Sided.toShape()
            is PlaybackFailureSource.YouTubeMusic -> MaterialShapes.Sunny.toShape()
            is PlaybackFailureSource.DirectLink -> MaterialShapes.Clover4Leaf.toShape()
            PlaybackFailureSource.LocalFile -> MaterialShapes.Cookie4Sided.toShape()
        }
    val avatarColor =
        when (source) {
            is PlaybackFailureSource.Module, PlaybackFailureSource.ModulePipeline -> colors.tertiaryContainer
            else -> colors.secondaryContainer
        }
    val avatarContent =
        when (source) {
            is PlaybackFailureSource.Module, PlaybackFailureSource.ModulePipeline -> colors.onTertiaryContainer
            else -> colors.onSecondaryContainer
        }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = colors.surfaceContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier =
                        Modifier
                            .size(PlaybackErrorSourceAvatarSize)
                            .background(avatarColor, avatarShape),
                    contentAlignment = Alignment.Center,
                ) {
                    val icon = (source as? PlaybackFailureSource.Module)?.icon
                    if (icon != null) {
                        ModuleProviderIcon(icon = icon, size = PlaybackErrorSourceIconSize)
                    } else {
                        Icon(
                            painter = painterResource(presentation.icon),
                            contentDescription = null,
                            tint = avatarContent,
                            modifier = Modifier.size(PlaybackErrorSourceIconSize),
                        )
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = stringResource(R.string.source),
                        style = MaterialTheme.typography.labelMedium,
                        color = colors.onSurfaceVariant,
                    )
                    Text(
                        text = presentation.name,
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        color = colors.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    presentation.subtitle?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            PlaybackSourceVerdict(text = presentation.verdict, blamesSource = presentation.blamesSource)
            when (source) {
                is PlaybackFailureSource.Module -> PlaybackModuleStreamTags(source)
                is PlaybackFailureSource.YouTubeMusic -> {
                    if (source.consultedModules.isNotEmpty()) PlaybackConsultedModules(source.consultedModules)
                }
                else -> Unit
            }
        }
    }
}

@Composable
private fun PlaybackSourceVerdict(
    text: String,
    blamesSource: Boolean,
) {
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = MaterialTheme.shapes.large,
        color = if (blamesSource) colors.errorContainer else colors.surfaceContainerHighest,
        contentColor = if (blamesSource) colors.onErrorContainer else colors.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(
                painter = painterResource(if (blamesSource) R.drawable.error else R.drawable.info),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PlaybackModuleStreamTags(source: PlaybackFailureSource.Module) {
    val format = listOf(source.mimeType, source.codecs).filter(String::isNotBlank).joinToString(" · ")
    val sampleRate =
        source.sampleRate?.takeIf { it > 0 }?.let {
            stringResource(R.string.playback_error_sample_rate, DecimalFormat("0.#").format(it / 1000.0))
        }
    val tags =
        listOfNotNull(
            source.upstream.takeIf(String::isNotBlank)?.let { stringResource(R.string.modules_upstream, it) },
            source.variant.takeIf(String::isNotBlank)?.let { stringResource(R.string.modules_variant, it) },
            format.takeIf(String::isNotBlank),
            source.bitrate.takeIf { it > 0 }?.let { stringResource(R.string.playback_error_bitrate, it / 1000) },
            sampleRate,
            if (source.offline) stringResource(R.string.offline) else null,
        )
    if (tags.isEmpty()) return
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        tags.forEach { tag ->
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ) {
                Text(
                    text = tag,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@Composable
private fun PlaybackConsultedModules(modules: List<ConsultedModule>) {
    val colors = MaterialTheme.colorScheme
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.playback_error_modules_tried),
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurfaceVariant,
            modifier = Modifier.semantics { heading() },
        )
        modules.forEachIndexed { index, module ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.semantics(mergeDescendants = true) {},
            ) {
                Box(
                    modifier =
                        Modifier
                            .sizeIn(minWidth = 24.dp, minHeight = 24.dp)
                            .background(colors.tertiaryContainer, CircleShape)
                            .padding(horizontal = 6.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = (index + 1).toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = colors.onTertiaryContainer,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = module.name,
                        style = MaterialTheme.typography.bodyMedium,
                        color = colors.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(consultedModuleStatus(module.status)),
                        style = MaterialTheme.typography.bodySmall,
                        color = colors.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaybackErrorDiagnostics(
    rows: List<PlaybackErrorDetailRow>,
    causeChain: List<String>,
    report: String,
    copiedKey: String?,
    onCopy: (String, String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.playback_error_details),
                style = MaterialTheme.typography.titleMediumEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { heading() },
            )
            PlaybackErrorCopyReportButton(
                copied = copiedKey == PlaybackErrorReportKey,
                onClick = { onCopy(PlaybackErrorReportKey, report) },
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap)) {
            rows.forEachIndexed { index, row ->
                PlaybackErrorDetailItem(
                    row = row,
                    index = index,
                    count = rows.size,
                    copied = copiedKey == row.key,
                    onCopy = { onCopy(row.key, row.value) },
                )
            }
        }
        if (causeChain.isNotEmpty()) {
            PlaybackErrorCauseChain(causeChain)
        }
        Row(
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
    }
}

@Composable
private fun PlaybackErrorCopyReportButton(
    copied: Boolean,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    FilledTonalButton(
        onClick = onClick,
        shapes = ButtonDefaults.shapes(),
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
        AnimatedContent(
            targetState = copied,
            transitionSpec = {
                (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()))
                    .togetherWith(scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()))
            },
            label = "playbackErrorCopyReport",
        ) { isCopied ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    painter = painterResource(if (isCopied) R.drawable.check else R.drawable.copy),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text =
                        stringResource(
                            if (isCopied) R.string.playback_error_report_copied else R.string.playback_error_copy_report,
                        ),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun PlaybackErrorDetailItem(
    row: PlaybackErrorDetailRow,
    index: Int,
    count: Int,
    copied: Boolean,
    onCopy: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val copyLabel = stringResource(R.string.playback_error_copy_value, row.label)
    SegmentedListItem(
        onClick = onCopy,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier =
            Modifier
                .fillMaxWidth()
                .semantics { liveRegion = LiveRegionMode.Polite },
        overlineContent = { Text(text = row.label) },
        trailingContent = {
            AnimatedContent(
                targetState = copied,
                transitionSpec = {
                    (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()))
                        .togetherWith(scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()))
                },
                label = "playbackErrorDetailCopied",
            ) { isCopied ->
                Icon(
                    painter = painterResource(if (isCopied) R.drawable.check else R.drawable.copy),
                    contentDescription = if (isCopied) stringResource(R.string.copied) else copyLabel,
                    tint = if (isCopied) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        },
    ) {
        Text(
            text = row.value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PlaybackErrorCauseChain(causeChain: List<String>) {
    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.playback_error_cause_chain),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.semantics { heading() },
            )
            SelectionContainer {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    causeChain.forEach { line ->
                        Text(
                            text = line,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
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
    val height = ButtonDefaults.MediumContainerHeight
    val closeLabel = stringResource(R.string.close)
    val retryLabel = stringResource(R.string.retry)
    val loginLabel = stringResource(R.string.action_login)

    ButtonGroup(
        overflowIndicator = { menuState ->
            IconButton(
                onClick = { if (menuState.isShowing) menuState.dismiss() else menuState.show() },
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(painterResource(R.drawable.more_horiz), contentDescription = stringResource(R.string.more_label))
            }
        },
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                OutlinedButton(
                    onClick = onClose,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = false),
                    interactionSource = interaction,
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = height)
                            .animateWidth(interaction),
                ) {
                    Text(text = closeLabel, style = ButtonDefaults.textStyleFor(height), maxLines = 1)
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text(closeLabel) },
                    leadingIcon = { Icon(painterResource(R.drawable.close), contentDescription = null) },
                    onClick = {
                        menuState.dismiss()
                        onClose()
                    },
                )
            },
        )
        if (showLoginAction) {
            customItem(
                buttonGroupContent = {
                    val interaction = remember { MutableInteractionSource() }
                    FilledTonalButton(
                        onClick = onRetry,
                        shapes = ButtonDefaults.shapesFor(height),
                        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = false),
                        interactionSource = interaction,
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = height)
                                .animateWidth(interaction),
                    ) {
                        Text(text = retryLabel, style = ButtonDefaults.textStyleFor(height), maxLines = 1)
                    }
                },
                menuContent = { menuState ->
                    DropdownMenuItem(
                        text = { Text(retryLabel) },
                        leadingIcon = { Icon(painterResource(R.drawable.replay), contentDescription = null) },
                        onClick = {
                            menuState.dismiss()
                            onRetry()
                        },
                    )
                },
            )
        }
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                Button(
                    onClick = if (showLoginAction) onLogin else onRetry,
                    shapes = ButtonDefaults.shapesFor(height),
                    contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                    interactionSource = interaction,
                    modifier =
                        Modifier
                            .weight(PlaybackErrorPrimaryActionWeight)
                            .heightIn(min = height)
                            .animateWidth(interaction),
                ) {
                    Icon(
                        painter = painterResource(if (showLoginAction) R.drawable.login else R.drawable.replay),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(height)))
                    Text(
                        text = if (showLoginAction) loginLabel else retryLabel,
                        style = ButtonDefaults.textStyleFor(height),
                        maxLines = 1,
                    )
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text(if (showLoginAction) loginLabel else retryLabel) },
                    leadingIcon = {
                        Icon(
                            painterResource(if (showLoginAction) R.drawable.login else R.drawable.replay),
                            contentDescription = null,
                        )
                    },
                    onClick = {
                        menuState.dismiss()
                        if (showLoginAction) onLogin() else onRetry()
                    },
                )
            },
        )
    }
}

@Composable
private fun playbackErrorCopy(
    kind: PlaybackErrorKind,
    httpCode: Int?,
    error: PlaybackException,
): PlaybackErrorCopy {
    val unknown = stringResource(R.string.playback_error_unknown)
    val noInternet = stringResource(R.string.playback_error_no_internet)
    val timeout = stringResource(R.string.error_timeout)
    val noStream = stringResource(R.string.error_no_stream)
    val malformed = stringResource(R.string.error_malformed_stream)
    val codeLabel = stringResource(R.string.playback_error_code)
    val httpLabel = stringResource(R.string.playback_error_http)
    val loginRefresh = stringResource(R.string.playback_requires_youtube_music_login_refresh)
    val confirmation = stringResource(R.string.playback_requires_youtube_music_confirmation)
    return when (kind) {
        PlaybackErrorKind.LoginRefreshRequired -> {
            PlaybackErrorCopy(stringResource(R.string.playback_login_refresh_required), loginRefresh, loginRefresh, R.drawable.login)
        }

        PlaybackErrorKind.ConfirmationRequired -> {
            PlaybackErrorCopy(stringResource(R.string.playback_confirmation_required), confirmation, confirmation, R.drawable.lock)
        }

        PlaybackErrorKind.NoInternet -> {
            PlaybackErrorCopy(noInternet, stringResource(R.string.playback_error_hint_no_internet), noInternet, R.drawable.cloud_off)
        }

        PlaybackErrorKind.Timeout -> {
            PlaybackErrorCopy(timeout, stringResource(R.string.playback_error_hint_timeout), timeout, R.drawable.timer)
        }

        PlaybackErrorKind.NoStream -> {
            PlaybackErrorCopy(noStream, stringResource(R.string.playback_error_hint_no_stream), noStream, R.drawable.block)
        }

        PlaybackErrorKind.MalformedStream -> {
            PlaybackErrorCopy(malformed, stringResource(R.string.playback_error_hint_malformed_stream), malformed, R.drawable.error)
        }

        PlaybackErrorKind.Decoder -> {
            PlaybackErrorCopy(
                stringResource(R.string.playback_error_decoder_title),
                stringResource(R.string.playback_error_hint_decoder),
                "$unknown ($codeLabel ${error.errorCode})",
                R.drawable.graphic_eq,
            )
        }

        PlaybackErrorKind.Http -> {
            PlaybackErrorCopy(
                stringResource(R.string.playback_error_http_title),
                if (httpCode != null) {
                    stringResource(R.string.playback_error_hint_http, httpCode)
                } else {
                    stringResource(R.string.playback_error_hint_unknown)
                },
                "$unknown ($httpLabel $httpCode)",
                R.drawable.error,
            )
        }

        PlaybackErrorKind.Unknown -> {
            PlaybackErrorCopy(
                unknown,
                stringResource(R.string.playback_error_hint_unknown),
                error.cause?.message?.takeIf { it.isNotBlank() }
                    ?: error.message?.takeIf { it.isNotBlank() }
                    ?: unknown,
                R.drawable.error,
            )
        }
    }
}

@Composable
private fun playbackSourcePresentation(
    source: PlaybackFailureSource,
    step: PlaybackFailureStep?,
): PlaybackSourcePresentation =
    when (source) {
        is PlaybackFailureSource.Module -> {
            val details = listOf(source.version, source.author).filter(String::isNotBlank).joinToString(" · ")
            PlaybackSourcePresentation(
                name = source.name,
                subtitle =
                    if (details.isNotBlank()) {
                        stringResource(R.string.playback_error_source_module_version, details)
                    } else {
                        stringResource(R.string.playback_error_source_module)
                    },
                icon = R.drawable.extension,
                verdict =
                    when {
                        source.moduleMessage != null ->
                            stringResource(R.string.playback_error_verdict_module_message, source.name, source.moduleMessage)
                        source.preparationFailed -> stringResource(R.string.playback_error_verdict_module_preparation)
                        step == PlaybackFailureStep.Device -> stringResource(R.string.playback_error_verdict_module_device)
                        step == PlaybackFailureStep.Connection -> stringResource(R.string.playback_error_verdict_connection, source.name)
                        else -> stringResource(R.string.playback_error_verdict_module)
                    },
                blamesSource = source.moduleMessage != null || source.preparationFailed || step == PlaybackFailureStep.Source || step == null,
            )
        }

        PlaybackFailureSource.ModulePipeline -> {
            PlaybackSourcePresentation(
                name = stringResource(R.string.modules_title),
                subtitle = null,
                icon = R.drawable.extension,
                verdict = stringResource(R.string.playback_error_verdict_module_pipeline),
                blamesSource = true,
            )
        }

        is PlaybackFailureSource.YouTubeMusic -> {
            val name = stringResource(R.string.playback_error_source_youtube_music)
            PlaybackSourcePresentation(
                name = name,
                subtitle = null,
                icon = R.drawable.library_music,
                verdict =
                    when {
                        step == PlaybackFailureStep.Device -> stringResource(R.string.playback_error_verdict_youtube_music_device)
                        step == PlaybackFailureStep.Connection -> stringResource(R.string.playback_error_verdict_connection, name)
                        source.consultedModules.isNotEmpty() -> stringResource(R.string.playback_error_verdict_youtube_music_fallback)
                        else -> stringResource(R.string.playback_error_verdict_youtube_music)
                    },
                blamesSource = step == PlaybackFailureStep.Source || step == null,
            )
        }

        is PlaybackFailureSource.DirectLink -> {
            val name = source.host.ifBlank { stringResource(R.string.playback_error_source_direct) }
            PlaybackSourcePresentation(
                name = name,
                subtitle = stringResource(R.string.playback_error_source_direct).takeIf { source.host.isNotBlank() },
                icon = R.drawable.link,
                verdict =
                    if (step == PlaybackFailureStep.Connection) {
                        stringResource(R.string.playback_error_verdict_connection, name)
                    } else {
                        stringResource(R.string.playback_error_verdict_direct)
                    },
                blamesSource = step == PlaybackFailureStep.Source,
            )
        }

        PlaybackFailureSource.LocalFile -> {
            PlaybackSourcePresentation(
                name = stringResource(R.string.playback_error_source_local),
                subtitle = null,
                icon = R.drawable.snippet_folder,
                verdict = stringResource(R.string.playback_error_verdict_local),
                blamesSource = false,
            )
        }
    }

private fun playbackFailureStep(
    kind: PlaybackErrorKind,
    source: PlaybackFailureSource?,
): PlaybackFailureStep? =
    when {
        source is PlaybackFailureSource.ModulePipeline -> PlaybackFailureStep.Source
        source is PlaybackFailureSource.Module && source.preparationFailed -> PlaybackFailureStep.Source
        kind == PlaybackErrorKind.NoInternet || kind == PlaybackErrorKind.Timeout -> PlaybackFailureStep.Connection
        kind == PlaybackErrorKind.Decoder -> PlaybackFailureStep.Device
        kind == PlaybackErrorKind.Unknown -> null
        else -> PlaybackFailureStep.Source
    }

@StringRes
private fun consultedModuleStatus(status: ModuleCheckStatus?): Int =
    when (status) {
        null -> R.string.playback_error_module_cached_result
        ModuleCheckStatus.READY -> R.string.modules_ready
        ModuleCheckStatus.CONFIGURATION -> R.string.modules_needs_configuration
        ModuleCheckStatus.UNAVAILABLE -> R.string.modules_unavailable
        ModuleCheckStatus.FAILED -> R.string.modules_failed
        ModuleCheckStatus.NO_MATCH -> R.string.modules_no_match
        ModuleCheckStatus.RESOLVED -> R.string.modules_resolved
        ModuleCheckStatus.TIMEOUT -> R.string.modules_timeout
    }

private fun playbackFailureSourceReport(source: PlaybackFailureSource): String =
    when (source) {
        is PlaybackFailureSource.Module -> {
            listOf(
                "module",
                "name=${source.name}",
                "version=${source.version}",
                "upstream=${source.upstream}",
                "variant=${source.variant}",
                "format=${listOf(source.mimeType, source.codecs).filter(String::isNotBlank).joinToString(" ")}",
                "bitrate=${source.bitrate}",
                "sampleRate=${source.sampleRate ?: 0}",
                "offline=${source.offline}",
                "preparationFailed=${source.preparationFailed}",
                "moduleHandled=${source.moduleMessage != null}",
            ).joinToString("; ")
        }

        PlaybackFailureSource.ModulePipeline -> {
            "module-pipeline"
        }

        is PlaybackFailureSource.YouTubeMusic -> {
            if (source.consultedModules.isEmpty()) {
                "youtube-music"
            } else {
                "youtube-music; modulesTried=" +
                    source.consultedModules.joinToString(", ") { "${it.name}:${it.status?.name ?: "CACHED"}" }
            }
        }

        is PlaybackFailureSource.DirectLink -> {
            "direct; host=${source.host}"
        }

        PlaybackFailureSource.LocalFile -> {
            "local-file"
        }
    }

private fun playbackErrorCauseChain(
    error: PlaybackException,
    messageLabel: String,
    causeLabel: String,
): List<String> =
    buildList {
        error.message?.trim()?.takeIf(String::isNotBlank)?.let { add("$messageLabel: $it") }
        var throwable: Throwable? = error.cause
        var depth = 0
        while (throwable != null && depth < PlaybackErrorMaxCauseDepth) {
            val name = throwable.javaClass.simpleName.ifBlank { throwable.javaClass.name }
            val message = throwable.message?.trim().orEmpty()
            add("$causeLabel: $name${if (message.isNotBlank()) ": $message" else ""}")
            throwable = throwable.cause
            depth++
        }
    }

private const val PlaybackErrorReportKey = "report"
private val PlaybackErrorCompactMaxWidth: Dp = 460.dp
private val PlaybackErrorExpandedMaxWidth: Dp = 880.dp
private val PlaybackErrorExpandedMinWidth: Dp = 600.dp
private val PlaybackErrorExpandedMinHeight: Dp = 400.dp
private val PlaybackErrorSectionGap: Dp = 20.dp
private val PlaybackErrorRevealOffset: Dp = 24.dp
private const val PlaybackErrorRevealStaggerMs = 70L
private val PlaybackErrorGlyphSize: Dp = 80.dp
private const val PlaybackErrorGlyphCoreRatio = 0.74f
private val PlaybackErrorGlyphIconSize: Dp = 28.dp
private const val PlaybackErrorGlyphStartScale = 0.6f
private const val PlaybackErrorGlyphStartRotation = 45f
private const val PlaybackErrorHaloAlpha = 0.14f
private const val PlaybackErrorHaloRotationMs = 18_000
private val PlaybackErrorNodeSize: Dp = 52.dp
private val PlaybackErrorNodeIconSize: Dp = 22.dp
private val PlaybackErrorConnectorThickness: Dp = 4.dp
private const val PlaybackErrorConnectorWeight = 0.45f
private const val PlaybackErrorPulseScale = 1.06f
private const val PlaybackErrorPulseMs = 900
private const val PlaybackErrorImpactScale = 1.18f
private const val PlaybackErrorImpactRotation = 30f
private val PlaybackErrorSourceAvatarSize: Dp = 56.dp
private val PlaybackErrorSourceIconSize: Dp = 26.dp
private const val PlaybackErrorPrimaryActionWeight = 1.4f
private const val PlaybackErrorMaxCauseDepth = 6
