/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.database.ContentObserver
import android.media.AudioManager
import android.os.SystemClock
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import android.net.Uri
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.composed
import androidx.compose.ui.platform.LocalView
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.runtime.produceState
import androidx.core.graphics.drawable.toBitmap
import android.graphics.Bitmap
import android.os.Build
import android.renderscript.Allocation
import android.renderscript.Element
import android.renderscript.RenderScript
import android.renderscript.ScriptIntrinsicBlur
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_BUFFERING
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Player.STATE_READY
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.palette.graphics.Palette
import androidx.navigation.NavController
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.request.SuccessResult
import coil3.toBitmap
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.DarkModeKey
import moe.rukamori.archivetune.constants.MaxCanvasCacheSizeKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerCustomImageUriKey
import moe.rukamori.archivetune.constants.PlayerCustomBlurKey
import moe.rukamori.archivetune.constants.PlayerCustomContrastKey
import moe.rukamori.archivetune.constants.PlayerCustomBrightnessKey
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.BlurRadiusKey
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.constants.PlayerButtonsStyleKey
import moe.rukamori.archivetune.ui.theme.PlayerBackgroundColorUtils
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.PlayerSliderColors
import moe.rukamori.archivetune.constants.PlayerHorizontalPadding
import moe.rukamori.archivetune.constants.QueuePeekHeight
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.constants.SliderStyleKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.BottomSheet
import moe.rukamori.archivetune.ui.component.BigSeekBar
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LocalBottomSheetPageState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.component.ResizableIconButton
import moe.rukamori.archivetune.ui.component.rememberBottomSheetState
import androidx.compose.animation.AnimatedVisibility
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.screens.settings.DarkMode
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import androidx.datastore.preferences.core.booleanPreferencesKey
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import me.saket.squiggles.SquigglySlider
import moe.rukamori.archivetune.playback.PlayerConnection
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

private const val SeekbarSettleToleranceMs = 1_500L
private const val V7BackdropMinArtworkSizePx = 1_024
private const val V7BackdropMaxArtworkSizePx = 2_048
private const val V7BackdropBlurDp = 44
private const val V7BackdropBlurScale = 1.18f
private const val V7BackdropArtworkOverscanFactor = 1.15f
private const val V7SharpStagePortraitFraction = 0.62f
private const val V7SharpStageLandscapeFraction = 0.58f
private const val V7BackdropOverlapDp = 72
private const val V7SharpStageBottomScrimStartFraction = 0.40f
private const val V7BackdropFloorBlackStartFraction = 0.88f
private const val V8BackdropArtworkSizePx = 1_024

@Stable
internal class DeviceMusicVolumeController(
    private val audioManager: AudioManager,
) {
    private var minVolume by mutableIntStateOf(readMinVolume())
    private var maxVolume by mutableIntStateOf(readMaxVolume())
    var volumeFraction by mutableFloatStateOf(readVolumeFraction())
        private set

    fun refresh() {
        minVolume = readMinVolume()
        maxVolume = readMaxVolume()
        volumeFraction = readVolumeFraction()
    }

    @JvmName("setDeviceMusicVolumeFraction")
    fun setVolumeFraction(fraction: Float) {
        val safeFraction = fraction.takeIf { it.isFinite() }?.coerceIn(0f, 1f) ?: volumeFraction
        val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
        val targetVolume = (minVolume + (safeFraction * volumeRange).roundToInt())
            .coerceIn(minVolume, maxVolume)

        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetVolume, 0)
        refresh()
    }

    private fun readVolumeFraction(): Float {
        val currentVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        val volumeRange = (maxVolume - minVolume).coerceAtLeast(1)
        return ((currentVolume - minVolume).toFloat() / volumeRange.toFloat()).coerceIn(0f, 1f)
    }

    private fun readMaxVolume(): Int {
        val streamMinVolume = readMinVolume()
        return audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            .coerceAtLeast(streamMinVolume + 1)
    }

    private fun readMinVolume(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }
}

