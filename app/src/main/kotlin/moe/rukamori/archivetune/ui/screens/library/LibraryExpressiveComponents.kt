/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerBasedShape
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.player.EditorialMorphShape

internal val LibraryContentHorizontalPadding = 20.dp

internal val LibraryGridCardTextInset = 10.dp

private val LibraryGridCardBottomCorner = CornerSize(16.dp)

@Composable
@ReadOnlyComposable
internal fun libraryGridCardShape(): CornerBasedShape =
    MaterialTheme.shapes.extraLarge.copy(
        bottomStart = LibraryGridCardBottomCorner,
        bottomEnd = LibraryGridCardBottomCorner,
    )

internal fun Modifier.libraryHorizontalBleed(horizontal: Dp): Modifier =
    layout { measurable, constraints ->
        if (!constraints.hasBoundedWidth) {
            val placeable = measurable.measure(constraints)
            return@layout layout(placeable.width, placeable.height) { placeable.placeRelative(0, 0) }
        }
        val bleedPx = horizontal.roundToPx()
        val bledWidth = constraints.maxWidth + bleedPx * 2
        val placeable = measurable.measure(constraints.copy(minWidth = bledWidth, maxWidth = bledWidth))
        layout(constraints.maxWidth, placeable.height) {
            placeable.placeRelative(-bleedPx, 0)
        }
    }

@Immutable
internal data class LibraryHeroStat(
    val value: String,
    val label: String,
)

@Composable
internal fun libraryDurationText(totalSeconds: Long): String? {
    if (totalSeconds <= 0L) return null
    val days = (totalSeconds / 86_400L).toInt()
    val hours = ((totalSeconds % 86_400L) / 3_600L).toInt()
    val minutes = ((totalSeconds % 3_600L) / 60L).toInt()
    return when {
        days > 0 -> stringResource(R.string.library_duration_days_hours, days, hours)
        hours > 0 -> stringResource(R.string.library_duration_hours_minutes, hours, minutes)
        else -> stringResource(R.string.library_duration_minutes, minutes.coerceAtLeast(1))
    }
}

@Composable
internal fun rememberLibraryPressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.96f,
): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    return animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "LibraryPressScale",
    )
}

