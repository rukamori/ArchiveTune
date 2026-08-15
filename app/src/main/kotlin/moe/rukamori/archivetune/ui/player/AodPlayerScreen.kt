/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.media3.common.C
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodAccentStyle
import moe.rukamori.archivetune.constants.AodAccentStyleKey
import moe.rukamori.archivetune.constants.AodAmbientIntensityKey
import moe.rukamori.archivetune.constants.AodArtworkGlowKey
import moe.rukamori.archivetune.constants.AodAutoDimTimeoutKey
import moe.rukamori.archivetune.constants.AodAutoDimmingKey
import moe.rukamori.archivetune.constants.AodAutoLockEnabledKey
import moe.rukamori.archivetune.constants.AodAutoLockTimeoutKey
import moe.rukamori.archivetune.constants.AodBackgroundStyle
import moe.rukamori.archivetune.constants.AodBackgroundStyleKey
import moe.rukamori.archivetune.constants.AodBrightnessKey
import moe.rukamori.archivetune.constants.AodClockStyle
import moe.rukamori.archivetune.constants.AodClockStyleKey
import moe.rukamori.archivetune.constants.AodContentPosition
import moe.rukamori.archivetune.constants.AodContentPositionKey
import moe.rukamori.archivetune.constants.AodControlSizeKey
import moe.rukamori.archivetune.constants.AodControlStyle
import moe.rukamori.archivetune.constants.AodControlStyleKey
import moe.rukamori.archivetune.constants.AodGesturesEnabledKey
import moe.rukamori.archivetune.constants.AodHorizontalPaddingKey
import moe.rukamori.archivetune.constants.AodMarqueeTitlesKey
import moe.rukamori.archivetune.constants.AodMinimalLockedStateKey
import moe.rukamori.archivetune.constants.AodPixelShiftEnabledKey
import moe.rukamori.archivetune.constants.AodProximityBlackoutKey
import moe.rukamori.archivetune.constants.AodShakeToUnlockKey
import moe.rukamori.archivetune.constants.AodShowAlbumKey
import moe.rukamori.archivetune.constants.AodShowArtistKey
import moe.rukamori.archivetune.constants.AodShowBatteryKey
import moe.rukamori.archivetune.constants.AodShowClockKey
import moe.rukamori.archivetune.constants.AodShowControlsKey
import moe.rukamori.archivetune.constants.AodShowExitButtonKey
import moe.rukamori.archivetune.constants.AodShowLyricTickerKey
import moe.rukamori.archivetune.constants.AodShowProgressKey
import moe.rukamori.archivetune.constants.AodShowThumbnailKey
import moe.rukamori.archivetune.constants.AodShowTimeLabelsKey
import moe.rukamori.archivetune.constants.AodTextAlignment
import moe.rukamori.archivetune.constants.AodTextAlignmentKey
import moe.rukamori.archivetune.constants.AodThumbnailShape
import moe.rukamori.archivetune.constants.AodThumbnailShapeKey
import moe.rukamori.archivetune.constants.AodThumbnailShapeRotationKey
import moe.rukamori.archivetune.constants.AodThumbnailSizeKey
import moe.rukamori.archivetune.constants.AodTitleMaxLinesKey
import moe.rukamori.archivetune.constants.AodTouchLockEnabledKey
import moe.rukamori.archivetune.constants.AodTrueAmbientModeKey
import moe.rukamori.archivetune.constants.AodUnlockMethod
import moe.rukamori.archivetune.constants.AodUnlockMethodKey
import moe.rukamori.archivetune.constants.AodVerticalSpacingKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.lyrics.LyricsEntry
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.utils.supportsArtworkGlowShadow
import moe.rukamori.archivetune.ui.utils.toComposeShape
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.reportException
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

private val White70 = Color.White.copy(alpha = 0.70f)
private val White65 = Color.White.copy(alpha = 0.65f)
private val White35 = Color.White.copy(alpha = 0.35f)
private val White30 = Color.White.copy(alpha = 0.30f)
private val White15 = Color.White.copy(alpha = 0.15f)
private val AodLyricsWhitespaceRegex = "\\s+".toRegex()

