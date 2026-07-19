/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp

fun Modifier.fadingEdge(
    left: Dp? = null,
    top: Dp? = null,
    right: Dp? = null,
    bottom: Dp? = null,
) = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black,
                            ),
                        startY = 0f,
                        endY = top.toPx(),
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom != null) {
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colors =
                            listOf(
                                Color.Black,
                                Color.Transparent,
                            ),
                        startY = size.height - bottom.toPx(),
                        endY = size.height,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (left != null) {
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                Color.Transparent,
                                Color.Black,
                            ),
                        startX = 0f,
                        endX = left.toPx(),
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (right != null) {
            drawRect(
                brush =
                    Brush.horizontalGradient(
                        colors =
                            listOf(
                                Color.Black,
                                Color.Transparent,
                            ),
                        startX = size.width - right.toPx(),
                        endX = size.width,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.fadingEdge(
    horizontal: Dp? = null,
    vertical: Dp? = null,
) = fadingEdge(
    left = horizontal,
    right = horizontal,
    top = vertical,
    bottom = vertical,
)

fun Modifier.smoothFadingEdge(
    top: Dp? = null,
    bottom: Dp? = null,
) = graphicsLayer(alpha = 0.99f)
    .drawWithContent {
        drawContent()
        if (top != null) {
            val topPx = top.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Transparent,
                                0.3f to Color.Black.copy(alpha = 0.15f),
                                0.5f to Color.Black.copy(alpha = 0.4f),
                                0.7f to Color.Black.copy(alpha = 0.7f),
                                0.85f to Color.Black.copy(alpha = 0.9f),
                                1.0f to Color.Black,
                            ),
                        startY = 0f,
                        endY = topPx,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
        if (bottom != null) {
            val bottomPx = bottom.toPx()
            drawRect(
                brush =
                    Brush.verticalGradient(
                        colorStops =
                            arrayOf(
                                0.0f to Color.Black,
                                0.15f to Color.Black.copy(alpha = 0.9f),
                                0.3f to Color.Black.copy(alpha = 0.7f),
                                0.5f to Color.Black.copy(alpha = 0.4f),
                                0.7f to Color.Black.copy(alpha = 0.15f),
                                1.0f to Color.Transparent,
                            ),
                        startY = size.height - bottomPx,
                        endY = size.height,
                    ),
                blendMode = BlendMode.DstIn,
            )
        }
    }

fun Modifier.smoothFadingEdge(vertical: Dp) =
    smoothFadingEdge(
        top = vertical,
        bottom = vertical,
    )
