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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition

class PlaybackCapsuleWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackCapsuleContent(context)
        }
    }
}

@Composable
private fun PlaybackCapsuleContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val tall = size.height >= 120.dp + 48.dp * textScale
    val showArtwork = size.width >= 240.dp || tall
    val artworkSize = if (tall) 64.dp else 48.dp

    WidgetTheme(state.dominantColor) { palette ->
        Box(
            modifier = GlanceModifier.fillMaxSize()
                .background(palette.primaryContainer)
                .cornerRadius(if (tall) 28.dp else size.height / 2)
                .clickable(openArchiveTuneAction(context))
                .padding(8.dp),
        ) {
            if (tall) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetArtwork(
                            artPath = state.artPath, context = context, contentDescription = null,
                            targetSize = artworkSize, cornerRadius = artworkSize / 2,
                            palette = palette, modifier = GlanceModifier.size(artworkSize), circular = true,
                        )
                        Spacer(GlanceModifier.width(12.dp))
                        WidgetTrackInfo(
                            state, palette, GlanceModifier.defaultWeight(),
                            titleLines = if (size.height >= 220.dp * textScale) 2 else 1,
                        )
                    }
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetPlaybackProgress(state, palette)
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                    }
                }
            } else {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showArtwork) {
                        WidgetArtwork(
                            artPath = state.artPath, context = context, contentDescription = null,
                            targetSize = artworkSize, cornerRadius = artworkSize / 2,
                            palette = palette, modifier = GlanceModifier.size(artworkSize), circular = true,
                        )
                        Spacer(GlanceModifier.width(10.dp))
                    }
                    WidgetTrackInfo(
                        state, palette, GlanceModifier.defaultWeight(), titleSize = 14,
                        showArtist = size.height - 16.dp >= 40.dp * textScale,
                    )
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetPlayPauseButton(state, palette, context)
                    }
                }
            }
        }
    }
}