@Composable
internal fun rememberDeviceMusicVolumeController(): DeviceMusicVolumeController {
    val context = LocalContext.current
    val audioManager = remember(context) {
        context.applicationContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val controller = remember(audioManager) {
        DeviceMusicVolumeController(audioManager)
    }

    DisposableEffect(context, controller) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                controller.refresh()
            }
        }
        val contentResolver = context.applicationContext.contentResolver
        contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        controller.refresh()
        onDispose {
            contentResolver.unregisterContentObserver(observer)
        }
    }

    return controller
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BottomSheetPlayer(
    state: BottomSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current

    val bottomSheetPageState = LocalBottomSheetPageState.current

    val playerConnection = LocalPlayerConnection.current ?: return

    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4
    )
    
    val storedPlayerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT
    )
    val playerUsesFixedBackground =
        playerDesignStyle == PlayerDesignStyle.V8 || playerDesignStyle == PlayerDesignStyle.V9
    val playerBackground =
        if (playerUsesFixedBackground) PlayerBackgroundStyle.DEFAULT else storedPlayerBackground

    // Custom background preferences (image + effects)
    val (playerCustomImageUri) = rememberPreference(PlayerCustomImageUriKey, "")
    val (playerCustomBlur) = rememberPreference(PlayerCustomBlurKey, 0f)
    val (playerCustomContrast) = rememberPreference(PlayerCustomContrastKey, 1f)
    val (playerCustomBrightness) = rememberPreference(PlayerCustomBrightnessKey, 1f)
    
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (blurRadius) = rememberPreference(BlurRadiusKey, 48f)
    val (backdropEnabled) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val (showCodecOnPlayer) = rememberPreference(booleanPreferencesKey("show_codec_on_player"), false)
    val (incrementalSeekSkipEnabled) = rememberPreference(moe.rukamori.archivetune.constants.SeekExtraSeconds, defaultValue = false)
    var keyboardSkipMultiplier by remember { mutableStateOf(1) }
    var lastKeyboardTapTime by remember { mutableLongStateOf(0L) }

    val playerButtonsStyle by rememberEnumPreference(
        key = PlayerButtonsStyleKey,
        defaultValue = PlayerButtonsStyle.DEFAULT
    )

    val isSystemInDarkTheme = isSystemInDarkTheme()
    val darkTheme by rememberEnumPreference(DarkModeKey, defaultValue = DarkMode.AUTO)
    val useDarkTheme = remember(darkTheme, isSystemInDarkTheme) {
        if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
    }
    val onBackgroundColor = when (playerBackground) {
        PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.secondary
        else ->
            if (useDarkTheme)
                MaterialTheme.colorScheme.onSurface
            else
                MaterialTheme.colorScheme.onPrimary
    }
    val useBlackBackground =
        remember(isSystemInDarkTheme, darkTheme, pureBlack) {
            val useDarkTheme =
                if (darkTheme == DarkMode.AUTO) isSystemInDarkTheme else darkTheme == DarkMode.ON
            useDarkTheme && pureBlack
        }
    val backgroundColor = if (useBlackBackground && state.value > state.collapsedBound) {
        val progress = ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
            .coerceIn(0f, 1f)
        Color.Black.copy(alpha = progress)
    } else {
        val progress = ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
            .coerceIn(0f, 1f)
        MaterialTheme.colorScheme.surfaceContainer.copy(alpha = progress)
    }

    val playbackState by playerConnection.playbackState.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentSongLiked = currentSong?.song?.liked == true
    val queueTitle by playerConnection.queueTitle.collectAsState()
    val currentFormat by playerConnection.currentFormat.collectAsState(initial = null)
    val queueWindows by playerConnection.queueWindows.collectAsState()
    val currentWindowIndex by playerConnection.currentWindowIndex.collectAsState()
    val deviceMusicVolumeController = rememberDeviceMusicVolumeController()
    val onPlayerVolumeChange = remember(deviceMusicVolumeController) {
        { volume: Float ->
            deviceMusicVolumeController.setVolumeFraction(volume)
        }
    }

    val repeatMode by playerConnection.repeatMode.collectAsState()

    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    val aodModeEnabled by playerConnection.aodModeEnabled.collectAsStateWithLifecycle()
    val (thumbnailCornerRadius) = rememberPreference(ThumbnailCornerRadiusKey, defaultValue = 8f)
    val archiveTuneCanvasEnabled by rememberPreference(ArchiveTuneCanvasKey, false)
    val lowDataModeActive = rememberLowDataModeActive()
    val (maxCanvasCacheSize, _) = rememberPreference(
        key = MaxCanvasCacheSizeKey,
        defaultValue = 256,
    )

    val sliderStyle by rememberEnumPreference(SliderStyleKey, SliderStyle.Standard)

    LaunchedEffect(maxCanvasCacheSize) {
        CanvasArtworkPlaybackCache.setMaxSize(maxCanvasCacheSize)
    }

    var position by rememberSaveable(mediaMetadata?.id) {
        mutableLongStateOf(playerConnection.player.currentPosition)
    }
    var duration by rememberSaveable(mediaMetadata?.id) {
        mutableLongStateOf(playerConnection.player.duration)
    }
    var lyricsSyncOffset by rememberSaveable(mediaMetadata?.id) {
        mutableIntStateOf(0)
    }
    var sliderPosition by remember(mediaMetadata?.id) {
        mutableStateOf<Long?>(null)
    }
    var isUserSeeking by remember(mediaMetadata?.id) {
        mutableStateOf(false)
    }
    
    // Track loading state: when buffering or when user is seeking
    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null

    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    
    // Previous background states for smooth transitions
    var previousThumbnailUrl by remember { mutableStateOf<String?>(null) }
    var previousGradientColors by remember { mutableStateOf<List<Color>>(emptyList()) }
    
    // Cache for gradient colors to prevent re-extraction for same songs
    val gradientColorsCache = remember { mutableMapOf<String, List<Color>>() }



    // Default gradient colors for fallback
    val defaultGradientColors = listOf(MaterialTheme.colorScheme.surface, MaterialTheme.colorScheme.surfaceVariant)
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    
    // Update previous states when media changes
    LaunchedEffect(mediaMetadata?.id) {
        val currentThumbnail = mediaMetadata?.thumbnailUrl
        if (currentThumbnail != previousThumbnailUrl) {
            previousThumbnailUrl = currentThumbnail
            previousGradientColors = gradientColors
        }
    }
    
    LaunchedEffect(mediaMetadata?.id, playerBackground) {
        if (aodModeEnabled) return@LaunchedEffect
        if (playerBackground == PlayerBackgroundStyle.GRADIENT || playerBackground == PlayerBackgroundStyle.COLORING || playerBackground == PlayerBackgroundStyle.BLUR_GRADIENT || playerBackground == PlayerBackgroundStyle.GLOW || playerBackground == PlayerBackgroundStyle.GLOW_ANIMATED) {
            val currentMetadata = mediaMetadata
            if (currentMetadata != null && currentMetadata.thumbnailUrl != null) {
                // Check cache first
                val cachedColors = gradientColorsCache[currentMetadata.id]
                if (cachedColors != null) {
                    gradientColors = cachedColors
                } else {
                    val request = ImageRequest.Builder(context)
                        .data(currentMetadata.thumbnailUrl)
                        .memoryCacheKey(currentMetadata.thumbnailUrl)
                        .diskCacheKey(currentMetadata.thumbnailUrl)
                        .diskCachePolicy(CachePolicy.ENABLED)
                        .networkCachePolicy(CachePolicy.ENABLED)
                        .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                        .allowHardware(false)
                        .build()

                    val result = runCatching {
                        withContext(Dispatchers.IO) {
                            context.imageLoader.execute(request)
                        }
                    }.getOrNull()

                    if (result != null) {
                        val bitmap = result.image?.toBitmap()
                        if (bitmap != null) {
                            val palette = withContext(Dispatchers.Default) {
                                Palette.from(bitmap)
                                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                    .generate()
                            }

                            val extractedColors = PlayerColorExtractor.extractGradientColors(
                                palette = palette,
                                fallbackColor = fallbackColor
                            )

                            gradientColorsCache[currentMetadata.id] = extractedColors
                            gradientColors = extractedColors
                        } else {
                            gradientColors = defaultGradientColors
                        }
                    } else {
                        gradientColors = defaultGradientColors
                    }
                }
            } else {
                gradientColors = emptyList()
            }
        } else {
            gradientColors = emptyList()
        }
    }

    val changeBound = state.expandedBound / 3

    val TextBackgroundColor =
        if (playerDesignStyle == PlayerDesignStyle.V7 || playerDesignStyle == PlayerDesignStyle.V8) Color.White
        else when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            PlayerBackgroundStyle.GRADIENT -> Color.White
            PlayerBackgroundStyle.COLORING -> Color.White
            PlayerBackgroundStyle.BLUR_GRADIENT -> Color.White
            PlayerBackgroundStyle.GLOW -> Color.White
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
            PlayerBackgroundStyle.CUSTOM -> Color.White
        }

    val icBackgroundColor =
        if (playerDesignStyle == PlayerDesignStyle.V7 || playerDesignStyle == PlayerDesignStyle.V8) Color.Black
        else when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.surface
            PlayerBackgroundStyle.BLUR -> Color.Black
            PlayerBackgroundStyle.GRADIENT -> Color.Black
            PlayerBackgroundStyle.COLORING -> Color.Black
            PlayerBackgroundStyle.BLUR_GRADIENT -> Color.Black
            PlayerBackgroundStyle.GLOW -> Color.Black
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.Black
            PlayerBackgroundStyle.CUSTOM -> Color.Black
        }

    val (textButtonColor, iconButtonColor) = when (playerButtonsStyle) {
        PlayerButtonsStyle.DEFAULT -> Pair(TextBackgroundColor, icBackgroundColor)
        PlayerButtonsStyle.SECONDARY -> Pair(
            MaterialTheme.colorScheme.secondary,
            MaterialTheme.colorScheme.onSecondary
        )
    }.let { (tb, ib) ->
        if (playerDesignStyle == PlayerDesignStyle.V7 || playerDesignStyle == PlayerDesignStyle.V8) {
            Pair(Color.White, Color.Black)
        } else {
            Pair(tb, ib)
        }
    }

    val download by LocalDownloadUtil.current.getDownload(mediaMetadata?.id ?: "")
        .collectAsState(initial = null)

    val sleepTimerEnabled =
        remember(
            playerConnection.service.sleepTimer.triggerTime,
            playerConnection.service.sleepTimer.pauseWhenSongEnd
        ) {
            playerConnection.service.sleepTimer.isActive
        }

    var sleepTimerTimeLeft by remember {
        mutableLongStateOf(0L)
    }

    LaunchedEffect(sleepTimerEnabled) {
        if (sleepTimerEnabled) {
            while (isActive) {
                sleepTimerTimeLeft =
                    if (playerConnection.service.sleepTimer.pauseWhenSongEnd) {
                        playerConnection.player.duration - playerConnection.player.currentPosition
                    } else {
                        playerConnection.service.sleepTimer.triggerTime - System.currentTimeMillis()
                    }
                delay(1000L)
            }
        }
    }

    var showSleepTimerDialog by remember {
        mutableStateOf(false)
    }

    var sleepTimerValue by remember {
        mutableFloatStateOf(30f)
    }
    if (showSleepTimerDialog) {
        AlertDialog(
            properties = DialogProperties(usePlatformDefaultWidth = false),
            onDismissRequest = { showSleepTimerDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.bedtime),
                    contentDescription = null
                )
            },
            title = { Text(stringResource(R.string.sleep_timer)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showSleepTimerDialog = false
                        playerConnection.service.sleepTimer.start(sleepTimerValue.roundToInt())
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showSleepTimerDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.cancel))
                }
            },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = pluralStringResource(
                            R.plurals.minute,
                            sleepTimerValue.roundToInt(),
                            sleepTimerValue.roundToInt()
                        ),
                        style = MaterialTheme.typography.bodyLarge,
                    )

                    Slider(
                        value = sleepTimerValue,
                        onValueChange = { sleepTimerValue = it },
                        valueRange = 5f..120f,
                        steps = (120 - 5) / 5 - 1,
                    )

                    OutlinedIconButton(
                        onClick = {
                            showSleepTimerDialog = false
                            playerConnection.service.sleepTimer.start(-1)
                        },
                    ) {
                        Text(stringResource(R.string.end_of_song))
                    }
                }
            },
        )
    }

    var showChoosePlaylistDialog by rememberSaveable {
        mutableStateOf(false)
    }

    LaunchedEffect(mediaMetadata?.id, playbackState, aodModeEnabled) {
        val startTime = SystemClock.elapsedRealtime()
        if (playbackState == STATE_READY) {
            while (isActive) {
                delay(if (aodModeEnabled) 500L else 100L)
                val isTransitioning = playerConnection.player.currentMediaItem?.mediaId != mediaMetadata?.id
                val currentPlayerPosition = playerConnection.player.currentPosition
                val currentPlayerDuration = playerConnection.player.duration
                
                if (isTransitioning) {
                    val elapsedSinceStart = SystemClock.elapsedRealtime() - startTime
                    position = elapsedSinceStart
                    mediaMetadata?.let {
                        val metaDuration = it.duration.toLong() * 1000
                        duration = if (metaDuration > 0) metaDuration else 0L
                    }
                } else {
                    position = currentPlayerPosition
                    duration = currentPlayerDuration
                    if (!isUserSeeking) {
                        sliderPosition?.let { targetPosition ->
                            val clampedTargetPosition = when {
                                currentPlayerDuration > 0L && currentPlayerDuration != C.TIME_UNSET -> {
                                    targetPosition.coerceIn(0L, currentPlayerDuration)
                                }
                                else -> targetPosition.coerceAtLeast(0L)
                            }
                            if (abs(currentPlayerPosition - clampedTargetPosition) <= SeekbarSettleToleranceMs) {
                                sliderPosition = null
                            }
                        }
                    }
                }
            }
        } else {
            mediaMetadata?.let {
                val metaDuration = it.duration.toLong() * 1000
                duration = if (metaDuration > 0) metaDuration else 0L
            }
            val currentPlayerPosition = playerConnection.player.currentPosition
            if (sliderPosition == null && currentPlayerPosition > 0L) {
                position = currentPlayerPosition
            }
        }
    }

    val dynamicQueuePeekHeight =
        if (playerDesignStyle == PlayerDesignStyle.V5) {
            0.dp
        } else if (playerDesignStyle == PlayerDesignStyle.V9) {
            88.dp +
                (if (showCodecOnPlayer) 24.dp else 0.dp) +
                (if (sleepTimerEnabled) 42.dp else 0.dp)
        } else if (showCodecOnPlayer) {
            88.dp
        } else {
            QueuePeekHeight
        }

    val dismissedBound = dynamicQueuePeekHeight + WindowInsets.systemBars.asPaddingValues().calculateBottomPadding()

    val queueSheetState = rememberBottomSheetState(
        dismissedBound = dismissedBound,
        expandedBound = state.expandedBound,
        collapsedBound = dismissedBound,
        initialAnchor = 0
    )
    
    var isLyricsScreenVisible by rememberSaveable {
        mutableStateOf(false)
    }

    BackHandler(
        enabled =
        queueSheetState.isExpandedOrExpanding ||
            state.isExpandedOrExpanding
    ) {
        when {
            isLyricsScreenVisible && state.isExpandedOrExpanding -> isLyricsScreenVisible = false
            queueSheetState.isExpandedOrExpanding -> queueSheetState.collapseSoft()
            state.isExpandedOrExpanding -> state.collapseSoft()
        }
    }

    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(state.isExpanded) {
        if (state.isExpanded) {
            focusRequester.requestFocus()
        }
        if (!state.isExpanded && aodModeEnabled) {
            playerConnection.aodModeEnabled.value = false
        }
    }

    BottomSheet(
        state = state,
        modifier = modifier
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { keyEvent ->
            if (keyEvent.type != KeyEventType.KeyDown || state.isCollapsed) return@onKeyEvent false

            when (keyEvent.key) {
                Key.DirectionLeft -> {
                    val now = SystemClock.uptimeMillis()
                    if (incrementalSeekSkipEnabled && now - lastKeyboardTapTime < 1000) {
                        keyboardSkipMultiplier++
                    } else {
                        keyboardSkipMultiplier = 1
                    }
                    lastKeyboardTapTime = now
                    val skipAmount = 5000L * keyboardSkipMultiplier
                    playerConnection.player.seekTo((playerConnection.player.currentPosition - skipAmount).coerceAtLeast(0))
                    true
                }
                Key.DirectionRight -> {
                    val now = SystemClock.uptimeMillis()
                    if (incrementalSeekSkipEnabled && now - lastKeyboardTapTime < 1000) {
                        keyboardSkipMultiplier++
                    } else {
                        keyboardSkipMultiplier = 1
                    }
                    lastKeyboardTapTime = now
                    val skipAmount = 5000L * keyboardSkipMultiplier
                    playerConnection.player.seekTo((playerConnection.player.currentPosition + skipAmount).coerceAtMost(playerConnection.player.duration))
                    true
                }
                Key.DirectionUp -> {
                    deviceMusicVolumeController.setVolumeFraction(
                        (deviceMusicVolumeController.volumeFraction + 0.05f).coerceAtMost(1f)
                    )
                    true
                }
                Key.DirectionDown -> {
                    deviceMusicVolumeController.setVolumeFraction(
                        (deviceMusicVolumeController.volumeFraction - 0.05f).coerceAtLeast(0f)
                    )
                    true
                }
                Key.Spacebar -> {
                    playerConnection.player.togglePlayPause()
                    true
                }
                Key.N -> {
                    if (keyEvent.isShiftPressed) {
                        playerConnection.seekToNext()
                        true
                    } else false
                }
                Key.P -> {
                    if (keyEvent.isShiftPressed) {
                        playerConnection.seekToPrevious()
                        true
                    } else false
                }
                Key.L -> {
                    playerConnection.toggleLike()
                    true
                }
                else -> false
            }
        },
        backgroundColor = if (playerDesignStyle == PlayerDesignStyle.V7 || playerDesignStyle == PlayerDesignStyle.V8) {
            val progress = ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                .coerceIn(0f, 1f)
            val fadeProgress = if (progress < 0.2f) {
                ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
            } else {
                0f
            }
            Color.Black.copy(alpha = 1f - fadeProgress)
        } else when (playerBackground) {
            PlayerBackgroundStyle.BLUR, PlayerBackgroundStyle.GRADIENT -> {
                // Apply same enhanced fade logic to blur/gradient backgrounds
                val progress = ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
                
                // Only start fading when very close to dismissal (last 20%)
                val fadeProgress = if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                MaterialTheme.colorScheme.surface.copy(alpha = 1f - fadeProgress)
            }
            else -> {
                // Enhanced background - stable until last 20% of drag (both normal and pure black)
                // Calculate progress for fade effect
                val progress = ((state.value - state.collapsedBound) / (state.expandedBound - state.collapsedBound))
                    .coerceIn(0f, 1f)
                
                // Only start fading when very close to dismissal (last 20%)
                val fadeProgress = if (progress < 0.2f) {
                    ((0.2f - progress) / 0.2f).coerceIn(0f, 1f)
                } else {
                    0f
                }
                
                if (useBlackBackground) {
                    // Apply same logic to pure black background
                    Color.Black.copy(alpha = 1f - fadeProgress)
                } else {
                    // Apply same logic to normal theme
                    MaterialTheme.colorScheme.surface.copy(alpha = 1f - fadeProgress)
                }
            }
        },
        onDismiss = {
            playerConnection.service.stopAndClearPlayback(clearPersistentState = true)
        },
        collapsedContent = {
            MiniPlayer(
                position = position,
                duration = duration,
                pureBlack = pureBlack,
            )
        },
    ) {
        val onSliderValueChange: (Long) -> Unit = {
            isUserSeeking = true
            sliderPosition = it
        }
        val onSliderValueChangeFinished: () -> Unit = {
            sliderPosition?.let {
                val isTransitioning = playerConnection.player.currentMediaItem?.mediaId != mediaMetadata?.id
                if (isTransitioning) {
                    // During crossfade, we want to seek in the NEXT song (the one UI is showing)
                    // The easiest way is to skip to it and then seek
                    playerConnection.player.seekToNext()
                    playerConnection.player.seekTo(it)
                } else {
                    playerConnection.player.seekTo(it)
                }
                position = it
            }
            isUserSeeking = false
        }
        val seekEnabled = duration > 0L && duration != C.TIME_UNSET
        val updatedOnSliderValueChange by rememberUpdatedState(onSliderValueChange)
        val updatedOnSliderValueChangeFinished by rememberUpdatedState(onSliderValueChangeFinished)

        val nextUpMetadata =
            remember(queueWindows, currentWindowIndex) {
                queueWindows.getOrNull(currentWindowIndex + 1)?.mediaItem?.metadata
            }

        val enrichedMetadata = remember(mediaMetadata, currentSong) {
            val meta = mediaMetadata ?: return@remember null
            if (meta.album != null) return@remember meta
            val dbAlbum = currentSong?.album
            val dbAlbumId = currentSong?.song?.albumId
            when {
                dbAlbum != null -> meta.copy(
                    album = MediaMetadata.Album(id = dbAlbum.id, title = dbAlbum.title)
                )
                dbAlbumId != null -> meta.copy(
                    album = MediaMetadata.Album(
                        id = dbAlbumId,
                        title = currentSong?.song?.albumName.orEmpty()
                    )
                )
                else -> meta
            }
        }

        val storefront =
            remember {
                val country = Locale.getDefault().country
                if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
            }
        val shouldUseV7Canvas =
            archiveTuneCanvasEnabled &&
                !lowDataModeActive &&
                playerDesignStyle == PlayerDesignStyle.V7 &&
                !aodModeEnabled
        val shouldUseArtworkCanvas =
            archiveTuneCanvasEnabled &&
                !lowDataModeActive &&
                (playerDesignStyle == PlayerDesignStyle.V8 || playerDesignStyle == PlayerDesignStyle.V9) &&
                !aodModeEnabled
        var v7CanvasArtwork by remember(mediaMetadata?.id) {
            mutableStateOf<CanvasArtwork?>(null)
        }
        var v7CanvasFetchInFlight by remember(mediaMetadata?.id) {
            mutableStateOf(false)
        }
        var artworkCanvas by remember(mediaMetadata?.id) {
            mutableStateOf<CanvasArtwork?>(null)
        }
        var artworkCanvasFetchInFlight by remember(mediaMetadata?.id) {
            mutableStateOf(false)
        }

        LaunchedEffect(shouldUseV7Canvas, mediaMetadata?.id) {
            val metadata = mediaMetadata
            if (!shouldUseV7Canvas || metadata == null) {
                v7CanvasArtwork = null
                v7CanvasFetchInFlight = false
                return@LaunchedEffect
            }

            CanvasArtworkPlaybackCache.get(metadata.id)
                ?.takeIf { !it.preferredVerticalAnimationUrl.isNullOrBlank() }
                ?.let { cached ->
                    v7CanvasArtwork = cached
                    v7CanvasFetchInFlight = false
                    return@LaunchedEffect
                }

            val artistNameRaw = metadata.artists.firstOrNull()?.name.orEmpty()
            if (metadata.title.isBlank() || artistNameRaw.isBlank() || v7CanvasFetchInFlight) {
                return@LaunchedEffect
            }

            v7CanvasFetchInFlight = true
            try {
                val fetched =
                    withContext(Dispatchers.IO) {
                        fetchCanvasArtworkForPlayback(
                            songTitleRaw = metadata.title,
                            artistNameRaw = artistNameRaw,
                            storefront = storefront,
                            requireVertical = true,
                        )
                    }
                v7CanvasArtwork = fetched
                if (fetched != null) {
                    v7CanvasArtwork = CanvasArtworkPlaybackCache.put(metadata.id, fetched)
                }
            } finally {
                v7CanvasFetchInFlight = false
            }
        }

        LaunchedEffect(shouldUseArtworkCanvas, mediaMetadata?.id) {
            val metadata = mediaMetadata
            if (!shouldUseArtworkCanvas || metadata == null) {
                artworkCanvas = null
                artworkCanvasFetchInFlight = false
                return@LaunchedEffect
            }

            CanvasArtworkPlaybackCache.get(metadata.id)
                ?.takeIf { !it.preferredAnimationUrl.isNullOrBlank() }
                ?.let { cached ->
                    artworkCanvas = cached
                    artworkCanvasFetchInFlight = false
                    return@LaunchedEffect
                }

            val artistNameRaw = metadata.artists.firstOrNull()?.name.orEmpty()
            if (metadata.title.isBlank() || artistNameRaw.isBlank() || artworkCanvasFetchInFlight) {
                return@LaunchedEffect
            }

            artworkCanvasFetchInFlight = true
            try {
                val fetched =
                    withContext(Dispatchers.IO) {
                        fetchCanvasArtworkForPlayback(
                            songTitleRaw = metadata.title,
                            artistNameRaw = artistNameRaw,
                            storefront = storefront,
                            requireVertical = false,
                        )
                    }
                artworkCanvas = fetched
                if (fetched != null) {
                    artworkCanvas = CanvasArtworkPlaybackCache.put(metadata.id, fetched)
                }
            } finally {
                artworkCanvasFetchInFlight = false
            }
        }

        val controlsContent: @Composable ColumnScope.(MediaMetadata) -> Unit = { mediaMetadata ->
            PlayerControlsContent(
                mediaMetadata = mediaMetadata,
                playerDesignStyle = playerDesignStyle,
                sliderStyle = sliderStyle,
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                repeatMode = repeatMode,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                textButtonColor = textButtonColor,
                iconButtonColor = iconButtonColor,
                textBackgroundColor = TextBackgroundColor,
                icBackgroundColor = icBackgroundColor,
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                playerConnection = playerConnection,
                navController = navController,
                state = state,
                menuState = menuState,
                bottomSheetPageState = bottomSheetPageState,
                context = context,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
                currentFormat = if (playerDesignStyle == PlayerDesignStyle.V7) currentFormat else null,
            )
        }

        if (!state.isCollapsed &&
            !aodModeEnabled &&
            playerDesignStyle != PlayerDesignStyle.V5 &&
            playerDesignStyle != PlayerDesignStyle.V7 &&
            playerDesignStyle != PlayerDesignStyle.V8 &&
            playerDesignStyle != PlayerDesignStyle.V9
        ) {
            PlayerBackground(
                playerBackground = playerBackground,
                mediaMetadata = mediaMetadata,
                gradientColors = gradientColors,
                disableBlur = disableBlur,
                blurRadius = blurRadius,
                playerCustomImageUri = playerCustomImageUri,
                playerCustomBlur = playerCustomBlur,
                playerCustomContrast = playerCustomContrast,
                playerCustomBrightness = playerCustomBrightness
            )
        }

// distance

        when (LocalConfiguration.current.orientation) {
            Configuration.ORIENTATION_LANDSCAPE -> {
                if (playerDesignStyle == PlayerDesignStyle.V5) {
                    val littleBackground = MaterialTheme.colorScheme.primaryContainer
                    val littleTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val displayPositionMs = sliderPosition ?: position
                    val progressFraction =
                        remember(displayPositionMs, duration) {
                            if (duration <= 0L || duration == C.TIME_UNSET) 0f
                            else (displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        }
                    val progressOverlayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)

                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .background(littleBackground),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(progressFraction)
                                .align(Alignment.TopStart)
                                .background(progressOverlayColor),
                        )
                        Box(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .littlePlayerOverlayGestures(
                                    seekEnabled = seekEnabled,
                                    durationMs = duration,
                                    progressFraction = progressFraction,
                                    canSkipPrevious = canSkipPrevious,
                                    canSkipNext = canSkipNext,
                                    onSeekToPositionMs = updatedOnSliderValueChange,
                                    onSeekFinished = updatedOnSliderValueChangeFinished,
                                    onSkipPrevious = playerConnection::seekToPrevious,
                                    onSkipNext = playerConnection::seekToNext,
                                )
                                .windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom
                                    )
                                ),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                LittlePlayerContent(
                                    mediaMetadata = metadata,
                                    sliderPosition = sliderPosition,
                                    positionMs = position,
                                    durationMs = duration,
                                    textColor = littleTextColor,
                                    liked = currentSongLiked,
                                    onCollapse = state::collapseSoft,
                                    onToggleLike = playerConnection::toggleLike,
                                    onExpandQueue = queueSheetState::expandSoft,
                                    onMenuClick = {
                                        menuState.show {
                                            PlayerMenu(
                                                mediaMetadata = metadata,
                                                navController = navController,
                                                playerBottomSheetState = state,
                                                onShowDetailsDialog = {
                                                    bottomSheetPageState.show {
                                                        ShowMediaInfo(metadata.id)
                                                    }
                                                },
                                                onDismiss = menuState::dismiss
                                            )
                                        }
                                    },
                                )
                            }
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V7) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        V7PlayerBackdrop(
                            thumbnailUrl = mediaMetadata?.thumbnailUrl,
                            canvasStaticUrl = v7CanvasArtwork?.static,
                            canvasPrimaryUrl = v7CanvasArtwork?.animatedVertical,
                            canvasFallbackUrl = v7CanvasArtwork?.videoUrlVertical,
                            isPlaying = isPlaying,
                            disableBlur = disableBlur,
                            backdropBlurAmount = backdropBlurAmount,
                            label = "v7BackdropLandscape",
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = queueSheetState.collapsedBound)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                V8PlayerControlsContent(
                                    mediaMetadata = metadata,
                                    queueTitle = "",
                                    playbackState = playbackState,
                                    isPlaying = isPlaying,
                                    isLoading = isLoading,
                                    canSkipPrevious = canSkipPrevious,
                                    canSkipNext = canSkipNext,
                                    currentSongLiked = currentSongLiked,
                                    sliderPosition = sliderPosition,
                                    position = position,
                                    duration = duration,
                                    volume = deviceMusicVolumeController.volumeFraction,
                                    currentFormat = currentFormat,
                                    playerConnection = playerConnection,
                                    navController = navController,
                                    state = state,
                                    menuState = menuState,
                                    bottomSheetPageState = bottomSheetPageState,
                                    onSliderValueChange = onSliderValueChange,
                                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                                    onVolumeChange = onPlayerVolumeChange,
                                    landscape = true,
                                )
                            }

                            Spacer(Modifier.height(16.dp))
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V8) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        V8PlayerBackdrop(
                            thumbnailUrl = mediaMetadata?.thumbnailUrl,
                            backdropBlurAmount = backdropBlurAmount,
                        )

                        enrichedMetadata?.let { metadata ->
                            V8PlayerContent(
                                mediaMetadata = metadata,
                                queueTitle = queueTitle,
                                playbackState = playbackState,
                                isPlaying = isPlaying,
                                isLoading = isLoading,
                                canSkipPrevious = canSkipPrevious,
                                canSkipNext = canSkipNext,
                                currentSongLiked = currentSongLiked,
                                sliderPosition = sliderPosition,
                                position = position,
                                duration = duration,
                                volume = deviceMusicVolumeController.volumeFraction,
                                playerConnection = playerConnection,
                                navController = navController,
                                state = state,
                                menuState = menuState,
                                bottomSheetPageState = bottomSheetPageState,
                                currentFormat = currentFormat,
                                canvasPrimaryUrl = artworkCanvas?.animated,
                                canvasFallbackUrl = artworkCanvas?.videoUrl,
                                onSliderValueChange = onSliderValueChange,
                                onSliderValueChangeFinished = onSliderValueChangeFinished,
                                onVolumeChange = onPlayerVolumeChange,
                                landscape = true,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                        )
                                    )
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                            )
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V9) {
                    enrichedMetadata?.let { metadata ->
                        V9PlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            textBackgroundColor = TextBackgroundColor,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            onCollapseClick = { state.collapseSoft() },
                            onQueueClick = { queueSheetState.expandSoft() },
                            onLyricsClick = { isLyricsScreenVisible = true },
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            landscape = true,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = queueSheetState.collapsedBound)
                                .windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom
                                    )
                                )
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else {
                    Row(
                        modifier =
                        Modifier
                            .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                            .padding(bottom = queueSheetState.collapsedBound + 48.dp),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                        ) {
                            val screenWidth = LocalConfiguration.current.screenWidthDp
                            val thumbnailSize = (screenWidth * 0.4).dp
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier.size(thumbnailSize),
                                isPlayerExpanded = state.isExpanded
                            )
                        }
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier =
                            Modifier
                                .weight(1f)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Top)),
                        ) {
                            Spacer(Modifier.weight(1f))

                            enrichedMetadata?.let {
                                controlsContent(it)
                            }

                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
            }

            else -> {
                if (playerDesignStyle == PlayerDesignStyle.V5) {
                    val littleBackground = MaterialTheme.colorScheme.primaryContainer
                    val littleTextColor = MaterialTheme.colorScheme.onPrimaryContainer
                    val displayPositionMs = sliderPosition ?: position
                    val progressFraction =
                        remember(displayPositionMs, duration) {
                            if (duration <= 0L || duration == C.TIME_UNSET) 0f
                            else (displayPositionMs.toFloat() / duration.toFloat()).coerceIn(0f, 1f)
                        }
                    val progressOverlayColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                    val seekEnabled = duration > 0L && duration != C.TIME_UNSET

                    Box(
                        modifier =
                        Modifier
                            .fillMaxSize()
                            .background(littleBackground),
                    ) {
                        Box(
                            modifier =
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(progressFraction)
                                .align(Alignment.TopStart)
                                .background(progressOverlayColor),
                        )
                        Box(
                            modifier =
                            Modifier
                                .fillMaxSize()
                                .littlePlayerOverlayGestures(
                                    seekEnabled = seekEnabled,
                                    durationMs = duration,
                                    progressFraction = progressFraction,
                                    canSkipPrevious = canSkipPrevious,
                                    canSkipNext = canSkipNext,
                                    onSeekToPositionMs = updatedOnSliderValueChange,
                                    onSeekFinished = updatedOnSliderValueChangeFinished,
                                    onSkipPrevious = playerConnection::seekToPrevious,
                                    onSkipNext = playerConnection::seekToNext,
                                )
                                .windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Horizontal + WindowInsetsSides.Top + WindowInsetsSides.Bottom
                                    )
                                ),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                LandscapeLikeBox(modifier = Modifier.fillMaxSize()) {
                                    LittlePlayerContent(
                                        mediaMetadata = metadata,
                                        sliderPosition = sliderPosition,
                                        positionMs = position,
                                        durationMs = duration,
                                        textColor = littleTextColor,
                                        liked = currentSongLiked,
                                        onCollapse = state::collapseSoft,
                                        onToggleLike = playerConnection::toggleLike,
                                        onExpandQueue = queueSheetState::expandSoft,
                                        onMenuClick = {
                                            menuState.show {
                                                PlayerMenu(
                                                    mediaMetadata = metadata,
                                                    navController = navController,
                                                    playerBottomSheetState = state,
                                                    onShowDetailsDialog = {
                                                        bottomSheetPageState.show {
                                                            ShowMediaInfo(metadata.id)
                                                        }
                                                    },
                                                    onDismiss = menuState::dismiss
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V7) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize(),
                    ) {
                        V7PlayerBackdrop(
                            thumbnailUrl = mediaMetadata?.thumbnailUrl,
                            canvasStaticUrl = v7CanvasArtwork?.static,
                            canvasPrimaryUrl = v7CanvasArtwork?.animatedVertical,
                            canvasFallbackUrl = v7CanvasArtwork?.videoUrlVertical,
                            isPlaying = isPlaying,
                            disableBlur = disableBlur,
                            backdropBlurAmount = backdropBlurAmount,
                            label = "v7BackdropPortrait",
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = queueSheetState.collapsedBound)
                                .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.Horizontal))
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        ) {
                            enrichedMetadata?.let { metadata ->
                                V8PlayerControlsContent(
                                    mediaMetadata = metadata,
                                    queueTitle = "",
                                    playbackState = playbackState,
                                    isPlaying = isPlaying,
                                    isLoading = isLoading,
                                    canSkipPrevious = canSkipPrevious,
                                    canSkipNext = canSkipNext,
                                    currentSongLiked = currentSongLiked,
                                    sliderPosition = sliderPosition,
                                    position = position,
                                    duration = duration,
                                    volume = deviceMusicVolumeController.volumeFraction,
                                    currentFormat = currentFormat,
                                    playerConnection = playerConnection,
                                    navController = navController,
                                    state = state,
                                    menuState = menuState,
                                    bottomSheetPageState = bottomSheetPageState,
                                    onSliderValueChange = onSliderValueChange,
                                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                                    onVolumeChange = onPlayerVolumeChange,
                                )
                            }

                            Spacer(Modifier.height(24.dp))
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V8) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        V8PlayerBackdrop(
                            thumbnailUrl = mediaMetadata?.thumbnailUrl,
                            backdropBlurAmount = backdropBlurAmount,
                        )

                        enrichedMetadata?.let { metadata ->
                            V8PlayerContent(
                                mediaMetadata = metadata,
                                queueTitle = queueTitle,
                                playbackState = playbackState,
                                isPlaying = isPlaying,
                                isLoading = isLoading,
                                canSkipPrevious = canSkipPrevious,
                                canSkipNext = canSkipNext,
                                currentSongLiked = currentSongLiked,
                                sliderPosition = sliderPosition,
                                position = position,
                                duration = duration,
                                volume = deviceMusicVolumeController.volumeFraction,
                                playerConnection = playerConnection,
                                navController = navController,
                                state = state,
                                menuState = menuState,
                                bottomSheetPageState = bottomSheetPageState,
                                currentFormat = currentFormat,
                                canvasPrimaryUrl = artworkCanvas?.animated,
                                canvasFallbackUrl = artworkCanvas?.videoUrl,
                                onSliderValueChange = onSliderValueChange,
                                onSliderValueChangeFinished = onSliderValueChangeFinished,
                                onVolumeChange = onPlayerVolumeChange,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(bottom = queueSheetState.collapsedBound)
                                    .windowInsetsPadding(
                                        WindowInsets.systemBars.only(
                                            WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                        )
                                    )
                                    .nestedScroll(state.preUpPostDownNestedScrollConnection),
                            )
                        }
                    }
                } else if (playerDesignStyle == PlayerDesignStyle.V9) {
                    enrichedMetadata?.let { metadata ->
                        V9PlayerContent(
                            mediaMetadata = metadata,
                            playbackState = playbackState,
                            isPlaying = isPlaying,
                            isLoading = isLoading,
                            canSkipPrevious = canSkipPrevious,
                            canSkipNext = canSkipNext,
                            sliderPosition = sliderPosition,
                            position = position,
                            duration = duration,
                            playerConnection = playerConnection,
                            navController = navController,
                            state = state,
                            textBackgroundColor = TextBackgroundColor,
                            textButtonColor = textButtonColor,
                            iconButtonColor = iconButtonColor,
                            canvasPrimaryUrl = artworkCanvas?.animated,
                            canvasFallbackUrl = artworkCanvas?.videoUrl,
                            onCollapseClick = { state.collapseSoft() },
                            onQueueClick = { queueSheetState.expandSoft() },
                            onLyricsClick = { isLyricsScreenVisible = true },
                            onSliderValueChange = onSliderValueChange,
                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(bottom = queueSheetState.collapsedBound)
                                .windowInsetsPadding(
                                    WindowInsets.systemBars.only(
                                        WindowInsetsSides.Top + WindowInsetsSides.Horizontal
                                    )
                                )
                                .nestedScroll(state.preUpPostDownNestedScrollConnection),
                        )
                    }
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier =
                        Modifier
                            .windowInsetsPadding(
                                WindowInsets.systemBars.only(
                                    WindowInsetsSides.Horizontal
                                )
                            )
                            .padding(bottom = queueSheetState.collapsedBound),
                    ) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.weight(1f),
                        ) {
                            Thumbnail(
                                sliderPositionProvider = { sliderPosition },
                                modifier = Modifier.nestedScroll(state.preUpPostDownNestedScrollConnection),
                                isPlayerExpanded = state.isExpanded
                            )
                        }

                        enrichedMetadata?.let {
                            controlsContent(it)
                        }

                        Spacer(Modifier.height(30.dp))
                    }
                }
            }
        }

        val queueOnBackgroundColor = if (useBlackBackground) Color.White else MaterialTheme.colorScheme.onSurface
        val queueSurfaceColor = if (useBlackBackground) Color.Black else MaterialTheme.colorScheme.surface

        val (queueTextButtonColor, queueIconButtonColor) = when (playerButtonsStyle) {
            PlayerButtonsStyle.DEFAULT -> Pair(queueOnBackgroundColor, queueSurfaceColor)
            PlayerButtonsStyle.SECONDARY -> Pair(
                MaterialTheme.colorScheme.secondary,
                MaterialTheme.colorScheme.onSecondary
            )
        }

        Queue(
            state = queueSheetState,
            playerBottomSheetState = state,
            navController = navController,
            backgroundColor =
            if (useBlackBackground) {
                Color.Black
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
            onBackgroundColor = queueOnBackgroundColor,
            TextBackgroundColor = TextBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onShowLyrics = { isLyricsScreenVisible = true },
            pureBlack = pureBlack,
        )

        mediaMetadata?.let { metadata ->
            MikoLyricsTransition(
                visible = isLyricsScreenVisible,
                backHandlerEnabled = isLyricsScreenVisible && state.isExpandedOrExpanding,
                mediaMetadata = metadata,
                navController = navController,
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = { lyricsSyncOffset = it },
                onDismiss = { isLyricsScreenVisible = false },
                onQueueClick = {
                    isLyricsScreenVisible = false
                    queueSheetState.expandSoft()
                },
            )
        }

        AnimatedVisibility(
            visible = aodModeEnabled,
            enter = fadeIn(tween(300)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            mediaMetadata?.let { metadata ->
                AodPlayerScreen(
                    mediaMetadata = metadata,
                    isPlaying = isPlaying,
                    position = position,
                    duration = duration,
                    sliderPosition = sliderPosition,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    thumbnailCornerRadius = thumbnailCornerRadius,
                    onPlayPause = { playerConnection.player.togglePlayPause() },
                    onSkipPrevious = playerConnection::seekToPrevious,
                    onSkipNext = playerConnection::seekToNext,
                    onSeek = { sliderPosition = it },
                    onSeekFinished = onSliderValueChangeFinished,
                    onExit = { playerConnection.aodModeEnabled.value = false },
                )
            }
        }
    }
}

