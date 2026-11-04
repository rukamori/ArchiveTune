/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.os.Build
import android.widget.RemoteViews
import androidx.annotation.DrawableRes
import androidx.annotation.LayoutRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.text.NumberFormat
import moe.rukamori.archivetune.R

internal fun Context.widgetTextScale(): Float = resources.configuration.fontScale.coerceAtLeast(1f)

@Composable
internal fun WidgetOverline(
    text: String,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
    color: ColorProvider = palette.onSurfaceVariant,
) {
    Text(
        text = text.uppercase(context.resources.configuration.locales[0]),
        modifier = modifier,
        style = TextStyle(color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold),
        maxLines = 1,
    )
}

@Composable
internal fun WidgetHeadline(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: TextUnit = 16.sp,
    maxLines: Int = 1,
    color: ColorProvider = palette.onSurface,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(color = color, fontSize = fontSize, fontWeight = FontWeight.Bold, textAlign = textAlign),
        maxLines = maxLines,
    )
}

@Composable
internal fun WidgetSupporting(
    text: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    fontSize: TextUnit = 12.sp,
    maxLines: Int = 1,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text = text,
        modifier = modifier,
        style = TextStyle(color = palette.onSurfaceVariant, fontSize = fontSize, textAlign = textAlign),
        maxLines = maxLines,
    )
}

@Composable
internal fun WidgetTrackText(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
    titleSize: TextUnit = 16.sp,
    titleLines: Int = 1,
    showArtist: Boolean = true,
    textAlign: TextAlign = TextAlign.Start,
) {
    Column(modifier = modifier) {
        WidgetHeadline(state.title, palette, fontSize = titleSize, maxLines = titleLines, textAlign = textAlign)
        if (showArtist) {
            Spacer(GlanceModifier.height(2.dp))
            WidgetSupporting(state.artist, palette, GlanceModifier.fillMaxWidth(), textAlign = textAlign)
        }
    }
}

@Composable
internal fun WidgetBrandGlyph(
    palette: WidgetPalette,
    size: Dp = 18.dp,
) {
    Image(
        provider = ImageProvider(R.drawable.small_icon),
        contentDescription = null,
        colorFilter = ColorFilter.tint(palette.onSurface),
        modifier = GlanceModifier.size(size),
    )
}

