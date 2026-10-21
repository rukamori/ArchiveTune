/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.google.common.collect.ImmutableList
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WaveformSliderTrack(
    sliderState: SliderState,
    amplitudes: ImmutableList<Float>,
    activeColor: Color,
    modifier: Modifier = Modifier,
) {
    val inactiveColor = activeColor.copy(alpha = 0.26f)
    Canvas(
        modifier
            .fillMaxWidth()
            .height(WaveformTrackHeight),
    ) {
        if (amplitudes.isEmpty()) return@Canvas
        val fraction =
            if (sliderState.valueRange.endInclusive == sliderState.valueRange.start) {
                0f
            } else {
                ((sliderState.value - sliderState.valueRange.start) /
                    (sliderState.valueRange.endInclusive - sliderState.valueRange.start))
                    .coerceIn(0f, 1f)
            }
        val progress =
            if (layoutDirection == LayoutDirection.Rtl) {
                1f - fraction
            } else {
                fraction
            }
        val binWidth = size.width / amplitudes.size
        val barWidth = minOf(WaveformBarWidth.toPx(), binWidth * 0.72f)
        val maxBarHeight = size.height * 0.9f
        val minBarHeight = WaveformMinBarHeight.toPx()

        amplitudes.forEachIndexed { index, amplitude ->
            val centerX = (index + 0.5f) * binWidth
            val height = max(minBarHeight, amplitude.coerceIn(0f, 1f) * maxBarHeight)
            drawRoundRect(
                color = if (centerX <= size.width * progress) activeColor else inactiveColor,
                topLeft = Offset(centerX - barWidth / 2f, (size.height - height) / 2f),
                size = Size(barWidth, height),
                cornerRadius = CornerRadius(barWidth / 2f),
            )
        }

        val playheadX = size.width * progress
        drawLine(
            color = activeColor,
            start = Offset(playheadX, size.height * 0.08f),
            end = Offset(playheadX, size.height * 0.92f),
            strokeWidth = WaveformPlayheadWidth.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

private val WaveformTrackHeight = 40.dp
private val WaveformBarWidth = 5.dp
private val WaveformMinBarHeight = 3.dp
private val WaveformPlayheadWidth = 2.dp
