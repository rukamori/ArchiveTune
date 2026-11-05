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
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
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
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import moe.rukamori.archivetune.R

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
    val textScale = context.widgetTextScale()
    val showHeader = size.height >= 150.dp + 24.dp * textScale
    val showArtist = size.height >= 120.dp + 40.dp * textScale
    val showControls = size.width >= 200.dp && size.height >= 220.dp + 56.dp * textScale
    val titleSize = if (size.width >= 240.dp) 22.sp else 17.sp

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = 0.dp, backdrop = false) {
            WidgetArtwork(
                artPath = state.artPath,
                context = context,
                contentDescription = null,
                targetSize = maxOf(size.width, size.height),
                shape = WidgetArtShape.Square,
                palette = palette,
                modifier = GlanceModifier.fillMaxSize(),
                fallbackIconSize = minOf(size.width, size.height) * 0.32f,
            )
            Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_scrim_bottom))) {}
            if (showHeader) {
                Box(modifier = GlanceModifier.fillMaxSize().background(ImageProvider(R.drawable.widget_scrim_top))) {}
            }
            Column(modifier = GlanceModifier.fillMaxSize().padding(14.dp)) {
                if (showHeader) {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetEqualizer(state.isPlaying, palette, context, size = 14.dp)
                        Spacer(GlanceModifier.width(6.dp))
                        WidgetOverline(
                            context.getString(R.string.widget_now_playing),
                            palette,
                            context,
                            GlanceModifier.defaultWeight(),
                            color = palette.onSurface,
                        )
                        WidgetBrandGlyph(palette)
                    }
                }
                Spacer(GlanceModifier.defaultWeight())
                if (showControls) {
                    WidgetTrackText(
                        state,
                        palette,
                        GlanceModifier.fillMaxWidth(),
                        titleSize = titleSize,
                        titleLines = if (size.height >= 280.dp + 48.dp * textScale) 2 else 1,
                    )
                    if (state.isAvailable) {
                        Spacer(GlanceModifier.height(10.dp))
                        WidgetProgress(state, palette)
                        Spacer(GlanceModifier.height(4.dp))
                        WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth())
                    }
                } else {
                    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                        WidgetTrackText(
                            state,
                            palette,
                            GlanceModifier.defaultWeight(),
                            titleSize = if (size.width >= 180.dp) 16.sp else 14.sp,
                            titleLines = if (size.height >= 170.dp * textScale) 2 else 1,
                            showArtist = showArtist,
                        )
                        if (state.isAvailable) {
                            Spacer(GlanceModifier.width(8.dp))
                            WidgetHeroPlayButton(state, palette, context, 44.dp)
                        }
                    }
                }
            }
        }
    }
}
