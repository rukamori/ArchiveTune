/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Shader
import android.os.Build
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.key
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R

@Immutable
internal data class WidgetPlaybackState(
    val title: String,
    val artist: String,
    val isPlaying: Boolean,
    val artPath: String?,
    val isAvailable: Boolean,
    val dominantColor: Int?,
    val playbackPosition: Float,
)

@Immutable
internal data class WidgetPalette(
    val primary: ColorProvider,
    val onPrimary: ColorProvider,
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val primaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val progress: ColorProvider,
    val progressTrack: ColorProvider,
    val artworkFallback: ColorProvider,
)

internal fun Preferences.toWidgetPlaybackState(context: Context): WidgetPlaybackState {
    val isAvailable = this[MusicWidgetKeys.IS_AVAILABLE] ?: false
    val rawTitle = this[MusicWidgetKeys.TRACK_TITLE].orEmpty()
    val rawArtist = this[MusicWidgetKeys.TRACK_ARTIST].orEmpty()

    return WidgetPlaybackState(
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
    content: @Composable (WidgetPalette) -> Unit,
) {
    GlanceTheme(
        colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            ArchiveTuneWidgetColors.providers
        },
    ) {
        content(rememberWidgetPalette(dominantColor))
    }
}

@Composable
private fun rememberWidgetPalette(dominantColor: Int?): WidgetPalette {
    val colors = GlanceTheme.colors
    return remember(dominantColor, colors) {
        if (dominantColor == null) {
            WidgetPalette(
                primary = colors.primary,
                onPrimary = colors.onPrimary,
                surface = colors.surface,
                onSurface = colors.onSurface,
                onSurfaceVariant = colors.onSurfaceVariant,
                primaryContainer = colors.primaryContainer,
                secondaryContainer = colors.secondaryContainer,
                progress = colors.primary,
                progressTrack = colors.surfaceVariant,
                artworkFallback = colors.surfaceVariant,
            )
        } else {
            val dominant = Color(dominantColor).copy(alpha = 1f)
            val primary = androidx.glance.color.ColorProvider(
                day = dominant.blendWith(Color.Black, 0.45f),
                night = dominant.blendWith(Color.White, 0.65f),
            )
            val surface = androidx.glance.color.ColorProvider(
                day = dominant.blendWith(Color.White, 0.94f),
                night = dominant.blendWith(Color.Black, 0.88f),
            )
            val container = androidx.glance.color.ColorProvider(
                day = dominant.blendWith(Color.White, 0.84f),
                night = dominant.blendWith(Color.Black, 0.72f),
            )
            WidgetPalette(
                primary = primary,
                onPrimary = androidx.glance.color.ColorProvider(Color.White, Color.Black),
                surface = surface,
                onSurface = colors.onSurface,
                onSurfaceVariant = colors.onSurfaceVariant,
                primaryContainer = container,
                secondaryContainer = container,
                progress = primary,
                progressTrack = container,
                artworkFallback = container,
            )
        }
    }
}

@Composable
internal fun WidgetArtwork(
    artPath: String?,
    context: Context,
    contentDescription: String?,
    targetSize: Dp,
    cornerRadius: Dp,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fallbackIconSize: Dp = targetSize * 0.54f,
    circular: Boolean = false,
) {
    val targetPx = (targetSize.value * context.resources.displayMetrics.density)
        .toInt()
        .coerceIn(48, 1024)
    val bitmap = key(artPath, targetPx, circular) {
        produceState<Bitmap?>(initialValue = null) {
            value = withContext(Dispatchers.IO) {
                artPath?.let { WidgetArtworkCache.decode(it, targetPx, circular) }
            }
        }
    }.value

    Box(modifier = modifier) {
        if (bitmap != null) {
            Image(
                provider = ImageProvider(bitmap),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(cornerRadius),
            )
        } else {
            Box(
                modifier =
                    GlanceModifier
                        .fillMaxSize()
                        .cornerRadius(cornerRadius)
                        .background(palette.artworkFallback),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.music_note),
                    contentDescription = contentDescription,
                    contentScale = ContentScale.Fit,
                    colorFilter = ColorFilter.tint(palette.onSurfaceVariant),
                    modifier = GlanceModifier.size(fallbackIconSize),
                )
            }
        }
    }
}

