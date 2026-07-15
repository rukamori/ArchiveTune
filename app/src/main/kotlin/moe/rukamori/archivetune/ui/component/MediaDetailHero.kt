/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize

@Composable
fun MediaDetailHero(
    title: String,
    thumbnailUrl: String?,
    @DrawableRes fallbackIcon: Int,
    systemBarsTopPadding: Dp,
    isAdded: Boolean,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: AnnotatedString? = null,
    metadata: String? = null,
    description: String? = null,
    additionalPrimaryActions: (@Composable RowScope.(Color) -> Unit)? = null,
) {
    val surfaceColor = MaterialTheme.colorScheme.surface
    val heroContentColor =
        if (surfaceColor.luminance() > 0.5f) {
            MaterialTheme.colorScheme.onSurface
        } else {
            Color.White
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = MediaDetailHeroMinHeight)
                .background(surfaceColor),
    ) {
        if (thumbnailUrl != null) {
            AsyncImage(
                model =
                    thumbnailUrl.resize(
                        width = MediaDetailHeroArtworkSizePx,
                        height = MediaDetailHeroArtworkSizePx,
                        sizeBuckets = MediaDetailHeroArtworkSizeBuckets,
                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                    ),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        } else {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(fallbackIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(96.dp),
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.42f),
                            0.18f to Color.Transparent,
                            0.42f to Color.Transparent,
                            0.72f to surfaceColor.copy(alpha = 0.78f),
                            1f to surfaceColor,
                        ),
                    ),
        )

        Column(
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .widthIn(max = MediaDetailContentMaxWidth)
                    .padding(
                        start = MediaDetailHorizontalPadding,
                        top = systemBarsTopPadding + AppBarHeight + 96.dp,
                        end = MediaDetailHorizontalPadding,
                        bottom = 24.dp,
                    ),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineLarge,
                color = heroContentColor,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            subtitle?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = heroContentColor.copy(alpha = 0.82f),
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            description?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = heroContentColor.copy(alpha = 0.76f),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            metadata?.takeIf(String::isNotBlank)?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelMedium,
                    color = heroContentColor.copy(alpha = 0.62f),
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 16.dp),
                )
            }

            MediaDetailPrimaryActions(
                isAdded = isAdded,
                contentColor = heroContentColor,
                contrastingColor = surfaceColor,
                addContentDescription = addContentDescription,
                removeContentDescription = removeContentDescription,
                onShuffle = onShuffle,
                onPlay = onPlay,
                onToggleAdd = onToggleAdd,
                additionalActions = additionalPrimaryActions,
                modifier = Modifier.padding(top = 12.dp),
            )
        }
    }
}

@Composable
fun MediaDetailPrimaryActions(
    isAdded: Boolean,
    contentColor: Color,
    contrastingColor: Color,
    @StringRes addContentDescription: Int,
    @StringRes removeContentDescription: Int,
    onShuffle: (() -> Unit)?,
    onPlay: (() -> Unit)?,
    onToggleAdd: (() -> Unit)?,
    modifier: Modifier = Modifier,
    additionalActions: (@Composable RowScope.(Color) -> Unit)? = null,
) {
    val secondaryButtonColors =
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = contentColor.copy(alpha = 0.16f),
            contentColor = contentColor,
            disabledContainerColor = contentColor.copy(alpha = 0.08f),
            disabledContentColor = contentColor.copy(alpha = 0.38f),
        )

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .widthIn(max = MediaDetailContentMaxWidth)
                .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        onShuffle?.let { shuffle ->
            FilledTonalIconButton(
                onClick = shuffle,
                shape = CircleShape,
                colors = secondaryButtonColors,
                modifier = Modifier.size(MediaDetailSecondaryActionSize),
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = stringResource(R.string.shuffle),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        onPlay?.let { play ->
            val playButtonHeight = ButtonDefaults.MediumContainerHeight
            Button(
                onClick = play,
                shape = RoundedCornerShape(percent = 50),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = contentColor,
                        contentColor = contrastingColor,
                    ),
                contentPadding = ButtonDefaults.contentPaddingFor(playButtonHeight, hasStartIcon = true),
                modifier = Modifier.heightIn(min = playButtonHeight),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(playButtonHeight)),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(playButtonHeight)))
                Text(
                    text = stringResource(R.string.play),
                    style = ButtonDefaults.textStyleFor(playButtonHeight),
                    fontWeight = FontWeight.Bold,
                )
            }
        }

        onToggleAdd?.let { toggleAdd ->
            FilledTonalIconButton(
                onClick = toggleAdd,
                shape = CircleShape,
                colors = secondaryButtonColors,
                modifier = Modifier.size(MediaDetailSecondaryActionSize),
            ) {
                Icon(
                    painter = painterResource(if (isAdded) R.drawable.done else R.drawable.add),
                    contentDescription =
                        stringResource(
                            if (isAdded) removeContentDescription else addContentDescription,
                        ),
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        additionalActions?.invoke(this, contentColor)
    }
}

@Composable
fun MediaDetailAction(
    @StringRes contentDescription: Int,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
    content: @Composable () -> Unit,
) {
    val actionDescription = stringResource(contentDescription)
    val colors =
        if (isDestructive) {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                contentColor = MaterialTheme.colorScheme.error,
                disabledContainerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.08f),
                disabledContentColor = MaterialTheme.colorScheme.error.copy(alpha = 0.38f),
            )
        } else {
            IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = contentColor.copy(alpha = 0.16f),
                contentColor = contentColor,
                disabledContainerColor = contentColor.copy(alpha = 0.08f),
                disabledContentColor = contentColor.copy(alpha = 0.38f),
            )
        }

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        shape = CircleShape,
        colors = colors,
        modifier =
            modifier
                .size(MediaDetailActionSize)
                .semantics { this.contentDescription = actionDescription },
    ) {
        content()
    }
}

@Composable
fun MediaDetailIconAction(
    @DrawableRes icon: Int,
    @StringRes contentDescription: Int,
    contentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isDestructive: Boolean = false,
) {
    MediaDetailAction(
        contentDescription = contentDescription,
        contentColor = contentColor,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        isDestructive = isDestructive,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(22.dp),
        )
    }
}

private const val MediaDetailHeroArtworkSizePx = 1200
private val MediaDetailHeroArtworkSizeBuckets = listOf(MediaDetailHeroArtworkSizePx)
private val MediaDetailHeroMinHeight = 560.dp
private val MediaDetailHorizontalPadding = 24.dp
private val MediaDetailContentMaxWidth = 720.dp
private val MediaDetailSecondaryActionSize = 52.dp
private val MediaDetailActionSize = 48.dp
