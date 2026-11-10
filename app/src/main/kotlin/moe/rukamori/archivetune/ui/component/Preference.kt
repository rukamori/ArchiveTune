/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.ListItemShapes
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SheetState
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HISTORY_DURATION_DEFAULT
import moe.rukamori.archivetune.constants.HISTORY_DURATION_RANGE
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import java.util.Locale
import kotlin.math.roundToInt

val LocalPreferenceInGroup = compositionLocalOf { false }

enum class PreferenceGroupPosition { Single, First, Middle, Last }

val LocalPreferenceGroupPosition = compositionLocalOf<PreferenceGroupPosition?> { null }

private val PreferenceGroupLargeCorner = 28.dp
private val PreferenceGroupSmallCorner = 6.dp
private val PreferenceItemPressedCorner = 20.dp
private val PreferenceGroupHorizontalPadding = 30.dp
private val PreferenceItemContentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)
private val PreferenceIconBadgeSize = 40.dp
private const val PreferenceIconBadgeActiveRotation = 40f
private const val PreferenceDisabledAlpha = 0.38f

private fun preferenceItemShapeForPosition(position: PreferenceGroupPosition?): Shape {
    val large = PreferenceGroupLargeCorner
    val small = PreferenceGroupSmallCorner
    return when (position) {
        null,
        PreferenceGroupPosition.Single,
        -> RoundedCornerShape(large)

        PreferenceGroupPosition.First -> {
            RoundedCornerShape(topStart = large, topEnd = large, bottomEnd = small, bottomStart = small)
        }

        PreferenceGroupPosition.Middle -> {
            RoundedCornerShape(small)
        }

        PreferenceGroupPosition.Last -> {
            RoundedCornerShape(topStart = small, topEnd = small, bottomEnd = large, bottomStart = large)
        }
    }
}

@Composable
private fun preferenceItemShapes(
    position: PreferenceGroupPosition?,
    customShape: Shape?,
): ListItemShapes {
    if (customShape != null) {
        return ListItemDefaults.shapes(shape = customShape, selectedShape = customShape)
    }
    val restingShape = preferenceItemShapeForPosition(position)
    return ListItemDefaults.shapes(
        shape = restingShape,
        selectedShape = restingShape,
        pressedShape = RoundedCornerShape(PreferenceItemPressedCorner),
    )
}

