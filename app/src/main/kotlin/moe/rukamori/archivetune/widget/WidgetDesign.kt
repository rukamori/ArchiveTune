/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.toPath
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R

@Immutable
internal data class WidgetPlaybackState(
    val mediaId: String?,
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artPath: String?,
    val isAvailable: Boolean,
    val dominantColor: Int?,
    val playbackPosition: Float,
)

internal enum class WidgetSurfaceStyle {
    Ambient,
    Tonal,
}

internal enum class WidgetArtShape {
    Square,
    Rounded,
    Circle,
    Cookie,
    Clover,
    Arch,
}

@Immutable
internal data class WidgetDayNightColor(
    val day: Int,
    val night: Int,
)

@Immutable
internal data class WidgetPalette(
    val style: WidgetSurfaceStyle,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val accent: ColorProvider,
    val onAccent: ColorProvider,
    val container: ColorProvider,
    val onContainer: ColorProvider,
    val track: ColorProvider,
    val divider: ColorProvider,
    val artworkFallback: ColorProvider,
    val motionAccent: WidgetDayNightColor,
    val motionContainer: WidgetDayNightColor,
    val backdropTint: Int,
)

internal fun Preferences.toWidgetPlaybackState(context: Context): WidgetPlaybackState {
    val isAvailable = this[MusicWidgetKeys.IS_AVAILABLE] ?: false
    val rawTitle = this[MusicWidgetKeys.TRACK_TITLE].orEmpty()
    val rawArtist = this[MusicWidgetKeys.TRACK_ARTIST].orEmpty()

    return WidgetPlaybackState(
        mediaId = this[MusicWidgetKeys.CURRENT_MEDIA_ID]?.takeIf { isAvailable && it.isNotBlank() },
        title =
            rawTitle.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.no_track_playing),
        artist =
            when {
                !isAvailable -> context.getString(R.string.widget_tap_to_open)
                rawArtist.isNotBlank() -> rawArtist
                else -> context.getString(R.string.unknown_artist)
            },
        isPlaying = this[MusicWidgetKeys.IS_PLAYING] ?: false,
        artPath = this[MusicWidgetKeys.ART_PATH],
        isAvailable = isAvailable,
        dominantColor = this[MusicWidgetKeys.DOMINANT_COLOR],
        playbackPosition = (this[MusicWidgetKeys.PLAYBACK_POSITION] ?: 0f).coerceIn(0f, 1f),
    )
}

@Composable
internal fun WidgetTheme(
    dominantColor: Int?,
    style: WidgetSurfaceStyle,
    context: Context,
    content: @Composable (WidgetPalette) -> Unit,
) {
    GlanceTheme(
        colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            ArchiveTuneWidgetColors.providers
        },
    ) {
        content(rememberWidgetPalette(dominantColor, style, context))
    }
}

@Composable
private fun rememberWidgetPalette(
    dominantColor: Int?,
    style: WidgetSurfaceStyle,
    context: Context,
): WidgetPalette {
    val colors = GlanceTheme.colors
    return remember(dominantColor, style, colors, context) {
        when (style) {
            WidgetSurfaceStyle.Ambient -> ambientPalette(dominantColor)
            WidgetSurfaceStyle.Tonal -> tonalPalette(dominantColor, colors, context)
        }
    }
}

private fun ambientPalette(dominantColor: Int?): WidgetPalette {
    val base = (dominantColor?.let { Color(it).copy(alpha = 1f) } ?: AmbientFallback).toAmbientBase()
    val white = Color.White
    val container = base.blendWith(white, 0.18f)
    return WidgetPalette(
        style = WidgetSurfaceStyle.Ambient,
        surface = ColorProvider(base),
        onSurface = ColorProvider(white),
        onSurfaceVariant = ColorProvider(white.copy(alpha = 0.72f)),
        accent = ColorProvider(white),
        onAccent = ColorProvider(base),
        container = ColorProvider(container),
        onContainer = ColorProvider(white),
        track = ColorProvider(base.blendWith(white, 0.28f)),
        divider = ColorProvider(base.blendWith(white, 0.12f)),
        artworkFallback = ColorProvider(base.blendWith(white, 0.14f)),
        motionAccent = WidgetDayNightColor(white.toArgb(), white.toArgb()),
        motionContainer = WidgetDayNightColor(container.toArgb(), container.toArgb()),
        backdropTint = base.toArgb(),
    )
}

