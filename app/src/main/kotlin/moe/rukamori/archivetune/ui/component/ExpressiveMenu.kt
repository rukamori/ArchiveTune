/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.MotionDurationScale
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.media3.exoplayer.offline.Download
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import kotlin.math.ceil

private val HeroArtworkSize = 96.dp
private val QuickActionMinWidth = 80.dp
private val QuickActionContainerSize = 56.dp
private val RowIconContainerSize = 40.dp
private val TwoPaneMinWidth = 560.dp
private const val EntranceStaggerMillis = 45L
private const val EntranceMaxStaggerSteps = 7

@Immutable
data class MenuAction(
    val label: String,
    val onClick: () -> Unit,
    @DrawableRes val icon: Int = 0,
    val iconContent: (@Composable () -> Unit)? = null,
    val enabled: Boolean = true,
    val active: Boolean = false,
    val busy: Boolean = false,
)

fun NewAction.toMenuAction(): MenuAction =
    MenuAction(
        label = text,
        onClick = onClick,
        iconContent = icon,
        enabled = enabled,
    )

enum class MenuArtworkStyle {
    Song,
    Album,
    Artist,
    Playlist,
}

enum class MenuRowTone {
    Neutral,
    Active,
    Destructive,
}

private fun MenuArtworkStyle.morphPolygons(): Pair<RoundedPolygon, RoundedPolygon> =
    when (this) {
        MenuArtworkStyle.Song -> MaterialShapes.Circle to MaterialShapes.Cookie9Sided
        MenuArtworkStyle.Album -> MaterialShapes.Square to MaterialShapes.Cookie4Sided
        MenuArtworkStyle.Artist -> MaterialShapes.Circle to MaterialShapes.Cookie12Sided
        MenuArtworkStyle.Playlist -> MaterialShapes.Square to MaterialShapes.Slanted
    }

private class MenuEntranceCounter {
    var next = 0
}

private val LocalMenuEntranceCounter = staticCompositionLocalOf<MenuEntranceCounter?> { null }

@Composable
fun Modifier.menuEntrance(): Modifier {
    val counter = LocalMenuEntranceCounter.current
    if (counter == null || LocalAnimationsDisabled.current) return this

    val order = remember { counter.next++ }
    val motion = MaterialTheme.motionScheme
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(1f) }

    LaunchedEffect(Unit) {
        val durationScale = coroutineContext[MotionDurationScale]?.scaleFactor ?: 1f
        delay((order.coerceAtMost(EntranceMaxStaggerSteps) * EntranceStaggerMillis * durationScale).toLong())
        launch { alpha.animateTo(1f, motion.defaultEffectsSpec()) }
        offset.animateTo(0f, motion.defaultSpatialSpec())
    }

    return graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value * 28.dp.toPx()
        val scale = 1f - offset.value * 0.04f
        scaleX = scale
        scaleY = scale
    }
}

@Composable
fun MenuSheetLayout(
    hero: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    leadingContent: @Composable ColumnScope.() -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val counter = remember { MenuEntranceCounter() }
    val bottomPadding = WindowInsets.systemBars.asPaddingValues().calculateBottomPadding() + 16.dp

    CompositionLocalProvider(LocalMenuEntranceCounter provides counter) {
        BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
            if (maxWidth >= TwoPaneMinWidth) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier =
                            Modifier
                                .weight(0.45f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = bottomPadding),
                    ) {
                        hero()
                        leadingContent()
                    }
                    Column(
                        verticalArrangement = Arrangement.spacedBy(20.dp),
                        modifier =
                            Modifier
                                .weight(0.55f)
                                .verticalScroll(rememberScrollState())
                                .padding(bottom = bottomPadding),
                    ) {
                        content()
                    }
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = bottomPadding),
                ) {
                    hero()
                    leadingContent()
                    Spacer(modifier = Modifier.size(4.dp))
                    content()
                }
            }
        }
    }
}

