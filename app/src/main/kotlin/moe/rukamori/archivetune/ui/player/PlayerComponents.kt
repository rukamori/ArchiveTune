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
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import me.saket.squiggles.SquigglySlider
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerButtonsStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.constants.PlayerHorizontalPadding
import moe.rukamori.archivetune.constants.SliderStyle
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.extensions.toggleRepeatMode
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.PlayerSliderTrack
import moe.rukamori.archivetune.ui.component.ResizableIconButton
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.theme.PlayerBackgroundColorUtils
import moe.rukamori.archivetune.ui.theme.PlayerSliderColors
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.makeTimeString
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalView
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.utils.rememberPreference

private const val PlayerBackgroundMaxBlurRadius = 64f

@Composable
fun PlayerTitleSection(
    mediaMetadata: MediaMetadata,
    textBackgroundColor: Color,
    navController: NavController,
    state: BottomSheetState,
) {
    // Tap/long-press behavior is centralized; this style keeps its own visual rendering.
    val actions = rememberPlayerTitleActions(
        mediaMetadata = mediaMetadata,
        navController = navController,
        state = state,
    )
    AnimatedContent(
        targetState = mediaMetadata.title,
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "",
    ) { title ->
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = textBackgroundColor,
            modifier =
            Modifier
                .basicMarquee()
                .combinedClickable(
                    enabled = true,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = actions.onTitleClick,
                    onLongClick = actions.onCopyTitle,
                ),
        )
    }

    Spacer(Modifier.height(6.dp))

    ClickableArtists(
        artists = mediaMetadata.artists,
        onArtistClick = actions.onArtistClick,
        style = MaterialTheme.typography.titleMedium.copy(color = textBackgroundColor, fontSize = 16.sp),
        onLongClick = actions.onCopyArtists,
        modifier = Modifier
            .fillMaxWidth()
            .basicMarquee()
            .padding(end = 12.dp),
    )
}

