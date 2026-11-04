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
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import moe.rukamori.archivetune.R

class ListeningInsightsWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            ListeningInsightsContent(context)
        }
    }
}

@Composable
private fun ListeningInsightsContent(context: Context) {
    val preferences = currentState<Preferences>()
    val playback = remember(preferences, context) { preferences.toWidgetPlaybackState(context) }
    val insights = remember(preferences, context) { preferences.toWidgetInsightsState(context) }
    val recentItems = remember(preferences) { preferences.widgetRecentItems() }
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val wide = size.width >= 280.dp + 40.dp * textScale

    WidgetTheme(playback.dominantColor, WidgetSurfaceStyle.Tonal, context) { palette ->
        WidgetSurface(playback, palette, context, contentPadding = 14.dp) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetOverline(context.getString(R.string.widget_last_30_days), palette, context)
                        WidgetHeadline(context.getString(R.string.widget_insights_title), palette, fontSize = 17.sp)
                    }
                    if (playback.isAvailable && size.width >= 240.dp) {
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetHeroPlayButton(playback, palette, context, 40.dp)
                    } else {
                        WidgetBrandGlyph(palette)
                    }
                }
                Spacer(GlanceModifier.height(12.dp))
                LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                    item(itemId = StatsItemId) {
                        if (wide) {
                            Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                InsightStatTile(
                                    label = context.getString(R.string.widget_listening_time_label),
                                    value = insights.listeningTime,
                                    palette = palette,
                                    context = context,
                                    emphasized = true,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                                Spacer(GlanceModifier.width(8.dp))
                                InsightStatTile(
                                    label = context.getString(R.string.widget_plays_label),
                                    value = insights.totalPlays,
                                    palette = palette,
                                    context = context,
                                    emphasized = false,
                                    modifier = GlanceModifier.defaultWeight(),
                                )
                            }
                        } else {
                            Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                                InsightStatTile(
                                    label = context.getString(R.string.widget_listening_time_label),
                                    value = insights.listeningTime,
                                    palette = palette,
                                    context = context,
                                    emphasized = true,
                                    modifier = GlanceModifier.fillMaxWidth(),
                                )
                                Spacer(GlanceModifier.height(8.dp))
                                InsightStatTile(
                                    label = context.getString(R.string.widget_plays_label),
                                    value = insights.totalPlays,
                                    palette = palette,
                                    context = context,
                                    emphasized = false,
                                    modifier = GlanceModifier.fillMaxWidth(),
                                )
                            }
                        }
                    }
                    insights.topSongSummary?.let { summary ->
                        item(itemId = TopSongItemId) {
                            WidgetSupporting(
                                summary,
                                palette,
                                GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
                                maxLines = 2,
                            )
                        }
                    }
                    item(itemId = RecentHeaderItemId) {
                        InsightSectionHeader(context.getString(insights.recentSongs.title), palette, context)
                    }
                    if (recentItems.isNotEmpty()) {
                        items(recentItems.take(InsightRecentLimit), itemId = { it.widgetItemId() }) { item ->
                            WidgetMediaRow(
                                item = item,
                                palette = palette,
                                context = context,
                                action = if (item.id == playback.mediaId) playPauseAction() else playSongAction(item),
                                isCurrent = item.id == playback.mediaId,
                                isPlaying = playback.isPlaying,
                                artSize = 36.dp,
                            )
                        }
                    } else {
                        item(itemId = RecentBodyItemId) { InsightLines(insights.recentSongs, palette, context) }
                    }
                    item(itemId = GenresItemId) {
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 12.dp)) {
                            InsightSectionHeader(context.getString(insights.genres.title), palette, context)
                            WidgetSupporting(
                                text =
                                    insights.genres.lines
                                        .joinToString(separator = " · ")
                                        .ifBlank { context.getString(insights.genres.emptyMessage) },
                                palette = palette,
                                fontSize = 14.sp,
                                maxLines = 2,
                            )
                        }
                    }
                    item(itemId = RecommendationsItemId) {
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(top = 12.dp)) {
                            InsightSectionHeader(context.getString(insights.recommendations.title), palette, context)
                            InsightLines(insights.recommendations, palette, context)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InsightStatTile(
    label: String,
    value: String,
    palette: WidgetPalette,
    context: Context,
    emphasized: Boolean,
    modifier: GlanceModifier,
) {
    Column(
        modifier =
            modifier
                .cornerRadius(20.dp)
                .background(if (emphasized) palette.accent else palette.container)
                .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        WidgetOverline(
            label,
            palette,
            context,
            color = if (emphasized) palette.onAccent else palette.onContainer,
        )
        Spacer(GlanceModifier.height(4.dp))
        Text(
            text = value,
            style = TextStyle(
                color = if (emphasized) palette.onAccent else palette.onContainer,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
    }
}

@Composable
private fun InsightSectionHeader(
    title: String,
    palette: WidgetPalette,
    context: Context,
) {
    WidgetOverline(title, palette, context, GlanceModifier.fillMaxWidth().padding(bottom = 6.dp))
}

@Composable
private fun InsightLines(
    section: WidgetInsightSection,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        if (section.lines.isEmpty()) {
            WidgetSupporting(context.getString(section.emptyMessage), palette, maxLines = 2)
        } else {
            section.lines.forEach { line ->
                Text(
                    text = line,
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 3.dp),
                    style = TextStyle(color = palette.onSurface, fontSize = 14.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

private const val StatsItemId = -1L
private const val TopSongItemId = -2L
private const val RecentHeaderItemId = -3L
private const val RecentBodyItemId = -4L
private const val GenresItemId = -5L
private const val RecommendationsItemId = -6L
private const val InsightRecentLimit = 4
