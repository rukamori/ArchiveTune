/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.coerceAtLeast
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.materialkolor.ktx.toColor
import com.materialkolor.ktx.toHct
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.abs
import kotlin.math.sign

@Immutable
data class V11Palette(
    val field: Color,
    val onField: Color,
    val accent: Color,
    val onAccent: Color,
    val ambient: Color,
)

internal fun v11PaletteFrom(
    seeds: Pair<Color, Color>?,
    darkTheme: Boolean,
    fallback: ColorScheme,
): V11Palette {
    if (seeds == null) {
        return V11Palette(
            field = fallback.surfaceContainerLowest,
            onField = fallback.onSurface,
            accent = fallback.primary,
            onAccent = fallback.onPrimary,
            ambient = fallback.secondaryContainer,
        )
    }
    val primary = seeds.first.toHct()
    val secondary = seeds.second.toHct()
    return if (darkTheme) {
        V11Palette(
            field = primary.withTone(10.0).toColor(),
            onField = primary.withTone(92.0).toColor(),
            accent = primary.withTone(80.0).toColor(),
            onAccent = primary.withTone(20.0).toColor(),
            ambient = secondary.withTone(28.0).toColor(),
        )
    } else {
        V11Palette(
            field = primary.withTone(96.0).toColor(),
            onField = primary.withTone(10.0).toColor(),
            accent = primary.withTone(40.0).toColor(),
            onAccent = primary.withTone(100.0).toColor(),
            ambient = secondary.withTone(86.0).toColor(),
        )
    }
}

@Composable
internal fun animateV11Palette(target: V11Palette): V11Palette {
    val spec = MaterialTheme.motionScheme.slowEffectsSpec<Color>()
    val field by animateColorAsState(target.field, spec, label = "v11Field")
    val onField by animateColorAsState(target.onField, spec, label = "v11OnField")
    val accent by animateColorAsState(target.accent, spec, label = "v11Accent")
    val onAccent by animateColorAsState(target.onAccent, spec, label = "v11OnAccent")
    val ambient by animateColorAsState(target.ambient, spec, label = "v11Ambient")
    return V11Palette(field, onField, accent, onAccent, ambient)
}

@Immutable
private data class V11TrackKey(
    val id: String,
    val index: Int,
)

@Immutable
private data class V11Frame(
    val key: V11TrackKey,
    val metadata: MediaMetadata,
    val artworkUrl: String?,
)

private class V11SwipeState {
    val offset = Animatable(0f)
    var owner by mutableStateOf<String?>(null)
    var direction by mutableIntStateOf(0)
}