@Composable
fun PlayerTopActions(
    mediaMetadata: MediaMetadata,
    playerDesignStyle: PlayerDesignStyle,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    playerConnection: PlayerConnection,
    navController: NavController,
    menuState: MenuState,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    context: Context,
    currentSongLiked: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()

    when (playerDesignStyle) {
        PlayerDesignStyle.V2 -> {
            val shareShape = RoundedCornerShape(
                topStart = 50.dp, bottomStart = 50.dp,
                topEnd = 10.dp, bottomEnd = 10.dp
            )

            val favShape = RoundedCornerShape(
                topStart = 10.dp, bottomStart = 10.dp,
                topEnd = 50.dp, bottomEnd = 50.dp
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(shareShape)
                        .background(textButtonColor)
                        .clickable {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        }
                ) {
                    Image(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(favShape)
                        .background(textButtonColor)
                        .clickable {
                            playerConnection.toggleLike()
                        }
                ) {
                    Image(
                        painter = painterResource(
                            if (currentSongLiked)
                                R.drawable.favorite
                            else R.drawable.favorite_border
                        ),
                        contentDescription = null,
                        colorFilter = ColorFilter.tint(iconButtonColor),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .size(24.dp)
                    )
                }
            }
        }

        PlayerDesignStyle.V3, PlayerDesignStyle.V5 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable {
                            val intent = Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                )
                            }
                            context.startActivity(Intent.createChooser(intent, null))
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        tint = textBackgroundColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { playerConnection.toggleLike() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(
                            if (currentSongLiked) R.drawable.favorite
                            else R.drawable.favorite_border
                        ),
                        contentDescription = null,
                        tint = if (currentSongLiked)
                            MaterialTheme.colorScheme.error.copy(alpha = 0.9f)
                        else textBackgroundColor.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        PlayerDesignStyle.V4 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(44.dp)
                        .width(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            tint = textBackgroundColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Surface(
                    onClick = { playerConnection.toggleLike() },
                    shape = RoundedCornerShape(14.dp),
                    color = if (currentSongLiked)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                    else textBackgroundColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(44.dp)
                        .width(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(
                                if (currentSongLiked) R.drawable.favorite
                                else R.drawable.favorite_border
                            ),
                            contentDescription = null,
                            tint = if (currentSongLiked)
                                MaterialTheme.colorScheme.error
                            else textBackgroundColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // More menu button - cinematic glass card
                Surface(
                    onClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onShowDetailsDialog = {
                                    mediaMetadata.id.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(44.dp)
                        .width(44.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            tint = textBackgroundColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        PlayerDesignStyle.V1 -> {
            Box(
                modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(textButtonColor)
                    .clickable {
                        val intent =
                            Intent().apply {
                                action = Intent.ACTION_SEND
                                type = "text/plain"
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                                )
                            }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.share),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconButtonColor),
                    modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(24.dp),
                )
            }

            Spacer(modifier = Modifier.size(12.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier =
                Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(textButtonColor)
                    .clickable {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onShowDetailsDialog = {
                                    mediaMetadata.id.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
            ) {
                Image(
                    painter = painterResource(R.drawable.more_horiz),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(iconButtonColor),
                )
            }
        }

        PlayerDesignStyle.V6 -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    onClick = {
                        val intent = Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "https://music.youtube.com/watch?v=${mediaMetadata.id}"
                            )
                        }
                        context.startActivity(Intent.createChooser(intent, null))
                    },
                    shape = RoundedCornerShape(
                        topStart = 50.dp, bottomStart = 50.dp,
                        topEnd = 6.dp, bottomEnd = 6.dp
                    ),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(42.dp)
                        .width(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.share),
                            contentDescription = null,
                            tint = textBackgroundColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    onClick = { playerConnection.toggleLike() },
                    shape = RoundedCornerShape(50),
                    color = if (currentSongLiked)
                        MaterialTheme.colorScheme.error.copy(alpha = 0.18f)
                    else textBackgroundColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(42.dp)
                        .width(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(
                                if (currentSongLiked) R.drawable.favorite
                                else R.drawable.favorite_border
                            ),
                            contentDescription = null,
                            tint = if (currentSongLiked)
                                MaterialTheme.colorScheme.error
                            else textBackgroundColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Surface(
                    onClick = {
                        menuState.show {
                            PlayerMenu(
                                mediaMetadata = mediaMetadata,
                                navController = navController,
                                playerBottomSheetState = state,
                                onShowDetailsDialog = {
                                    mediaMetadata.id.let {
                                        bottomSheetPageState.show {
                                            ShowMediaInfo(it)
                                        }
                                    }
                                },
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    shape = RoundedCornerShape(
                        topStart = 6.dp, bottomStart = 6.dp,
                        topEnd = 50.dp, bottomEnd = 50.dp
                    ),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                    modifier = Modifier
                        .height(42.dp)
                        .width(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            painter = painterResource(R.drawable.more_horiz),
                            contentDescription = null,
                            tint = textBackgroundColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        PlayerDesignStyle.V7, PlayerDesignStyle.V8, PlayerDesignStyle.V9 -> Unit
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSlider(
    sliderStyle: SliderStyle,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    textButtonColor: Color,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit
) {
    val safeDuration = if (duration <= 0L) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, maxOf(0f, safeDuration))
    
    StyledPlaybackSlider(
        sliderStyle = sliderStyle,
        value = safeValue,
        valueRange = 0f..maxOf(1f, safeDuration),
        onValueChange = { onValueChange(it.toLong()) },
        onValueChangeFinished = onValueChangeFinished,
        activeColor = textButtonColor,
        isPlaying = isPlaying,
        modifier = Modifier.padding(horizontal = PlayerHorizontalPadding)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StyledPlaybackSlider(
    sliderStyle: SliderStyle,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    activeColor: Color,
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    when (sliderStyle) {
        SliderStyle.Standard -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.standardSliderColors(activeColor),
                modifier = modifier
            )
        }

        SliderStyle.Wavy -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.wavySliderColors(activeColor),
                modifier = modifier,
                squigglesSpec = SquigglySlider.SquigglesSpec(
                    amplitude = if (isPlaying) 2.dp else 0.dp,
                    strokeWidth = 6.dp
                )
            )
        }

        SliderStyle.Thick -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.thickSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = PlayerSliderColors.thickSliderColors(activeColor),
                        trackHeight = 12.dp
                    )
                },
                modifier = modifier
            )
        }

        SliderStyle.Circular -> {
            SquigglySlider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.circularSliderColors(activeColor),
                modifier = modifier,
                squigglesSpec = SquigglySlider.SquigglesSpec(
                    amplitude = if (isPlaying) 2.dp else 0.dp,
                    strokeWidth = 6.dp
                )
            )
        }

        SliderStyle.Simple -> {
            Slider(
                value = value,
                valueRange = valueRange,
                onValueChange = onValueChange,
                onValueChangeFinished = onValueChangeFinished,
                colors = PlayerSliderColors.simpleSliderColors(activeColor),
                thumb = { Spacer(modifier = Modifier.size(0.dp)) },
                track = { sliderState ->
                    PlayerSliderTrack(
                        sliderState = sliderState,
                        colors = PlayerSliderColors.simpleSliderColors(activeColor),
                        trackHeight = 3.dp
                    )
                },
                modifier = modifier
            )
        }
    }
}

@Composable
fun PlayerTimeLabel(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    showRemainingTime: Boolean = false,
    centerContent: @Composable (() -> Unit)? = null,
) {
    Box(
        modifier =
        Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding + 4.dp),
    ) {
        Text(
            text = makeTimeString(sliderPosition ?: position),
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterStart),
        )

        if (centerContent != null) {
            Box(
                modifier = Modifier.align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                centerContent()
            }
        }

        Text(
            text = if (duration != C.TIME_UNSET) {
                if (showRemainingTime) {
                    val remaining = duration - (sliderPosition ?: position)
                    "-${makeTimeString(remaining.coerceAtLeast(0))}"
                } else {
                    makeTimeString(duration)
                }
            } else "",
            style = MaterialTheme.typography.labelMedium,
            color = textBackgroundColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

@Composable
fun PlayerPlaybackControls(
    playerDesignStyle: PlayerDesignStyle,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    playPauseRoundness: androidx.compose.ui.unit.Dp,
    playerConnection: PlayerConnection,
    currentSongLiked: Boolean
) {
    val haptic = LocalHapticFeedback.current
    val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsState()
    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    val cinematicPlayPauseCorner by animateDpAsState(
        targetValue = if (isPlaying) 28.dp else 44.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "cinematicPlayPauseCorner",
    )

    when (playerDesignStyle) {
        PlayerDesignStyle.V2 -> {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth()
            ) {
                val maxW = maxWidth
                val playButtonHeight = maxW / 6f
                val playButtonWidth = playButtonHeight * 1.6f
                val sideButtonHeight = playButtonHeight * 0.8f
                val sideButtonWidth = sideButtonHeight * 1.3f

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {

                    FilledTonalIconButton(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToPrevious() },
                        enabled = canSkipPrevious,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = textButtonColor,
                            contentColor = iconButtonColor
                        ),
                        modifier = Modifier
                            .size(width = sideButtonWidth, height = sideButtonHeight)
                            .clip(RoundedCornerShape(32.dp))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledIconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = textButtonColor,
                            contentColor = iconButtonColor
                        ),
                        modifier = Modifier
                            .size(width = playButtonWidth, height = playButtonHeight)
                            .clip(RoundedCornerShape(32.dp))
                    ) {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(42.dp),
                                color = iconButtonColor,
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    when {
                                        playbackState == STATE_ENDED -> R.drawable.replay
                                        isPlaying -> R.drawable.pause
                                        else -> R.drawable.play
                                    }
                                ),
                                contentDescription = null,
                                modifier = Modifier.size(42.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    FilledTonalIconButton(
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToNext() },
                        enabled = canSkipNext,
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = textButtonColor,
                            contentColor = iconButtonColor
                        ),
                        modifier = Modifier
                            .size(width = sideButtonWidth, height = sideButtonHeight)
                            .clip(RoundedCornerShape(32.dp))
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
            }
        }

        PlayerDesignStyle.V3 -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(
                                alpha = if (shuffleModeEnabled) 1f else 0.4f
                            ),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(textBackgroundColor.copy(alpha = 0.08f))
                            .clickable(enabled = canSkipPrevious) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.seekToPrevious()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_previous),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(alpha = if (canSkipPrevious) 0.9f else 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(70.dp)
                            .clip(RoundedCornerShape(50))
                            .background(textBackgroundColor)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isLoading) {
                            CircularWavyProgressIndicator(
                                modifier = Modifier.size(32.dp),
                                color = icBackgroundColor,
                            )
                        } else {
                            Icon(
                                painter = painterResource(
                                    when {
                                        playbackState == STATE_ENDED -> R.drawable.replay
                                        isPlaying -> R.drawable.pause
                                        else -> R.drawable.play
                                    }
                                ),
                                contentDescription = null,
                                tint = icBackgroundColor,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(textBackgroundColor.copy(alpha = 0.08f))
                            .clickable(enabled = canSkipNext) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                playerConnection.seekToNext()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.skip_next),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(alpha = if (canSkipNext) 0.9f else 0.4f),
                            modifier = Modifier.size(26.dp)
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                                playerConnection.player.toggleRepeatMode()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(
                                when (repeatMode) {
                                    Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                                    Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                    else -> R.drawable.repeat
                                }
                            ),
                            contentDescription = null,
                            tint = textBackgroundColor.copy(
                                alpha = if (repeatMode == Player.REPEAT_MODE_OFF) 0.4f else 1f
                            ),
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }

        PlayerDesignStyle.V4 -> {
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                val baseLarge = 56.dp
                val baseSmall = 46.dp
                val baseGap = 12.dp
                val baseLargeIcon = 28.dp
                val baseSmallIcon = 22.dp
                val baseLargeRadius = 18.dp
                val baseSmallRadius = 16.dp
                val centerSize = 88.dp
                val centerPadding = 40.dp
                val sideTotal = (maxWidth - centerSize - centerPadding) / 2f
                val scale =
                    ((sideTotal - baseGap) / (baseLarge + baseSmall)).coerceAtMost(1f).coerceAtLeast(0.6f)
                val large = baseLarge * scale
                val small = baseSmall * scale
                val gap = baseGap * scale
                val largeIcon = baseLargeIcon * scale
                val smallIcon = baseSmallIcon * scale
                val largeRadius = baseLargeRadius * scale
                val smallRadius = baseSmallRadius * scale

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = {
                                if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                                playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                            },
                            shape = RoundedCornerShape(smallRadius),
                            color = textBackgroundColor.copy(
                                alpha = if (shuffleModeEnabled) 0.2f else 0.08f
                            ),
                            modifier = Modifier.size(small)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.shuffle),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(
                                        alpha = if (shuffleModeEnabled) 1f else 0.6f
                                    ),
                                    modifier = Modifier.size(smallIcon)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(gap))

                        Surface(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToPrevious() },
                            enabled = canSkipPrevious,
                            shape = RoundedCornerShape(largeRadius),
                            color = textBackgroundColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(large)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(
                                        alpha = if (canSkipPrevious) 1f else 0.4f
                                    ),
                                    modifier = Modifier.size(largeIcon)
                                )
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                        shape = RoundedCornerShape(cinematicPlayPauseCorner),
                        color = textButtonColor,
                        modifier = Modifier
                            .padding(horizontal = 20.dp)
                            .size(88.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isLoading) {
                                CircularWavyProgressIndicator(
                                    modifier = Modifier.size(40.dp),
                                    color = icBackgroundColor,
                                )
                            } else {
                                Icon(
                                    painter = painterResource(
                                        when {
                                            playbackState == STATE_ENDED -> R.drawable.replay
                                            isPlaying -> R.drawable.pause
                                            else -> R.drawable.play
                                        }
                                    ),
                                    contentDescription = null,
                                    tint = icBackgroundColor,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToNext() },
                            enabled = canSkipNext,
                            shape = RoundedCornerShape(largeRadius),
                            color = textBackgroundColor.copy(alpha = 0.15f),
                            modifier = Modifier.size(large)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(
                                        alpha = if (canSkipNext) 1f else 0.4f
                                    ),
                                    modifier = Modifier.size(largeIcon)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(gap))

                        Surface(
                            onClick = {
                                if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                                playerConnection.player.toggleRepeatMode()
                            },
                            shape = RoundedCornerShape(smallRadius),
                            color = textBackgroundColor.copy(
                                alpha = if (repeatMode != Player.REPEAT_MODE_OFF) 0.2f else 0.08f
                            ),
                            modifier = Modifier.size(small)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(
                                        when (repeatMode) {
                                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                            else -> R.drawable.repeat
                                        }
                                    ),
                                    contentDescription = null,
                                    tint = textBackgroundColor.copy(
                                        alpha = if (repeatMode == Player.REPEAT_MODE_OFF) 0.6f else 1f
                                    ),
                                    modifier = Modifier.size(smallIcon)
                                )
                            }
                        }
                    }
                }
            }
        }

        PlayerDesignStyle.V1, PlayerDesignStyle.V5 -> {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding),
            ) {
                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = when (repeatMode) {
                            Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL -> R.drawable.repeat
                            Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                            else -> throw IllegalStateException()
                        },
                        color = textBackgroundColor,
                        modifier = Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .align(Alignment.Center)
                            .alpha(if (repeatMode == Player.REPEAT_MODE_OFF) 0.5f else 1f),
                        onClick = {
                            if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            playerConnection.player.toggleRepeatMode()
                        },
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_previous,
                        enabled = canSkipPrevious,
                        color = textBackgroundColor,
                        modifier =
                        Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToPrevious() },
                    )
                }

                Spacer(Modifier.width(8.dp))

                Box(
                    modifier =
                    Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(playPauseRoundness))
                        .background(textButtonColor)
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playbackState == STATE_ENDED) {
                                playerConnection.player.seekTo(0, 0)
                                playerConnection.player.playWhenReady = true
                            } else {
                                playerConnection.player.togglePlayPause()
                            }
                        },
                ) {
                    if (isLoading) {
                        CircularWavyProgressIndicator(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .size(36.dp),
                            color = iconButtonColor,
                        )
                    } else {
                        Image(
                            painter =
                            painterResource(
                                if (playbackState ==
                                    STATE_ENDED
                                ) {
                                    R.drawable.replay
                                } else if (isPlaying) {
                                    R.drawable.pause
                                } else {
                                    R.drawable.play
                                },
                            ),
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(iconButtonColor),
                            modifier =
                            Modifier
                                .align(Alignment.Center)
                                .size(36.dp),
                        )
                    }
                }

                Spacer(Modifier.width(8.dp))

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = R.drawable.skip_next,
                        enabled = canSkipNext,
                        color = textBackgroundColor,
                        modifier =
                        Modifier
                            .size(32.dp)
                            .align(Alignment.Center),
                        onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToNext() },
                    )
                }

                Box(modifier = Modifier.weight(1f)) {
                    ResizableIconButton(
                        icon = if (currentSongLiked) R.drawable.favorite else R.drawable.favorite_border,
                        color = if (currentSongLiked) MaterialTheme.colorScheme.error else textBackgroundColor,
                        modifier =
                        Modifier
                            .size(32.dp)
                            .padding(4.dp)
                            .align(Alignment.Center),
                        onClick = playerConnection::toggleLike,
                    )
                }
            }
        }

        PlayerDesignStyle.V6 -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerHorizontalPadding)
            ) {
                Surface(
                    shape = RoundedCornerShape(28.dp),
                    color = textBackgroundColor.copy(alpha = 0.08f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToPrevious() },
                            enabled = canSkipPrevious,
                            shape = RoundedCornerShape(
                                topStart = 22.dp, bottomStart = 22.dp,
                                topEnd = 8.dp, bottomEnd = 8.dp
                            ),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_previous),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                        alpha = if (canSkipPrevious) 1f else 0.4f
                                    ),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                if (playbackState == STATE_ENDED) {
                                    playerConnection.player.seekTo(0, 0)
                                    playerConnection.player.playWhenReady = true
                                } else {
                                    playerConnection.player.togglePlayPause()
                                }
                            },
                            shape = RoundedCornerShape(28.dp),
                            color = textButtonColor,
                            modifier = Modifier
                                .size(width = 88.dp, height = 80.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLoading) {
                                    CircularWavyProgressIndicator(
                                        modifier = Modifier.size(40.dp),
                                        color = iconButtonColor,
                                    )
                                } else {
                                    Icon(
                                        painter = painterResource(
                                            when {
                                                playbackState == STATE_ENDED -> R.drawable.replay
                                                isPlaying -> R.drawable.pause
                                                else -> R.drawable.play
                                            }
                                        ),
                                        contentDescription = null,
                                        tint = iconButtonColor,
                                        modifier = Modifier.size(44.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.width(6.dp))

                        Surface(
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.LongPress); playerConnection.seekToNext() },
                            enabled = canSkipNext,
                            shape = RoundedCornerShape(
                                topStart = 8.dp, bottomStart = 8.dp,
                                topEnd = 22.dp, bottomEnd = 22.dp
                            ),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            modifier = Modifier
                                .weight(1f)
                                .height(56.dp)
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.skip_next),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                                        alpha = if (canSkipNext) 1f else 0.4f
                                    ),
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        onClick = {
                            if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                        },
                        shape = RoundedCornerShape(50),
                        color = if (shuffleModeEnabled)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else textBackgroundColor.copy(alpha = 0.08f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = null,
                                tint = if (shuffleModeEnabled)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else textBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Surface(
                        onClick = {
                            if (enableHapticFeedback) view.performHapticFeedback(android.view.HapticFeedbackConstants.CONTEXT_CLICK, android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING)
                            playerConnection.player.toggleRepeatMode()
                        },
                        shape = RoundedCornerShape(50),
                        color = if (repeatMode != Player.REPEAT_MODE_OFF)
                            MaterialTheme.colorScheme.tertiaryContainer
                        else textBackgroundColor.copy(alpha = 0.08f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                painter = painterResource(
                                    when (repeatMode) {
                                        Player.REPEAT_MODE_ONE -> R.drawable.repeat_one
                                        else -> R.drawable.repeat
                                    }
                                ),
                                contentDescription = null,
                                tint = if (repeatMode != Player.REPEAT_MODE_OFF)
                                    MaterialTheme.colorScheme.onTertiaryContainer
                                else textBackgroundColor.copy(alpha = 0.5f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        PlayerDesignStyle.V7, PlayerDesignStyle.V8, PlayerDesignStyle.V9 -> Unit
    }
}

/**
 * Wrapper composable that combines all player control components.
 * This replaces the large inline controlsContent lambda in BottomSheetPlayer
 * to reduce JIT compilation overhead.
 */
@Composable
fun PlayerControlsContent(
    mediaMetadata: MediaMetadata,
    playerDesignStyle: PlayerDesignStyle,
    sliderStyle: SliderStyle,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    repeatMode: Int,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    textButtonColor: Color,
    iconButtonColor: Color,
    textBackgroundColor: Color,
    icBackgroundColor: Color,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    context: Context,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    currentFormat: FormatEntity? = null,
) {
    val currentSong by playerConnection.currentSong.collectAsState(initial = null)
    val currentSongLiked = currentSong?.song?.liked == true

    val playPauseRoundness by animateDpAsState(
        targetValue = if (isPlaying) 24.dp else 36.dp,
        animationSpec = tween(durationMillis = 90, easing = LinearEasing),
        label = "playPauseRoundness",
    )

    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerHorizontalPadding),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PlayerTitleSection(
                mediaMetadata = mediaMetadata,
                textBackgroundColor = textBackgroundColor,
                navController = navController,
                state = state,
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        PlayerTopActions(
            mediaMetadata = mediaMetadata,
            playerDesignStyle = playerDesignStyle,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            textBackgroundColor = textBackgroundColor,
            playerConnection = playerConnection,
            navController = navController,
            menuState = menuState,
            state = state,
            bottomSheetPageState = bottomSheetPageState,
            context = context,
            currentSongLiked = currentSongLiked
        )
    }

    Spacer(Modifier.height(12.dp))

    PlayerSlider(
        sliderStyle = sliderStyle,
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        isPlaying = isPlaying,
        textButtonColor = textButtonColor,
        onValueChange = onSliderValueChange,
        onValueChangeFinished = onSliderValueChangeFinished
    )

    Spacer(Modifier.height(4.dp))

    PlayerTimeLabel(
        sliderPosition = sliderPosition,
        position = position,
        duration = duration,
        textBackgroundColor = textBackgroundColor,
        showRemainingTime = playerDesignStyle == PlayerDesignStyle.V7,
        centerContent = if (playerDesignStyle == PlayerDesignStyle.V7 && currentFormat != null) {
            {
                val codec = currentFormat.mimeType.substringAfter("/").uppercase()
                val label = when {
                    codec.contains("FLAC") || codec.contains("ALAC") -> "Lossless"
                    codec.contains("OPUS") -> codec
                    codec.contains("AAC") -> codec
                    codec.contains("MP4A") -> "AAC"
                    codec.contains("VORBIS") -> "Vorbis"
                    else -> codec
                }
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = textBackgroundColor.copy(alpha = 0.12f),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.graphic_eq),
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = textBackgroundColor.copy(alpha = 0.8f),
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = textBackgroundColor.copy(alpha = 0.8f),
                        )
                    }
                }
            }
        } else null,
    )

    Spacer(Modifier.height(12.dp))

    PlayerPlaybackControls(
        playerDesignStyle = playerDesignStyle,
        playbackState = playbackState,
        isPlaying = isPlaying,
        isLoading = isLoading,
        repeatMode = repeatMode,
        canSkipPrevious = canSkipPrevious,
        canSkipNext = canSkipNext,
        textButtonColor = textButtonColor,
        iconButtonColor = iconButtonColor,
        textBackgroundColor = textBackgroundColor,
        icBackgroundColor = icBackgroundColor,
        playPauseRoundness = playPauseRoundness,
        playerConnection = playerConnection,
        currentSongLiked = currentSongLiked
    )
}

