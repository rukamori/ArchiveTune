/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.res.Configuration
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.accessibility.AccessibilityManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalAccessibilityManager
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.media3.common.C
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_READY
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import dagger.hilt.android.EntryPointAccessors
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.LyricsBackgroundStyle
import moe.rukamori.archivetune.constants.LyricsAutoHideControlsKey
import moe.rukamori.archivetune.constants.LyricsBackgroundStyleKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerCustomBlurKey
import moe.rukamori.archivetune.constants.PlayerCustomBrightnessKey
import moe.rukamori.archivetune.constants.PlayerCustomContrastKey
import moe.rukamori.archivetune.constants.PlayerCustomImageUriKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.needsRemoteFetch
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsSearchingIndicator
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.component.buildLyricsSourceLabel
import moe.rukamori.archivetune.ui.component.rememberControlledSliderState
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.rememberArchiveTuneLyricsFontFamily
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsRenderScreenState
import moe.rukamori.archivetune.viewmodels.LyricsRenderViewModel
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val AppleMusicFallbackGradient =
    listOf(
        Color(0xFF202020),
        Color(0xFF141414),
        Color(0xFF050505),
    )

private const val ARTWORK_BLUR_OVERSCAN = 1.35f
private const val ARTWORK_DRIFT_SCALE = 0.18f
private const val ARTWORK_DRIFT_ROTATION_DEGREES = 8f
private const val ARTWORK_DRIFT_PERIOD_MS = 32_000
private const val LYRICS_CONTROLS_AUTO_HIDE_DELAY_MS = 5_000L
private const val CONTROLS_MOTION_STIFFNESS = 220f
private const val CONTROLS_FADE_IN_MS = 280
private const val CONTROLS_FADE_OUT_MS = 220
private const val LANDSCAPE_CONTROLS_WIDTH_FRACTION = 0.425f
private const val IDLE_TIME_LABEL_ALPHA = 0.54f
private const val SCRUBBING_TIME_LABEL_ALPHA = 0.9f
private val AppleMusicTrackHeight = 6.dp
private val AppleMusicActiveTrackHeight = 12.dp
private val EnhancedLyricsHorizontalPadding = 8.dp
private val V2LyricsHorizontalPadding = 24.dp