@Composable
private fun MikoLyricsTransition(
    visible: Boolean,
    backHandlerEnabled: Boolean,
    mediaMetadata: MediaMetadata,
    navController: NavController,
    lyricsSyncOffset: Int,
    onLyricsSyncOffsetChange: (Int) -> Unit,
    onDismiss: () -> Unit,
    onQueueClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = spring(
            dampingRatio = 0.82f,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "mikoLyricsTransition",
    )

    val boundedProgress = progress.coerceIn(0f, 1f)

    if (visible || boundedProgress > 0.001f) {
        val scaleX = 0.92f + (0.08f * boundedProgress)
        val scaleY = 0.78f + (0.22f * boundedProgress)
        val alpha = (0.2f + (0.8f * boundedProgress)).coerceIn(0f, 1f)
        val cornerRadius = 32.dp * (1f - boundedProgress)

        Box(
            modifier = modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = boundedProgress }
                .background(Color.Black.copy(alpha = 0.24f * boundedProgress)),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0.5f, 1f)
                        this.scaleX = scaleX
                        this.scaleY = scaleY
                        this.alpha = alpha
                        translationY = size.height * 0.16f * (1f - boundedProgress)
                    }
                    .clip(RoundedCornerShape(cornerRadius))
                    .background(MaterialTheme.colorScheme.surface),
            ) {
                LyricsScreen(
                    mediaMetadata = mediaMetadata,
                    onBackClick = onDismiss,
                    navController = navController,
                    lyricsSyncOffset = lyricsSyncOffset,
                    onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                    onQueueClick = onQueueClick,
                    backHandlerEnabled = backHandlerEnabled,
                )
            }
        }
    }
}

