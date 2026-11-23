/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(androidx.compose.material3.ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.Morph
import androidx.palette.graphics.Palette
import androidx.window.core.layout.WindowSizeClass
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.google.common.collect.ImmutableList
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.utils.ComposeToImage

@Immutable
private data class LyricsGlassStyleOptions(
    val items: ImmutableList<LyricsGlassStyle>,
)

private enum class LyricsSharePane(
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
) {
    Layout(R.string.lyrics_share_layout, R.drawable.grid_view),
    Style(R.string.lyrics_share_style, R.drawable.palette),
    Adjust(R.string.lyrics_share_adjust, R.drawable.tune),
}

fun shareLyricsAsText(
    context: Context,
    payload: LyricsSharePayload,
    songId: String?,
) {
    val songLink = songId?.takeIf { it.isNotBlank() }?.let { "https://music.youtube.com/watch?v=$it" }
    val shareBody =
        buildString {
            append("\"")
            append(payload.lyricsText)
            append("\"\n\n")
            append(payload.songTitle)
            append(" - ")
            append(payload.artists)
            if (songLink != null) {
                append('\n')
                append(songLink)
            }
        }

    val shareIntent =
        Intent().apply {
            action = Intent.ACTION_SEND
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareBody)
        }
    context.startActivity(
        Intent.createChooser(
            shareIntent,
            context.getString(R.string.share_lyrics),
        ),
    )
}