private data class AodLyricsTickerData(
    val lines: List<LyricsEntry>,
    val isTtml: Boolean,
    val fallbackText: String?,
)

private fun parseAodLyricsTickerData(lyrics: String?): AodLyricsTickerData {
    val normalizedLyrics = lyrics?.let(LyricsUtils::normalizeLyricsText).orEmpty()
    if (normalizedLyrics.isBlank() || normalizedLyrics == LyricsEntity.LYRICS_NOT_FOUND) {
        return AodLyricsTickerData(emptyList(), isTtml = false, fallbackText = null)
    }

    val isTtml = LyricsUtils.isTtml(normalizedLyrics)
    val isLineSynced = LyricsUtils.isLineSyncedLrc(normalizedLyrics)
    if (!isTtml && !isLineSynced) {
        return AodLyricsTickerData(
            lines = emptyList(),
            isTtml = false,
            fallbackText = normalizedLyrics,
        )
    }

    val lines =
        try {
            if (isTtml) {
                LyricsUtils.parseTtml(normalizedLyrics)
            } else {
                LyricsUtils.parseLyrics(normalizedLyrics)
            }
        } catch (exception: Exception) {
            reportException(exception)
            emptyList()
        }

    return AodLyricsTickerData(
        lines = lines,
        isTtml = isTtml,
        fallbackText = null,
    )
}

private fun AodLyricsTickerData.textAt(position: Long): String? {
    if (lines.isEmpty()) return fallbackText?.takeIf { it.isNotBlank() }

    val currentLineIndex =
        LyricsUtils.findCurrentLineIndex(
            lines = lines,
            position = position,
            leadMs = if (isTtml) 0L else 300L,
        )
    return lines
        .getOrNull(currentLineIndex)
        ?.text
        ?.replace(AodLyricsWhitespaceRegex, " ")
        ?.trim()
        ?.takeIf { it.isNotEmpty() }
}