@Composable
private fun V8PlayerBackdrop(
    thumbnailUrl: String?,
    backdropBlurAmount: Int,
    modifier: Modifier = Modifier,
) {
    val backdropModel = remember(thumbnailUrl) {
        thumbnailUrl?.resize(V8BackdropArtworkSizePx, V8BackdropArtworkSizePx)
    }
    val backdropRequest = rememberOfflineArtworkImageRequest(backdropModel)
    val blurRadiusDp = 44.dp * (backdropBlurAmount.toFloat() / 100f)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        if (backdropModel != null) {
            val backdropHasBlur = backdropBlurAmount > 0
            if (backdropHasBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                AsyncImage(
                    model = backdropRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(if (blurRadiusDp > 0.dp) Modifier.blur(blurRadiusDp) else Modifier)
                        .graphicsLayer {
                            scaleX = 1.16f
                            scaleY = 1.16f
                            alpha = 0.66f
                        },
                )
            } else if (backdropHasBlur) {
                BackdropBlurApi30(
                    model = backdropModel,
                    blurAmount = backdropBlurAmount,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.16f
                            scaleY = 1.16f
                            alpha = 0.66f
                        },
                )
            } else {
                AsyncImage(
                    model = backdropRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.16f
                            scaleY = 1.16f
                            alpha = 0.66f
                        },
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.52f)),
        )
    }
}