@Composable
fun LyricsShareImageDialog(
    mediaMetadata: MediaMetadata?,
    payload: LyricsSharePayload,
    onDismissRequest: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isExpandedLayout =
        currentWindowAdaptiveInfo().windowSizeClass.isWidthAtLeastBreakpoint(WindowSizeClass.WIDTH_DP_MEDIUM_LOWER_BOUND)
    val coverUrl = mediaMetadata?.thumbnailUrl

    var isSharing by remember { mutableStateOf(false) }
    var exportError by remember { mutableStateOf<String?>(null) }
    var selectedGlassStyle by remember { mutableStateOf(LyricsGlassStyle.FrostedDark) }
    var paletteGlassStyle by remember { mutableStateOf<LyricsGlassStyle?>(null) }
    var options by remember { mutableStateOf(LyricsShareImageOptions()) }
    var selectedPane by rememberSaveable { mutableStateOf(LyricsSharePane.Layout) }

    LaunchedEffect(coverUrl) {
        paletteGlassStyle = coverUrl?.let { url -> extractPaletteGlassStyle(context, url) }
    }

    val availableStyles =
        remember(paletteGlassStyle) {
            LyricsGlassStyleOptions(
                items =
                    ImmutableList.copyOf(
                        buildList {
                            paletteGlassStyle?.let(::add)
                            addAll(LyricsGlassStyle.allPresets.filterNot { it == paletteGlassStyle })
                        },
                    ),
            )
        }

    val handleShare: () -> Unit = handleShare@{
        if (isSharing) return@handleShare
        isSharing = true
        exportError = null
        scope.launch {
            try {
                val image =
                    ComposeToImage.createLyricsImage(
                        context = context,
                        coverArtUrl = coverUrl,
                        songTitle = payload.songTitle,
                        artistName = payload.artists,
                        lyrics = payload.lyricsText,
                        width = options.aspectRatio.exportWidth,
                        height = options.aspectRatio.exportHeight,
                        glassStyle = selectedGlassStyle,
                        shareOptions = options,
                    )
                val fileName = "lyrics_${System.currentTimeMillis()}"
                val uri = ComposeToImage.saveBitmapAsFile(context, image, fileName)
                val shareIntent =
                    Intent(Intent.ACTION_SEND).apply {
                        type = "image/png"
                        putExtra(Intent.EXTRA_STREAM, uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                context.startActivity(
                    Intent.createChooser(
                        shareIntent,
                        context.getString(R.string.share_lyrics),
                    ),
                )
                onDismissRequest()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                exportError = context.getString(R.string.lyrics_share_export_failed, e.message.orEmpty())
            } finally {
                isSharing = false
            }
        }
    }

    Dialog(
        onDismissRequest = {
            if (!isSharing) onDismissRequest()
        },
        properties =
            DialogProperties(
                dismissOnBackPress = !isSharing,
                dismissOnClickOutside = false,
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
            ),
    ) {
        LyricsShareStudio(
            payload = payload,
            coverUrl = coverUrl,
            options = options,
            onOptionsChange = { options = it },
            availableStyles = availableStyles,
            selectedGlassStyle = selectedGlassStyle,
            onStyleSelect = { selectedGlassStyle = it },
            selectedPane = selectedPane,
            onPaneSelect = { selectedPane = it },
            isSharing = isSharing,
            exportError = exportError,
            isExpandedLayout = isExpandedLayout,
            onShare = handleShare,
            onDismiss = onDismissRequest,
        )
    }
}

private suspend fun extractPaletteGlassStyle(
    context: Context,
    coverUrl: String,
): LyricsGlassStyle? =
    withContext(Dispatchers.IO) {
        val request =
            ImageRequest
                .Builder(context)
                .data(coverUrl)
                .size(256)
                .allowHardware(false)
                .build()
        val bitmap = context.imageLoader.execute(request).image?.toBitmap() ?: return@withContext null
        LyricsGlassStyle.fromPalette(Palette.from(bitmap).generate())
    }

@Composable
private fun LyricsShareStudio(
    payload: LyricsSharePayload,
    coverUrl: String?,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    selectedPane: LyricsSharePane,
    onPaneSelect: (LyricsSharePane) -> Unit,
    isSharing: Boolean,
    exportError: String?,
    isExpandedLayout: Boolean,
    onShare: () -> Unit,
    onDismiss: () -> Unit,
) {
    val hazeState = rememberHazeState()

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        LyricsShareBackdrop(
            coverUrl = coverUrl,
            glassStyle = selectedGlassStyle,
            modifier =
                Modifier
                    .fillMaxSize()
                    .hazeSource(hazeState),
        )

        val dock: @Composable (Modifier) -> Unit = { dockModifier ->
            LyricsShareOptionsDock(
                hazeState = hazeState,
                coverUrl = coverUrl,
                options = options,
                onOptionsChange = onOptionsChange,
                availableStyles = availableStyles,
                selectedGlassStyle = selectedGlassStyle,
                onStyleSelect = onStyleSelect,
                selectedPane = selectedPane,
                onPaneSelect = onPaneSelect,
                isSharing = isSharing,
                exportError = exportError,
                isExpandedLayout = isExpandedLayout,
                onShare = onShare,
                modifier = dockModifier,
            )
        }

        if (isExpandedLayout) {
            val dockWidth = (maxWidth * 0.42f).coerceIn(320.dp, 420.dp)
            Row(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                ) {
                    LyricsShareTopBar(
                        payload = payload,
                        isSharing = isSharing,
                        onDismiss = onDismiss,
                    )
                    LyricsSharePreviewStage(
                        payload = payload,
                        coverUrl = coverUrl,
                        glassStyle = selectedGlassStyle,
                        options = options,
                        isSharing = isSharing,
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxWidth(),
                    )
                }
                dock(
                    Modifier
                        .width(dockWidth)
                        .fillMaxHeight(),
                )
            }
        } else {
            val dockMaxHeight = maxHeight * 0.6f
            Column(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                LyricsShareTopBar(
                    payload = payload,
                    isSharing = isSharing,
                    onDismiss = onDismiss,
                )
                LyricsSharePreviewStage(
                    payload = payload,
                    coverUrl = coverUrl,
                    glassStyle = selectedGlassStyle,
                    options = options,
                    isSharing = isSharing,
                    modifier =
                        Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                )
                dock(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = dockMaxHeight),
                )
            }
        }
    }
}

