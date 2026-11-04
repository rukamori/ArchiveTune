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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import moe.rukamori.archivetune.R

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
    val textScale = context.widgetTextScale()
    val padding = 14.dp
    val split = size.width >= 280.dp && size.height >= 120.dp + 40.dp * textScale
    val stacked = !split && size.height >= 240.dp + 64.dp * textScale

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = padding) {
            when {
                split -> {
                    val artSize = minOf(size.height - padding * 2, size.width * 0.42f).coerceAtLeast(64.dp)
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        WidgetArtwork(
                            artPath = state.artPath,
                            context = context,
                            contentDescription = null,
                            targetSize = artSize,
                            shape = WidgetArtShape.Rounded,
                            cornerRadius = 20.dp,
                            palette = palette,
                            modifier = GlanceModifier.size(artSize),
                        )
                        Spacer(GlanceModifier.width(14.dp))
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            CardHeader(state, palette, context)
                            Spacer(GlanceModifier.defaultWeight())
                            WidgetTrackText(
                                state,
                                palette,
                                GlanceModifier.fillMaxWidth(),
                                titleSize = 17.sp,
                                titleLines = if (size.height >= 170.dp + 64.dp * textScale) 2 else 1,
                            )
                            if (state.isAvailable) {
                                Spacer(GlanceModifier.height(8.dp))
                                WidgetProgress(state, palette)
                                Spacer(GlanceModifier.height(4.dp))
                                WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth(), heroSize = 44.dp)
                            }
                        }
                    }
                }
                stacked -> {
                    val artSize =
                        minOf(size.width - padding * 2, size.height - padding * 2 - (128.dp + 40.dp * textScale))
                            .coerceAtLeast(64.dp)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        CardHeader(state, palette, context)
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetArtwork(
                            artPath = state.artPath,
                            context = context,
                            contentDescription = null,
                            targetSize = artSize,
                            shape = WidgetArtShape.Rounded,
                            cornerRadius = 22.dp,
                            palette = palette,
                            modifier = GlanceModifier.size(artSize),
                        )
                        Spacer(GlanceModifier.defaultWeight())
                        WidgetTrackText(state, palette, GlanceModifier.fillMaxWidth(), titleSize = 17.sp)
                        if (state.isAvailable) {
                            Spacer(GlanceModifier.height(8.dp))
                            WidgetProgress(state, palette)
                            Spacer(GlanceModifier.height(4.dp))
                            WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth())
                        }
                    }
                }
                else -> {
                    val artSize = (size.height - padding * 2).coerceIn(40.dp, 64.dp)
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
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
                        Spacer(GlanceModifier.width(12.dp))
                        WidgetTrackText(
                            state,
                            palette,
                            GlanceModifier.defaultWeight(),
                            titleSize = 15.sp,
                            showArtist = size.height - padding * 2 >= 36.dp * textScale,
                        )
                        if (state.isAvailable) {
                            Spacer(GlanceModifier.width(8.dp))
                            WidgetHeroPlayButton(state, palette, context, minOf(artSize, 48.dp))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CardHeader(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
) {
    Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        WidgetEqualizer(state.isPlaying, palette, context, size = 14.dp)
        Spacer(GlanceModifier.width(6.dp))
        WidgetOverline(context.getString(R.string.widget_now_playing), palette, context, GlanceModifier.defaultWeight())
        WidgetBrandGlyph(palette)
    }
}