@Suppress("DEPRECATION")
@Composable
private fun BackdropBlurApi30(
    model: String?,
    blurAmount: Int,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val blurredBitmap by produceState<Bitmap?>(null, model, blurAmount) {
        if (model == null) return@produceState
        value = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(model)
                    .memoryCacheKey(model)
                    .diskCacheKey(model)
                    .diskCachePolicy(CachePolicy.ENABLED)
                    .networkCachePolicy(CachePolicy.ENABLED)
                    .allowHardware(false)
                    .size(500)
                    .build()
                val result = imageLoader.execute(request)
                when (result) {
                    is SuccessResult -> {
                        val bitmap = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                        val scale = 0.4f
                        val sw = (bitmap.width * scale).toInt().coerceAtLeast(1)
                        val sh = (bitmap.height * scale).toInt().coerceAtLeast(1)
                        val scaled = Bitmap.createScaledBitmap(bitmap, sw, sh, true)
                        if (bitmap !== scaled && !bitmap.isRecycled) bitmap.recycle()

                        val radius = (blurAmount * 25 / 100f).coerceIn(1f, 25f)
                        RenderScript.create(context).also { rs ->
                            try {
                                val input = Allocation.createFromBitmap(rs, scaled)
                                val output = Allocation.createTyped(rs, input.type)
                                ScriptIntrinsicBlur.create(rs, Element.U8_4(rs)).apply {
                                    setRadius(radius)
                                    setInput(input)
                                    forEach(output)
                                }
                                output.copyTo(scaled)
                            } finally {
                                rs.destroy()
                            }
                        }
                        scaled
                    }
                    else -> null
                }
            } catch (_: Exception) {
                null
            }
        }
    }

    val loadedBitmap = blurredBitmap
    if (loadedBitmap != null) {
        Image(
            painter = BitmapPainter(loadedBitmap.asImageBitmap()),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = rememberOfflineArtworkImageRequest(model),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier,
        )
    }
}

