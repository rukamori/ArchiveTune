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
import androidx.glance.appwidget.provideContent
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
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition

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
    val textScale = context.widgetTextScale()
    val padding = if (size.width < 220.dp) 10.dp else 14.dp
    val metadataHeight = 44.dp * textScale
    val showMetadata = size.height >= padding * 2 + metadataHeight + 64.dp
    val showProgress = size.height >= padding * 2 + metadataHeight + 120.dp
    val controlsHeight =
        size.height - padding * 2 -
            (if (showMetadata) metadataHeight + 10.dp else 0.dp) -
            (if (showProgress) 17.dp else 0.dp)
    val heroSize = minOf(controlsHeight, size.width * 0.42f, 112.dp).coerceAtLeast(44.dp)

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Tonal, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = padding) {
            Column(modifier = GlanceModifier.fillMaxSize()) {
                if (showMetadata || !state.isAvailable) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetArtwork(
                            artPath = state.artPath,
                            context = context,
                            contentDescription = null,
                            targetSize = 40.dp,
                            shape = WidgetArtShape.Clover,
                            palette = palette,
                            modifier = GlanceModifier.size(40.dp),
                        )
                        Spacer(GlanceModifier.width(10.dp))
                        WidgetTrackText(state, palette, GlanceModifier.defaultWeight(), titleSize = 15.sp)
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetBrandGlyph(palette)
                    }
                    Spacer(GlanceModifier.height(10.dp))
                }
                if (state.isAvailable) {
                    if (showProgress) {
                        WidgetProgress(state, palette)
                        Spacer(GlanceModifier.height(12.dp))
                    }
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        WidgetSkipButton(
                            forward = false,
                            palette = palette,
                            context = context,
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                            container = palette.container,
                            cornerRadius = 24.dp,
                            iconSize = 28.dp,
                        )
                        Spacer(GlanceModifier.width(8.dp))
                        Box(modifier = GlanceModifier.fillMaxHeight(), contentAlignment = Alignment.Center) {
                            WidgetHeroPlayButton(state, palette, context, heroSize)
                        }
                        Spacer(GlanceModifier.width(8.dp))
                        WidgetSkipButton(
                            forward = true,
                            palette = palette,
                            context = context,
                            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
                            container = palette.container,
                            cornerRadius = 24.dp,
                            iconSize = 28.dp,
                        )
                    }
                } else {
                    Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                        WidgetPlayPill(state, palette, context, emphasized = true)
                    }
                }
            }
        }
    }
}
