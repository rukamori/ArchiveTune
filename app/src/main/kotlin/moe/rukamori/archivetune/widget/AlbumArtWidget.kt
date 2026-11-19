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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.TextAlign
import moe.rukamori.archivetune.R

class AlbumArtWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode = SizeMode.Exact

    override suspend fun provideGlance(
        context: Context,
        id: GlanceId,
    ) {
        provideContent {
            AlbumArtWidgetContent(context)
        }
    }
}

@Composable
private fun AlbumArtWidgetContent(context: Context) {
    val state = currentState<Preferences>().toWidgetPlaybackState(context)
    val size = LocalSize.current
    val textScale = context.widgetTextScale()
    val frame = if (minOf(size.width, size.height) < 120.dp) 8.dp else 12.dp
    val horizontal = size.width >= size.height + 120.dp * textScale
    val captionHeight = 44.dp * textScale
    val showCaption = !horizontal && size.height >= size.width + captionHeight - 8.dp

    WidgetTheme(state.dominantColor, WidgetSurfaceStyle.Ambient, context) { palette ->
        WidgetSurface(state, palette, context, contentPadding = frame) {
            if (horizontal) {
                val coverSize = (size.height - frame * 2).coerceAtLeast(48.dp)
                Row(modifier = GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                    AlbumFrameCover(state, palette, context, coverSize, describeCover = false)
                    Spacer(GlanceModifier.width(14.dp))
                    Column(modifier = GlanceModifier.defaultWeight()) {
                        WidgetOverline(context.getString(R.string.widget_now_playing), palette, context)
                        Spacer(GlanceModifier.height(4.dp))
                        WidgetTrackText(
                            state,
                            palette,
                            GlanceModifier.fillMaxWidth(),
                            titleSize = 16.sp,
                            titleLines = if (size.height >= 120.dp * textScale) 2 else 1,
                        )
                    }
                }
            } else {
                val coverSize =
                    minOf(
                        size.width - frame * 2,
                        size.height - frame * 2 - if (showCaption) captionHeight else 0.dp,
                    ).coerceAtLeast(48.dp)
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumFrameCover(state, palette, context, coverSize, describeCover = !showCaption)
                    if (showCaption) {
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetTrackText(
                            state,
                            palette,
                            GlanceModifier.fillMaxWidth(),
                            titleSize = 14.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumFrameCover(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    coverSize: Dp,
    describeCover: Boolean,
) {
    Box(modifier = GlanceModifier.size(coverSize), contentAlignment = Alignment.BottomEnd) {
        WidgetArtwork(
            artPath = state.artPath,
            context = context,
            contentDescription = if (describeCover) state.title else null,
            targetSize = coverSize,
            shape = WidgetArtShape.Rounded,
            cornerRadius = 18.dp,
            palette = palette,
            modifier = GlanceModifier.fillMaxSize(),
        )
        if (state.isAvailable) {
            Box(modifier = GlanceModifier.padding(8.dp)) {
                WidgetHeroPlayButton(state, palette, context, (coverSize * 0.3f).coerceIn(36.dp, 56.dp))
            }
        }
    }
}