private fun tonalPalette(
    dominantColor: Int?,
    colors: androidx.glance.color.ColorProviders,
    context: Context,
): WidgetPalette {
    val palette =
        if (dominantColor == null) {
            WidgetPalette(
                style = WidgetSurfaceStyle.Tonal,
                surface = colors.surface,
                onSurface = colors.onSurface,
                onSurfaceVariant = colors.onSurfaceVariant,
                accent = colors.primary,
                onAccent = colors.onPrimary,
                container = colors.secondaryContainer,
                onContainer = colors.onSecondaryContainer,
                track = colors.surfaceVariant,
                divider = colors.surfaceVariant,
                artworkFallback = colors.surfaceVariant,
                motionAccent = WidgetDayNightColor(0, 0),
                motionContainer = WidgetDayNightColor(0, 0),
                backdropTint = 0,
            )
        } else {
            val dominant = Color(dominantColor).copy(alpha = 1f)
            val primaryDay = dominant.blendWith(Color.Black, 0.45f)
            val primaryNight = dominant.blendWith(Color.White, 0.65f)
            val container = androidx.glance.color.ColorProvider(
                day = dominant.blendWith(Color.White, 0.84f),
                night = dominant.blendWith(Color.Black, 0.70f),
            )
            WidgetPalette(
                style = WidgetSurfaceStyle.Tonal,
                surface = androidx.glance.color.ColorProvider(
                    day = dominant.blendWith(Color.White, 0.93f),
                    night = dominant.blendWith(Color.Black, 0.86f),
                ),
                onSurface = colors.onSurface,
                onSurfaceVariant = colors.onSurfaceVariant,
                accent = androidx.glance.color.ColorProvider(day = primaryDay, night = primaryNight),
                onAccent = androidx.glance.color.ColorProvider(
                    day = Color.White,
                    night = dominant.blendWith(Color.Black, 0.80f),
                ),
                container = container,
                onContainer = colors.onSurface,
                track = container,
                divider = container,
                artworkFallback = container,
                motionAccent = WidgetDayNightColor(0, 0),
                motionContainer = WidgetDayNightColor(0, 0),
                backdropTint = 0,
            )
        }
    return palette.copy(
        motionAccent = palette.accent.resolveDayNight(context),
        motionContainer = palette.container.resolveDayNight(context),
    )
}

private fun ColorProvider.resolveDayNight(context: Context): WidgetDayNightColor =
    WidgetDayNightColor(
        day = getColor(context.withNightMode(false)).toArgb(),
        night = getColor(context.withNightMode(true)).toArgb(),
    )

private fun Context.withNightMode(night: Boolean): Context {
    val configuration = Configuration(resources.configuration)
    configuration.uiMode =
        (configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or
        (if (night) Configuration.UI_MODE_NIGHT_YES else Configuration.UI_MODE_NIGHT_NO)
    return createConfigurationContext(configuration)
}

internal fun GlanceModifier.widgetRootCorners(): GlanceModifier =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        this
    }

@Composable
internal fun WidgetSurface(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    contentPadding: Dp = 14.dp,
    backdrop: Boolean = palette.style == WidgetSurfaceStyle.Ambient,
    content: @Composable () -> Unit,
) {
    Box(
        modifier =
            GlanceModifier
                .fillMaxSize()
                .appWidgetBackground()
                .widgetRootCorners()
                .background(palette.surface)
                .clickable(openArchiveTuneAction(context)),
    ) {
        if (backdrop && state.artPath != null) {
            WidgetBackdrop(state.artPath, palette)
        }
        Box(modifier = GlanceModifier.fillMaxSize().padding(contentPadding)) {
            content()
        }
    }
}

