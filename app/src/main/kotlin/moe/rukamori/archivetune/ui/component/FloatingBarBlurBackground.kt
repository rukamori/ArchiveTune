/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
    backdrop: BackdropSnapshot? = null,
) {
    val surfaceColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surfaceContainerHigh
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S && backdrop != null) {
        // Below API 31 Haze cannot blur live content; approximate it with a
        // stack-blurred snapshot of what is behind the bar.
        val snapshot = backdrop.image
        Box(modifier) {
            if (snapshot != null) {
                Image(
                    bitmap = snapshot,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    alignment = Alignment.BottomCenter,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Box(
                Modifier.fillMaxSize()
                    .background(surfaceColor.copy(alpha = if (isAcrylic) 0.72f else 0.62f)),
            )
        }
        return
    }
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