@Composable
private fun LyricsShareBackdrop(
    coverUrl: String?,
    glassStyle: LyricsGlassStyle,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val animationsDisabled = LocalAnimationsDisabled.current
    val isBlurAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val backdropBlur =
        remember(density, isBlurAvailable) {
            if (isBlurAvailable) {
                val radius = with(density) { 36.dp.toPx() }
                BlurEffect(radiusX = radius, radiusY = radius, edgeTreatment = TileMode.Clamp)
            } else {
                null
            }
        }
    val zoom = remember { Animatable(if (animationsDisabled) 1.08f else 1.2f) }
    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) zoom.snapTo(1.08f) else zoom.animateTo(1.08f, motion.slowEffectsSpec())
    }
    val glowColor by animateColorAsState(
        targetValue = glassStyle.surfaceTint.copy(alpha = 0.42f),
        animationSpec = motion.slowEffectsSpec(),
        label = "lyricsShareBackdropGlow",
    )
    val scrimBase = colors.surface
    val scrimAlphas = if (isBlurAvailable) floatArrayOf(0.4f, 0.58f, 0.86f) else floatArrayOf(0.62f, 0.74f, 0.92f)

    Box(modifier = modifier.background(colors.surfaceContainerLowest)) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = zoom.value
                            scaleY = zoom.value
                            renderEffect = backdropBlur
                        },
            )
        }
        Box(
            modifier =
                Modifier
                    .fillMaxSize()
                    .drawWithCache {
                        val scrim =
                            Brush.verticalGradient(
                                colors = scrimAlphas.map { scrimBase.copy(alpha = it) },
                            )
                        val glow =
                            Brush.radialGradient(
                                colors = listOf(glowColor, Color.Transparent),
                                center = Offset(size.width * 0.5f, size.height * 0.34f),
                                radius = size.maxDimension * 0.62f,
                            )
                        onDrawBehind {
                            drawRect(scrim)
                            drawRect(glow)
                        }
                    },
        )
    }
}

@Composable
private fun LyricsShareTopBar(
    payload: LyricsSharePayload,
    isSharing: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        FilledTonalIconButton(
            onClick = onDismiss,
            enabled = !isSharing,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.close),
                contentDescription = stringResource(R.string.close_dialog),
            )
        }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = stringResource(R.string.share_lyrics),
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = payload.songTitle,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = payload.artists,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun LyricsSharePreviewStage(
    payload: LyricsSharePayload,
    coverUrl: String?,
    glassStyle: LyricsGlassStyle,
    options: LyricsShareImageOptions,
    isSharing: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val entrance = remember { Animatable(if (animationsDisabled) 1f else 0f) }
    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) entrance.snapTo(1f) else entrance.animateTo(1f, motion.slowSpatialSpec())
    }

    BoxWithConstraints(
        modifier = modifier.padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        val availableHeight = (maxHeight - 56.dp).coerceAtLeast(0.dp)
        val availableWidth = maxWidth.coerceAtMost(440.dp)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier =
                Modifier.graphicsLayer {
                    val progress = entrance.value
                    alpha = progress.coerceIn(0f, 1f)
                    scaleX = 0.86f + 0.14f * progress
                    scaleY = 0.86f + 0.14f * progress
                    translationY = (1f - progress) * 40.dp.toPx()
                },
        ) {
            AnimatedContent(
                targetState = options.aspectRatio,
                transitionSpec = {
                    if (animationsDisabled) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (
                            fadeIn(motion.defaultEffectsSpec()) +
                                scaleIn(motion.defaultSpatialSpec(), initialScale = 0.9f)
                        ) togetherWith (
                            fadeOut(motion.fastEffectsSpec()) +
                                scaleOut(motion.fastSpatialSpec(), targetScale = 1.04f)
                        ) using SizeTransform(clip = false)
                    }
                },
                contentAlignment = Alignment.Center,
                label = "lyricsSharePreviewFormat",
            ) { aspectRatio ->
                val ratio = aspectRatio.previewAspectRatio
                val cardWidth = minOf(availableWidth, availableHeight * ratio)
                LyricsSharePreviewCard(
                    payload = payload,
                    coverUrl = coverUrl,
                    glassStyle = glassStyle,
                    options = options,
                    isSharing = isSharing,
                    modifier = Modifier.size(width = cardWidth, height = cardWidth / ratio),
                )
            }
            LyricsShareFormatChip(aspectRatio = options.aspectRatio)
        }
    }
}

