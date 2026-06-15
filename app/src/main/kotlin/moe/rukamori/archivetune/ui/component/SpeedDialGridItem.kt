/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.utils.resize

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedDialGridItem(
    item: YTItem,
    isPinned: Boolean,
    modifier: Modifier = Modifier,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val motionScheme = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue =
            if (isActive) MaterialTheme.colorScheme.inverseOnSurface
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = motionScheme.defaultEffectsSpec(),
        label = "speedDialContainerColor",
    )
    val borderWidth by animateDpAsState(
        targetValue = if (isActive) 3.dp else 0.dp,
        animationSpec = motionScheme.defaultSpatialSpec(),
        label = "speedDialBorderWidth",
    )
    val playingScale by animateFloatAsState(
        targetValue = if (isPlaying && isActive) 0.96f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "speedDialPlayingScale",
    )
    val shape = if (isActive) MaterialTheme.shapes.extraLarge else MaterialTheme.shapes.large
    val foregroundColor = Color.White
    val pinnedIconColor = Color.Black.copy(alpha = 0.86f)
    val labelGradient = Brush.verticalGradient(
        colors = listOf(
            Color.Transparent,
            Color.Black.copy(alpha = 0.34f),
            Color.Black.copy(alpha = 0.78f),
        ),
    )

    Surface(
        color = containerColor,
        contentColor = foregroundColor,
        shape = shape,
        tonalElevation = if (isActive) 8.dp else 2.dp,
        border = BorderStroke(borderWidth, MaterialTheme.colorScheme.inverseOnSurface),
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .scale(playingScale),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val widthPx = with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
            val heightPx = with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
            val request = remember(item.thumbnail, widthPx, heightPx) {
                ImageRequest.Builder(context)
                    .data(item.thumbnail?.resize(widthPx, heightPx))
                    .size(widthPx, heightPx)
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(86.dp)
                    .background(labelGradient),
            )

            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 6.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = foregroundColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )

                if (item !is SongItem) {
                    Icon(
                        painter = painterResource(R.drawable.navigate_next),
                        contentDescription = null,
                        tint = foregroundColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            if (isPinned) {
                Icon(
                    painter = painterResource(R.drawable.bookmark_filled),
                    contentDescription = null,
                    tint = pinnedIconColor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(7.dp)
                        .size(24.dp)
                )
            }
        }
    }
}