@Composable
internal fun WidgetPlayPill(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
    emphasized: Boolean = false,
) {
    val label =
        context.getString(
            when {
                !state.isAvailable -> R.string.widget_open
                state.isPlaying -> R.string.widget_pause
                else -> R.string.play
            },
        )
    val icon =
        when {
            !state.isAvailable -> R.drawable.music_note
            state.isPlaying -> R.drawable.pause
            else -> R.drawable.play
        }
    val container = if (emphasized) palette.accent else palette.container
    val content = if (emphasized) palette.onAccent else palette.onContainer
    Row(
        modifier =
            modifier
                .height(34.dp)
                .cornerRadius(17.dp)
                .background(container)
                .clickable(if (state.isAvailable) playPauseAction() else openArchiveTuneAction(context))
                .padding(start = 10.dp, end = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = null,
            colorFilter = ColorFilter.tint(content),
            modifier = GlanceModifier.size(18.dp),
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = label,
            style = TextStyle(color = content, fontSize = 13.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
    }
}

@Composable
internal fun WidgetHeroPlayButton(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    size: Dp,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(
        modifier = modifier.size(size).clickable(playPauseAction()),
        contentAlignment = Alignment.Center,
    ) {
        if (state.isPlaying) {
            WidgetMotion(WidgetMotionKind.Cookie, palette.motionAccent, context, GlanceModifier.fillMaxSize())
        } else {
            Image(
                provider = ImageProvider(R.drawable.widget_shape_circle),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.accent),
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        Image(
            provider = ImageProvider(if (state.isPlaying) R.drawable.pause else R.drawable.play),
            contentDescription = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.play),
            colorFilter = ColorFilter.tint(palette.onAccent),
            modifier = GlanceModifier.size(size * 0.46f),
        )
    }
}

@Composable
internal fun WidgetIconButton(
    @DrawableRes icon: Int,
    contentDescription: String,
    action: Action,
    tint: ColorProvider,
    modifier: GlanceModifier = GlanceModifier.size(44.dp),
    container: ColorProvider? = null,
    cornerRadius: Dp = 22.dp,
    iconSize: Dp = 24.dp,
) {
    val base = if (container != null) modifier.cornerRadius(cornerRadius).background(container) else modifier
    Box(
        modifier = base.clickable(action),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = contentDescription,
            colorFilter = ColorFilter.tint(tint),
            modifier = GlanceModifier.size(iconSize),
        )
    }
}

@Composable
internal fun WidgetSkipButton(
    forward: Boolean,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier.size(44.dp),
    container: ColorProvider? = null,
    cornerRadius: Dp = 22.dp,
    iconSize: Dp = 24.dp,
) {
    WidgetIconButton(
        icon = if (forward) R.drawable.skip_next else R.drawable.skip_previous,
        contentDescription = context.getString(if (forward) R.string.next else R.string.widget_previous),
        action = if (forward) skipNextAction() else skipPreviousAction(),
        tint = if (container != null) palette.onContainer else palette.onSurface,
        modifier = modifier,
        container = container,
        cornerRadius = cornerRadius,
        iconSize = iconSize,
    )
}

@Composable
internal fun WidgetTransport(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
    heroSize: Dp = 52.dp,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
            WidgetSkipButton(forward = false, palette = palette, context = context)
        }
        WidgetHeroPlayButton(state, palette, context, heroSize)
        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
            WidgetSkipButton(forward = true, palette = palette, context = context)
        }
    }
}

@Composable
internal fun WidgetProgress(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    LinearProgressIndicator(
        progress = state.playbackPosition,
        color = palette.accent,
        backgroundColor = palette.track,
        modifier = modifier.fillMaxWidth().height(5.dp).cornerRadius(3.dp),
    )
}