@Composable
private fun V7PlayerBackdrop(
    thumbnailUrl: String?,
    canvasStaticUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
    label: String,
    modifier: Modifier = Modifier,
) {
    val configuration = LocalConfiguration.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val fallbackColor = Color.Black.toArgb()
    val backdropArtworkSizePx = remember(
        configuration.screenWidthDp,
        configuration.screenHeightDp,
        density.density,
    ) {
        with(density) {
            (maxOf(configuration.screenWidthDp, configuration.screenHeightDp).dp.toPx() *
                V7BackdropArtworkOverscanFactor)
                .roundToInt()
                .coerceIn(V7BackdropMinArtworkSizePx, V7BackdropMaxArtworkSizePx)
        }
    }

    val canvasPrimary = canvasPrimaryUrl?.takeIf { it.isNotBlank() }
    val canvasFallback = canvasFallbackUrl?.takeIf { it.isNotBlank() }
    val canvasStatic = canvasStaticUrl?.takeIf { it.isNotBlank() }
    val coverArtworkUrl = thumbnailUrl?.takeIf { it.isNotBlank() }
    val hasCanvas = !canvasPrimary.isNullOrBlank() || !canvasFallback.isNullOrBlank()
    // When canvas is available, prefer its static image as the sharp-stage placeholder.
    // This prevents the jarring YTM thumbnail → canvas video flash on expand.
    val sharpArtworkUrl = if (hasCanvas) (canvasStatic ?: coverArtworkUrl) else (coverArtworkUrl ?: canvasStatic)
    val backdropArtworkUrl = coverArtworkUrl ?: canvasStatic
    // For palette extraction, use canvas static when canvas is active so the scrim
    // gradient is derived from the canvas colors rather than the YTM thumbnail.
    val paletteSourceUrl = if (hasCanvas && canvasStatic != null) canvasStatic else backdropArtworkUrl
    var backdropPalette by remember(paletteSourceUrl, fallbackColor) {
        mutableStateOf(V7BackdropPalette.fromColors(emptyList(), fallbackColor))
    }

    LaunchedEffect(paletteSourceUrl, hasCanvas, fallbackColor) {
        backdropPalette = V7BackdropPalette.fromColors(emptyList(), fallbackColor)
        if (paletteSourceUrl == null) return@LaunchedEffect

        val request = ImageRequest.Builder(context)
            .data(paletteSourceUrl)
            .memoryCacheKey(paletteSourceUrl)
            .diskCacheKey(paletteSourceUrl)
            .diskCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
            .allowHardware(false)
            .build()

        val extractedColors = try {
            val image = withContext(Dispatchers.IO) {
                context.imageLoader.execute(request)
            }.image
            if (image == null) {
                null
            } else {
                withContext(Dispatchers.Default) {
                    val fullBitmap = image.toBitmap()
                    // When canvas is active, extract from the bottom 30% of the static frame.
                    // This gives us the actual colors at the canvas bottom edge, so the scrim
                    // gradient blends seamlessly into the backdrop below.
                    val bitmapForPalette = if (hasCanvas && fullBitmap.height > 4) {
                        val startY = (fullBitmap.height * 0.70f).toInt().coerceAtLeast(0)
                        val cropHeight = (fullBitmap.height - startY).coerceAtLeast(1)
                        android.graphics.Bitmap.createBitmap(fullBitmap, 0, startY, fullBitmap.width, cropHeight)
                    } else {
                        fullBitmap
                    }
                    val palette = Palette.from(bitmapForPalette)
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

        backdropPalette = V7BackdropPalette.fromColors(extractedColors.orEmpty(), fallbackColor)
    }

    val backdropState =
        remember(sharpArtworkUrl, canvasPrimary, canvasFallback) {
            V7PlayerBackdropState(
                artworkUrl = sharpArtworkUrl,
                canvasPrimaryUrl = canvasPrimary,
                canvasFallbackUrl = canvasFallback,
            )
        }
    val backdropArtworkModel = remember(backdropArtworkUrl, backdropArtworkSizePx) {
        backdropArtworkUrl?.resize(backdropArtworkSizePx, backdropArtworkSizePx)
    }
    val backdropArtworkRequest = rememberOfflineArtworkImageRequest(backdropArtworkModel)
    val sharpStageBottomScrim = remember(backdropPalette) {
        val blendColor = backdropPalette.bottom
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to Color.Transparent,
                V7SharpStageBottomScrimStartFraction to Color.Transparent,
                0.60f to blendColor.copy(alpha = 0.18f),
                0.76f to blendColor.copy(alpha = 0.52f),
                0.88f to blendColor.copy(alpha = 0.82f),
                1f to blendColor,
            )
        )
    }
    val backdropFloor = remember(backdropPalette) {
        Brush.verticalGradient(
            colorStops = arrayOf(
                0f to backdropPalette.bottom,
                V7BackdropFloorBlackStartFraction to backdropPalette.bottom,
                1f to backdropPalette.bottom,
            )
        )
    }
    val backdropBlurRadius = V7BackdropBlurDp.dp * (backdropBlurAmount.toFloat() / 100f)
    val needsBlur = !disableBlur && backdropBlurAmount > 0
    val backdropImageModifier = remember(disableBlur, needsBlur) {
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                scaleX = V7BackdropBlurScale
                scaleY = V7BackdropBlurScale
                alpha = if (disableBlur || !needsBlur) 0.20f else 0.58f
            }
    }
    val canvasStageModifier = remember {
        Modifier
            .fillMaxSize()
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(backdropPalette.top),
    ) {
        val sharpStageFraction =
            if (configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                V7SharpStageLandscapeFraction
            } else {
                V7SharpStagePortraitFraction
            }
        val sharpStageHeight = maxHeight * sharpStageFraction
        val sharpStageTopOffset = 0.dp
        val sharpStageBottomOffset = sharpStageTopOffset + sharpStageHeight
        val backdropTopOffset = (sharpStageBottomOffset - V7BackdropOverlapDp.dp).coerceAtLeast(0.dp)
        val backdropHeight = maxHeight - backdropTopOffset

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(backdropHeight)
                .clipToBounds()
                .background(backdropPalette.bottom),
        ) {
            if (backdropArtworkModel != null) {
                if (needsBlur && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    AsyncImage(
                        model = backdropArtworkRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = backdropImageModifier.blur(backdropBlurRadius),
                    )
                } else if (needsBlur) {
                    BackdropBlurApi30(
                        model = backdropArtworkModel,
                        blurAmount = backdropBlurAmount,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = V7BackdropBlurScale
                                scaleY = V7BackdropBlurScale
                                alpha = 0.58f
                            },
                    )
                } else {
                    AsyncImage(
                        model = backdropArtworkRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = backdropImageModifier,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropFloor),
            )
        }

        AnimatedContent(
            targetState = backdropState,
            transitionSpec = {
                fadeIn(tween(900)) togetherWith fadeOut(tween(900))
            },
            label = label,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = sharpStageTopOffset)
                .fillMaxWidth()
                .height(sharpStageHeight)
                .clipToBounds(),
        ) { backdrop ->
            val sharpArtworkModel = remember(backdrop.artworkUrl, backdropArtworkSizePx) {
                backdrop.artworkUrl?.resize(backdropArtworkSizePx, backdropArtworkSizePx)
            }
            val sharpArtworkRequest = rememberOfflineArtworkImageRequest(sharpArtworkModel)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(backdropPalette.top),
                contentAlignment = Alignment.Center,
            ) {
                if (sharpArtworkModel != null) {
                    AsyncImage(
                        model = sharpArtworkRequest,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                if (hasCanvas) {
                    CanvasArtworkPlayer(
                        primaryUrl = backdrop.canvasPrimaryUrl,
                        fallbackUrl = backdrop.canvasFallbackUrl,
                        isPlaying = isPlaying,
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                        modifier = canvasStageModifier,
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = sharpStageTopOffset)
                .fillMaxWidth()
                .height(sharpStageHeight)
                .background(sharpStageBottomScrim),
        )
    }
}

@Immutable
private data class V7BackdropPalette(
    val top: Color,
    val mid: Color,
    val bottom: Color,
) {
    companion object {
        fun fromColors(colors: List<Color>, fallbackColor: Int): V7BackdropPalette {
            // Only use the FIRST extracted color (dominant hue from the image).
            // PlayerColorExtractor fills colors[1..N] with hue-shifted synthetic variants
            // (e.g. red → green at +120°) which are wrong for a backdrop that should feel
            // coherent. We derive mid/bottom by darkening the same hue instead.
            val dominantColor = colors.firstOrNull()
            val fallback = Color(fallbackColor).v7BackdropTone(valueMin = 0.12f, valueMax = 0.38f)
            val top    = dominantColor?.v7BackdropTone(valueMin = 0.20f, valueMax = 0.72f) ?: fallback
            val mid    = dominantColor?.v7BackdropTone(valueMin = 0.13f, valueMax = 0.48f) ?: top
            val bottom = dominantColor?.v7BackdropTone(valueMin = 0.08f, valueMax = 0.32f) ?: mid
            return V7BackdropPalette(
                top = top,
                mid = mid,
                bottom = bottom,
            )
        }
    }
}

private fun Color.v7BackdropTone(
    valueMin: Float,
    valueMax: Float,
): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(toArgb(), hsv)
    hsv[1] = if (hsv[1] < 0.12f) {
        hsv[1].coerceAtMost(0.08f)
    } else {
        (hsv[1] * 1.22f).coerceIn(0f, 1f)
    }
    hsv[2] = hsv[2].coerceIn(valueMin, valueMax)
    return Color(android.graphics.Color.HSVToColor(hsv))
}

