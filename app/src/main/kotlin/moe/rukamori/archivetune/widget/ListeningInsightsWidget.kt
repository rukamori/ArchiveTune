/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.currentState
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
    val prefs = currentState<Preferences>()
    val playbackState = prefs.toWidgetPlaybackState(context)
    val insightsState = prefs.toWidgetInsightsState(context)

    GlanceTheme(
        colors =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                GlanceTheme.colors
            } else {
                ArchiveTuneWidgetColors.providers
            },
    ) {
        val palette = rememberWidgetPalette(playbackState.dominantColor)
        val size = LocalSize.current
        val compact = size.width < 270.dp || size.height < 180.dp

        Box(
            modifier =
                GlanceModifier
                    .fillMaxSize()
                    .background(palette.surface)
                    .cornerRadius(30.dp)
                    .padding(if (compact) 10.dp else 14.dp)
                    .clickable(openArchiveTuneAction(context)),
        ) {
            if (compact) {
                ListeningInsightsCompact(
                    playbackState = playbackState,
                    insightsState = insightsState,
                    palette = palette,
                    context = context,
                )
            } else {
                ListeningInsightsPanel(
                    playbackState = playbackState,
                    insightsState = insightsState,
                    palette = palette,
                    context = context,
                )
            }
        }
    }
}

@Composable
private fun ListeningInsightsCompact(
    playbackState: WidgetPlaybackState,
    insightsState: WidgetInsightsState,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetArtwork(
                artPath = playbackState.artPath,
                context = context,
                contentDescription = context.getString(R.string.album_cover_desc),
                targetSize = 48.dp,
                cornerRadius = 16.dp,
                palette = palette,
                modifier = GlanceModifier.size(48.dp),
            )

            Spacer(GlanceModifier.width(10.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.Vertical.CenterVertically,
            ) {
                Text(
                    text = context.getString(R.string.widget_insights_title),
                    style =
                        TextStyle(
                            color = palette.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                        ),
                    maxLines = 1,
                )
                Text(
                    text = insightsState.listeningTime,
                    style =
                        TextStyle(
                            color = palette.onSurfaceVariant,
                            fontSize = 12.sp,
                        ),
                    maxLines = 1,
                )
            }
        }

        Spacer(GlanceModifier.height(10.dp))

        InsightMetricRow(
            listeningTime = insightsState.listeningTime,
            totalPlays = insightsState.totalPlays,
            palette = palette,
        )

        Spacer(GlanceModifier.height(10.dp))

        InsightLineSection(
            title = context.getString(R.string.recently_played),
            items = insightsState.recentSongs.ifEmpty { listOf(context.getString(R.string.widget_no_recent_songs)) },
            palette = palette,
            maxItems = 2,
        )
    }
}

@Composable
private fun ListeningInsightsPanel(
    playbackState: WidgetPlaybackState,
    insightsState: WidgetInsightsState,
    palette: WidgetPalette,
    context: Context,
) {
    Column(modifier = GlanceModifier.fillMaxSize()) {
        ListeningInsightsHeader(
            playbackState = playbackState,
            palette = palette,
            context = context,
        )

        Spacer(GlanceModifier.height(12.dp))

        InsightMetricRow(
            listeningTime = insightsState.listeningTime,
            totalPlays = insightsState.totalPlays,
            palette = palette,
        )

        insightsState.topSongSummary?.let { summary ->
            Spacer(GlanceModifier.height(8.dp))
            Text(
                text = summary,
                style =
                    TextStyle(
                        color = palette.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                maxLines = 1,
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        Row(modifier = GlanceModifier.fillMaxWidth()) {
            InsightLineSection(
                title = context.getString(R.string.recently_played),
                items = insightsState.recentSongs.ifEmpty { listOf(context.getString(R.string.widget_no_recent_songs)) },
                palette = palette,
                maxItems = 3,
                modifier = GlanceModifier.defaultWeight(),
            )

            Spacer(GlanceModifier.width(10.dp))

            InsightLineSection(
                title = context.getString(R.string.mood_and_genres),
                items = insightsState.genres.ifEmpty { listOf(context.getString(R.string.widget_no_genres)) },
                palette = palette,
                maxItems = 3,
                modifier = GlanceModifier.defaultWeight(),
            )
        }

        Spacer(GlanceModifier.height(12.dp))

        InsightLineSection(
            title = context.getString(R.string.widget_recommendations),
            items = insightsState.recommendations.ifEmpty { listOf(context.getString(R.string.widget_no_recommendations)) },
            palette = palette,
            maxItems = 3,
        )
    }
}

@Composable
private fun ListeningInsightsHeader(
    playbackState: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        WidgetArtwork(
            artPath = playbackState.artPath,
            context = context,
            contentDescription = context.getString(R.string.album_cover_desc),
            targetSize = 58.dp,
            cornerRadius = 18.dp,
            palette = palette,
            modifier = GlanceModifier.size(58.dp),
        )

        Spacer(GlanceModifier.width(12.dp))

        Column(
            modifier = GlanceModifier.defaultWeight(),
            verticalAlignment = Alignment.Vertical.CenterVertically,
        ) {
            Text(
                text = context.getString(R.string.widget_insights_title),
                style =
                    TextStyle(
                        color = palette.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                maxLines = 1,
            )
            Text(
                text = playbackState.title,
                style =
                    TextStyle(
                        color = palette.onSurfaceVariant,
                        fontSize = 12.sp,
                    ),
                maxLines = 1,
            )
        }

        if (playbackState.isAvailable) {
            Spacer(GlanceModifier.width(10.dp))
            WidgetControlButton(
                modifier = GlanceModifier.size(48.dp),
                action = playPauseAction(),
                icon = if (playbackState.isPlaying) R.drawable.pause else R.drawable.play,
                contentDescription =
                    context.getString(
                        if (playbackState.isPlaying) R.string.widget_pause else R.string.play,
                    ),
                backgroundColor = palette.primaryContainer,
                contentColor = palette.onPrimaryContainer,
                cornerRadius = if (playbackState.isPlaying) 14.dp else 24.dp,
                iconSize = 24.dp,
            )
        }
    }
}

@Composable
private fun InsightMetricRow(
    listeningTime: String,
    totalPlays: String,
    palette: WidgetPalette,
) {
    Row(modifier = GlanceModifier.fillMaxWidth()) {
        InsightMetric(
            label = listeningTime,
            modifier = GlanceModifier.defaultWeight(),
            palette = palette,
        )
        Spacer(GlanceModifier.width(8.dp))
        InsightMetric(
            label = totalPlays,
            modifier = GlanceModifier.defaultWeight(),
            palette = palette,
        )
    }
}

@Composable
private fun InsightMetric(
    label: String,
    modifier: GlanceModifier,
    palette: WidgetPalette,
) {
    Box(
        modifier =
            modifier
                .height(42.dp)
                .background(palette.secondaryContainer)
                .cornerRadius(21.dp)
                .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style =
                TextStyle(
                    color = palette.onSecondaryContainer,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                ),
            maxLines = 1,
        )
    }
}

@Composable
private fun InsightLineSection(
    title: String,
    items: List<String>,
    palette: WidgetPalette,
    maxItems: Int,
    modifier: GlanceModifier = GlanceModifier,
) {
    Column(modifier = modifier) {
        Text(
            text = title,
            style =
                TextStyle(
                    color = palette.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(4.dp))
        items.take(maxItems).forEach { item ->
            Text(
                text = item,
                style =
                    TextStyle(
                        color = palette.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                maxLines = 1,
            )
            Spacer(GlanceModifier.height(2.dp))
        }
    }
}
