/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryMediaListCard(
    title: String,
    subtitle: String,
    artworkUrls: List<String>,
    @DrawableRes fallbackIconRes: Int,
    sourceLabel: String,
    sourceColor: Color,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    contentAlpha: Float = 1f,
    metadataTrailingContent: (@Composable RowScope.() -> Unit)? = null,
    additionalAction: (@Composable RowScope.() -> Unit)? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource, pressedScale = 0.98f)

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = contentAlpha
                }.clip(MaterialTheme.shapes.largeIncreased)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryArtworkMosaic(
            artworkUrls = artworkUrls,
            fallbackIconRes = fallbackIconRes,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(64.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text = sourceLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = sourceColor,
                    maxLines = 1,
                    softWrap = false,
                    modifier =
                        Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(sourceColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                )
                Text(
                    text = subtitle,
                    modifier = Modifier.weight(1f, fill = false),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                metadataTrailingContent?.invoke(this)
            }
        }

        FilledTonalIconButton(
            onClick = onPlay,
            shapes = IconButtonDefaults.shapes(),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(20.dp),
            )
        }

        IconButton(onClick = onMenuClick) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.options_label),
            )
        }

        additionalAction?.invoke(this)
    }
}