@Composable
private fun preferenceItemColors(): ListItemColors {
    val colorScheme = MaterialTheme.colorScheme
    return ListItemDefaults.segmentedColors(
        containerColor = colorScheme.surfaceContainerLow,
        contentColor = colorScheme.onSurface,
        supportingContentColor = colorScheme.onSurfaceVariant,
        trailingContentColor = colorScheme.onSurfaceVariant,
        disabledContainerColor = colorScheme.surfaceContainerLow,
        selectedContainerColor = colorScheme.surfaceContainerLow,
        selectedContentColor = colorScheme.onSurface,
        selectedSupportingContentColor = colorScheme.onSurfaceVariant,
        selectedTrailingContentColor = colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun preferenceSheetOptionColors(): ListItemColors =
    ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )

@Composable
fun PreferenceEntry(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    content: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    isEnabled: Boolean = true,
    shape: Shape? = null,
) {
    PreferenceListItem(
        modifier = modifier,
        title = title,
        description = description,
        content = content,
        icon = icon,
        trailingContent = trailingContent,
        onClick = onClick,
        checked = null,
        onCheckedChange = null,
        isEnabled = isEnabled,
        shape = shape,
    )
}

@Composable
private fun PreferenceListItem(
    modifier: Modifier,
    title: @Composable () -> Unit,
    description: String?,
    content: (@Composable () -> Unit)?,
    icon: (@Composable () -> Unit)?,
    trailingContent: (@Composable () -> Unit)?,
    onClick: (() -> Unit)?,
    checked: Boolean?,
    onCheckedChange: ((Boolean) -> Unit)?,
    isEnabled: Boolean,
    shape: Shape?,
) {
    val inGroup = LocalPreferenceInGroup.current
    val groupPosition = LocalPreferenceGroupPosition.current
    val shapes = preferenceItemShapes(groupPosition, shape)
    val colors = preferenceItemColors()
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isChecked = checked == true

    val itemModifier =
        modifier
            .fillMaxWidth()
            .padding(
                horizontal = if (inGroup) 0.dp else 16.dp,
                vertical = if (inGroup) 0.dp else 3.dp,
            )

    val leadingContent: (@Composable () -> Unit)? =
        icon?.let { iconContent ->
            {
                PreferenceIconBadge(
                    active = isPressed || isChecked,
                    highlighted = isChecked,
                    enabled = isEnabled,
                    icon = iconContent,
                )
            }
        }

    val supportingContent: (@Composable () -> Unit)? =
        if (description != null || content != null) {
            {
                Column {
                    if (description != null) {
                        Text(
                            text = description,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    content?.invoke()
                }
            }
        } else {
            null
        }

    val headline: @Composable () -> Unit = {
        ProvideTextStyle(MaterialTheme.typography.titleMedium) {
            title()
        }
    }

    when {
        checked != null && onCheckedChange != null -> {
            SegmentedListItem(
                checked = checked,
                onCheckedChange = onCheckedChange,
                shapes = shapes,
                modifier = itemModifier,
                enabled = isEnabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                colors = colors,
                contentPadding = PreferenceItemContentPadding,
                interactionSource = interactionSource,
                content = headline,
            )
        }

        onClick != null -> {
            SegmentedListItem(
                onClick = onClick,
                shapes = shapes,
                modifier = itemModifier,
                enabled = isEnabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                colors = colors,
                contentPadding = PreferenceItemContentPadding,
                interactionSource = interactionSource,
                content = headline,
            )
        }

        else -> {
            SegmentedListItem(
                shapes = shapes,
                modifier = itemModifier,
                enabled = isEnabled,
                leadingContent = leadingContent,
                trailingContent = trailingContent,
                supportingContent = supportingContent,
                colors = colors,
                contentPadding = PreferenceItemContentPadding,
                content = headline,
            )
        }
    }
}

@Composable
private fun PreferenceIconBadge(
    active: Boolean,
    highlighted: Boolean,
    enabled: Boolean,
    icon: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val colorScheme = MaterialTheme.colorScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else motion.fastSpatialSpec(),
        label = "preferenceIconMorph",
    )
    val containerColor by animateColorAsState(
        targetValue = if (highlighted) colorScheme.primaryContainer else colorScheme.secondaryContainer,
        animationSpec = if (animationsDisabled) snap() else motion.defaultEffectsSpec(),
        label = "preferenceIconContainer",
    )
    val contentColor by animateColorAsState(
        targetValue = if (highlighted) colorScheme.onPrimaryContainer else colorScheme.onSecondaryContainer,
        animationSpec = if (animationsDisabled) snap() else motion.defaultEffectsSpec(),
        label = "preferenceIconContent",
    )

    Box(
        modifier =
            Modifier
                .size(PreferenceIconBadgeSize)
                .alpha(if (enabled) 1f else PreferenceDisabledAlpha),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val value = progress.coerceIn(0f, 1f)
                        rotationZ = progress * PreferenceIconBadgeActiveRotation
                        shape = EditorialMorphShape(morph, value)
                        clip = true
                    }.background(containerColor),
        )
        CompositionLocalProvider(LocalContentColor provides contentColor) {
            icon()
        }
    }
}

