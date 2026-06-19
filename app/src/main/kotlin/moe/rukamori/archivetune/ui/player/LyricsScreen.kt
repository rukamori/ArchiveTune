/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.player

import android.content.res.Configuration
import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.LyricsMode
import moe.rukamori.archivetune.constants.LyricsModeKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LyricsV2
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.menu.LyricsMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.coroutines.cancellation.CancellationException

private val AppleMusicFallbackGradient = listOf(
    Color(0xFF202020),
    Color(0xFF141414),
    Color(0xFF050505),
)

private val AppleMusicForeground = Color.White

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
    val onVolumeChange = remember(deviceMusicVolumeController) {
        { volume: Float ->
            deviceMusicVolumeController.setVolumeFraction(volume)
        }
    }
    val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val lyricsMode by rememberEnumPreference(LyricsModeKey, LyricsMode.ENHANCED)

    val hapticClick = remember(enableHapticFeedback, view) {
        {
            if (enableHapticFeedback) {
                view.performHapticFeedback(
                    HapticFeedbackConstants.CONTEXT_CLICK,
                    HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                )
            }
        }
    }
    val lyricsHelper = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            moe.rukamori.archivetune.di.LyricsHelperEntryPoint::class.java,
        ).lyricsHelper()
    }

    LaunchedEffect(mediaMetadata.id, currentLyrics?.lyrics) {
        if (currentLyrics != null) return@LaunchedEffect
        try {
            val existingLyrics = withContext(Dispatchers.IO) {
                database.lyrics(mediaMetadata.id).first()
            }
            if (existingLyrics != null) return@LaunchedEffect

            val lyrics = withContext(Dispatchers.IO) {
                lyricsHelper.getLyrics(mediaMetadata)
            }
            withContext(Dispatchers.IO) {
                database.query {
                    insertLyricsIfAbsent(
                        id = mediaMetadata.id,
                        lyrics = lyrics,
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

    val gradientColorsCache = remember {
        object : LinkedHashMap<String, List<Color>>(20, 0.75f, true) {
            override fun removeEldestEntry(eldest: Map.Entry<String, List<Color>>) = size > 20
        }
    }
    val fallbackColor = remember { Color.Black.toArgb() }

    LaunchedEffect(mediaMetadata.id, mediaMetadata.thumbnailUrl) {
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

        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .size(Size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE))
            .allowHardware(false)
            .build()

        val extractedColors = try {
            val image = withContext(Dispatchers.IO) {
                context.imageLoader.execute(request)
            }.image
            if (image == null) {
                null
            } else {
                val bitmap = image.toBitmap()
                withContext(Dispatchers.Default) {
                    val palette = Palette.from(bitmap)
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
                onDismiss = menuState::dismiss,
            )
        }
    }

    val isLoading = playbackState == STATE_BUFFERING || sliderPosition != null
    val orientation = LocalConfiguration.current.orientation

    BackHandler(enabled = backHandlerEnabled, onBack = onBackClick)

    Box(modifier = modifier.fillMaxSize()) {
        AppleMusicBackground(
            mediaMetadata = mediaMetadata,
            gradientColors = gradientColors,
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars),
        ) {
            AppleMusicGrabber(onClick = onBackClick)
            AppleMusicTrackHeader(
                mediaMetadata = mediaMetadata,
                onMoreClick = showLyricsMenu,
                onDismissClick = onBackClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 36.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AppleMusicLyricsPane(
                        lyricsMode = lyricsMode,
                        sliderPositionProvider = { sliderPosition },
                        lyricsSyncOffset = lyricsSyncOffset,
                        modifier = Modifier
                            .weight(1.15f)
                            .fillMaxHeight()
                            .padding(end = 32.dp),
                    )

                    Column(
                        modifier = Modifier
                            .weight(0.85f)
                            .widthIn(max = 420.dp),
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
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                AppleMusicLyricsPane(
                    lyricsMode = lyricsMode,
                    sliderPositionProvider = { sliderPosition },
                    lyricsSyncOffset = lyricsSyncOffset,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                )

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
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 40.dp),
                )
            }
        }
    }
}

@Composable
private fun AppleMusicBackground(
    mediaMetadata: MediaMetadata,
    gradientColors: List<Color>,
    modifier: Modifier = Modifier,
) {
    val colors = if (gradientColors.isNotEmpty()) gradientColors else AppleMusicFallbackGradient
    val backgroundBrush = remember(colors) {
        Brush.verticalGradient(
            listOf(
                colors.getOrElse(0) { AppleMusicFallbackGradient[0] }.copy(alpha = 0.88f),
                colors.getOrElse(1) { AppleMusicFallbackGradient[1] }.copy(alpha = 0.76f),
                colors.getOrElse(2) { AppleMusicFallbackGradient[2] }.copy(alpha = 0.96f),
            ),
        )
    }
    val bottomScrim = remember {
        Brush.verticalGradient(
            listOf(
                Color.Transparent,
                Color.Black.copy(alpha = 0.28f),
            ),
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
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
                    modifier = Modifier
                        .fillMaxSize()
                        .blur(46.dp)
                        .alpha(0.62f),
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(backgroundBrush),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.18f)),
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(bottomScrim),
        )
    }
}

@Composable
private fun AppleMusicGrabber(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val closeDescription = stringResource(R.string.close)
    Box(
        modifier = modifier
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
            modifier = Modifier
                .width(42.dp)
                .height(5.dp)
                .clip(RoundedCornerShape(50))
                .background(AppleMusicForeground.copy(alpha = 0.34f)),
        )
    }
}

