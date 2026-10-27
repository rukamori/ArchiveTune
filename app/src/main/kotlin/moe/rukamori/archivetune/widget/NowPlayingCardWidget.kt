/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import androidx.compose.runtime.Composable
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

class NowPlayingCardWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            NowPlayingCardContent(context)
        }
    }
}

@Composable
private fun NowPlayingCardContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val split = size.width >= 300.dp && size.height >= 96.dp + 40.dp * textScale
    val stacked = !split && size.height >= 200.dp + 56.dp * textScale
    val compactTransport = state.isAvailable && size.height >= 92.dp + 44.dp * textScale
    val compactProgress = state.isAvailable && size.height >= 100.dp
    val compactTextHeight = size.height - 16.dp -
        (if (compactTransport) 56.dp else 0.dp) - (if (compactProgress) 12.dp else 0.dp)

    WidgetTheme(state.dominantColor) { palette ->
        Box(
            modifier = GlanceModifier.fillMaxSize().background(palette.surface)
                .cornerRadius(28.dp).clickable(openArchiveTuneAction(context)),
        ) {
            when {
                split -> {
                    val artworkWidth = minOf(size.height, size.width * 0.38f, size.width - 180.dp)
                    Row(modifier = GlanceModifier.fillMaxSize()) {
                        WidgetArtwork(
                            artPath = state.artPath, context = context, contentDescription = null,
                            targetSize = maxOf(artworkWidth, size.height), cornerRadius = 0.dp,
                            palette = palette,
                            modifier = GlanceModifier.width(artworkWidth).fillMaxHeight(),
                        )
                        Column(
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight().padding(12.dp),
                        ) {
                            WidgetTrackInfo(
                                state, palette, GlanceModifier.defaultWeight().fillMaxWidth(),
                                titleLines = if (size.height >= 136.dp + 64.dp * textScale) 2 else 1,
                            )
                            if (state.isAvailable) {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetPlaybackProgress(state, palette)
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                            }
                        }
                    }
                }
                stacked -> {
                    val artworkHeight = size.height - (96.dp + 56.dp * textScale)
                    Column(modifier = GlanceModifier.fillMaxSize()) {
                        WidgetArtwork(
                            artPath = state.artPath, context = context, contentDescription = null,
                            targetSize = maxOf(size.width, artworkHeight), cornerRadius = 0.dp,
                            palette = palette,
                            modifier = GlanceModifier.fillMaxWidth().height(artworkHeight),
                        )
                        Column(modifier = GlanceModifier.fillMaxWidth().defaultWeight().padding(12.dp)) {
                            WidgetTrackInfo(state, palette, GlanceModifier.defaultWeight().fillMaxWidth())
                            if (state.isAvailable) {
                                WidgetPlaybackProgress(state, palette)
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                            }
                        }
                    }
                }
                else -> {
                    Column(modifier = GlanceModifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (size.width >= 240.dp) {
                                WidgetArtwork(
                                    artPath = state.artPath, context = context, contentDescription = null,
                                    targetSize = 48.dp, cornerRadius = 8.dp,
                                    palette = palette, modifier = GlanceModifier.size(48.dp),
                                )
                                Spacer(GlanceModifier.width(12.dp))
                            }
                            WidgetTrackInfo(
                                state, palette, GlanceModifier.defaultWeight(), titleSize = 14,
                                showArtist = compactTextHeight >= 40.dp * textScale,
                            )
                            if (state.isAvailable && !compactTransport) {
                                Spacer(GlanceModifier.width(8.dp))
                                WidgetPlayPauseButton(state, palette, context, cornerRadius = 12.dp)
                            }
                        }
                        if (compactTransport) {
                            Spacer(GlanceModifier.height(8.dp))
                            WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                        }
                        if (compactProgress) {
                            Spacer(GlanceModifier.height(8.dp))
                            WidgetPlaybackProgress(state, palette)
                        }
                    }
                }
            }
        }
    }
}
