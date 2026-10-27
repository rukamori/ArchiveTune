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
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle

class PlaybackSpotlightWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackSpotlightContent(context)
        }
    }
}

@Composable
private fun PlaybackSpotlightContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val showArtist = size.height >= 104.dp + 56.dp * textScale
    val showControls = size.width >= 184.dp && size.height >= 152.dp + 56.dp * textScale
    val showTitle = size.height >= 144.dp + 40.dp * textScale

    WidgetTheme(state.dominantColor) { palette ->
        Box(
            modifier = GlanceModifier.fillMaxSize().background(palette.surface)
                .cornerRadius(28.dp).clickable(openArchiveTuneAction(context)),
        ) {
            WidgetArtwork(
                artPath = state.artPath, context = context, contentDescription = null,
                targetSize = maxOf(size.width, size.height), cornerRadius = 28.dp,
                palette = palette, modifier = GlanceModifier.fillMaxSize(),
            )
            Box(modifier = GlanceModifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                if (showTitle) {
                    Column(
                        modifier = GlanceModifier.fillMaxWidth().background(palette.surface).padding(12.dp),
                    ) {
                        Text(
                            text = state.title,
                            style = TextStyle(
                                color = palette.onSurface,
                                fontSize = if (size.width >= 240.dp) 22.sp else 16.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            maxLines = if (size.height >= 240.dp + 48.dp * textScale) 2 else 1,
                        )
                        if (showArtist) {
                            Spacer(GlanceModifier.height(2.dp))
                            Text(
                                text = state.artist,
                                style = TextStyle(color = palette.onSurfaceVariant, fontSize = 12.sp),
                                maxLines = 1,
                            )
                        }
                        if (state.isAvailable) {
                            Spacer(GlanceModifier.height(8.dp))
                            WidgetPlaybackProgress(state, palette)
                            Spacer(GlanceModifier.height(8.dp))
                            if (showControls) {
                                WidgetTransportControls(state, palette, context, GlanceModifier.fillMaxWidth())
                            } else {
                                WidgetPlayPauseButton(state, palette, context, cornerRadius = 12.dp)
                            }
                        }
                    }
                } else {
                    Box(modifier = GlanceModifier.padding(8.dp)) {
                        if (state.isAvailable) {
                            WidgetPlayPauseButton(state, palette, context, cornerRadius = 12.dp)
                        } else {
                            Text(
                                text = state.title,
                                modifier = GlanceModifier.background(palette.surface).cornerRadius(8.dp).padding(8.dp),
                                style = TextStyle(color = palette.onSurface, fontSize = 12.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}