@Composable
fun V8PlayerControlsContent(
    mediaMetadata: MediaMetadata,
    queueTitle: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    currentFormat: FormatEntity?,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val onMenuClick = remember(mediaMetadata, navController, state, menuState, bottomSheetPageState) {
        {
            menuState.show {
                PlayerMenu(
                    mediaMetadata = mediaMetadata,
                    navController = navController,
                    playerBottomSheetState = state,
                    onShowDetailsDialog = {
                        bottomSheetPageState.show {
                            ShowMediaInfo(mediaMetadata.id)
                        }
                    },
                    onDismiss = menuState::dismiss
                )
            }
        }
    }
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val onTitleClick = titleActions.onTitleClick
    val onArtistClick = titleActions.onArtistClick
    val onPlayPauseClick = remember(playbackState, playerConnection) {
        {
            if (playbackState == STATE_ENDED) {
                playerConnection.player.seekTo(0, 0)
                playerConnection.player.playWhenReady = true
            } else {
                playerConnection.player.togglePlayPause()
            }
        }
    }
    val onToggleLike = remember(playerConnection) {
        { playerConnection.toggleLike() }
    }
    val onPreviousClick = remember(playerConnection) {
        { playerConnection.seekToPrevious() }
    }
    val onNextClick = remember(playerConnection) {
        { playerConnection.seekToNext() }
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val horizontalPadding = if (landscape) 36.dp else if (maxWidth < 380.dp) 22.dp else 24.dp
        val contentGap = if (landscape) 14.dp else 18.dp
        val progressToTransportGap = if (landscape) 12.dp else 18.dp
        val transportToVolumeGap = if (landscape) 12.dp else 18.dp
        val subtitle = queueTitle ?: mediaMetadata.album?.title.orEmpty()

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.titleMedium,
                    color = secondaryForeground,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .basicMarquee(),
                )

                Spacer(Modifier.height(contentGap))
            }

            V8MetadataActions(
                title = mediaMetadata.title,
                artists = mediaMetadata.artists,
                liked = currentSongLiked,
                foreground = foreground,
                onMenuClick = onMenuClick,
                onToggleLike = onToggleLike,
                onTitleClick = onTitleClick,
                onArtistClick = onArtistClick,
            )

            Spacer(Modifier.height(contentGap))

            V8PlaybackProgress(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                currentFormat = currentFormat,
                foreground = foreground,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(progressToTransportGap))

            V8TransportControls(
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                foreground = foreground,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
            )

            Spacer(Modifier.height(transportToVolumeGap))

            V8VolumeControls(
                volume = volume,
                foreground = foreground,
                secondaryForeground = secondaryForeground,
                onVolumeChange = onVolumeChange,
            )
        }
    }
}