@Composable
internal fun WidgetControlButton(
    modifier: GlanceModifier,
    action: Action,
    @DrawableRes icon: Int,
    contentDescription: String,
    backgroundColor: ColorProvider,
    contentColor: ColorProvider,
    cornerRadius: Dp,
    iconSize: Dp = 22.dp,
) {
    Box(
        modifier =
            modifier
                .background(backgroundColor)
                .cornerRadius(cornerRadius)
                .clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(contentColor),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

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

@Composable
internal fun WidgetTrackInfo(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    titleSize: Int = 16,
    titleLines: Int = 1,
    showArtist: Boolean = true,
) {
    Column(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = state.title,
            style = TextStyle(color = palette.onSurface, fontSize = titleSize.sp, fontWeight = FontWeight.Bold),
            maxLines = titleLines,
        )
        if (showArtist) {
            Spacer(GlanceModifier.height(2.dp))
            Text(
                text = state.artist,
                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun WidgetPlayPauseButton(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier.size(48.dp),
    cornerRadius: Dp = 24.dp,
) {
    WidgetControlButton(
        modifier = modifier,
        action = playPauseAction(),
        icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
        contentDescription = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.play),
        backgroundColor = palette.primary,
        contentColor = palette.onPrimary,
        cornerRadius = cornerRadius,
        iconSize = 28.dp,
    )
}

@Composable
internal fun WidgetSkipButton(
    forward: Boolean,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier.size(48.dp),
) {
    WidgetControlButton(
        modifier = modifier,
        action = if (forward) skipNextAction() else skipPreviousAction(),
        icon = if (forward) R.drawable.skip_next else R.drawable.skip_previous,
        contentDescription = context.getString(if (forward) R.string.next else R.string.widget_previous),
        backgroundColor = TransparentWidgetColor,
        contentColor = palette.onSurface,
        cornerRadius = 24.dp,
        iconSize = 24.dp,
    )
}

@Composable
internal fun WidgetTransportControls(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        WidgetSkipButton(forward = false, palette = palette, context = context)
        WidgetPlayPauseButton(state = state, palette = palette, context = context)
        WidgetSkipButton(forward = true, palette = palette, context = context)
    }
}

@Composable
internal fun WidgetPlaybackProgress(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    LinearProgressIndicator(
        progress = state.playbackPosition,
        color = palette.progress,
        backgroundColor = palette.progressTrack,
        modifier = modifier.fillMaxWidth().height(4.dp).cornerRadius(2.dp),
    )
}

private val TransparentWidgetColor = ColorProvider(Color.Transparent)

private object WidgetArtworkCache {
    private const val CacheSizeBytes = 4 * 1024 * 1024

    private val cache =
        object : LruCache<String, Bitmap>(CacheSizeBytes) {
            override fun sizeOf(
                key: String,
                value: Bitmap,
            ): Int = value.byteCount
        }

    fun decode(
        path: String,
        targetPx: Int,
        circular: Boolean,
    ): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val cacheKey = "${file.absolutePath}:${file.lastModified()}:$targetPx:$circular"
        cache.get(cacheKey)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, targetPx, targetPx)
            }
        val decoded = BitmapFactory.decodeFile(file.absolutePath, options) ?: return null
        val scale = targetPx.toFloat() / maxOf(decoded.width, decoded.height)
        val bitmap = if (scale < 1f) {
            Bitmap.createScaledBitmap(
                decoded,
                (decoded.width * scale).toInt().coerceAtLeast(1),
                (decoded.height * scale).toInt().coerceAtLeast(1),
                true,
            ).also { if (it !== decoded) decoded.recycle() }
        } else {
            decoded
        }
        val artwork = if (circular) {
            val side = minOf(bitmap.width, bitmap.height)
            val result = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
            val shader = BitmapShader(bitmap, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
            shader.setLocalMatrix(Matrix().apply {
                setTranslate((side - bitmap.width) / 2f, (side - bitmap.height) / 2f)
            })
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            Canvas(result).drawCircle(side / 2f, side / 2f, side / 2f, paint)
            bitmap.recycle()
            result
        } else {
            bitmap
        }
        cache.put(cacheKey, artwork)
        return artwork
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
        while (halfHeight / sampleSize >= requestedHeight || halfWidth / sampleSize >= requestedWidth) {
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
