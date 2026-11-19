/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.R

private const val FROSTED_BASE_ALPHA = 0.32f
private const val FROSTED_HIGHLIGHT_ALPHA = 0.92f
private const val FROSTED_STATIC_ALPHA = 0.6f
private const val SWEEP_BAND_FRACTION = 0.4f
private const val SWEEP_DURATION_MS = 1_600
private const val SWEEP_PAUSE_MS = 350L
private const val APPEAR_DURATION_MS = 450

@Composable
internal fun LyricsSearchingIndicator(
    textColor: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val motionEnabled =
        remember(context) {
            Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
        }
    val appear = remember { Animatable(if (motionEnabled) 0f else 1f) }
    val sweep = remember { Animatable(0f) }

    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        appear.animateTo(1f, tween(APPEAR_DURATION_MS))
    }
    LaunchedEffect(motionEnabled) {
        if (!motionEnabled) return@LaunchedEffect
        while (isActive) {
            sweep.snapTo(0f)
            sweep.animateTo(1f, tween(SWEEP_DURATION_MS, easing = FastOutSlowInEasing))
            delay(SWEEP_PAUSE_MS)
        }
    }

    Text(
        text = stringResource(R.string.lyrics_searching_lyrics_providers),
        style = textStyle,
        color = if (motionEnabled) textColor else textColor.copy(alpha = FROSTED_STATIC_ALPHA),
        modifier =
            modifier
                .semantics { liveRegion = LiveRegionMode.Polite }
                .graphicsLayer {
                    compositingStrategy = CompositingStrategy.Offscreen
                    alpha = appear.value
                }.drawWithContent {
                    drawContent()
                    if (!motionEnabled) return@drawWithContent
                    val band = size.width * SWEEP_BAND_FRACTION
                    val center = -band + (size.width + band * 2f) * sweep.value
                    drawRect(
                        brush =
                            Brush.horizontalGradient(
                                colors =
                                    listOf(
                                        Color.Black.copy(alpha = FROSTED_BASE_ALPHA),
                                        Color.Black.copy(alpha = FROSTED_HIGHLIGHT_ALPHA),
                                        Color.Black.copy(alpha = FROSTED_BASE_ALPHA),
                                    ),
                                startX = center - band,
                                endX = center + band,
                            ),
                        blendMode = BlendMode.DstIn,
                    )
                },
    )
}