@Suppress("UNUSED_PARAMETER")
@Composable
fun LyricsScreen(
    mediaMetadata: MediaMetadata,
    onBackClick: () -> Unit,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onQueueClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
    backHandlerEnabled: Boolean = true,
    lyricsRenderViewModel: LyricsRenderViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val player = playerConnection.player
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val view = LocalView.current

    val playbackState by playerConnection.playbackState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onVolumeChange =
        remember(deviceMusicVolumeController) {
            { volume: Float ->
                deviceMusicVolumeController.setVolumeFraction(volume)
            }
        }
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val lyricsRenderState by lyricsRenderViewModel.state.collectAsStateWithLifecycle()
    val lyricsSourceLabel =
        currentLyrics
            ?.takeIf { entity -> entity.lyrics != LyricsEntity.LYRICS_NOT_FOUND }
            ?.let { entity ->
                when {
                    entity.source == LyricsEntity.Source.EMBEDDED.value -> {
                        AnnotatedString(
                            stringResource(R.string.lyrics_source_label, stringResource(R.string.lyrics_source_embedded)),
                        )
                    }

                    entity.provider != null -> {
                        val label = stringResource(R.string.lyrics_source_label, entity.provider)
                        remember(label, entity.provider, entity.providerUrl, entity.providerAuthor, entity.providerAuthorUrl) {
                            buildLyricsSourceLabel(
                                text = label,
                                providerName = entity.provider,
                                providerUrl = entity.providerUrl,
                                author = entity.providerAuthor,
                                authorUrl = entity.providerAuthorUrl,
                            )
                        }
                    }

                    else -> {
                        null
                    }
                }
            }

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)
    val playerBackground by rememberEnumPreference(PlayerBackgroundStyleKey, PlayerBackgroundStyle.DEFAULT)
    val configuredLyricsBackground by rememberEnumPreference(LyricsBackgroundStyleKey, LyricsBackgroundStyle.DEFAULT)
    val lyricsBackground = configuredLyricsBackground.resolveFor(playerBackground)
    val disableBlur by rememberPreference(DisableBlurKey, false)
    val blurRadius by rememberPreference(BlurRadiusKey, 48f)
    val playerCustomImageUri by rememberPreference(PlayerCustomImageUriKey, "")
    val playerCustomBlur by rememberPreference(PlayerCustomBlurKey, 0f)
    val playerCustomContrast by rememberPreference(PlayerCustomContrastKey, 1f)
    val playerCustomBrightness by rememberPreference(PlayerCustomBrightnessKey, 1f)
    val foregroundColor =
        if (lyricsBackground == LyricsBackgroundStyle.FOLLOW_THEME) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }
    val autoHideControlsState =
        rememberPreference(LyricsAutoHideControlsKey, false)
    val autoHideControls by autoHideControlsState
    val onAutoHideControlsChange =
        remember(autoHideControlsState) {
            { enabled: Boolean ->
                autoHideControlsState.value = enabled
            }
        }
    val composeAccessibilityManager = LocalAccessibilityManager.current
    val platformAccessibilityManager =
        remember(context) { context.getSystemService(AccessibilityManager::class.java) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsInteraction by remember { mutableIntStateOf(0) }

    val hapticClick =
        remember(enableHapticFeedback, view) {
            {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        HapticFeedbackConstants.CONTEXT_CLICK,
                        HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
            }
        }
    val lyricsHelper =
        remember(context) {
            EntryPointAccessors
                .fromApplication(
                    context.applicationContext,
                    moe.rukamori.archivetune.di.LyricsHelperEntryPoint::class.java,
                ).lyricsHelper()
    }

    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics) {
        if (mediaMetadata.isPodcast) return@LaunchedEffect
        if (!currentLyrics.needsRemoteFetch()) return@LaunchedEffect
        try {
            val existingLyrics =
                withContext(Dispatchers.IO) {
                    database.lyrics(mediaMetadata.id).first()
                }
            if (!existingLyrics.needsRemoteFetch()) return@LaunchedEffect

            val fetched =
                withContext(Dispatchers.IO) {
                    lyricsHelper.getLyrics(mediaMetadata)
                }
            if (!fetched.isConclusive) return@LaunchedEffect
            withContext(Dispatchers.IO) {
                database.query {
                    saveFetchedLyrics(
                        id = mediaMetadata.id,
                        lyrics = fetched.lyrics,
                        provider = fetched.providerName,
                        providerUrl = fetched.providerUrl,
                        providerAuthor = fetched.author,
                        providerAuthorUrl = fetched.authorUrl,
                    )
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    val positionState = remember(mediaMetadata.id) { mutableLongStateOf(0L) }
    val durationState = remember(mediaMetadata.id) { mutableLongStateOf(C.TIME_UNSET) }
    var sliderPosition by remember(mediaMetadata.id) { mutableStateOf<Long?>(null) }
    var gradientColors by remember(mediaMetadata.thumbnailUrl) { mutableStateOf(AppleMusicFallbackGradient) }
    val isScrubbing = sliderPosition != null

    LaunchedEffect(autoHideControls, isPlaying, isScrubbing, controlsInteraction) {
        controlsVisible = true
        if (!autoHideControls || !isPlaying || isScrubbing) return@LaunchedEffect
        if (platformAccessibilityManager?.isTouchExplorationEnabled == true) return@LaunchedEffect
        val hideDelayMillis =
            composeAccessibilityManager?.calculateRecommendedTimeoutMillis(
                originalTimeoutMillis = LYRICS_CONTROLS_AUTO_HIDE_DELAY_MS,
                containsIcons = true,
                containsText = true,
                containsControls = true,
            ) ?: LYRICS_CONTROLS_AUTO_HIDE_DELAY_MS
        delay(hideDelayMillis)
        controlsVisible = false
    }

    val lyricsDurationMs = durationState.longValue.takeIf { it != C.TIME_UNSET } ?: 0L
    LaunchedEffect(mediaMetadata.id, lyricsDurationMs) {
        lyricsRenderViewModel.bind(mediaMetadata.id, lyricsDurationMs)
    }

    val gradientColorsCache =
        remember {
            object : LinkedHashMap<String, List<Color>>(20, 0.75f, true) {
                override fun removeEldestEntry(eldest: Map.Entry<String, List<Color>>) = size > 20
            }
        }
    val fallbackColor = remember { Color.Black.toArgb() }

    LaunchedEffect(mediaMetadata.id, mediaMetadata.thumbnailUrl, lyricsBackground) {
        if (lyricsBackground != LyricsBackgroundStyle.DEFAULT && lyricsBackground != LyricsBackgroundStyle.COLORING) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }
        val thumbnailUrl = mediaMetadata.thumbnailUrl
        if (thumbnailUrl == null) {
            gradientColors = AppleMusicFallbackGradient
            return@LaunchedEffect
        }

        gradientColorsCache[thumbnailUrl]?.let {
            gradientColors = it
            return@LaunchedEffect
        }

        gradientColors = AppleMusicFallbackGradient

        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val image =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }.image
                if (image == null) {
                    null
                } else {
                    val bitmap = image.toBitmap()
                    withContext(Dispatchers.Default) {
                        val palette =
                            Palette
                                .from(bitmap)
                                .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                .generate()
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            }

        gradientColors = extractedColors ?: AppleMusicFallbackGradient
        gradientColorsCache[thumbnailUrl] = gradientColors
    }

    LaunchedEffect(player, playbackState, mediaMetadata.id) {
        if (playbackState != STATE_READY && playbackState != STATE_BUFFERING) return@LaunchedEffect
        while (isActive) {
            positionState.longValue = player.currentPosition.coerceAtLeast(0L)
            durationState.longValue = player.duration
            delay(250)
        }
    }

    val showLyricsMenu = {
        menuState.show {
            LyricsMenu(
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                autoHideControlsState = autoHideControlsState,
                onAutoHideControlsChange = onAutoHideControlsChange,
                onDismiss = menuState::dismiss,
                lyricsRenderViewModel = lyricsRenderViewModel,
            )
        }
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    val lyricsBackdropHazeState = rememberHazeState()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        controlsInteraction++
                    }
                },
    ) {
        LyricsScreenBackground(
            style = lyricsBackground,
            mediaMetadata = mediaMetadata,
            gradientColors = gradientColors,
            isPlaying = isPlaying,
            disableBlur = disableBlur,
            blurRadius = blurRadius,
            playerCustomImageUri = playerCustomImageUri,
            playerCustomBlur = playerCustomBlur,
            playerCustomContrast = playerCustomContrast,
            playerCustomBrightness = playerCustomBrightness,
            modifier = Modifier.hazeSource(lyricsBackdropHazeState),
        )

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .consumeUnhandledPointerInput(),
        )

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            AppleMusicGrabber(
                foregroundColor = foregroundColor,
                onClick = onBackClick,
            )
            AppleMusicTrackHeader(
                mediaMetadata = mediaMetadata,
                foregroundColor = foregroundColor,
                onMoreClick = showLyricsMenu,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Row(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(horizontal = 36.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleMusicLyricsPane(
                        lyricsMode = lyricsMode,
                        lyricsState = lyricsRenderState,
                        foregroundColor = foregroundColor,
                        sliderPositionProvider = { sliderPosition },
                        sourceLabel = lyricsSourceLabel,
                        lyricsSyncOffset = lyricsSyncOffset,
                        backdropHazeState = lyricsBackdropHazeState,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                    )

                    AnimatedVisibility(
                        visible = controlsVisible,
                        enter =
                            expandHorizontally(
                                animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                                expandFrom = Alignment.Start,
                            ) +
                                slideInHorizontally(
                                    animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                                    initialOffsetX = { it / 4 },
                                ) +
                                fadeIn(tween(CONTROLS_FADE_IN_MS)),
                        exit =
                            shrinkHorizontally(
                                animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                                shrinkTowards = Alignment.Start,
                            ) +
                                slideOutHorizontally(
                                    animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                                    targetOffsetX = { it / 4 },
                                ) +
                                fadeOut(tween(CONTROLS_FADE_OUT_MS)),
                        label = "lyrics-landscape-controls",
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth(LANDSCAPE_CONTROLS_WIDTH_FRACTION)
                                    .widthIn(max = 452.dp)
                                    .padding(start = 32.dp),
                            verticalArrangement = Arrangement.Center,
                        ) {
                            AppleMusicControls(
                                positionProvider = { positionState.longValue },
                                durationProvider = { durationState.longValue },
                                sliderPosition = sliderPosition,
                                isPlaying = isPlaying,
                                isLoading = isLoading,
                                volume = deviceMusicVolumeController.volumeFraction,
                                onPositionChange = { sliderPosition = it },
                                onPositionChangeFinished = {
                                    sliderPosition?.let {
                                        player.seekTo(it)
                                        positionState.longValue = it
                                    }
                                    sliderPosition = null
                                },
                                onVolumeChange = onVolumeChange,
                                onPreviousClick = {
                                    hapticClick()
                                    playerConnection.seekToPrevious()
                                },
                                onPlayPauseClick = {
                                    hapticClick()
                                    player.togglePlayPause()
                                },
                                onNextClick = {
                                    hapticClick()
                                    playerConnection.seekToNext()
                                },
                                onLyricsClick = onBackClick,
                                onQueueClick = onQueueClick,
                                foregroundColor = foregroundColor,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            } else {
                AppleMusicLyricsPane(
                    lyricsMode = lyricsMode,
                    lyricsState = lyricsRenderState,
                    foregroundColor = foregroundColor,
                    sliderPositionProvider = { sliderPosition },
                    sourceLabel = lyricsSourceLabel,
                    lyricsSyncOffset = lyricsSyncOffset,
                    backdropHazeState = lyricsBackdropHazeState,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )

                AnimatedVisibility(
                    visible = controlsVisible,
                    enter =
                        expandVertically(
                            animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                            expandFrom = Alignment.Top,
                        ) +
                            slideInVertically(
                                animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                                initialOffsetY = { it / 4 },
                            ) +
                            fadeIn(tween(CONTROLS_FADE_IN_MS)),
                    exit =
                        shrinkVertically(
                            animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                            shrinkTowards = Alignment.Top,
                        ) +
                            slideOutVertically(
                                animationSpec = spring(dampingRatio = 1f, stiffness = CONTROLS_MOTION_STIFFNESS),
                                targetOffsetY = { it / 4 },
                            ) +
                            fadeOut(tween(CONTROLS_FADE_OUT_MS)),
                    label = "lyrics-portrait-controls",
                ) {
                    AppleMusicControls(
                        positionProvider = { positionState.longValue },
                        durationProvider = { durationState.longValue },
                        sliderPosition = sliderPosition,
                        isPlaying = isPlaying,
                        isLoading = isLoading,
                        volume = deviceMusicVolumeController.volumeFraction,
                        onPositionChange = { sliderPosition = it },
                        onPositionChangeFinished = {
                            sliderPosition?.let {
                                player.seekTo(it)
                                positionState.longValue = it
                            }
                            sliderPosition = null
                        },
                        onVolumeChange = onVolumeChange,
                        onPreviousClick = {
                            hapticClick()
                            playerConnection.seekToPrevious()
                        },
                        onPlayPauseClick = {
                            hapticClick()
                            player.togglePlayPause()
                        },
                        onNextClick = {
                            hapticClick()
                            playerConnection.seekToNext()
                        },
                        onLyricsClick = onBackClick,
                        onQueueClick = onQueueClick,
                        foregroundColor = foregroundColor,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 40.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsScreenBackground(
    style: LyricsBackgroundStyle,
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    isPlaying: Boolean,
    disableBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(
                    if (style == LyricsBackgroundStyle.FOLLOW_THEME) {
                        MaterialTheme.colorScheme.surface
                    } else {
                        Color.Black
                    },
                ),
    ) {
        when (style) {
            LyricsBackgroundStyle.DEFAULT -> {
                AppleMusicBackground(
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                    animate = isPlaying,
                )
            }

            LyricsBackgroundStyle.FOLLOW_THEME -> Unit

            LyricsBackgroundStyle.COLORING,
            LyricsBackgroundStyle.CUSTOM,
            -> {
                PlayerBackground(
                    playerBackground =
                        if (style == LyricsBackgroundStyle.CUSTOM) {
                            PlayerBackgroundStyle.CUSTOM
                        } else {
                            PlayerBackgroundStyle.COLORING
                        },
                    mediaMetadata = mediaMetadata,
                    gradientColors = gradientColors,
                    disableBlur = disableBlur,
                    blurRadius = blurRadius,
                    playerCustomImageUri = playerCustomImageUri,
                    playerCustomBlur = playerCustomBlur,
                    playerCustomContrast = playerCustomContrast,
                    playerCustomBrightness = playerCustomBrightness,
                )
            }
        }
    }
}

@Composable
private fun AppleMusicBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    animate: Boolean,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val motionEnabled =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
    val drift = remember { Animatable(0f) }
    LaunchedEffect(animate, motionEnabled) {
        if (!animate || !motionEnabled) return@LaunchedEffect
        while (isActive) {
            drift.animateTo(
                targetValue = 1f,
                animationSpec =
                    tween(
                        durationMillis = ((1f - drift.value) * ARTWORK_DRIFT_PERIOD_MS).roundToInt().coerceAtLeast(1),
                        easing = LinearEasing,
                    ),
            )
            drift.snapTo(0f)
        }
    }
    val colors = if (gradientColors.isNotEmpty()) gradientColors else AppleMusicFallbackGradient
    val backgroundBrush =
        remember(colors) {
            Brush.verticalGradient(
                listOf(
                    colors.getOrElse(0) { AppleMusicFallbackGradient[0] }.copy(alpha = 0.88f),
                    colors.getOrElse(1) { AppleMusicFallbackGradient[1] }.copy(alpha = 0.76f),
                    colors.getOrElse(2) { AppleMusicFallbackGradient[2] }.copy(alpha = 0.96f),
                ),
            )
        }
    val bottomScrim =
        remember {
            Brush.verticalGradient(
                listOf(
                    Color.Transparent,
                    Color.Black.copy(alpha = 0.28f),
                ),
            )
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .clipToBounds()
                .background(AppleMusicFallbackGradient.last()),
    ) {
        AnimatedContent(
            targetState = mediaMetadata.thumbnailUrl,
            transitionSpec = { fadeIn(tween(700)) togetherWith fadeOut(tween(700)) },
            label = "lyrics-apple-background",
        ) { thumbnailUrl ->
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val angle = 2f * PI.toFloat() * drift.value
                                val scale = ARTWORK_BLUR_OVERSCAN + ARTWORK_DRIFT_SCALE * (0.5f - 0.5f * cos(angle))
                                scaleX = scale
                                scaleY = scale
                                rotationZ = sin(angle) * ARTWORK_DRIFT_ROTATION_DEGREES
                                alpha = 0.62f
                            }.blur(46.dp),
                )
            }
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(backgroundBrush),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(bottomScrim),
        )
    }
}

@Composable
private fun AppleMusicGrabber(
    foregroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeDescription = stringResource(R.string.close)
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(44.dp)
                .semantics { contentDescription = closeDescription }
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(width = 36.dp, height = 5.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.32f)),
        )
    }
}