@Composable
internal fun LibraryCollectionHero(
    overline: String,
    title: String,
    stats: List<LibraryHeroStat>,
    artworkUrls: List<String>,
    @DrawableRes fallbackIconRes: Int,
    modifier: Modifier = Modifier,
    primaryLabel: String? = null,
    @DrawableRes primaryIconRes: Int = R.drawable.play,
    primaryEnabled: Boolean = true,
    onPrimary: (() -> Unit)? = null,
    onShuffle: (() -> Unit)? = null,
    circularArtwork: Boolean = false,
) {
    val tint =
        rememberArtworkCardColor(
            thumbnailUrl = artworkUrls.firstOrNull(),
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )
    val containerColor by animateColorAsState(
        targetValue = tint,
        animationSpec = MaterialTheme.motionScheme.slowEffectsSpec(),
        label = "LibraryHeroContainer",
    )

    Surface(
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                LibraryHeroArtwork(
                    artworkUrls = artworkUrls,
                    fallbackIconRes = fallbackIconRes,
                    circular = circularArtwork,
                    ringColor = containerColor,
                    modifier = Modifier.size(104.dp),
                )
                Spacer(modifier = Modifier.width(18.dp))
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) { heading() },
                ) {
                    Text(
                        text = overline,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (stats.isNotEmpty()) {
                LibraryStatRow(stats = stats)
            }

            if (primaryLabel != null && onPrimary != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val height = ButtonDefaults.MediumContainerHeight
                    Button(
                        onClick = onPrimary,
                        enabled = primaryEnabled,
                        shapes = ButtonDefaults.shapesFor(height),
                        contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
                        modifier =
                            Modifier
                                .weight(1f)
                                .heightIn(min = height),
                    ) {
                        Icon(
                            painter = painterResource(primaryIconRes),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
                        )
                        Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(height)))
                        Text(
                            text = primaryLabel,
                            style = ButtonDefaults.textStyleFor(height),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (onShuffle != null) {
                        FilledTonalIconButton(
                            onClick = onShuffle,
                            enabled = primaryEnabled,
                            shapes = IconButtonDefaults.shapes(),
                            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.shuffle),
                                contentDescription = stringResource(R.string.shuffle),
                                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibraryHeroArtwork(
    artworkUrls: List<String>,
    @DrawableRes fallbackIconRes: Int,
    circular: Boolean,
    ringColor: Color,
    modifier: Modifier = Modifier,
) {
    val leadShape = MaterialShapes.Cookie9Sided.toShape()
    val satelliteUrl = artworkUrls.getOrNull(1).takeUnless { circular }
    Box(modifier = modifier) {
        val leadUrl = artworkUrls.firstOrNull()
        Box(
            modifier =
                Modifier
                    .fillMaxSize(if (satelliteUrl != null) 0.86f else 1f)
                    .clip(leadShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            if (leadUrl != null) {
                AsyncImage(
                    model = leadUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Icon(
                    painter = painterResource(fallbackIconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxSize(0.38f),
                )
            }
        }
        if (satelliteUrl != null) {
            AsyncImage(
                model = satelliteUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxSize(0.44f)
                        .border(width = 3.dp, color = ringColor, shape = CircleShape)
                        .padding(3.dp)
                        .clip(CircleShape),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LibraryStatRow(stats: List<LibraryHeroStat>) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        stats.forEach { stat ->
            LibraryStatBlock(stat = stat)
        }
    }
}

@Composable
private fun LibraryStatBlock(stat: LibraryHeroStat) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    Column(
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.56f))
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .semantics(mergeDescendants = true) {},
    ) {
        AnimatedContent(
            targetState = stat.value,
            transitionSpec = {
                val transform =
                    if (animationsDisabled) {
                        fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                    } else {
                        (
                            slideInVertically(motion.defaultSpatialSpec()) { height -> height / 2 } +
                                fadeIn(motion.defaultEffectsSpec())
                        ) togetherWith (
                            slideOutVertically(motion.defaultSpatialSpec()) { height -> -height / 2 } +
                                fadeOut(motion.fastEffectsSpec())
                        )
                    }
                transform using SizeTransform(clip = false)
            },
            label = "LibraryStatValue",
        ) { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
            )
        }
        Text(
            text = stat.label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun <T> LibraryFilterGroup(
    options: List<T>,
    selected: T?,
    label: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    Row(
        modifier = modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        options.forEachIndexed { index, option ->
            val checked = option == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(option) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                modifier =
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .semantics { role = Role.RadioButton },
            ) {
                AnimatedVisibility(
                    visible = checked,
                    enter = expandHorizontally(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                    exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    }
                }
                Text(
                    text = label(option),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun LibrarySortButton(
    label: String,
    descending: Boolean,
    directionEnabled: Boolean,
    onToggleDirection: () -> Unit,
    modifier: Modifier = Modifier,
    menuContent: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit,
) {
    var menuOpen by rememberSaveable { mutableStateOf(false) }
    val motion = MaterialTheme.motionScheme
    val arrowRotation by animateFloatAsState(
        targetValue = if (descending) 0f else 180f,
        animationSpec = motion.defaultSpatialSpec(),
        label = "LibrarySortArrow",
    )
    val chevronRotation by animateFloatAsState(
        targetValue = if (menuOpen) 180f else 0f,
        animationSpec = motion.fastSpatialSpec(),
        label = "LibrarySortChevron",
    )
    val directionDescription = stringResource(if (descending) R.string.sort_descending else R.string.sort_ascending)

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.TonalLeadingButton(
                    onClick = {
                        if (directionEnabled) onToggleDirection() else menuOpen = true
                    },
                    modifier =
                        Modifier
                            .heightIn(min = 48.dp)
                            .widthIn(max = 220.dp)
                            .semantics {
                                if (directionEnabled) stateDescription = directionDescription
                            },
                ) {
                    if (directionEnabled) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_downward),
                            contentDescription = null,
                            modifier =
                                Modifier
                                    .size(ButtonDefaults.IconSize)
                                    .graphicsLayer { rotationZ = arrowRotation },
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                    }
                    Text(
                        text = label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            trailingButton = {
                SplitButtonDefaults.TonalTrailingButton(
                    checked = menuOpen,
                    onCheckedChange = { menuOpen = it },
                    modifier = Modifier.heightIn(min = 48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.expand_more),
                        contentDescription = stringResource(R.string.library_sort_options),
                        modifier =
                            Modifier
                                .size(SplitButtonDefaults.TrailingIconSize)
                                .graphicsLayer { rotationZ = chevronRotation },
                    )
                }
            },
        )
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            modifier = Modifier.widthIn(min = 200.dp),
        ) {
            menuContent { menuOpen = false }
        }
    }
}

@Composable
internal fun LibrarySortMenuItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = {
            Text(
                text = label,
                style =
                    if (selected) {
                        MaterialTheme.typography.labelLargeEmphasized
                    } else {
                        MaterialTheme.typography.bodyLarge
                    },
            )
        },
        onClick = onClick,
        leadingIcon = {
            Icon(
                painter =
                    painterResource(
                        if (selected) R.drawable.radio_button_checked else R.drawable.radio_button_unchecked,
                    ),
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

@Composable
internal fun LibraryViewModeToggle(
    isGrid: Boolean,
    onGridChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        ToggleButton(
            checked = !isGrid,
            onCheckedChange = { onGridChange(false) },
            shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
        ) {
            Icon(
                painter = painterResource(R.drawable.list),
                contentDescription = stringResource(R.string.list_view),
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
        ToggleButton(
            checked = isGrid,
            onCheckedChange = { onGridChange(true) },
            shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            modifier =
                Modifier
                    .heightIn(min = 48.dp)
                    .semantics { role = Role.RadioButton },
        ) {
            Icon(
                painter = painterResource(R.drawable.grid_view),
                contentDescription = stringResource(R.string.grid_view),
                modifier = Modifier.size(ButtonDefaults.IconSize),
            )
        }
    }
}

@Composable
internal fun LibrarySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = LibraryContentHorizontalPadding, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(vertical = 4.dp)
                    .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailingContent?.invoke()
        if (actionLabel != null && onAction != null) {
            TextButton(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
            ) {
                Text(text = actionLabel)
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
            }
        }
    }
}

@Composable
internal fun LibraryArtworkMosaic(
    artworkUrls: List<String>,
    @DrawableRes fallbackIconRes: Int,
    shape: Shape,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier =
            modifier
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        when {
            artworkUrls.size >= 4 -> {
                Column(modifier = Modifier.fillMaxSize()) {
                    repeat(2) { row ->
                        Row(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                        ) {
                            repeat(2) { column ->
                                AsyncImage(
                                    model = artworkUrls[row * 2 + column],
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .weight(1f)
                                            .fillMaxHeight(),
                                )
                            }
                        }
                    }
                }
            }

            artworkUrls.isNotEmpty() -> {
                AsyncImage(
                    model = artworkUrls.first(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            else -> {
                Icon(
                    painter = painterResource(fallbackIconRes),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxSize(0.4f),
                )
            }
        }
    }
}

private val NowPlayingArtworkMorph by lazy { Morph(MaterialShapes.Square, MaterialShapes.Cookie9Sided) }

@Composable
internal fun LibraryNowPlayingArtwork(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    modifier: Modifier = Modifier,
) {
    val progress by animateFloatAsState(
        targetValue = if (isActive) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "LibraryNowPlayingMorph",
    )
    val shape = remember(progress) { EditorialMorphShape(NowPlayingArtworkMorph, progress) }
    ItemThumbnail(
        thumbnailUrl = thumbnailUrl,
        isActive = isActive,
        isPlaying = isPlaying,
        shape = shape,
        contentScale = ContentScale.Crop,
        showPlaceholder = true,
        modifier = modifier,
    )
}
