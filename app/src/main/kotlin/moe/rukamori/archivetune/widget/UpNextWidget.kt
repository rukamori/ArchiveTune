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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.itemsIndexed
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

class UpNextWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            UpNextContent(context)
        }
    }
}

@Composable
private fun UpNextContent(context: Context) {
    val preferences = currentState<Preferences>()
    val state = remember(preferences, context) { preferences.toWidgetPlaybackState(context) }
    val upNext = remember(preferences) { preferences.widgetUpNext() }
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val showProgress = size.height >= 200.dp + 40.dp * textScale

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = 14.dp) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetHeadline(
                        context.getString(R.string.widget_up_next),
                        palette,
                        GlanceModifier.defaultWeight(),
                        fontSize = 15.sp,
                    )
                    WidgetBrandGlyph(palette)
                }
                Spacer(GlanceModifier.height(10.dp))
                CurrentTrackRow(state, palette, context)
                if (showProgress && state.isAvailable) {
                    Spacer(GlanceModifier.height(8.dp))
                    WidgetProgress(state, palette)
                }
                Spacer(GlanceModifier.height(10.dp))
                WidgetDivider(palette)
                Spacer(GlanceModifier.height(6.dp))
                if (upNext.isEmpty()) {
                    WidgetEmptyMessage(
                        icon = R.drawable.queue_music,
                        message =
                            context.getString(
                                if (state.isAvailable) R.string.widget_queue_empty else R.string.widget_tap_to_open,
                            ),
                        palette = palette,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    )
                } else {
                    LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        itemsIndexed(upNext, itemId = { _, item -> item.widgetItemId() }) { index, item ->
                            WidgetMediaRow(
                                item = item,
                                palette = palette,
                                context = context,
                                action = playQueueItemAction(item),
                                rank = index + 1,
                                artSize = 36.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CurrentTrackRow(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = null,
            targetSize = 48.dp,
            shape = WidgetArtShape.Cookie,
            palette = palette,
            modifier = GlanceModifier.size(48.dp),
        )
        Spacer(GlanceModifier.width(12.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetEqualizer(state.isPlaying, palette, context, size = 12.dp)
                Spacer(GlanceModifier.width(5.dp))
                WidgetOverline(context.getString(R.string.widget_now_playing), palette, context)
            }
            WidgetHeadline(state.title, palette, fontSize = 14.sp)
            WidgetSupporting(state.artist, palette)
        }
        Spacer(GlanceModifier.width(8.dp))
        if (state.isAvailable) {
            WidgetHeroPlayButton(state, palette, context, 44.dp)
        } else {
            WidgetPlayPill(state, palette, context)
        }
    }
}
