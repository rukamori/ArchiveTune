/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Player design ported from Flamingo (yos.music.player) ui/pages/NowPlaying.kt — GPLv3,
 * https://github.com/shouryadixitisverycool/Flamingo
 *
 * Layout: album page (artwork 0.595 of height + title row + action buttons),
 * in-page state machine Album / Lyric / PlayingList with a shared-element
 * artwork morph into the compact PlayingBar header (non-bouncy spring,
 * 600ms cross-fades, canvas rendered inside the shared bounds like the
 * upstream AppleMusicPlayer), queue overlay fade at 114dp, auto-hiding
 * controls (2500ms on the lyrics page), 19.5/18.5sp title typography, and
 * the floating-light background.
 */

package moe.rukamori.archivetune.ui.player.flamingo

import android.Manifest
import android.graphics.Bitmap
import android.os.SystemClock
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope.OverlayClip
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.TweenSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.InspectorInfo
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.toIntSize
import androidx.compose.ui.zIndex
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.ui.player.LocalLyricsScrollListener
import java.time.LocalDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.draw.drawWithContent
import moe.rukamori.archivetune.LocalStableSystemBarsTopPadding
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AiRomanizeLyricsKey
import moe.rukamori.archivetune.constants.ArtistSeparatorsKey
import moe.rukamori.archivetune.constants.AutoAiRomanizeLyricsKey
import moe.rukamori.archivetune.constants.AutoTranslateExcludedLanguagesKey
import moe.rukamori.archivetune.constants.AutoTranslateLyricsKey
import moe.rukamori.archivetune.constants.FlamingoBackgroundEffectKey
import moe.rukamori.archivetune.constants.FlamingoShowVolumeBarKey
import moe.rukamori.archivetune.constants.FlamingoHideSlideBarKey
import moe.rukamori.archivetune.constants.TranslatorTargetLangKey
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.LyricsEntity
import moe.rukamori.archivetune.db.entities.codecLabel
import moe.rukamori.archivetune.db.entities.isLossless
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.lyrics.LyricsUtils
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.ui.component.BottomSheetPageState
import moe.rukamori.archivetune.ui.component.BottomSheetState
import moe.rukamori.archivetune.ui.component.LyricsEnhanced
import moe.rukamori.archivetune.ui.component.LocalLiquidGlassTuning
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.PlatformBackdrop
import moe.rukamori.archivetune.ui.component.layerBackdrop
import moe.rukamori.archivetune.ui.component.rememberBackdrop
import moe.rukamori.archivetune.ui.component.rememberLiquidGlassEnabled
import moe.rukamori.archivetune.ui.menu.AnchoredLyricsOverflowMenu
import moe.rukamori.archivetune.ui.menu.PlayerMenu
import moe.rukamori.archivetune.ui.menu.rememberCastPlayerMenuAction
import moe.rukamori.archivetune.ui.utils.ShowMediaInfo
import moe.rukamori.archivetune.ui.utils.highRes
import moe.rukamori.archivetune.utils.ImageBlurUtils
import moe.rukamori.archivetune.utils.rememberLowDataModeActive
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LyricsMenuViewModel
import moe.rukamori.archivetune.ui.player.CanvasArtworkPlayer
import moe.rukamori.archivetune.canvas.procedural.ProceduralArtworkFallback
import moe.rukamori.archivetune.constants.ProceduralArtworkEnabledKey
import moe.rukamori.archivetune.ui.player.CanvasLoopSync
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.platform.LocalConfiguration
import moe.rukamori.archivetune.ui.player.LocalVideoArtworkState
import moe.rukamori.archivetune.ui.player.InlineVideoPlayer
import moe.rukamori.archivetune.ui.player.LocalVideoPlaybackFailed
import moe.rukamori.archivetune.ui.player.rememberThumbnailSwapState
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.draw.clip
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.colorControls
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.media3.ui.AspectRatioFrameLayout
import coil3.compose.AsyncImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import moe.rukamori.archivetune.utils.isLocalMediaId

private const val AnimDurationMillis = 300
private const val ShareAlbumKey = "flamingoAlbum"
private val QueueRowHeight = 64.dp
private val QueueDraggingItemShape = RoundedCornerShape(0.dp)

private const val FlamingoSharpStageFadeStart = 0.62f
private const val FlamingoCanvasBackdropUpscale = 6f
private const val FlamingoCanvasBackdropOverscan = 1.10f
private val FlamingoCanvasBackdropBlurRadius = 288.dp
private const val FlamingoCanvasBackdropMaxVideoEdgePx = 480
private const val FlamingoCanvasSnapshotIntervalMs = 50L
private const val FlamingoCanvasBakeRetryCooldownMs = 2_000L
private const val FlamingoCanvasBackdropBakeMaxRadiusPx = 48f

private fun Modifier.canvasSnapshotSource(
    graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    minIntervalMillis: Long,
): Modifier = this then CanvasSnapshotSourceElement(graphicsLayer, minIntervalMillis)

private class CanvasSnapshotSourceElement(
    val graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    val minIntervalMillis: Long,
) : ModifierNodeElement<CanvasSnapshotSourceNode>() {
    override fun create() = CanvasSnapshotSourceNode(graphicsLayer, minIntervalMillis)

    override fun update(node: CanvasSnapshotSourceNode) {
        if (node.graphicsLayer !== graphicsLayer || node.minIntervalMillis != minIntervalMillis) {
            node.graphicsLayer = graphicsLayer
            node.minIntervalMillis = minIntervalMillis
            node.lastRecordUptimeMillis = 0L
        }
        node.invalidateDraw()
    }

    override fun equals(other: Any?): Boolean =
        other is CanvasSnapshotSourceElement &&
            other.graphicsLayer === graphicsLayer &&
            other.minIntervalMillis == minIntervalMillis

    override fun hashCode(): Int =
        graphicsLayer.hashCode() * 31 + minIntervalMillis.hashCode()

    override fun InspectorInfo.inspectableProperties() {
        name = "canvasSnapshotSource"
        properties["minIntervalMillis"] = minIntervalMillis
    }
}

private class CanvasSnapshotSourceNode(
    var graphicsLayer: androidx.compose.ui.graphics.layer.GraphicsLayer,
    var minIntervalMillis: Long,
) : DrawModifierNode, Modifier.Node() {
    var lastRecordUptimeMillis = 0L

    var isRecording = false

    override fun ContentDrawScope.draw() {
        val now = SystemClock.uptimeMillis()
        if (!isRecording && now - lastRecordUptimeMillis >= minIntervalMillis) {
            lastRecordUptimeMillis = now
            isRecording = true
            try {
                runCatching {
                    graphicsLayer.record(size.toIntSize()) {
                        this@draw.drawContent()
                    }
                }
            } finally {
                isRecording = false
            }
        }
    }
}

private const val FlamingoCanvasPageFadeMs = 650

private val FlamingoCanvasScrimBrush =
    Brush.verticalGradient(
        0f to Color.Black.copy(alpha = 0.35f),
        0.5f to Color.Black.copy(alpha = 0.50f),
        1f to Color.Black.copy(alpha = 0.75f),
    )

private val FlamingoLandscapeRightScrim =
    Brush.horizontalGradient(
        0.55f to Color.Transparent,
        1f to Color.Black.copy(alpha = 0.45f),
    )

private val FlamingoSharpStageFadeBrush =
    Brush.verticalGradient(
        FlamingoSharpStageFadeStart to Color.Black,
        1f to Color.Transparent,
    )

private val FlamingoBandTopFeatherBrush =
    Brush.verticalGradient(
        0f to Color.Transparent,
        0.16f to Color.Black,
        1f to Color.Black,
    )

private const val FlamingoLyricsContentDeferMs = 600L

private const val FlamingoLyricsContentDeferCanvasMs = 300L

private const val FlamingoLyricsExitHoldMs = 240L
private const val FlamingoPageMorphHoldMs = 420L
private const val FlamingoStageSurfaceRetireMs = 420L
private const val FlamingoStageCanvasExitFadeMs = 180

private class FlamingoAdaptiveCornerShape(
    private val smallRadius: Dp,
    private val smallSize: Dp,
    private val largeRadius: Dp,
    private val largeSize: Dp,
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: androidx.compose.ui.unit.Density,
    ): Outline {
        val elementSize = minOf(size.width, size.height)
        val smallSizePx = with(density) { smallSize.toPx() }
        val largeSizePx = with(density) { largeSize.toPx() }
        val t =
            if (largeSizePx > smallSizePx) {
                ((elementSize - smallSizePx) / (largeSizePx - smallSizePx)).coerceIn(0f, 1f)
            } else {
                1f
            }
        val smallRadiusPx = with(density) { smallRadius.toPx() }
        val largeRadiusPx = with(density) { largeRadius.toPx() }
        val radius = smallRadiusPx + (largeRadiusPx - smallRadiusPx) * t
        return Outline.Rounded(
            RoundRect(
                left = 0f,
                top = 0f,
                right = size.width,
                bottom = size.height,
                cornerRadius = CornerRadius(radius, radius),
            ),
        )
    }
}

private val FlamingoAlbumOverlayShape =
    FlamingoAdaptiveCornerShape(
        smallRadius = 5.dp,
        smallSize = 69.dp,
        largeRadius = 8.dp,
        largeSize = 400.dp,
    )

private data class QueueReorderTarget(
    val nextInQueue: Boolean,
    val index: Int,
)

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val secs = seconds % 60
    return "$minutes:${if (secs < 10) "0$secs" else "$secs"}"
}

