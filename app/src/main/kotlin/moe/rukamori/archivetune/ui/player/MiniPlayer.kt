/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.MiniPlayerHeight
import moe.rukamori.archivetune.constants.NavigationBarMaxWidth
import moe.rukamori.archivetune.constants.SwipeSensitivityKey
import moe.rukamori.archivetune.playback.artwork.PlayerPaletteCacheKey
import moe.rukamori.archivetune.playback.artwork.guessArtworkProvider
import moe.rukamori.archivetune.ui.component.LocalNavigationBarBackdrop
import moe.rukamori.archivetune.ui.component.rememberPreSFrostedBitmap
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.PlayerPaletteCache
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

@Composable
fun MiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isPairedWithNavigation: Boolean = false,
) {
    NewMiniPlayer(
        position = position,
        duration = duration,
        modifier = modifier,
        pureBlack = pureBlack,
        isPairedWithNavigation = isPairedWithNavigation,
    )
}

@Composable
private fun NewMiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
    isPairedWithNavigation: Boolean,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val context = LocalContext.current
    val layoutDirection = LocalLayoutDirection.current
    val coroutineScope = rememberCoroutineScope()
    val swipeSensitivity by rememberPreference(SwipeSensitivityKey, 0.73f)
    val swipeThumbnail by rememberPreference(moe.rukamori.archivetune.constants.SwipeThumbnailKey, true)
    val miniPlayerBackgroundStyle by rememberEnumPreference(
        key = MiniPlayerBackgroundStyleKey,
        defaultValue = MiniPlayerBackgroundStyle.THEME,
    )
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    // Keep the previous valid palette while the next artwork loads; replace only on success.
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    var hasValidPalette by remember { mutableStateOf(false) }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    // Only the artwork-derived styles need palette extraction; THEME and FROSTED don't.
    val shouldUseArtworkBackground =
        miniPlayerBackgroundStyle == MiniPlayerBackgroundStyle.GRADIENT ||
            miniPlayerBackgroundStyle == MiniPlayerBackgroundStyle.GLOW
    val darkTheme = isSystemInDarkTheme()

    LaunchedEffect(
        mediaMetadata?.id,
        mediaMetadata?.thumbnailUrl,
        shouldUseArtworkBackground,
        fallbackColor,
        darkTheme,
    ) {
        if (!shouldUseArtworkBackground) {
            gradientColors = emptyList()
            hasValidPalette = false
            return@LaunchedEffect
        }

        val currentMetadata = mediaMetadata
        val thumbnailUrl = currentMetadata?.thumbnailUrl
        if (currentMetadata == null || thumbnailUrl.isNullOrBlank()) {
            if (!hasValidPalette) gradientColors = emptyList()
            return@LaunchedEffect
        }

        val cacheKey =
            PlayerPaletteCacheKey(
                mediaId = currentMetadata.id,
                provider = guessArtworkProvider(thumbnailUrl),
                artworkIdentity = thumbnailUrl,
                backgroundMode = miniPlayerBackgroundStyle.name,
                darkTheme = darkTheme,
            )
        PlayerPaletteCache.get(cacheKey)?.let { cachedColors ->
            gradientColors = cachedColors
            hasValidPalette = true
            return@LaunchedEffect
        }

        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val extractedColors =
            try {
                val result =
                    withContext(Dispatchers.IO) {
                        context.imageLoader.execute(request)
                    }
                if (result !is SuccessResult) {
                    null
                } else {
                    val bitmap = result.image?.toBitmap()
                    if (bitmap == null) {
                        null
                    } else {
                        val palette =
                            withContext(Dispatchers.Default) {
                                Palette
                                    .from(bitmap)
                                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                                    .generate()
                            }
                        PlayerColorExtractor.extractGradientColors(
                            palette = palette,
                            fallbackColor = fallbackColor,
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                null
            }

        // On failure/cancellation keep the previous valid palette; never force a grey fallback.
        if (extractedColors != null) {
            val stillCurrent =
                mediaMetadata?.id == currentMetadata.id &&
                    mediaMetadata?.thumbnailUrl == thumbnailUrl
            if (stillCurrent) {
                PlayerPaletteCache.put(cacheKey, extractedColors)
                gradientColors = extractedColors
                hasValidPalette = true
            }
        } else if (!hasValidPalette) {
            gradientColors = emptyList()
        }
    }

    val backgroundPalette =
        remember(gradientColors) {
            MiniPlayerBackgroundPalette.from(gradientColors)
        }
    val effectiveBackgroundStyle =
        when {
            miniPlayerBackgroundStyle == MiniPlayerBackgroundStyle.FROSTED -> MiniPlayerBackgroundStyle.FROSTED
            shouldUseArtworkBackground && backgroundPalette != null -> miniPlayerBackgroundStyle
            else -> MiniPlayerBackgroundStyle.THEME
        }

    val contentColors =
        rememberMiniPlayerContentColors(
            useArtworkBackground =
                effectiveBackgroundStyle == MiniPlayerBackgroundStyle.GRADIENT ||
                    effectiveBackgroundStyle == MiniPlayerBackgroundStyle.GLOW,
        )
    val miniPlayerShape =
        remember(isPairedWithNavigation) {
            if (isPairedWithNavigation) {
                RoundedCornerShape(
                    topStart = 28.dp,
                    topEnd = 28.dp,
                    bottomStart = 12.dp,
                    bottomEnd = 12.dp,
                )
            } else {
                null
            }
        } ?: MaterialTheme.shapes.extraLarge

    SwipeableMiniPlayerBox(
        modifier = modifier,
        contentMaxWidth = if (isPairedWithNavigation) NavigationBarMaxWidth else null,
        swipeSensitivity = swipeSensitivity,
        swipeThumbnail = swipeThumbnail,
        playerConnection = playerConnection,
        layoutDirection = layoutDirection,
        coroutineScope = coroutineScope,
        pureBlack = pureBlack,
        useLegacyBackground = false,
    ) { offsetX ->
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(MiniPlayerHeight)
                    .offset { IntOffset(offsetX.roundToInt(), 0) }
                    .clip(miniPlayerShape),
        ) {
            MiniPlayerBackground(
                style = effectiveBackgroundStyle,
                palette = backgroundPalette,
                modifier = Modifier.fillMaxSize(),
            )
            NewMiniPlayerContent(
                position = position,
                duration = duration,
                playerConnection = playerConnection,
                colors = contentColors,
            )
        }
    }
}

@Composable
private fun rememberMiniPlayerContentColors(useArtworkBackground: Boolean): MiniPlayerContentColors {
    val colorScheme = MaterialTheme.colorScheme
    return remember(
        useArtworkBackground,
        colorScheme.primary,
        colorScheme.onPrimary,
        colorScheme.outline,
        colorScheme.onSurface,
        colorScheme.onSurfaceVariant,
        colorScheme.surface,
        colorScheme.surfaceContainerHighest,
        colorScheme.surfaceVariant,
        colorScheme.primaryContainer,
        colorScheme.onPrimaryContainer,
    ) {
        if (useArtworkBackground) {
            MiniPlayerContentColors(
                title = Color.White,
                secondary = Color.White.copy(alpha = 0.72f),
                progress = Color.White,
                progressTrack = Color.White.copy(alpha = 0.24f),
                artworkContainer = Color.White.copy(alpha = 0.14f),
                artworkBorder = Color.White.copy(alpha = 0.22f),
                primaryButtonContainer = Color.White.copy(alpha = 0.92f),
                primaryButtonIcon = Color.Black,
                secondaryButtonContainer = Color.Black.copy(alpha = 0.22f),
                buttonIcon = Color.White,
                disabledButtonIcon = Color.White.copy(alpha = 0.38f),
                togetherContainer = Color.White.copy(alpha = 0.16f),
                togetherContent = Color.White,
            )
        } else {
            MiniPlayerContentColors(
                title = colorScheme.onSurface,
                secondary = colorScheme.onSurfaceVariant,
                progress = colorScheme.primary,
                progressTrack = colorScheme.outline.copy(alpha = 0.18f),
                artworkContainer = colorScheme.surfaceVariant,
                artworkBorder = colorScheme.outline.copy(alpha = 0.2f),
                primaryButtonContainer = colorScheme.primary,
                primaryButtonIcon = colorScheme.onPrimary,
                secondaryButtonContainer = colorScheme.surfaceContainerHighest,
                buttonIcon = colorScheme.onSurface,
                disabledButtonIcon = colorScheme.onSurface.copy(alpha = 0.38f),
                togetherContainer = colorScheme.primaryContainer,
                togetherContent = colorScheme.onPrimaryContainer,
            )
        }
    }
}

// Frosted mini-player backdrop: blur radius in raw px (RenderEffect works in pixels) and the
// bounded fraction of blurred content shown over the opaque base — same recipe as the nav bar.
private const val FrostedMiniPlayerBlurRadiusPx = 60f
private const val FrostedMiniPlayerOverlayAlpha = 0.30f

@Composable
private fun MiniPlayerBackground(
    style: MiniPlayerBackgroundStyle,
    palette: MiniPlayerBackgroundPalette?,
    modifier: Modifier = Modifier,
) {
    when (style) {
        MiniPlayerBackgroundStyle.THEME -> {
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            )
        }

        MiniPlayerBackgroundStyle.FROSTED -> {
            // Same frosted-glass recipe as the navigation bar: an always-opaque surface with the
            // captured app content blurred and composited on top at a bounded alpha. On Android
            // 12+ this uses RenderEffect (every frame, hardware-accelerated). Below API 31
            // RenderEffect is unavailable, so we fall back to a periodically captured + CPU-blurred
            // bitmap (see [rememberPreSFrostedBitmap]) — same approach as the moving-blur lyrics
            // background. When no backdrop capture is available (rail layouts), the plain theme
            // surface is shown.
            val backdrop = LocalNavigationBarBackdrop.current
            val baseColor = MaterialTheme.colorScheme.surfaceContainerHigh
            if (backdrop == null) {
                Box(modifier = modifier.background(baseColor))
            } else if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                // Pre-S: CPU-blurred bitmap fallback. The bitmap is updated a few times per
                // second (see [rememberPreSFrostedBitmap]) — enough for a frosted-glass effect
                // without the per-frame cost that would tank pre-S hardware.
                val blurredBitmap = rememberPreSFrostedBitmap(
                    backdrop = backdrop,
                    blurRadiusPx = FrostedMiniPlayerBlurRadiusPx,
                )
                var positionInRoot by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier =
                        modifier
                            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
                            .background(baseColor),
                ) {
                    if (blurredBitmap != null) {
                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        alpha = FrostedMiniPlayerOverlayAlpha
                                        clip = true
                                    }.drawBehind {
                                        val offset = backdrop.contentOffsetInRoot - positionInRoot
                                        translate(offset.x, offset.y) {
                                            drawImage(blurredBitmap)
                                        }
                                    },
                        )
                    }
                }
            } else {
                var positionInRoot by remember { mutableStateOf(Offset.Zero) }
                Box(
                    modifier =
                        modifier
                            .onGloballyPositioned { positionInRoot = it.positionInRoot() }
                            .background(baseColor),
                ) {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    renderEffect =
                                        BlurEffect(
                                            radiusX = FrostedMiniPlayerBlurRadiusPx,
                                            radiusY = FrostedMiniPlayerBlurRadiusPx,
                                            edgeTreatment = TileMode.Clamp,
                                        )
                                    alpha = FrostedMiniPlayerOverlayAlpha
                                    clip = true
                                }.drawBehind {
                                    val offset = backdrop.contentOffsetInRoot - positionInRoot
                                    translate(offset.x, offset.y) {
                                        drawLayer(backdrop.layer)
                                    }
                                },
                    )
                }
            }
        }

        MiniPlayerBackgroundStyle.GRADIENT -> {
            val colors = requireNotNull(palette)
            Box(modifier = modifier) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colorStops =
                                        arrayOf(
                                            0f to colors.first.copy(alpha = 0.95f),
                                            0.52f to colors.second.copy(alpha = 0.82f),
                                            1f to colors.third.copy(alpha = 0.72f),
                                        ),
                                ),
                            ),
                )
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.32f)),
                )
            }
        }

        MiniPlayerBackgroundStyle.GLOW -> {
            val colors = requireNotNull(palette)
            Box(
                modifier =
                    modifier.drawWithCache {
                        val width = size.width
                        val height = size.height
                        val startGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.first.copy(alpha = 0.82f), colors.first.copy(alpha = 0.38f), Color.Transparent),
                                center = Offset(width * 0.12f, height * 0.42f),
                                radius = width * 0.72f,
                            )
                        val endGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.second.copy(alpha = 0.78f), colors.second.copy(alpha = 0.34f), Color.Transparent),
                                center = Offset(width * 0.88f, height * 0.58f),
                                radius = width * 0.72f,
                            )
                        val topGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.third.copy(alpha = 0.58f), Color.Transparent),
                                center = Offset(width * 0.52f, height * 0.05f),
                                radius = width * 0.54f,
                            )
                        val bottomGlow =
                            Brush.radialGradient(
                                colors = listOf(colors.fourth.copy(alpha = 0.46f), Color.Transparent),
                                center = Offset(width * 0.46f, height * 1.05f),
                                radius = width * 0.54f,
                            )

                        onDrawBehind {
                            drawRect(Color.Black)
                            drawRect(startGlow)
                            drawRect(endGlow)
                            drawRect(topGlow)
                            drawRect(bottomGlow)
                            drawRect(Color.Black.copy(alpha = 0.24f))
                        }
                    },
            )
        }
    }
}

@Immutable
private data class MiniPlayerBackgroundPalette(
    val first: Color,
    val second: Color,
    val third: Color,
    val fourth: Color,
) {
    companion object {
        fun from(colors: List<Color>): MiniPlayerBackgroundPalette? {
            val first = colors.firstOrNull() ?: return null
            val second = colors.getOrElse(1) { first }
            val third = colors.getOrElse(2) { second }
            val fourth = colors.getOrElse(3) { first }
            return MiniPlayerBackgroundPalette(
                first = first,
                second = second,
                third = third,
                fourth = fourth,
            )
        }
    }
}
