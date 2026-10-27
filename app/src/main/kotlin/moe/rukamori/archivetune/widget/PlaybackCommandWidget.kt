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
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import moe.rukamori.archivetune.R

class PlaybackCommandWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackCommandContent(context)
        }
    }
}

@Composable
private fun PlaybackCommandContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val padding = if (size.width < 240.dp) 8.dp else 12.dp
    val showMetadata = size.height >= padding * 2 + 56.dp + 24.dp * textScale
    val showArtist = size.height >= padding * 2 + 72.dp + 44.dp * textScale
    val largeControls = size.height >= 176.dp + 48.dp * textScale

    WidgetTheme(state.dominantColor) { palette ->
        Column(
            modifier = GlanceModifier.fillMaxSize().background(palette.surface)
                .cornerRadius(20.dp).clickable(openArchiveTuneAction(context)).padding(padding),
        ) {
            if (!state.isAvailable) {
                WidgetTrackInfo(
                    state, palette, GlanceModifier.fillMaxSize(),
                    titleLines = if (size.height >= 100.dp * textScale) 2 else 1,
                )
            } else {
                if (showMetadata) {
                    WidgetTrackInfo(
                        state, palette, GlanceModifier.fillMaxWidth(),
                        titleSize = if (showArtist) 16 else 14, showArtist = showArtist,
                    )
                    Spacer(GlanceModifier.height(8.dp))
                }
                if (largeControls) {
                    WidgetPlaybackProgress(state, palette)
                    Spacer(GlanceModifier.height(12.dp))
                    Row(modifier = GlanceModifier.fillMaxWidth().defaultWeight()) {
                        Column(modifier = GlanceModifier.width(56.dp).fillMaxHeight()) {
                            Box(
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                                    .background(palette.secondaryContainer).cornerRadius(12.dp),
                            ) {
                                WidgetSkipButton(false, palette, context, GlanceModifier.fillMaxSize())
                            }
                            Spacer(GlanceModifier.height(8.dp))
                            Box(
                                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                                    .background(palette.secondaryContainer).cornerRadius(12.dp),
                            ) {
                                WidgetSkipButton(true, palette, context, GlanceModifier.fillMaxSize())
                            }
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetControlButton(
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                            action = playPauseAction(),
                            icon = if (state.isPlaying) R.drawable.pause else R.drawable.play,
                            contentDescription = context.getString(if (state.isPlaying) R.string.widget_pause else R.string.play),
                            backgroundColor = palette.primary, contentColor = palette.onPrimary,
                            cornerRadius = 16.dp, iconSize = 48.dp,
                        )
                    }
                } else {
                    Box(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            modifier = GlanceModifier.fillMaxWidth().height(48.dp)
                                .background(palette.secondaryContainer).cornerRadius(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            WidgetSkipButton(false, palette, context, GlanceModifier.defaultWeight().height(48.dp))
                            WidgetPlayPauseButton(
                                state, palette, context,
                                GlanceModifier.defaultWeight().height(48.dp), cornerRadius = 12.dp,
                            )
                            WidgetSkipButton(true, palette, context, GlanceModifier.defaultWeight().height(48.dp))
                        }
                    }
                }
            }
        }
    }
}
