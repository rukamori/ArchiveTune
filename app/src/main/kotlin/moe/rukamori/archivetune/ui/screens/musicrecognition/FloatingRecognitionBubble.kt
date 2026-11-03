/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.musicrecognition

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.flow.collectLatest
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.musicrecognition.FloatingRecognitionUiState
import moe.rukamori.archivetune.musicrecognition.MusicRecognitionFailure
import moe.rukamori.archivetune.ui.player.EditorialMorphShape

internal val FloatingBubbleFieldSize = 88.dp
private val FloatingBubbleCoreSize = 60.dp

@Composable
internal fun FloatingRecognitionBubble(
    state: FloatingRecognitionUiState,
    captureLevel: () -> Float,
    captureProgress: () -> Float,
    dockedToEnd: Boolean,
    showCard: Boolean,
    onBubbleClick: () -> Unit,
    onOpenResult: () -> Unit,
    onDismissResult: () -> Unit,
) {
    val cardState = state.takeIf {
        showCard && (it is FloatingRecognitionUiState.Matched || it is FloatingRecognitionUiState.Failed)
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (dockedToEnd) {
            FloatingRecognitionCard(cardState, onBubbleClick, onOpenResult, onDismissResult)
            FloatingRecognitionCore(state, captureLevel, captureProgress, onBubbleClick)
        } else {
            FloatingRecognitionCore(state, captureLevel, captureProgress, onBubbleClick)
            FloatingRecognitionCard(cardState, onBubbleClick, onOpenResult, onDismissResult)
        }
    }
}

private fun FloatingRecognitionUiState.polygon(): RoundedPolygon =
    when (this) {
        FloatingRecognitionUiState.Idle -> MaterialShapes.Cookie9Sided
        FloatingRecognitionUiState.Listening -> MaterialShapes.SoftBurst
        FloatingRecognitionUiState.Processing -> MaterialShapes.Circle
        is FloatingRecognitionUiState.Matched -> MaterialShapes.Sunny
        is FloatingRecognitionUiState.Failed -> MaterialShapes.Cookie4Sided
    }