@Composable
private fun PreferenceValueChip(
    text: String,
    isEnabled: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    Box(
        modifier =
            Modifier
                .alpha(if (isEnabled) 1f else PreferenceDisabledAlpha)
                .clip(RoundedCornerShape(50))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        AnimatedContent(
            targetState = text,
            transitionSpec = {
                if (animationsDisabled) {
                    EnterTransition.None togetherWith ExitTransition.None
                } else {
                    (
                        slideInVertically(motion.fastSpatialSpec()) { it / 2 } +
                            fadeIn(motion.fastEffectsSpec())
                    ) togetherWith (
                        slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } +
                            fadeOut(motion.fastEffectsSpec())
                    ) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                }
            },
            label = "preferenceValueChip",
        ) { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelLargeEmphasized,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PreferenceChevron() {
    Icon(
        painter = painterResource(R.drawable.arrow_forward),
        contentDescription = null,
        modifier = Modifier.size(20.dp),
    )
}

@Composable
private fun PreferenceDialogValue(
    value: Int,
    text: @Composable (Int) -> String,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    AnimatedContent(
        targetState = value,
        transitionSpec = {
            if (animationsDisabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                val direction = if (targetState >= initialState) 1 else -1
                (
                    slideInVertically(motion.fastSpatialSpec()) { height -> direction * height / 2 } +
                        fadeIn(motion.fastEffectsSpec()) +
                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.85f)
                ) togetherWith (
                    slideOutVertically(motion.fastSpatialSpec()) { height -> -direction * height / 2 } +
                        fadeOut(motion.fastEffectsSpec()) +
                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f)
                ) using SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
            }
        },
        contentAlignment = Alignment.Center,
        label = "preferenceDialogValue",
    ) { targetValue ->
        Text(
            text = text(targetValue),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> SegmentedPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        isEnabled = isEnabled,
        content = {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            ) {
                values.forEachIndexed { index, value ->
                    ToggleButton(
                        checked = value == selectedValue,
                        onCheckedChange = { onValueSelected(value) },
                        enabled = isEnabled,
                        shapes =
                            when (index) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                values.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        modifier =
                            Modifier
                                .weight(1f)
                                .semantics { role = Role.RadioButton },
                    ) {
                        Text(
                            text = valueText(value),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
    )
}

@Composable
inline fun <reified T : Enum<T>> EnumSegmentedPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    description: String? = null,
    noinline icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    val values = remember { enumValues<T>().toList() }

    SegmentedPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    isValueEnabled: (T) -> Boolean = { true },
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    var showBottomSheet by remember {
        mutableStateOf(false)
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()

    ListPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        valueDescription = valueDescription,
        isValueEnabled = isValueEnabled,
        showBottomSheet = showBottomSheet,
        sheetState = sheetState,
        onOpen = { showBottomSheet = true },
        onDismiss = { showBottomSheet = false },
        onValueSelected = { value ->
            onValueSelected(value)
            coroutineScope
                .launch {
                    sheetState.hide()
                }.invokeOnCompletion {
                    showBottomSheet = false
                }
        },
        isEnabled = isEnabled,
    )
}

@Composable
fun <T> ListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    selectedValue: T,
    values: List<T>,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    isValueEnabled: (T) -> Boolean = { true },
    showBottomSheet: Boolean,
    sheetState: SheetState,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    if (showBottomSheet) {
        PreferenceSelectionBottomSheet(
            title = title,
            values = values,
            selectedValue = selectedValue,
            valueText = valueText,
            valueDescription = valueDescription,
            isValueEnabled = isValueEnabled,
            sheetState = sheetState,
            onDismiss = onDismiss,
            onValueSelected = onValueSelected,
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        content = {
            Spacer(Modifier.height(8.dp))
            PreferenceValueChip(text = valueText(selectedValue), isEnabled = isEnabled)
        },
        trailingContent = { PreferenceChevron() },
        onClick = onOpen,
        isEnabled = isEnabled,
    )
}

@Composable
fun <T : Any> MultiSelectListPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    values: ImmutableList<T>,
    checkedValues: ImmutableList<T>,
    selectionText: String,
    valueText: @Composable (T) -> String,
    isBottomSheetVisible: Boolean,
    onOpen: () -> Unit,
    onDismiss: () -> Unit,
    onValueCheckedChange: (T, Boolean) -> Unit,
    onConfirm: () -> Unit,
    isEnabled: Boolean = true,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val coroutineScope = rememberCoroutineScope()
    val hideSheet: (() -> Unit) -> Unit =
        remember(coroutineScope, sheetState) {
            { completion ->
                coroutineScope
                    .launch {
                        sheetState.hide()
                        if (!sheetState.isVisible) completion()
                    }
            }
        }

    if (isBottomSheetVisible) {
        MultiSelectPreferenceBottomSheet(
            title = title,
            values = values,
            checkedValues = checkedValues,
            valueText = valueText,
            sheetState = sheetState,
            onDismiss = onDismiss,
            onValueCheckedChange = onValueCheckedChange,
            onCancel = { hideSheet(onDismiss) },
            onConfirm = { hideSheet(onConfirm) },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        content = {
            Spacer(Modifier.height(8.dp))
            PreferenceValueChip(text = selectionText, isEnabled = isEnabled)
        },
        trailingContent = { PreferenceChevron() },
        onClick = onOpen,
        isEnabled = isEnabled,
    )
}

@Composable
private fun PreferenceSheetScaffold(
    title: @Composable () -> Unit,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    BackAwareModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 8.dp, end = 8.dp, top = 4.dp, bottom = 20.dp)
                        .semantics { heading() },
            ) {
                ProvideTextStyle(MaterialTheme.typography.headlineSmallEmphasized) {
                    title()
                }
            }

            Box(modifier = Modifier.weight(1f, fill = false)) {
                content()
            }

            if (actions != null) {
                Spacer(Modifier.height(16.dp))
                actions()
            }
        }
    }
}

