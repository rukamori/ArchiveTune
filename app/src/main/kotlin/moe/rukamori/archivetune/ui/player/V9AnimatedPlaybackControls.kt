package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R

private enum class V9PlaybackButtonType { NONE, PREVIOUS, PLAY_PAUSE, NEXT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun V9AnimatedPlaybackControls(
    isPlayingProvider: () -> Boolean,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 90.dp,
    baseWeight: Float = 1f,
    expansionWeight: Float = 1.1f,
    compressionWeight: Float = 0.65f,
    pressAnimationSpec: AnimationSpec<Float>,
    releaseDelay: Long = 220L,
    playPauseCornerPlaying: Dp = 60.dp,
    playPauseCornerPaused: Dp = 26.dp,
    colorOtherButtons: Color = MaterialTheme.colorScheme.secondaryContainer,
    colorPlayPause: Color = MaterialTheme.colorScheme.primary,
    tintPlayPauseIcon: Color = MaterialTheme.colorScheme.onPrimary,
    tintOtherIcons: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    colorPreviousButton: Color = colorOtherButtons,
    colorNextButton: Color = colorOtherButtons,
    tintPreviousIcon: Color = tintOtherIcons,
    tintNextIcon: Color = tintOtherIcons,
    playPauseIconSize: Dp = 36.dp,
    iconSize: Dp = 32.dp,
) {
    val isPlaying = isPlayingProvider()
    var lastClicked by remember { mutableStateOf<V9PlaybackButtonType?>(null) }
    var clickTrigger by remember { mutableStateOf(0) }
    val latestIsPlayingProvider by rememberUpdatedState(newValue = isPlayingProvider)
    val latestLastClicked by rememberUpdatedState(newValue = lastClicked)
    val isPlayPauseLocked =
        lastClicked == V9PlaybackButtonType.NEXT || lastClicked == V9PlaybackButtonType.PREVIOUS
    var playPauseVisualState by remember { mutableStateOf(isPlaying) }
    var pendingPlayPauseState by remember { mutableStateOf<Boolean?>(null) }
    val hapticFeedback = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()

    val motionScheme = remember { MotionScheme.expressive() }
    val defaultSpatialDpSpec = remember { motionScheme.defaultSpatialSpec<Dp>() }

    LaunchedEffect(lastClicked, clickTrigger) {
        if (lastClicked != null) {
            val delayTime = when (lastClicked) {
                V9PlaybackButtonType.NEXT, V9PlaybackButtonType.PREVIOUS -> 600L
                else -> releaseDelay
            }
            delay(delayTime)
            lastClicked = null
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            pendingPlayPauseState = true
            return@LaunchedEffect
        }

        val shouldDelay = latestLastClicked != V9PlaybackButtonType.PLAY_PAUSE
        if (shouldDelay) {
            delay(releaseDelay)
        }
        if (!latestIsPlayingProvider()) {
            pendingPlayPauseState = false
        }
    }

    LaunchedEffect(isPlayPauseLocked, pendingPlayPauseState) {
        if (!isPlayPauseLocked) {
            pendingPlayPauseState?.let {
                playPauseVisualState = it
                pendingPlayPauseState = null
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
    ) {
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            fun weightFor(button: V9PlaybackButtonType): Float = when (lastClicked) {
                button -> expansionWeight
                null -> baseWeight
                else -> compressionWeight
            }

            val prevWeight by animateFloatAsState(
                targetValue = weightFor(V9PlaybackButtonType.PREVIOUS),
                animationSpec = pressAnimationSpec,
                label = "prevWeight"
            )
            Box(
                modifier = Modifier
                    .weight(prevWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorPreviousButton)
                    .clickable {
                        lastClicked = V9PlaybackButtonType.PREVIOUS
                        clickTrigger++
                        coroutineScope.launch {
                            delay(180)
                            onPrevious()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_previous),
                    contentDescription = stringResource(R.string.widget_previous),
                    tint = tintPreviousIcon,
                    modifier = Modifier.size(iconSize)
                )
            }

            val playWeight by animateFloatAsState(
                targetValue = weightFor(V9PlaybackButtonType.PLAY_PAUSE),
                animationSpec = pressAnimationSpec,
                label = "playWeight"
            )
            val playCorner by animateDpAsState(
                targetValue = if (!playPauseVisualState) playPauseCornerPlaying else playPauseCornerPaused,
                animationSpec = defaultSpatialDpSpec,
                label = "playCorner"
            )
            Box(
                modifier = Modifier
                    .weight(playWeight)
                    .fillMaxHeight()
                    .graphicsLayer {
                        clip = true
                        shape = RoundedCornerShape(playCorner)
                    }
                    .background(colorPlayPause)
                    .clickable {
                        lastClicked = V9PlaybackButtonType.PLAY_PAUSE
                        clickTrigger++
                        hapticFeedback.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onPlayPause()
                    },
                contentAlignment = Alignment.Center
            ) {
                V9MorphingPlayPauseIcon(
                    isPlaying = playPauseVisualState,
                    tint = tintPlayPauseIcon,
                    size = playPauseIconSize,
                    motionScheme = motionScheme
                )
            }

            val nextWeight by animateFloatAsState(
                targetValue = weightFor(V9PlaybackButtonType.NEXT),
                animationSpec = pressAnimationSpec,
                label = "nextWeight"
            )
            Box(
                modifier = Modifier
                    .weight(nextWeight)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(colorNextButton)
                    .clickable {
                        lastClicked = V9PlaybackButtonType.NEXT
                        clickTrigger++
                        coroutineScope.launch {
                            delay(180)
                            onNext()
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.skip_next),
                    contentDescription = stringResource(R.string.next),
                    tint = tintNextIcon,
                    modifier = Modifier.size(iconSize)
                )
            }
        }
    }
}

@Composable
private fun V9MorphingPlayPauseIcon(
    isPlaying: Boolean,
    tint: Color,
    size: Dp,
    motionScheme: MotionScheme
) {
    Crossfade(
        targetState = isPlaying,
        animationSpec = motionScheme.fastEffectsSpec(),
        label = "v9PlayPauseCrossfade"
    ) { playing ->
        Icon(
            painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
            contentDescription = if (playing) {
                stringResource(R.string.widget_pause)
            } else {
                stringResource(R.string.play)
            },
            tint = tint,
            modifier = Modifier.size(size)
        )
    }
}
