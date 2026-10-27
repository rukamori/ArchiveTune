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
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
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
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val wide = size.width >= 280.dp + 40.dp * textScale

    WidgetTheme(playback.dominantColor) { palette ->
        Column(
            modifier = GlanceModifier.fillMaxSize().background(palette.surface)
                .cornerRadius(20.dp).clickable(openArchiveTuneAction(context)).padding(12.dp),
        ) {
            Row(
                modifier = GlanceModifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.widget_insights_title),
                    modifier = GlanceModifier.defaultWeight(),
                    style = TextStyle(color = palette.onSurface, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    maxLines = 1,
                )
                if (playback.isAvailable && size.width >= 260.dp) {
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetPlayPauseButton(playback, palette, context, cornerRadius = 12.dp)
                }
            }
            Spacer(GlanceModifier.height(12.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                item(itemId = 1L) {
                    if (wide) {
                        Row(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text(
                                text = insights.listeningTime,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                maxLines = 2,
                            )
                            Spacer(GlanceModifier.width(16.dp))
                            Text(
                                text = insights.totalPlays,
                                modifier = GlanceModifier.defaultWeight(),
                                style = TextStyle(color = palette.onSurface, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                maxLines = 2,
                            )
                        }
                    } else {
                        Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp)) {
                            Text(
                                text = insights.listeningTime,
                                style = TextStyle(color = palette.primary, fontSize = 22.sp, fontWeight = FontWeight.Bold),
                                maxLines = 2,
                            )
                            Spacer(GlanceModifier.height(4.dp))
                            Text(
                                text = insights.totalPlays,
                                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 14.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
                insights.topSongSummary?.let { summary ->
                    item(itemId = 2L) {
                        Text(
                            text = summary,
                            modifier = GlanceModifier.fillMaxWidth().padding(bottom = 12.dp),
                            style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                            maxLines = 2,
                        )
                    }
                }
                item(itemId = 3L) { InsightSection(insights.recentSongs, palette, context) }
                item(itemId = 4L) { InsightSection(insights.genres, palette, context) }
                item(itemId = 5L) { InsightSection(insights.recommendations, palette, context) }
            }
        }
    }
}

@Composable
private fun InsightSection(
    section: WidgetInsightSection,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxWidth().padding(bottom = 16.dp)) {
        Text(
            text = context.getString(section.title),
            style = TextStyle(color = palette.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(6.dp))
        if (section.lines.isEmpty()) {
            Text(
                text = context.getString(section.emptyMessage),
                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                maxLines = 2,
            )
        } else {
            section.lines.forEach { line ->
                Text(
                    text = line,
                    modifier = GlanceModifier.fillMaxWidth().padding(vertical = 4.dp),
                    style = TextStyle(color = palette.onSurfaceVariant, fontSize = 14.sp),
                    maxLines = 2,
                )
            }
        }
    }
}