@Composable
fun V8PlayerContent(
    mediaMetadata: MediaMetadata,
    queueTitle: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    menuState: MenuState,
    bottomSheetPageState: BottomSheetPageState,
    currentFormat: FormatEntity?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val foreground = Color.White
    val secondaryForeground = foreground.copy(alpha = 0.72f)
    val artworkUrl = mediaMetadata.thumbnailUrl?.highRes()
    val subtitle = queueTitle ?: mediaMetadata.album?.title.orEmpty()
    val onMenuClick = {
        menuState.show {
            PlayerMenu(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerBottomSheetState = state,
                onShowDetailsDialog = {
                    bottomSheetPageState.show {
                        ShowMediaInfo(mediaMetadata.id)
                    }
                },
                onDismiss = menuState::dismiss
            )
        }
    }

    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val onTitleClick = titleActions.onTitleClick
    val onArtistClick = titleActions.onArtistClick

    if (landscape) {
        V8LandscapeContent(
            mediaMetadata = mediaMetadata,
            subtitle = subtitle,
            artists = mediaMetadata.artists,
            artworkUrl = artworkUrl,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            currentSongLiked = currentSongLiked,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            volume = volume,
            currentFormat = currentFormat,
            foreground = foreground,
            secondaryForeground = secondaryForeground,
            onMenuClick = onMenuClick,
            onToggleLike = playerConnection::toggleLike,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onPreviousClick = playerConnection::seekToPrevious,
            onNextClick = playerConnection::seekToNext,
            onPlayPauseClick = {
                if (playbackState == STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.togglePlayPause()
                }
            },
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            onVolumeChange = onVolumeChange,
            modifier = modifier,
        )
    } else {
        V8PortraitContent(
            mediaMetadata = mediaMetadata,
            subtitle = subtitle,
            artists = mediaMetadata.artists,
            artworkUrl = artworkUrl,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            currentSongLiked = currentSongLiked,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            volume = volume,
            currentFormat = currentFormat,
            foreground = foreground,
            secondaryForeground = secondaryForeground,
            onMenuClick = onMenuClick,
            onToggleLike = playerConnection::toggleLike,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onPreviousClick = playerConnection::seekToPrevious,
            onNextClick = playerConnection::seekToNext,
            onPlayPauseClick = {
                if (playbackState == STATE_ENDED) {
                    playerConnection.player.seekTo(0, 0)
                    playerConnection.player.playWhenReady = true
                } else {
                    playerConnection.player.togglePlayPause()
                }
            },
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            onVolumeChange = onVolumeChange,
            modifier = modifier,
        )
    }
}

@Composable
private fun V8PortraitContent(
    mediaMetadata: MediaMetadata,
    subtitle: String,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    currentFormat: FormatEntity?,
    foreground: Color,
    secondaryForeground: Color,
    onMenuClick: () -> Unit,
    onToggleLike: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val contentPadding = if (maxWidth < 380.dp) 22.dp else 24.dp
        val compactHeight = maxHeight < 760.dp
        val veryCompactHeight = maxHeight < 680.dp
        val headerTop = if (compactHeight) 6.dp else 14.dp
        val headerToArtwork = when {
            veryCompactHeight -> 10.dp
            compactHeight -> 14.dp
            else -> 28.dp
        }
        val artworkToMetadata = when {
            veryCompactHeight -> 12.dp
            compactHeight -> 16.dp
            else -> 28.dp
        }
        val controlsGap = if (compactHeight) 10.dp else 18.dp
        val progressToTransportGap = if (compactHeight) 8.dp else 18.dp
        val transportToVolumeGap = if (compactHeight) 8.dp else 18.dp
        val bottomGap = if (compactHeight) 8.dp else 16.dp
        val reservedControlsHeight =
            headerTop +
                56.dp +
                headerToArtwork +
                artworkToMetadata +
                58.dp +
                controlsGap +
                62.dp +
                progressToTransportGap +
                72.dp +
                transportToVolumeGap +
                30.dp +
                bottomGap
        val maxArtworkSize = (maxWidth - contentPadding * 2)
            .coerceAtMost(if (compactHeight) 360.dp else 420.dp)
        val artworkSize = maxArtworkSize
            .coerceAtMost(maxHeight - reservedControlsHeight)
            .coerceAtLeast(0.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(headerTop))

            V8Header(
                title = stringResource(R.string.now_playing),
                subtitle = subtitle,
                foreground = foreground,
                secondaryForeground = secondaryForeground,
            )

            Spacer(Modifier.height(headerToArtwork))
            Spacer(Modifier.weight(1f))

            V8Artwork(
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                size = artworkSize,
            )

            Spacer(Modifier.height(artworkToMetadata))

            V8MetadataActions(
                title = mediaMetadata.title,
                artists = artists,
                liked = currentSongLiked,
                foreground = foreground,
                onMenuClick = onMenuClick,
                onToggleLike = onToggleLike,
                onTitleClick = onTitleClick,
                onArtistClick = onArtistClick,
            )

            Spacer(Modifier.height(controlsGap))

            V8PlaybackProgress(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                currentFormat = currentFormat,
                foreground = foreground,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(progressToTransportGap))

            V8TransportControls(
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                foreground = foreground,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
            )

            Spacer(Modifier.height(transportToVolumeGap))

            V8VolumeControls(
                volume = volume,
                foreground = foreground,
                secondaryForeground = secondaryForeground,
                onVolumeChange = onVolumeChange,
            )

            Spacer(Modifier.height(bottomGap))
        }
    }
}