@Composable
private fun WidgetBackdrop(
    artPath: String,
    palette: WidgetPalette,
) {
    val bitmap = rememberWidgetBitmap("backdrop:$artPath:${palette.backdropTint}") {
        WidgetArtworkCache.backdrop(artPath, palette.backdropTint)
    }
    if (bitmap != null) {
        Image(
            provider = ImageProvider(bitmap),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = GlanceModifier.fillMaxSize(),
        )
    }
}

@Composable
internal fun WidgetArtwork(
    artPath: String?,
    context: Context,
    contentDescription: String?,
    targetSize: Dp,
    shape: WidgetArtShape,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    cornerRadius: Dp = 12.dp,
    fallbackIconSize: Dp = targetSize * 0.42f,
) {
    val density = context.resources.displayMetrics.density
    val targetPx = (targetSize.value * density).toInt().coerceIn(48, 1024)
    val radiusPx = cornerRadius.value * density
    val bitmap = rememberWidgetBitmap("art:$artPath:$targetPx:$shape:$radiusPx") {
        artPath?.let { WidgetArtworkCache.artwork(it, targetPx, shape, radiusPx) }
    }

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        } else {
            WidgetArtworkFallback(shape, palette, contentDescription, fallbackIconSize, cornerRadius)
        }
    }
}

