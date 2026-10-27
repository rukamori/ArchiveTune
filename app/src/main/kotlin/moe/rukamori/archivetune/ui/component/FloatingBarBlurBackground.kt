/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur

@Composable
fun FloatingBarBlurBackground(
    hazeState: HazeState,
    isAcrylic: Boolean,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val surfaceColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
    val blurStyle = remember(isAcrylic, surfaceColor) {
        HazeBlurStyle {
            backgroundColor(surfaceColor)
            blurRadius(if (isAcrylic) 32.dp else 8.dp)
            noiseFactor(if (isAcrylic) 0.12f else 0f)
            colorEffects(
                listOf(
                    HazeColorEffect.tint(surfaceColor.copy(alpha = if (isAcrylic) 0.72f else 0.62f)),
                ),
            )
            fallbackColorEffect(HazeColorEffect.tint(surfaceColor))
        }
    }
    Box(
        modifier = modifier.hazeBlur(
            input = hazeInput,
            style = blurStyle,
            expandLayerBounds = true,
        ),
    )
}
