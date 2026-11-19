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
import moe.rukamori.archivetune.R

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
    val textScale = context.widgetTextScale()
    val expanded = size.height >= 150.dp + 48.dp * textScale
    val showSkips = size.width >= 300.dp + 40.dp * textScale

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Tonal, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = if (expanded) 16.dp else 10.dp) {
            if (expanded) {
                Column(modifier = GlanceModifier.fillMaxSize()) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetEqualizer(state.isPlaying, palette, context, size = 14.dp)
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetOverline(
                            context.getString(R.string.widget_now_playing),
                            palette,
                            context,
                            GlanceModifier.defaultWeight(),
                        )
                        WidgetBrandGlyph(palette)
                    }
                    Spacer(GlanceModifier.defaultWeight())
                    WidgetTrackText(
                        state,
                        palette,
                        GlanceModifier.fillMaxWidth(),
                        titleSize = 22.sp,
                        titleLines = 2,
                    )
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.height(12.dp))
                        WidgetProgress(state, palette)
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth())
                    }
                }
            } else {
                val artSize = (size.height - 20.dp).coerceIn(36.dp, 56.dp)
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    WidgetArtwork(
                        artPath = state.artPath,
                        context = context,
                        contentDescription = null,
                        targetSize = artSize,
                        shape = WidgetArtShape.Cookie,
                        palette = palette,
                        modifier = GlanceModifier.size(artSize),
                    )
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetTrackText(
                        state,
                        palette,
                        GlanceModifier.defaultWeight(),
                        titleSize = 15.sp,
                        showArtist = size.height - 20.dp >= 36.dp * textScale,
                    )
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.width(6.dp))
                        if (showSkips) {
                            WidgetSkipButton(forward = false, palette = palette, context = context)
                        }
                        WidgetHeroPlayButton(state, palette, context, minOf(artSize, 48.dp))
                        if (showSkips) {
                            WidgetSkipButton(forward = true, palette = palette, context = context)
                        }
                    }
                }
            }
        }
    }
}
