/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player.immersive

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImage
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.ListDialog
import moe.rukamori.archivetune.canvas.CanvasVideo
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.ui.player.rememberOfflineArtworkImageRequest
import moe.rukamori.archivetune.utils.makeTimeString

private val ImmersiveContentColor = Color.White
private val ImmersiveSecondaryContentColor = Color.White.copy(alpha = 0.7f)

@Composable
fun ImmersivePlayerScreen(
    state: ImmersivePlayerScreenState,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
    showVolumeBar: Boolean,
    showCodecOnPlayer: Boolean,
    contentBottomPadding: Dp,
    onAction: (ImmersivePlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        ImmersivePlayerScreenState.Loading -> ImmersiveLoading(modifier)
        ImmersivePlayerScreenState.Empty -> Box(modifier = modifier.background(Color.Black))
        is ImmersivePlayerScreenState.Success -> {
            ImmersivePlayerContent(
                model = state.model,
                disableBlur = disableBlur,
                backdropBlurAmount = backdropBlurAmount,
                showVolumeBar = showVolumeBar,
                showCodecOnPlayer = showCodecOnPlayer,
                contentBottomPadding = contentBottomPadding,
                onAction = onAction,
                modifier = modifier,
            )
        }
        is ImmersivePlayerScreenState.Error -> {
            state.previous?.let { model ->
                ImmersivePlayerContent(
                    model = model,
                    disableBlur = disableBlur,
                    backdropBlurAmount = backdropBlurAmount,
                    showVolumeBar = showVolumeBar,
                    showCodecOnPlayer = showCodecOnPlayer,
                    contentBottomPadding = contentBottomPadding,
                    onAction = onAction,
                    modifier = modifier,
                )
            } ?: ImmersiveLoading(modifier)
        }
    }
}

@Composable
private fun ImmersiveLoading(modifier: Modifier) {
    Box(
        modifier = modifier.background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        CircularProgressIndicator(color = ImmersiveContentColor)
    }
}