@Composable
private fun LyricsSharePreviewCard(
    payload: LyricsSharePayload,
    coverUrl: String?,
    glassStyle: LyricsGlassStyle,
    options: LyricsShareImageOptions,
    isSharing: Boolean,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val cardShape = MaterialTheme.shapes.large

    Box(
        modifier =
            modifier
                .shadow(
                    elevation = 24.dp,
                    shape = cardShape,
                    ambientColor = Color.Black.copy(alpha = 0.4f),
                    spotColor = Color.Black.copy(alpha = 0.5f),
                ).clip(cardShape),
    ) {
        LyricsImageCard(
            lyricText = payload.lyricsText,
            songTitle = payload.songTitle,
            artistName = payload.artists,
            coverArtUrl = coverUrl,
            glassStyle = glassStyle,
            shareOptions = options,
        )
        AnimatedVisibility(
            visible = isSharing,
            enter = fadeIn(motion.defaultEffectsSpec()),
            exit = fadeOut(motion.defaultEffectsSpec()),
            modifier = Modifier.matchParentSize(),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.42f)),
                contentAlignment = Alignment.Center,
            ) {
                LoadingIndicator(
                    color = Color.White,
                    modifier = Modifier.size(56.dp),
                )
            }
        }
    }
}

@Composable
private fun LyricsShareFormatChip(
    aspectRatio: LyricsShareAspectRatio,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.78f),
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.image),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            AnimatedContent(
                targetState = aspectRatio,
                transitionSpec = {
                    if (animationsDisabled) {
                        EnterTransition.None togetherWith ExitTransition.None
                    } else {
                        (
                            slideInVertically(motion.fastSpatialSpec()) { it / 2 } +
                                fadeIn(motion.fastEffectsSpec())
                        ) togetherWith (
                            slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } +
                                fadeOut(motion.fastEffectsSpec())
                        ) using SizeTransform(clip = false)
                    }
                },
                label = "lyricsShareFormatChip",
            ) { ratio ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(ratio.labelRes),
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                    )
                    Text(
                        text = stringResource(R.string.lyrics_share_resolution_value, ratio.exportWidth, ratio.exportHeight),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareOptionsDock(
    hazeState: HazeState,
    coverUrl: String?,
    options: LyricsShareImageOptions,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
    availableStyles: LyricsGlassStyleOptions,
    selectedGlassStyle: LyricsGlassStyle,
    onStyleSelect: (LyricsGlassStyle) -> Unit,
    selectedPane: LyricsSharePane,
    onPaneSelect: (LyricsSharePane) -> Unit,
    isSharing: Boolean,
    exportError: String?,
    isExpandedLayout: Boolean,
    onShare: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val dockShape = RoundedCornerShape(32.dp)
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val rimBrush =
        Brush.linearGradient(
            colors =
                listOf(
                    Color.White.copy(alpha = if (isDarkSurface) 0.2f else 0.55f),
                    Color.White.copy(alpha = 0.04f),
                    Color.White.copy(alpha = if (isDarkSurface) 0.1f else 0.28f),
                ),
        )
    val entrance = remember { Animatable(if (animationsDisabled) 1f else 0f) }
    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) entrance.snapTo(1f) else entrance.animateTo(1f, motion.slowSpatialSpec())
    }

    Box(
        modifier =
            modifier
                .graphicsLayer {
                    val progress = entrance.value
                    alpha = progress.coerceIn(0f, 1f)
                    if (isExpandedLayout) {
                        translationX = (1f - progress) * 72.dp.toPx()
                    } else {
                        translationY = (1f - progress) * 96.dp.toPx()
                    }
                }.clip(dockShape),
    ) {
        LyricsShareGlassSurface(
            hazeState = hazeState,
            shape = dockShape,
            modifier =
                Modifier
                    .matchParentSize()
                    .border(width = 1.dp, brush = rimBrush, shape = dockShape),
        )
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LyricsSharePaneSelector(
                selectedPane = selectedPane,
                onPaneSelect = onPaneSelect,
                enabled = !isSharing,
            )
            Box(
                modifier =
                    Modifier
                        .weight(1f, fill = isExpandedLayout)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                AnimatedContent(
                    targetState = selectedPane,
                    transitionSpec = {
                        if (animationsDisabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            val forward = targetState.ordinal > initialState.ordinal
                            (
                                slideInHorizontally(motion.defaultSpatialSpec()) { width ->
                                    if (forward) width / 3 else -width / 3
                                } + fadeIn(motion.defaultEffectsSpec())
                            ) togetherWith (
                                slideOutHorizontally(motion.fastSpatialSpec()) { width ->
                                    if (forward) -width / 3 else width / 3
                                } + fadeOut(motion.fastEffectsSpec())
                            ) using SizeTransform(clip = false)
                        }
                    },
                    label = "lyricsSharePane",
                ) { pane ->
                    when (pane) {
                        LyricsSharePane.Layout ->
                            LyricsShareLayoutPane(
                                selected = options.aspectRatio,
                                enabled = !isSharing,
                                onSelect = { onOptionsChange(options.copy(aspectRatio = it)) },
                            )

                        LyricsSharePane.Style ->
                            LyricsShareStylePane(
                                styles = availableStyles,
                                selected = selectedGlassStyle,
                                coverUrl = coverUrl,
                                enabled = !isSharing,
                                onSelect = onStyleSelect,
                            )

                        LyricsSharePane.Adjust ->
                            LyricsShareAdjustPane(
                                options = options,
                                enabled = !isSharing,
                                onOptionsChange = onOptionsChange,
                            )
                    }
                }
            }
            Column(modifier = Modifier.fillMaxWidth()) {
                LyricsShareErrorBanner(message = exportError)
                LyricsShareButton(
                    isSharing = isSharing,
                    onShare = onShare,
                )
            }
        }
    }
}