@Composable
fun MenuHero(
    title: String,
    style: MenuArtworkStyle,
    thumbnails: List<String>,
    @DrawableRes placeholderIcon: Int,
    modifier: Modifier = Modifier,
    overline: String? = null,
    subtitle: String? = null,
    meta: String? = null,
    trailing: (@Composable () -> Unit)? = null,
    bottom: (@Composable () -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier =
            modifier
                .fillMaxWidth()
                .menuEntrance(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                MenuHeroArtwork(
                    style = style,
                    thumbnails = thumbnails,
                    placeholderIcon = placeholderIcon,
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    if (!overline.isNullOrBlank()) {
                        Text(
                            text = overline,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!subtitle.isNullOrBlank()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee(),
                        )
                    }
                    if (!meta.isNullOrBlank()) {
                        Text(
                            text = meta,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                trailing?.invoke()
            }

            bottom?.invoke()
        }
    }
}

@Composable
private fun MenuHeroArtwork(
    style: MenuArtworkStyle,
    thumbnails: List<String>,
    @DrawableRes placeholderIcon: Int,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motion = MaterialTheme.motionScheme
    val morph =
        remember(style) {
            val (start, end) = style.morphPolygons()
            Morph(start, end)
        }
    val progress = remember { Animatable(if (animationsDisabled) 1f else 0f) }

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) {
            progress.snapTo(1f)
        } else {
            progress.animateTo(1f, motion.slowSpatialSpec())
        }
    }

    Box(
        modifier =
            Modifier
                .size(HeroArtworkSize)
                .graphicsLayer {
                    val value = progress.value
                    val scale = 0.8f + 0.2f * value
                    scaleX = scale
                    scaleY = scale
                    rotationZ = (1f - value) * -16f
                    shape = EditorialMorphShape(morph, value.coerceIn(0f, 1f))
                    clip = true
                },
    ) {
        PlaylistThumbnail(
            thumbnails = thumbnails,
            size = HeroArtworkSize,
            placeHolder = {
                Icon(
                    painter = painterResource(placeholderIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(40.dp),
                )
            },
            shape = RectangleShape,
        )
    }
}

@Composable
fun MenuFavoriteToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val scope = rememberCoroutineScope()
    val iconScale = remember { Animatable(1f) }

    FilledTonalIconToggleButton(
        checked = checked,
        onCheckedChange = { value ->
            onCheckedChange(value)
            if (!animationsDisabled) {
                scope.launch {
                    iconScale.snapTo(0.6f)
                    iconScale.animateTo(1f, motion.fastSpatialSpec())
                }
            }
        },
        enabled = enabled,
        shapes = IconButtonDefaults.toggleableShapes(),
        colors =
            IconButtonDefaults.filledTonalIconToggleButtonColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                checkedContainerColor = MaterialTheme.colorScheme.errorContainer,
                checkedContentColor = MaterialTheme.colorScheme.onErrorContainer,
            ),
        modifier = modifier.size(IconButtonDefaults.mediumContainerSize()),
    ) {
        Icon(
            painter = painterResource(if (checked) R.drawable.favorite else R.drawable.favorite_border),
            contentDescription = stringResource(R.string.menu_favorite),
            modifier =
                Modifier
                    .size(IconButtonDefaults.mediumIconSize)
                    .graphicsLayer {
                        scaleX = iconScale.value
                        scaleY = iconScale.value
                    },
        )
    }
}

