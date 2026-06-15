/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.HazeDefaults
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import moe.rukamori.archivetune.ui.theme.LocalLiquidGlassEnabled
import moe.rukamori.archivetune.ui.theme.LocalHazeState
import moe.rukamori.archivetune.ui.theme.LiquidGlassDefaults
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyle
import moe.rukamori.archivetune.constants.MiniPlayerBackgroundStyleKey
import moe.rukamori.archivetune.constants.SwipeSensitivityKey
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private const val MiniPlayerPaletteCacheSize = 24

@Composable
fun MiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
) {
    NewMiniPlayer(
        position = position,
        duration = duration,
        modifier = modifier,
        pureBlack = pureBlack
    )
}

@Composable
private fun NewMiniPlayer(
    position: Long,
    duration: Long,
    modifier: Modifier = Modifier,
    pureBlack: Boolean,
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
    var gradientColors by remember {
        mutableStateOf<List<Color>>(emptyList())
    }
    val gradientColorsCache = remember {
        object : LinkedHashMap<String, List<Color>>(MiniPlayerPaletteCacheSize, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<Color>>?): Boolean {
                return size > MiniPlayerPaletteCacheSize
            }
        }
    }
    val fallbackColor = MaterialTheme.colorScheme.surface.toArgb()
    val shouldUseArtworkBackground = miniPlayerBackgroundStyle != MiniPlayerBackgroundStyle.THEME

    LaunchedEffect(
        mediaMetadata?.id,
        mediaMetadata?.thumbnailUrl,
        shouldUseArtworkBackground,
        fallbackColor,
    ) {
        if (!shouldUseArtworkBackground) {
            gradientColors = emptyList()
            return@LaunchedEffect
        }

        val currentMetadata = mediaMetadata
        val thumbnailUrl = currentMetadata?.thumbnailUrl
        if (currentMetadata == null || thumbnailUrl.isNullOrBlank()) {
            gradientColors = emptyList()
            return@LaunchedEffect
        }

        val cachedColors = gradientColorsCache[currentMetadata.id]
        if (cachedColors != null) {
            gradientColors = cachedColors
            return@LaunchedEffect
        }

        val request = ImageRequest.Builder(context)
            .data(thumbnailUrl)
            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
            .allowHardware(false)
            .build()

        val extractedColors = runCatching {
            val result = withContext(Dispatchers.IO) {
                context.imageLoader.execute(request)
            }
            val bitmap = result.image?.toBitmap() ?: return@runCatching emptyList()
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap)
                    .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                    .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                    .generate()
            }
            PlayerColorExtractor.extractGradientColors(
                palette = palette,
                fallbackColor = fallbackColor,
            )
        }.getOrDefault(emptyList())

        if (extractedColors.isNotEmpty()) {
            gradientColorsCache[currentMetadata.id] = extractedColors
        }
        gradientColors = extractedColors
    }

    val backgroundPalette = remember(gradientColors) {
        MiniPlayerBackgroundPalette.from(gradientColors)
    }
    val effectiveBackgroundStyle =
        if (shouldUseArtworkBackground && backgroundPalette != null) {
            miniPlayerBackgroundStyle
        } else {
            MiniPlayerBackgroundStyle.THEME
        }

    val contentColors = rememberMiniPlayerContentColors(
        useArtworkBackground = effectiveBackgroundStyle != MiniPlayerBackgroundStyle.THEME,
    )

    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    val hazeState = LocalHazeState.current
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val miniPlayerShape = RoundedCornerShape(32.dp)

    val glassModifier = if (isLiquidGlassEnabled) {
        val hazeMod = if (hazeState != null && effectiveBackgroundStyle == MiniPlayerBackgroundStyle.THEME) {
            Modifier.hazeEffect(
                state = hazeState,
                style = HazeStyle(
                    tints = listOf(HazeTint(color = LiquidGlassDefaults.surfaceContainerColor(isDark))),
                    blurRadius = 24.dp,
                    noiseFactor = 0.1f,
                    backgroundColor = MaterialTheme.colorScheme.background
                )
            )
        } else Modifier
        hazeMod.border(1.dp, LiquidGlassDefaults.borderBrush(isDark), miniPlayerShape)
    } else Modifier

    SwipeableMiniPlayerBox(
        modifier = modifier,
        swipeSensitivity = swipeSensitivity,
        swipeThumbnail = swipeThumbnail,
        playerConnection = playerConnection,
        layoutDirection = layoutDirection,
        coroutineScope = coroutineScope,
        pureBlack = pureBlack,
        useLegacyBackground = false
    ) { offsetX ->
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .clip(miniPlayerShape)
                .then(glassModifier)
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
private fun rememberMiniPlayerContentColors(
    useArtworkBackground: Boolean,
): MiniPlayerContentColors {
    val colorScheme = MaterialTheme.colorScheme
    val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
    return remember(
        useArtworkBackground,
        isLiquidGlassEnabled,
        colorScheme.primary,
        colorScheme.outline,
        colorScheme.onSurface,
        colorScheme.onSurfaceVariant,
        colorScheme.surface,
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
                primaryButtonContainer = Color.White.copy(alpha = 0.16f),
                buttonBorder = Color.White.copy(alpha = 0.24f),
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
                artworkContainer = if (isLiquidGlassEnabled) colorScheme.surfaceVariant.copy(alpha = 0.30f) else colorScheme.surfaceVariant,
                artworkBorder = colorScheme.outline.copy(alpha = 0.2f),
                primaryButtonContainer = if (isLiquidGlassEnabled) colorScheme.surface.copy(alpha = 0.15f) else colorScheme.surface,
                buttonBorder = colorScheme.outline.copy(alpha = 0.3f),
                buttonIcon = colorScheme.onSurface,
                disabledButtonIcon = colorScheme.onSurface.copy(alpha = 0.38f),
                togetherContainer = if (isLiquidGlassEnabled) colorScheme.primaryContainer.copy(alpha = 0.15f) else colorScheme.primaryContainer,
                togetherContent = colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun MiniPlayerBackground(
    style: MiniPlayerBackgroundStyle,
    palette: MiniPlayerBackgroundPalette?,
    modifier: Modifier = Modifier,
) {
    when (style) {
        MiniPlayerBackgroundStyle.THEME -> {
            val isLiquidGlassEnabled = LocalLiquidGlassEnabled.current
            Box(
                modifier = modifier.background(
                    if (isLiquidGlassEnabled) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer
                )
            )
        }

        MiniPlayerBackgroundStyle.GRADIENT -> {
            val colors = requireNotNull(palette)
            Box(modifier = modifier) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0f to colors.first.copy(alpha = 0.95f),
                                    0.52f to colors.second.copy(alpha = 0.82f),
                                    1f to colors.third.copy(alpha = 0.72f),
                                )
                            )
                        )
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.32f))
                )
            }
        }

        MiniPlayerBackgroundStyle.GLOW -> {
            val colors = requireNotNull(palette)
            Box(
                modifier = modifier.drawWithCache {
                    val width = size.width
                    val height = size.height
                    val startGlow = Brush.radialGradient(
                        colors = listOf(colors.first.copy(alpha = 0.82f), colors.first.copy(alpha = 0.38f), Color.Transparent),
                        center = Offset(width * 0.12f, height * 0.42f),
                        radius = width * 0.72f,
                    )
                    val endGlow = Brush.radialGradient(
                        colors = listOf(colors.second.copy(alpha = 0.78f), colors.second.copy(alpha = 0.34f), Color.Transparent),
                        center = Offset(width * 0.88f, height * 0.58f),
                        radius = width * 0.72f,
                    )
                    val topGlow = Brush.radialGradient(
                        colors = listOf(colors.third.copy(alpha = 0.58f), Color.Transparent),
                        center = Offset(width * 0.52f, height * 0.05f),
                        radius = width * 0.54f,
                    )
                    val bottomGlow = Brush.radialGradient(
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
                }
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