@OptIn(ExperimentalHazeApi::class)
@Composable
private fun LyricsShareGlassSurface(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val surfaceColor = colors.surfaceContainer
    val isDarkSurface = colors.surface.luminance() < 0.5f
    val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val glassStyle =
            remember(shape, surfaceColor, isDarkSurface) {
                GlassStyle.clear.then {
                    shape(shape)
                    backgroundColor(surfaceColor)
                    tint(surfaceColor.copy(alpha = if (isDarkSurface) 0.48f else 0.54f))
                    whitePoint(if (isDarkSurface) -0.06f else 0.08f)
                    contrast(0f)
                    chromaMultiplier(1f)
                    specularIntensity(0.36f)
                    edgeShadow(Color.Black.copy(alpha = 0.08f))
                    ambientResponse(0.14f)
                }
            }
        Box(
            modifier =
                modifier.hazeGlass(
                    input = hazeInput,
                    style = glassStyle,
                    performanceMode = HazePerformanceMode.Default,
                    expandLayerBounds = true,
                ),
        )
    } else {
        val blurStyle =
            remember(surfaceColor, isDarkSurface) {
                HazeBlurStyle {
                    backgroundColor(surfaceColor)
                    blurRadius(28.dp)
                    noiseFactor(0f)
                    colorEffects(
                        listOf(
                            HazeColorEffect.tint(surfaceColor.copy(alpha = if (isDarkSurface) 0.6f else 0.64f)),
                        ),
                    )
                    fallbackColorEffect(HazeColorEffect.tint(surfaceColor.copy(alpha = 0.94f)))
                }
            }
        Box(
            modifier =
                modifier.hazeBlur(
                    input = hazeInput,
                    style = blurStyle,
                    expandLayerBounds = true,
                ),
        )
    }
}

