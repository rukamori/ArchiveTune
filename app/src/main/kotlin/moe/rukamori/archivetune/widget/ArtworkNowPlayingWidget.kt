/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import moe.rukamori.archivetune.R

class ArtworkNowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            ArtworkNowPlayingContent(context)
        }
    }
}

@Composable
private fun ArtworkNowPlayingContent(context: Context) {
    val preferences = currentState<Preferences>()
    val state = remember(preferences, context) { preferences.toWidgetPlaybackState(context) }
    val recents = remember(preferences) { preferences.widgetRecentItems() }
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val padding = 14.dp
    val rowHeight = 48.dp * textScale
    val large = size.height >= 250.dp + 40.dp * textScale
    val headerArt =
        if (large) {
            minOf(size.width * 0.36f, 120.dp)
        } else {
            minOf(size.height - padding * 2, size.width * 0.42f, 150.dp)
        }.coerceAtLeast(56.dp)
    val listHeight = size.height - padding * 2 - headerArt - 12.dp - 1.dp - 10.dp - 18.dp * textScale - 4.dp
    val rowCount = (listHeight / rowHeight).toInt().coerceIn(0, MaxRecentRows)

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = padding) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                NowPlayingHeader(state, palette, context, headerArt, large)
                if (large) {
                    Spacer(GlanceModifier.height(12.dp))
                    WidgetDivider(palette)
                    Spacer(GlanceModifier.height(10.dp))
                    WidgetOverline(context.getString(R.string.recently_played), palette, context)
                    Spacer(GlanceModifier.height(4.dp))
                    RecentRows(state, recents.take(rowCount), palette, context)
                }
            }
        }
    }
}

@Composable
private fun NowPlayingHeader(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    artSize: Dp,
    large: Boolean,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = null,
            targetSize = artSize,
            shape = WidgetArtShape.Rounded,
            cornerRadius = 16.dp,
            palette = palette,
            modifier = GlanceModifier.size(artSize),
        )
        Spacer(GlanceModifier.width(14.dp))
        Column(modifier = GlanceModifier.defaultWeight().height(artSize)) {
            Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                WidgetEqualizer(state.isPlaying, palette, context, size = 12.dp)
                Spacer(GlanceModifier.width(5.dp))
                WidgetOverline(
                    context.getString(R.string.widget_now_playing),
                    palette,
                    context,
                    GlanceModifier.defaultWeight(),
                )
                WidgetBrandGlyph(palette)
            }
            Spacer(GlanceModifier.height(4.dp))
            WidgetTrackText(
                state,
                palette,
                GlanceModifier.fillMaxWidth(),
                titleSize = if (large) 17.sp else 16.sp,
                titleLines = 2,
            )
            Spacer(GlanceModifier.defaultWeight())
            WidgetPlayPill(state, palette, context)
        }
    }
}

@Composable
private fun RecentRows(
    state: WidgetPlaybackState,
    items: List<WidgetMediaItem>,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (items.isEmpty()) {
            WidgetSupporting(context.getString(R.string.widget_no_recent_songs), palette, maxLines = 2)
        } else {
            items.forEach { item ->
                val isCurrent = item.id == state.mediaId
                WidgetMediaRow(
                    item = item,
                    palette = palette,
                    context = context,
                    action = if (isCurrent) playPauseAction() else playSongAction(item),
                    isCurrent = isCurrent,
                    isPlaying = state.isPlaying,
                )
            }
        }
    }
}

private const val MaxRecentRows = 5