@Composable
private fun FloatingRecognitionCore(
    state: FloatingRecognitionUiState,
    captureLevel: () -> Float,
    captureProgress: () -> Float,
    onClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val animationsEnabled = recognitionAnimationsEnabled()
    val listening = state == FloatingRecognitionUiState.Listening

    val containerColor = animateColorAsState(
        targetValue = when (state) {
            FloatingRecognitionUiState.Idle, FloatingRecognitionUiState.Listening -> colors.primary
            FloatingRecognitionUiState.Processing -> colors.tertiary
            is FloatingRecognitionUiState.Matched -> colors.primaryContainer
            is FloatingRecognitionUiState.Failed -> colors.errorContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "FloatingCoreContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = when (state) {
            FloatingRecognitionUiState.Idle, FloatingRecognitionUiState.Listening -> colors.onPrimary
            FloatingRecognitionUiState.Processing -> colors.onTertiary
            is FloatingRecognitionUiState.Matched -> colors.onPrimaryContainer
            is FloatingRecognitionUiState.Failed -> colors.onErrorContainer
        },
        animationSpec = motion.defaultEffectsSpec(),
        label = "FloatingCoreContent",
    )

    val morphState = remember { PolygonMorphState(state.polygon()) }
    val polygon = state.polygon()
    LaunchedEffect(polygon, animationsEnabled) {
        morphState.morphTo(polygon, if (animationsEnabled) motion.defaultSpatialSpec<Float>() else null)
    }

    val level = remember { Animatable(0f) }
    LaunchedEffect(listening, animationsEnabled) {
        if (listening && animationsEnabled) {
            snapshotFlow(captureLevel).collectLatest { level.animateTo(it, motion.fastSpatialSpec()) }
        } else {
            level.animateTo(0f, motion.defaultSpatialSpec())
        }
    }

    val description = when (state) {
        FloatingRecognitionUiState.Idle -> stringResource(R.string.music_recognition_tap_to_listen)
        FloatingRecognitionUiState.Listening -> stringResource(R.string.music_recognition_listening)
        FloatingRecognitionUiState.Processing -> stringResource(R.string.music_recognition_searching)
        is FloatingRecognitionUiState.Matched -> state.track.title
        is FloatingRecognitionUiState.Failed -> stringResource(R.string.music_recognition_listen_again)
    }
    val clickLabel = when (state) {
        FloatingRecognitionUiState.Idle -> stringResource(R.string.music_recognition_tap_to_listen)
        FloatingRecognitionUiState.Listening, FloatingRecognitionUiState.Processing -> stringResource(R.string.cancel)
        is FloatingRecognitionUiState.Matched -> stringResource(R.string.music_recognition_floating_open_details)
        is FloatingRecognitionUiState.Failed -> stringResource(R.string.music_recognition_listen_again)
    }
    val glowColor = colors.primary

    Box(Modifier.size(FloatingBubbleFieldSize), contentAlignment = Alignment.Center) {
        if (animationsEnabled) {
            Canvas(Modifier.size(FloatingBubbleFieldSize)) {
                val pulse = level.value
                if (pulse <= 0f) return@Canvas
                val coreRadius = FloatingBubbleCoreSize.toPx() / 2f
                val radius = coreRadius + (size.minDimension / 2f - coreRadius) * pulse
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(glowColor.copy(alpha = 0.32f * pulse), Color.Transparent),
                        center = center,
                        radius = radius,
                    ),
                    radius = radius,
                )
            }
        }
        when (state) {
            FloatingRecognitionUiState.Listening -> CircularWavyProgressIndicator(
                progress = captureProgress,
                modifier = Modifier.size(FloatingBubbleCoreSize + 20.dp),
                color = colors.primary,
                trackColor = colors.surfaceContainerHighest,
                amplitude = { if (animationsEnabled) 0.2f + level.value * 0.8f else 0f },
            )
            FloatingRecognitionUiState.Processing -> if (animationsEnabled) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(FloatingBubbleCoreSize + 20.dp),
                    color = colors.tertiary,
                    trackColor = colors.surfaceContainerHighest,
                )
            }
            else -> Unit
        }
        Box(
            modifier = Modifier
                .size(FloatingBubbleCoreSize)
                .clickable(onClickLabel = clickLabel, role = Role.Button, onClick = onClick)
                .semantics {
                    contentDescription = description
                    liveRegion = LiveRegionMode.Polite
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size(FloatingBubbleCoreSize)
                    .graphicsLayer {
                        val scale = 1f + level.value * FloatingPulseScaleRange
                        scaleX = scale
                        scaleY = scale
                        shadowElevation = 6.dp.toPx()
                        shape = EditorialMorphShape(morphState.morph, morphState.progress.value)
                        clip = true
                    }
                    .drawBehind { drawRect(containerColor.value) },
            )
            FloatingRecognitionCoreContent(state, contentColor, animationsEnabled)
        }
    }
}

@Composable
private fun FloatingRecognitionCoreContent(
    state: FloatingRecognitionUiState,
    contentColor: Color,
    animationsEnabled: Boolean,
) {
    val motion = MaterialTheme.motionScheme
    val artworkShape = MaterialShapes.Cookie12Sided.toShape()
    AnimatedContent(
        targetState = state,
        contentKey = { it::class },
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.6f))
        },
        contentAlignment = Alignment.Center,
        label = "FloatingCoreContent",
    ) { current ->
        val iconModifier = Modifier.size(26.dp)
        when (current) {
            FloatingRecognitionUiState.Idle ->
                Icon(painterResource(R.drawable.mic), null, iconModifier, tint = contentColor)
            FloatingRecognitionUiState.Listening ->
                Icon(painterResource(R.drawable.close), null, Modifier.size(22.dp), tint = contentColor)
            FloatingRecognitionUiState.Processing ->
                if (animationsEnabled) {
                    LoadingIndicator(modifier = Modifier.size(34.dp), color = contentColor)
                } else {
                    Icon(painterResource(R.drawable.cached), null, iconModifier, tint = contentColor)
                }
            is FloatingRecognitionUiState.Matched ->
                CoverArt(current.track.coverArtUrl ?: current.track.coverArtHqUrl, FloatingBubbleCoreSize - 10.dp, artworkShape)
            is FloatingRecognitionUiState.Failed ->
                Icon(
                    painterResource(
                        if (current.failure == MusicRecognitionFailure.RecordingFailed) R.drawable.mic
                        else R.drawable.search_off,
                    ),
                    null,
                    iconModifier,
                    tint = contentColor,
                )
        }
    }
}