@Composable
private fun WidgetArtworkFallback(
    shape: WidgetArtShape,
    palette: WidgetPalette,
    contentDescription: String?,
    iconSize: Dp,
    cornerRadius: Dp,
) {
    val shapeModifier =
        when (shape) {
            WidgetArtShape.Square -> GlanceModifier.fillMaxSize().background(palette.artworkFallback)
            WidgetArtShape.Rounded, WidgetArtShape.Arch ->
                GlanceModifier.fillMaxSize().cornerRadius(cornerRadius).background(palette.artworkFallback)
            WidgetArtShape.Circle ->
                GlanceModifier.fillMaxSize().background(
                    imageProvider = ImageProvider(R.drawable.widget_shape_circle),
                    colorFilter = ColorFilter.tint(palette.artworkFallback),
                )
            WidgetArtShape.Cookie, WidgetArtShape.Clover ->
                GlanceModifier.fillMaxSize().background(
                    imageProvider = ImageProvider(R.drawable.widget_shape_cookie),
                    colorFilter = ColorFilter.tint(palette.artworkFallback),
                )
        }
    Box(modifier = shapeModifier, contentAlignment = Alignment.Center) {
        Image(
            provider = ImageProvider(R.drawable.music_note),
            contentDescription = contentDescription,
            contentScale = ContentScale.Fit,
            colorFilter = ColorFilter.tint(palette.onSurfaceVariant),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

@Composable
private fun rememberWidgetBitmap(
    cacheKey: String,
    loader: () -> Bitmap?,
): Bitmap? =
    key(cacheKey) {
        produceState<Bitmap?>(initialValue = null) {
            value = withContext(Dispatchers.IO) { loader() }
        }
    }.value

internal fun openArchiveTuneAction(context: Context): Action =
    actionStartActivity(
        Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
    )

internal fun playPauseAction(): Action = actionRunCallback<PlayPauseAction>()

internal fun skipNextAction(): Action = actionRunCallback<SkipNextAction>()

internal fun skipPreviousAction(): Action = actionRunCallback<SkipPrevAction>()

internal fun playQueueItemAction(item: WidgetMediaItem): Action =
    actionRunCallback<PlayQueueItemAction>(
        actionParametersOf(
            WidgetActionKeys.MediaId to item.id,
            WidgetActionKeys.QueueIndex to item.queueIndex,
        ),
    )

internal fun playSongAction(item: WidgetMediaItem): Action =
    actionRunCallback<PlaySongAction>(actionParametersOf(WidgetActionKeys.MediaId to item.id))

internal object WidgetActionKeys {
    val MediaId = ActionParameters.Key<String>("widget_media_id")
    val QueueIndex = ActionParameters.Key<Int>("widget_queue_index")
}

internal object ArchiveTuneWidgetColors {
    val providers =
        ColorProviders(
            light =
                lightColorScheme(
                    primary = Color(0xFFB3181C),
                    onPrimary = Color(0xFFFFFFFF),
                    primaryContainer = Color(0xFFFFDAD6),
                    onPrimaryContainer = Color(0xFF410003),
                    secondary = Color(0xFF775651),
                    onSecondary = Color(0xFFFFFFFF),
                    secondaryContainer = Color(0xFFFFDAD6),
                    onSecondaryContainer = Color(0xFF2C1512),
                    surface = Color(0xFFFFF8F7),
                    onSurface = Color(0xFF231919),
                    onSurfaceVariant = Color(0xFF534342),
                    surfaceVariant = Color(0xFFF5DDDB),
                ),
            dark =
                darkColorScheme(
                    primary = Color(0xFFFFB3AD),
                    onPrimary = Color(0xFF680007),
                    primaryContainer = Color(0xFF93000D),
                    onPrimaryContainer = Color(0xFFFFDAD6),
                    secondary = Color(0xFFE7BDB8),
                    onSecondary = Color(0xFF442926),
                    secondaryContainer = Color(0xFF5D3F3C),
                    onSecondaryContainer = Color(0xFFFFDAD6),
                    surface = Color(0xFF1A1111),
                    onSurface = Color(0xFFEEDEDD),
                    onSurfaceVariant = Color(0xFFD8C2C0),
                    surfaceVariant = Color(0xFF534342),
                ),
        )
}

private val AmbientFallback = Color(0xFF5D2A2A)

private fun Color.toAmbientBase(): Color {
    var color = this
    var steps = 0
    while (color.luminance() > AmbientMaxLuminance && steps < 12) {
        color = color.blendWith(Color.Black, 0.18f)
        steps++
    }
    while (color.luminance() < AmbientMinLuminance && steps < 24) {
        color = color.blendWith(Color.White, 0.08f)
        steps++
    }
    return color
}

private const val AmbientMaxLuminance = 0.09f
private const val AmbientMinLuminance = 0.025f

private object WidgetArtworkCache {
    private const val CacheSizeBytes = 6 * 1024 * 1024
    private const val BackdropSizePx = 128
    private const val BackdropBlurPx = 10

    private val cache =
        object : LruCache<String, Bitmap>(CacheSizeBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    private val shapePolygons: Map<WidgetArtShape, RoundedPolygon> by lazy {
        mapOf(
            WidgetArtShape.Cookie to MaterialShapes.Cookie9Sided,
            WidgetArtShape.Clover to MaterialShapes.Clover4Leaf,
            WidgetArtShape.Arch to MaterialShapes.Arch,
        )
    }

    fun artwork(
        path: String,
        targetPx: Int,
        shape: WidgetArtShape,
        radiusPx: Float,
    ): Bitmap? {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return null
        val cacheKey = "art:${file.absolutePath}:${file.lastModified()}:$targetPx:$shape:$radiusPx"
        cache.get(cacheKey)?.let { return it }

        val square = decodeSquare(file, targetPx) ?: return null
        val result =
            if (shape == WidgetArtShape.Square) {
                square
            } else {
                mask(square, shape, radiusPx).also { square.recycle() }
            }
        cache.put(cacheKey, result)
        return result
    }

    fun backdrop(
        path: String,
        tint: Int,
    ): Bitmap? {
        val file = File(path)
        if (!file.isFile || file.length() == 0L) return null
        val cacheKey = "backdrop:${file.absolutePath}:${file.lastModified()}:$tint"
        cache.get(cacheKey)?.let { return it }

        val source = decodeSquare(file, BackdropSizePx) ?: return null
        val tiny = Bitmap.createScaledBitmap(source, BackdropBlurPx, BackdropBlurPx, true)
        if (tiny !== source) source.recycle()
        val mid = Bitmap.createScaledBitmap(tiny, BackdropSizePx / 4, BackdropSizePx / 4, true)
        tiny.recycle()
        val soft = Bitmap.createScaledBitmap(mid, BackdropSizePx, BackdropSizePx, true)
        mid.recycle()

        val result = soft.copy(Bitmap.Config.ARGB_8888, true)
        soft.recycle()
        val canvas = Canvas(result)
        val tintColor = Color(tint)
        canvas.drawColor(tintColor.copy(alpha = 0.58f).toArgb())
        val size = BackdropSizePx.toFloat()
        val gradient = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = LinearGradient(
                0f, 0f, 0f, size,
                tintColor.copy(alpha = 0f).toArgb(),
                Color.Black.copy(alpha = 0.34f).toArgb(),
                Shader.TileMode.CLAMP,
            )
        }
        canvas.drawRect(0f, 0f, size, size, gradient)
        cache.put(cacheKey, result)
        return result
    }

    private fun decodeSquare(
        file: File,
        targetPx: Int,
    ): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, targetPx, targetPx)
            }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val side = minOf(decoded.width, decoded.height)
        val cropped =
            if (decoded.width != decoded.height) {
                Bitmap.createBitmap(
                    decoded,
                    (decoded.width - side) / 2,
                    (decoded.height - side) / 2,
                    side,
                    side,
                ).also { if (it !== decoded) decoded.recycle() }
            } else {
                decoded
            }
        return if (side > targetPx) {
            Bitmap.createScaledBitmap(cropped, targetPx, targetPx, true)
                .also { if (it !== cropped) cropped.recycle() }
        } else {
            cropped
        }
    }

    private fun mask(
        source: Bitmap,
        shape: WidgetArtShape,
        radiusPx: Float,
    ): Bitmap {
        val side = source.width
        val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = BitmapShader(source, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
        }
        val bounds = RectF(0f, 0f, side.toFloat(), side.toFloat())
        val path =
            when (shape) {
                WidgetArtShape.Square -> Path().apply { addRect(bounds, Path.Direction.CW) }
                WidgetArtShape.Rounded -> Path().apply {
                    val scaledRadius = radiusPx.coerceAtMost(side / 2f)
                    addRoundRect(bounds, scaledRadius, scaledRadius, Path.Direction.CW)
                }
                WidgetArtShape.Circle -> Path().apply { addOval(bounds, Path.Direction.CW) }
                WidgetArtShape.Cookie, WidgetArtShape.Clover, WidgetArtShape.Arch ->
                    polygonPath(shapePolygons.getValue(shape), bounds)
            }
        Canvas(result).drawPath(path, paint)
        return result
    }

    private fun polygonPath(
        polygon: RoundedPolygon,
        bounds: RectF,
    ): Path {
        val path = polygon.toPath(Path())
        val polygonBounds = RectF()
        path.computeBounds(polygonBounds, true)
        val matrix = Matrix().apply { setRectToRect(polygonBounds, bounds, Matrix.ScaleToFit.CENTER) }
        path.transform(matrix)
        return path
    }
}

private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    requestedWidth: Int,
    requestedHeight: Int,
): Int {
    var sampleSize = 1
    val width = options.outWidth
    val height = options.outHeight

    if (height > requestedHeight || width > requestedWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2
        while (halfHeight / sampleSize >= requestedHeight && halfWidth / sampleSize >= requestedWidth) {
            sampleSize *= 2
        }
    }

    return sampleSize.coerceAtLeast(1)
}

private fun Color.blendWith(
    other: Color,
    fraction: Float,
): Color {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    val inverse = 1f - clampedFraction
    return Color(
        red = red * inverse + other.red * clampedFraction,
        green = green * inverse + other.green * clampedFraction,
        blue = blue * inverse + other.blue * clampedFraction,
        alpha = alpha * inverse + other.alpha * clampedFraction,
    )
}
