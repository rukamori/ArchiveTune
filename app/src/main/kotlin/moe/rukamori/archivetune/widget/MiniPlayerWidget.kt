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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition

class MiniPlayerWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            MiniPlayerContent(context)
        }
    }
}

@Composable
private fun MiniPlayerContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val padding = 12.dp
    val roomy = size.height >= 130.dp + 40.dp * textScale && size.width >= 120.dp

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = padding) {
            if (roomy) {
                val artSize = minOf(size.width * 0.5f, size.height * 0.42f, 80.dp).coerceAtLeast(44.dp)
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                        WidgetArtwork(
                            artPath = state.artPath,
                            context = context,
                            contentDescription = null,
                            targetSize = artSize,
                            shape = WidgetArtShape.Rounded,
                            cornerRadius = 14.dp,
                            palette = palette,
                            modifier = GlanceModifier.size(artSize),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        WidgetBrandGlyph(palette)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetTrackText(
                        state,
                        palette,
                        GlanceModifier.fillMaxWidth(),
                        titleSize = 14.sp,
                        titleLines = if (size.height >= 190.dp * textScale) 2 else 1,
                    )
                    Spacer(GlanceModifier.height(8.dp))
                    WidgetPlayPill(state, palette, context)
                }
            } else {
                val artSize = (size.height - padding * 2).coerceIn(36.dp, 64.dp)
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetArtwork(
                        artPath = state.artPath,
                        context = context,
                        contentDescription = null,
                        targetSize = artSize,
                        shape = WidgetArtShape.Rounded,
                        cornerRadius = 12.dp,
                        palette = palette,
                        modifier = GlanceModifier.size(artSize),
                    )
                    Spacer(GlanceModifier.width(10.dp))
                    WidgetTrackText(
                        state,
                        palette,
                        GlanceModifier.defaultWeight(),
                        titleSize = 13.sp,
                        showArtist = size.height - padding * 2 >= 34.dp * textScale,
                    )
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetHeroPlayButton(state, palette, context, 40.dp)
                    }
                }
            }
        }
    }
}
