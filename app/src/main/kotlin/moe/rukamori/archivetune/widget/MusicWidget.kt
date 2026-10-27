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

class MusicWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            MusicWidgetContent(context)
        }
    }
}

@Composable
private fun MusicWidgetContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val expanded = size.height >= 88.dp + 80.dp * textScale
    val fullControls = size.width >= 320.dp + 64.dp * textScale

    WidgetTheme(state.dominantColor) { palette ->
        Row(
            modifier = GlanceModifier.fillMaxSize()
                .background(palette.surface)
                .cornerRadius(20.dp)
                .clickable(openArchiveTuneAction(context))
                .padding(horizontal = if (size.width < 220.dp) 8.dp else 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier.width(4.dp).height(if (expanded) 64.dp else 32.dp)
                    .background(palette.primary).cornerRadius(2.dp),
            ) {}
            Spacer(GlanceModifier.width(12.dp))
            if (expanded) {
                Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                    WidgetTrackInfo(
                        state = state, palette = palette,
                        modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                        titleSize = 22, titleLines = 2,
                    )
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetPlaybackProgress(state, palette)
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetTransportControls(
                            state, palette, context, GlanceModifier.fillMaxWidth(),
                        )
                    }
                }
            } else {
                WidgetTrackInfo(
                    state = state, palette = palette,
                    modifier = GlanceModifier.defaultWeight(),
                    titleSize = 14,
                    showArtist = size.height - 12.dp >= 40.dp * textScale,
                )
                if (state.isAvailable) {
                    Spacer(GlanceModifier.width(8.dp))
                    if (fullControls) {
                        WidgetTransportControls(state, palette, context)
                    } else {
                        WidgetPlayPauseButton(state, palette, context)
                    }
                }
            }
        }
    }
}