@Composable
fun V11PlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    palette: V11Palette,
    queueTitle: String?,
    currentSongLiked: Boolean,
    canvasSource: CanvasSource?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onSleepTimerClick: () -> Unit,
    onMenuClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    landscape: Boolean,
    modifier: Modifier = Modifier,
) {
    val thumbnailSwapState =
        rememberThumbnailSwapState(
            videoId = mediaMetadata.id,
            ytmUrl = mediaMetadata.thumbnailUrl?.highRes(),
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata.isMusicVideo,
        )
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val windowIndex by playerConnection.currentWindowIndex.collectAsState()
    val trackKey = V11TrackKey(mediaMetadata.id, windowIndex)
    val frame = V11Frame(trackKey, mediaMetadata, thumbnailSwapState.displayUrl)
    val swipeState = remember { V11SwipeState() }
    val isBuffering = playbackState == STATE_BUFFERING
    val isEnded = playbackState == STATE_ENDED
    val onPlayPause = {
        if (isEnded) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.player.togglePlayPause()
        }
    }
    val onNext = playerConnection::seekToNext
    val onPrevious = playerConnection::seekToPrevious

    val artwork: @Composable (Dp) -> Unit = { size ->
        V11ArtworkStage(
            frame = frame,
            swipeState = swipeState,
            canvasSource = canvasSource,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            isPlaying = isPlaying,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            palette = palette,
            artSize = size,
            onPlayPause = onPlayPause,
            onNext = onNext,
            onPrevious = onPrevious,
        )
    }
    val header: @Composable () -> Unit = {
        V11Header(
            queueTitle = queueTitle,
            palette = palette,
            onCollapseClick = onCollapseClick,
            onMenuClick = onMenuClick,
        )
    }
    val details: @Composable (compact: Boolean, wide: Boolean) -> Unit = { compact, wide ->
        V11Metadata(
            frame = frame,
            swipeState = swipeState,
            palette = palette,
            titleStyleLevel = if (wide) 2 else if (compact) 0 else 1,
            onTitleClick = titleActions.onTitleClick,
            onTitleLongClick = titleActions.onCopyTitle,
            onArtistClick = titleActions.onArtistClick,
            onArtistLongClick = titleActions.onCopyArtists,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        V11SeekSection(
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            isPlaying = isPlaying,
            palette = palette,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
        )
        Spacer(Modifier.height(if (compact) 12.dp else 20.dp))
        V11TransportGroup(
            isPlaying = isPlaying,
            isBuffering = isBuffering,
            isEnded = isEnded,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            palette = palette,
            height = if (wide) 88.dp else if (compact) 64.dp else 76.dp,
            onPrevious = onPrevious,
            onPlayPause = onPlayPause,
            onNext = onNext,
        )
    }
    val toolbar: @Composable (narrow: Boolean) -> Unit = { narrow ->
        V11ActionToolbar(
            palette = palette,
            liked = currentSongLiked,
            sleepTimerEnabled = sleepTimerEnabled,
            sleepTimerTimeLeft = sleepTimerTimeLeft,
            showSleepCountdown = !narrow,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            onLikeClick = playerConnection::toggleLike,
            onAddToPlaylistClick = onAddToPlaylistClick,
            onSleepTimerClick = onSleepTimerClick,
        )
    }

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(palette.field, lerp(palette.field, palette.ambient, 0.4f)),
                    ),
                ),
    ) {
        val narrow = maxWidth < 360.dp
        if (landscape) {
            Column(modifier = Modifier.fillMaxSize()) {
                header()
                Row(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    BoxWithConstraints(
                        modifier = Modifier.weight(0.45f).fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        artwork(min(maxWidth, maxHeight) * 0.82f)
                    }
                    BoxWithConstraints(
                        modifier = Modifier.weight(0.55f).fillMaxSize().padding(end = 24.dp),
                    ) {
                        val paneHeight = maxHeight
                        Column(modifier = Modifier.fillMaxSize()) {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState())
                                        .heightIn(min = (paneHeight - 88.dp).coerceAtLeast(0.dp)),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                details(true, false)
                            }
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                toolbar(narrow)
                            }
                        }
                    }
                }
            }
        } else {
            val wide = maxWidth >= 600.dp
            val compact = maxHeight < 700.dp
            Column(
                modifier =
                    Modifier
                        .align(Alignment.TopCenter)
                        .widthIn(max = 560.dp)
                        .fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                header()
                BoxWithConstraints(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    val cap = if (wide) 480.dp else 400.dp
                    artwork(minOf(maxWidth - 64.dp, maxHeight * 0.86f, cap))
                }
                Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
                    details(compact, wide)
                }
                Spacer(Modifier.height(if (compact) 12.dp else 24.dp))
                toolbar(narrow)
                Spacer(Modifier.height(if (compact) 8.dp else 16.dp))
            }
        }
    }
}

@Composable
private fun V11Header(
    queueTitle: String?,
    palette: V11Palette,
    onCollapseClick: () -> Unit,
    onMenuClick: () -> Unit,
) {
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = palette.onField)
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapseClick, shapes = IconButtonDefaults.shapes(), colors = iconColors) {
            Icon(
                painter = painterResource(R.drawable.expand_more),
                contentDescription = stringResource(R.string.player_collapse),
            )
        }
        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.now_playing),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.accent,
                maxLines = 1,
            )
            if (!queueTitle.isNullOrBlank()) {
                Text(
                    text = queueTitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.onField.copy(alpha = 0.72f),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
            }
        }
        IconButton(onClick = onMenuClick, shapes = IconButtonDefaults.shapes(), colors = iconColors) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.more_label),
            )
        }
    }
}