@Composable
private fun AppleMusicTrackHeader(
    mediaMetadata: MediaMetadata,
    foregroundColor: Color,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistText =
        remember(mediaMetadata.id, mediaMetadata.artists) {
            mediaMetadata.artists.joinToString { it.name }
        }

    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(foregroundColor.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = mediaMetadata.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (mediaMetadata.thumbnailUrl == null) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = foregroundColor.copy(alpha = 0.72f),
                    modifier = Modifier.size(26.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = mediaMetadata.title,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = foregroundColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyMedium,
                color = foregroundColor.copy(alpha = 0.6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.more_horiz,
            contentDescription = stringResource(R.string.more_options),
            foregroundColor = foregroundColor,
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun AppleMusicHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .size(48.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = ripple(bounded = false, radius = 24.dp),
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foregroundColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicLyricsPane(
    lyricsMode: LyricsMode,
    lyricsState: LyricsRenderScreenState,
    foregroundColor: Color,
    sliderPositionProvider: () -> Long?,
    sourceLabel: AnnotatedString?,
    lyricsSyncOffset: Int,
    backdropHazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    LyricsContent(
        lyricsMode = lyricsMode,
        lyricsState = lyricsState,
        sliderPositionProvider = sliderPositionProvider,
        sourceLabel = sourceLabel,
        lyricsSyncOffset = lyricsSyncOffset,
        backdropHazeState = backdropHazeState,
        modifier =
            modifier
                .fillMaxSize()
                .padding(
                    horizontal =
                        when (lyricsMode) {
                            LyricsMode.ENHANCED -> EnhancedLyricsHorizontalPadding
                            LyricsMode.V2 -> V2LyricsHorizontalPadding
                        },
                ),
        textColor = foregroundColor,
    )
}

@Composable
private fun AppleMusicControls(
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    sliderPosition: Long?,
    isPlaying: Boolean,
    isLoading: Boolean,
    volume: Float,
    onPositionChange: (Long) -> Unit,
    onPositionChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: (() -> Unit)?,
    foregroundColor: Color,
    modifier: Modifier = Modifier,
) {
    val position = positionProvider()
    val duration = durationProvider()
    val hasDuration = duration != C.TIME_UNSET && duration > 0L
    val safeDuration = if (hasDuration) duration else 1L
    val currentPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val remainingPosition = (safeDuration - currentPosition).coerceAtLeast(0L)
    val timeLabelAlpha by animateFloatAsState(
        targetValue = if (sliderPosition != null) SCRUBBING_TIME_LABEL_ALPHA else IDLE_TIME_LABEL_ALPHA,
        animationSpec = tween(200),
        label = "lyrics-time-label-alpha",
    )
    val playPauseState =
        when {
            isLoading -> PlayPauseVisualState.Loading
            isPlaying -> PlayPauseVisualState.Playing
            else -> PlayPauseVisualState.Paused
        }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppleMusicSlider(
            value = currentPosition.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            activeColor = foregroundColor.copy(alpha = 0.94f),
            inactiveColor = foregroundColor.copy(alpha = 0.28f),
            onValueChange = { onPositionChange(it.toLong()) },
            onValueChangeFinished = onPositionChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .graphicsLayer { alpha = timeLabelAlpha },
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor,
            )
            Text(
                text = if (hasDuration) "-${makeTimeString(remainingPosition)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = foregroundColor,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_previous,
                contentDescription = stringResource(R.string.widget_previous),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onPreviousClick,
            )
            AppleMusicPressableIcon(
                touchSize = 74.dp,
                foregroundColor = foregroundColor,
                onClick = onPlayPauseClick,
            ) {
                AnimatedContent(
                    targetState = playPauseState,
                    transitionSpec = {
                        (
                            fadeIn(tween(160)) +
                                scaleIn(
                                    initialScale = 0.6f,
                                    animationSpec =
                                        spring(
                                            dampingRatio = 0.6f,
                                            stiffness = Spring.StiffnessMedium,
                                        ),
                                )
                        ) togetherWith fadeOut(tween(120))
                    },
                    contentAlignment = Alignment.Center,
                    label = "lyrics-play-pause",
                ) { state ->
                    when (state) {
                        PlayPauseVisualState.Loading -> {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = foregroundColor,
                            )
                        }

                        PlayPauseVisualState.Playing,
                        PlayPauseVisualState.Paused,
                        -> {
                            val playing = state == PlayPauseVisualState.Playing
                            Icon(
                                painter = painterResource(if (playing) R.drawable.pause else R.drawable.play),
                                contentDescription =
                                    if (playing) {
                                        stringResource(R.string.widget_pause)
                                    } else {
                                        stringResource(R.string.play)
                                    },
                                tint = foregroundColor,
                                modifier = Modifier.size(54.dp),
                            )
                        }
                    }
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_next,
                contentDescription = stringResource(R.string.next),
                iconSize = 44.dp,
                touchSize = 68.dp,
                foregroundColor = foregroundColor,
                onClick = onNextClick,
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 26.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_off),
                contentDescription = stringResource(R.string.minimum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(17.dp),
            )
            AppleMusicSlider(
                value = volume.coerceIn(0f, 1f),
                valueRange = 0f..1f,
                activeColor = foregroundColor.copy(alpha = 0.88f),
                inactiveColor = foregroundColor.copy(alpha = 0.24f),
                onValueChange = onVolumeChange,
                onValueChangeFinished = {},
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 16.dp),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = stringResource(R.string.maximum_volume),
                tint = foregroundColor.copy(alpha = 0.66f),
                modifier = Modifier.size(19.dp),
            )
        }

        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppleMusicLyricsToggle(
                foregroundColor = foregroundColor,
                onClick = onLyricsClick,
            )
            if (onQueueClick != null) {
                AppleMusicTransportButton(
                    iconRes = R.drawable.queue_music,
                    contentDescription = stringResource(R.string.queue),
                    iconSize = 24.dp,
                    touchSize = 48.dp,
                    foregroundColor = foregroundColor.copy(alpha = 0.72f),
                    onClick = onQueueClick,
                )
            }
        }
    }
}

private enum class PlayPauseVisualState {
    Loading,
    Playing,
    Paused,
}

@Composable
private fun AppleMusicLyricsToggle(
    foregroundColor: Color,
    onClick: () -> Unit,
) {
    val iconTint = if (foregroundColor.luminance() > 0.5f) Color.Black.copy(alpha = 0.72f) else Color.White

    IconToggleButton(
        checked = true,
        onCheckedChange = { onClick() },
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.9f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.lyrics),
                contentDescription = stringResource(R.string.lyrics),
                tint = iconTint,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicTransportButton(
    iconRes: Int,
    contentDescription: String?,
    iconSize: Dp,
    touchSize: Dp,
    foregroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    AppleMusicPressableIcon(
        touchSize = touchSize,
        foregroundColor = foregroundColor,
        onClick = onClick,
        modifier = modifier,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = foregroundColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
private fun AppleMusicPressableIcon(
    touchSize: Dp,
    foregroundColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val pressProgress by animateFloatAsState(
        targetValue = if (isPressed) 1f else 0f,
        animationSpec =
            spring(
                dampingRatio = 0.7f,
                stiffness = Spring.StiffnessMedium,
            ),
        label = "lyrics-transport-press",
    )

    Box(
        modifier =
            modifier
                .size(touchSize)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.Button,
                    onClick = onClick,
                ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val haloScale = 0.8f + 0.2f * pressProgress
                        scaleX = haloScale
                        scaleY = haloScale
                        alpha = pressProgress
                    }.clip(CircleShape)
                    .background(foregroundColor.copy(alpha = 0.14f)),
        )
        Box(
            modifier =
                Modifier.graphicsLayer {
                    val contentScale = 1f - 0.12f * pressProgress
                    scaleX = contentScale
                    scaleY = contentScale
                },
            contentAlignment = Alignment.Center,
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeStart = valueRange.start
    val safeEnd = valueRange.endInclusive.coerceAtLeast(safeStart + 1f)
    val safeRange = safeStart..safeEnd
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged
    val trackHeight by animateDpAsState(
        targetValue = if (isInteracting) AppleMusicActiveTrackHeight else AppleMusicTrackHeight,
        animationSpec =
            spring(
                dampingRatio = 0.8f,
                stiffness = Spring.StiffnessMediumLow,
            ),
        label = "lyrics-slider-track-height",
    )
    val sliderColors =
        SliderDefaults.colors(
            activeTrackColor = if (isInteracting) activeColor.copy(alpha = 1f) else activeColor,
            activeTickColor = activeColor,
            thumbColor = Color.Transparent,
            inactiveTrackColor = inactiveColor,
        )
    val sliderState =
        rememberControlledSliderState(
            value = value.coerceIn(safeRange),
            valueRange = safeRange,
        )

    Slider(
        state = sliderState,
        onValueChange = { updated ->
            sliderState.value = updated
            onValueChange(updated)
        },
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
        interactionSource = interactionSource,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = sliderColors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(28.dp),
    )
}

@Composable
private fun LyricsContent(
    lyricsMode: LyricsMode,
    lyricsState: LyricsRenderScreenState,
    sliderPositionProvider: () -> Long?,
    sourceLabel: AnnotatedString?,
    lyricsSyncOffset: Int,
    textColor: Color,
    backdropHazeState: HazeState,
    modifier: Modifier = Modifier,
) {
    when (lyricsMode) {
        LyricsMode.V2 -> {
            if (lyricsState is LyricsRenderScreenState.Loading) {
                val lyricsFontFamily = rememberArchiveTuneLyricsFontFamily()
                val searchingBaseStyle = MaterialTheme.typography.headlineSmall
                Box(
                    modifier = modifier,
                    contentAlignment = Alignment.Center,
                ) {
                    LyricsSearchingIndicator(
                        textColor = textColor,
                        textStyle =
                            searchingBaseStyle.copy(
                                fontWeight = FontWeight.Bold,
                                fontFamily = lyricsFontFamily,
                                textAlign = TextAlign.Center,
                            ),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                }
            } else {
                LyricsV2(
                    lyricsState = lyricsState,
                    sliderPositionProvider = sliderPositionProvider,
                    lyricsSyncOffset = lyricsSyncOffset,
                    modifier = modifier,
                    sourceLabel = sourceLabel,
                    textColorOverride = textColor,
                    backdropHazeState = backdropHazeState,
                )
            }
        }

        LyricsMode.ENHANCED -> {
            LyricsEnhanced(
                lyricsState = lyricsState,
                sliderPositionProvider = sliderPositionProvider,
                lyricsSyncOffset = lyricsSyncOffset,
                modifier = modifier,
                sourceLabel = sourceLabel,
                textColorOverride = textColor,
                backdropHazeState = backdropHazeState,
            )
        }
    }
}
