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
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import moe.rukamori.archivetune.R

class RecentlyPlayedWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            RecentlyPlayedContent(context)
        }
    }
}

@Composable
private fun RecentlyPlayedContent(context: Context) {
    val preferences = currentState<Preferences>()
    val state = remember(preferences, context) { preferences.toWidgetPlaybackState(context) }
    val recents = remember(preferences) { preferences.widgetRecentItems() }
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val padding = 14.dp
    val gap = 10.dp
    val headerHeight = 22.dp * textScale + 10.dp
    val availableWidth = size.width - padding * 2
    val availableHeight = size.height - padding * 2 - headerHeight
    val grid = remember(availableWidth, availableHeight, textScale) {
        recentGrid(availableWidth, availableHeight, gap, 20.dp * textScale)
    }

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = padding) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetHeadline(
                        context.getString(R.string.recently_played),
                        palette,
                        GlanceModifier.defaultWeight(),
                        fontSize = 15.sp,
                    )
                    WidgetBrandGlyph(palette)
                }
                Spacer(GlanceModifier.height(10.dp))
                if (recents.isEmpty()) {
                    WidgetEmptyMessage(
                        icon = R.drawable.history,
                        message = context.getString(R.string.widget_no_recent_songs),
                        palette = palette,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    )
                } else {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        recents.take(grid.columns * grid.rows).chunked(grid.columns).forEachIndexed { rowIndex, rowItems ->
                            if (rowIndex > 0) {
                                Spacer(GlanceModifier.height(gap))
                            }
                            Row(
                                modifier = GlanceModifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                rowItems.forEachIndexed { columnIndex, item ->
                                    if (columnIndex > 0) {
                                        Spacer(GlanceModifier.width(gap))
                                    }
                                    val isCurrent = item.id == state.mediaId
                                    WidgetArtTile(
                                        item = item,
                                        palette = palette,
                                        context = context,
                                        tileSize = grid.tileSize,
                                        showCaption = grid.showCaption,
                                        isCurrent = isCurrent,
                                        isPlaying = state.isPlaying,
                                        shape = if (isCurrent) WidgetArtShape.Cookie else WidgetArtShape.Rounded,
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

private data class RecentGrid(
    val columns: Int,
    val rows: Int,
    val tileSize: Dp,
    val showCaption: Boolean,
)

private fun recentGrid(
    availableWidth: Dp,
    availableHeight: Dp,
    gap: Dp,
    captionHeight: Dp,
): RecentGrid {
    val columns =
        when {
            availableWidth >= 300.dp -> 4
            availableWidth >= 190.dp -> 3
            else -> 2
        }
    val widthTile = (availableWidth - gap * (columns - 1)) / columns
    val captionedRows = ((availableHeight + gap) / (widthTile + captionHeight + gap)).toInt()
    return if (captionedRows >= 1) {
        RecentGrid(columns, captionedRows.coerceAtMost(MaxRows), widthTile, showCaption = true)
    } else {
        val heightTile = (availableHeight - captionHeight).coerceAtLeast(MinTile)
        val showCaption = availableHeight - captionHeight >= MinTile
        val tile = minOf(widthTile, if (showCaption) heightTile else availableHeight).coerceAtLeast(MinTile)
        val fittedColumns = ((availableWidth + gap) / (tile + gap)).toInt().coerceIn(1, MaxColumns)
        RecentGrid(fittedColumns, 1, tile, showCaption)
    }
}

private val MinTile = 40.dp
private const val MaxRows = 2
private const val MaxColumns = 5