@Composable
private fun FloatingRecognitionCard(
    state: FloatingRecognitionUiState?,
    onRetry: () -> Unit,
    onOpenResult: () -> Unit,
    onDismiss: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = state,
        contentKey = { it?.let { current -> current::class } },
        transitionSpec = {
            (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f)) togetherWith
                (fadeOut(motion.fastEffectsSpec()) + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)) using
                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
        },
        contentAlignment = Alignment.Center,
        label = "FloatingCard",
    ) { current ->
        when (current) {
            is FloatingRecognitionUiState.Matched -> FloatingCardSurface(
                onClick = onOpenResult,
                onDismiss = onDismiss,
                overline = stringResource(R.string.music_recognition_match_found),
                title = current.track.title,
                body = current.track.artist,
                overlineColor = MaterialTheme.colorScheme.primary,
            )
            is FloatingRecognitionUiState.Failed -> FloatingCardSurface(
                onClick = onRetry,
                onDismiss = onDismiss,
                overline = null,
                title = stringResource(
                    when (current.failure) {
                        MusicRecognitionFailure.NoMatch -> R.string.music_recognition_no_match
                        MusicRecognitionFailure.RecordingFailed -> R.string.music_recognition_floating_mic_unavailable
                        else -> R.string.music_recognition_error
                    },
                ),
                body = stringResource(R.string.music_recognition_floating_try_again),
                overlineColor = MaterialTheme.colorScheme.error,
            )
            else -> Spacer(Modifier.size(0.dp))
        }
    }
}

@Composable
private fun FloatingCardSurface(
    onClick: () -> Unit,
    onDismiss: () -> Unit,
    overline: String?,
    title: String,
    body: String,
    overlineColor: Color,
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Row(
            modifier = Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.widthIn(max = FloatingCardTextMaxWidth),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                if (overline != null) {
                    Text(overline, style = MaterialTheme.typography.labelMediumEmphasized, color = overlineColor)
                }
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    body,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDismiss) {
                Icon(painterResource(R.drawable.close), stringResource(R.string.close))
            }
        }
    }
}

@Composable
internal fun FloatingRecognitionDismissTarget(hovered: Boolean) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val scale by animateFloatAsState(
        targetValue = if (hovered) 1.2f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "DismissTargetScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (hovered) colors.errorContainer else colors.surfaceContainerHighest,
        animationSpec = motion.defaultEffectsSpec(),
        label = "DismissTargetContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (hovered) colors.onErrorContainer else colors.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "DismissTargetContent",
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Transparent, colors.scrim.copy(alpha = 0.36f))))
            .navigationBarsPadding()
            .padding(top = 56.dp, bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            modifier = Modifier.size(64.dp).graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            shadowElevation = 4.dp,
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.close), contentDescription = null, modifier = Modifier.size(28.dp))
            }
        }
        Surface(
            shape = CircleShape,
            color = colors.inverseSurface,
            contentColor = colors.inverseOnSurface,
        ) {
            Text(
                stringResource(R.string.music_recognition_floating_dismiss),
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

private val FloatingCardTextMaxWidth = 196.dp
private const val FloatingPulseScaleRange = 0.14f