@Composable
fun MenuHeroToggleButton(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    label: String,
    @DrawableRes icon: Int,
    @DrawableRes checkedIcon: Int,
    modifier: Modifier = Modifier,
) {
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = ButtonDefaults.MinHeight),
    ) {
        Icon(
            painter = painterResource(if (checked) checkedIcon else icon),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun MenuPrimaryActions(
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    val primary = actions.firstOrNull() ?: return
    val secondary = actions.drop(1)
    val height = ButtonDefaults.MediumContainerHeight

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .menuEntrance(),
    ) {
        Button(
            onClick = primary.onClick,
            enabled = primary.enabled,
            shapes = ButtonDefaults.shapesFor(height),
            contentPadding = ButtonDefaults.contentPaddingFor(height, hasStartIcon = true),
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = height),
        ) {
            MenuActionIcon(
                action = primary,
                modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
            )
            Spacer(modifier = Modifier.size(ButtonDefaults.iconSpacingFor(height)))
            Text(
                text = primary.label,
                style = ButtonDefaults.textStyleFor(height),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        secondary.forEach { action ->
            TooltipBox(
                positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                tooltip = { PlainTooltip { Text(text = action.label) } },
                state = rememberTooltipState(),
            ) {
                FilledTonalIconButton(
                    onClick = action.onClick,
                    enabled = action.enabled,
                    shapes = IconButtonDefaults.shapes(),
                    modifier =
                        Modifier
                            .size(IconButtonDefaults.mediumContainerSize())
                            .semantics { contentDescription = action.label },
                ) {
                    MenuActionIcon(
                        action = action,
                        modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
                    )
                }
            }
        }
    }
}

@Composable
fun MenuQuickActions(
    actions: List<MenuAction>,
    modifier: Modifier = Modifier,
) {
    if (actions.isEmpty()) return

    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth()
                .menuEntrance(),
    ) {
        val maxColumns = (maxWidth / QuickActionMinWidth).toInt().coerceIn(3, 5)
        val rowCount = ceil(actions.size / maxColumns.toFloat()).toInt().coerceAtLeast(1)
        val columns = ceil(actions.size / rowCount.toFloat()).toInt().coerceAtLeast(1)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            actions.chunked(columns).forEach { rowActions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    rowActions.forEach { action ->
                        MenuQuickActionTile(
                            action = action,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    repeat(columns - rowActions.size) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuQuickActionTile(
    action: MenuAction,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val corner by animateFloatAsState(
        targetValue = if (pressed || action.active) 50f else 30f,
        animationSpec = motion.fastSpatialSpec(),
        label = "quickActionCorner",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "quickActionScale",
    )
    val containerColor by animateColorAsState(
        targetValue = if (action.active) colors.primary else colors.surfaceContainerHigh,
        animationSpec = motion.defaultEffectsSpec(),
        label = "quickActionContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (action.active) colors.onPrimary else colors.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "quickActionContent",
    )
    val activeStateDescription = stringResource(R.string.menu_state_on)

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    interactionSource = interactionSource,
                    indication = LocalIndication.current,
                    enabled = action.enabled,
                    role = Role.Button,
                    onClick = action.onClick,
                ).semantics {
                    if (action.active) stateDescription = activeStateDescription
                }.alpha(if (action.enabled) 1f else 0.38f)
                .padding(vertical = 6.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(QuickActionContainerSize)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        shape = RoundedCornerShape(CornerSize(corner.coerceIn(0f, 50f)))
                        clip = true
                    }.background(containerColor),
        ) {
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                MenuActionIcon(
                    action = action,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        Text(
            text = action.label,
            style = MaterialTheme.typography.labelMedium,
            color = colors.onSurface,
            textAlign = TextAlign.Center,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp),
        )
    }
}

@Composable
private fun MenuActionIcon(
    action: MenuAction,
    modifier: Modifier = Modifier,
) {
    val iconContent = action.iconContent
    when {
        action.busy -> CircularWavyProgressIndicator(modifier = modifier)
        iconContent != null -> Box(modifier = modifier, contentAlignment = Alignment.Center) { iconContent() }
        else ->
            Icon(
                painter = painterResource(action.icon),
                contentDescription = null,
                modifier = modifier,
            )
    }
}

internal sealed interface MenuRowSpec {
    val key: Any

    class Action(
        override val key: Any,
        val title: String,
        @DrawableRes val icon: Int,
        val onClick: () -> Unit,
        val supporting: String?,
        val tone: MenuRowTone,
        val enabled: Boolean,
        val navigates: Boolean,
        val iconRotation: (() -> Float)?,
        val leading: (@Composable () -> Unit)?,
    ) : MenuRowSpec

    class Toggle(
        override val key: Any,
        val title: String,
        @DrawableRes val icon: Int,
        val checked: Boolean,
        val onCheckedChange: (Boolean) -> Unit,
        val supporting: String?,
        val enabled: Boolean,
    ) : MenuRowSpec
}

@Stable
class MenuSectionScope internal constructor() {
    internal val rows = mutableListOf<MenuRowSpec>()

    fun action(
        title: String,
        @DrawableRes icon: Int,
        onClick: () -> Unit,
        supporting: String? = null,
        tone: MenuRowTone = MenuRowTone.Neutral,
        enabled: Boolean = true,
        navigates: Boolean = false,
        iconRotation: (() -> Float)? = null,
        leading: (@Composable () -> Unit)? = null,
        key: Any = icon to title,
    ) {
        rows +=
            MenuRowSpec.Action(
                key = key,
                title = title,
                icon = icon,
                onClick = onClick,
                supporting = supporting,
                tone = tone,
                enabled = enabled,
                navigates = navigates,
                iconRotation = iconRotation,
                leading = leading,
            )
    }

    fun toggle(
        title: String,
        @DrawableRes icon: Int,
        checked: Boolean,
        onCheckedChange: (Boolean) -> Unit,
        supporting: String? = null,
        enabled: Boolean = true,
        key: Any = icon to title,
    ) {
        rows +=
            MenuRowSpec.Toggle(
                key = key,
                title = title,
                icon = icon,
                checked = checked,
                onCheckedChange = onCheckedChange,
                supporting = supporting,
                enabled = enabled,
            )
    }
}

@Composable
fun MenuSectionScope.downloadAction(
    state: Int?,
    onDownload: () -> Unit,
    onRemove: () -> Unit,
    onCancel: () -> Unit = onRemove,
) {
    val leading: @Composable () -> Unit = { MenuDownloadLeading(state) }
    when (state) {
        Download.STATE_COMPLETED ->
            action(
                title = stringResource(R.string.remove_download),
                icon = R.drawable.offline,
                onClick = onRemove,
                supporting = stringResource(R.string.menu_download_available_offline),
                tone = MenuRowTone.Destructive,
                leading = leading,
                key = MenuDownloadRowKey,
            )

        Download.STATE_QUEUED, Download.STATE_DOWNLOADING ->
            action(
                title = stringResource(R.string.downloading),
                icon = R.drawable.download,
                onClick = onCancel,
                supporting = stringResource(R.string.menu_download_tap_to_cancel),
                leading = leading,
                key = MenuDownloadRowKey,
            )

        else ->
            action(
                title = stringResource(R.string.action_download),
                icon = R.drawable.download,
                onClick = onDownload,
                leading = leading,
                key = MenuDownloadRowKey,
            )
    }
}

private const val MenuDownloadRowKey = "menu_download_row"

@Composable
private fun MenuDownloadLeading(state: Int?) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val phase =
        when (state) {
            Download.STATE_COMPLETED -> 2
            Download.STATE_QUEUED, Download.STATE_DOWNLOADING -> 1
            else -> 0
        }

    AnimatedContent(
        targetState = phase,
        transitionSpec = {
            if (animationsDisabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                (fadeIn(motion.defaultEffectsSpec()) + scaleIn(motion.fastSpatialSpec(), initialScale = 0.6f)) togetherWith
                    fadeOut(motion.fastEffectsSpec())
            }
        },
        label = "menuDownloadLeading",
    ) { targetPhase ->
        when (targetPhase) {
            1 ->
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(RowIconContainerSize),
                ) {
                    CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                }

            2 -> MenuRowIcon(icon = R.drawable.offline, tone = MenuRowTone.Destructive)
            else -> MenuRowIcon(icon = R.drawable.download)
        }
    }
}

@Composable
fun MenuSection(
    title: String?,
    modifier: Modifier = Modifier,
    content: @Composable MenuSectionScope.() -> Unit,
) {
    val scope = MenuSectionScope().apply { content() }
    if (scope.rows.isEmpty()) return

    val motion = MaterialTheme.motionScheme
    val rows = scope.rows

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .menuEntrance(),
    ) {
        if (!title.isNullOrBlank()) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier =
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp)
                        .semantics { heading() },
            )
        }

        Column(
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            modifier = Modifier.animateContentSize(motion.defaultSpatialSpec()),
        ) {
            rows.forEachIndexed { index, row ->
                key(row.key) {
                    when (row) {
                        is MenuRowSpec.Action -> MenuActionRow(row = row, index = index, count = rows.size)
                        is MenuRowSpec.Toggle -> MenuToggleRow(row = row, index = index, count = rows.size)
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuActionRow(
    row: MenuRowSpec.Action,
    index: Int,
    count: Int,
) {
    val supporting = row.supporting
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl

    SegmentedListItem(
        onClick = row.onClick,
        enabled = row.enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        leadingContent = {
            val leading = row.leading
            if (leading != null) {
                leading()
            } else {
                MenuRowIcon(icon = row.icon, tone = row.tone, rotation = row.iconRotation)
            }
        },
        supportingContent =
            supporting?.let {
                {
                    Text(
                        text = it,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        trailingContent =
            if (row.navigates) {
                {
                    Icon(
                        painter = painterResource(R.drawable.navigate_next),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.graphicsLayer { scaleX = if (isRtl) -1f else 1f },
                    )
                }
            } else {
                null
            },
        content = {
            Text(
                text = row.title,
                color =
                    if (row.tone == MenuRowTone.Destructive) {
                        MaterialTheme.colorScheme.error
                    } else {
                        Color.Unspecified
                    },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
private fun MenuToggleRow(
    row: MenuRowSpec.Toggle,
    index: Int,
    count: Int,
) {
    val supporting = row.supporting

    SegmentedListItem(
        checked = row.checked,
        onCheckedChange = row.onCheckedChange,
        enabled = row.enabled,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 56.dp),
        leadingContent = {
            MenuRowIcon(
                icon = row.icon,
                tone = if (row.checked) MenuRowTone.Active else MenuRowTone.Neutral,
            )
        },
        supportingContent =
            supporting?.let {
                {
                    Text(
                        text = it,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        trailingContent = {
            Switch(
                checked = row.checked,
                onCheckedChange = null,
                enabled = row.enabled,
            )
        },
        content = {
            Text(
                text = row.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

@Composable
fun MenuRowIcon(
    @DrawableRes icon: Int,
    modifier: Modifier = Modifier,
    tone: MenuRowTone = MenuRowTone.Neutral,
    rotation: (() -> Float)? = null,
) {
    val motion = MaterialTheme.motionScheme
    val colors = MaterialTheme.colorScheme
    val containerColor by animateColorAsState(
        targetValue =
            when (tone) {
                MenuRowTone.Neutral -> colors.surfaceContainerHighest
                MenuRowTone.Active -> colors.secondaryContainer
                MenuRowTone.Destructive -> colors.errorContainer
            },
        animationSpec = motion.defaultEffectsSpec(),
        label = "menuRowIconContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            when (tone) {
                MenuRowTone.Neutral -> colors.onSurfaceVariant
                MenuRowTone.Active -> colors.onSecondaryContainer
                MenuRowTone.Destructive -> colors.onErrorContainer
            },
        animationSpec = motion.defaultEffectsSpec(),
        label = "menuRowIconContent",
    )
    val corner by animateDpAsState(
        targetValue = if (tone == MenuRowTone.Active) RowIconContainerSize / 2 else 12.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "menuRowIconCorner",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(RowIconContainerSize)
                .graphicsLayer {
                    shape = RoundedCornerShape(corner.coerceAtLeast(0.dp))
                    clip = true
                }.background(containerColor),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = contentColor,
            modifier =
                Modifier
                    .size(22.dp)
                    .graphicsLayer { rotationZ = rotation?.invoke() ?: 0f },
        )
    }
}