@Composable
private fun V11ArtworkStage(
    frame: V11Frame,
    swipeState: V11SwipeState,
    canvasSource: CanvasSource?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    palette: V11Palette,
    artSize: Dp,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val haptic = LocalHapticFeedback.current
    val (hapticsEnabled) = rememberPreference(EnableHapticFeedbackKey, true)
    val scope = rememberCoroutineScope()

    val ambientRotation = remember { Animatable(0f) }
    LaunchedEffect(isPlaying, animationsDisabled) {
        if (!isPlaying || animationsDisabled) return@LaunchedEffect
        while (true) {
            ambientRotation.snapTo(ambientRotation.value % 360f)
            ambientRotation.animateTo(
                targetValue = ambientRotation.value + 360f,
                animationSpec = tween(durationMillis = 32_000, easing = LinearEasing),
            )
        }
    }
    val ambientScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.86f,
        animationSpec = motion.slowSpatialSpec(),
        label = "v11AmbientScale",
    )
    val cardScale by animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0.9f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "v11CardScale",
    )
    val cardCorner by animateDpAsState(
        targetValue = if (isPlaying) artSize * 0.1f else artSize * 0.2f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "v11CardCorner",
    )

    val latestKey by rememberUpdatedState(frame.key)
    val latestHapticsEnabled by rememberUpdatedState(hapticsEnabled)
    val latestCanSkipNext by rememberUpdatedState(canSkipNext)
    val latestCanSkipPrevious by rememberUpdatedState(canSkipPrevious)
    val latestOnNext by rememberUpdatedState(onNext)
    val latestOnPrevious by rememberUpdatedState(onPrevious)

    val nextLabel = stringResource(R.string.next)
    val previousLabel = stringResource(R.string.player_previous)
    val playLabel = stringResource(R.string.play)
    val pauseLabel = stringResource(R.string.player_pause)
    val playStateLabel = stringResource(if (isPlaying) R.string.player_state_playing else R.string.player_state_paused)
    val ambientShape = MaterialShapes.Cookie12Sided.toShape()

    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier =
                Modifier
                    .size(artSize * 1.22f)
                    .graphicsLayer {
                        rotationZ = ambientRotation.value
                        scaleX = ambientScale
                        scaleY = ambientScale
                    }.background(palette.ambient.copy(alpha = 0.7f), ambientShape),
        )
        AnimatedContent(
            targetState = frame,
            contentKey = { it.key.id },
            transitionSpec = {
                val swipe = swipeState.direction
                val forward = if (swipe != 0) swipe > 0 else targetState.key.index >= initialState.key.index
                val sideSign = (if (forward) 1 else -1) * (if (isRtl) -1 else 1)
                val enter =
                    slideInHorizontally(motion.defaultSpatialSpec()) { full -> sideSign * full } +
                        fadeIn(motion.defaultEffectsSpec())
                val exit =
                    if (swipe != 0) {
                        fadeOut(snap())
                    } else {
                        slideOutHorizontally(motion.defaultSpatialSpec()) { full -> -sideSign * full } +
                            fadeOut(motion.fastEffectsSpec())
                    }
                enter togetherWith exit
            },
            label = "v11Artwork",
        ) { shown ->
            val shape = RoundedCornerShape(cardCorner)
            Box(
                modifier =
                    Modifier
                        .size(artSize)
                        .graphicsLayer {
                            val drag = if (shown.key.id == swipeState.owner) swipeState.offset.value else 0f
                            translationX = drag
                            rotationZ = if (animationsDisabled) 0f else drag / artSize.toPx() * 10f
                            scaleX = cardScale
                            scaleY = cardScale
                            shadowElevation = 20.dp.toPx()
                            this.shape = shape
                            clip = true
                        }.background(palette.onField.copy(alpha = 0.12f))
                        .pointerInput(isRtl) {
                            val tracker = VelocityTracker()
                            val nextSign = if (isRtl) 1f else -1f
                            fun allowed(offset: Float): Boolean =
                                if (offset * nextSign > 0f) latestCanSkipNext else latestCanSkipPrevious
                            detectHorizontalDragGestures(
                                onDragStart = {
                                    tracker.resetTracking()
                                    swipeState.owner = latestKey.id
                                    scope.launch { swipeState.offset.snapTo(0f) }
                                },
                                onHorizontalDrag = { change, amount ->
                                    change.consume()
                                    tracker.addPointerInputChange(change)
                                    val proposed = swipeState.offset.value + amount
                                    val limit = this.size.width * 0.12f
                                    val next =
                                        if (allowed(proposed)) {
                                            proposed
                                        } else {
                                            (swipeState.offset.value + amount * 0.3f).coerceIn(-limit, limit)
                                        }
                                    scope.launch { swipeState.offset.snapTo(next) }
                                },
                                onDragCancel = {
                                    scope.launch { swipeState.offset.animateTo(0f, motion.fastSpatialSpec()) }
                                },
                                onDragEnd = {
                                    val offset = swipeState.offset.value
                                    val velocity = tracker.calculateVelocity().x
                                    val width = this.size.width.toFloat()
                                    val flung = abs(velocity) > 900.dp.toPx() && sign(velocity) == sign(offset)
                                    val commit = offset != 0f && allowed(offset) && (abs(offset) > width * 0.28f || flung)
                                    scope.launch {
                                        if (!commit) {
                                            swipeState.offset.animateTo(0f, motion.fastSpatialSpec())
                                            return@launch
                                        }
                                        val startId = latestKey.id
                                        val goesNext = offset * nextSign > 0f
                                        swipeState.offset.animateTo(sign(offset) * width * 1.3f, motion.fastSpatialSpec())
                                        if (latestHapticsEnabled) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        swipeState.direction = if (goesNext) 1 else -1
                                        if (goesNext) latestOnNext() else latestOnPrevious()
                                        val changed =
                                            withTimeoutOrNull(1_500L) {
                                                snapshotFlow { latestKey.id }.first { it != startId }
                                            }
                                        swipeState.direction = 0
                                        if (changed == null) {
                                            swipeState.offset.animateTo(0f, motion.defaultSpatialSpec())
                                        } else {
                                            swipeState.offset.snapTo(0f)
                                        }
                                        swipeState.owner = null
                                    }
                                },
                            )
                        }.semantics {
                            stateDescription = playStateLabel
                            customActions =
                                buildList {
                                    if (canSkipNext) add(CustomAccessibilityAction(nextLabel) { onNext(); true })
                                    if (canSkipPrevious) add(CustomAccessibilityAction(previousLabel) { onPrevious(); true })
                                }
                        }.clickable(
                            onClickLabel = if (isPlaying) pauseLabel else playLabel,
                            role = Role.Button,
                            onClick = onPlayPause,
                        ),
            ) {
                AsyncImage(
                    model = rememberOfflineArtworkImageRequest(shown.artworkUrl),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val drag = if (shown.key.id == swipeState.owner) swipeState.offset.value else 0f
                                translationX = -drag * 0.12f
                                scaleX = 1.12f
                                scaleY = 1.12f
                            },
                )
                if (shown.key.id == frame.key.id && (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank())) {
                    CanvasArtworkPlayer(
                        source = canvasSource,
                        primaryUrl = canvasPrimaryUrl,
                        fallbackUrl = canvasFallbackUrl,
                        isPlaying = isPlaying,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }
}

@Composable
private fun V11Metadata(
    frame: V11Frame,
    swipeState: V11SwipeState,
    palette: V11Palette,
    titleStyleLevel: Int,
    onTitleClick: () -> Unit,
    onTitleLongClick: () -> Unit,
    onArtistClick: (String) -> Unit,
    onArtistLongClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val titleStyle =
        when (titleStyleLevel) {
            0 -> MaterialTheme.typography.headlineSmall
            1 -> MaterialTheme.typography.headlineMedium
            else -> MaterialTheme.typography.displaySmall
        }
    AnimatedContent(
        targetState = frame,
        contentKey = { it.key.id },
        transitionSpec = {
            val swipe = swipeState.direction
            val forward = if (swipe != 0) swipe > 0 else targetState.key.index >= initialState.key.index
            val sideSign = (if (forward) 1 else -1) * (if (isRtl) -1 else 1)
            (slideInHorizontally(motion.defaultSpatialSpec()) { full -> sideSign * full / 4 } + fadeIn(motion.defaultEffectsSpec()))
                .togetherWith(
                    slideOutHorizontally(motion.fastSpatialSpec()) { full -> -sideSign * full / 4 } + fadeOut(motion.fastEffectsSpec()),
                )
        },
        contentAlignment = Alignment.CenterStart,
        label = "v11Metadata",
        modifier = Modifier.fillMaxWidth(),
    ) { shown ->
        val mediaMetadata = shown.metadata
        Column(modifier = Modifier.fillMaxWidth()) {
            PlayerTitleText(
                title = mediaMetadata.title,
                explicit = mediaMetadata.explicit,
                color = palette.onField,
                style = titleStyle,
                fontWeight = FontWeight.Bold,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .basicMarquee()
                        .combinedClickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onTitleClick,
                            onLongClick = onTitleLongClick,
                        ),
            )
            Spacer(Modifier.height(4.dp))
            ClickableArtists(
                artists = mediaMetadata.artists,
                onArtistClick = onArtistClick,
                onLongClick = onArtistLongClick,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = palette.accent,
                modifier = Modifier.fillMaxWidth().basicMarquee(),
            )
        }
    }
}