@Composable
internal fun WidgetEqualizer(
    isPlaying: Boolean,
    palette: WidgetPalette,
    context: Context,
    size: Dp = 16.dp,
) {
    Box(modifier = GlanceModifier.size(size)) {
        if (isPlaying) {
            WidgetMotion(WidgetMotionKind.Equalizer, palette.motionAccent, context, GlanceModifier.fillMaxSize())
        } else {
            Image(
                provider = ImageProvider(R.drawable.widget_eq_rest),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.accent),
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
    }
}

@Composable
internal fun WidgetMediaRow(
    item: WidgetMediaItem,
    palette: WidgetPalette,
    context: Context,
    action: Action,
    modifier: GlanceModifier = GlanceModifier,
    rank: Int? = null,
    isCurrent: Boolean = false,
    isPlaying: Boolean = false,
    artSize: Dp = 40.dp,
) {
    Row(
        modifier = modifier.fillMaxWidth().cornerRadius(12.dp).clickable(action).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (rank != null) {
            Text(
                text = NumberFormat.getIntegerInstance(context.resources.configuration.locales[0]).format(rank),
                modifier = GlanceModifier.width(22.dp),
                style = TextStyle(
                    color = palette.onSurfaceVariant,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        WidgetArtwork(
            artPath = item.artPath,
            context = context,
            contentDescription = null,
            targetSize = artSize,
            shape = WidgetArtShape.Rounded,
            cornerRadius = 8.dp,
            palette = palette,
            modifier = GlanceModifier.size(artSize),
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            WidgetHeadline(item.title, palette, fontSize = 13.sp)
            if (item.subtitle.isNotBlank()) {
                WidgetSupporting(item.subtitle, palette)
            }
        }
        Spacer(GlanceModifier.width(8.dp))
        if (isCurrent) {
            Box(modifier = GlanceModifier.size(28.dp), contentAlignment = Alignment.Center) {
                WidgetEqualizer(isPlaying, palette, context, size = 18.dp)
            }
        } else {
            Box(
                modifier = GlanceModifier.size(28.dp).cornerRadius(14.dp).background(palette.container),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    provider = ImageProvider(R.drawable.play),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(palette.onContainer),
                    modifier = GlanceModifier.size(16.dp),
                )
            }
        }
    }
}

@Composable
internal fun WidgetArtTile(
    item: WidgetMediaItem,
    palette: WidgetPalette,
    context: Context,
    tileSize: Dp,
    showCaption: Boolean,
    isCurrent: Boolean,
    isPlaying: Boolean,
    shape: WidgetArtShape = WidgetArtShape.Rounded,
) {
    Column(
        modifier = GlanceModifier.width(tileSize).clickable(playSongAction(item)),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(modifier = GlanceModifier.size(tileSize), contentAlignment = Alignment.BottomEnd) {
            WidgetArtwork(
                artPath = item.artPath,
                context = context,
                contentDescription = if (showCaption) null else item.title,
                targetSize = tileSize,
                shape = shape,
                cornerRadius = 14.dp,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
            )
            if (isCurrent) {
                Box(modifier = GlanceModifier.padding(6.dp)) {
                    Box(
                        modifier = GlanceModifier.size(26.dp).cornerRadius(13.dp).background(palette.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        WidgetEqualizer(isPlaying, palette, context, size = 16.dp)
                    }
                }
            }
        }
        if (showCaption) {
            Spacer(GlanceModifier.height(4.dp))
            Text(
                text = item.title,
                modifier = GlanceModifier.fillMaxWidth(),
                style = TextStyle(
                    color = palette.onSurface,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                ),
                maxLines = 1,
            )
        }
    }
}

@Composable
internal fun WidgetEmptyMessage(
    @DrawableRes icon: Int,
    message: String,
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = GlanceModifier.size(44.dp).background(
                imageProvider = ImageProvider(R.drawable.widget_shape_cookie),
                colorFilter = ColorFilter.tint(palette.container),
            ),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(icon),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.onContainer),
                modifier = GlanceModifier.size(22.dp),
            )
        }
        Spacer(GlanceModifier.height(8.dp))
        WidgetSupporting(message, palette, GlanceModifier.fillMaxWidth(), maxLines = 2, textAlign = TextAlign.Center)
    }
}

@Composable
internal fun WidgetDivider(
    palette: WidgetPalette,
    modifier: GlanceModifier = GlanceModifier,
) {
    Box(modifier = modifier.fillMaxWidth().height(1.dp).background(palette.divider)) {}
}

internal enum class WidgetMotionKind(
    @LayoutRes val layout: Int,
    @DrawableRes val restingDrawable: Int,
) {
    Cookie(
        layout = R.layout.widget_motion_cookie,
        restingDrawable = R.drawable.widget_shape_cookie,
    ),
    Equalizer(
        layout = R.layout.widget_motion_equalizer,
        restingDrawable = R.drawable.widget_eq_rest,
    ),
}

@Composable
internal fun WidgetMotion(
    kind: WidgetMotionKind,
    color: WidgetDayNightColor,
    context: Context,
    modifier: GlanceModifier = GlanceModifier,
) {
    val remoteViews =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            RemoteViews(context.packageName, kind.layout).apply {
                setColorStateList(
                    R.id.widget_motion,
                    "setIndeterminateTintList",
                    ColorStateList.valueOf(color.day),
                    ColorStateList.valueOf(color.night),
                )
            }
        } else {
            val night =
                context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK ==
                    Configuration.UI_MODE_NIGHT_YES
            RemoteViews(context.packageName, R.layout.widget_motion_static).apply {
                setImageViewResource(R.id.widget_motion_static, kind.restingDrawable)
                setInt(R.id.widget_motion_static, "setColorFilter", if (night) color.night else color.day)
            }
        }
    AndroidRemoteViews(remoteViews = remoteViews, modifier = modifier)
}