@Composable
private fun <T : Any> MultiSelectPreferenceBottomSheet(
    title: @Composable () -> Unit,
    values: ImmutableList<T>,
    checkedValues: ImmutableList<T>,
    valueText: @Composable (T) -> String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onValueCheckedChange: (T, Boolean) -> Unit,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    val optionColors = preferenceSheetOptionColors()

    PreferenceSheetScaffold(
        title = title,
        sheetState = sheetState,
        onDismiss = onDismiss,
        actions = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onCancel) {
                    Text(stringResource(android.R.string.cancel))
                }
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onConfirm,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        },
    ) {
        LazyColumn(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            itemsIndexed(
                items = values,
                key = { index, value -> preferenceOptionKey(index, value) },
                contentType = { _, _ -> "multi_select_preference_option" },
            ) { index, value ->
                val checked = value in checkedValues
                SegmentedListItem(
                    checked = checked,
                    onCheckedChange = { isChecked -> onValueCheckedChange(value, isChecked) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = values.size),
                    trailingContent = {
                        Checkbox(
                            checked = checked,
                            onCheckedChange = null,
                        )
                    },
                    colors = optionColors,
                ) {
                    Text(
                        text = valueText(value),
                        style =
                            if (checked) {
                                MaterialTheme.typography.titleMediumEmphasized
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
inline fun <reified T : Enum<T>> EnumListPreference(
    modifier: Modifier = Modifier,
    noinline title: @Composable () -> Unit,
    description: String? = null,
    noinline icon: (@Composable () -> Unit)?,
    selectedValue: T,
    noinline valueText: @Composable (T) -> String,
    noinline onValueSelected: (T) -> Unit,
    isEnabled: Boolean = true,
) {
    val values = remember { enumValues<T>().toList() }

    ListPreference(
        modifier = modifier,
        title = title,
        description = description,
        icon = icon,
        selectedValue = selectedValue,
        values = values,
        valueText = valueText,
        onValueSelected = onValueSelected,
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PreferenceSelectionBottomSheet(
    title: @Composable () -> Unit,
    values: List<T>,
    selectedValue: T,
    valueText: @Composable (T) -> String,
    valueDescription: (@Composable (T) -> String)? = null,
    isValueEnabled: (T) -> Boolean,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onValueSelected: (T) -> Unit,
) {
    val optionColors = preferenceSheetOptionColors()
    val listState =
        rememberLazyListState(
            initialFirstVisibleItemIndex = values.indexOf(selectedValue).coerceAtLeast(0),
        )

    PreferenceSheetScaffold(
        title = title,
        sheetState = sheetState,
        onDismiss = onDismiss,
    ) {
        LazyColumn(
            state = listState,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 520.dp)
                    .selectableGroup(),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
            contentPadding = PaddingValues(bottom = 8.dp),
        ) {
            itemsIndexed(
                items = values,
                key = { index, value -> preferenceOptionKey(index, value) },
                contentType = { _, _ -> "preference_option" },
            ) { index, value ->
                val selected = value == selectedValue
                val enabled = isValueEnabled(value)
                val description = valueDescription?.invoke(value)
                SegmentedListItem(
                    selected = selected,
                    onClick = { onValueSelected(value) },
                    shapes = ListItemDefaults.segmentedShapes(index = index, count = values.size),
                    enabled = enabled,
                    leadingContent = {
                        RadioButton(
                            selected = selected,
                            onClick = null,
                            enabled = enabled,
                        )
                    },
                    supportingContent =
                        description?.let { text ->
                            {
                                Text(
                                    text = text,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        },
                    colors = optionColors,
                ) {
                    Text(
                        text = valueText(value),
                        style =
                            if (selected) {
                                MaterialTheme.typography.titleMediumEmphasized
                            } else {
                                MaterialTheme.typography.titleMedium
                            },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

private fun preferenceOptionKey(
    index: Int,
    value: Any?,
): String {
    val valueKey =
        when (value) {
            is Enum<*> -> value.name
            null -> "null"
            else -> value.hashCode().toString()
        }
    return "${value?.javaClass?.name.orEmpty()}:$valueKey:$index"
}

@Composable
fun SwitchPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    description: String? = null,
    icon: (@Composable () -> Unit)? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isEnabled: Boolean = true,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    PreferenceListItem(
        modifier = modifier,
        title = title,
        description = description,
        content = null,
        icon = icon,
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = null,
                enabled = isEnabled,
                thumbContent = {
                    AnimatedContent(
                        targetState = checked,
                        transitionSpec = {
                            if (animationsDisabled) {
                                EnterTransition.None togetherWith ExitTransition.None
                            } else {
                                (
                                    fadeIn(motion.fastEffectsSpec()) +
                                        scaleIn(motion.fastSpatialSpec(), initialScale = 0.4f)
                                ) togetherWith (
                                    fadeOut(motion.fastEffectsSpec()) +
                                        scaleOut(motion.fastSpatialSpec(), targetScale = 0.4f)
                                )
                            }
                        },
                        label = "switchThumbIcon",
                    ) { isChecked ->
                        Icon(
                            painter =
                                painterResource(
                                    id = if (isChecked) R.drawable.check else R.drawable.close,
                                ),
                            contentDescription = null,
                            modifier = Modifier.size(SwitchDefaults.IconSize),
                        )
                    }
                },
            )
        },
        onClick = null,
        checked = checked,
        onCheckedChange = onCheckedChange,
        isEnabled = isEnabled,
        shape = null,
    )
}

@Composable
fun EditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
    isInputValid: (String) -> Boolean = { it.isNotEmpty() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            title = title,
            initialTextFieldValue =
                TextFieldValue(
                    text = value,
                    selection = TextRange(value.length),
                ),
            singleLine = singleLine,
            keyboardOptions = keyboardOptions,
            isInputValid = isInputValid,
            onDone = onValueChange,
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        description = value,
        icon = icon,
        trailingContent = { PreferenceChevron() },
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun NumberEditTextPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    isInputValid: (String) -> Boolean = { it.toIntOrNull() != null },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    if (showDialog) {
        TextFieldDialog(
            title = title,
            initialTextFieldValue =
                TextFieldValue(
                    text = value.toString(),
                    selection = TextRange(value.toString().length),
                ),
            singleLine = true,
            keyboardOptions =
                KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
            isInputValid = isInputValid,
            onDone = { it.toIntOrNull()?.let(onValueChange) },
            onDismiss = { showDialog = false },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        icon = icon,
        content = {
            Spacer(Modifier.height(8.dp))
            PreferenceValueChip(text = value.toString(), isEnabled = isEnabled)
        },
        trailingContent = { PreferenceChevron() },
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember(value) {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.history_duration),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                showDialog = false
                onValueChange.invoke(sliderValue.roundToInt())
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = HISTORY_DURATION_DEFAULT.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PreferenceDialogValue(value = sliderValue.roundToInt()) { seconds ->
                        pluralStringResource(R.plurals.seconds, seconds, seconds)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.history_duration_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    val sliderState =
                        rememberControlledSliderState(
                            value = sliderValue,
                            valueRange = HISTORY_DURATION_RANGE,
                        )

                    Slider(
                        state = sliderState,
                        onValueChange = { value ->
                            sliderState.value = value
                            sliderValue = value
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        icon = icon,
        content = {
            Spacer(Modifier.height(8.dp))
            PreferenceValueChip(
                text = stringResource(R.string.history_duration_seconds, value),
                isEnabled = isEnabled,
            )
        },
        trailingContent = { PreferenceChevron() },
        onClick = { showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
private fun crossfadeDurationText(halfSeconds: Int): String {
    if (halfSeconds <= 0) return stringResource(R.string.dark_theme_off)
    val displayValue =
        if (halfSeconds % 2 == 0) {
            (halfSeconds / 2).toString()
        } else {
            String.format(Locale.getDefault(), "%.1f", halfSeconds / 2f)
        }
    return stringResource(R.string.audio_crossfade_seconds, displayValue)
}

private fun crossfadeHalfSeconds(seconds: Float): Int = (seconds * 2f).roundToInt().coerceIn(0, 20)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CrossfadeSliderPreference(
    modifier: Modifier = Modifier,
    valueSeconds: Float,
    onValueChange: (Float) -> Unit,
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(valueSeconds.coerceIn(0f, 10f))
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = stringResource(R.string.audio_crossfade_dialog_title),
                        overflow = TextOverflow.Ellipsis,
                        maxLines = 1,
                        style = MaterialTheme.typography.headlineSmall,
                    )
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded = crossfadeHalfSeconds(sliderValue) / 2f
                sliderValue = rounded
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = valueSeconds.coerceIn(0f, 10f)
                showDialog = false
            },
            onReset = {
                sliderValue = 5f
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PreferenceDialogValue(value = crossfadeHalfSeconds(sliderValue)) { halfSeconds ->
                        crossfadeDurationText(halfSeconds)
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = stringResource(R.string.audio_crossfade_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Spacer(Modifier.height(20.dp))

                    val crossfadeSliderState =
                        rememberControlledSliderState(
                            value = sliderValue,
                            steps = 19,
                            valueRange = 0f..10f,
                        )

                    Slider(
                        state = crossfadeSliderState,
                        onValueChange = { value ->
                            crossfadeSliderState.value = value
                            sliderValue = value.coerceIn(0f, 10f)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = { Text(stringResource(R.string.audio_crossfade_title)) },
        icon = { Icon(painterResource(R.drawable.graphic_eq), null) },
        content = {
            Spacer(Modifier.height(8.dp))
            PreferenceValueChip(
                text = crossfadeDurationText(crossfadeHalfSeconds(valueSeconds)),
                isEnabled = isEnabled,
            )
        },
        trailingContent = { PreferenceChevron() },
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

@Composable
fun NumberPickerPreference(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    icon: (@Composable () -> Unit)? = null,
    value: Int,
    onValueChange: (Int) -> Unit,
    minValue: Int = 0,
    maxValue: Int = 10,
    valueText: (Int) -> String = { it.toString() },
    isEnabled: Boolean = true,
) {
    var showDialog by remember {
        mutableStateOf(false)
    }

    var sliderValue by remember {
        mutableFloatStateOf(value.toFloat())
    }

    if (showDialog) {
        ActionPromptDialog(
            titleBar = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    title()
                }
            },
            onDismiss = { showDialog = false },
            onConfirm = {
                val rounded = sliderValue.roundToInt().coerceIn(minValue, maxValue)
                sliderValue = rounded.toFloat()
                showDialog = false
                onValueChange.invoke(rounded)
            },
            onCancel = {
                sliderValue = value.toFloat()
                showDialog = false
            },
            onReset = {
                sliderValue = minValue.toFloat()
            },
            content = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    PreferenceDialogValue(value = sliderValue.roundToInt().coerceIn(minValue, maxValue)) { number ->
                        valueText(number)
                    }

                    Spacer(Modifier.height(20.dp))

                    val pickerSliderState =
                        rememberControlledSliderState(
                            value = sliderValue,
                            steps = maxValue - minValue - 1,
                            valueRange = minValue.toFloat()..maxValue.toFloat(),
                        )

                    Slider(
                        state = pickerSliderState,
                        onValueChange = { value ->
                            pickerSliderState.value = value
                            sliderValue = value.coerceIn(minValue.toFloat(), maxValue.toFloat())
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
        )
    }

    PreferenceEntry(
        modifier = modifier,
        title = title,
        icon = icon,
        content = {
            Spacer(Modifier.height(8.dp))
            PreferenceValueChip(text = valueText(value), isEnabled = isEnabled)
        },
        trailingContent = { PreferenceChevron() },
        onClick = { if (isEnabled) showDialog = true },
        isEnabled = isEnabled,
    )
}

class PreferenceGroupScope internal constructor() {
    internal val items = mutableListOf<@Composable () -> Unit>()

    fun item(
        visible: Boolean = true,
        content: @Composable () -> Unit,
    ) {
        if (visible) {
            items.add(content)
        }
    }
}

@Composable
fun PreferenceGroup(
    modifier: Modifier = Modifier,
    title: String? = null,
    content: PreferenceGroupScope.() -> Unit,
) {
    val scope = PreferenceGroupScope().apply(content)
    val itemCount = scope.items.size

    if (itemCount == 0) return

    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    Column(modifier = modifier) {
        if (title != null) {
            PreferenceGroupTitle(
                title = title,
                modifier = Modifier.padding(horizontal = PreferenceGroupHorizontalPadding),
            )
        }

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .then(
                        if (animationsDisabled) {
                            Modifier
                        } else {
                            Modifier.animateContentSize(motion.defaultSpatialSpec())
                        },
                    ).padding(horizontal = PreferenceGroupHorizontalPadding),
            verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
        ) {
            scope.items.forEachIndexed { index, itemContent ->
                val position =
                    when {
                        itemCount == 1 -> PreferenceGroupPosition.Single
                        index == 0 -> PreferenceGroupPosition.First
                        index == itemCount - 1 -> PreferenceGroupPosition.Last
                        else -> PreferenceGroupPosition.Middle
                    }
                CompositionLocalProvider(
                    LocalPreferenceInGroup provides true,
                    LocalPreferenceGroupPosition provides position,
                ) {
                    itemContent()
                }
            }
        }
    }
}

@Composable
fun PreferenceGroupTitle(
    title: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmallEmphasized,
        color = MaterialTheme.colorScheme.primary,
        modifier =
            modifier
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .semantics { heading() },
    )
}
