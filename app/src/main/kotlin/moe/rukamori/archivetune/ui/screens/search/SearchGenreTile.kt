/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.constrainHeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.viewmodels.MoodAndGenresArtworkState
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
internal fun SearchGenreTile(
    title: String,
    stripeColor: Long,
    artwork: MoodAndGenresArtworkState?,
    tileIndex: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val accent = lerp(Color(stripeColor), colors.primary, 0.12f)
    val variant = tileIndex % 6
    val artworkShape = GenreArtworkShapes[tileIndex % GenreArtworkShapes.size].toShape()
    val medium = MaterialTheme.shapes.medium
    val large = MaterialTheme.shapes.extraLarge
    val tileShape = when (variant) {
        1 -> medium.copy(bottomEnd = large.bottomEnd)
        2 -> medium.copy(bottomStart = large.bottomStart)
        3 -> medium.copy(topEnd = large.topEnd, bottomStart = large.bottomStart)
        4 -> medium.copy(topStart = large.topStart, bottomEnd = large.bottomEnd)
        5 -> large
        else -> medium
    }
    val compact = variant == 3 || variant == 4
    val ratio = when (variant) {
        1, 2 -> 0.8f
        3, 4 -> 2.6f
        else -> 1.2f
    }

    Card(
        onClick = onClick,
        shape = tileShape,
        colors = CardDefaults.cardColors(containerColor = colors.surfaceContainerLow),
        border = BorderStroke(1.dp, accent),
        modifier = modifier,
    ) {
        Layout(
            content = {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.onSurface,
                )
                GenreTileArtwork(artwork = artwork, accent = accent, shape = artworkShape)
            },
        ) { measurables, constraints ->
            val width = constraints.maxWidth
            val padding = 16.dp.roundToPx()
            val artworkWidth = if (compact) (width * 0.4f).roundToInt() else width
            val labelWidth = (width - padding * 2 - if (compact) artworkWidth else 0).coerceAtLeast(0)
            val label = measurables[0].measure(Constraints(maxWidth = labelWidth))
            val labelHeight = label.height + padding * 2
            val height = constraints.constrainHeight(
                max(
                    (width / ratio).roundToInt(),
                    labelHeight + if (compact) 0 else 48.dp.roundToPx(),
                ),
            )
            val artworkHeight = if (compact) height else (height - labelHeight).coerceAtLeast(0)
            val image = measurables[1].measure(Constraints.fixed(artworkWidth, artworkHeight))

            layout(width, height) {
                image.placeRelative(if (compact) width - artworkWidth else 0, if (compact) 0 else labelHeight)
                label.placeRelative(padding, padding)
            }
        }
    }
}

@Composable
private fun GenreTileArtwork(
    artwork: MoodAndGenresArtworkState?,
    accent: Color,
    shape: Shape,
) {
    val outline = MaterialTheme.colorScheme.onSurface
    val dark = MaterialTheme.colorScheme.scrim
    val tint = remember(accent, dark) {
        val red = accent.red - dark.red
        val green = accent.green - dark.green
        val blue = accent.blue - dark.blue
        ColorFilter.colorMatrix(
            ColorMatrix(
                floatArrayOf(
                    0.2126f * red, 0.7152f * red, 0.0722f * red, 0f, dark.red * 255f,
                    0.2126f * green, 0.7152f * green, 0.0722f * green, 0f, dark.green * 255f,
                    0.2126f * blue, 0.7152f * blue, 0.0722f * blue, 0f, dark.blue * 255f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }

    Box(
        modifier =
            Modifier.drawWithCache {
                val artworkOutline = shape.createOutline(
                    size = Size(size.width * 0.82f, size.height * 0.82f),
                    layoutDirection = layoutDirection,
                    density = this,
                )
                val stroke = Stroke(width = 1.dp.toPx())
                onDrawBehind {
                    for (layer in 0..3) {
                        val shift = size.width * layer * 0.06f
                        val left = if (layoutDirection == LayoutDirection.Rtl) size.width * 0.18f - shift else shift
                        translate(left = left, top = size.height * (3 - layer) * 0.06f) {
                            drawOutline(artworkOutline, accent)
                            drawOutline(artworkOutline, outline, style = stroke)
                        }
                    }
                }
            },
    ) {
        if (artwork is MoodAndGenresArtworkState.Available) {
            AsyncImage(
                model = artwork.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = tint,
                modifier =
                    Modifier
                        .fillMaxSize(0.82f)
                        .align(Alignment.TopEnd)
                        .clip(shape),
            )
        }
    }
}

private val GenreArtworkShapes = listOf(
    MaterialShapes.Arch,
    MaterialShapes.Flower,
    MaterialShapes.Slanted,
    MaterialShapes.Clover4Leaf,
    MaterialShapes.Diamond,
    MaterialShapes.SoftBurst,
    MaterialShapes.Pentagon,
    MaterialShapes.Heart,
    MaterialShapes.Sunny,
    MaterialShapes.Cookie6Sided,
    MaterialShapes.Ghostish,
    MaterialShapes.Cookie9Sided,
    MaterialShapes.Triangle,
    MaterialShapes.PixelCircle,
    MaterialShapes.Pill,
    MaterialShapes.Square,
    MaterialShapes.Circle,
)