@Composable
fun AodPlayerScreen(
    mediaMetadata: MediaMetadata,
    isPlaying: Boolean,
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    thumbnailCornerRadius: Float,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    lyricsText: String? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val (thumbnailShapeType) = rememberEnumPreference(AodThumbnailShapeKey, AodThumbnailShape.ROUNDED)
    val (thumbnailSize) = rememberPreference(AodThumbnailSizeKey, 260f)
    val (thumbnailShapeRotation) = rememberPreference(AodThumbnailShapeRotationKey, 0)
    val (showThumbnail) = rememberPreference(AodShowThumbnailKey, true)
    val (showArtist) = rememberPreference(AodShowArtistKey, true)
    val (showAlbum) = rememberPreference(AodShowAlbumKey, false)
    val (showProgress) = rememberPreference(AodShowProgressKey, true)
    val (showTimeLabels) = rememberPreference(AodShowTimeLabelsKey, true)
    val (showControls) = rememberPreference(AodShowControlsKey, true)
    val (showExitButton) = rememberPreference(AodShowExitButtonKey, true)
    val (showLyricTicker) = rememberPreference(AodShowLyricTickerKey, true)
    val (artworkGlow) = rememberPreference(AodArtworkGlowKey, true)
    val (backgroundStyle) = rememberEnumPreference(AodBackgroundStyleKey, AodBackgroundStyle.PURE_BLACK)
    val (accentStyle) = rememberEnumPreference(AodAccentStyleKey, AodAccentStyle.MONOCHROME)
    val (contentPosition) = rememberEnumPreference(AodContentPositionKey, AodContentPosition.CENTER)
    val (textAlignment) = rememberEnumPreference(AodTextAlignmentKey, AodTextAlignment.CENTER)
    val (controlStyle) = rememberEnumPreference(AodControlStyleKey, AodControlStyle.FILLED)
    val (controlSize) = rememberPreference(AodControlSizeKey, 64f)
    val (horizontalPadding) = rememberPreference(AodHorizontalPaddingKey, 40f)
    val (verticalSpacing) = rememberPreference(AodVerticalSpacingKey, 20f)
    val (titleMaxLines) = rememberPreference(AodTitleMaxLinesKey, 1)
    val (ambientIntensity) = rememberPreference(AodAmbientIntensityKey, 0.18f)

    val (touchLockEnabled) = rememberPreference(AodTouchLockEnabledKey, false)
    val (unlockMethod) = rememberEnumPreference(AodUnlockMethodKey, AodUnlockMethod.SLIDE)
    val (showClock) = rememberPreference(AodShowClockKey, true)
    val (clockStyle) = rememberEnumPreference(AodClockStyleKey, AodClockStyle.BOLD_DIGITAL)
    val (showBattery) = rememberPreference(AodShowBatteryKey, true)
    val (pixelShiftEnabled) = rememberPreference(AodPixelShiftEnabledKey, true)
    val (autoDimming) = rememberPreference(AodAutoDimmingKey, true)
    val (autoDimTimeout) = rememberPreference(AodAutoDimTimeoutKey, 5)
    val (gesturesEnabled) = rememberPreference(AodGesturesEnabledKey, true)
    val (shakeToUnlock) = rememberPreference(AodShakeToUnlockKey, false)
    val (autoLockEnabled) = rememberPreference(AodAutoLockEnabledKey, false)
    val (autoLockTimeout) = rememberPreference(AodAutoLockTimeoutKey, 10)
    val (marqueeTitles) = rememberPreference(AodMarqueeTitlesKey, false)
    val (minimalLockedState) = rememberPreference(AodMinimalLockedStateKey, false)
    val (trueAmbientModeEnabled) = rememberPreference(AodTrueAmbientModeKey, true)
    val (aodBrightness) = rememberPreference(AodBrightnessKey, 0.15f)
    val (proximityBlackoutEnabled) = rememberPreference(AodProximityBlackoutKey, false)

    val lyricsTickerData = remember(lyricsText) { parseAodLyricsTickerData(lyricsText) }
    val lyricsTickerText =
        remember(lyricsTickerData, position, sliderPosition) {
            lyricsTickerData.textAt(sliderPosition ?: position)
        }

    var isLocked by remember { mutableStateOf(touchLockEnabled) }
    var pixelShiftOffset by remember { mutableStateOf(IntOffset.Zero) }
    var isDimmed by remember { mutableStateOf(false) }
    var isCovered by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val isAmbient = trueAmbientModeEnabled && isDimmed

    val contentAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.25f else 1.0f,
        animationSpec = tween(500),
        label = "dimAlpha",
    )

    fun resetInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (isDimmed) isDimmed = false
    }

    LaunchedEffect(Unit) {
        resetInteraction()
    }

    LaunchedEffect(pixelShiftEnabled) {
        if (pixelShiftEnabled) {
            val shifts = listOf(
                IntOffset(0, 0), IntOffset(8, 4), IntOffset(-8, -4),
                IntOffset(4, -8), IntOffset(-6, 6), IntOffset(6, -6)
            )
            var index = 0
            while (true) {
                delay(60000L)
                index = (index + 1) % shifts.size
                pixelShiftOffset = shifts[index]
            }
        } else {
            pixelShiftOffset = IntOffset.Zero
        }
    }

    LaunchedEffect(autoLockEnabled, autoLockTimeout, lastInteractionTime, isLocked) {
        if (!autoLockEnabled || isLocked) return@LaunchedEffect
        val timeoutMs = autoLockTimeout.coerceIn(3, 120) * 1000L
        delay(timeoutMs)
        isLocked = true
    }

    DisposableEffect(proximityBlackoutEnabled) {
        if (!proximityBlackoutEnabled) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val proximitySensor = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)
        if (proximitySensor == null) return@DisposableEffect onDispose {}

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val distance = event.values.getOrNull(0) ?: return
                val maxRange = proximitySensor.maximumRange
                isCovered = maxRange > 0f && distance <= (maxRange * 0.1f).coerceAtLeast(1.0f)
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, proximitySensor, SensorManager.SENSOR_DELAY_NORMAL)
        onDispose {
            sensorManager.unregisterListener(listener)
        }
    }

    DisposableEffect(shakeToUnlock, isLocked) {
        if (!shakeToUnlock || !isLocked) return@DisposableEffect onDispose {}
        val sensorManager = context.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var hasBaseline = false
        var lastShakeTime = 0L
        var lastX = 0f; var lastY = 0f; var lastZ = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                if (!hasBaseline) {
                    lastX = x; lastY = y; lastZ = z
                    hasBaseline = true
                    return
                }
                val delta = abs(x - lastX) + abs(y - lastY) + abs(z - lastZ)
                lastX = x; lastY = y; lastZ = z
                val now = System.currentTimeMillis()
                if (delta > 18f && now - lastShakeTime > 1000L) {
                    lastShakeTime = now
                    isLocked = false
                    resetInteraction()
                }
            }
            override fun onAccuracyChanged(sensor: Sensor, accuracy: Int) {}
        }
        sensorManager?.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose { sensorManager?.unregisterListener(listener) }
    }

    LaunchedEffect(autoDimming, autoDimTimeout, lastInteractionTime, isDimmed) {
        if (!autoDimming || isDimmed) return@LaunchedEffect
        val timeoutMs = autoDimTimeout.coerceIn(3, 30) * 1000L
        delay(timeoutMs)
        isDimmed = true
    }

    DisposableEffect(isDimmed, isCovered, aodBrightness, proximityBlackoutEnabled) {
        val window = (context as? Activity)?.window ?: (context as? android.service.dreams.DreamService)?.window
        window?.let { w ->
            val lp = w.attributes
            if (isCovered && proximityBlackoutEnabled) {
                lp.screenBrightness = 0.001f
            } else if (isDimmed) {
                lp.screenBrightness = aodBrightness.coerceIn(0.01f, 1.0f)
            } else {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            w.attributes = lp
        }
        onDispose {
            val window = (context as? Activity)?.window ?: (context as? android.service.dreams.DreamService)?.window
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    var extractedArtworkColors by remember { mutableStateOf<List<Color>>(emptyList()) }

    LaunchedEffect(mediaMetadata.thumbnailUrl) {
        val url = mediaMetadata.thumbnailUrl ?: return@LaunchedEffect
        val fallbackColor = 0xFF121212.toInt()
        val request = ImageRequest.Builder(context)
            .data(url)
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
                extractedArtworkColors = PlayerColorExtractor.extractGradientColors(
                    palette = palette,
                    fallbackColor = fallbackColor,
                )
            }
        }
    }

    val dominantArtworkColor = extractedArtworkColors.firstOrNull() ?: MaterialTheme.colorScheme.primary
    val targetAccentColor =
        if (accentStyle == AodAccentStyle.THEME) dominantArtworkColor else Color.White

    val accentColor by animateColorAsState(
        targetValue = targetAccentColor,
        animationSpec = tween(1000),
        label = "accentColorMorph",
    )
    val supportsArtworkGlowShadow = thumbnailShapeType.supportsArtworkGlowShadow()
    val thumbnailShape =
        thumbnailShapeType.toComposeShape(
            cornerRadius = thumbnailCornerRadius,
            startAngle = thumbnailShapeRotation,
        )
    val artworkSize = thumbnailSize.coerceIn(160f, 340f).dp
    val artworkSizePx = with(density) { artworkSize.roundToPx().coerceAtLeast(1) }
    val imageRequest =
        remember(context, mediaMetadata.thumbnailUrl, artworkSizePx) {
            ImageRequest
                .Builder(context)
                .data(mediaMetadata.thumbnailUrl)
                .size(artworkSizePx, artworkSizePx)
                .allowHardware(true)
                .build()
        }
    val artistText =
        remember(mediaMetadata.artists) {
            mediaMetadata.artists.joinToString { it.name }
        }
    val contentAlignment = contentPosition.toBoxAlignment()
    val textHorizontalAlignment = textAlignment.toHorizontalAlignment()
    val textAlign = textAlignment.toTextAlign()

    BackHandler(enabled = true) {
        if (isLocked) {
            resetInteraction()
        } else {
            onExit()
        }
    }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .pointerInput(gesturesEnabled, isLocked) {
                    detectTapGestures(
                        onTap = { resetInteraction() },
                        onDoubleTap = {
                            resetInteraction()
                            if (gesturesEnabled && !isLocked) {
                                onPlayPause()
                            }
                        }
                    )
                }
                .pointerInput(gesturesEnabled, isLocked) {
                    detectHorizontalDragGestures(
                        onDragStart = { resetInteraction() },
                        onHorizontalDrag = { _, _ -> },
                        onDragEnd = {
                            resetInteraction()
                        }
                    )
                }
                .pointerInput(gesturesEnabled, isLocked) {
                    if (gesturesEnabled && !isLocked) {
                        var accumVerticalDrag = 0f
                        detectVerticalDragGestures(
                            onDragStart = {
                                resetInteraction()
                                accumVerticalDrag = 0f
                            },
                            onVerticalDrag = { _, dragAmount ->
                                resetInteraction()
                                accumVerticalDrag += dragAmount
                                if (kotlin.math.abs(accumVerticalDrag) > 40f) {
                                    val audioManager = context.getSystemService(android.content.Context.AUDIO_SERVICE) as? android.media.AudioManager
                                    val direction = if (accumVerticalDrag < 0) android.media.AudioManager.ADJUST_RAISE else android.media.AudioManager.ADJUST_LOWER
                                    audioManager?.adjustStreamVolume(android.media.AudioManager.STREAM_MUSIC, direction, android.media.AudioManager.FLAG_SHOW_UI)
                                    accumVerticalDrag = 0f
                                }
                            },
                            onDragEnd = { resetInteraction() },
                        )
                    }
                }
                .aodBackground(
                    style = backgroundStyle,
                    accentColor = accentColor,
                    ambientIntensity = ambientIntensity,
                ),
    ) {
        if (showExitButton && !isLocked) {
            IconButton(
                onClick = {
                    resetInteraction()
                    onExit()
                },
                modifier =
                    Modifier
                        .align(Alignment.TopEnd)
                        .safeDrawingPadding()
                        .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = null,
                    tint = White70,
                )
            }
        }

        Column(
            horizontalAlignment = textHorizontalAlignment,
            verticalArrangement = Arrangement.spacedBy(verticalSpacing.coerceIn(8f, 36f).dp),
            modifier =
                Modifier
                    .align(contentAlignment)
                    .fillMaxWidth()
                    .offset { pixelShiftOffset }
                    .alpha(contentAlpha)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = horizontalPadding.coerceIn(16f, 72f).dp)
                    .padding(vertical = 32.dp),
        ) {
            AodClockWidget(
                showClock = showClock,
                clockStyle = clockStyle,
                showBattery = showBattery,
                accentColor = accentColor,
            )

            AnimatedVisibility(
                visible = showThumbnail && (!isLocked || !minimalLockedState),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                if (showThumbnail) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(artworkSize)
                            .then(
                                if (artworkGlow && supportsArtworkGlowShadow) {
                                    Modifier.shadow(
                                        elevation = 28.dp,
                                        shape = thumbnailShape,
                                        clip = false,
                                        ambientColor = accentColor,
                                        spotColor = accentColor,
                                    )
                                } else {
                                    Modifier
                                },
                            ).clip(thumbnailShape),
                )
                } 
            } 

            Column(
                horizontalAlignment = textHorizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                val showFullContent = !isLocked || !minimalLockedState

                Text(
                    text = mediaMetadata.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    maxLines = if (marqueeTitles) 1 else titleMaxLines.coerceIn(1, 3),
                    overflow = if (marqueeTitles) TextOverflow.Clip else TextOverflow.Ellipsis,
                    textAlign = textAlign,
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(if (marqueeTitles) Modifier.basicMarquee() else Modifier),
                )
                AnimatedVisibility(
                    visible = showFullContent && showArtist,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                ) {
                    if (showArtist) {
                        Text(
                            text = artistText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = White65,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showFullContent && showLyricTicker && !lyricsTickerText.isNullOrBlank(),
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                ) {
                    if (showLyricTicker && !lyricsTickerText.isNullOrBlank()) {
                        Text(
                            text = lyricsTickerText,
                            style = MaterialTheme.typography.bodySmall,
                            color = accentColor.copy(alpha = 0.85f),
                            maxLines = 1,
                            overflow = TextOverflow.Clip,
                            textAlign = textAlign,
                            modifier = Modifier
                                .fillMaxWidth()
                                .basicMarquee(),
                        )
                    }
                }
                AnimatedVisibility(
                    visible = showFullContent && showAlbum && mediaMetadata.album?.title?.isNotBlank() == true,
                    enter = fadeIn(tween(300)),
                    exit = fadeOut(tween(300)),
                ) {
                    if (showAlbum && mediaMetadata.album?.title?.isNotBlank() == true) {
                        Text(
                            text = mediaMetadata.album.title,
                            style = MaterialTheme.typography.labelMedium,
                            color = White65.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = textAlign,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = showProgress && !isAmbient && (!isLocked || !minimalLockedState),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                if (showProgress) {
                    AodSliderSection(
                        position = position,
                        duration = duration,
                        sliderPosition = sliderPosition,
                        accentColor = accentColor,
                        showTimeLabels = showTimeLabels,
                        onSeek = {
                            resetInteraction()
                            onSeek(it)
                        },
                        onSeekFinished = {
                            resetInteraction()
                            onSeekFinished()
                        },
                    )
                }
            }

            AnimatedVisibility(
                visible = showControls && !isAmbient && !isLocked,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                AodControls(
                    isPlaying = isPlaying,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    controlStyle = controlStyle,
                    controlSize = controlSize.coerceIn(52f, 84f),
                    accentColor = accentColor,
                    onPlayPause = {
                        resetInteraction()
                        onPlayPause()
                    },
                    onSkipPrevious = {
                        resetInteraction()
                        onSkipPrevious()
                    },
                    onSkipNext = {
                        resetInteraction()
                        onSkipNext()
                    },
                )
            }

            AnimatedVisibility(
                visible = !isLocked && !isAmbient,
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                AodSlideToLockButton(
                    accentColor = accentColor,
                    onLock = {
                        resetInteraction()
                        isLocked = true
                    },
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        if (isCovered && proximityBlackoutEnabled) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .zIndex(999f),
            )
        }

        AodTouchLockOverlay(
            isLocked = isLocked,
            unlockMethod = unlockMethod,
            accentColor = accentColor,
            onUnlock = {
                resetInteraction()
                isLocked = false
            },
        )
    }
}

@Composable
private fun AodSliderSection(
    position: Long,
    duration: Long,
    sliderPosition: Long?,
    accentColor: Color,
    showTimeLabels: Boolean,
    onSeek: (Long) -> Unit,
    onSeekFinished: () -> Unit,
) {
    val seekEnabled = duration > 0L && duration != C.TIME_UNSET
    val displayPosition = sliderPosition ?: position
    val sliderValue =
        remember(displayPosition, seekEnabled) {
            if (seekEnabled) displayPosition.toFloat() else 0f
        }
    val positionText = remember(displayPosition) { makeTimeString(displayPosition) }
    val durationText =
        remember(duration, seekEnabled) {
            if (seekEnabled) makeTimeString(duration) else ""
        }
    val sliderColors =
            SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
                inactiveTrackColor = White30,
                disabledThumbColor = White30,
                disabledActiveTrackColor = White30,
                disabledInactiveTrackColor = White15,
            )

    Column(modifier = Modifier.fillMaxWidth()) {
        Slider(
            value = sliderValue,
            onValueChange = { onSeek(it.toLong()) },
            onValueChangeFinished = onSeekFinished,
            valueRange = 0f..(if (seekEnabled) duration.toFloat() else 1f),
            enabled = seekEnabled,
            colors = sliderColors,
            modifier = Modifier.fillMaxWidth(),
        )
        if (showTimeLabels) {
            Row(
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = positionText,
                    style = MaterialTheme.typography.labelSmall,
                    color = White65,
                )
                Text(
                    text = durationText,
                    style = MaterialTheme.typography.labelSmall,
                    color = White65,
                )
            }
        }
    }
}

@Composable
private fun AodControls(
    isPlaying: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    controlStyle: AodControlStyle,
    controlSize: Float,
    accentColor: Color,
    onPlayPause: () -> Unit,
    onSkipPrevious: () -> Unit,
    onSkipNext: () -> Unit,
) {
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val playButtonSize = controlSize.dp
    val skipButtonSize = (controlSize * 0.75f).dp
    val playIconSize = (controlSize * 0.5f).dp
    val skipIconSize = (controlSize * 0.5f).dp
    val playButtonColors =
        IconButtonDefaults.filledIconButtonColors(
            containerColor = accentColor,
            contentColor = if (accentColor == Color.White) Color.Black else MaterialTheme.colorScheme.onPrimary,
        )
    val tonalButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = accentColor.copy(alpha = 0.22f),
            contentColor = Color.White,
        )

    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onSkipPrevious()
            },
            enabled = canSkipPrevious,
            modifier = Modifier.size(skipButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_previous),
                contentDescription = null,
                tint = if (canSkipPrevious) Color.White else White35,
                modifier = Modifier.size(skipIconSize),
            )
        }

        when (controlStyle) {
            AodControlStyle.FILLED -> {
                FilledIconButton(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onPlayPause()
                    },
                    modifier =
                        Modifier
                            .size(playButtonSize)
                            .clip(CircleShape),
                    colors = playButtonColors,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(playIconSize),
                    )
                }
            }

            AodControlStyle.TONAL -> {
                FilledTonalIconButton(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onPlayPause()
                    },
                    modifier =
                        Modifier
                            .size(playButtonSize)
                            .clip(CircleShape),
                    colors = tonalButtonColors,
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(playIconSize),
                    )
                }
            }

            AodControlStyle.MINIMAL -> {
                IconButton(
                    onClick = {
                        if (enableHapticFeedback) {
                            view.performHapticFeedback(
                                android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                            )
                        }
                        onPlayPause()
                    },
                    modifier = Modifier.size(playButtonSize),
                ) {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(playIconSize),
                    )
                }
            }
        }

        IconButton(
            onClick = {
                if (enableHapticFeedback) {
                    view.performHapticFeedback(
                        android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                        android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                    )
                }
                onSkipNext()
            },
            enabled = canSkipNext,
            modifier = Modifier.size(skipButtonSize),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = null,
                tint = if (canSkipNext) Color.White else White35,
                modifier = Modifier.size(skipIconSize),
            )
        }
    }
}