@Immutable
private data class V7PlayerBackdropState(
    val artworkUrl: String?,
    val canvasPrimaryUrl: String?,
    val canvasFallbackUrl: String?,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LittlePlayerContent(
    mediaMetadata: MediaMetadata,
    sliderPosition: Long?,
    positionMs: Long,
    durationMs: Long,
    textColor: Color,
    liked: Boolean,
    onCollapse: () -> Unit,
    onToggleLike: () -> Unit,
    onExpandQueue: () -> Unit,
    onMenuClick: () -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val titleColor = textColor.copy(alpha = 0.95f)
        val secondaryColor = textColor.copy(alpha = 0.6f)
        val timeColor = textColor.copy(alpha = 0.85f)

        val scale =
            minOf(maxWidth / 420.dp, maxHeight / 260.dp)
                .coerceIn(0.78f, 1.15f)

        val titleSize = (56f * scale).sp
        val timeSize = (44f * scale).sp
        val iconSize = (26f * scale).dp
        val collapseIconSize = (28f * scale).dp
        val horizontalPadding = (18f * scale).dp
        val verticalPadding = (10f * scale).dp

        val displayPositionMs = sliderPosition ?: positionMs

        val timeText = remember(displayPositionMs, durationMs) {
            val positionText = makeTimeString(displayPositionMs)
            val durationText = if (durationMs != C.TIME_UNSET) makeTimeString(durationMs) else ""
            if (durationText.isBlank()) positionText else "$positionText/$durationText"
        }

        val artistsText = remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString(separator = ", ") { artist -> artist.name }
        }

        Column(
            modifier =
            Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        ) {
            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    AnimatedContent(
                        targetState = mediaMetadata.title,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "little_title",
                    ) { title ->
                        Text(
                            text = title,
                            color = titleColor,
                            fontSize = titleSize,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.basicMarquee(),
                        )
                    }

                    Spacer(Modifier.height((10f * scale).dp))

                    mediaMetadata.album?.title?.takeIf { it.isNotBlank() }?.let { albumTitle ->
                        AnimatedContent(
                            targetState = albumTitle,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "little_album",
                        ) { album ->
                            Text(
                                text = album,
                                color = secondaryColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }

                    artistsText.takeIf { it.isNotBlank() }?.let { artists ->
                        AnimatedContent(
                            targetState = artists,
                            transitionSpec = { fadeIn() togetherWith fadeOut() },
                            label = "little_artists",
                        ) { artistLine ->
                            Text(
                                text = "by - $artistLine",
                                color = secondaryColor,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.basicMarquee(),
                            )
                        }
                    }
                }

                Spacer(Modifier.width((16f * scale).dp))

                Text(
                    text = timeText,
                    color = timeColor,
                    fontSize = timeSize,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.End,
                    maxLines = 1,
                    modifier = Modifier.widthIn(min = (140f * scale).dp),
                )
            }

            Spacer(Modifier.height((14f * scale).dp))

            Spacer(Modifier.height((6f * scale).dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.8f),
                    modifier =
                    Modifier
                        .size(collapseIconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onCollapse,
                        ),
                )

                Spacer(Modifier.weight(1f))

                Icon(
                    painter = painterResource(if (liked) R.drawable.favorite else R.drawable.favorite_border),
                    contentDescription = null,
                    tint =
                    if (liked) MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                    else textColor.copy(alpha = 0.78f),
                    modifier =
                    Modifier
                        .size(iconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onToggleLike,
                        ),
                )

                Spacer(Modifier.width((18f * scale).dp))

                Icon(
                    painter = painterResource(R.drawable.queue_music),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.78f),
                    modifier =
                    Modifier
                        .size(iconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onExpandQueue,
                        ),
                )

                Spacer(Modifier.width((18f * scale).dp))

                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = null,
                    tint = textColor.copy(alpha = 0.78f),
                    modifier =
                    Modifier
                        .size(iconSize)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            onClick = onMenuClick,
                        ),
                )
            }
        }
    }
}

