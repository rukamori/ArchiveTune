/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazePerformanceMode
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass

@OptIn(ExperimentalHazeApi::class)
@Composable
fun FloatingBarGlassBackground(
    hazeState: HazeState,
    shape: RoundedCornerShape,
    pureBlack: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val surfaceColor = if (pureBlack) Color.Black else colorScheme.surfaceContainerHigh
    val isDarkSurface = pureBlack || colorScheme.surface.luminance() < colorScheme.onSurface.luminance()
    val hazeInput = remember(hazeState) { HazeInput.Sources(hazeState) }
    val glassStyle = remember(shape, surfaceColor, isDarkSurface) {
        GlassStyle.clear.then {
            shape(shape)
            backgroundColor(surfaceColor)
            tint(surfaceColor.copy(alpha = if (isDarkSurface) 0.2f else 0.16f))
            whitePoint(if (isDarkSurface) -0.08f else 0.08f)
            contrast(0f)
            chromaMultiplier(1f)
            specularIntensity(0.32f)
            edgeShadow(Color.Black.copy(alpha = 0.06f))
            ambientResponse(0.12f)
        }
    }
    Box(
        modifier = modifier.hazeGlass(
            input = hazeInput,
            style = glassStyle,
            performanceMode = HazePerformanceMode.Default,
            expandLayerBounds = true,
        ),
    )
}
