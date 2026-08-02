/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.C
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AodAccentStyle
import moe.rukamori.archivetune.constants.AodAccentStyleKey
import moe.rukamori.archivetune.constants.AodAmbientIntensityKey
import moe.rukamori.archivetune.constants.AodArtworkGlowKey
import moe.rukamori.archivetune.constants.AodBackgroundStyle
import moe.rukamori.archivetune.constants.AodBackgroundStyleKey
import moe.rukamori.archivetune.constants.AodContentPosition
import moe.rukamori.archivetune.constants.AodContentPositionKey
import moe.rukamori.archivetune.constants.AodControlSizeKey
import moe.rukamori.archivetune.constants.AodControlStyle
import moe.rukamori.archivetune.constants.AodControlStyleKey
import moe.rukamori.archivetune.constants.AodHorizontalPaddingKey
import moe.rukamori.archivetune.constants.AodShowAlbumKey
import moe.rukamori.archivetune.constants.AodShowArtistKey
import moe.rukamori.archivetune.constants.AodShowControlsKey
import moe.rukamori.archivetune.constants.AodShowExitButtonKey
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
import moe.rukamori.archivetune.constants.AodVerticalSpacingKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.utils.supportsArtworkGlowShadow
import moe.rukamori.archivetune.ui.utils.toComposeShape
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import android.app.Activity
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.WindowManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.constants.AodAutoLockEnabledKey
import moe.rukamori.archivetune.constants.AodAutoLockTimeoutKey
import moe.rukamori.archivetune.constants.AodAutoDimTimeoutKey
import moe.rukamori.archivetune.constants.AodAutoDimmingKey
import moe.rukamori.archivetune.constants.AodClockStyle
import moe.rukamori.archivetune.constants.AodClockStyleKey
import moe.rukamori.archivetune.constants.AodGesturesEnabledKey
import moe.rukamori.archivetune.constants.AodMarqueeTitlesKey
import moe.rukamori.archivetune.constants.AodMinimalLockedStateKey
import moe.rukamori.archivetune.constants.AodPixelShiftEnabledKey
import moe.rukamori.archivetune.constants.AodShakeToUnlockKey
import moe.rukamori.archivetune.constants.AodShowBatteryKey
import moe.rukamori.archivetune.constants.AodShowClockKey
import moe.rukamori.archivetune.constants.AodShowLyricTickerKey
import moe.rukamori.archivetune.constants.AodTouchLockEnabledKey
import moe.rukamori.archivetune.constants.AodUnlockMethod
import moe.rukamori.archivetune.constants.AodUnlockMethodKey
import moe.rukamori.archivetune.ui.player.AodClockWidget
import moe.rukamori.archivetune.ui.player.AodTouchLockOverlay

