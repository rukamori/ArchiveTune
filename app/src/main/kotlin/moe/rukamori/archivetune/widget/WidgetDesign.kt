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
import android.util.LruCache
import androidx.annotation.DrawableRes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.material3.ColorProviders
import androidx.glance.unit.ColorProvider
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import java.io.File

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
    val surface: ColorProvider,
    val onSurface: ColorProvider,
    val onSurfaceVariant: ColorProvider,
    val primaryContainer: ColorProvider,
    val onPrimaryContainer: ColorProvider,
    val secondaryContainer: ColorProvider,
    val onSecondaryContainer: ColorProvider,
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
internal fun rememberWidgetPalette(dominantColor: Int?): WidgetPalette {
    if (dominantColor == null) {
        return WidgetPalette(
            surface = GlanceTheme.colors.surface,
            onSurface = GlanceTheme.colors.onSurface,
            onSurfaceVariant = GlanceTheme.colors.onSurfaceVariant,
            primaryContainer = GlanceTheme.colors.primaryContainer,
            onPrimaryContainer = GlanceTheme.colors.onPrimaryContainer,
            secondaryContainer = GlanceTheme.colors.secondaryContainer,
            onSecondaryContainer = GlanceTheme.colors.onSecondaryContainer,
            progress = GlanceTheme.colors.primary,
            progressTrack = GlanceTheme.colors.surfaceVariant,
            artworkFallback = GlanceTheme.colors.surfaceVariant,
        )
    }

    return remember(dominantColor) {
        val dominant = Color(dominantColor)
        val dark = dominant.isDark()
        val surface =
            if (dark) {
                dominant.blendWith(Color.Black, 0.24f)
            } else {
                dominant.blendWith(Color.White, 0.76f)
            }
        val onSurface = if (dark) Color.White else Color.Black
        val progress =
            if (dark) {
                Color.White
            } else {
                dominant.blendWith(Color.Black, 0.28f)
            }

        WidgetPalette(
            surface = ColorProvider(surface),
            onSurface = ColorProvider(onSurface),
            onSurfaceVariant = ColorProvider(onSurface.copy(alpha = 0.72f)),
            primaryContainer = ColorProvider(onSurface.copy(alpha = if (dark) 0.2f else 0.12f)),
            onPrimaryContainer = ColorProvider(onSurface),
            secondaryContainer = ColorProvider(onSurface.copy(alpha = if (dark) 0.13f else 0.08f)),
            onSecondaryContainer = ColorProvider(onSurface),
            progress = ColorProvider(progress),
            progressTrack = ColorProvider(onSurface.copy(alpha = if (dark) 0.22f else 0.14f)),
            artworkFallback = ColorProvider(onSurface.copy(alpha = if (dark) 0.11f else 0.08f)),
        )
    }
}

@Composable
internal fun WidgetArtwork(
    artPath: String?,
    context: Context,
    contentDescription: String,
    targetSize: Dp,
    cornerRadius: Dp,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fallbackIconSize: Dp = targetSize * 0.54f,
) {
    val bitmap =
        remember(artPath, targetSize) {
            artPath?.let { WidgetArtworkCache.decode(it, context, targetSize) }
        }

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
internal fun WidgetExpressiveControlPill(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .height(50.dp)
                .background(palette.secondaryContainer)
                .cornerRadius(25.dp),
    ) {
        Row(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .padding(horizontal = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetControlButton(
                modifier = GlanceModifier.size(44.dp),
                action = skipPreviousAction(),
                icon = R.drawable.skip_previous,
                contentDescription = context.getString(R.string.widget_previous),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
            WidgetControlButton(
                modifier =
                    GlanceModifier
                        .defaultWeight()
                        .height(44.dp),
                action = playPauseAction(),
                icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (state.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = palette.primaryContainer,
                contentColor = palette.onPrimaryContainer,
                cornerRadius = if (state.isPlaying) 13.dp else 22.dp,
                iconSize = 26.dp,
            )
            WidgetControlButton(
                modifier = GlanceModifier.size(44.dp),
                action = skipNextAction(),
                icon = R.drawable.skip_next,
                contentDescription = context.getString(R.string.next),
                backgroundColor = ColorProvider(Color.Transparent),
                contentColor = palette.onSecondaryContainer,
                cornerRadius = 22.dp,
            )
        }
    }
}

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
        context: Context,
        targetSize: Dp,
    ): Bitmap? {
        val file = File(path)
        if (!file.exists() || file.length() == 0L) return null

        val targetPx =
            (targetSize.value * context.resources.displayMetrics.density)
                .toInt()
                .coerceAtLeast(64)
        val cacheKey = "${file.absolutePath}:${file.lastModified()}:$targetPx"
        cache.get(cacheKey)?.let { return it }

        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options =
            BitmapFactory.Options().apply {
                inSampleSize = calculateInSampleSize(bounds, targetPx, targetPx)
            }
        return BitmapFactory.decodeFile(file.absolutePath, options)?.also {
            cache.put(cacheKey, it)
        }
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

private fun Color.isDark(): Boolean = red * 0.299f + green * 0.587f + blue * 0.114f < 0.52f

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
