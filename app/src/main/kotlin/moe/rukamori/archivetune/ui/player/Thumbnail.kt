/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.graphics.Bitmap
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemInfo
import androidx.compose.foundation.lazy.grid.LazyGridLayoutInfo
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEach
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.models.CanvasArtwork
import moe.rukamori.archivetune.constants.ArchiveTuneCanvasKey
import moe.rukamori.archivetune.constants.BackdropBlurAmountKey
import moe.rukamori.archivetune.constants.BackdropEnabledKey
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.HidePlayerThumbnailKey
import moe.rukamori.archivetune.constants.MaxCanvasCacheSizeKey
import moe.rukamori.archivetune.constants.PlayerBackgroundStyle
import moe.rukamori.archivetune.constants.PlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.PlayerDesignStyle
import moe.rukamori.archivetune.constants.PlayerDesignStyleKey
import moe.rukamori.archivetune.constants.PlayerHorizontalPadding
import moe.rukamori.archivetune.constants.SeekExtraSeconds
import moe.rukamori.archivetune.constants.SwipeThumbnailKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import java.util.Locale
import kotlin.math.abs

private data class ThumbnailPage(
    val slotKey: String,
    val windowIndex: Int,
    val mediaItem: MediaItem,
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumbnail(
    sliderPositionProvider: () -> Long?,
    modifier: Modifier = Modifier,
    isPlayerExpanded: Boolean = true, // Add parameter to control swipe based on player state
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current

    // States
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val error by playerConnection.error.collectAsState()
    val queueTitle by playerConnection.queueTitle.collectAsState()

    val swipeThumbnail by rememberPreference(SwipeThumbnailKey, true)

    val view = LocalView.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)

    val hidePlayerThumbnail by rememberPreference(HidePlayerThumbnailKey, false)
    val archiveTuneCanvasEnabled by rememberPreference(ArchiveTuneCanvasKey, false)
    val lowDataModeActive = rememberLowDataModeActive()
    val playerDesignStyle by rememberEnumPreference(
        key = PlayerDesignStyleKey,
        defaultValue = PlayerDesignStyle.V4,
    )
    val (maxCanvasCacheSize, _) =
        rememberPreference(
            key = MaxCanvasCacheSizeKey,
            defaultValue = 256,
        )
    val (thumbnailCornerRadius, _) =
        rememberPreference(
            key = ThumbnailCornerRadiusKey,
            defaultValue = 16f,
        )
    val cropThumbnailToSquare by rememberPreference(CropThumbnailToSquareKey, false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (backdropEnabled) = rememberPreference(BackdropEnabledKey, defaultValue = true)
    val (backdropBlurAmount) = rememberPreference(BackdropBlurAmountKey, defaultValue = 60)
    val canSkipPrevious by playerConnection.canSkipPrevious.collectAsState()
    val canSkipNext by playerConnection.canSkipNext.collectAsState()

    // Player background style for consistent theming
    val playerBackground by rememberEnumPreference(
        key = PlayerBackgroundStyleKey,
        defaultValue = PlayerBackgroundStyle.DEFAULT,
    )

    val textBackgroundColor =
        when (playerBackground) {
            PlayerBackgroundStyle.DEFAULT -> MaterialTheme.colorScheme.onBackground
            PlayerBackgroundStyle.BLUR -> Color.White
            PlayerBackgroundStyle.GRADIENT -> Color.White
            PlayerBackgroundStyle.COLORING -> Color.White
            PlayerBackgroundStyle.BLUR_GRADIENT -> Color.White
            PlayerBackgroundStyle.GLOW -> Color.White
            PlayerBackgroundStyle.GLOW_ANIMATED -> Color.White
            PlayerBackgroundStyle.CUSTOM -> Color.White
        }

    LaunchedEffect(maxCanvasCacheSize) {
        CanvasArtworkPlaybackCache.setMaxSize(maxCanvasCacheSize)
    }

    // Grid state
    val thumbnailLazyGridState = rememberLazyGridState()

    // Create a playlist using correct shuffle-aware logic
    val timeline = playerConnection.player.currentTimeline
    val currentIndex = playerConnection.player.currentMediaItemIndex
    val shuffleModeEnabled = playerConnection.player.shuffleModeEnabled
    val previousWindowIndex =
        if (swipeThumbnail && !timeline.isEmpty) {
            timeline.getPreviousWindowIndex(
                currentIndex,
                Player.REPEAT_MODE_OFF,
                shuffleModeEnabled,
            )
        } else {
            C.INDEX_UNSET
        }
    val previousMediaMetadata =
        if (previousWindowIndex != C.INDEX_UNSET) {
            try {
                playerConnection.player.getMediaItemAt(previousWindowIndex)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

    val nextWindowIndex =
        if (swipeThumbnail && !timeline.isEmpty) {
            timeline.getNextWindowIndex(
                currentIndex,
                Player.REPEAT_MODE_OFF,
                shuffleModeEnabled,
            )
        } else {
            C.INDEX_UNSET
        }
    val nextMediaMetadata =
        if (nextWindowIndex != C.INDEX_UNSET) {
            try {
                playerConnection.player.getMediaItemAt(nextWindowIndex)
            } catch (e: Exception) {
                null
            }
        } else {
            null
        }

    val currentMediaItem =
        remember(mediaMetadata) {
            // Fallback to player's current item if mediaMetadata is null,
            // but prefer mediaMetadata for immediate updates during crossfade.
            val metadata = mediaMetadata
            if (metadata != null) {
                // Use extension to convert metadata to a proper MediaItem with all fields (uri, artwork, tag)
                metadata.toMediaItem()
            } else {
                try {
                    playerConnection.player.currentMediaItem
                } catch (e: Exception) {
                    null
                }
            }
        }

    val thumbnailPages =
        buildList {
            if (previousMediaMetadata != null) {
                add(ThumbnailPage(slotKey = "previous", windowIndex = previousWindowIndex, mediaItem = previousMediaMetadata))
            }
            if (currentMediaItem != null) {
                add(ThumbnailPage(slotKey = "current", windowIndex = currentIndex, mediaItem = currentMediaItem))
            }
            if (nextMediaMetadata != null) {
                add(ThumbnailPage(slotKey = "next", windowIndex = nextWindowIndex, mediaItem = nextMediaMetadata))
            }
        }
    val currentMediaIndex = thumbnailPages.indexOfFirst { it.slotKey == "current" }

    // OuterTune Snap behavior
    val horizontalLazyGridItemWidthFactor = 1f
    val thumbnailSnapLayoutInfoProvider =
        remember(thumbnailLazyGridState) {
            SnapLayoutInfoProvider(
                lazyGridState = thumbnailLazyGridState,
                positionInLayout = { layoutSize, itemSize ->
                    (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                },
                velocityThreshold = 500f,
            )
        }

    // Current item tracking
    val currentItem by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemIndex } }
    val itemScrollOffset by remember { derivedStateOf { thumbnailLazyGridState.firstVisibleItemScrollOffset } }

    // Handle swipe to change song
    LaunchedEffect(itemScrollOffset) {
        if (!thumbnailLazyGridState.isScrollInProgress || !swipeThumbnail || itemScrollOffset != 0 ||
            currentMediaIndex < 0
        ) {
            return@LaunchedEffect
        }

        if (currentItem > currentMediaIndex && canSkipNext) {
            playerConnection.player.seekToNext()
        } else if (currentItem < currentMediaIndex && canSkipPrevious) {
            playerConnection.player.seekToPreviousMediaItem()
        }
    }

    // Update position when song changes
    LaunchedEffect(mediaMetadata, currentMediaItem?.mediaId, canSkipPrevious, canSkipNext) {
        val index = maxOf(0, currentMediaIndex)
        if (index >= 0 && index < thumbnailPages.size) {
            try {
                thumbnailLazyGridState.animateScrollToItem(index)
            } catch (e: Exception) {
                thumbnailLazyGridState.scrollToItem(index)
            }
        }
    }

    LaunchedEffect(playerConnection.player.currentMediaItemIndex, currentMediaItem?.mediaId) {
        val index = currentMediaIndex
        if (index >= 0 && index != currentItem) {
            thumbnailLazyGridState.scrollToItem(index)
        }
    }

    // Seek on double tap
    var showSeekEffect by remember { mutableStateOf(false) }
    var seekDirection by remember { mutableStateOf("") }
    val layoutDirection = LocalLayoutDirection.current

    Box(modifier = modifier) {
        // Error view
        AnimatedVisibility(
            visible = error != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .padding(32.dp)
                    .align(Alignment.Center),
        ) {
            error?.let { playbackError ->
                PlaybackError(
                    error = playbackError,
                    retry = {
                        playerConnection.player.prepare()
                        playerConnection.player.play()
                    },
                )
            }
        }

        // Main thumbnail view
        AnimatedVisibility(
            visible = error == null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier =
                Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
        ) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Now Playing header
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(horizontal = 32.dp, vertical = 16.dp),
                ) {
                    Text(
                        text = stringResource(R.string.now_playing),
                        style = MaterialTheme.typography.titleMedium,
                        color = textBackgroundColor,
                    )
                    // Show album title or queue title
                    val playingFrom = queueTitle ?: mediaMetadata?.album?.title
                    if (!playingFrom.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = playingFrom,
                            style = MaterialTheme.typography.titleMedium,
                            color = textBackgroundColor.copy(alpha = 0.8f),
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                }

                // Thumbnail content
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor
                    val containerMaxWidth = maxWidth

                    LazyHorizontalGrid(
                        state = thumbnailLazyGridState,
                        rows = GridCells.Fixed(1),
                        flingBehavior = rememberSnapFlingBehavior(thumbnailSnapLayoutInfoProvider),
                        userScrollEnabled = swipeThumbnail && isPlayerExpanded, // Only allow swipe when player is expanded
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(
                            items = thumbnailPages,
                            key = { page ->
                                "${page.slotKey}:${page.windowIndex}:${page.mediaItem.mediaId.ifEmpty { "unknown" }}"
                            },
                            contentType = { "thumbnailPage" },
                        ) { page ->
                            val item = page.mediaItem
                            val incrementalSeekSkipEnabled by rememberPreference(SeekExtraSeconds, defaultValue = false)
                            var skipMultiplier by remember { mutableStateOf(1) }
                            var lastTapTime by remember { mutableLongStateOf(0L) }
                            val itemMetadata = remember(item) { item.metadata }
                            val storefront =
                                remember {
                                    val country = Locale.getDefault().country
                                    if (country.length == 2) country.lowercase(Locale.ROOT) else "us"
                                }
                            val shouldUseCanvas =
                                archiveTuneCanvasEnabled &&
                                    playerDesignStyle != PlayerDesignStyle.V7 &&
                                    playerDesignStyle != PlayerDesignStyle.V8 &&
                                    item.mediaId.isNotBlank() &&
                                    item.mediaId == currentMediaItem?.mediaId
                            val shouldFetchCanvas = shouldUseCanvas && !lowDataModeActive
                            var canvasArtwork by remember(item.mediaId) { mutableStateOf<CanvasArtwork?>(null) }
                            var canvasFetchInFlight by remember(item.mediaId) { mutableStateOf(false) }

                            LaunchedEffect(shouldUseCanvas) {
                                if (!shouldUseCanvas) {
                                    canvasArtwork = null
                                    canvasFetchInFlight = false
                                }
                            }

                            LaunchedEffect(shouldUseCanvas, shouldFetchCanvas, item.mediaId) {
                                if (!shouldUseCanvas) return@LaunchedEffect

                                val songTitleRaw =
                                    itemMetadata
                                        ?.title
                                        ?.takeIf { it.isNotBlank() }
                                        ?: item.mediaMetadata.title?.toString()
                                        ?: ""

                                val artistNameRaw =
                                    itemMetadata
                                        ?.artists
                                        ?.firstOrNull()
                                        ?.name
                                        ?.takeIf { it.isNotBlank() }
                                        ?: item.mediaMetadata.artist?.toString()
                                        ?: item.mediaMetadata.subtitle?.toString()
                                        ?: ""

                                if (canvasFetchInFlight) return@LaunchedEffect
                                canvasFetchInFlight = true

                                try {
                                    canvasArtwork =
                                        resolveCanvasArtworkForPlayback(
                                            mediaId = item.mediaId,
                                            songTitleRaw = songTitleRaw,
                                            artistNameRaw = artistNameRaw,
                                            albumId = itemMetadata?.album?.id,
                                            albumTitleRaw = itemMetadata?.album?.title,
                                            storefront = storefront,
                                            requireVertical = false,
                                            allowNetwork = shouldFetchCanvas,
                                        )
                                } finally {
                                    canvasFetchInFlight = false
                                }
                            }

                            Box(
                                modifier =
                                    Modifier
                                        .width(horizontalLazyGridItemWidth)
                                        .fillMaxSize()
                                        .padding(horizontal = PlayerHorizontalPadding)
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = { offset ->
                                                    if (enableHapticFeedback) {
                                                        view.performHapticFeedback(
                                                            android.view.HapticFeedbackConstants.CONTEXT_CLICK,
                                                            android.view.HapticFeedbackConstants.FLAG_IGNORE_GLOBAL_SETTING,
                                                        )
                                                    }
                                                    val currentPosition = playerConnection.player.currentPosition
                                                    val duration = playerConnection.player.duration

                                                    val now = System.currentTimeMillis()
                                                    if (incrementalSeekSkipEnabled && now - lastTapTime < 1000) {
                                                        skipMultiplier++
                                                    } else {
                                                        skipMultiplier = 1
                                                    }
                                                    lastTapTime = now

                                                    val skipAmount = 5000 * skipMultiplier

                                                    if ((layoutDirection == LayoutDirection.Ltr && offset.x < size.width / 2) ||
                                                        (layoutDirection == LayoutDirection.Rtl && offset.x > size.width / 2)
                                                    ) {
                                                        playerConnection.player.seekTo(
                                                            (currentPosition - skipAmount).coerceAtLeast(0),
                                                        )
                                                        seekDirection =
                                                            context.getString(R.string.seek_backward_dynamic, skipAmount / 1000)
                                                    } else {
                                                        playerConnection.player.seekTo(
                                                            (currentPosition + skipAmount).coerceAtMost(duration),
                                                        )
                                                        seekDirection = context.getString(R.string.seek_forward_dynamic, skipAmount / 1000)
                                                    }
                                                    // If a user double-tap skip lands on a new media item, force a centralized Discord sync
                                                    playerConnection.service.forceDiscordSync("thumbnail_double_tap_skip")

                                                    showSeekEffect = true
                                                },
                                            )
                                        },
                                contentAlignment = Alignment.Center,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(containerMaxWidth - (PlayerHorizontalPadding * 2))
                                            .clip(RoundedCornerShape(thumbnailCornerRadius.dp)),
                                ) {
                                    if (hidePlayerThumbnail) {
                                        // Show app logo when thumbnail is hidden
                                        Box(
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.about_splash),
                                                contentDescription = stringResource(R.string.hide_player_thumbnail),
                                                tint = textBackgroundColor.copy(alpha = 0.7f),
                                                modifier = Modifier.size(120.dp),
                                            )
                                        }
                                    } else {
                                        val primaryCanvasUrl = canvasArtwork?.animated
                                        val fallbackCanvasUrl = canvasArtwork?.videoUrl

                                        val shouldCropArtwork =
                                            cropThumbnailToSquare &&
                                                playerDesignStyle != PlayerDesignStyle.V7 &&
                                                playerDesignStyle != PlayerDesignStyle.V8

                                        val baseArtworkUrl =
                                            item.metadata?.thumbnailUrl?.highRes()
                                                ?: item.mediaMetadata.artworkUri?.toString()

                                        val thumbnailSwapState = rememberThumbnailSwapState(
                                            videoId = item.metadata?.id,
                                            ytmUrl = baseArtworkUrl,
                                            lowDataMode = lowDataModeActive,
                                            isMusicVideo = item.metadata?.isMusicVideo ?: false,
                                        )

                                        val displayUrl = thumbnailSwapState.displayUrl

                                        val thumbnailBgRequest = rememberOfflineArtworkImageRequest(displayUrl)
                                        val thumbnailArtworkRequest = rememberOfflineArtworkImageRequest(displayUrl)
                                        val thumbnailBgBlurEnabled = backdropEnabled && backdropBlurAmount > 0

                                        if (thumbnailBgBlurEnabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            val blurRadiusPx = (backdropBlurAmount * 60 / 100f).coerceAtMost(60f)
                                            AsyncImage(
                                                model = thumbnailBgRequest,
                                                contentDescription = null,
                                                contentScale = ContentScale.FillBounds,
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .let { if (shouldCropArtwork) it.aspectRatio(1f) else it }
                                                        .graphicsLayer(
                                                            renderEffect = BlurEffect(radiusX = blurRadiusPx, radiusY = blurRadiusPx),
                                                            alpha = 0.6f,
                                                        ),
                                            )
                                        } else if (thumbnailBgBlurEnabled) {
                                            ThumbnailBgBlurApi30(
                                                imageUrl = displayUrl,
                                                blurAmount = backdropBlurAmount,
                                                shouldCropArtwork = shouldCropArtwork,
                                            )
                                        } else {
                                            AsyncImage(
                                                model = thumbnailBgRequest,
                                                contentDescription = null,
                                                contentScale = ContentScale.FillBounds,
                                                modifier =
                                                    Modifier
                                                        .fillMaxSize()
                                                        .let { if (shouldCropArtwork) it.aspectRatio(1f) else it }
                                                        .graphicsLayer(alpha = 0.6f),
                                            )
                                        }

                                        AsyncImage(
                                            model = thumbnailArtworkRequest,
                                            contentDescription = null,
                                            contentScale = if (shouldCropArtwork) ContentScale.Crop else ContentScale.Fit,
                                            modifier =
                                                Modifier
                                                    .fillMaxSize()
                                                    .let { if (shouldCropArtwork) it.aspectRatio(1f) else it },
                                        )

                                        if (shouldUseCanvas &&
                                            (!primaryCanvasUrl.isNullOrBlank() || !fallbackCanvasUrl.isNullOrBlank())
                                        ) {
                                            CanvasArtworkPlayer(
                                                primaryUrl = primaryCanvasUrl,
                                                fallbackUrl = fallbackCanvasUrl,
                                                isPlaying = isPlaying,
                                                modifier = Modifier.fillMaxSize(),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Seek effect
        LaunchedEffect(showSeekEffect) {
            if (showSeekEffect) {
                delay(1000)
                showSeekEffect = false
            }
        }

        AnimatedVisibility(
            visible = showSeekEffect,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center),
        ) {
            Text(
                text = seekDirection,
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier =
                    Modifier
                        .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
                        .padding(8.dp),
            )
        }
    }
}

@Composable
private fun ThumbnailBgBlurApi30(
    imageUrl: String?,
    blurAmount: Int,
    shouldCropArtwork: Boolean,
) {
    val context = LocalContext.current
    val imageLoader = context.imageLoader

    val blurredBitmap by produceState<Bitmap?>(null, imageUrl, blurAmount) {
        if (imageUrl == null) return@produceState
        value =
            withContext(Dispatchers.IO) {
                try {
                    val request =
                        ImageRequest
                            .Builder(context)
                            .data(imageUrl)
                            .memoryCacheKey(imageUrl)
                            .diskCacheKey(imageUrl)
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .networkCachePolicy(CachePolicy.ENABLED)
                            .allowHardware(false)
                            .size(500)
                            .build()
                    val result = imageLoader.execute(request)
                    when (result) {
                        is SuccessResult -> {
                            val bitmap = result.image.toBitmap().copy(Bitmap.Config.ARGB_8888, true)
                            val radius = (blurAmount * 25 / 100f).coerceIn(1f, 25f)
                            ImageBlurUtils.blur(bitmap, radius)
                        }

                        else -> {
                            null
                        }
                    }
                } catch (_: Exception) {
                    null
                }
            }
    }

    val modifier =
        Modifier
            .fillMaxSize()
            .let { if (shouldCropArtwork) it.aspectRatio(1f) else it }
            .graphicsLayer(alpha = 0.6f)

    val loadedBitmap = blurredBitmap
    if (loadedBitmap != null) {
        Image(
            painter = BitmapPainter(loadedBitmap.asImageBitmap()),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
    } else {
        AsyncImage(
            model = rememberOfflineArtworkImageRequest(imageUrl),
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = modifier,
        )
    }
}

/*
 * Copyright (C) OuterTune Project
 * Custom SnapLayoutInfoProvider idea belongs to OuterTune
 */

// SnapLayoutInfoProvider
@ExperimentalFoundationApi
fun SnapLayoutInfoProvider(
    lazyGridState: LazyGridState,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float = { layoutSize, itemSize ->
        (layoutSize / 2f - itemSize / 2f)
    },
    velocityThreshold: Float = 1000f,
): SnapLayoutInfoProvider =
    object : SnapLayoutInfoProvider {
        private val layoutInfo: LazyGridLayoutInfo
            get() = lazyGridState.layoutInfo

        override fun calculateApproachOffset(
            velocity: Float,
            decayOffset: Float,
        ): Float = 0f

        override fun calculateSnapOffset(velocity: Float): Float {
            val bounds = calculateSnappingOffsetBounds()

            // Only snap when velocity exceeds threshold
            if (abs(velocity) < velocityThreshold) {
                if (abs(bounds.start) < abs(bounds.endInclusive)) {
                    return bounds.start
                }

                return bounds.endInclusive
            }

            return when {
                velocity < 0 -> bounds.start
                velocity > 0 -> bounds.endInclusive
                else -> 0f
            }
        }

        fun calculateSnappingOffsetBounds(): ClosedFloatingPointRange<Float> {
            var lowerBoundOffset = Float.NEGATIVE_INFINITY
            var upperBoundOffset = Float.POSITIVE_INFINITY

            layoutInfo.visibleItemsInfo.fastForEach { item ->
                val offset = calculateDistanceToDesiredSnapPosition(layoutInfo, item, positionInLayout)

                // Find item that is closest to the center
                if (offset <= 0 && offset > lowerBoundOffset) {
                    lowerBoundOffset = offset
                }

                // Find item that is closest to center, but after it
                if (offset >= 0 && offset < upperBoundOffset) {
                    upperBoundOffset = offset
                }
            }

            return lowerBoundOffset.rangeTo(upperBoundOffset)
        }
    }

fun calculateDistanceToDesiredSnapPosition(
    layoutInfo: LazyGridLayoutInfo,
    item: LazyGridItemInfo,
    positionInLayout: (layoutSize: Float, itemSize: Float) -> Float,
): Float {
    val containerSize =
        layoutInfo.singleAxisViewportSize - layoutInfo.beforeContentPadding - layoutInfo.afterContentPadding

    val desiredDistance = positionInLayout(containerSize.toFloat(), item.size.width.toFloat())
    val itemCurrentPosition = item.offset.x.toFloat()

    return itemCurrentPosition - desiredDistance
}

private val LazyGridLayoutInfo.singleAxisViewportSize: Int
    get() = if (orientation == Orientation.Vertical) viewportSize.height else viewportSize.width