private val White70 = Color.White.copy(alpha = 0.70f)
private val White65 = Color.White.copy(alpha = 0.65f)
private val White35 = Color.White.copy(alpha = 0.35f)
private val White30 = Color.White.copy(alpha = 0.30f)
private val White15 = Color.White.copy(alpha = 0.15f)

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

    // New Advanced AOD Preferences
    val (touchLockEnabled) = rememberPreference(AodTouchLockEnabledKey, false)
    val (unlockMethod) = rememberEnumPreference(AodUnlockMethodKey, AodUnlockMethod.SLIDE)
    val (showClock) = rememberPreference(AodShowClockKey, true)
    val (clockStyle) = rememberEnumPreference(AodClockStyleKey, AodClockStyle.BOLD_DIGITAL)
    val (showBattery) = rememberPreference(AodShowBatteryKey, true)
    val (pixelShiftEnabled) = rememberPreference(AodPixelShiftEnabledKey, true)
    val (autoDimming) = rememberPreference(AodAutoDimmingKey, true)
    val (autoDimTimeout) = rememberPreference(AodAutoDimTimeoutKey, 5)
    val (gesturesEnabled) = rememberPreference(AodGesturesEnabledKey, true)
    // Feature: shake-to-unlock, auto-lock, marquee, minimal locked state
    val (shakeToUnlock) = rememberPreference(AodShakeToUnlockKey, false)
    val (autoLockEnabled) = rememberPreference(AodAutoLockEnabledKey, false)
    val (autoLockTimeout) = rememberPreference(AodAutoLockTimeoutKey, 10)
    val (marqueeTitles) = rememberPreference(AodMarqueeTitlesKey, false)
    val (minimalLockedState) = rememberPreference(AodMinimalLockedStateKey, false)

    // Bug fix #1: Don't use touchLockEnabled as a remember key — that would reset
    // isLocked to its default whenever ANY preference changes mid-session.
    var isLocked by remember { mutableStateOf(touchLockEnabled) }
    var pixelShiftOffset by remember { mutableStateOf(IntOffset.Zero) }
    var isDimmed by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    val contentAlpha by animateFloatAsState(
        targetValue = if (isDimmed) 0.25f else 1.0f,
        animationSpec = tween(500),
        label = "dimAlpha",
    )

    fun resetInteraction() {
        lastInteractionTime = System.currentTimeMillis()
        if (isDimmed) isDimmed = false
    }

    // OLED Pixel Shifting Loop (Every 60 Seconds)
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

    // Feature #2: Auto-lock — automatically lock the screen after N seconds of entering AOD
    LaunchedEffect(autoLockEnabled, autoLockTimeout) {
        if (autoLockEnabled && !isLocked) {
            delay(autoLockTimeout.coerceIn(3, 120) * 1000L)
            isLocked = true
        }
    }

    // Feature #1: Shake-to-unlock — register accelerometer listener when locked
    DisposableEffect(shakeToUnlock, isLocked) {
        if (!shakeToUnlock || !isLocked) return@DisposableEffect onDispose {}
        val sensorManager = (context as? Activity)
            ?.getSystemService(android.content.Context.SENSOR_SERVICE) as? SensorManager
        val accelerometer = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        var lastShakeTime = 0L
        var lastX = 0f; var lastY = 0f; var lastZ = 0f
        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent) {
                val x = event.values[0]; val y = event.values[1]; val z = event.values[2]
                val delta = Math.abs(x - lastX) + Math.abs(y - lastY) + Math.abs(z - lastZ)
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

    // Bug fix #2: Use a single cancellable coroutine for auto-dim.
    // LaunchedEffect cancels the previous coroutine when lastInteractionTime changes,
    // so there's never more than one timer running at once.
    LaunchedEffect(autoDimming, autoDimTimeout, lastInteractionTime) {
        if (!autoDimming) return@LaunchedEffect
        val timeoutMs = autoDimTimeout.coerceIn(3, 30) * 1000L
        delay(timeoutMs)
        isDimmed = true
    }

    // Hardware Screen Brightness Control (iPhone AOD Style Dimming)
    DisposableEffect(isDimmed, isLocked) {
        val window = (context as? Activity)?.window
        window?.let { w ->
            val lp = w.attributes
            if (isDimmed || isLocked) {
                lp.screenBrightness = 0.01f
            } else {
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
            }
            w.attributes = lp
        }
        onDispose {
            val window = (context as? Activity)?.window
            window?.let { w ->
                val lp = w.attributes
                lp.screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
                w.attributes = lp
            }
        }
    }
    val accentColor =
        if (accentStyle == AodAccentStyle.THEME) MaterialTheme.colorScheme.primary else Color.White
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
                // Consume ALL vertical drags to block the notification shade
                // from pulling down while in AOD mode.
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = { resetInteraction() },
                        onVerticalDrag = { _, _ -> }, // consume and discard
                        onDragEnd = { resetInteraction() },
                    )
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
                    contentDescription = stringResource(R.string.aod_mode_exit),
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
            // Live Clock & Battery Widget
            AodClockWidget(
                showClock = showClock,
                clockStyle = clockStyle,
                showBattery = showBattery,
                accentColor = accentColor,
            )

            // Feature #5: Hide artwork in minimal locked state
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
                } // end if (showThumbnail)
            } // end AnimatedVisibility

            Column(
                horizontalAlignment = textHorizontalAlignment,
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                // Feature #5: Minimal locked state — show only clock + title when locked
                val showFullContent = !isLocked || !minimalLockedState

                // Feature #4: Marquee scrolling for long titles
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

            // Feature #5: Progress and controls hidden in minimal locked state
            AnimatedVisibility(
                visible = showProgress && (!isLocked || !minimalLockedState),
                enter = fadeIn(tween(300)),
                exit = fadeOut(tween(300)),
            ) {
                if (showProgress && !isLocked) {
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

            if (showControls && !isLocked) {
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

            // Bug fix #5: Slide-to-lock shown independently of showControls so the
            // user can always lock even when playback controls are hidden in settings.
            if (!isLocked) {
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

        // Touch Lock Overlay
        AodTouchLockOverlay(
            isLocked = isLocked,
            unlockMethod = unlockMethod,
            accentColor = accentColor,
            onUnlock = {
                resetInteraction()
                isLocked = false
            },
            onAuthenticateBiometric = {
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
                                accentColor.copy(alpha = 0.22f * alpha),
                                Color.Black,
                            ),
                    )
                }

                AodBackgroundStyle.TONAL_EDGE -> {
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.18f * alpha),
                                Color.Black,
                                accentColor.copy(alpha = 0.12f * alpha),
                            ),
                    )
                }

                AodBackgroundStyle.AMBIENT_GLOW -> {
                    Brush.linearGradient(
                        colors =
                            listOf(
                                accentColor.copy(alpha = 0.28f * alpha),
                                Color.Black,
                                Color(0xFF101010),
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