@Composable
private fun V11SeekSection(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    palette: V11Palette,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val knownDuration = duration > 0L
    val (smoothFraction, displayedPosition) =
        rememberSmoothProgress(
            isPlayingProvider = { isPlaying },
            currentPositionProvider = { sliderPosition ?: position },
            totalDuration = duration.coerceAtLeast(0L),
        )
    val scrubbing = sliderPosition != null
    val timeScale by animateFloatAsState(
        targetValue = if (scrubbing) 1.18f else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "v11TimeScale",
    )
    Column(modifier = Modifier.fillMaxWidth()) {
        WavySliderExpressive(
            value = { smoothFraction.value },
            onValueChange = { fraction -> onSliderValueChange((fraction * duration.coerceAtLeast(0L)).toLong()) },
            onValueCommit = { onSliderValueChangeFinished() },
            enabled = knownDuration,
            activeTrackColor = palette.accent,
            inactiveTrackColor = palette.onField.copy(alpha = 0.18f),
            thumbColor = palette.accent,
            isPlaying = isPlaying,
            strokeWidth = 6.dp,
            thumbRadius = 9.dp,
            modifier = Modifier.fillMaxWidth().height(40.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (knownDuration) makeTimeString(sliderPosition ?: displayedPosition.value) else "--:--",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = if (scrubbing) palette.accent else palette.onField.copy(alpha = 0.78f),
                maxLines = 1,
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = timeScale
                        scaleY = timeScale
                        transformOrigin = TransformOrigin(0f, 0.5f)
                    },
            )
            Text(
                text = if (knownDuration) makeTimeString(duration) else "--:--",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = palette.onField.copy(alpha = 0.78f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun V11TransportGroup(
    isPlaying: Boolean,
    isBuffering: Boolean,
    isEnded: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    palette: V11Palette,
    height: Dp,
    onPrevious: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val previousLabel = stringResource(R.string.player_previous)
    val nextLabel = stringResource(R.string.next)
    val playPauseLabel = stringResource(if (isPlaying) R.string.player_pause else R.string.play)
    val playStateLabel = stringResource(if (isPlaying) R.string.player_state_playing else R.string.player_state_paused)
    val tonal = palette.accent.copy(alpha = 0.18f)
    val motion = MaterialTheme.motionScheme

    ButtonGroup(
        overflowIndicator = { menuState ->
            IconButton(
                onClick = { if (menuState.isShowing) menuState.dismiss() else menuState.show() },
                shapes = IconButtonDefaults.shapes(),
                colors = IconButtonDefaults.iconButtonColors(contentColor = palette.onField),
            ) {
                Icon(painterResource(R.drawable.more_horiz), contentDescription = stringResource(R.string.more_label))
            }
        },
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                V11MorphButton(
                    onClick = onPrevious,
                    enabled = canSkipPrevious,
                    containerColor = tonal,
                    contentColor = palette.onField,
                    height = height,
                    restingRound = true,
                    interactionSource = interaction,
                    modifier = Modifier.weight(1f).animateWidth(interaction),
                ) {
                    Icon(painterResource(R.drawable.skip_previous), contentDescription = previousLabel, modifier = Modifier.size(height * 0.4f))
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text(previousLabel) },
                    enabled = canSkipPrevious,
                    leadingIcon = { Icon(painterResource(R.drawable.skip_previous), contentDescription = null) },
                    onClick = {
                        onPrevious()
                        menuState.dismiss()
                    },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                V11MorphButton(
                    onClick = onPlayPause,
                    enabled = true,
                    containerColor = palette.accent,
                    contentColor = palette.onAccent,
                    height = height,
                    restingRound = !isPlaying,
                    interactionSource = interaction,
                    modifier =
                        Modifier
                            .weight(1.7f)
                            .animateWidth(interaction)
                            .semantics { stateDescription = playStateLabel },
                ) {
                    AnimatedContent(
                        targetState = Triple(isBuffering, isPlaying, isEnded),
                        transitionSpec = {
                            (scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()))
                                .togetherWith(scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()))
                        },
                        label = "v11PlayIcon",
                    ) { (buffering, playing, ended) ->
                        if (buffering) {
                            LoadingIndicator(
                                color = palette.onAccent,
                                modifier = Modifier.size(height * 0.56f),
                            )
                        } else {
                            Icon(
                                painter =
                                    painterResource(
                                        when {
                                            ended -> R.drawable.replay
                                            playing -> R.drawable.pause
                                            else -> R.drawable.play
                                        },
                                    ),
                                contentDescription = playPauseLabel,
                                modifier = Modifier.size(height * 0.46f),
                            )
                        }
                    }
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text(playPauseLabel) },
                    leadingIcon = {
                        Icon(painterResource(if (isPlaying) R.drawable.pause else R.drawable.play), contentDescription = null)
                    },
                    onClick = {
                        onPlayPause()
                        menuState.dismiss()
                    },
                )
            },
        )
        customItem(
            buttonGroupContent = {
                val interaction = remember { MutableInteractionSource() }
                V11MorphButton(
                    onClick = onNext,
                    enabled = canSkipNext,
                    containerColor = tonal,
                    contentColor = palette.onField,
                    height = height,
                    restingRound = true,
                    interactionSource = interaction,
                    modifier = Modifier.weight(1f).animateWidth(interaction),
                ) {
                    Icon(painterResource(R.drawable.skip_next), contentDescription = nextLabel, modifier = Modifier.size(height * 0.4f))
                }
            },
            menuContent = { menuState ->
                DropdownMenuItem(
                    text = { Text(nextLabel) },
                    enabled = canSkipNext,
                    leadingIcon = { Icon(painterResource(R.drawable.skip_next), contentDescription = null) },
                    onClick = {
                        onNext()
                        menuState.dismiss()
                    },
                )
            },
        )
    }
}