@Composable
private fun AppleMusicTrackHeader(
    mediaMetadata: MediaMetadata,
    onMoreClick: () -> Unit,
    onDismissClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val artistText = remember(mediaMetadata.id, mediaMetadata.artists) {
        mediaMetadata.artists.joinToString { it.name }
    }

    Row(
        modifier = modifier.heightIn(min = 64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(7.dp))
                .background(AppleMusicForeground.copy(alpha = 0.18f)),
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
                    tint = AppleMusicForeground.copy(alpha = 0.72f),
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
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = AppleMusicForeground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = artistText,
                style = MaterialTheme.typography.bodyLarge,
                color = AppleMusicForeground.copy(alpha = 0.72f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.close,
            contentDescription = stringResource(R.string.close),
            onClick = onDismissClick,
        )

        Spacer(modifier = Modifier.width(4.dp))

        AppleMusicHeaderIconButton(
            iconRes = R.drawable.more_horiz,
            contentDescription = stringResource(R.string.more_options),
            onClick = onMoreClick,
        )
    }
}

@Composable
private fun AppleMusicHeaderIconButton(
    iconRes: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
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
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(AppleMusicForeground.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = AppleMusicForeground,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}

@Composable
private fun AppleMusicLyricsPane(
    lyricsMode: LyricsMode,
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    modifier: Modifier = Modifier,
) {
    LyricsContent(
        lyricsMode = lyricsMode,
        sliderPositionProvider = sliderPositionProvider,
        lyricsSyncOffset = lyricsSyncOffset,
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        textColor = AppleMusicForeground,
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
    modifier: Modifier = Modifier,
) {
    val position = positionProvider()
    val duration = durationProvider()
    val hasDuration = duration != C.TIME_UNSET && duration > 0L
    val safeDuration = if (hasDuration) duration else 1L
    val currentPosition = (sliderPosition ?: position).coerceIn(0L, safeDuration)
    val remainingPosition = (safeDuration - currentPosition).coerceAtLeast(0L)

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppleMusicSlider(
            value = currentPosition.toFloat(),
            valueRange = 0f..safeDuration.toFloat(),
            activeColor = AppleMusicForeground.copy(alpha = 0.94f),
            inactiveColor = AppleMusicForeground.copy(alpha = 0.28f),
            trackHeight = 8.dp,
            onValueChange = { onPositionChange(it.toLong()) },
            onValueChangeFinished = onPositionChangeFinished,
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = makeTimeString(currentPosition),
                style = MaterialTheme.typography.labelMedium,
                color = AppleMusicForeground.copy(alpha = 0.54f),
            )
            Text(
                text = if (hasDuration) "-${makeTimeString(remainingPosition)}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = AppleMusicForeground.copy(alpha = 0.54f),
            )
        }

        Row(
            modifier = Modifier
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
                onClick = onPreviousClick,
            )
            IconButton(
                onClick = onPlayPauseClick,
                modifier = Modifier.size(74.dp),
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(42.dp),
                        color = AppleMusicForeground,
                    )
                } else {
                    Icon(
                        painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.widget_pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        tint = AppleMusicForeground,
                        modifier = Modifier.size(54.dp),
                    )
                }
            }
            AppleMusicTransportButton(
                iconRes = R.drawable.skip_next,
                contentDescription = stringResource(R.string.next),
                iconSize = 44.dp,
                touchSize = 68.dp,
                onClick = onNextClick,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 26.dp, bottom = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.volume_off),
                contentDescription = stringResource(R.string.minimum_volume),
                tint = AppleMusicForeground.copy(alpha = 0.66f),
                modifier = Modifier.size(17.dp),
            )
            AppleMusicSlider(
                value = volume.coerceIn(0f, 1f),
                valueRange = 0f..1f,
                activeColor = AppleMusicForeground.copy(alpha = 0.88f),
                inactiveColor = AppleMusicForeground.copy(alpha = 0.24f),
                trackHeight = 8.dp,
                onValueChange = onVolumeChange,
                onValueChangeFinished = {},
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 16.dp),
            )
            Icon(
                painter = painterResource(R.drawable.volume_up),
                contentDescription = stringResource(R.string.maximum_volume),
                tint = AppleMusicForeground.copy(alpha = 0.66f),
                modifier = Modifier.size(19.dp),
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(touchSize),
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = AppleMusicForeground,
            modifier = Modifier.size(iconSize),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppleMusicSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val safeStart = valueRange.start
    val safeEnd = valueRange.endInclusive.coerceAtLeast(safeStart + 1f)
    val safeRange = safeStart..safeEnd
    val sliderColors = SliderDefaults.colors(
        activeTrackColor = activeColor,
        activeTickColor = activeColor,
        thumbColor = Color.Transparent,
        inactiveTrackColor = inactiveColor,
    )

    Slider(
        value = value.coerceIn(safeRange),
        valueRange = safeRange,
        onValueChange = onValueChange,
        onValueChangeFinished = onValueChangeFinished,
        colors = sliderColors,
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
    sliderPositionProvider: () -> Long?,
    lyricsSyncOffset: Int,
    textColor: Color,
    modifier: Modifier = Modifier,
) {
    when (lyricsMode) {
        LyricsMode.V2 -> LyricsV2(
            sliderPositionProvider = sliderPositionProvider,
            lyricsSyncOffset = lyricsSyncOffset,
            modifier = modifier,
            textColorOverride = textColor,
        )
        LyricsMode.ENHANCED -> LyricsEnhanced(
            sliderPositionProvider = sliderPositionProvider,
            lyricsSyncOffset = lyricsSyncOffset,
            modifier = modifier,
            textColorOverride = textColor,
        )
    }
}