@Composable
private fun LyricsSharePaneSelector(
    selectedPane: LyricsSharePane,
    onPaneSelect: (LyricsSharePane) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val panes = LyricsSharePane.entries

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        panes.forEachIndexed { index, pane ->
            val checked = pane == selectedPane
            val label = stringResource(pane.labelRes)
            val weight by animateFloatAsState(
                targetValue = if (checked) 2.2f else 1f,
                animationSpec = motion.defaultSpatialSpec(),
                label = "lyricsSharePaneWeight",
            )
            ToggleButton(
                checked = checked,
                onCheckedChange = { if (!checked) onPaneSelect(pane) },
                enabled = enabled,
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        panes.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = colors.surfaceContainerHighest.copy(alpha = 0.72f),
                        contentColor = colors.onSurfaceVariant,
                        checkedContainerColor = colors.primary,
                        checkedContentColor = colors.onPrimary,
                    ),
                modifier =
                    Modifier
                        .weight(weight.coerceAtLeast(0.5f))
                        .height(52.dp),
            ) {
                Icon(
                    painter = painterResource(pane.iconRes),
                    contentDescription = if (checked) null else label,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                AnimatedVisibility(
                    visible = checked,
                    enter = expandHorizontally(motion.defaultSpatialSpec()) + fadeIn(motion.defaultEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(start = ButtonDefaults.IconSpacing),
                    )
                }
            }
        }
    }
}