@Composable
private fun Modifier.aodBackground(
    style: AodBackgroundStyle,
    accentColor: Color,
    ambientIntensity: Float,
): Modifier {
    val alpha = ambientIntensity.coerceIn(0f, 1f)
    val brush =
        remember(style, accentColor, alpha) {
            when (style) {
                AodBackgroundStyle.PURE_BLACK -> {
                    Brush.verticalGradient(listOf(Color.Black, Color.Black))
                }

                AodBackgroundStyle.SOFT_RADIAL -> {
                    Brush.radialGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.30f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.TONAL_EDGE -> {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.25f * alpha),
                                Color.Black,
                                accentColor.copy(alpha = 0.15f * alpha),
                            ),
                    )
                }

                AodBackgroundStyle.AMBIENT_GLOW -> {
                    Brush.radialGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.35f * alpha),
                                accentColor.copy(alpha = 0.10f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.ADAPTIVE_ART -> {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.40f * alpha),
                                accentColor.copy(alpha = 0.15f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.FROSTED_WALLPAPER -> {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                Color(0xFF1E1E24).copy(alpha = 0.60f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.ADAPTIVE_FROSTED -> {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.35f * alpha),
                                Color(0xFF121216),
                                Color.Black,
                            ),
                    )
                }
            }
        }

    return background(brush)
}

private fun AodContentPosition.toBoxAlignment(): Alignment =
    when (this) {
        AodContentPosition.TOP -> Alignment.TopCenter
        AodContentPosition.CENTER -> Alignment.Center
        AodContentPosition.BOTTOM -> Alignment.BottomCenter
    }

private fun AodTextAlignment.toTextAlign(): TextAlign =
    when (this) {
        AodTextAlignment.START -> TextAlign.Start
        AodTextAlignment.CENTER -> TextAlign.Center
        AodTextAlignment.END -> TextAlign.End
    }

private fun AodTextAlignment.toHorizontalAlignment(): Alignment.Horizontal =
    when (this) {
        AodTextAlignment.START -> Alignment.Start
        AodTextAlignment.CENTER -> Alignment.CenterHorizontally
        AodTextAlignment.END -> Alignment.End
    }