@Composable
private fun ImmersivePlayerContent(
    model: ImmersivePlayerUiModel,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
    showVolumeBar: Boolean,
    showCodecOnPlayer: Boolean,
    contentBottomPadding: Dp,
    onAction: (ImmersivePlayerAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (model.showArtistDialog) {
        val artists = remember(model.artists) {
            model.artists.filter { !it.id.isNullOrBlank() }.distinctBy { it.id }
        }
        ListDialog(onDismiss = { onAction(ImmersivePlayerAction.DismissArtistDialog) }) {
            items(artists, key = { requireNotNull(it.id) }, contentType = { "artist" }) { artist ->
                ListItem(
                    headlineContent = {
                        Text(text = artist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier.fillMaxWidth().clickable {
                        onAction(ImmersivePlayerAction.OpenArtist(requireNotNull(artist.id)))
                    },
                )
            }
        }
    }
    BoxWithConstraints(modifier = modifier.background(Color.Black)) {
        val landscape = maxWidth > maxHeight
        if (landscape) {
            ImmersiveLandscape(
                model = model,
                disableBlur = disableBlur,
                backdropBlurAmount = backdropBlurAmount,
                showVolumeBar = showVolumeBar,
                showCodecOnPlayer = showCodecOnPlayer,
                contentBottomPadding = contentBottomPadding,
                onAction = onAction,
            )
        } else {
            ImmersivePortrait(
                model = model,
                disableBlur = disableBlur,
                backdropBlurAmount = backdropBlurAmount,
                showVolumeBar = showVolumeBar,
                showCodecOnPlayer = showCodecOnPlayer,
                contentBottomPadding = contentBottomPadding,
                onAction = onAction,
            )
        }
    }
}

@Composable
private fun ImmersivePortrait(
    model: ImmersivePlayerUiModel,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
    showVolumeBar: Boolean,
    showCodecOnPlayer: Boolean,
    contentBottomPadding: Dp,
    onAction: (ImmersivePlayerAction) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val contentStartFraction =
            when {
                maxHeight < 700.dp -> 0.40f
                maxHeight < 900.dp -> 0.44f
                else -> 0.46f
            }
        val contentStart = maxHeight * contentStartFraction + 16.dp
        val stageHeight = maxHeight * 0.64f
        key(model.mediaId, model.canvas) {
            ImmersiveBackdrop(
                mediaId = model.mediaId,
                canvasFrame = model.canvasFrame,
                onAction = onAction,
                artworkUrl = model.artworkUrl,
                canvas = model.canvas,
                isPlaying = model.isPlaying,
                artWidth = maxWidth,
                artHeight = stageHeight,
                disableBlur = disableBlur,
                backdropBlurAmount = backdropBlurAmount,
            )
        }
        SourceLabel(
            sourceTitle = model.sourceTitle,
            modifier =
                Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 26.dp, start = 24.dp, end = 24.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = contentStart - 18.dp,
                        start = 28.dp,
                        end = 28.dp,
                        bottom = contentBottomPadding + 10.dp,
                    ),
        ) {
            FormatSeam(if (showCodecOnPlayer) model.formatDetails else "")
            Spacer(modifier = Modifier.height(52.dp))
            MetadataRow(
                title = model.title,
                artists = model.artists,
                isLiked = model.isLiked,
                albumId = model.albumId,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.height(20.dp))
            ProgressSection(
                model = model,
                showCodecOnPlayer = showCodecOnPlayer,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.weight(1f))
            TransportControls(
                isPlaying = model.isPlaying,
                isBuffering = model.isBuffering,
                canSkipPrevious = model.canSkipPrevious,
                canSkipNext = model.canSkipNext,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.weight(1f))
            if (showVolumeBar) {
                VolumeControls(volume = model.volume, onAction = onAction)
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun ImmersiveLandscape(
    model: ImmersivePlayerUiModel,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
    showVolumeBar: Boolean,
    showCodecOnPlayer: Boolean,
    contentBottomPadding: Dp,
    onAction: (ImmersivePlayerAction) -> Unit,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val artWidth = maxWidth * 0.48f
        key(model.mediaId, model.canvas) {
            ImmersiveBackdrop(
                mediaId = model.mediaId,
                canvasFrame = model.canvasFrame,
                onAction = onAction,
                artworkUrl = model.artworkUrl,
                canvas = model.canvas,
                isPlaying = model.isPlaying,
                artWidth = artWidth,
                artHeight = maxHeight,
                disableBlur = disableBlur,
                backdropBlurAmount = backdropBlurAmount,
            )
        }
        SourceLabel(
            sourceTitle = model.sourceTitle,
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(start = 24.dp)
                    .statusBarsPadding()
                    .padding(top = 18.dp),
        )
        Column(
            modifier =
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f)
                    .align(Alignment.CenterEnd)
                    .windowInsetsPadding(WindowInsets.systemBars.only(WindowInsetsSides.End + WindowInsetsSides.Top))
                    .padding(
                        start = 30.dp,
                        top = 18.dp,
                        end = 30.dp,
                        bottom = contentBottomPadding + 18.dp,
                    ),
        ) {
            FormatSeam(if (showCodecOnPlayer) model.formatDetails else "")
            Spacer(modifier = Modifier.height(22.dp))
            MetadataRow(
                title = model.title,
                artists = model.artists,
                isLiked = model.isLiked,
                albumId = model.albumId,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.weight(1f))
            ProgressSection(
                model = model,
                showCodecOnPlayer = showCodecOnPlayer,
                onAction = onAction,
            )
            Spacer(modifier = Modifier.height(18.dp))
            TransportControls(
                isPlaying = model.isPlaying,
                isBuffering = model.isBuffering,
                canSkipPrevious = model.canSkipPrevious,
                canSkipNext = model.canSkipNext,
                onAction = onAction,
            )
            if (showVolumeBar) {
                Spacer(modifier = Modifier.height(20.dp))
                VolumeControls(volume = model.volume, onAction = onAction)
            }
        }
    }
}

@Composable
private fun ImmersiveBackdrop(
    mediaId: String,
    canvasFrame: ImageBitmap?,
    onAction: (ImmersivePlayerAction) -> Unit,
    artworkUrl: String?,
    canvas: CanvasVideo?,
    isPlaying: Boolean,
    artWidth: Dp,
    artHeight: Dp,
    disableBlur: Boolean,
    backdropBlurAmount: Int,
) {
    val hazeState = rememberHazeState()
    val artworkRequest = rememberOfflineArtworkImageRequest(artworkUrl)
    val artworkPlaceholder = painterResource(R.drawable.immersive_artwork_placeholder)
    val canvasArtworkRequest = rememberOfflineArtworkImageRequest(canvas?.static)
    val blurRadius = remember(backdropBlurAmount) { backdropBlurAmount.coerceIn(0, 100).dp }
    val blurStyle =
        remember(disableBlur, blurRadius) {
            HazeBlurStyle {
                blurEnabled(!disableBlur && blurRadius > 0.dp)
                blurRadius(blurRadius)
                noiseFactor(0f)
                backgroundColor(Color.Transparent)
            }
        }
    val lowerScrim =
        remember {
            Brush.verticalGradient(
                0f to Color.Transparent,
                0.38f to Color.Black.copy(alpha = 0.03f),
                0.68f to Color.Black.copy(alpha = 0.14f),
                1f to Color.Black.copy(alpha = 0.40f),
            )
        }
    val stageScrim =
        remember {
            Brush.verticalGradient(
                0.58f to Color.Transparent,
                1f to Color.Black.copy(alpha = 0.12f),
            )
        }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val stageFadeMask =
            remember {
                Brush.verticalGradient(
                    0f to Color.Black,
                    0.44f to Color.Black,
                    0.58f to Color.Black.copy(alpha = 0.96f),
                    0.70f to Color.Black.copy(alpha = 0.70f),
                    0.82f to Color.Black.copy(alpha = 0.28f),
                    1f to Color.Transparent,
                )
            }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
        ) {
            if (canvasFrame != null && canvas != null) {
                Image(
                    bitmap = canvasFrame,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                AsyncImage(
                    model = artworkRequest,
                    placeholder = artworkPlaceholder,
                    error = artworkPlaceholder,
                    fallback = artworkPlaceholder,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                AsyncImage(
                    model = artworkRequest,
                    placeholder = artworkPlaceholder,
                    error = artworkPlaceholder,
                    fallback = artworkPlaceholder,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(width = artWidth, height = artHeight)
                            .align(Alignment.TopStart),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeBlur(
                        input = HazeInput.Sources(hazeState),
                        style = blurStyle,
                        performanceMode = HazePerformanceMode.Adaptive,
                        expandLayerBounds = true,
                    ).background(lowerScrim),
        )

        Box(
            modifier =
                Modifier
                    .size(width = artWidth, height = artHeight)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.Offscreen
                    }.drawWithContent {
                        drawContent()
                        drawRect(
                            brush = stageFadeMask,
                            blendMode = BlendMode.DstIn,
                        )
                    },
        ) {
            AsyncImage(
                model = canvasArtworkRequest ?: artworkRequest,
                placeholder = artworkPlaceholder,
                error = artworkPlaceholder,
                fallback = artworkPlaceholder,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            CanvasArtworkPlayer(
                source = canvas?.source,
                primaryUrl = canvas?.animatedVertical ?: canvas?.animated,
                fallbackUrl = canvas?.videoUrlVertical ?: canvas?.videoUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                onFrameCaptured = remember(mediaId, canvas, onAction) {
                    { bitmap ->
                        if (canvas != null) {
                            onAction(ImmersivePlayerAction.CanvasFrameCaptured(mediaId, canvas, bitmap?.asImageBitmap()))
                        }
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(stageScrim),
            )
        }
    }
}

@Composable
private fun SourceLabel(
    sourceTitle: String,
    modifier: Modifier = Modifier,
) {
    val resolvedSourceTitle =
        if (sourceTitle.isBlank()) {
            stringResource(R.string.now_playing)
        } else {
            sourceTitle
        }
    Text(
        text = stringResource(R.string.playing_from, resolvedSourceTitle),
        color = ImmersiveContentColor,
        fontSize = 16.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier,
    )
}

@Composable
private fun FormatSeam(formatDetails: String) {
    if (formatDetails.isBlank()) return
    Text(
        text = formatDetails,
        color = ImmersiveSecondaryContentColor,
        style = MaterialTheme.typography.labelMedium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun MetadataRow(
    title: String,
    artists: List<ImmersivePlayerArtist>,
    isLiked: Boolean,
    albumId: String?,
    onAction: (ImmersivePlayerAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = ImmersiveContentColor,
                fontSize = 27.sp,
                lineHeight = 31.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .basicMarquee()
                        .clickable(enabled = albumId != null) {
                            onAction(ImmersivePlayerAction.OpenAlbum)
                        },
            )
            val artistText = remember(artists) { artists.joinToString(separator = " • ") { it.name } }
            Text(
                text = artistText,
                color = ImmersiveSecondaryContentColor,
                fontSize = 19.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .basicMarquee()
                        .clickable(enabled = artists.any { !it.id.isNullOrBlank() }) {
                            onAction(ImmersivePlayerAction.OpenArtists)
                        },
            )
        }
        IconButton(onClick = { onAction(ImmersivePlayerAction.ToggleLike) }) {
            Icon(
                painter = painterResource(if (isLiked) R.drawable.favorite else R.drawable.favorite_border),
                contentDescription = stringResource(if (isLiked) R.string.action_remove_like else R.string.action_like),
                tint = ImmersiveContentColor,
                modifier = Modifier.size(28.dp),
            )
        }
        IconButton(onClick = { onAction(ImmersivePlayerAction.OpenMenu) }) {
            Icon(
                painter = painterResource(R.drawable.more_horiz),
                contentDescription = stringResource(R.string.more_label),
                tint = ImmersiveContentColor,
                modifier = Modifier.size(28.dp),
            )
        }
    }
}

@Composable
private fun ProgressSection(
    model: ImmersivePlayerUiModel,
    showCodecOnPlayer: Boolean,
    onAction: (ImmersivePlayerAction) -> Unit,
) {
    val displayPosition = model.seekPositionMs ?: model.positionMs
    val valueRange = remember(model.durationMs) { 0L..model.durationMs.coerceAtLeast(1L) }
    ThinProgressSlider(
        value = displayPosition,
        valueRange = valueRange,
        accessibilityLabel = stringResource(R.string.music_player),
        onValueChange = { onAction(ImmersivePlayerAction.PreviewSeek(it)) },
        onValueChangeFinished = { onAction(ImmersivePlayerAction.CommitSeek) },
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = makeTimeString(displayPosition),
            color = ImmersiveSecondaryContentColor,
            style = MaterialTheme.typography.bodyMedium,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showCodecOnPlayer && model.formatDetails.isNotBlank()) {
            Icon(
                imageVector = model.outputDevice.type.imageVector,
                contentDescription = null,
                tint = ImmersiveContentColor,
                modifier = Modifier.size(17.dp),
            )
            Text(
                text = model.formatDetails.substringBefore(" · "),
                color = ImmersiveContentColor,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 5.dp),
            )
            Spacer(modifier = Modifier.weight(1f))
        }
        Text(
            text = stringResource(R.string.remaining_time, makeTimeString((model.durationMs - displayPosition).coerceAtLeast(0L))),
            color = ImmersiveSecondaryContentColor,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun TransportControls(
    isPlaying: Boolean,
    isBuffering: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    onAction: (ImmersivePlayerAction) -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        IconButton(
            onClick = { onAction(ImmersivePlayerAction.SkipPrevious) },
            enabled = canSkipPrevious,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_previous),
                contentDescription = stringResource(R.string.widget_previous),
                tint = ImmersiveContentColor.copy(alpha = if (canSkipPrevious) 1f else 0.35f),
                modifier = Modifier.size(56.dp),
            )
        }
        IconButton(
            onClick = { onAction(ImmersivePlayerAction.TogglePlayPause) },
            modifier = Modifier.size(88.dp),
        ) {
            if (isBuffering) {
                CircularProgressIndicator(
                    color = ImmersiveContentColor,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(52.dp),
                )
            } else {
                Icon(
                    painter = painterResource(if (isPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = stringResource(if (isPlaying) R.string.widget_pause else R.string.play),
                    tint = ImmersiveContentColor,
                    modifier = Modifier.size(68.dp),
                )
            }
        }
        IconButton(
            onClick = { onAction(ImmersivePlayerAction.SkipNext) },
            enabled = canSkipNext,
            modifier = Modifier.size(72.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.skip_next),
                contentDescription = stringResource(R.string.next),
                tint = ImmersiveContentColor.copy(alpha = if (canSkipNext) 1f else 0.35f),
                modifier = Modifier.size(56.dp),
            )
        }
    }
}

@Composable
private fun VolumeControls(
    volume: Float,
    onAction: (ImmersivePlayerAction) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painter = painterResource(R.drawable.volume_off),
            contentDescription = null,
            tint = ImmersiveSecondaryContentColor,
            modifier = Modifier.size(20.dp),
        )
        ThinProgressSlider(
            value = (volume.coerceIn(0f, 1f) * 1000).toLong(),
            valueRange = remember { 0L..1000L },
            accessibilityLabel = stringResource(R.string.volume),
            onValueChange = { onAction(ImmersivePlayerAction.ChangeVolume(it / 1000f)) },
            onValueChangeFinished = {},
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
        )
        Icon(
            painter = painterResource(R.drawable.volume_up),
            contentDescription = stringResource(R.string.volume),
            tint = ImmersiveSecondaryContentColor,
            modifier = Modifier.size(23.dp),
        )
    }
}

@Composable
private fun ThinProgressSlider(
    value: Long,
    valueRange: LongRange,
    accessibilityLabel: String,
    onValueChange: (Long) -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val span = (valueRange.last - valueRange.first).coerceAtLeast(1L)
    val fraction = ((value - valueRange.first).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    val currentOnValueChange by rememberUpdatedState(onValueChange)
    val currentOnValueChangeFinished by rememberUpdatedState(onValueChangeFinished)
    val interactionModifier =
        remember(valueRange) {
            Modifier
                .pointerInput(valueRange) {
                    detectTapGestures(
                        onTap = { offset ->
                            val selected = (valueRange.first + span * (offset.x / size.width).coerceIn(0f, 1f)).toLong()
                            currentOnValueChange(selected)
                            currentOnValueChangeFinished()
                        },
                    )
                }.pointerInput(valueRange) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, _ ->
                            val selected = (valueRange.first + span * (change.position.x / size.width).coerceIn(0f, 1f)).toLong()
                            currentOnValueChange(selected)
                        },
                        onDragEnd = { currentOnValueChangeFinished() },
                        onDragCancel = { currentOnValueChangeFinished() },
                    )
                }
        }
    Canvas(
        modifier =
            modifier
                .fillMaxWidth()
                .height(24.dp)
                .semantics {
                    contentDescription = accessibilityLabel
                    progressBarRangeInfo = ProgressBarRangeInfo(fraction, 0f..1f)
                    setProgress { targetValue ->
                        val selected = valueRange.first + (span * targetValue.coerceIn(0f, 1f)).toLong()
                        currentOnValueChange(selected)
                        currentOnValueChangeFinished()
                        true
                    }
                }
                .then(interactionModifier),
    ) {
        val centerY = size.height / 2f
        drawLine(
            color = Color.White.copy(alpha = 0.28f),
            start = Offset(0f, centerY),
            end = Offset(size.width, centerY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ImmersiveContentColor,
            start = Offset(0f, centerY),
            end = Offset(size.width * fraction, centerY),
            strokeWidth = 6.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