@Composable
private fun V8LandscapeContent(
    mediaMetadata: MediaMetadata,
    subtitle: String,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    currentSongLiked: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    volume: Float,
    currentFormat: FormatEntity?,
    foreground: Color,
    secondaryForeground: Color,
    onMenuClick: () -> Unit,
    onToggleLike: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val horizontalPadding = 36.dp
        val contentGap = 36.dp
        val artworkSize = (maxHeight - 48.dp)
            .coerceAtMost((maxWidth - horizontalPadding * 2 - contentGap) * 0.44f)
            .coerceAtLeast(0.dp)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(contentGap),
        ) {
            V8Artwork(
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                size = artworkSize,
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .heightIn(min = 320.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                V8Header(
                    title = stringResource(R.string.now_playing),
                    subtitle = subtitle,
                    foreground = foreground,
                    secondaryForeground = secondaryForeground,
                )

                Spacer(Modifier.height(22.dp))

                V8MetadataActions(
                    title = mediaMetadata.title,
                    artists = artists,
                    liked = currentSongLiked,
                    foreground = foreground,
                    onMenuClick = onMenuClick,
                    onToggleLike = onToggleLike,
                    onTitleClick = onTitleClick,
                    onArtistClick = onArtistClick,
                )

                Spacer(Modifier.height(18.dp))

                V8PlaybackProgress(
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    currentFormat = currentFormat,
                    foreground = foreground,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )

                Spacer(Modifier.height(18.dp))

                V8TransportControls(
                    playbackState = playbackState,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    foreground = foreground,
                    onPreviousClick = onPreviousClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onNextClick = onNextClick,
                )

                Spacer(Modifier.height(18.dp))

                V8VolumeControls(
                    volume = volume,
                    foreground = foreground,
                    secondaryForeground = secondaryForeground,
                    onVolumeChange = onVolumeChange,
                )
            }
        }
    }
}

@Composable
private fun V8Header(
    title: String,
    subtitle: String,
    foreground: Color,
    secondaryForeground: Color,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = foreground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.titleMedium,
            color = secondaryForeground,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
        )
    }
}

@Composable
private fun V8Artwork(
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    size: androidx.compose.ui.unit.Dp,
) {
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkUrl)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        AsyncImage(
            model = artworkRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun V8MetadataActions(
    title: String,
    artists: List<MediaMetadata.Artist>,
    liked: Boolean,
    foreground: Color,
    onMenuClick: () -> Unit,
    onToggleLike: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .basicMarquee()
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() },
                        onClick = onTitleClick,
                    ),
            )
            ClickableArtists(
                artists = artists,
                onArtistClick = onArtistClick,
                style = MaterialTheme.typography.titleMedium,
                color = foreground,
                modifier = Modifier.basicMarquee(),
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V8ActionButton(
                iconRes = R.drawable.more_vert,
                contentDescription = stringResource(R.string.more_options),
                foreground = foreground,
                containerColor = foreground.copy(alpha = 0.16f),
                iconSize = 24.dp,
                onClick = onMenuClick,
            )
            V8ActionButton(
                iconRes = if (liked) R.drawable.favorite else R.drawable.favorite_border,
                contentDescription = stringResource(R.string.action_like),
                foreground = foreground,
                containerColor = foreground.copy(alpha = 0.16f),
                iconSize = 26.dp,
                onClick = onToggleLike,
            )
        }
    }
}

@Composable
private fun V8ActionButton(
    iconRes: Int,
    contentDescription: String,
    foreground: Color,
    containerColor: Color,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = containerColor,
        modifier = Modifier.size(48.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foreground,
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun V8PlaybackProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    currentFormat: FormatEntity?,
    foreground: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L || duration == C.TIME_UNSET) 0f else duration.toFloat()
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(0f, safeDuration.coerceAtLeast(0f))

    Column(modifier = Modifier.fillMaxWidth()) {
        V8FlatSlider(
            value = safeValue,
            valueRange = 0f..safeDuration.coerceAtLeast(0f),
            activeColor = foreground.copy(alpha = 0.88f),
            inactiveColor = foreground.copy(alpha = 0.32f),
            trackHeight = 9.dp,
            onValueChange = { onSliderValueChange(it.toLong()) },
            onValueChangeFinished = onSliderValueChangeFinished,
            enabled = safeDuration > 0f,
            modifier = Modifier.fillMaxWidth(),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: position),
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart),
            )

            if (currentFormat != null) {
                V8QualityChip(
                    currentFormat = currentFormat,
                    foreground = foreground,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            Text(
                text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                style = MaterialTheme.typography.labelMedium,
                color = foreground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterEnd),
            )
        }
    }
}

@Composable
private fun V8QualityChip(
    currentFormat: FormatEntity,
    foreground: Color,
    modifier: Modifier = Modifier,
) {
    val label = remember(currentFormat.mimeType, currentFormat.codecs) {
        currentFormat.codecLabel()
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = foreground.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = foreground.copy(alpha = 0.13f),
        ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.graphic_eq),
                contentDescription = null,
                tint = foreground.copy(alpha = 0.72f),
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = foreground.copy(alpha = 0.72f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun V8TransportControls(
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    foreground: Color,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        V8TransportButton(
            iconRes = R.drawable.skip_previous,
            contentDescription = stringResource(R.string.widget_previous),
            foreground = foreground,
            enabled = canSkipPrevious,
            touchSize = 64.dp,
            iconSize = 44.dp,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPreviousClick()
            },
        )

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayPauseClick()
            },
            shape = CircleShape,
            color = Color.Transparent,
            modifier = Modifier.size(72.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(44.dp),
                        color = foreground,
                    )
                } else {
                    Icon(
                        painter = painterResource(
                            when {
                                playbackState == STATE_ENDED -> R.drawable.replay
                                isPlaying -> R.drawable.pause
                                else -> R.drawable.play
                            }
                        ),
                        contentDescription = if (isPlaying) {
                            stringResource(R.string.widget_pause)
                        } else {
                            stringResource(R.string.play)
                        },
                        tint = foreground,
                        modifier = Modifier.size(52.dp),
                    )
                }
            }
        }

        V8TransportButton(
            iconRes = R.drawable.skip_next,
            contentDescription = stringResource(R.string.next),
            foreground = foreground,
            enabled = canSkipNext,
            touchSize = 64.dp,
            iconSize = 44.dp,
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNextClick()
            },
        )
    }
}

@Composable
private fun V8TransportButton(
    iconRes: Int,
    contentDescription: String,
    foreground: Color,
    enabled: Boolean,
    touchSize: androidx.compose.ui.unit.Dp,
    iconSize: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        color = Color.Transparent,
        modifier = Modifier.size(touchSize),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = foreground.copy(alpha = if (enabled) 1f else 0.4f),
                modifier = Modifier.size(iconSize),
            )
        }
    }
}