private fun MediaMetadata.artistNames(): String =
    artists.joinToString { it.name }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun FlamingoPlayerContent(
    mediaMetadata: MediaMetadata,
    playbackState: Int,
    isPlaying: Boolean,
    isLoading: Boolean,
    canSkipPrevious: Boolean,
    canSkipNext: Boolean,
    sliderPosition: Long?,
    positionProvider: () -> Long,
    duration: Long,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    currentSongLiked: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    currentFormat: FormatEntity?,
    contentBottomPadding: Dp,
    onQueueClick: () -> Unit = {},
    onLyricsClick: () -> Unit = {},
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    lyricsSyncOffset: Int = 0,
    onLyricsSyncOffsetChange: (Int) -> Unit = {},
    onLyricsVisibilityChange: (Boolean) -> Unit = {},
    externalBackRequests: androidx.compose.runtime.MutableIntState? = null,
    modifier: Modifier = Modifier,
    landscape: Boolean = false,
    orientationRefreshEpoch: Int = 0,
) {
    Surface(
        modifier = modifier.fillMaxSize(),
        contentColor = Color.White,
        color = Color.Transparent,
    ) {
        val context = LocalContext.current
        val scope = rememberCoroutineScope()
        val topInset = LocalStableSystemBarsTopPadding.current

        val player = playerConnection.player

        val (showVolumeBar) = rememberPreference(FlamingoShowVolumeBarKey, defaultValue = true)
        val (backgroundEffect) = rememberPreference(FlamingoBackgroundEffectKey, defaultValue = false)

        val shuffleModeEnabled by playerConnection.shuffleModeEnabled.collectAsStateWithLifecycle()
        val repeatMode by playerConnection.repeatMode.collectAsStateWithLifecycle()
        val queueWindows by playerConnection.queueWindows.collectAsStateWithLifecycle()
        val currentWindowIndex by playerConnection.currentWindowIndex.collectAsStateWithLifecycle()

        val currentLyrics by playerConnection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)

        val isPlayingStatusLambda = rememberUpdatedState(isPlaying)

        val videoShowing =
            LocalVideoArtworkState.current != null &&
                mediaMetadata.isMusicVideo &&
                !mediaMetadata.id.isLocalMediaId() &&
                !LocalVideoPlaybackFailed.current
        val isPreS = Build.VERSION.SDK_INT < Build.VERSION_CODES.S
        val canvasActive =
            !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()
        val canvasVisualActive = canvasActive && !videoShowing && !isPreS

        val canvasLoopSync = remember { CanvasLoopSync() }

        val canvasBackdropReveal by animateFloatAsState(
            targetValue = if (canvasVisualActive && !landscape) 1f else 0f,
            animationSpec = tween(durationMillis = 650, easing = FastOutSlowInEasing),
            label = "flamingo-canvas-backdrop-reveal",
        )

        val canvasDensity = LocalDensity.current
        var titleTopInRootY by remember { mutableStateOf<Float?>(null) }
        var backgroundTopY by remember { mutableStateOf(0f) }
        val sharpStageHeight: Dp? =
            titleTopInRootY?.let { top ->
                with(canvasDensity) { (top - backgroundTopY).coerceAtLeast(0f).toDp() }
            }

        var controlsOverlayHeightPx by remember { mutableStateOf(0f) }

        val lastClickTime = rememberSaveable(key = "FlamingoNowPlaying_lastClickTime") {
            mutableLongStateOf(0L)
        }

        val showControl = rememberSaveable(key = "FlamingoNowPlaying_showControl") {
            mutableStateOf(!landscape)
        }

        var nowPage by rememberSaveable(key = "FlamingoNowPlaying_nowPage") {
            mutableStateOf(if (landscape) FlamingoPage.Lyric else FlamingoPage.Album)
        }

        LaunchedEffect(landscape) {
            showControl.value = !landscape
        }

        val nowPageLambda = rememberUpdatedState(nowPage)
        val showControlLambda = rememberUpdatedState(showControl.value)

        val firstPageObservation = remember { mutableStateOf(true) }
        var pageMorphRunning by remember { mutableStateOf(false) }
        LaunchedEffect(nowPage) {
            if (firstPageObservation.value) {
                firstPageObservation.value = false
                return@LaunchedEffect
            }
            pageMorphRunning = true
            delay(FlamingoPageMorphHoldMs)
            pageMorphRunning = false
        }

        LaunchedEffect(mediaMetadata.id, landscape) {
            if (landscape) {
                if (nowPage != FlamingoPage.Lyric) nowPage = FlamingoPage.Lyric
            } else {
                snapshotFlow { pageMorphRunning }.first { !it }
                if (nowPage != FlamingoPage.Album) nowPage = FlamingoPage.Album
            }
        }

        val overlayArtworkReveal by animateFloatAsState(
            targetValue = if (pageMorphRunning || nowPage != FlamingoPage.Album) 1f else 0f,
            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
            label = "flamingo-overlay-artwork-reveal",
        )

        val pageTakesBackdrop =
            (nowPage == FlamingoPage.Lyric || nowPage == FlamingoPage.PlayingList) &&
                !landscape
        var canvasSurfacesPlaying by remember { mutableStateOf(true) }
        LaunchedEffect(pageTakesBackdrop) {
            if (pageTakesBackdrop) {
                canvasSurfacesPlaying = true
                delay(FlamingoCanvasPageFadeMs.toLong())
                canvasSurfacesPlaying = false
            } else {
                canvasSurfacesPlaying = true
            }
        }
        val canvasSurfacesAlpha = remember { Animatable(1f) }
        LaunchedEffect(pageTakesBackdrop) {
            canvasSurfacesAlpha.animateTo(
                targetValue = if (pageTakesBackdrop) 0f else 1f,
                animationSpec = tween(
                    durationMillis = if (pageTakesBackdrop) {
                        FlamingoStageSurfaceRetireMs.toInt()
                    } else {
                        FlamingoCanvasPageFadeMs
                    },
                    easing = FastOutSlowInEasing,
                ),
            )
        }
        var canvasStageSurfaceActive by remember { mutableStateOf(true) }
        LaunchedEffect(nowPage) {
            if (nowPage != FlamingoPage.Album) {
                delay(FlamingoStageSurfaceRetireMs)
                canvasStageSurfaceActive = false
            } else {
                canvasStageSurfaceActive = true
            }
        }
        val stageCanvasPageAlpha by animateFloatAsState(
            targetValue = if (nowPage == FlamingoPage.Album) 1f else 0f,
            animationSpec = tween(
                durationMillis = FlamingoStageCanvasExitFadeMs,
                easing = FastOutSlowInEasing,
            ),
            label = "flamingo-stage-canvas-page-alpha",
        )
        LaunchedEffect(state.isExpandedOrExpanding) {
            if (!state.isExpandedOrExpanding && nowPage != FlamingoPage.Album) {
                nowPage = if (landscape) FlamingoPage.Lyric else FlamingoPage.Album
            }
        }
        val externalBackEpoch = externalBackRequests?.intValue ?: 0
        LaunchedEffect(externalBackEpoch) {
            if (externalBackEpoch > 0 && nowPage != FlamingoPage.Album) {
                nowPage = FlamingoPage.Album
            }
        }

        val (proceduralArtworkEnabled) = rememberPreference(ProceduralArtworkEnabledKey, defaultValue = false)
        val proceduralStageActive =
            !canvasVisualActive && !videoShowing && !landscape && proceduralArtworkEnabled
        val stageVisualActive = (canvasVisualActive || proceduralStageActive) && !landscape

        BackHandler(enabled = nowPage != FlamingoPage.Album) {
            nowPage = FlamingoPage.Album
        }

        LaunchedEffect(nowPage, state.isExpandedOrExpanding) {
            onLyricsVisibilityChange(nowPage != FlamingoPage.Album && state.isExpandedOrExpanding)
        }

        var lyricsContentReady by remember { mutableStateOf(false) }
        LaunchedEffect(nowPage) {
            if (nowPage == FlamingoPage.Lyric) {
                lyricsContentReady = false

                delay(
                    if (stageVisualActive) {
                        FlamingoLyricsContentDeferCanvasMs
                    } else {
                        FlamingoLyricsContentDeferMs
                    },
                )
                lyricsContentReady = true
            } else {
                if (stageVisualActive) {
                    lyricsContentReady = false
                } else {
                    delay(FlamingoLyricsExitHoldMs)
                    lyricsContentReady = false
                }
            }
        }

        val alphaAnim = remember { Animatable(0f) }
        LaunchedEffect(nowPage) {

            alphaAnim.snapTo(if (nowPage == FlamingoPage.Lyric) 1f else 0f)
        }

        val baseArtworkUrl = mediaMetadata.thumbnailUrl?.highRes()
        val thumbnailSwapState = rememberThumbnailSwapState(
            videoId = mediaMetadata.id,
            ytmUrl = baseArtworkUrl,
            lowDataMode = rememberLowDataModeActive(),
            isMusicVideo = mediaMetadata.isMusicVideo,
        )
        val artworkUrl = thumbnailSwapState.displayUrl

        val (autoTranslateLyrics, onAutoTranslateLyricsChange) =
            rememberPreference(AutoTranslateLyricsKey, defaultValue = false)
        val (translatorTargetLang) = rememberPreference(TranslatorTargetLangKey, defaultValue = "")
        val (autoTranslateExcludedLanguages) =
            rememberPreference(AutoTranslateExcludedLanguagesKey, defaultValue = emptySet())
        val (aiRomanizeLyricsPref, onAiRomanizeLyricsChange) =
            rememberPreference(AiRomanizeLyricsKey, defaultValue = false)
        val (autoAiRomanizeLyrics, onAutoAiRomanizeLyricsChange) =
            rememberPreference(AutoAiRomanizeLyricsKey, defaultValue = false)
        val romanizationOn = aiRomanizeLyricsPref && autoAiRomanizeLyrics

        val lyricsMenuViewModel: LyricsMenuViewModel = hiltViewModel()
        val translationDismissedMediaIds by lyricsMenuViewModel.translationDismissedMediaIds
            .collectAsStateWithLifecycle()

        LaunchedEffect(
            mediaMetadata.id,
            currentLyrics?.lyrics,
            currentLyrics?.source,
            autoTranslateLyrics,
            translatorTargetLang,
            autoTranslateExcludedLanguages,
            translationDismissedMediaIds,
        ) {
            if (!autoTranslateLyrics) return@LaunchedEffect
            val snapshot = currentLyrics ?: return@LaunchedEffect
            val text = snapshot.lyrics ?: return@LaunchedEffect
            if (text.isBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) return@LaunchedEffect

            if (snapshot.source == LyricsEntity.Source.AI_TRANSLATION.value &&
                LyricsUtils.hasTranslation(text)
            ) return@LaunchedEffect

            if (mediaMetadata.id in translationDismissedMediaIds) return@LaunchedEffect

            if (!LyricsUtils.shouldAutoTranslate(
                    lyrics = text,
                    targetLanguage = translatorTargetLang,
                    excludedLanguageCodes = autoTranslateExcludedLanguages,
                )
            ) {
                return@LaunchedEffect
            }

            lyricsMenuViewModel.translateLyricsWithAi(
                mediaMetadata = mediaMetadata,
                lyrics = text,
                targetLanguage = translatorTargetLang,
            )
        }

        val currentLyricsState = rememberUpdatedState(currentLyrics)
        val targetLangState = rememberUpdatedState(translatorTargetLang)

        fun setTranslationEnabled(enabled: Boolean) {
            onAutoTranslateLyricsChange(enabled)
            if (enabled) {
                val snapshot = currentLyricsState.value
                val text = snapshot?.lyrics
                if (snapshot != null && !text.isNullOrBlank() &&
                    text != LyricsEntity.LYRICS_NOT_FOUND &&
                    snapshot.source != LyricsEntity.Source.AI_TRANSLATION.value
                ) {
                    scope.launch {
                        lyricsMenuViewModel.translateLyricsWithAi(
                            mediaMetadata = mediaMetadata,
                            lyrics = text,
                            targetLanguage = targetLangState.value,
                        )
                    }
                }
            }
        }

        fun setRomanizationEnabled(enabled: Boolean) {
            onAiRomanizeLyricsChange(enabled)
            onAutoAiRomanizeLyricsChange(enabled)
        }

        var translationPopupOpen by remember { mutableStateOf(false) }
        var translationIconBounds by remember { mutableStateOf(Rect.Zero) }

        var artistPickerOpen by remember { mutableStateOf(false) }

        LaunchedEffect(nowPage) {
            if (nowPage != FlamingoPage.Lyric) {
                translationPopupOpen = false
            }
        }

        var showAnchoredLyricsMenu by remember { mutableStateOf(false) }
        var moreIconBounds by remember { mutableStateOf(Rect.Zero) }

        val glassAvailable = rememberLiquidGlassEnabled() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
        val popupBackdrop: PlatformBackdrop? =
            if (glassAvailable) {
                rememberBackdrop(Color.Transparent)
            } else {
                null
            }

        val menuState = LocalMenuState.current
        val onMoreClick = {
            if (nowPageLambda.value == FlamingoPage.Lyric) {
                showAnchoredLyricsMenu = true
            } else {
                menuState.showImmersive {
                    PlayerMenu(
                        mediaMetadata = mediaMetadata,
                        navController = navController,
                        playerBottomSheetState = state,
                        onShowDetailsDialog = {
                            bottomSheetPageState.show {
                                ShowMediaInfo(mediaMetadata.id)
                            }
                        },
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }

        FlamingoWrapper {
            LaunchedEffect(
                showControlLambda.value,
                nowPageLambda.value,
                lastClickTime.longValue,
                translationPopupOpen,
            ) {
                if (nowPageLambda.value == FlamingoPage.Album && !showControlLambda.value) {
                    showControl.value = true
                }

                if (translationPopupOpen) return@LaunchedEffect
                if (showControlLambda.value) {
                    val time = 2500L
                    delay(time)
                    withContext(Dispatchers.Main) {
                        if (System.currentTimeMillis() - lastClickTime.longValue >= time &&
                            nowPageLambda.value != FlamingoPage.Album
                        ) {
                            showControl.value = false
                        }
                    }
                }
            }
        }

        val sliderPositionState = rememberUpdatedState(sliderPosition)
        val lyricsPosProvider = remember { { sliderPositionState.value } }

        val lyricsControlsPoke: (Boolean) -> Unit =
            remember(showControl, landscape) {
                { scrolling ->
                    if (scrolling && !landscape) {
                        if (!showControl.value) showControl.value = true
                        lastClickTime.longValue = System.currentTimeMillis()
                    }
                }
            }

        val glassLayerActive = showAnchoredLyricsMenu || translationPopupOpen || artistPickerOpen

        LaunchedEffect(glassLayerActive) {
            if (!glassLayerActive) return@LaunchedEffect
            while (isActive) {
                withFrameNanos { }
                popupBackdrop?.requestRecord()
            }
        }

        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .let { base ->
                        if (popupBackdrop != null && glassLayerActive) {
                            base.layerBackdrop(popupBackdrop)
                        } else {
                            base
                        }
                    },
        ) {
            FlamingoWrapper {
                Box(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    FlamingoWrapper {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .onGloballyPositioned {
                                    backgroundTopY = it.positionInRoot().y
                                },
                        ) {
                            FlamingoFloatingLight(
                                albumUrl = { artworkUrl },
                                isPlaying = { isPlayingStatusLambda.value },
                                modifier = Modifier.fillMaxSize(),
                                nowPage = { nowPageLambda.value },
                                backgroundEffect = backgroundEffect,
                                motionSuspended = { pageMorphRunning },
                            )

                            if (!landscape && !videoShowing && canvasVisualActive) {
                                val configuration = LocalConfiguration.current
                                val stageFraction =
                                    sharpStageHeight?.let {
                                        (it / configuration.screenHeightDp.dp).coerceIn(0.1f, 1f)
                                    } ?: 0.55f
                                val frostFraction =
                                    (1f - FlamingoSharpStageFadeStart * stageFraction).coerceIn(0.2f, 1f)

                                val twinSnapshotLayer = rememberGraphicsLayer()
                                var blurredTwinBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
                                var twinBakeFailures by remember { mutableStateOf(0) }
                                val twinBackdropHealthy = twinBakeFailures < 5
                                val twinSnapshotBlurRadiusPx =
                                    with(LocalDensity.current) {
                                        (FlamingoCanvasBackdropBlurRadius / FlamingoCanvasBackdropUpscale).toPx()
                                    }
                                LaunchedEffect(canvasPrimaryUrl, canvasFallbackUrl) {
                                    blurredTwinBitmap = null
                                    twinBakeFailures = 0
                                    while (isActive) {
                                        if (twinBakeFailures >= 5) {
                                            delay(FlamingoCanvasBakeRetryCooldownMs)
                                        }
                                        if (pageMorphRunning) {
                                            delay(FlamingoCanvasSnapshotIntervalMs)
                                            continue
                                        }
                                        val layer = twinSnapshotLayer
                                        if (layer.size.width >= 8 && layer.size.height >= 8) {
                                            withFrameNanos { }
                                            val snapshot =
                                                runCatching {
                                                    layer.toImageBitmap().asAndroidBitmap()
                                                }.getOrNull()
                                            if (snapshot != null) {
                                                val baked =
                                                    withContext(Dispatchers.Default) {
                                                        runCatching {
                                                            val bakeRadius =
                                                                twinSnapshotBlurRadiusPx
                                                                    .coerceAtMost(FlamingoCanvasBackdropBakeMaxRadiusPx)
                                                            val downscale =
                                                                if (bakeRadius < twinSnapshotBlurRadiusPx) {
                                                                    (twinSnapshotBlurRadiusPx / bakeRadius)
                                                                } else {
                                                                    1f
                                                                }
                                                            val scaled =
                                                                if (downscale > 1.01f) {
                                                                    val w =
                                                                        (snapshot.width / downscale)
                                                                            .toInt()
                                                                            .coerceAtLeast(1)
                                                                    val h =
                                                                        (snapshot.height / downscale)
                                                                            .toInt()
                                                                            .coerceAtLeast(1)
                                                                    Bitmap.createScaledBitmap(snapshot, w, h, true)
                                                                } else {
                                                                    snapshot
                                                                }
                                                            ImageBlurUtils
                                                                .blur(scaled, bakeRadius)
                                                                .asImageBitmap()
                                                        }.getOrNull()
                                                    }
                                                if (baked != null) {
                                                    twinBakeFailures = 0
                                                    blurredTwinBitmap = baked
                                                } else {
                                                    twinBakeFailures++
                                                }
                                            } else {
                                                twinBakeFailures++
                                            }
                                        }
                                        delay(FlamingoCanvasSnapshotIntervalMs)
                                    }
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .align(Alignment.BottomCenter)
                                            .fillMaxWidth()
                                            .fillMaxHeight(frostFraction)
                                            .graphicsLayer {
                                                compositingStrategy = CompositingStrategy.Offscreen
                                                alpha = canvasSurfacesAlpha.value * canvasBackdropReveal
                                            }
                                            .drawWithContent {
                                                drawContent()
                                                drawRect(
                                                    brush = FlamingoBandTopFeatherBrush,
                                                    blendMode = BlendMode.DstIn,
                                                )
                                            },
                                ) {
                                    Box(
                                        modifier =
                                            Modifier
                                                .matchParentSize()
                                                .graphicsLayer {
                                                    val scale =
                                                        FlamingoCanvasBackdropOverscan * FlamingoCanvasBackdropUpscale
                                                    scaleX = scale
                                                    scaleY = scale
                                                },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        if (twinBackdropHealthy) {
                                            CanvasArtworkPlayer(
                                                primaryUrl = canvasPrimaryUrl,
                                                fallbackUrl = canvasFallbackUrl,
                                                isPlaying = isPlayingStatusLambda.value && canvasSurfacesPlaying,
                                                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                                maxVideoEdgePx = FlamingoCanvasBackdropMaxVideoEdgePx,
                                                loopSyncFollower = canvasLoopSync,
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth(1f / FlamingoCanvasBackdropUpscale)
                                                        .fillMaxHeight(1f / FlamingoCanvasBackdropUpscale)
                                                        .canvasSnapshotSource(
                                                            twinSnapshotLayer,
                                                            FlamingoCanvasSnapshotIntervalMs,
                                                        ),
                                            )
                                        }
                                        blurredTwinBitmap?.let { baked ->
                                            Image(
                                                bitmap = baked,
                                                contentDescription = null,
                                                contentScale = ContentScale.FillBounds,
                                                modifier =
                                                    Modifier
                                                        .fillMaxWidth(1f / FlamingoCanvasBackdropUpscale)
                                                        .fillMaxHeight(1f / FlamingoCanvasBackdropUpscale),
                                            )
                                        }
                                    }
                                }

                                Box(
                                    modifier =
                                        Modifier
                                            .matchParentSize()
                                            .graphicsLayer {
                                                alpha = canvasSurfacesAlpha.value * canvasBackdropReveal
                                            }
                                            .background(FlamingoCanvasScrimBrush),
                                )

                            }
                        }
                    }

                    if (!landscape) {
                        FlamingoWrapper {
                            AnimatedVisibility(
                                visible = nowPage == FlamingoPage.Lyric,
                                enter = fadeIn(tween(260, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(200, easing = FastOutSlowInEasing)),
                            ) {
                                Column(
                                    Modifier
                                        .fillMaxSize()
                                        .graphicsLayer {
                                            compositingStrategy = CompositingStrategy.ModulateAlpha
                                        },
                                ) {
                                    Spacer(modifier = Modifier.height(topInset + 114.dp))

                                    if (lyricsContentReady) {
                                        val isAiTranslated =
                                            currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value
                                        AnimatedContent(
                                            targetState = Triple(autoTranslateLyrics, romanizationOn, isAiTranslated),
                                            transitionSpec = {
                                                fadeIn(tween(360, easing = FastOutSlowInEasing)) togetherWith
                                                    fadeOut(tween(280, easing = FastOutSlowInEasing))
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .weight(1f),
                                            label = "FlamingoLyricsRender",
                                        ) { renderKey ->
                                            val (showTranslationLines, showRomanization, _) = renderKey
                                            CompositionLocalProvider(
                                                LocalLyricsScrollListener provides lyricsControlsPoke,
                                            ) {
                                                LyricsEnhanced(
                                                    sliderPositionProvider = lyricsPosProvider,
                                                    lyricsSyncOffset = lyricsSyncOffset,
                                                    translationVisibleOverride = showTranslationLines,
                                                    phoneticVisibleOverride = if (showRomanization) null else false,
                                                    textColorOverride = Color.White,
                                                    modifier = Modifier
                                                        .fillMaxSize()
                                                        .flamingoLyricsEdgeFade(
                                                            weightLambda = { showControlLambda.value },
                                                        ),
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

            FlamingoWrapper {
                val translationButtonEnabled = remember("FlamingoNowPlaying_translationButtonEnabled") {
                    derivedStateOf {
                        showControlLambda.value && alphaAnim.value != 0f
                    }
                }

                if (landscape) {
                    val landscapeSwipeModifier =
                        Modifier

                            .pointerInput(playerConnection) {
                                val swipeThresholdPx = 72.dp.toPx()
                                var accumulatedDrag = 0f
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            accumulatedDrag <= -swipeThresholdPx -> playerConnection.seekToNext()
                                            accumulatedDrag >= swipeThresholdPx -> playerConnection.seekToPrevious()
                                        }
                                        accumulatedDrag = 0f
                                    },
                                ) { change, dragAmount ->
                                    change.consume()
                                    accumulatedDrag += dragAmount
                                }
                            }

                    Row(
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        BoxWithConstraints(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        ) {
                            val landscapeCanvasFullBleed = canvasActive && !videoShowing
                            if (videoShowing) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .then(landscapeSwipeModifier),
                                ) {
                                    InlineVideoPlayer(
                                        controlsOnTap = true,
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                    Box(
                                        modifier =
                                            Modifier
                                                .matchParentSize()
                                                .background(FlamingoLandscapeRightScrim),
                                    )
                                }
                            } else if (landscapeCanvasFullBleed) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .then(landscapeSwipeModifier),
                                ) {
                                    FlamingoLandscapeStage(
                                        artworkUrl = artworkUrl,
                                        canvasPrimaryUrl = canvasPrimaryUrl,
                                        canvasFallbackUrl = canvasFallbackUrl,
                                        isPlaying = isPlaying,
                                        fullBleed = true,
                                        artworkSize = null,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            } else {
                                val landscapeArtworkSize =
                                    (maxWidth - 96.dp)
                                        .coerceAtMost(maxHeight * 0.68f)
                                        .coerceAtLeast(220.dp)

                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .then(landscapeSwipeModifier),
                                ) {
                                    FlamingoLandscapeStage(
                                        artworkUrl = artworkUrl,
                                        canvasPrimaryUrl = canvasPrimaryUrl,
                                        canvasFallbackUrl = canvasFallbackUrl,
                                        isPlaying = isPlaying,
                                        fullBleed = false,
                                        artworkSize = landscapeArtworkSize,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }

                        Box(
                            modifier =
                                Modifier
                                    .weight(1f)
                                    .fillMaxHeight(),
                        ) {
                            androidx.compose.animation.AnimatedVisibility(
                                visible = nowPage == FlamingoPage.Lyric,
                                enter = fadeIn(tween(400, easing = FastOutSlowInEasing)),
                                exit = fadeOut(tween(300, easing = FastOutSlowInEasing)),
                                modifier = Modifier.matchParentSize(),
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(horizontal = 16.dp),
                                ) {
                                    if (lyricsContentReady) {
                                        val isAiTranslated =
                                            currentLyrics?.source == LyricsEntity.Source.AI_TRANSLATION.value
                                        AnimatedContent(
                                            targetState = Triple(autoTranslateLyrics, romanizationOn, isAiTranslated),
                                            transitionSpec = {
                                                fadeIn(tween(360, easing = FastOutSlowInEasing)) togetherWith
                                                    fadeOut(tween(280, easing = FastOutSlowInEasing))
                                            },
                                            modifier = Modifier.fillMaxSize(),
                                            label = "FlamingoLandscapeLyricsRender",
                                        ) { renderKey ->
                                            val (showTranslationLines, showRomanization, _) = renderKey
                                            CompositionLocalProvider(
                                                LocalLyricsScrollListener provides lyricsControlsPoke,
                                            ) {
                                                LyricsEnhanced(
                                                    sliderPositionProvider = lyricsPosProvider,
                                                    lyricsSyncOffset = lyricsSyncOffset,
                                                    translationVisibleOverride = showTranslationLines,
                                                    phoneticVisibleOverride = if (showRomanization) null else false,
                                                    textColorOverride = Color.White,
                                                    modifier = Modifier.fillMaxSize(),
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            androidx.compose.animation.AnimatedVisibility(
                                visible = nowPage != FlamingoPage.Lyric || showControl.value,
                                enter = fadeIn(tween(120)),
                                exit = fadeOut(tween(100)),
                                modifier = Modifier.matchParentSize(),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxSize()
                                            .padding(bottom = contentBottomPadding),
                                    verticalArrangement = Arrangement.Bottom,
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                    ) {
                                        FlamingoLandscapeTitleBlock(
                                            mediaMetadata = mediaMetadata,
                                            currentSongLiked = currentSongLiked,
                                            playerConnection = playerConnection,
                                            navController = navController,
                                            state = state,
                                            bottomSheetPageState = bottomSheetPageState,
                                            onMoreClick = onMoreClick,
                                            onMorePositioned = { moreIconBounds = it },
                                            onArtistClick = { artistPickerOpen = true },
                                            playerMenuOpen = showAnchoredLyricsMenu,
                                        )

                                        FlamingoPlayerControl(
                                            isPlayingLambda = { isPlayingStatusLambda.value },
                                            playbackState = playbackState,
                                            positionProvider = positionProvider,
                                            durationProvider = { duration },
                                            playerConnection = playerConnection,
                                            currentFormat = currentFormat,
                                            showVolumeBar = showVolumeBar,
                                            volume = volume,
                                            onVolumeChange = onVolumeChange,
                                            nowPage = { nowPageLambda.value },
                                            onLyrics = {
                                                nowPage = if (nowPageLambda.value == FlamingoPage.Lyric) {
                                                    FlamingoPage.Album
                                                } else {
                                                    FlamingoPage.Lyric
                                                }
                                            },
                                            onPlaylist = {
                                                nowPage = if (nowPageLambda.value == FlamingoPage.PlayingList) {
                                                    FlamingoPage.Album
                                                } else {
                                                    FlamingoPage.PlayingList
                                                }
                                            },
                                            onSlider = {
                                                showControl.value = true
                                                lastClickTime.longValue = System.currentTimeMillis()
                                            },
                                            onQualityClick = {
                                                bottomSheetPageState.show {
                                                    ShowMediaInfo(mediaMetadata.id)
                                                }
                                            },
                                            onSliderValueChange = onSliderValueChange,
                                            onSliderValueChangeFinished = onSliderValueChangeFinished,
                                            modifier = Modifier
                                                .padding(top = 8.dp),
                                        )
                                    }
                                }
                            }
                        }
                    }

                    FlamingoWrapper {
                        AnimatedVisibility(
                            visible = nowPage == FlamingoPage.PlayingList,
                            enter = fadeIn(tween(AnimDurationMillis)),
                            exit = fadeOut(tween(AnimDurationMillis)),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topInset + 20.dp),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(enabled = false, onClick = {}),
                            ) {
                                FlamingoPlayingList(
                                    playerConnection = playerConnection,
                                    queueWindows = queueWindows,
                                    currentWindowIndex = currentWindowIndex,
                                    shuffleModeEnabled = shuffleModeEnabled,
                                    repeatMode = repeatMode,
                                    onControlsPoke = {
                                        if (!showControlLambda.value) showControl.value = true
                                        lastClickTime.longValue = System.currentTimeMillis()
                                    },
                                )
                            }
                        }
                    }
                } else {
                    FlamingoWrapper {
                        SharedTransitionLayout {
                            AnimatedContent(
                                targetState = nowPage,
                                transitionSpec = {

                                    fadeIn(
                                        tween(
                                            durationMillis = 600,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    ) togetherWith fadeOut(
                                        tween(
                                            durationMillis = 600,
                                            easing = FastOutSlowInEasing,
                                        ),
                                    )
                                },
                                modifier = Modifier.fillMaxSize(),
                                label = "FlamingoPageCrossfade",
                            ) { page ->
                                val pageBoundsScope = this@AnimatedContent

                                val albumSharedBounds =
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = ShareAlbumKey),
                                        animatedVisibilityScope = pageBoundsScope,
                                        clipInOverlayDuringTransition = OverlayClip(FlamingoAlbumOverlayShape),
                                        boundsTransform = BoundsTransform { _, _ ->
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            )
                                        },
                                    )
                                val barSharedBounds =
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = ShareAlbumKey),
                                        animatedVisibilityScope = pageBoundsScope,
                                        clipInOverlayDuringTransition = OverlayClip(FlamingoAlbumOverlayShape),
                                        boundsTransform = BoundsTransform { _, _ ->
                                            spring(
                                                dampingRatio = Spring.DampingRatioNoBouncy,
                                                stiffness = Spring.StiffnessMediumLow,
                                            )
                                        },
                                    )
                                when (page) {
                                    FlamingoPage.Album ->
                                        Box(Modifier.fillMaxSize()) {
                                            if (stageVisualActive) {
                                                FlamingoCanvasStage(
                                                    canvasPrimaryUrl = canvasPrimaryUrl,
                                                    canvasFallbackUrl = canvasFallbackUrl,
                                                    isPlaying = isPlayingStatusLambda.value,
                                                    stageHeight = sharpStageHeight,
                                                    staticArtworkUrl = artworkUrl,
                                                    loopSyncLeader = canvasLoopSync,
                                                    refreshEpoch = orientationRefreshEpoch,
                                                    surfaceVisible = canvasStageSurfaceActive,
                                                    videoVisualAlpha = stageCanvasPageAlpha,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .then(albumSharedBounds),
                                                    proceduralContent = if (canvasVisualActive) {
                                                        null
                                                    } else {
                                                        {
                                                            FlamingoProceduralStage(
                                                                artworkUrl = artworkUrl,
                                                                isPlaying = isPlayingStatusLambda.value,
                                                                modifier = Modifier.matchParentSize(),
                                                            )
                                                        }
                                                    },
                                                )
                                            }
                                            Column(
                                                Modifier
                                                    .fillMaxSize()
                                                    .clickable(enabled = false, onClick = {}),
                                            ) {
                                                FlamingoWrapper {
                                                    Column(
                                                        Modifier
                                                            .padding(top = topInset + 22.dp)
                                                            .fillMaxHeight(0.595f),
                                                    ) {
                                                        FlamingoAlbum(
                                                            modifier = albumSharedBounds,
                                                            artworkUrl = artworkUrl,
                                                            isPlaying = { isPlayingStatusLambda.value },
                                                            canvasActive = canvasVisualActive,
                                                            videoShowing = videoShowing,
                                                            proceduralActive = proceduralStageActive,
                                                            overlayArtworkReveal = overlayArtworkReveal,
                                                        )
                                                        AnimatedContent(
                                                            targetState = mediaMetadata,
                                                            transitionSpec = {
                                                                fadeIn() togetherWith fadeOut()
                                                            },
                                                            modifier = Modifier
                                                                .padding(horizontal = 32.dp)
                                                                .onGloballyPositioned { coords ->
                                                                    titleTopInRootY = coords.positionInRoot().y
                                                                },
                                                        ) { metadata ->
                                                        Row(
                                                            Modifier
                                                                .fillMaxWidth(),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                        ) {
                                                            Column(
                                                                Modifier
                                                                    .fillMaxWidth()
                                                                    .weight(1f)
                                                                    .padding(end = 15.dp),
                                                            ) {
                                                                Text(
                                                                    text = metadata.title,
                                                                    fontSize = 19.5.sp,
                                                                    maxLines = 1,
                                                                    fontWeight = FontWeight.Medium,
                                                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
                                                                )
                                                                Text(
                                                                    text = metadata.artistNames(),
                                                                    fontSize = 18.5.sp,
                                                                    modifier = Modifier
                                                                        .overlayEffect()

                                                                        .clickable(
                                                                            interactionSource = remember { MutableInteractionSource() },
                                                                            indication = null,
                                                                            onClick = {
                                                                                FlamingoHaptics.click(context)
                                                                                artistPickerOpen = true
                                                                            },
                                                                        ),
                                                                    maxLines = 1,
                                                                    overflow = TextOverflow.Ellipsis,
                                                                    color = Color.White.copy(alpha = 0.35f),
                                                                )
                                                            }

                                                            FlamingoWrapper {
                                                                FlamingoActionButtonsRow(
                                                                    mediaMetadata = metadata,
                                                                    playerConnection = playerConnection,
                                                                    navController = navController,
                                                                    state = state,
                                                                    bottomSheetPageState = bottomSheetPageState,
                                                                    currentSongLiked = currentSongLiked,
                                                                    onMoreClick = onMoreClick,
                                                                    onMorePositioned = { moreIconBounds = it },
                                                                    playerMenuOpen = showAnchoredLyricsMenu,
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    FlamingoPage.Lyric ->
                                        Column(
                                            Modifier
                                                .fillMaxSize()
                                                .padding(top = topInset + 22.dp),
                                        ) {
                                            FlamingoWrapper {
                                                FlamingoPlayingBar(
                                                    modifier = barSharedBounds,
                                                    artworkUrl = artworkUrl,
                                                    mediaMetadata = mediaMetadata,
                                                    playerConnection = playerConnection,
                                                    navController = navController,
                                                    state = state,
                                                    bottomSheetPageState = bottomSheetPageState,
                                                    currentSongLiked = currentSongLiked,
                                                    onAlbumClick = { nowPage = FlamingoPage.Album },
                                                    onMoreClick = onMoreClick,
                                                    onMorePositioned = { moreIconBounds = it },
                                                    onArtistClick = { artistPickerOpen = true },
                                                    playerMenuOpen = showAnchoredLyricsMenu,
                                                )
                                            }
                                        }

                                    FlamingoPage.PlayingList ->
                                        FlamingoWrapper {
                                            Column(
                                                Modifier
                                                    .fillMaxSize()
                                                    .padding(top = topInset + 22.dp)
                                                    .clickable(enabled = false, onClick = {}),
                                            ) {
                                                FlamingoPlayingBar(
                                                    modifier = barSharedBounds,
                                                    artworkUrl = artworkUrl,
                                                    mediaMetadata = mediaMetadata,
                                                    playerConnection = playerConnection,
                                                    navController = navController,
                                                    state = state,
                                                    bottomSheetPageState = bottomSheetPageState,
                                                    currentSongLiked = currentSongLiked,
                                                    onAlbumClick = { nowPage = FlamingoPage.Album },
                                                    onMoreClick = onMoreClick,
                                                    onMorePositioned = { moreIconBounds = it },
                                                    onArtistClick = { artistPickerOpen = true },
                                                    playerMenuOpen = showAnchoredLyricsMenu,
                                                )
                                            }
                                        }
                                }
                            }
                        }
                    }


                    val (hideSlideBar) = rememberPreference(FlamingoHideSlideBarKey, defaultValue = false)
                    if (!hideSlideBar) {
                        FlamingoWrapper {
                            Column(Modifier.fillMaxWidth()) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = topInset + 20.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Box(
                                        Modifier
                                            .overlayEffect()
                                            .size(
                                                width = 32.dp,
                                                height = 4.5.dp,
                                            )
                                            .background(Color(0x4DFFFFFF), RoundedCornerShape(2.25.dp)),
                                    )
                                }
                            }
                        }
                    }

                    FlamingoWrapper {
                        AnimatedVisibility(
                            visible = nowPage == FlamingoPage.PlayingList,
                            enter = fadeIn(tween(AnimDurationMillis)),
                            exit = fadeOut(tween(AnimDurationMillis)),
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(top = topInset + 114.dp),
                        ) {
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clickable(enabled = false, onClick = {}),
                            ) {
                                FlamingoPlayingList(
                                    playerConnection = playerConnection,
                                    queueWindows = queueWindows,
                                    currentWindowIndex = currentWindowIndex,
                                    shuffleModeEnabled = shuffleModeEnabled,
                                    repeatMode = repeatMode,
                                    bottomOverlayPx = { controlsOverlayHeightPx },
                                    controlsVisible = { showControlLambda.value },
                                    onControlsPoke = {
                                        if (!showControlLambda.value) showControl.value = true
                                        lastClickTime.longValue = System.currentTimeMillis()
                                    },
                                )
                            }
                        }
                    }

                    FlamingoWrapper {
                        Column(
                            Modifier
                                .fillMaxSize()
                                .padding(top = topInset),
                            verticalArrangement = Arrangement.Bottom,
                        ) {
                            Box(
                                Modifier
                                    .fillMaxHeight(0.437f)
                                    .fillMaxWidth(),
                            ) {
                                FlamingoWrapper {
                                    if (nowPage == FlamingoPage.Lyric) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(top = 40.dp)
                                                .clickable(
                                                    interactionSource = remember { MutableInteractionSource() },
                                                    indication = null,
                                                    onClick = {
                                                        showControl.value = true
                                                        lastClickTime.longValue =
                                                            System.currentTimeMillis()
                                                    },
                                                ),
                                        )
                                    }
                                }

                                FlamingoWrapper {
                                    Column(
                                        Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.Bottom,
                                    ) {
                                        AnimatedVisibility(
                                            visible = showControl.value,
                                            modifier = Modifier
                                                .onGloballyPositioned { coords ->

                                                    controlsOverlayHeightPx = coords.size.height.toFloat()
                                                },
                                            enter = fadeIn() + expandVertically(
                                                expandFrom = Alignment.Top,
                                                initialHeight = { (it / 1.4).toInt() },
                                            ),
                                            exit = fadeOut() + shrinkVertically(
                                                shrinkTowards = Alignment.Top,
                                                targetHeight = { (it / 1.4).toInt() },
                                            ),
                                        ) {

                                            Box(modifier = Modifier.fillMaxWidth()) {
                                                if (nowPage == FlamingoPage.Lyric) {
                                                Row(
                                                    Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 32.dp)
                                                        .graphicsLayer {
                                                            compositingStrategy =
                                                                CompositingStrategy.ModulateAlpha
                                                            this.alpha = alphaAnim.value
                                                        },
                                                    horizontalArrangement = Arrangement.End,
                                                ) {
                                                    FlamingoWrapper {
                                                        Box(
                                                            modifier = Modifier
                                                                .overlayEffect()
                                                                .alpha(0.4f)
                                                                .onGloballyPositioned { coords ->
                                                                    translationIconBounds = coords.boundsInRoot()
                                                                }
                                                                .clickable(
                                                                    enabled = translationButtonEnabled.value,
                                                                    onClick = {
                                                                        FlamingoHaptics.click(context)
                                                                        translationPopupOpen = !translationPopupOpen
                                                                        showControl.value = true
                                                                        lastClickTime.longValue =
                                                                            System.currentTimeMillis()
                                                                    },
                                                                    indication = null,
                                                                    interactionSource = remember { MutableInteractionSource() },
                                                                ),
                                                            contentAlignment = Alignment.Center,
                                                        ) {
                                                            AnimatedContent(
                                                                targetState = autoTranslateLyrics || romanizationOn,
                                                                transitionSpec = {
                                                                    fadeIn() togetherWith fadeOut()
                                                                },
                                                            ) { translationOn ->
                                                                if (translationOn) {
                                                                    Icon(
                                                                        painterResource(id = R.drawable.flamingo_np_translateon),
                                                                        contentDescription = null,
                                                                        tint = Color.Unspecified,
                                                                        modifier = Modifier
                                                                            .size(30.dp),
                                                                    )
                                                                } else {
                                                                    Icon(
                                                                        painterResource(id = R.drawable.flamingo_np_translate),
                                                                        contentDescription = null,
                                                                        tint = Color.Unspecified,
                                                                        modifier = Modifier
                                                                            .size(30.dp),
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                                }
                                            }

                                            FlamingoPlayerControl(
                                                isPlayingLambda = { isPlayingStatusLambda.value },
                                                playbackState = playbackState,
                                                positionProvider = positionProvider,
                                                durationProvider = { duration },
                                                playerConnection = playerConnection,
                                                currentFormat = currentFormat,
                                                showVolumeBar = showVolumeBar,
                                                volume = volume,
                                                onVolumeChange = onVolumeChange,
                                                nowPage = { nowPageLambda.value },
                                                onLyrics = {
                                                    translationPopupOpen = false
                                                    nowPage = if (nowPageLambda.value == FlamingoPage.Lyric) {
                                                        FlamingoPage.Album
                                                    } else {
                                                        FlamingoPage.Lyric
                                                    }
                                                },
                                                onPlaylist = {
                                                    translationPopupOpen = false
                                                    nowPage = if (nowPageLambda.value == FlamingoPage.PlayingList) {
                                                        FlamingoPage.Album
                                                    } else {
                                                        FlamingoPage.PlayingList
                                                    }
                                                },
                                                onSlider = {
                                                    showControl.value = true
                                                    lastClickTime.longValue = System.currentTimeMillis()
                                                },
                                                onQualityClick = {
                                                    bottomSheetPageState.show {
                                                        ShowMediaInfo(mediaMetadata.id)
                                                    }
                                                },
                                                onSliderValueChange = onSliderValueChange,
                                                onSliderValueChangeFinished = onSliderValueChangeFinished,
                                                modifier = Modifier
                                                    .padding(top = 52.dp),
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

        if (showAnchoredLyricsMenu) {
            AnchoredLyricsOverflowMenu(
                iconBoundsInRoot = moreIconBounds,
                lyricsProvider = { currentLyrics },
                mediaMetadataProvider = { mediaMetadata },
                lyricsSyncOffset = lyricsSyncOffset,
                onLyricsSyncOffsetChange = onLyricsSyncOffsetChange,
                onDismiss = { showAnchoredLyricsMenu = false },
                backdrop = popupBackdrop,
                showTranslationActions = false,
            )
        }

        if (translationPopupOpen) {
            FlamingoTranslationPopup(
                iconBoundsInRoot = translationIconBounds,
                translationEnabled = autoTranslateLyrics,
                romanizationEnabled = romanizationOn,
                onToggleTranslation = {
                    FlamingoHaptics.click(context)
                    setTranslationEnabled(!autoTranslateLyrics)
                },
                onToggleRomanization = {
                    FlamingoHaptics.click(context)
                    setRomanizationEnabled(!romanizationOn)
                },
                onDismiss = { translationPopupOpen = false },
                backdrop = popupBackdrop,
            )
        }

        if (artistPickerOpen) {
            FlamingoArtistPickerSheet(
                mediaMetadata = mediaMetadata,
                navController = navController,
                playerSheetState = state,
                onDismiss = { artistPickerOpen = false },
                backdrop = popupBackdrop,
            )
        }
    }
}

private val FlamingoLyricsEdgeFadeStopsControlsVisible =
    listOf(
        Color.Transparent,
        Color(0x59000000),
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color(0x59000000),
        Color(0x21000000),
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
        Color.Transparent,
    )

private val FlamingoLyricsEdgeFadeStopsControlsHidden =
    listOf(
        Color.Transparent,
        Color(0x59000000),
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
        Color.Black,
    )

private fun Modifier.flamingoLyricsEdgeFade(
    weightLambda: () -> Boolean,
): Modifier = drawWithCache {

    val overlayPaint = Paint().apply {
        blendMode = BlendMode.Plus
    }
    val rect = androidx.compose.ui.geometry.Rect(0f, 0f, size.width, size.height)
    val fadeBrush = Brush.verticalGradient(
        if (weightLambda()) {
            FlamingoLyricsEdgeFadeStopsControlsVisible
        } else {
            FlamingoLyricsEdgeFadeStopsControlsHidden
        },
    )

    onDrawWithContent {
        val canvas = this.drawContext.canvas
        canvas.saveLayer(rect, overlayPaint)

        drawContent()

        drawRect(
            brush = fadeBrush,
            blendMode = BlendMode.DstIn,
        )

        canvas.restore()
    }
}

private val FlamingoPopupVerticalGap = 4.dp
private val FlamingoPopupHorizontalMargin = 16.dp

@Composable
private fun FlamingoPopupDismissScrim(
    onDismiss: () -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onDismiss,
                ),
    )
}

@Composable
private fun FlamingoPopupPanel(
    alpha: Float,
    scale: Float,
    transformOrigin: TransformOrigin,
    backdrop: PlatformBackdrop?,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    val glassTuning = LocalLiquidGlassTuning.current
    val frostedBlurModifier =
        remember(backdrop, glassTuning) {
            if (backdrop != null) {
                Modifier.drawBackdrop(
                    backdrop = backdrop,
                    effects = {
                        colorControls(saturation = glassTuning.saturation)
                        blur((20f * glassTuning.blurFactor).dp.toPx())
                    },
                    onDrawBackdrop = { drawBackdrop ->
                        drawBackdrop()
                    },
                    shape = { RoundedCornerShape(16.dp) },
                )
            } else {
                null
            }
        }

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    this.alpha = alpha
                    this.scaleX = scale
                    this.scaleY = scale
                    this.transformOrigin = transformOrigin
                    this.shadowElevation = 16.dp.toPx()
                    this.shape = RoundedCornerShape(16.dp)
                    this.clip = false
                }
                .then(
                    frostedBlurModifier
                        ?: Modifier.background(Color.Black.copy(alpha = 0.65f * alpha)),
                )
                .background(Color.Black.copy(alpha = 0.55f))
                .clip(RoundedCornerShape(16.dp))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                ),
        content = content,
    )
}

@Composable
private fun FlamingoTranslationPopup(
    iconBoundsInRoot: Rect,
    translationEnabled: Boolean,
    romanizationEnabled: Boolean,
    onToggleTranslation: () -> Unit,
    onToggleRomanization: () -> Unit,
    onDismiss: () -> Unit,
    backdrop: PlatformBackdrop?,
) {
    var dismissed by remember { mutableStateOf(false) }

    BackHandler(enabled = !dismissed) {
        dismissed = true
    }

    val scope = rememberCoroutineScope()
    val scaleAnim = remember { Animatable(0.85f) }
    val alphaAnim = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        if (dismissed) return@LaunchedEffect
        scope.launch {
            scaleAnim.animateTo(
                targetValue = 1f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
        scope.launch {
            alphaAnim.animateTo(targetValue = 1f, animationSpec = tween(180))
        }
    }

    LaunchedEffect(dismissed) {
        if (!dismissed) return@LaunchedEffect
        val scaleJob = scope.launch {
            scaleAnim.animateTo(
                targetValue = 0.85f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium,
                    ),
            )
        }
        val alphaJob = scope.launch {
            alphaAnim.animateTo(targetValue = 0f, animationSpec = tween(180))
        }
        scaleJob.join()
        alphaJob.join()
        onDismiss()
    }

    val density = LocalDensity.current
    val popupWidth = 200.dp
    var popupHeightPx by remember { mutableIntStateOf(0) }

    Box(modifier = Modifier.fillMaxSize()) {
        FlamingoPopupDismissScrim(onDismiss = { if (!dismissed) dismissed = true })

        FlamingoPopupPanel(
            alpha = alphaAnim.value,
            scale = scaleAnim.value,
            transformOrigin = TransformOrigin(1f, 1f),
            backdrop = backdrop,
            modifier =
                Modifier
                    .offset {
                        val popupWidthPx = with(density) { popupWidth.toPx() }.toInt()
                        val horizontalMarginPx =
                            with(density) { FlamingoPopupHorizontalMargin.toPx() }.toInt()
                        val gapPx = with(density) { FlamingoPopupVerticalGap.toPx() }.toInt()
                        val iconRight = iconBoundsInRoot.right.toInt()

                        val x = (iconRight - popupWidthPx)
                            .coerceAtLeast(horizontalMarginPx)
                        val neededHeightPx =
                            if (popupHeightPx > 0) {
                                popupHeightPx
                            } else {
                                with(density) { 120.dp.toPx() }.toInt()
                            }
                        val y = (iconBoundsInRoot.top - gapPx - neededHeightPx)
                            .coerceAtLeast(0f)
                            .toInt()
                        IntOffset(x = x, y = y)
                    }
                    .width(popupWidth)
                    .onSizeChanged { popupHeightPx = it.height }
                    .padding(6.dp),
        ) {
            FlamingoTranslationPopupRow(
                label = stringResource(
                    if (translationEnabled) {
                        R.string.hide_translation
                    } else {
                        R.string.show_translation
                    },
                ),
                iconRes = R.drawable.solar_eye,
                onClick = onToggleTranslation,
            )

            Box(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .background(Color.White.copy(alpha = 0.16f)),
            )
            FlamingoTranslationPopupRow(
                label = stringResource(
                    if (romanizationEnabled) {
                        R.string.hide_romanization
                    } else {
                        R.string.show_romanization
                    },
                ),
                iconRes = R.drawable.language,
                onClick = onToggleRomanization,
            )
        }
    }
}

@Composable
private fun FlamingoTranslationPopupRow(
    label: String,
    iconRes: Int,
    onClick: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 44.dp)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onClick,
                )
                .padding(horizontal = 14.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.85f),
            modifier = Modifier.size(18.dp),
        )
    }
}

@Composable
private fun FlamingoArtistPickerSheet(
    mediaMetadata: MediaMetadata,
    navController: NavController,
    playerSheetState: BottomSheetState,
    onDismiss: () -> Unit,
    backdrop: PlatformBackdrop?,
) {
    val context = LocalContext.current
    val database = LocalDatabase.current
    val scope = rememberCoroutineScope()

    var dismissed by remember { mutableStateOf(false) }
    BackHandler(enabled = !dismissed) {
        dismissed = true
    }

    val slideAnim = remember { Animatable(60f) }
    val alphaAnim = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (dismissed) return@LaunchedEffect
        scope.launch {
            slideAnim.animateTo(
                targetValue = 0f,
                animationSpec =
                    spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow,
                    ),
            )
        }
        scope.launch {
            alphaAnim.animateTo(targetValue = 1f, animationSpec = tween(200))
        }
    }
    LaunchedEffect(dismissed) {
        if (!dismissed) return@LaunchedEffect
        scope.launch { alphaAnim.animateTo(targetValue = 0f, animationSpec = tween(180)) }.join()
        onDismiss()
    }

    val (artistSeparators) = rememberPreference(ArtistSeparatorsKey, defaultValue = "")

    data class SplitArtist(
        val name: String,
        val originalArtist: MediaMetadata.Artist?,
    )

    val splitArtists =
        remember(mediaMetadata.artists, artistSeparators) {
            if (artistSeparators.isEmpty()) {
                mediaMetadata.artists.map { SplitArtist(it.name, it) }
            } else {
                val separatorRegex = "[${Regex.escape(artistSeparators)}]".toRegex()
                mediaMetadata.artists.flatMap { artist ->
                    val parts =
                        artist.name
                            .split(separatorRegex)
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                    if (parts.size > 1) {
                        parts.map { name -> SplitArtist(name, artist) }
                    } else {
                        listOf(SplitArtist(artist.name, artist))
                    }
                }
            }
        }

    val artistIdsKey =
        remember(splitArtists) {
            splitArtists.mapNotNull { it.originalArtist?.id }.distinct().sorted()
        }
    val artistThumbnailsById: Map<String, String?> by produceState(
        initialValue = emptyMap(),
        artistIdsKey,
    ) {
        withContext(Dispatchers.IO) {
            val result = mutableMapOf<String, String?>()
            val nameById =
                splitArtists
                    .mapNotNull { sa ->
                        sa.originalArtist?.id?.let { id -> id to sa.originalArtist.name }
                    }.toMap()

            splitArtists.mapNotNull { it.originalArtist?.id }.distinct().forEach { artistId ->
                val dbEntity = database.getArtistById(artistId)
                val cached = dbEntity?.thumbnailUrl
                if (!cached.isNullOrBlank()) {
                    result[artistId] = cached
                    value = result.toMap()
                } else {
                    val fetched =
                        runCatching { YouTube.artist(artistId) }
                            .getOrNull()
                            ?.getOrNull()
                            ?.artist
                            ?.thumbnail
                    if (!fetched.isNullOrBlank()) {
                        result[artistId] = fetched
                        value = result.toMap()
                        runCatching {
                            database.query {
                                upsert(
                                    ArtistEntity(
                                        id = artistId,
                                        name = dbEntity?.name ?: nameById[artistId].orEmpty(),
                                        thumbnailUrl = fetched,
                                        channelId = dbEntity?.channelId,
                                        lastUpdateTime = dbEntity?.lastUpdateTime ?: LocalDateTime.now(),
                                        bookmarkedAt = dbEntity?.bookmarkedAt,
                                        blockedAt = dbEntity?.blockedAt,
                                        isLocal = dbEntity?.isLocal ?: false,
                                    ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    fun openArtist(artistId: String) {
        dismissed = true
        playerSheetState.snapTo(playerSheetState.collapsedBound)
        navController.navigate("artist/$artistId")
    }

    Box(modifier = Modifier.fillMaxSize()) {
        FlamingoPopupDismissScrim(onDismiss = { if (!dismissed) dismissed = true })

        val density = LocalDensity.current
        FlamingoPopupPanel(
            alpha = alphaAnim.value,
            scale = 1f,
            transformOrigin = TransformOrigin(0.5f, 1f),
            backdrop = backdrop,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 30.dp)
                    .graphicsLayer {
                        translationY = with(density) { slideAnim.value.dp.toPx() }
                    }
                    .fillMaxWidth()
                    .widthIn(max = 360.dp),
        ) {
            Column(Modifier.padding(vertical = 8.dp)) {
                splitArtists.forEachIndexed { index, splitArtist ->
                    if (index > 0) {
                        Box(
                            modifier =
                                Modifier
                                    .padding(horizontal = 14.dp)
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(Color.White.copy(alpha = 0.16f)),
                        )
                    }
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        FlamingoHaptics.click(context)
                                        splitArtist.originalArtist?.id?.let(::openArtist)
                                    },
                                )
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier.size(36.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val thumb =
                                splitArtist.originalArtist?.id
                                    ?.let { artistThumbnailsById[it] }
                            if (thumb != null) {
                                AsyncImage(
                                    model = thumb,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape),
                                )
                            } else {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(Color.White.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.artist),
                                        contentDescription = null,
                                        tint = Color.White.copy(alpha = 0.6f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = splitArtist.name,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.FlamingoAlbum(
    modifier: Modifier,
    artworkUrl: String?,
    isPlaying: () -> Boolean,
    canvasActive: Boolean,
    videoShowing: Boolean,
    proceduralActive: Boolean,
    overlayArtworkReveal: Float,
) {
    if (videoShowing) {
        Box(
            Modifier
                .weight(1f)
                .padding(top = 20.dp)
                .padding(horizontal = 15.dp)
                .padding(bottom = 33.dp),
            contentAlignment = Alignment.Center,
        ) {
            ShadowImageWithCache(
                dataLambda = { artworkUrl },
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                        alpha = overlayArtworkReveal.coerceAtLeast(1f / 255f)
                    }
                    .then(modifier),
                imageQuality = ImageQuality.LOW,
                shadowOverlay = true,
            )
            InlineVideoPlayer(
                controlsOnTap = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
        }
        return
    }

    if (canvasActive || proceduralActive) {
        Box(
            Modifier
                .weight(1f),
        )
        return
    }

    Box(
        Modifier
            .weight(1f)
            .padding(top = 20.dp)
            .padding(horizontal = 15.dp)
            .padding(bottom = 33.dp),
        contentAlignment = Alignment.BottomCenter,
    ) {
        val springSpec: AnimationSpec<Float> = remember("FlamingoAlbum_springSpec") {
            SpringSpec(stiffness = 300f, dampingRatio = 1f, visibilityThreshold = 0.001f)
        }

        val tweenSpec: AnimationSpec<Float> = remember("FlamingoAlbum_tweenSpec") {
            TweenSpec(durationMillis = 350, easing = EaseOutQuart)
        }

        val scale = animateFloatAsState(
            targetValue = if (isPlaying()) 0f else 1f,
            animationSpec = if (isPlaying()) springSpec else tweenSpec,
            visibilityThreshold = 0.001f,
        )

        FlamingoWrapper {
            val dp = (7 + (27 * scale.value)).dp
            ShadowImageWithCache(
                dataLambda = { artworkUrl },
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        compositingStrategy = CompositingStrategy.ModulateAlpha
                    }
                    .padding(start = dp, end = dp, bottom = dp)
                    .then(modifier),
                imageQuality = ImageQuality.RAW,
                shadowOverlay = true,
            )
        }
    }
}

@Composable
private fun FlamingoCanvasStage(
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    stageHeight: Dp?,
    staticArtworkUrl: String?,
    loopSyncLeader: CanvasLoopSync? = null,
    refreshEpoch: Int = 0,
    surfaceVisible: Boolean = true,
    videoVisualAlpha: Float = 1f,
    modifier: Modifier = Modifier,
    proceduralContent: (@Composable () -> Unit)? = null,
) {
    var canvasFrameReady by remember(canvasPrimaryUrl, canvasFallbackUrl) {
        mutableStateOf(false)
    }
    var settledArtworkUrl by remember { mutableStateOf<String?>(null) }
    var incomingArtworkUrl by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(staticArtworkUrl) {
        if (staticArtworkUrl.isNullOrBlank()) {
            settledArtworkUrl = null
            incomingArtworkUrl = null
        } else if (staticArtworkUrl != settledArtworkUrl) {
            incomingArtworkUrl = staticArtworkUrl
        }
    }
    val staticBaseAlpha by animateFloatAsState(
        targetValue = if (canvasFrameReady) 0f else 1f,
        animationSpec = tween(durationMillis = 450, easing = FastOutSlowInEasing),
        label = "flamingo-canvas-static-base",
    )
    val staticRevealAlpha = maxOf(staticBaseAlpha, 1f - videoVisualAlpha)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(
                when {
                    stageHeight != null -> Modifier.height(stageHeight)
                    else -> Modifier.fillMaxHeight(0.55f)
                },
            )
            .graphicsLayer {
                compositingStrategy = CompositingStrategy.Offscreen
            }
            .drawWithContent {
                drawContent()
                drawRect(
                    brush = FlamingoSharpStageFadeBrush,
                    blendMode = BlendMode.DstIn,
                )
            },
    ) {
        if (proceduralContent != null) {
            proceduralContent()
        } else {
            settledArtworkUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = staticRevealAlpha },
                )
            }
            incomingArtworkUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    onSuccess = {
                        settledArtworkUrl = url
                        incomingArtworkUrl = null
                    },
                    onError = { incomingArtworkUrl = null },
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = staticRevealAlpha },
                )
            }
            CanvasArtworkPlayer(
                primaryUrl = canvasPrimaryUrl,
                fallbackUrl = canvasFallbackUrl,
                isPlaying = isPlaying,
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                loopSyncLeader = loopSyncLeader,
                visible = surfaceVisible,
                videoAlpha = videoVisualAlpha,
                onPlaybackAvailabilityChange = { available ->
                    if (!available) {
                        canvasFrameReady = false
                    }
                },
                onFirstFrameRendered = {
                    canvasFrameReady = true
                },
                refreshEpoch = refreshEpoch,
                modifier = Modifier.matchParentSize(),
            )
        }
    }
}

@Composable
private fun FlamingoProceduralStage(
    artworkUrl: String?,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        ProceduralArtworkFallback(
            artworkUrl = artworkUrl,
            isPlaying = isPlaying,
            modifier = Modifier.matchParentSize(),
        )
    }
}

@Composable
private fun FlamingoLandscapeStage(
    artworkUrl: String?,
    canvasPrimaryUrl: String?,
    canvasFallbackUrl: String?,
    isPlaying: Boolean,
    fullBleed: Boolean,
    artworkSize: Dp?,
    modifier: Modifier = Modifier,
) {
    val hasCanvas = !canvasPrimaryUrl.isNullOrBlank() || !canvasFallbackUrl.isNullOrBlank()

    Box(modifier = modifier) {
        if (fullBleed && hasCanvas) {
            var canvasRendering by remember(canvasPrimaryUrl, canvasFallbackUrl) {
                mutableStateOf(false)
            }
            val staticBaseAlpha by animateFloatAsState(
                targetValue = if (canvasRendering) 0f else 1f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                label = "flamingoLandscapeStaticCrossfade",
            )

            Box(
                modifier = Modifier.matchParentSize(),
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .graphicsLayer { alpha = staticBaseAlpha.coerceAtLeast(1f / 255f) },
                ) {
                    AsyncImage(
                        model = artworkUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .matchParentSize(),
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(Color.Black.copy(alpha = 0.55f)),
                    )
                }

                CanvasArtworkPlayer(
                    primaryUrl = canvasPrimaryUrl,
                    fallbackUrl = canvasFallbackUrl,
                    isPlaying = isPlaying,
                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
                    onPlaybackAvailabilityChange = { canvasRendering = it },
                    modifier = Modifier.matchParentSize(),
                )

                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(FlamingoLandscapeRightScrim),
                )
            }
        } else {
            val springSpec: AnimationSpec<Float> = remember("FlamingoLandscapeStage_springSpec") {
                SpringSpec(stiffness = 300f, dampingRatio = 1f, visibilityThreshold = 0.001f)
            }
            val tweenSpec: AnimationSpec<Float> = remember("FlamingoLandscapeStage_tweenSpec") {
                TweenSpec(durationMillis = 350, easing = EaseOutQuart)
            }
            val scale = animateFloatAsState(
                targetValue = if (isPlaying) 0f else 1f,
                animationSpec = if (isPlaying) springSpec else tweenSpec,
                visibilityThreshold = 0.001f,
            )
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FlamingoWrapper {
                    val dp = (7 + (27 * scale.value)).dp
                    ShadowImageWithCache(
                        dataLambda = { artworkUrl },
                        contentDescription = null,
                        modifier = Modifier
                            .size(artworkSize ?: 220.dp)
                            .graphicsLayer {
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .padding(start = dp, end = dp, bottom = dp),
                        imageQuality = ImageQuality.RAW,
                        shadowOverlay = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun FlamingoLandscapeTitleBlock(
    mediaMetadata: MediaMetadata,
    currentSongLiked: Boolean,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    onMoreClick: () -> Unit,
    onMorePositioned: (Rect) -> Unit,
    onArtistClick: () -> Unit = {},
    playerMenuOpen: Boolean = false,
) {
    val context = LocalContext.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier
                .weight(1f)
                .padding(end = 12.dp),
        ) {
            Text(
                text = mediaMetadata.title,
                fontSize = 19.5.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                text = mediaMetadata.artistNames(),
                fontSize = 18.5.sp,
                modifier = Modifier
                    .overlayEffect()

                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            FlamingoHaptics.click(context)
                            onArtistClick()
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.35f),
            )
        }

        FlamingoActionButtonsRow(
            mediaMetadata = mediaMetadata,
            playerConnection = playerConnection,
            navController = navController,
            state = state,
            bottomSheetPageState = bottomSheetPageState,
            currentSongLiked = currentSongLiked,
            onMoreClick = onMoreClick,
            onMorePositioned = onMorePositioned,
            playerMenuOpen = playerMenuOpen,
        )
    }
}

private data class FlamingoQueueDragInfo(
    val draggedItemUid: Any?,
    val destinationUid: Any?,
)

@Composable
private fun FlamingoPlayingList(
    playerConnection: PlayerConnection,
    queueWindows: List<Timeline.Window>,
    currentWindowIndex: Int,
    shuffleModeEnabled: Boolean,
    repeatMode: Int,
    bottomOverlayPx: () -> Float = { 0f },
    controlsVisible: () -> Boolean = { true },
    onControlsPoke: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val localPlayer = playerConnection.localPlayer
    val density = LocalDensity.current

    val controlsRevealFraction by animateFloatAsState(
        targetValue = if (controlsVisible()) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
        label = "flamingoQueueControlsReveal",
    )

    Spacer(modifier = Modifier.height(12.dp))

    val upNextCount = (queueWindows.size - currentWindowIndex - 1).coerceAtLeast(0)

    val bottomContentPadding = with(density) { (bottomOverlayPx() * controlsRevealFraction).toDp() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxSize(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 30.dp)
                .padding(top = 10.dp)
                .height(65.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                Text(
                    text = stringResource(id = R.string.flamingo_queue_header_title),
                    fontSize = 16.5.sp,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = stringResource(id = R.string.flamingo_queue_header_count, upNextCount),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .overlayEffect()
                        .alpha(0.35f),
                )
            }

            Row(
                modifier = Modifier
                    .overlayEffect()
                    .alpha(0.6f),
            ) {
                val dp = 36.dp
                FlamingoWrapper {
                    val shuffleBackgroundAlpha =
                        animateFloatAsState(targetValue = if (shuffleModeEnabled) 0.9f else 0f)
                    Box(
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    FlamingoHaptics.click(context)
                                    playerConnection.player.shuffleModeEnabled = !shuffleModeEnabled
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            )
                            .size(36.dp)
                            .background(
                                Color.White.copy(alpha = shuffleBackgroundAlpha.value),
                                shape = FlamingoSmoothCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlamingoWrapper {
                            val shuffleIconTint =
                                animateColorAsState(targetValue = if (shuffleModeEnabled) Color.Black else Color.White)
                            Icon(
                                painterResource(id = R.drawable.flamingo_np_shuffle),
                                contentDescription = null,
                                modifier = Modifier
                                    .size(dp),
                                tint = shuffleIconTint.value,
                            )
                        }
                    }
                }
                FlamingoWrapper {
                    val repeatHighlight =
                        repeatMode == REPEAT_MODE_ALL || repeatMode == REPEAT_MODE_ONE
                    val repeatBackgroundAlpha =
                        animateFloatAsState(targetValue = if (repeatHighlight) 0.9f else 0f)
                    Box(
                        modifier = Modifier
                            .clickable(
                                onClick = {
                                    FlamingoHaptics.click(context)
                                    val targetMode = when (repeatMode) {
                                        REPEAT_MODE_OFF -> REPEAT_MODE_ALL
                                        REPEAT_MODE_ALL -> REPEAT_MODE_ONE
                                        else -> REPEAT_MODE_OFF
                                    }
                                    playerConnection.player.repeatMode = targetMode
                                },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            )
                            .padding(start = 10.dp)
                            .size(36.dp)
                            .background(
                                Color.White.copy(alpha = repeatBackgroundAlpha.value),
                                shape = FlamingoSmoothCornerShape(10.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        FlamingoWrapper {
                            AnimatedContent(targetState = repeatMode, transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            }) { mode ->
                                when (mode) {
                                    REPEAT_MODE_ONE -> Icon(
                                        painterResource(id = R.drawable.flamingo_np_repeatone),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(dp),
                                        tint = animateColorAsState(
                                            targetValue = if (repeatHighlight) Color.Black else Color.White,
                                        ).value,
                                    )

                                    else -> Icon(
                                        painterResource(id = R.drawable.flamingo_np_repeat),
                                        contentDescription = null,
                                        modifier = Modifier
                                            .size(dp),
                                        tint = animateColorAsState(
                                            targetValue = if (repeatHighlight) Color.Black else Color.White,
                                        ).value,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        if (queueWindows.isEmpty()) {
            Column(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.flamingo_queue_empty),
                    contentDescription = null,
                    modifier = Modifier
                        .overlayEffect()
                        .size(70.dp)
                        .alpha(0.6f),
                )
                Text(
                    text = stringResource(id = R.string.flamingo_queue_empty_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier.padding(top = 18.dp, bottom = 12.dp),
                )
                FlamingoWrapper {
                    Text(
                        text = stringResource(id = R.string.flamingo_queue_empty_desc),
                        fontSize = 16.sp,
                        color = Color.White,
                        modifier = Modifier
                            .overlayEffect()
                            .alpha(0.4f),
                    )
                }
            }
        } else {
            val historyWindows =
                remember(currentWindowIndex, queueWindows) {
                    if (currentWindowIndex in 1..queueWindows.lastIndex) {
                        queueWindows.subList(0, currentWindowIndex).asReversed()
                    } else {
                        emptyList()
                    }
                }
            val currentWindow = queueWindows.getOrNull(currentWindowIndex)
            val currentDisplayIndex = 1 + historyWindows.size

            val state = rememberLazyListState(
                initialFirstVisibleItemIndex = currentDisplayIndex,
                initialFirstVisibleItemScrollOffset = 0,
            )

            val mutableQueueWindows =
                remember {
                    mutableStateListOf<Timeline.Window>().apply {
                        addAll(queueWindows.drop(currentWindowIndex + 1))
                    }
                }

            val currentPlayingUid =
                remember(currentWindowIndex, queueWindows) {
                    if (currentWindowIndex in queueWindows.indices) {
                        queueWindows[currentWindowIndex].uid
                    } else {
                        null
                    }
                }

            var dragInfo by remember { mutableStateOf<FlamingoQueueDragInfo?>(null) }
            var justCommittedDragUid by remember { mutableStateOf<Any?>(null) }

            val historySizeState = rememberUpdatedState(historyWindows.size)

            val reorderableState =
                rememberReorderableLazyListState(
                    lazyListState = state,
                ) onMove@{ from, to ->
                    val upNextBase = 3 + historySizeState.value
                    val fromMirror = from.index - upNextBase
                    val toMirror = to.index - upNextBase
                    if (fromMirror !in mutableQueueWindows.indices ||
                        toMirror !in mutableQueueWindows.indices
                    ) {
                        return@onMove
                    }

                    val draggedItemUid =
                        dragInfo?.draggedItemUid ?: mutableQueueWindows[fromMirror].uid
                    val actualFromQueueIndex =
                        mutableQueueWindows.indexOfFirst { it.uid == draggedItemUid }
                    if (actualFromQueueIndex == -1) return@onMove

                    mutableQueueWindows.move(actualFromQueueIndex, toMirror)

                    val destinationUid: Any? =
                        if (toMirror == 0) {
                            currentPlayingUid
                        } else {
                            mutableQueueWindows.getOrNull(toMirror - 1)?.uid
                        }
                    dragInfo = FlamingoQueueDragInfo(draggedItemUid, destinationUid)
                }

            LaunchedEffect(reorderableState) {
                androidx.compose.runtime.snapshotFlow { reorderableState.isAnyItemDragging }
                    .collect { dragging ->
                        if (dragging) {
                            FlamingoHaptics.longClick(context)
                        } else {
                            FlamingoHaptics.click(context)
                        }
                    }
            }

            LaunchedEffect(currentWindowIndex, queueWindows) {
                if (!state.isScrollInProgress && !reorderableState.isAnyItemDragging) {
                    runCatching { state.scrollToItem(currentDisplayIndex) }
                }
            }

            LaunchedEffect(queueWindows, currentWindowIndex, reorderableState.isAnyItemDragging) {
                if (reorderableState.isAnyItemDragging) return@LaunchedEffect

                val completedDrag = dragInfo
                if (completedDrag != null) {
                    val sourceIndex =
                        queueWindows.indexOfFirst { it.uid == completedDrag.draggedItemUid }
                    val destinationAnchorIndex =
                        queueWindows.indexOfFirst { it.uid == completedDrag.destinationUid }
                    dragInfo = null

                    if (sourceIndex != -1) {
                        val destinationIndex =
                            if (destinationAnchorIndex == -1) {
                                currentWindowIndex + 1
                            } else if (sourceIndex < destinationAnchorIndex) {
                                destinationAnchorIndex
                            } else {
                                (destinationAnchorIndex + 1).coerceAtMost(queueWindows.lastIndex)
                            }

                        if (sourceIndex != destinationIndex) {
                            justCommittedDragUid = completedDrag.draggedItemUid
                            if (!shuffleModeEnabled) {
                                playerConnection.player.moveMediaItem(sourceIndex, destinationIndex)
                            } else {
                                localPlayer.setShuffleOrder(
                                    DefaultShuffleOrder(
                                        queueWindows
                                            .map { it.firstPeriodIndex }
                                            .toMutableList()
                                            .move(sourceIndex, destinationIndex)
                                            .toIntArray(),
                                        System.currentTimeMillis(),
                                    ),
                                )
                            }
                            return@LaunchedEffect
                        }
                    }
                }

                if (justCommittedDragUid != null) {
                    justCommittedDragUid = null
                    return@LaunchedEffect
                }

                Snapshot.withMutableSnapshot {
                    mutableQueueWindows.clear()
                    val startIndex = currentWindowIndex + 1
                    mutableQueueWindows.addAll(queueWindows.drop(startIndex))
                }
            }

            val controlsPokeConnection =
                remember(onControlsPoke) {
                    object : NestedScrollConnection {
                        override fun onPreScroll(
                            available: Offset,
                            source: NestedScrollSource,
                        ): Offset {

                            if (source == NestedScrollSource.UserInput &&
                                kotlin.math.abs(available.y) > 4f
                            ) {
                                onControlsPoke()
                            }
                            return Offset.Zero
                        }
                    }
                }

            FlamingoWrapper {
                LazyColumn(
                    state = state,
                    contentPadding = PaddingValues(bottom = bottomContentPadding),
                    modifier = Modifier
                        .fillMaxSize()
                        .nestedScroll(controlsPokeConnection)
                        .graphicsLayer {
                            compositingStrategy = CompositingStrategy.Offscreen
                        }
                        .drawWithCache {
                            val topFadePx = (size.height * 0.06f).coerceAtLeast(24.dp.toPx())
                            val fadeBandPx = 56.dp.toPx()
                            val overlayPx = bottomOverlayPx() * controlsRevealFraction
                            val transparentFromPx =
                                (size.height - overlayPx + 20.dp.toPx() * controlsRevealFraction)
                                    .coerceAtLeast(topFadePx + fadeBandPx)
                            val fadeStartPx = transparentFromPx - fadeBandPx
                            val fadeBrush =
                                Brush.verticalGradient(
                                    0f to Color.Transparent,
                                    topFadePx / size.height to Color.Black,
                                    fadeStartPx / size.height to Color.Black,
                                    transparentFromPx / size.height to Color.Transparent,
                                    1f to Color.Transparent,
                                )
                            onDrawWithContent {
                                drawContent()
                                drawRect(
                                    brush = fadeBrush,
                                    blendMode = BlendMode.DstIn,
                                )
                            }
                        },
                ) {
                    item("blank_before") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    items(
                        items = historyWindows,
                        key = { window -> "history_" + window.uid.hashCode() },
                    ) { window ->
                        Box(Modifier.graphicsLayer { alpha = 0.55f }) {
                            FlamingoQueueMusicListItem(
                                window = window,
                                queueWindows = queueWindows,
                                currentWindowIndex = currentWindowIndex,
                                reorderEnabled = false,
                                isDragging = false,
                                reorderHandleModifier = Modifier,
                                onMoveToNextQueue = null,
                                onRemove = null,
                            ) {
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            }
                        }
                    }

                    if (currentWindow != null) {
                        item(key = "current_" + currentWindow.uid.hashCode()) {
                            FlamingoQueueMusicListItem(
                                window = currentWindow,
                                queueWindows = queueWindows,
                                currentWindowIndex = currentWindowIndex,
                                reorderEnabled = false,
                                isDragging = false,
                                reorderHandleModifier = Modifier,
                                onMoveToNextQueue = null,
                                onRemove = null,
                            ) {
                                playerConnection.player.seekToDefaultPosition(currentWindow.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            }
                        }
                    }

                    item("up_next_header") {
                        QueueSectionHeader(title = stringResource(id = R.string.flamingo_queue_up_next))
                    }

                    itemsIndexed(
                        items = mutableQueueWindows,
                        key = { _, window -> window.uid.hashCode() },
                    ) { _, window ->
                        ReorderableItem(reorderableState, key = window.uid.hashCode()) { isDragging ->
                            FlamingoQueueMusicListItem(
                                window = window,
                                queueWindows = queueWindows,
                                currentWindowIndex = currentWindowIndex,
                                reorderEnabled = mutableQueueWindows.size > 1,
                                isDragging = isDragging,
                                reorderHandleModifier = Modifier.draggableHandle(),
                                onMoveToNextQueue = {
                                    val currentTimelineIndex = currentWindow?.firstPeriodIndex ?: -1
                                    if (window.firstPeriodIndex > 0 && currentTimelineIndex >= 0) {
                                        playerConnection.player.moveMediaItem(
                                            window.firstPeriodIndex,
                                            currentTimelineIndex + 1,
                                        )
                                        true
                                    } else {
                                        false
                                    }
                                },
                                onRemove = {
                                    playerConnection.player.removeMediaItem(window.firstPeriodIndex)
                                    true
                                },
                            ) {
                                playerConnection.player.seekToDefaultPosition(window.firstPeriodIndex)
                                playerConnection.player.playWhenReady = true
                            }
                        }
                    }

                    item("blank_after") {
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun QueueSectionHeader(
    title: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 30.dp)
            .padding(top = 12.dp, bottom = 6.dp)
            .overlayEffect(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Medium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FlamingoQueueMusicListItem(
    window: Timeline.Window,
    queueWindows: List<Timeline.Window>,
    currentWindowIndex: Int,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    reorderHandleModifier: Modifier,
    onMoveToNextQueue: (suspend () -> Boolean)?,
    onRemove: (suspend () -> Boolean)?,
    itemClick: () -> Unit,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val coroutineScope = rememberCoroutineScope()
    val addedToQueueToast = stringResource(id = R.string.flamingo_queue_added_toast)
    val music = window.mediaItem.metadata
    var rowWidthPx by remember(window.uid) {
        mutableFloatStateOf(0f)
    }
    var rowHeightPx by remember(window.uid) {
        mutableFloatStateOf(0f)
    }
    var swipeOffsetPx by remember(window.uid) {
        mutableFloatStateOf(0f)
    }
    var deleteHeightPx by remember(window.uid) {
        mutableFloatStateOf(0f)
    }
    var resetAnimationJob by remember(window.uid) {
        mutableStateOf<Job?>(null)
    }
    var deleteAnimating by remember(window.uid) {
        mutableStateOf(false)
    }
    var deleteCollapsing by remember(window.uid) {
        mutableStateOf(false)
    }

    val swipeRightEnabled = onMoveToNextQueue != null
    val swipeLeftEnabled = onRemove != null
    val triggerOffsetPx = rowWidthPx * 0.20f
    val minSwipeOffsetPx = if (swipeLeftEnabled) -rowWidthPx else 0f
    val maxSwipeOffsetPx = if (swipeRightEnabled) rowWidthPx else 0f
    val absoluteSwipeOffsetPx = if (swipeOffsetPx < 0f) -swipeOffsetPx else swipeOffsetPx
    val swipeProgress = if (rowWidthPx > 0f) {
        (absoluteSwipeOffsetPx / rowWidthPx).coerceIn(0f, 1f)
    } else {
        0f
    }
    val rowHeight = with(density) {
        rowHeightPx.toDp()
    }
    val swipeRevealWidth = with(density) {
        absoluteSwipeOffsetPx.toDp()
    }
    val deleteHeight = with(density) {
        deleteHeightPx.coerceAtLeast(0f).toDp()
    }

    val swipeModifier = if ((swipeRightEnabled || swipeLeftEnabled) && !deleteAnimating) {
        Modifier.draggable(
            orientation = Orientation.Horizontal,
            state = rememberDraggableState { delta ->
                if (rowWidthPx <= 0f || deleteAnimating) {
                    return@rememberDraggableState
                }

                resetAnimationJob?.cancel()

                val wasPastAddThreshold = swipeOffsetPx >= triggerOffsetPx
                val wasPastDeleteThreshold = swipeOffsetPx <= -triggerOffsetPx
                swipeOffsetPx = (swipeOffsetPx + delta).coerceIn(minSwipeOffsetPx, maxSwipeOffsetPx)
                val isPastAddThreshold = swipeOffsetPx >= triggerOffsetPx
                val isPastDeleteThreshold = swipeOffsetPx <= -triggerOffsetPx

                if (wasPastAddThreshold != isPastAddThreshold || wasPastDeleteThreshold != isPastDeleteThreshold) {
                    if (isPastAddThreshold || isPastDeleteThreshold) {
                        FlamingoHaptics.longClick(context)
                    } else {
                        FlamingoHaptics.click(context)
                    }
                }
            },
            onDragStopped = {
                val shouldMoveToNextQueue = triggerOffsetPx > 0f && swipeOffsetPx >= triggerOffsetPx
                val shouldRemove = triggerOffsetPx > 0f && swipeOffsetPx <= -triggerOffsetPx

                resetAnimationJob?.cancel()
                resetAnimationJob = coroutineScope.launch {
                    if (shouldRemove && onRemove != null) {
                        deleteAnimating = true
                        deleteHeightPx = if (rowHeightPx > 0f) {
                            rowHeightPx
                        } else {
                            with(density) { QueueRowHeight.toPx() }
                        }

                        androidx.compose.animation.core.animate(
                            initialValue = swipeOffsetPx,
                            targetValue = -rowWidthPx,
                            animationSpec = tween(
                                durationMillis = 110,
                                easing = EaseOutQuart,
                            ),
                        ) { value, _ ->
                            swipeOffsetPx = value
                        }

                        swipeOffsetPx = 0f
                        deleteCollapsing = true

                        androidx.compose.animation.core.animate(
                            initialValue = deleteHeightPx,
                            targetValue = 0f,
                            animationSpec = tween(
                                durationMillis = 170,
                                easing = EaseOutQuart,
                            ),
                        ) { value, _ ->
                            deleteHeightPx = value
                        }

                        onRemove.invoke()
                        return@launch
                    }

                    if (shouldMoveToNextQueue && onMoveToNextQueue?.invoke() == true) {
                        Toast.makeText(context, addedToQueueToast, Toast.LENGTH_SHORT).show()
                    }

                    val animationStart = swipeOffsetPx

                    androidx.compose.animation.core.animate(
                        initialValue = animationStart,
                        targetValue = 0f,
                        animationSpec = SpringSpec(
                            dampingRatio = 0.72f,
                            stiffness = 420f,
                            visibilityThreshold = 0.5f,
                        ),
                    ) { value, _ ->
                        swipeOffsetPx = value
                    }
                }
            },
        )
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .zIndex(if (isDragging || deleteAnimating) 1f else 0f)
            .then(if (deleteCollapsing) Modifier.height(deleteHeight) else Modifier)
            .onSizeChanged {
                rowWidthPx = it.width.toFloat()
                if (!deleteCollapsing) {
                    rowHeightPx = it.height.toFloat()
                }
            },
    ) {
        if (deleteCollapsing) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(deleteHeight)
                    .background(Color(0xFFD32F2F)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.flamingo_swipe_delete),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(28.dp),
                )
            }
        } else if (swipeRightEnabled && swipeOffsetPx > 0f) {
            Box(
                modifier = Modifier
                    .width(swipeRevealWidth)
                    .height(rowHeight)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.flamingo_swipe_queue),
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            val iconScale = 0.82f + (swipeProgress * 0.18f)
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }

        if (!deleteCollapsing && swipeLeftEnabled && swipeOffsetPx < 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(swipeRevealWidth)
                    .height(rowHeight)
                    .background(Color(0xFFD32F2F)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.flamingo_swipe_delete),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .size(28.dp)
                        .graphicsLayer {
                            val iconScale = 0.82f + (swipeProgress * 0.18f)
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                )
            }
        }

        if (!deleteCollapsing) {
            FlamingoSmallMusicListItem(
                music = music,
                reorderEnabled = reorderEnabled,
                isDragging = isDragging,
                modifier = Modifier
                    .graphicsLayer {
                        translationX = swipeOffsetPx
                    }
                    .then(swipeModifier),
                reorderHandleModifier = reorderHandleModifier,
                itemClick = itemClick,
            )
        }
    }
}

@Composable
private fun FlamingoSmallMusicListItem(
    music: MediaMetadata?,
    reorderEnabled: Boolean,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
    reorderHandleModifier: Modifier,
    itemClick: () -> Unit,
) {
    val draggedItemBackground by animateColorAsState(
        targetValue = if (isDragging) {
            Color.White.copy(alpha = 0.08f)
        } else {
            Color.Transparent
        },
        label = "FlamingoQueueDraggedItemBackground",
    )
    Surface(
        modifier = modifier
            .height(QueueRowHeight)
            .fillMaxWidth(),
        color = draggedItemBackground,
        contentColor = Color.White,
        shape = QueueDraggingItemShape,
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .clickable {
                    itemClick()
                }
                .padding(horizontal = 30.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShadowImageWithCache(
                dataLambda = { music?.thumbnailUrl },
                contentDescription = null,
                modifier = Modifier.size(48.dp),
                cornerRadius = 4.dp,
                shadowAlpha = 0f,
                imageQuality = ImageQuality.LOW,
            )

            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 12.dp),
            ) {
                Text(
                    text = music?.title ?: "",
                    modifier = Modifier.padding(bottom = 1.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 16.sp,
                    lineHeight = 16.sp,
                )

                Text(
                    text = music?.artistNames() ?: "",
                    modifier = Modifier.alpha(0.5f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 11.5.sp,
                    lineHeight = 11.5.sp,
                )
            }

            if (reorderEnabled) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .then(reorderHandleModifier),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.flamingo_queue_reorder),
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.34f),
                        modifier = Modifier.size(24.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlamingoActionButtonsRow(
    mediaMetadata: MediaMetadata,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    currentSongLiked: Boolean,
    onMoreClick: () -> Unit,
    onMorePositioned: (Rect) -> Unit = {},
    playerMenuOpen: Boolean = false,
) {
    Row(
        modifier = Modifier
            .overlayEffect(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val dp = 28.dp

        val context = LocalContext.current
        val menuState = LocalMenuState.current
        val overflowOpen = menuState.isVisible || playerMenuOpen

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    onClick = {
                        FlamingoHaptics.click(context)
                        playerConnection.toggleLike()
                    },
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            contentAlignment = Alignment.CenterEnd,
        ) {
            AnimatedContent(
                targetState = currentSongLiked,
                transitionSpec = {
                    fadeIn() togetherWith fadeOut()
                },
            ) { liked ->
                if (liked) {
                    Icon(
                        painterResource(id = R.drawable.flamingo_np_favorited),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dp),
                    )
                } else {
                    Icon(
                        painterResource(id = R.drawable.flamingo_np_favorite),
                        contentDescription = null,
                        modifier = Modifier
                            .overlayEffect()
                            .size(dp),
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(2.dp))

        Box(
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .clickable(
                    onClick = onMoreClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                )
                .let { base ->
                    if (onMorePositioned != null) {
                        base.onGloballyPositioned { coords ->
                            onMorePositioned(coords.boundsInRoot())
                        }
                    } else {
                        base
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            AnimatedContent(
                targetState = overflowOpen,
                transitionSpec = {
                    fadeIn(animationSpec = tween(durationMillis = 300)) togetherWith
                        fadeOut(animationSpec = tween(durationMillis = 300))
                },
            ) { menuOpen ->
                if (menuOpen) {
                    Icon(
                        painterResource(id = R.drawable.flamingo_np_more_fill),
                        contentDescription = null,
                        modifier = Modifier
                            .size(dp),
                    )
                } else {
                    Icon(
                        painterResource(id = R.drawable.flamingo_np_more),
                        contentDescription = null,
                        modifier = Modifier
                            .overlayEffect()
                            .size(dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun FlamingoPlayingBar(
    modifier: Modifier,
    artworkUrl: String?,
    mediaMetadata: MediaMetadata,
    playerConnection: PlayerConnection,
    navController: NavController,
    state: BottomSheetState,
    bottomSheetPageState: BottomSheetPageState,
    currentSongLiked: Boolean,
    onAlbumClick: () -> Unit,
    onMoreClick: () -> Unit,
    onMorePositioned: (Rect) -> Unit,
    onArtistClick: () -> Unit = {},
    playerMenuOpen: Boolean = false,
) = FlamingoWrapper {
    val context = LocalContext.current
    Row(
        Modifier
            .fillMaxWidth()

            .padding(horizontal = 16.dp)
            .padding(top = 22.dp)
            .height(70.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShadowImageWithCache(
            dataLambda = { artworkUrl },
            contentDescription = null,
            modifier = modifier
                .size(69.dp)
                .clickable(
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                    onClick = {
                        onAlbumClick()
                    },
                ),
            cornerRadius = 5.dp,
            imageQuality = ImageQuality.LOW,
            shadowType = ShadowType.Small,
            shadowOverlay = true,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(start = 12.dp, end = 15.dp),
        ) {
            Text(
                text = mediaMetadata.title,
                fontSize = 16.5.sp,
                maxLines = 1,
                fontWeight = FontWeight.Medium,
                lineHeight = 16.5.sp,
                modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE),
            )
            Text(
                text = mediaMetadata.artistNames(),
                fontSize = 15.sp,
                modifier = Modifier
                    .overlayEffect()

                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = {
                            FlamingoHaptics.click(context)
                            onArtistClick()
                        },
                    ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = Color.White.copy(alpha = 0.35f),
            )
        }

        FlamingoWrapper {
            FlamingoActionButtonsRow(
                mediaMetadata = mediaMetadata,
                playerConnection = playerConnection,
                navController = navController,
                state = state,
                bottomSheetPageState = bottomSheetPageState,
                currentSongLiked = currentSongLiked,
                onMoreClick = onMoreClick,
                onMorePositioned = onMorePositioned,
                playerMenuOpen = playerMenuOpen,
            )
        }
    }
}

@Composable
private fun RowScope.FlamingoAirPlay() {
    val contextCompose = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val connectedDevices =
        remember("FlamingoAirPlay_connectedDevices") { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    val audioDeviceName = remember("FlamingoAirPlay_audioDeviceName") { mutableStateOf("") }
    val showName = remember("FlamingoAirPlay_showName") { mutableStateOf(false) }

    FlamingoWrapper {
        DisposableEffect(Unit) {
            val filter = IntentFilter(BluetoothDevice.ACTION_ACL_CONNECTED).apply {
                addAction(BluetoothDevice.ACTION_ACL_DISCONNECTED)
            }
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(context: Context?, intent: Intent?) {
                    val action = intent?.action

                    if (action == BluetoothDevice.ACTION_ACL_CONNECTED ||
                        action == BluetoothDevice.ACTION_ACL_DISCONNECTED
                    ) {
                        if (ActivityCompat.checkSelfPermission(
                                contextCompose,
                                Manifest.permission.BLUETOOTH_CONNECT,
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            return
                        }
                        connectedDevices.value =
                            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

                        val thisName =
                            connectedDevices.value.firstOrNull {
                                it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO &&
                                    it.isConnectedCompat()
                            }?.aliasCompat()
                        showName.value = thisName != null
                        if (thisName != null) {
                            audioDeviceName.value = thisName.trim()
                        }
                    }
                }
            }
            ContextCompat.registerReceiver(
                contextCompose,
                receiver,
                filter,
                ContextCompat.RECEIVER_EXPORTED,
            )

            if (ActivityCompat.checkSelfPermission(
                    contextCompose,
                    Manifest.permission.BLUETOOTH_CONNECT,
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                connectedDevices.value = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
                val thisName =
                    connectedDevices.value.firstOrNull {
                        it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.AUDIO_VIDEO &&
                            it.isConnectedCompat()
                    }?.aliasCompat()
                showName.value = thisName != null
                if (thisName != null) {
                    audioDeviceName.value = thisName.trim()
                }
            }

            onDispose {
                runCatching {
                    contextCompose.unregisterReceiver(receiver)
                }
            }
        }
    }

    FlamingoWrapper {
        val context = LocalContext.current

        val castAction = rememberCastPlayerMenuAction()
        val onOutputClick: () -> Unit = castAction?.onClick ?: {
            runCatching {
                context.startActivity(Intent("android.settings.panel.action.MEDIA_OUTPUT"))
            }
        }

        val navBottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Column(
            modifier = Modifier
                .heightIn(min = 53.dp)
                .height(navBottomInset + 48.dp)
                .weight(1f)
                .clickable(
                    onClick = onOutputClick,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() },
                ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(modifier = Modifier.height(36.dp), contentAlignment = Alignment.Center) {
                AnimatedContent(
                    targetState = showName.value,
                    transitionSpec = {
                        (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                            scaleOut(
                                targetScale = 0.3f,
                            ) + fadeOut(),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) { connected ->
                    if (connected) {
                        Icon(
                            painterResource(id = R.drawable.flamingo_earphone),
                            contentDescription = null,
                            modifier = Modifier
                                .size(27.dp),
                        )
                    } else {
                        Icon(
                            painterResource(id = R.drawable.flamingo_np_airplay),
                            contentDescription = null,
                            modifier = Modifier
                                .size(21.5.dp),
                        )
                    }
                }
            }

            AnimatedVisibility(
                showName.value,
                enter = scaleIn(initialScale = 0.3f) + fadeIn(),
                exit = scaleOut(targetScale = 0.3f) + fadeOut(),
            ) {
                Text(
                    text = audioDeviceName.value,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    lineHeight = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private fun BluetoothDevice.isConnectedCompat(): Boolean {
    return runCatching {
        val isConnectedMethod =
            BluetoothDevice::class.java.getMethod("isConnected")
        isConnectedMethod.isAccessible = true
        isConnectedMethod.invoke(this) as Boolean
    }.getOrDefault(false)
}

private fun BluetoothDevice.aliasCompat(): String? {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        runCatching { alias }.getOrNull() ?: name
    } else {
        name
    }
}

@Composable
private fun FlamingoPlayerControl(
    isPlayingLambda: () -> Boolean,
    playbackState: Int,
    positionProvider: () -> Long,
    durationProvider: () -> Long,
    playerConnection: PlayerConnection,
    currentFormat: FormatEntity?,
    showVolumeBar: Boolean,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    nowPage: () -> String,
    onLyrics: () -> Unit,
    onPlaylist: () -> Unit,
    onSlider: () -> Unit,
    onQualityClick: () -> Unit = {},
    onSliderValueChange: (Long) -> Unit,
    onSliderValueChangeFinished: () -> Unit,
    modifier: Modifier,
) {
    val playingDuration = rememberSaveable(key = "FlamingoPlayerControl_playingDuration") {
        mutableLongStateOf(0L)
    }
    val playingPosition = rememberSaveable(key = "FlamingoPlayerControl_playingPosition") {
        mutableLongStateOf(0L)
    }
    val context = LocalContext.current
    val crossfadeMixing by playerConnection.service.crossfadeMixing.collectAsStateWithLifecycle()
    val automixMixing by moe.rukamori.archivetune.playback.automix.AutoMixUiState.mixing.collectAsStateWithLifecycle()
    val sliderMixing = crossfadeMixing || automixMixing
    val playedTime = rememberSaveable(key = "FlamingoPlayerControl_playedTime") { mutableStateOf("0:00") }
    val remainingTime =
        rememberSaveable(key = "FlamingoPlayerControl_remainingTime") { mutableStateOf("-0:00") }
    val sliderPosition = remember("FlamingoPlayerControl_sliderPosition") { mutableFloatStateOf(0f) }
    val isSliding = remember("FlamingoPlayerControl_isSliding") {
        mutableStateOf(false)
    }

    val latestDurationProvider = rememberUpdatedState(durationProvider)
    val latestPositionProvider = rememberUpdatedState(positionProvider)

    val player = playerConnection.player

    FlamingoWrapper {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 25.dp)
                .padding(bottom = 15.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            FlamingoWrapper {
                FlamingoWrapper {
                    val lifecycleState =
                        LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsStateWithLifecycle()

                    LaunchedEffect(Unit) {
                        var lastPosition = 0L
                        while (true) {
                            if (lifecycleState.value.isAtLeast(Lifecycle.State.RESUMED)) {
                                playingDuration.longValue = latestDurationProvider.value()
                                playingPosition.longValue = latestPositionProvider.value()

                                if (!isSliding.value && playingDuration.longValue > 0L) {
                                    val totalSeconds =
                                        playingPosition.longValue.coerceAtLeast(0) / 1000
                                    if (totalSeconds != lastPosition) {
                                        playedTime.value = formatTime(totalSeconds)

                                        sliderPosition.floatValue =
                                            playingPosition.longValue.coerceAtLeast(0).toFloat()

                                        val remainingSeconds =
                                            playingDuration.longValue.coerceAtLeast(0) / 1000 - totalSeconds
                                        remainingTime.value = "-${formatTime(remainingSeconds)}"
                                        lastPosition = totalSeconds
                                    }
                                }
                            }

                            delay(1000)
                        }
                    }
                }

                FlamingoWrapper {
                    val sliderEngage by animateFloatAsState(
                        targetValue = if (isSliding.value) 1f else 0f,
                        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                        label = "flamingoSeekEngage",
                    )
                    Slider(
                        value = sliderPosition.floatValue,
                        onValueChange = { newValue ->
                            isSliding.value = true

                            sliderPosition.floatValue = newValue
                            val newTotalSeconds = newValue.toLong() / 1000
                            playedTime.value = formatTime(newTotalSeconds)

                            val newRemainingSeconds =
                                playingDuration.longValue / 1000 - newTotalSeconds
                            remainingTime.value = "-${formatTime(newRemainingSeconds)}"

                            onSliderValueChange(newValue.toLong())
                            onSlider()
                        },
                        onValueChangeFinished = {
                            FlamingoHaptics.longClick(context)
                            player.seekTo(sliderPosition.floatValue.toLong())
                            onSliderValueChangeFinished()
                            isSliding.value = false
                        },
                        valueRange = 0f..playingDuration.longValue.toFloat().coerceAtLeast(0f),
                        colors = SliderDefaults.colors(
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color(0x0DFFFFFF),
                        ),
                        modifier = Modifier
                            .overlayEffect()
                            .alpha(0.85f)
                            .height(18.dp),
                        thumb = {
                            Spacer(modifier = Modifier.size(0.dp))
                        },
                        track = { _ ->
                            FlamingoTrack(
                                activeFraction = if (playingDuration.longValue > 0L) {
                                    (sliderPosition.floatValue / playingDuration.longValue).coerceIn(0f, 1f)
                                } else {
                                    0f
                                },
                                height = 7.dp + 4.dp * sliderEngage,
                                mixing = sliderMixing,
                            )
                        },
                    )
                }

                FlamingoWrapper {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp, horizontal = 7.dp)
                            .height(22.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = playedTime.value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.3.sp,
                                color = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.overlayEffect(),
                            )
                            Text(
                                text = remainingTime.value,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 0.3.sp,
                                color = Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.overlayEffect(),
                            )
                        }

                        FlamingoQualityIndicator(
                            currentFormat = currentFormat,
                            onQualityClick = onQualityClick,
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(61.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false),
                                        onClick = {
                                            FlamingoHaptics.click(context)
                                            player.seekToPreviousMediaItem()
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.flamingo_np_rewind),
                                    contentDescription = "Previous",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(43.dp))

                            Box(
                                modifier = Modifier
                                    .size(58.5.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false),
                                        onClick = {
                                            FlamingoHaptics.click(context)
                                            if (playbackState == Player.STATE_ENDED) {
                                                player.seekTo(0, 0)
                                                player.playWhenReady = true
                                            } else {
                                                player.playWhenReady = !player.playWhenReady
                                            }
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                AnimatedContent(
                                    targetState = isPlayingLambda(),
                                    transitionSpec = {
                                        (scaleIn(initialScale = 0.3f) + fadeIn()).togetherWith(
                                            scaleOut(
                                                targetScale = 0.3f,
                                            ) + fadeOut(),
                                        )
                                    },
                                ) { playing ->
                                    if (playing) {
                                        Icon(
                                            painterResource(id = R.drawable.flamingo_np_pause),
                                            contentDescription = "Pause",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(10.dp),
                                        )
                                    } else {
                                        Icon(
                                            painterResource(id = R.drawable.flamingo_np_play),
                                            contentDescription = "Play",
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(9.dp),
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.width(43.dp))
                            Box(
                                modifier = Modifier
                                    .size(61.dp)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = ripple(bounded = false),
                                        onClick = {
                                            FlamingoHaptics.click(context)
                                            player.seekToNextMediaItem()
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painterResource(id = R.drawable.flamingo_np_fforward),
                                    contentDescription = "Next",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(10.dp),
                                )
                            }
                        }
                    }
                }
            }

            FlamingoWrapper {
                if (showVolumeBar) {
                    FlamingoVolumeSlider(
                        volume = volume,
                        onVolumeChange = onVolumeChange,
                        onSlider = onSlider,
                    )
                }
            }

            FlamingoWrapper {
                Row(
                    modifier = Modifier
                        .overlayEffect()
                        .fillMaxWidth()
                        .alpha(0.4f),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    val dp = 32.dp
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .weight(1f)
                            .clickable(
                                onClick = { onLyrics() },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = nowPage() == FlamingoPage.Lyric,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                        ) { lyricsOn ->
                            if (lyricsOn) {
                                Icon(
                                    painterResource(id = R.drawable.flamingo_np_lyricson),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp),
                                )
                            } else {
                                Icon(
                                    painterResource(id = R.drawable.flamingo_np_lyrics),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.weight(0.1f))

                    FlamingoAirPlay()

                    Spacer(modifier = Modifier.weight(0.1f))

                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .weight(1f)
                            .clickable(
                                onClick = { onPlaylist() },
                                indication = null,
                                interactionSource = remember { MutableInteractionSource() },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        AnimatedContent(
                            targetState = nowPage() == FlamingoPage.PlayingList,
                            transitionSpec = {
                                fadeIn() togetherWith fadeOut()
                            },
                        ) { queueOn ->
                            if (queueOn) {
                                Icon(
                                    painterResource(id = R.drawable.flamingo_np_queueon),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp),
                                )
                            } else {
                                Icon(
                                    painterResource(id = R.drawable.flamingo_np_queue),
                                    contentDescription = null,
                                    modifier = Modifier
                                        .size(dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FlamingoVolumeSlider(
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    onSlider: () -> Unit,
) {
    val context = LocalContext.current
    val sliderPosition =
        remember("FlamingoVolumeSlider_sliderPosition") { mutableFloatStateOf(volume) }
    val sliding = remember("FlamingoVolumeSlider_sliding") {
        mutableStateOf(false)
    }

    LaunchedEffect(volume) {
        if (!sliding.value) {
            sliderPosition.floatValue = volume
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .padding(end = 1.5.dp)
            .padding(horizontal = 8.dp)
            .padding(top = 4.dp, bottom = 2.5.dp)
            .overlayEffect()
            .alpha(0.45f),
    ) {
        Icon(
            painter = painterResource(id = R.drawable.flamingo_np_volume),
            contentDescription = "Mute",
            modifier = Modifier.size(20.dp),
        )

        FlamingoWrapper {
            val animatedProgress = if (sliding.value) {
                sliderPosition
            } else {
                animateFloatAsState(
                    targetValue = sliderPosition.floatValue,
                    animationSpec = androidx.compose.material3.ProgressIndicatorDefaults.ProgressAnimationSpec,
                    visibilityThreshold = 0.0001f,
                )
            }
            val volumeEngage by animateFloatAsState(
                targetValue = if (sliding.value) 1f else 0f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
                label = "flamingoVolumeEngage",
            )

            Slider(
                value = animatedProgress.value,
                onValueChange = { newValue ->
                    sliding.value = true
                    sliderPosition.floatValue = newValue
                    onVolumeChange(newValue)
                    onSlider()
                },
                valueRange = 0f..1f,
                colors = SliderDefaults.colors(
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color(0x0DFFFFFF),
                ),
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 1.5.dp, end = 5.dp),
                thumb = {
                    Spacer(modifier = Modifier.size(0.dp))
                },
                track = { _ ->
                    FlamingoTrack(
                        activeFraction = animatedProgress.value.coerceIn(0f, 1f),
                        height = 7.dp + 4.dp * volumeEngage,
                    )
                },
                onValueChangeFinished = {
                    FlamingoHaptics.longClick(context)
                    sliding.value = false
                },
            )
        }
        Icon(
            painter = painterResource(id = R.drawable.flamingo_np_volume_full),
            contentDescription = "Max Volume",
            modifier = Modifier.size(20.dp),
        )
    }
}

private val FlamingoTrackBackingColor = Color(0x4D000000)

@Composable
private fun FlamingoTrack(
    activeFraction: Float,
    modifier: Modifier = Modifier,
    height: Dp,
    mixing: Boolean = false,
) = FlamingoWrapper {
    val inactiveTrackColor = Color.White.copy(alpha = 0.35f)
    val activeTrackColor = Color.White
    val fillFactor by animateFloatAsState(
        targetValue = if (mixing) 0f else 1f,
        animationSpec = tween(durationMillis = FlamingoMixMorphMs, easing = FastOutSlowInEasing),
        label = "flamingoSeekFillFactor",
    )
    Box(
        modifier
            .fillMaxWidth()
            .height(height),
    ) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height),
        ) {
            val isRtl = layoutDirection == LayoutDirection.Rtl
            val sliderLeft = Offset(0f, center.y)
            val sliderRight = Offset(size.width, center.y)
            val sliderStart = if (isRtl) sliderRight else sliderLeft
            val sliderEnd = if (isRtl) sliderLeft else sliderRight
            val trackStrokeWidth = height.toPx()

            drawRoundRect(
                color = FlamingoTrackBackingColor,
                topLeft = Offset(0f, center.y - trackStrokeWidth / 2f),
                size = Size(size.width, trackStrokeWidth),
                cornerRadius = CornerRadius(trackStrokeWidth / 2f),
            )
            drawRoundRect(
                color = inactiveTrackColor,
                topLeft = Offset(0f, center.y - trackStrokeWidth / 2f),
                size = Size(size.width, trackStrokeWidth),
                cornerRadius = CornerRadius(trackStrokeWidth / 2f),
            )
            val sliderValueEnd = Offset(
                sliderStart.x +
                    (sliderEnd.x - sliderStart.x) * activeFraction * fillFactor,
                center.y,
            )

            val sliderValueStart = Offset(
                sliderStart.x +
                    (sliderEnd.x - sliderStart.x) * 0f,
                center.y,
            )

            val activeLeft = minOf(sliderValueStart.x, sliderValueEnd.x)
            val activeWidth = kotlin.math.abs(sliderValueEnd.x - sliderValueStart.x)
            if (activeWidth > 0f) {
                drawRoundRect(
                    color = activeTrackColor,
                    topLeft = Offset(activeLeft, center.y - trackStrokeWidth / 2f),
                    size = Size(activeWidth, trackStrokeWidth),
                    cornerRadius = CornerRadius(trackStrokeWidth / 2f),
                )
            }
        }
        AnimatedVisibility(
            visible = mixing,
            enter = fadeIn(tween(durationMillis = FlamingoMixMorphMs, easing = FastOutSlowInEasing)) +
                scaleIn(
                    animationSpec = tween(durationMillis = FlamingoMixMorphMs, easing = FastOutSlowInEasing),
                    initialScale = 0f,
                    transformOrigin = TransformOrigin(0f, 0.5f),
                ),
            exit = fadeOut(tween(durationMillis = FlamingoMixMorphMs, easing = FastOutSlowInEasing)),
        ) {
            FlamingoMixSheen(height = height, color = activeTrackColor)
        }
    }
}

@Composable
private fun FlamingoMixSheen(height: Dp, color: Color) {
    val transition = rememberInfiniteTransition(label = "flamingoMixSheen")
    val phase by transition.animateFloat(
        initialValue = -FlamingoSheenBandFraction,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = FlamingoSheenPeriodMs, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "flamingoMixSheenPhase",
    )
    Canvas(
        Modifier
            .fillMaxWidth()
            .height(height),
    ) {
        val band = size.width * FlamingoSheenBandFraction
        val x = phase * size.width
        drawRoundRect(
            brush = Brush.horizontalGradient(
                colorStops = arrayOf(
                    0f to Color.Transparent,
                    0.5f to color,
                    1f to Color.Transparent,
                ),
                startX = x,
                endX = x + band,
            ),
            size = Size(size.width, size.height),
            cornerRadius = CornerRadius(size.height / 2f),
        )
    }
}

private const val FlamingoMixMorphMs = 450
private const val FlamingoSheenBandFraction = 0.34f
private const val FlamingoSheenPeriodMs = 1000

@Composable
private fun FlamingoQualityIndicator(
    currentFormat: FormatEntity?,
    onQualityClick: () -> Unit = {},
) {
    if (currentFormat == null) return

    val musicBitrateKbps = currentFormat.bitrate / 1000
    val musicSamplingRate = currentFormat.sampleRate ?: 0

    val isLossless = currentFormat.isLossless() ||
        (musicBitrateKbps >= 700 && musicSamplingRate >= 44100)
    val isHiRes = musicBitrateKbps >= 2000 && musicSamplingRate >= 96000

    val qualityLabel =
        remember(currentFormat) {
            when {
                isHiRes -> "Hi-Res Lossless"
                isLossless -> null
                else -> currentFormat.codecLabel()
            }
        }
    val text = qualityLabel ?: stringResource(id = R.string.flamingo_quality_lossless)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(20.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onQualityClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .overlayEffect()
                .background(
                    color = Color(0x14FFFFFF),
                    shape = FlamingoSmoothCornerShape(5.dp),
                )
                .height(20.dp)
                .padding(horizontal = 7.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    painter = painterResource(
                        if (isHiRes || isLossless) {
                            R.drawable.flamingo_quality_lossless
                        } else {
                            R.drawable.player_graphic_eq
                        },
                    ),
                    contentDescription = "quality_badge",
                    modifier = Modifier
                        .height(if (isHiRes || isLossless) 9.dp else 10.dp)
                        .alpha(0.45f),
                )
                Spacer(modifier = Modifier.width(5.dp))

                AnimatedContent(
                    targetState = text,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                ) { thisText ->
                    Text(
                        text = thisText,
                        modifier = Modifier.alpha(0.45f),
                        fontSize = 11.sp,
                        lineHeight = 11.sp,
                    )
                }
            }
        }
    }
}