@Composable
private fun V11MorphButton(
    onClick: () -> Unit,
    enabled: Boolean,
    containerColor: Color,
    contentColor: Color,
    height: Dp,
    restingRound: Boolean,
    interactionSource: MutableInteractionSource,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateDpAsState(
        targetValue =
            when {
                pressed -> height * 0.2f
                restingRound -> height / 2
                else -> height * 0.3f
            },
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "v11ButtonCorner",
    )
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(corner),
        color = if (enabled) containerColor else containerColor.copy(alpha = containerColor.alpha * 0.4f),
        contentColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f),
        interactionSource = interactionSource,
        modifier = modifier.height(height),
    ) {
        Box(contentAlignment = Alignment.Center) {
            content()
        }
    }
}

@Composable
private fun V11ActionToolbar(
    palette: V11Palette,
    liked: Boolean,
    sleepTimerEnabled: Boolean,
    sleepTimerTimeLeft: Long,
    showSleepCountdown: Boolean,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onLikeClick: () -> Unit,
    onAddToPlaylistClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val scope = rememberCoroutineScope()
    val likeScale = remember { Animatable(1f) }
    val iconColors = IconButtonDefaults.iconButtonColors(contentColor = palette.onAccent)
    val countdown = makeTimeString(sleepTimerTimeLeft.coerceAtLeast(0L))
    val sleepDescription =
        if (sleepTimerEnabled) {
            stringResource(R.string.player_sleep_timer_remaining, countdown)
        } else {
            stringResource(R.string.sleep_timer)
        }

    HorizontalFloatingToolbar(
        expanded = true,
        colors = FloatingToolbarDefaults.standardFloatingToolbarColors(toolbarContainerColor = palette.accent),
    ) {
        IconButton(onClick = onQueueClick, shapes = IconButtonDefaults.shapes(), colors = iconColors) {
            Icon(painterResource(R.drawable.queue_music), contentDescription = stringResource(R.string.queue))
        }
        IconButton(onClick = onLyricsClick, shapes = IconButtonDefaults.shapes(), colors = iconColors) {
            Icon(painterResource(R.drawable.lyrics), contentDescription = stringResource(R.string.lyrics))
        }
        FilledTonalIconToggleButton(
            checked = liked,
            onCheckedChange = {
                onLikeClick()
                if (!animationsDisabled) {
                    scope.launch {
                        likeScale.snapTo(0.6f)
                        likeScale.animateTo(1f, motion.fastSpatialSpec())
                    }
                }
            },
            shapes = IconButtonDefaults.toggleableShapes(),
            colors =
                IconButtonDefaults.filledTonalIconToggleButtonColors(
                    containerColor = Color.Transparent,
                    contentColor = palette.onAccent,
                    checkedContainerColor = palette.onAccent,
                    checkedContentColor = palette.accent,
                ),
        ) {
            Icon(
                painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(if (liked) R.string.action_remove_like else R.string.action_like),
                modifier =
                    Modifier.graphicsLayer {
                        scaleX = likeScale.value
                        scaleY = likeScale.value
                    },
            )
        }
        IconButton(onClick = onAddToPlaylistClick, shapes = IconButtonDefaults.shapes(), colors = iconColors) {
            Icon(painterResource(R.drawable.library_add), contentDescription = stringResource(R.string.add_to_playlist))
        }
        if (sleepTimerEnabled && showSleepCountdown) {
            Surface(
                onClick = onSleepTimerClick,
                shape = CircleShape,
                color = palette.onAccent,
                contentColor = palette.accent,
                modifier =
                    Modifier
                        .heightIn(min = 48.dp)
                        .padding(vertical = 4.dp)
                        .animateContentSize(motion.fastSpatialSpec())
                        .semantics { this.stateDescription = sleepDescription },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        painterResource(R.drawable.bedtime),
                        contentDescription = stringResource(R.string.sleep_timer),
                        modifier = Modifier.size(20.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = countdown,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Clip,
                    )
                }
            }
        } else {
            IconButton(
                onClick = onSleepTimerClick,
                shapes = IconButtonDefaults.shapes(),
                colors =
                    if (sleepTimerEnabled) {
                        IconButtonDefaults.iconButtonColors(containerColor = palette.onAccent, contentColor = palette.accent)
                    } else {
                        iconColors
                    },
            ) {
                Icon(painterResource(R.drawable.bedtime), contentDescription = sleepDescription)
            }
        }
    }
}
