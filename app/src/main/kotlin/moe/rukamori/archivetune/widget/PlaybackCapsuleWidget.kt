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
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
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
    val textScale = context.widgetTextScale()
    val tall = size.height >= 130.dp + 48.dp * textScale

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Tonal, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = 6.dp) {
            if (tall) {
                Column(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(28.dp)
                            .background(palette.container)
                            .padding(12.dp),
                ) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetArtwork(
                            artPath = state.artPath,
                            context = context,
                            contentDescription = null,
                            targetSize = 64.dp,
                            shape = WidgetArtShape.Circle,
                            palette = palette,
                            modifier = GlanceModifier.size(64.dp),
                        )
                        Spacer(GlanceModifier.width(12.dp))
                        WidgetTrackText(
                            state,
                            palette,
                            GlanceModifier.defaultWeight(),
                            titleSize = 17.sp,
                            titleLines = if (size.height >= 220.dp * textScale) 2 else 1,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetEqualizer(state.isPlaying, palette, context, size = 18.dp)
                    }
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetProgress(state, palette)
                        Spacer(GlanceModifier.height(4.dp))
                        WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth(), heroSize = 48.dp)
                    }
                }
            } else {
                val innerHeight = size.height - 12.dp
                val artSize = (innerHeight - 12.dp).coerceIn(32.dp, 56.dp)
                val showArtwork = size.width >= 220.dp
                Row(
                    modifier =
                        GlanceModifier
                            .fillMaxSize()
                            .cornerRadius(innerHeight / 2)
                            .background(palette.container)
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showArtwork) {
                        WidgetArtwork(
                            artPath = state.artPath,
                            context = context,
                            contentDescription = null,
                            targetSize = artSize,
                            shape = WidgetArtShape.Circle,
                            palette = palette,
                            modifier = GlanceModifier.size(artSize),
                        )
                        Spacer(GlanceModifier.width(10.dp))
                    } else {
                        Spacer(GlanceModifier.width(8.dp))
                    }
                    WidgetTrackText(
                        state,
                        palette,
                        GlanceModifier.defaultWeight(),
                        titleSize = 14.sp,
                        showArtist = innerHeight - 12.dp >= 36.dp * textScale,
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    WidgetEqualizer(state.isPlaying, palette, context, size = 16.dp)
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetHeroPlayButton(state, palette, context, artSize)
                    }
                }
            }
        }
    }
}