@Composable
private fun LandscapeLikeBox(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Layout(
        content = content,
        modifier = modifier.graphicsLayer { clip = true },
    ) { measurables, constraints ->
        val measurable = measurables.firstOrNull()
        if (measurable == null) {
            layout(constraints.minWidth, constraints.minHeight) {}
        } else {
            val swappedConstraints =
                Constraints(
                    minWidth = constraints.minHeight,
                    maxWidth = constraints.maxHeight,
                    minHeight = constraints.minWidth,
                    maxHeight = constraints.maxWidth,
                )

            val placeable = measurable.measure(swappedConstraints)
            val width = constraints.maxWidth
            val height = constraints.maxHeight
            val rotatedWidth = placeable.height
            val rotatedHeight = placeable.width

            val x = ((width - rotatedWidth) / 2).coerceAtLeast(0)
            val y = ((height - rotatedHeight) / 2).coerceAtLeast(0)

            layout(width, height) {
                placeable.placeWithLayer(x, y) {
                    transformOrigin = TransformOrigin(0f, 0f)
                    rotationZ = 90f
                    translationX = placeable.height.toFloat()
                }
            }
        }
    }
}

private fun Modifier.littlePlayerOverlayGestures(
    seekEnabled: Boolean,
    durationMs: Long,
    progressFraction: Float,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onSeekToPositionMs: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
): Modifier {
    return composed {
        val view = LocalView.current
        val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
        
        pointerInput(seekEnabled, durationMs, canSkipPrevious, canSkipNext) {
            var lastTapUptimeMs = 0L
        var lastTapPosition: Offset? = null
        val doubleTapTimeoutMs = viewConfiguration.doubleTapTimeoutMillis.toLong()
        val touchSlop = viewConfiguration.touchSlop

        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = true)
            val pointerId = down.id

            var upPosition = down.position
            val minOverlayHeightPx = 24.dp.toPx()
            val overlayHeightPx =
                (progressFraction * size.height).coerceAtLeast(minOverlayHeightPx)
            val seekAllowedFromDown =
                seekEnabled &&
                    durationMs > 0L &&
                    durationMs != C.TIME_UNSET &&
                    down.position.y <= overlayHeightPx

            var isSeeking = false

            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Main)
                val change = event.changes.firstOrNull { it.id == pointerId } ?: continue
                upPosition = change.position

                if (!change.pressed) break

                if (!isSeeking && seekAllowedFromDown) {
                    val distanceFromDown = (change.position - down.position).getDistance()
                    if (distanceFromDown > touchSlop) isSeeking = true
                }

                if (isSeeking) {
                    val fraction =
                        if (size.height > 0) (change.position.y / size.height.toFloat()) else 0f
                    val clampedFraction = fraction.coerceIn(0f, 1f)

                    val targetMs =
                        (durationMs.toDouble() * clampedFraction.toDouble()).roundToLong().coerceIn(0L, durationMs)
                    onSeekToPositionMs(targetMs)
                    change.consume()
                }
            }

            if (isSeeking) {
                onSeekFinished()
                lastTapUptimeMs = 0L
                lastTapPosition = null
            } else {
                val now = SystemClock.uptimeMillis()
                val previousTapPosition = lastTapPosition
                val isDoubleTap =
                    previousTapPosition != null &&
                            (now - lastTapUptimeMs) <= doubleTapTimeoutMs &&
                            (upPosition - previousTapPosition).getDistance() <= (touchSlop * 2f)

                if (isDoubleTap) {
                    val isTopSide = upPosition.y < size.height / 2f
                    if (isTopSide) {
                        if (canSkipPrevious) {
                            if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            onSkipPrevious()
                        }
                    } else {
                        if (canSkipNext) {
                            if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            onSkipNext()
                        }
                    }
                    lastTapUptimeMs = 0L
                    lastTapPosition = null
                } else {
                    lastTapUptimeMs = now
                    lastTapPosition = upPosition
                }
            }
        }
    }
}
}