@Composable
private fun V8VolumeControls(
    volume: Float,
    foreground: Color,
    secondaryForeground: Color,
    onVolumeChange: (Float) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.volume_off),
            contentDescription = stringResource(R.string.minimum_volume),
            tint = secondaryForeground,
            modifier = Modifier.size(22.dp),
        )
        V8FlatSlider(
            value = volume.coerceIn(0f, 1f),
            valueRange = 0f..1f,
            activeColor = foreground.copy(alpha = 0.86f),
            inactiveColor = foreground.copy(alpha = 0.24f),
            trackHeight = 8.dp,
            onValueChange = { onVolumeChange(it.coerceIn(0f, 1f)) },
            onValueChangeFinished = {},
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 18.dp),
        )
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = stringResource(R.string.maximum_volume),
            tint = secondaryForeground,
            modifier = Modifier.size(24.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun V8FlatSlider(
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    activeColor: Color,
    inactiveColor: Color,
    trackHeight: androidx.compose.ui.unit.Dp,
    onValueChange: (Float) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val safeEnd = valueRange.endInclusive.coerceAtLeast(valueRange.start + 1f)
    val safeRange = valueRange.start..safeEnd
    val colors = SliderDefaults.colors(
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
        enabled = enabled,
        colors = colors,
        thumb = { Spacer(modifier = Modifier.size(0.dp)) },
        track = { sliderState ->
            PlayerSliderTrack(
                sliderState = sliderState,
                colors = colors,
                trackHeight = trackHeight,
            )
        },
        modifier = modifier.height(30.dp),
    )
}

@Composable
fun V9PlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
) {
    val artworkUrl = mediaMetadata.thumbnailUrl?.highRes()
    val titleActions = rememberPlayerTitleActions(mediaMetadata, navController, state)
    val onTitleClick = titleActions.onTitleClick
    val onArtistClick = titleActions.onArtistClick
    val onPlayPauseClick = {
        if (playbackState == STATE_ENDED) {
            playerConnection.player.seekTo(0, 0)
            playerConnection.player.playWhenReady = true
        } else {
            playerConnection.player.togglePlayPause()
        }
    }

    if (landscape) {
        V9LandscapeContent(
            title = mediaMetadata.title,
            artists = mediaMetadata.artists,
            artworkUrl = artworkUrl,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onCollapseClick = onCollapseClick,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onPreviousClick = playerConnection::seekToPrevious,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = playerConnection::seekToNext,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            modifier = modifier,
        )
    } else {
        V9PortraitContent(
            title = mediaMetadata.title,
            artists = mediaMetadata.artists,
            artworkUrl = artworkUrl,
            canvasPrimaryUrl = canvasPrimaryUrl,
            canvasFallbackUrl = canvasFallbackUrl,
            playbackState = playbackState,
            isPlaying = isPlaying,
            isLoading = isLoading,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            sliderPosition = sliderPosition,
            position = position,
            duration = duration,
            textBackgroundColor = textBackgroundColor,
            textButtonColor = textButtonColor,
            iconButtonColor = iconButtonColor,
            onCollapseClick = onCollapseClick,
            onQueueClick = onQueueClick,
            onLyricsClick = onLyricsClick,
            onTitleClick = onTitleClick,
            onArtistClick = onArtistClick,
            onPreviousClick = playerConnection::seekToPrevious,
            onPlayPauseClick = onPlayPauseClick,
            onNextClick = playerConnection::seekToNext,
            onSliderValueChange = onSliderValueChange,
            onSliderValueChangeFinished = onSliderValueChangeFinished,
            modifier = modifier,
        )
    }
}

@Composable
private fun V9PortraitContent(
    title: String,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
    val horizontalPadding = if (maxWidth < 380.dp) 16.dp else 20.dp
    val compactHeight = maxHeight < 760.dp
    val veryCompactHeight = maxHeight < 700.dp

    val artworkMinSize = when {
        veryCompactHeight -> 200.dp
        compactHeight -> 216.dp
        else -> 236.dp
    }
    val artworkHeightLimit = maxHeight * when {
        veryCompactHeight -> 0.32f
        compactHeight -> 0.35f
        else -> 0.40f
    }
    val artworkSize = (maxWidth - horizontalPadding * 2)
        .coerceAtMost(artworkHeightLimit)
        .coerceAtLeast(artworkMinSize)

    val headerGap = when {
        veryCompactHeight -> 14.dp
        compactHeight -> 18.dp
        else -> 26.dp
    }
    val metadataGap = when {
        veryCompactHeight -> 16.dp
        compactHeight -> 20.dp
        else -> 26.dp
    }
    val controlsGap = when {
        veryCompactHeight -> 12.dp
        compactHeight -> 16.dp
        else -> 22.dp
    }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(if (compactHeight) 8.dp else 14.dp))

            V9Header(
                textColor = textBackgroundColor,
                containerColor = textButtonColor.copy(alpha = 0.16f),
                iconColor = textBackgroundColor,
                onCollapseClick = onCollapseClick,
                onLyricsClick = onLyricsClick,
                onQueueClick = onQueueClick,
            )

            Spacer(Modifier.height(headerGap))

            V9Artwork(
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                size = artworkSize,
                placeholderColor = textButtonColor.copy(alpha = 0.12f),
            )

            Spacer(Modifier.height(metadataGap))

            V9Metadata(
                title = title,
                artists = artists,
                textColor = textBackgroundColor,
                onTitleClick = onTitleClick,
                onArtistClick = onArtistClick,
            )

            Spacer(Modifier.height(controlsGap))

            V9PlaybackProgress(
                sliderPosition = sliderPosition,
                position = position,
                duration = duration,
                isPlaying = isPlaying,
                activeColor = textButtonColor,
                inactiveColor = textButtonColor.copy(alpha = 0.24f),
                textColor = textBackgroundColor,
                onSliderValueChange = onSliderValueChange,
                onSliderValueChangeFinished = onSliderValueChangeFinished,
            )

            Spacer(Modifier.height(if (compactHeight) 24.dp else 32.dp))

            V9TransportControls(
                playbackState = playbackState,
                isPlaying = isPlaying,
                isLoading = isLoading,
                canSkipPrevious = canSkipPrevious,
                canSkipNext = canSkipNext,
                containerColor = textButtonColor.copy(alpha = 0.14f),
                primaryContainerColor = textButtonColor,
                iconColor = textBackgroundColor,
                primaryIconColor = iconButtonColor,
                onPreviousClick = onPreviousClick,
                onPlayPauseClick = onPlayPauseClick,
                onNextClick = onNextClick,
            )

            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
private fun V9LandscapeContent(
    title: String,
    artists: List<MediaMetadata.Artist>,
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    textBackgroundColor: Color,
    textButtonColor: Color,
    iconButtonColor: Color,
    onCollapseClick: () -> Unit,
    onQueueClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val artworkSize = (maxHeight * 0.74f)
            .coerceAtMost(maxWidth * 0.4f)
            .coerceAtLeast(236.dp)

        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(26.dp),
        ) {
            V9Artwork(
                artworkUrl = artworkUrl,
                canvasPrimaryUrl = canvasPrimaryUrl,
                canvasFallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                size = artworkSize,
                placeholderColor = textButtonColor.copy(alpha = 0.12f),
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                V9Header(
                    textColor = textBackgroundColor,
                    containerColor = textButtonColor.copy(alpha = 0.16f),
                    iconColor = textBackgroundColor,
                    onCollapseClick = onCollapseClick,
                    onLyricsClick = onLyricsClick,
                    onQueueClick = onQueueClick,
                )

                Spacer(Modifier.height(22.dp))

                V9Metadata(
                    title = title,
                    artists = artists,
                    textColor = textBackgroundColor,
                    onTitleClick = onTitleClick,
                    onArtistClick = onArtistClick,
                )

                Spacer(Modifier.height(20.dp))

                V9PlaybackProgress(
                    sliderPosition = sliderPosition,
                    position = position,
                    duration = duration,
                    isPlaying = isPlaying,
                    activeColor = textButtonColor,
                    inactiveColor = textButtonColor.copy(alpha = 0.24f),
                    textColor = textBackgroundColor,
                    onSliderValueChange = onSliderValueChange,
                    onSliderValueChangeFinished = onSliderValueChangeFinished,
                )

                Spacer(Modifier.height(24.dp))

                V9TransportControls(
                    playbackState = playbackState,
                    isPlaying = isPlaying,
                    isLoading = isLoading,
                    canSkipPrevious = canSkipPrevious,
                    canSkipNext = canSkipNext,
                    containerColor = textButtonColor.copy(alpha = 0.14f),
                    primaryContainerColor = textButtonColor,
                    iconColor = textBackgroundColor,
                    primaryIconColor = iconButtonColor,
                    onPreviousClick = onPreviousClick,
                    onPlayPauseClick = onPlayPauseClick,
                    onNextClick = onNextClick,
                )
            }
        }
    }
}

@Composable
private fun V9Header(
    textColor: Color,
    containerColor: Color,
    iconColor: Color,
    onCollapseClick: () -> Unit,
    onLyricsClick: () -> Unit,
    onQueueClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        V9HeaderButton(
            iconRes = R.drawable.expand_more,
            contentDescription = null,
            containerColor = containerColor,
            iconColor = iconColor,
            shape = CircleShape,
            onClick = onCollapseClick,
        )

        Text(
            text = stringResource(R.string.now_playing),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = textColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .weight(1f)
                .basicMarquee(),
        )

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            V9HeaderButton(
                iconRes = R.drawable.lyrics,
                contentDescription = stringResource(R.string.lyrics),
                containerColor = containerColor,
                iconColor = iconColor,
                shape = RoundedCornerShape(22.dp),
                onClick = onLyricsClick,
            )
            V9HeaderButton(
                iconRes = R.drawable.queue_music,
                contentDescription = stringResource(R.string.queue),
                containerColor = containerColor,
                iconColor = iconColor,
                shape = RoundedCornerShape(22.dp),
                onClick = onQueueClick,
            )
        }
    }
}

@Composable
private fun V9HeaderButton(
    iconRes: Int,
    contentDescription: String?,
    containerColor: Color,
    iconColor: Color,
    shape: androidx.compose.ui.graphics.Shape,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = shape,
        color = containerColor,
        modifier = Modifier.size(56.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconColor,
                modifier = Modifier.size(26.dp),
            )
        }
    }
}

