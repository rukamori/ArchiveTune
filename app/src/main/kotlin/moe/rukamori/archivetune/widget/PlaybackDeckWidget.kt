/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition

class PlaybackDeckWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackDeckContent(context)
        }
    }
}

@Composable
private fun PlaybackDeckContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val wide = size.width >= 320.dp && size.height >= 128.dp + 48.dp * textScale
    val stacked = !wide && size.height >= 168.dp + 48.dp * textScale

    WidgetTheme(state.dominantColor) { palette ->
        Box(
            modifier = GlanceModifier.fillMaxSize().background(palette.surface)
                .cornerRadius(28.dp).clickable(openArchiveTuneAction(context))
                .padding(if (size.width < 200.dp) 8.dp else 12.dp),
        ) {
            when {
                wide -> {
                    val recordSize = minOf(size.height - 24.dp, size.width * 0.42f, size.width - 184.dp)
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        DeckRecord(state, palette, context, recordSize)
                        Spacer(GlanceModifier.width(16.dp))
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            WidgetTrackInfo(
                                state, palette, GlanceModifier.defaultWeight().fillMaxWidth(),
                                titleSize = 22,
                                titleLines = if (size.height >= 176.dp + 56.dp * textScale) 2 else 1,
                            )
                            if (state.isAvailable) {
                                WidgetPlaybackProgress(state, palette)
                                Spacer(GlanceModifier.height(12.dp))
                                WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                            }
                        }
                    }
                }
                stacked -> {
                    val recordSize = minOf(size.width - 24.dp, size.height - (96.dp + 48.dp * textScale))
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                            DeckRecord(state, palette, context, recordSize)
                        }
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetTrackInfo(state, palette, GlanceModifier.fillMaxWidth())
                        if (state.isAvailable) {
                            Spacer(GlanceModifier.height(8.dp))
                            WidgetPlaybackProgress(state, palette)
                            Spacer(GlanceModifier.height(8.dp))
                            WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                        }
                    }
                }
                else -> {
                    val recordSize = minOf(80.dp, size.height - 24.dp, (size.width - 36.dp) * 0.45f)
                        .coerceAtLeast(48.dp)
                    Row(
                        modifier = GlanceModifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(modifier = GlanceModifier.size(recordSize), contentAlignment = Alignment.Center) {
                            DeckRecord(state, palette, context, recordSize)
                            if (state.isAvailable) {
                                WidgetPlayPauseButton(state, palette, context)
                            }
                        }
                        Spacer(GlanceModifier.width(12.dp))
                        Column(modifier = GlanceModifier.defaultWeight()) {
                            WidgetTrackInfo(
                                state, palette, titleSize = 14,
                                showArtist = size.height - 24.dp >= 40.dp * textScale,
                            )
                            if (state.isAvailable && size.height >= 136.dp + 24.dp * textScale &&
                                size.width - recordSize - 36.dp >= 96.dp
                            ) {
                                Spacer(GlanceModifier.height(8.dp))
                                Row(modifier = GlanceModifier.fillMaxWidth()) {
                                    WidgetSkipButton(false, palette, context, GlanceModifier.defaultWeight().height(48.dp))
                                    WidgetSkipButton(true, palette, context, GlanceModifier.defaultWeight().height(48.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckRecord(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    diameter: Dp,
) {
    Box(
        modifier = GlanceModifier.size(diameter).background(palette.secondaryContainer)
            .cornerRadius(diameter / 2).padding(4.dp),
        contentAlignment = Alignment.Center,
    ) {
        WidgetArtwork(
            artPath = state.artPath, context = context, contentDescription = null,
            targetSize = diameter - 8.dp, cornerRadius = (diameter - 8.dp) / 2,
            palette = palette, modifier = GlanceModifier.fillMaxSize(), circular = true,
        )
        Box(
            modifier = GlanceModifier.size(16.dp).background(palette.surface).cornerRadius(8.dp),
        ) {}
    }
}
