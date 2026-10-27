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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.PreferencesGlanceStateDefinition

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
    val textScale = context.resources.configuration.fontScale.coerceAtLeast(1f)
    val frame = if (minOf(size.width, size.height) < 112.dp) 6.dp else 12.dp
    val horizontal = size.width >= size.height + 112.dp * textScale
    val showCaption = size.height >= size.width + 40.dp * textScale
    val coverSize = minOf(
        size.width - frame * 2,
        size.height - frame * 2 - if (showCaption) 48.dp * textScale else 0.dp,
    ).coerceAtLeast(48.dp)

    WidgetTheme(state.dominantColor) { palette ->
        Box(
            modifier = GlanceModifier.fillMaxSize().background(palette.surface)
                .cornerRadius(20.dp).clickable(openArchiveTuneAction(context)).padding(frame),
        ) {
            if (horizontal) {
                Row(
                    modifier = GlanceModifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArtCover(state, palette, context, coverSize, showMetadata = true)
                    Spacer(GlanceModifier.width(12.dp))
                    WidgetTrackInfo(
                        state, palette, GlanceModifier.defaultWeight(), titleSize = 14,
                        titleLines = if (size.height >= 88.dp * textScale) 2 else 1,
                        showArtist = size.height - frame * 2 >= 40.dp * textScale,
                    )
                }
            } else {
                Column(
                    modifier = GlanceModifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AlbumArtCover(state, palette, context, coverSize, showMetadata = showCaption)
                    if (showCaption) {
                        Spacer(GlanceModifier.height(8.dp))
                        WidgetTrackInfo(state, palette, GlanceModifier.fillMaxWidth(), titleSize = 14)
                    }
                }
            }
        }
    }
}

@Composable
private fun AlbumArtCover(
    state: WidgetPlaybackState,
    palette: WidgetPalette,
    context: Context,
    coverSize: Dp,
    showMetadata: Boolean,
) {
    Box(modifier = GlanceModifier.size(coverSize), contentAlignment = Alignment.BottomEnd) {
        WidgetArtwork(
            artPath = state.artPath, context = context,
            contentDescription = if (showMetadata) null else state.title,
            targetSize = coverSize, cornerRadius = 8.dp,
            palette = palette, modifier = GlanceModifier.fillMaxSize(),
        )
        if (state.isAvailable) {
            Box(modifier = GlanceModifier.padding(4.dp)) {
                WidgetPlayPauseButton(state, palette, context, cornerRadius = 12.dp)
            }
        }
    }
}
