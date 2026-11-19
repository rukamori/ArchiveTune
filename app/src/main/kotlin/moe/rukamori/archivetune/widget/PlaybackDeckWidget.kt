/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
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
import androidx.glance.text.TextAlign
import moe.rukamori.archivetune.R

class PlaybackDeckWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            PlaybackDeckContent(context)
        }
    }
}

@Composable
private fun PlaybackDeckContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val padding = if (size.width < 200.dp) 10.dp else 14.dp
    val wide = size.width >= 300.dp && size.height >= 150.dp + 48.dp * textScale
    val stacked = !wide && size.height >= 220.dp + 64.dp * textScale

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = padding) {
            when {
                wide -> {
                    val recordSize = minOf(size.height - padding * 2, size.width * 0.44f, size.width - 190.dp)
                        .coerceAtLeast(72.dp)
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        DeckRecord(state, palette, context, recordSize)
                        Spacer(GlanceModifier.width(16.dp))
                        Column(modifier = GlanceModifier.defaultWeight().fillMaxHeight()) {
                            DeckHeader(state, palette, context)
                            Spacer(GlanceModifier.defaultWeight())
                            WidgetTrackText(
                                state,
                                palette,
                                GlanceModifier.fillMaxWidth(),
                                titleSize = 20.sp,
                                titleLines = if (size.height >= 190.dp + 56.dp * textScale) 2 else 1,
                            )
                            if (state.isAvailable) {
                                Spacer(GlanceModifier.height(10.dp))
                                WidgetProgress(state, palette)
                                Spacer(GlanceModifier.height(6.dp))
                                WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth(), heroSize = 48.dp)
                            }
                        }
                    }
                }
                stacked -> {
                    val controls = if (state.isAvailable) 84.dp else 0.dp
                    val recordSize =
                        minOf(size.width - padding * 2, size.height - padding * 2 - controls - 64.dp * textScale - 28.dp)
                            .coerceAtLeast(72.dp)
                    Column(
                        modifier = GlanceModifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DeckHeader(state, palette, context)
                        Box(modifier = GlanceModifier.fillMaxWidth().defaultWeight(), contentAlignment = Alignment.Center) {
                            DeckRecord(state, palette, context, recordSize)
                        }
                        WidgetTrackText(
                            state,
                            palette,
                            GlanceModifier.fillMaxWidth(),
                            titleSize = 17.sp,
                            textAlign = TextAlign.Center,
                        )
                        if (state.isAvailable) {
                            Spacer(GlanceModifier.height(10.dp))
                            WidgetProgress(state, palette)
                            Spacer(GlanceModifier.height(4.dp))
                            WidgetTransport(state, palette, context, GlanceModifier.fillMaxWidth())
                        }
                    }
                }
                else -> {
                    val recordSize = (size.height - padding * 2).coerceIn(48.dp, 88.dp)
                    Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                        DeckRecord(state, palette, context, recordSize)
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
                            WidgetHeroPlayButton(state, palette, context, 44.dp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DeckHeader(
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

@Composable
private fun DeckRecord(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    diameter: Dp,
) {
    val artSize = diameter * 0.84f
    Box(modifier = GlanceModifier.size(diameter), contentAlignment = Alignment.Center) {
        if (state.isPlaying) {
            WidgetMotion(WidgetMotionKind.Cookie, palette.motionContainer, context, GlanceModifier.fillMaxSize())
        } else {
            Image(
                provider = ImageProvider(R.drawable.widget_shape_cookie),
                contentDescription = null,
                colorFilter = ColorFilter.tint(palette.container),
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = null,
            targetSize = artSize,
            shape = WidgetArtShape.Circle,
            palette = palette,
            modifier = GlanceModifier.size(artSize),
        )
        Image(
            provider = ImageProvider(R.drawable.widget_shape_circle),
            contentDescription = null,
            colorFilter = ColorFilter.tint(palette.surface),
            modifier = GlanceModifier.size((diameter * 0.12f).coerceAtLeast(8.dp)),
        )
    }
}