@Composable
private fun LyricsShareLayoutPane(
    selected: LyricsShareAspectRatio,
    enabled: Boolean,
    onSelect: (LyricsShareAspectRatio) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LyricsShareAspectRatio.entries.forEach { aspectRatio ->
            LyricsAspectTile(
                aspectRatio = aspectRatio,
                selected = selected == aspectRatio,
                enabled = enabled,
                onClick = { onSelect(aspectRatio) },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LyricsAspectTile(
    aspectRatio: LyricsShareAspectRatio,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val corner by animateDpAsState(
        targetValue = if (selected) 28.dp else 16.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "lyricsAspectTileCorner",
    )
    val containerColor by animateColorAsState(
        targetValue = if (selected) colors.primaryContainer else colors.surfaceContainerHighest.copy(alpha = 0.6f),
        animationSpec = motion.defaultEffectsSpec(),
        label = "lyricsAspectTileContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) colors.onPrimaryContainer else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "lyricsAspectTileContent",
    )
    val glyphScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.84f,
        animationSpec = motion.fastSpatialSpec(),
        label = "lyricsAspectTileGlyph",
    )
    val tileShape = RoundedCornerShape(corner)
    val glyphShape = RoundedCornerShape(6.dp)

    Column(
        modifier =
            modifier
                .clip(tileShape)
                .background(containerColor)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(horizontal = 8.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .height(44.dp)
                        .aspectRatio(aspectRatio.previewAspectRatio, matchHeightConstraintsFirst = true)
                        .graphicsLayer {
                            scaleX = glyphScale
                            scaleY = glyphScale
                        }.background(contentColor.copy(alpha = if (selected) 0.2f else 0f), glyphShape)
                        .border(width = 2.dp, color = contentColor, shape = glyphShape),
            )
        }
        Text(
            text = stringResource(aspectRatio.labelRes),
            style = if (selected) MaterialTheme.typography.labelLargeEmphasized else MaterialTheme.typography.labelLarge,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = stringResource(R.string.lyrics_share_resolution_value, aspectRatio.exportWidth, aspectRatio.exportHeight),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor.copy(alpha = 0.78f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun LyricsShareStylePane(
    styles: LyricsGlassStyleOptions,
    selected: LyricsGlassStyle,
    coverUrl: String?,
    enabled: Boolean,
    onSelect: (LyricsGlassStyle) -> Unit,
) {
    LazyRow(
        modifier =
            Modifier
                .fillMaxWidth()
                .selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp),
    ) {
        items(
            items = styles.items,
            key = { it.labelRes },
        ) { style ->
            LyricsStyleSwatch(
                style = style,
                selected = style == selected,
                coverUrl = coverUrl,
                enabled = enabled,
                onClick = { onSelect(style) },
                modifier = Modifier.animateItem(),
            )
        }
    }
}

@Composable
private fun LyricsStyleSwatch(
    style: LyricsGlassStyle,
    selected: Boolean,
    coverUrl: String?,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val morphProgress by animateFloatAsState(
        targetValue = if (selected) 1f else 0f,
        animationSpec = if (animationsDisabled) motion.fastEffectsSpec() else motion.defaultSpatialSpec(),
        label = "lyricsStyleSwatchMorph",
    )
    val ringColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.outlineVariant.copy(alpha = 0.5f),
        animationSpec = motion.defaultEffectsSpec(),
        label = "lyricsStyleSwatchRing",
    )
    val labelColor by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "lyricsStyleSwatchLabel",
    )
    val swatchShape = EditorialMorphShape(morph, morphProgress.coerceIn(0f, 1f))
    val itemShape = RoundedCornerShape(20.dp)

    Column(
        modifier =
            modifier
                .width(80.dp)
                .clip(itemShape)
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ).padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(64.dp)
                    .border(width = 2.dp, color = ringColor, shape = swatchShape),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .clip(swatchShape)
                        .background(colors.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (coverUrl != null) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.matchParentSize(),
                    )
                }
                Box(
                    modifier =
                        Modifier
                            .matchParentSize()
                            .background(style.surfaceTint.copy(alpha = style.surfaceAlpha))
                            .background(style.overlayColor.copy(alpha = style.overlayAlpha)),
                )
                LyricsStyleSwatchCheck(
                    visible = selected,
                    tint = style.textColor,
                )
            }
        }
        Text(
            text = stringResource(style.labelRes),
            style = if (selected) MaterialTheme.typography.labelMediumEmphasized else MaterialTheme.typography.labelMedium,
            color = labelColor,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun LyricsStyleSwatchCheck(
    visible: Boolean,
    tint: Color,
) {
    val motion = MaterialTheme.motionScheme

    AnimatedVisibility(
        visible = visible,
        enter = scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f) + fadeIn(motion.fastEffectsSpec()),
        exit = scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f) + fadeOut(motion.fastEffectsSpec()),
    ) {
        Icon(
            painter = painterResource(R.drawable.check),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun LyricsShareAdjustPane(
    options: LyricsShareImageOptions,
    enabled: Boolean,
    onOptionsChange: (LyricsShareImageOptions) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        LyricsShareSliderRow(
            iconRes = R.drawable.blur_on,
            title = stringResource(R.string.lyrics_share_background_blur),
            valueLabel = stringResource(R.string.lyrics_share_background_blur_value, options.sanitizedBlurRadius.toInt()),
            value = options.sanitizedBlurRadius,
            valueRange = 0f..48f,
            enabled = enabled,
            onValueChange = { onOptionsChange(options.copy(blurRadius = it)) },
        )
        LyricsShareSliderRow(
            iconRes = R.drawable.contrast,
            title = stringResource(R.string.lyrics_share_background_dim),
            valueLabel = stringResource(R.string.lyrics_share_background_dim_value, (options.sanitizedDimAmount * 100).toInt()),
            value = options.sanitizedDimAmount,
            valueRange = 0.6f..1.6f,
            enabled = enabled,
            onValueChange = { onOptionsChange(options.copy(dimAmount = it)) },
        )
        LyricsShareCoverToggleRow(
            checked = options.showArtwork,
            enabled = enabled,
            onCheckedChange = { onOptionsChange(options.copy(showArtwork = it)) },
        )
    }
}

@Composable
private fun LyricsShareSliderRow(
    @DrawableRes iconRes: Int,
    title: String,
    valueLabel: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    enabled: Boolean,
    onValueChange: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val rowShape = RoundedCornerShape(24.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.55f))
                .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LyricsShareRowIcon(iconRes = iconRes)
        Column(modifier = Modifier.weight(1f)) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = colors.primary,
                    maxLines = 1,
                )
            }
            Slider(
                value = value,
                onValueChange = onValueChange,
                valueRange = valueRange,
                enabled = enabled,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { stateDescription = valueLabel },
            )
        }
    }
}