@Composable
private fun V9Artwork(
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    size: Dp,
    placeholderColor: Color,
) {
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkUrl)
    Box(
        modifier = Modifier
            .size(size)
            .clip(RoundedCornerShape(30.dp))
            .background(placeholderColor),
    ) {
        AsyncImage(
            model = artworkRequest,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        if (!canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()) {
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun V9Metadata(
    title: String,
    artists: List<MediaMetadata.Artist>,
    textColor: Color,
    onTitleClick: () -> Unit,
    onArtistClick: (artistId: String) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = textColor,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee()
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = onTitleClick,
                ),
        )
        ClickableArtists(
            artists = artists,
            onArtistClick = onArtistClick,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            color = textColor.copy(alpha = 0.72f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .basicMarquee(),
        )
    }
}

@Composable
private fun V9PlaybackProgress(
    sliderPosition: Long?,
    position: Long,
    duration: Long,
    isPlaying: Boolean,
    activeColor: Color,
    inactiveColor: Color,
    textColor: Color,
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
) {
    val safeDuration = if (duration <= 0L || duration == C.TIME_UNSET) 0f else duration.toFloat()
    val safeRange = 0f..safeDuration.coerceAtLeast(1f)
    val safeValue = (sliderPosition ?: position).toFloat().coerceIn(safeRange)
    val sliderColors = SliderDefaults.colors(
        thumbColor = activeColor,
        activeTrackColor = activeColor,
        activeTickColor = activeColor,
        inactiveTrackColor = inactiveColor,
    )
    val squigglesSpec = remember(isPlaying) {
        SquigglySlider.SquigglesSpec(
            amplitude = if (isPlaying) 3.dp else 0.dp,
            strokeWidth = 7.dp,
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        SquigglySlider(
            value = safeValue,
            valueRange = safeRange,
            onValueChange = { onSliderValueChange(it.toLong()) },
            onValueChangeFinished = onSliderValueChangeFinished,
            enabled = safeDuration > 0f,
            colors = sliderColors,
            squigglesSpec = squigglesSpec,
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = makeTimeString(sliderPosition ?: position),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (duration != C.TIME_UNSET) makeTimeString(duration) else "",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = textColor.copy(alpha = 0.78f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun V9TransportControls(
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    containerColor: Color,
    primaryContainerColor: Color,
    iconColor: Color,
    primaryIconColor: Color,
    onPreviousClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val playPauseCorner by animateDpAsState(
        targetValue = if (isPlaying) 34.dp else 52.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow,
        ),
        label = "v9PlayPauseCorner",
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        V9TransportButton(
            iconRes = R.drawable.skip_previous,
            contentDescription = stringResource(R.string.widget_previous),
            enabled = canSkipPrevious,
            containerColor = containerColor,
            iconColor = iconColor,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPreviousClick()
            },
        )

        Surface(
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onPlayPauseClick()
            },
            shape = RoundedCornerShape(playPauseCorner),
            color = primaryContainerColor,
            modifier = Modifier
                .weight(1f)
                .height(110.dp),
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                if (isLoading) {
                    CircularWavyProgressIndicator(
                        modifier = Modifier.size(46.dp),
                        color = primaryIconColor,
                    )
                } else {
                    AnimatedContent(
                        targetState = when {
                            playbackState == STATE_ENDED -> R.drawable.replay
                            isPlaying -> R.drawable.pause
                            else -> R.drawable.play
                        },
                        transitionSpec = {
                            fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith fadeOut(tween(90))
                        },
                        label = "v9PlayPauseIcon",
                    ) { iconRes ->
                        Icon(
                            painter = painterResource(iconRes),
                            contentDescription = if (isPlaying) {
                                stringResource(R.string.widget_pause)
                            } else {
                                stringResource(R.string.play)
                            },
                            tint = primaryIconColor,
                            modifier = Modifier.size(42.dp),
                        )
                    }
                }
            }
        }

        V9TransportButton(
            iconRes = R.drawable.skip_next,
            contentDescription = stringResource(R.string.next),
            enabled = canSkipNext,
            containerColor = containerColor,
            iconColor = iconColor,
            modifier = Modifier.weight(1f),
            onClick = {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onNextClick()
            },
        )
    }
}

@Composable
private fun V9TransportButton(
    iconRes: Int,
    contentDescription: String,
    enabled: Boolean,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(56.dp),
        color = containerColor,
        modifier = modifier.height(110.dp),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = contentDescription,
                tint = iconColor.copy(alpha = if (enabled) 0.88f else 0.36f),
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

@Composable
fun PlayerBackground(
    playerBackground: PlayerBackgroundStyle,
    mediaMetadata: MediaMetadata?,
    gradientColors: List<Color>,
    disableBlur: Boolean,
    blurRadius: Float,
    playerCustomImageUri: String,
    playerCustomBlur: Float,
    playerCustomContrast: Float,
    playerCustomBrightness: Float
) {
    val effectiveBlurRadius = blurRadius.coerceIn(0f, PlayerBackgroundMaxBlurRadius)
    val shouldApplyBlur = !disableBlur && effectiveBlurRadius > 0f
    Box(modifier = Modifier.fillMaxSize()) {
        when (playerBackground) {
            PlayerBackgroundStyle.BLUR -> {
                AnimatedContent(
                    targetState = mediaMetadata?.thumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = ""
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = thumbnailUrl.highRes(),
                                contentDescription = "Blurred background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().let {
                                    if (shouldApplyBlur) it.blur(radius = effectiveBlurRadius.dp) else it
                                }
                            )
                            val overlayStops = PlayerBackgroundColorUtils.buildBlurOverlayStops(gradientColors)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(colorStops = overlayStops))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.08f))
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.GRADIENT -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = ""
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val gradientColorStops = if (colors.size >= 3) {
                                arrayOf(
                                    0.0f to colors[0].copy(alpha = 0.92f), // Top: primary vibrant color
                                    0.5f to colors[1].copy(alpha = 0.75f), // Middle: darker variant
                                    1.0f to colors[2].copy(alpha = 0.65f)  // Bottom: black-ish
                                )
                            } else {
                                arrayOf(
                                    0.0f to colors[0].copy(alpha = 0.9f), // Top: primary color
                                    0.6f to colors[0].copy(alpha = 0.55f), // Middle: faded variant
                                    1.0f to Color.Black.copy(alpha = 0.7f) // Bottom: black
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(colorStops = gradientColorStops))
                            )
                            // Keep a gentle dark overlay to ensure text contrast on bright artwork
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.18f))
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.COLORING -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = ""
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val baseColor = PlayerBackgroundColorUtils.ensureComfortableColor(colors.first())
                        val gradientStops = PlayerBackgroundColorUtils.buildColoringStops(baseColor)
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(modifier = Modifier.fillMaxSize().background(baseColor))
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(colorStops = gradientStops))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.25f))
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.BLUR_GRADIENT -> {
                AnimatedContent(
                    targetState = mediaMetadata?.thumbnailUrl,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = ""
                ) { thumbnailUrl ->
                    if (thumbnailUrl != null) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            AsyncImage(
                                model = thumbnailUrl.highRes(),
                                contentDescription = "Blurred background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().let {
                                    if (shouldApplyBlur) it.blur(radius = effectiveBlurRadius.dp) else it
                                }
                            )
                            val gradientColorStops =
                                PlayerBackgroundColorUtils.buildBlurGradientStops(gradientColors)
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Brush.verticalGradient(colorStops = gradientColorStops))
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.05f))
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.CUSTOM -> {
                AnimatedContent(
                    targetState = playerCustomImageUri,
                    transitionSpec = {
                        fadeIn(tween(1000)) togetherWith fadeOut(tween(1000))
                    },
                    label = ""
                ) { uri ->
                    if (uri.isNotBlank()) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            val blurPx = playerCustomBlur
                            val contrastVal = playerCustomContrast
                            val brightnessVal = playerCustomBrightness

                            val t = (1f - contrastVal) * 128f + (brightnessVal - 1f) * 255f
                            val matrix = floatArrayOf(
                                contrastVal, 0f, 0f, 0f, t,
                                0f, contrastVal, 0f, 0f, t,
                                0f, 0f, contrastVal, 0f, t,
                                0f, 0f, 0f, 1f, 0f,
                            )

                            val cm = ColorMatrix(matrix)

                            AsyncImage(
                                model = Uri.parse(uri),
                                contentDescription = "Custom background",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().let {
                                    if (disableBlur) it else it.blur(radius = blurPx.dp)
                                },
                                colorFilter = ColorFilter.colorMatrix(cm)
                            )
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.4f))
                            )
                        }
                    }
                }
            }

            PlayerBackgroundStyle.GLOW -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = ""
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val width = size.width
                                    val height = size.height

                                    // Use a dark base, but the gradients will cover most of it
                                    val baseColor = Color(0xFF050505)

                                    // Extract up to 6 colors
                                    val color1 = colors.getOrElse(0) { Color.DarkGray }
                                    val color2 = colors.getOrElse(1) { color1 }
                                    val color3 = colors.getOrElse(2) { color2 }
                                    val color4 = colors.getOrElse(3) { color1 }
                                    val color5 = colors.getOrElse(4) { color2 }
                                    val color6 = colors.getOrElse(5) { color3 }

                                    // Top-Left Large Glow (Primary)
                                    val brush1 = Brush.radialGradient(
                                        colors = listOf(
                                            color1.copy(alpha = 0.8f),
                                            color1.copy(alpha = 0.5f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.2f, height * 0.25f),
                                        radius = width * 1.2f
                                    )

                                    // Bottom-Right Large Glow (Secondary)
                                    val brush2 = Brush.radialGradient(
                                        colors = listOf(
                                            color2.copy(alpha = 0.75f),
                                            color2.copy(alpha = 0.45f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.85f, height * 0.8f),
                                        radius = width * 1.1f
                                    )

                                    // Top-Right Glow (Tertiary)
                                    val brush3 = Brush.radialGradient(
                                        colors = listOf(
                                            color3.copy(alpha = 0.7f),
                                            color3.copy(alpha = 0.4f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.9f, height * 0.15f),
                                        radius = width * 1.0f
                                    )
                                    
                                    // Bottom-Left (Quaternary)
                                    val brush4 = Brush.radialGradient(
                                        colors = listOf(
                                            color4.copy(alpha = 0.65f),
                                            color4.copy(alpha = 0.35f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.1f, height * 0.9f),
                                        radius = width * 1.0f
                                    )
                                    
                                    // Top-Center (Quinary)
                                    val brush5 = Brush.radialGradient(
                                        colors = listOf(
                                            color5.copy(alpha = 0.6f),
                                            color5.copy(alpha = 0.3f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.5f, height * 0.1f),
                                        radius = width * 0.9f
                                    )
                                    
                                    // Bottom-Center (Senary)
                                    val brush6 = Brush.radialGradient(
                                        colors = listOf(
                                            color6.copy(alpha = 0.6f),
                                            color6.copy(alpha = 0.3f),
                                            Color.Transparent
                                        ),
                                        center = Offset(width * 0.5f, height * 0.95f),
                                        radius = width * 0.9f
                                    )

                                    onDrawBehind {
                                        drawRect(color = baseColor)
                                        drawRect(brush = brush1)
                                        drawRect(brush = brush2)
                                        drawRect(brush = brush3)
                                        drawRect(brush = brush4)
                                        drawRect(brush = brush5)
                                        drawRect(brush = brush6)
                                    }
                                }
                        )
                    }
                }
            }

            PlayerBackgroundStyle.GLOW_ANIMATED -> {
                AnimatedContent(
                    targetState = gradientColors,
                    transitionSpec = {
                        fadeIn(tween(1200)) togetherWith fadeOut(tween(1200))
                    },
                    label = "GlowAnimatedContent"
                ) { colors ->
                    if (colors.isNotEmpty()) {
                        val infiniteTransition = rememberInfiniteTransition(label = "GlowAnimation")

                        val progress by infiniteTransition.animateFloat(
                            initialValue = 0f,
                            targetValue = 1f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(20000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "glowProgress"
                        )

                        fun rotatedColorAt(index: Int): Color {
                            val size = colors.size
                            val idx = index.toFloat() + progress * size
                            val a = kotlin.math.floor(idx).toInt() % size
                            val b = (a + 1) % size
                            val frac = idx - kotlin.math.floor(idx)
                            return androidx.compose.ui.graphics.lerp(colors.getOrElse(a) { Color.DarkGray }, colors.getOrElse(b) { Color.DarkGray }, frac)
                        }

                        fun oscillate(min: Float, max: Float, phase: Float, speed: Float = 1f): Float {
                            // speed MUST be an integer to ensure seamless looping when progress wraps from 1f to 0f.
                            val v = kotlin.math.sin(2f * kotlin.math.PI.toFloat() * (progress * speed + phase)).toFloat()
                            return min + (max - min) * ((v + 1f) * 0.5f)
                        }

                        val color1 = rotatedColorAt(0)
                        val color2 = rotatedColorAt(1)
                        val color3 = rotatedColorAt(2)
                        val color4 = rotatedColorAt(3)
                        val color5 = rotatedColorAt(4)
                        val color6 = rotatedColorAt(5)

                        val o1x = oscillate(0.0f, 1.0f, 0.00f, 1.0f)
                        val o1y = oscillate(0.0f, 0.5f, 0.07f, 1.0f)
                        val r1 = oscillate(0.8f, 1.6f, 0.12f, 1.0f)

                        val o2x = oscillate(1.0f, 0.0f, 0.2f, 1.0f)
                        val o2y = oscillate(0.5f, 1.0f, 0.25f, 1.0f)
                        val r2 = oscillate(0.7f, 1.5f, 0.18f, 1.0f)

                        val o3x = oscillate(0.2f, 0.8f, 0.33f, 1.0f)
                        val o3y = oscillate(0.8f, 0.2f, 0.36f, 1.0f)
                        val r3 = oscillate(0.6f, 1.4f, 0.29f, 1.0f)

                        val o4x = oscillate(0.3f, 0.7f, 0.44f, 1.0f)
                        val o4y = oscillate(0.2f, 0.8f, 0.41f, 1.0f)
                        val r4 = oscillate(0.9f, 1.7f, 0.47f, 1.0f)

                        val o5x = oscillate(0.4f, 0.6f, 0.55f, 1.0f)
                        val o5y = oscillate(0.0f, 1.0f, 0.51f, 1.0f)
                        val r5 = oscillate(0.7f, 1.5f, 0.58f, 1.0f)

                        val o6x = oscillate(0.0f, 1.0f, 0.66f, 1.0f)
                        val o6y = oscillate(0.5f, 0.7f, 0.62f, 1.0f)
                        val r6 = oscillate(0.8f, 1.8f, 0.69f, 1.0f)

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawWithCache {
                                    val width = size.width
                                    val height = size.height
                                    val baseColor = Color(0xFF050505)

                                    val brush1 = Brush.radialGradient(
                                        colors = listOf(color1.copy(alpha = 0.85f), color1.copy(alpha = 0.5f), Color.Transparent),
                                        center = Offset(width * o1x, height * o1y),
                                        radius = width * r1
                                    )
                                    val brush2 = Brush.radialGradient(
                                        colors = listOf(color2.copy(alpha = 0.8f), color2.copy(alpha = 0.45f), Color.Transparent),
                                        center = Offset(width * o2x, height * o2y),
                                        radius = width * r2
                                    )
                                    val brush3 = Brush.radialGradient(
                                        colors = listOf(color3.copy(alpha = 0.75f), color3.copy(alpha = 0.4f), Color.Transparent),
                                        center = Offset(width * o3x, height * o3y),
                                        radius = width * r3
                                    )
                                    val brush4 = Brush.radialGradient(
                                        colors = listOf(color4.copy(alpha = 0.7f), color4.copy(alpha = 0.35f), Color.Transparent),
                                        center = Offset(width * o4x, height * o4y),
                                        radius = width * r4
                                    )
                                    val brush5 = Brush.radialGradient(
                                        colors = listOf(color5.copy(alpha = 0.65f), color5.copy(alpha = 0.3f), Color.Transparent),
                                        center = Offset(width * o5x, height * o5y),
                                        radius = width * r5
                                    )
                                    val brush6 = Brush.radialGradient(
                                        colors = listOf(color6.copy(alpha = 0.6f), color6.copy(alpha = 0.25f), Color.Transparent),
                                        center = Offset(width * o6x, height * o6y),
                                        radius = width * r6
                                    )

                                    onDrawBehind {
                                        drawRect(color = baseColor)
                                        drawRect(brush = brush1)
                                        drawRect(brush = brush2)
                                        drawRect(brush = brush3)
                                        drawRect(brush = brush4)
                                        drawRect(brush = brush5)
                                        drawRect(brush = brush6)
                                    }
                                }
                        )
                    }
                }
            }

            else -> {
                // DEFAULT or other modes - no background
            }
        }
    }
}