@Composable
private fun LyricsShareCoverToggleRow(
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val rowShape = RoundedCornerShape(24.dp)

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(rowShape)
                .background(colors.surfaceContainerHighest.copy(alpha = 0.55f))
                .toggleable(
                    value = checked,
                    enabled = enabled,
                    role = Role.Switch,
                    onValueChange = onCheckedChange,
                ).padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LyricsShareRowIcon(iconRes = if (checked) R.drawable.image else R.drawable.hide_image)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.lyrics_share_show_cover),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurface,
            )
            Text(
                text = stringResource(R.string.lyrics_share_show_cover_desc),
                style = MaterialTheme.typography.bodySmall,
                color = colors.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = null,
            enabled = enabled,
            thumbContent = {
                AnimatedContent(
                    targetState = checked,
                    transitionSpec = {
                        if (animationsDisabled) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (
                                fadeIn(motion.fastEffectsSpec()) +
                                    scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f)
                            ) togetherWith (
                                fadeOut(motion.fastEffectsSpec()) +
                                    scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f)
                            )
                        }
                    },
                    label = "lyricsShareCoverThumb",
                ) { isChecked ->
                    Icon(
                        painter = painterResource(if (isChecked) R.drawable.check else R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(SwitchDefaults.IconSize),
                    )
                }
            },
        )
    }
}

@Composable
private fun LyricsShareRowIcon(
    @DrawableRes iconRes: Int,
) {
    Box(
        modifier =
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.secondaryContainer),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun LyricsShareErrorBanner(message: String?) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    AnimatedContent(
        targetState = message,
        transitionSpec = {
            if (animationsDisabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (
                    fadeIn(motion.defaultEffectsSpec()) +
                        scaleIn(motion.defaultSpatialSpec(), initialScale = 0.92f)
                ) togetherWith fadeOut(motion.fastEffectsSpec()) using SizeTransform(clip = false)
            }
        },
        label = "lyricsShareError",
    ) { error ->
        if (error != null) {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier =
                    Modifier
                        .padding(bottom = 10.dp)
                        .fillMaxWidth()
                        .semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        } else {
            Spacer(modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun LyricsShareButton(
    isSharing: Boolean,
    onShare: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    Button(
        onClick = onShare,
        enabled = !isSharing,
        shapes = ButtonDefaults.shapes(),
        colors =
            ButtonDefaults.buttonColors(
                containerColor = colors.primary,
                contentColor = colors.onPrimary,
                disabledContainerColor = colors.primary,
                disabledContentColor = colors.onPrimary,
            ),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
    ) {
        AnimatedContent(
            targetState = isSharing,
            transitionSpec = {
                if (animationsDisabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (
                        fadeIn(motion.fastEffectsSpec()) +
                            scaleIn(motion.fastSpatialSpec(), initialScale = 0.8f)
                    ) togetherWith (
                        fadeOut(motion.fastEffectsSpec()) +
                            scaleOut(motion.fastSpatialSpec(), targetScale = 0.8f)
                    ) using SizeTransform(clip = false)
                }
            },
            contentAlignment = Alignment.Center,
            label = "lyricsShareButtonContent",
        ) { sharing ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing),
                modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
            ) {
                if (sharing) {
                    LoadingIndicator(
                        color = LocalContentColor.current,
                        modifier = Modifier.size(ButtonDefaults.IconSize + 8.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.share),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                }
                Text(
                    text = stringResource(if (sharing) R.string.generating_image else R.string.share_as_image),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
