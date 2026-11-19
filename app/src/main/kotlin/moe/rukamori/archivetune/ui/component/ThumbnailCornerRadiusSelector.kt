/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableHapticFeedbackKey
import moe.rukamori.archivetune.constants.PlayerHorizontalPadding
import moe.rukamori.archivetune.constants.ThumbnailCornerRadiusKey
import moe.rukamori.archivetune.utils.rememberPreference
import kotlin.math.roundToInt

private const val CornerRadiusMin = 0
private const val CornerRadiusMax = 45
private const val CornerRadiusDefault = 16f
private val CornerRadiusPresets = listOf(0, 8, 16, 24, 32, 40)
private const val PreviewWidthFraction = 0.6f
private val PreviewMaxSize = 224.dp
private const val BackdropScale = 1.2f
private const val BackdropMaxRotation = 60f
private val HeaderBadgeSize = 56.dp
private const val PresetGlyphMaxFontScale = 1.3f
private const val TabularFigures = "tnum"

@Composable
fun ThumbnailCornerRadiusSelectorButton(
    modifier: Modifier = Modifier,
    onRadiusSelected: (Float) -> Unit,
) {
    val (thumbnailCornerRadius, onThumbnailCornerRadiusChange) =
        rememberPreference(
            ThumbnailCornerRadiusKey,
            defaultValue = CornerRadiusDefault,
        )

    var showSheet by rememberSaveable { mutableStateOf(false) }

    PreferenceEntry(
        modifier = modifier,
        title = { Text(text = stringResource(id = R.string.custom_radius)) },
        description = stringResource(id = R.string.corner_radius, thumbnailCornerRadius.roundToInt()),
        icon = {
            Icon(
                painter = painterResource(id = R.drawable.image),
                contentDescription = null,
            )
        },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        onClick = { showSheet = true },
    )

    if (showSheet) {
        ThumbnailCornerRadiusBottomSheet(
            savedRadius = thumbnailCornerRadius,
            onDismiss = { showSheet = false },
            onApply = { newRadius ->
                onThumbnailCornerRadiusChange(newRadius)
                onRadiusSelected(newRadius)
            },
        )
    }
}

@Composable
private fun ThumbnailCornerRadiusBottomSheet(
    savedRadius: Float,
    onDismiss: () -> Unit,
    onApply: (Float) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val (enableHapticFeedback) = rememberPreference(EnableHapticFeedbackKey, true)
    val savedValue = savedRadius.roundToInt().coerceIn(CornerRadiusMin, CornerRadiusMax)
    var draftRadius by rememberSaveable { mutableIntStateOf(savedValue) }
    var applying by remember { mutableStateOf(false) }
    val latestOnDismiss by rememberUpdatedState(onDismiss)

    val sliderInteraction = remember { MutableInteractionSource() }
    val sliderDragged by sliderInteraction.collectIsDraggedAsState()
    val sliderPressed by sliderInteraction.collectIsPressedAsState()
    val animatedRadius by animateFloatAsState(
        targetValue = draftRadius.toFloat(),
        animationSpec = if (sliderDragged || sliderPressed) snap() else motion.defaultSpatialSpec(),
        label = "thumbnailCornerRadiusPreview",
    )

    fun feedback(type: HapticFeedbackType) {
        if (enableHapticFeedback) haptic.performHapticFeedback(type)
    }

    fun updateRadius(target: Int) {
        val clamped = target.coerceIn(CornerRadiusMin, CornerRadiusMax)
        if (clamped == draftRadius) return
        val milestone = clamped in CornerRadiusPresets || clamped == CornerRadiusMin || clamped == CornerRadiusMax
        feedback(if (milestone) HapticFeedbackType.SegmentTick else HapticFeedbackType.SegmentFrequentTick)
        draftRadius = clamped
    }

    fun hideSheet() {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            if (!sheetState.isVisible) latestOnDismiss()
        }
    }

    BackAwareModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = colors.surfaceContainerLow,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(top = 4.dp, bottom = 24.dp),
        ) {
            CornerRadiusSheetHeader(animatedRadius = animatedRadius)

            Spacer(Modifier.height(20.dp))

            CornerRadiusPreviewStage(
                radius = draftRadius,
                animatedRadius = animatedRadius,
            )

            Spacer(Modifier.height(20.dp))

            CornerRadiusValueControls(
                radius = draftRadius,
                isModified = draftRadius != savedValue,
                interactionSource = sliderInteraction,
                onRadiusChange = ::updateRadius,
                onRevert = {
                    feedback(HapticFeedbackType.ContextClick)
                    draftRadius = savedValue
                },
            )

            Spacer(Modifier.height(24.dp))

            CornerRadiusPresetGroup(
                radius = draftRadius,
                onSelect = ::updateRadius,
            )

            Spacer(Modifier.height(28.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Button(
                    onClick = ::hideSheet,
                    shapes = ButtonDefaults.shapes(),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = colors.surfaceContainerHigh,
                            contentColor = colors.primary,
                        ),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                ) {
                    Text(
                        text = stringResource(R.string.cancel_button),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Button(
                    onClick = {
                        if (!applying) {
                            applying = true
                            feedback(HapticFeedbackType.Confirm)
                            onApply(draftRadius.toFloat())
                            hideSheet()
                        }
                    },
                    shapes = ButtonDefaults.shapes(),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.check),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.ok_button),
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerRadiusSheetHeader(
    animatedRadius: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val badgeShape = RoundedCornerShape(HeaderBadgeSize / 2 * (animatedRadius / CornerRadiusMax))

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.appearance).uppercase(locale),
                style = MaterialTheme.typography.labelMedium.copy(letterSpacing = 1.5.sp),
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.custom_radius),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = colors.onSurface,
                modifier = Modifier.semantics { heading() },
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.thumbnail_corner_radius_supporting),
                style = MaterialTheme.typography.bodyMedium,
                color = colors.onSurfaceVariant,
            )
        }
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(HeaderBadgeSize)
                    .background(colors.primaryContainer, badgeShape),
        ) {
            Icon(
                painter = painterResource(R.drawable.image),
                contentDescription = null,
                tint = colors.onPrimaryContainer,
            )
        }
    }
}

@Composable
private fun CornerRadiusPreviewStage(
    radius: Int,
    animatedRadius: Float,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val motion = MaterialTheme.motionScheme
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val backdropRotation by animateFloatAsState(
        targetValue = radius / CornerRadiusMax.toFloat() * BackdropMaxRotation,
        animationSpec = motion.slowSpatialSpec(),
        label = "thumbnailCornerRadiusBackdrop",
    )
    val previewDescription = stringResource(R.string.thumbnail_corner_radius_preview_description, radius)

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.extraLarge)
                .background(colors.surfaceContainer),
    ) {
        BoxWithConstraints(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 36.dp),
        ) {
            val previewSize = min(maxWidth * PreviewWidthFraction, PreviewMaxSize)
            val playerArtworkSize = maxOf(screenWidth - PlayerHorizontalPadding * 2, previewSize)
            val previewRadius: Dp = (animatedRadius * (previewSize / playerArtworkSize)).dp
            val artworkShape = RoundedCornerShape(previewRadius)

            Box(
                modifier =
                    Modifier
                        .size(previewSize * BackdropScale)
                        .graphicsLayer { rotationZ = backdropRotation }
                        .background(colors.primaryContainer.copy(alpha = 0.6f), MaterialShapes.Cookie12Sided.toShape()),
            )

            Image(
                painter = painterResource(R.drawable.ic_music_placeholder),
                contentDescription = previewDescription,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(previewSize)
                        .shadow(elevation = 12.dp, shape = artworkShape)
                        .clip(artworkShape)
                        .background(colors.surfaceVariant),
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier =
                Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(colors.surfaceContainerHighest, CircleShape)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.music_note),
                contentDescription = null,
                tint = colors.primary,
                modifier = Modifier.size(16.dp),
            )
            Text(
                text = stringResource(R.string.player),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun CornerRadiusValueControls(
    radius: Int,
    isModified: Boolean,
    interactionSource: MutableInteractionSource,
    onRadiusChange: (Int) -> Unit,
    onRevert: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val valueLabel = stringResource(R.string.thumbnail_corner_radius_value, radius)
    val sliderLabel = stringResource(R.string.adjust_radius)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 40.dp),
        ) {
            Text(
                text = sliderLabel,
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = isModified,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                TextButton(
                    onClick = onRevert,
                    shapes = ButtonDefaults.shapes(),
                    contentPadding = ButtonDefaults.TextButtonWithIconContentPadding,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.replay),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(text = stringResource(R.string.reset))
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            FilledTonalIconButton(
                onClick = { onRadiusChange(radius - 1) },
                enabled = radius > CornerRadiusMin,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.remove),
                    contentDescription = stringResource(R.string.thumbnail_corner_radius_decrease),
                )
            }

            AnimatedContent(
                targetState = radius,
                transitionSpec = {
                    val rising = targetState > initialState
                    (slideInVertically { height -> if (rising) height else -height } + fadeIn()) togetherWith
                        (slideOutVertically { height -> if (rising) -height else height } + fadeOut()) using
                        SizeTransform(clip = false)
                },
                contentAlignment = Alignment.Center,
                label = "thumbnailCornerRadiusReadout",
                modifier = Modifier.weight(1f),
            ) { value ->
                Text(
                    text = stringResource(R.string.thumbnail_corner_radius_value, value),
                    style =
                        MaterialTheme.typography.displaySmall.copy(
                            fontFeatureSettings = TabularFigures,
                        ),
                    fontWeight = FontWeight.SemiBold,
                    color = colors.primary,
                    maxLines = 1,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            }

            FilledTonalIconButton(
                onClick = { onRadiusChange(radius + 1) },
                enabled = radius < CornerRadiusMax,
                shapes = IconButtonDefaults.shapes(),
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = stringResource(R.string.thumbnail_corner_radius_increase),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Slider(
            value = radius.toFloat(),
            onValueChange = { onRadiusChange(it.roundToInt()) },
            valueRange = CornerRadiusMin.toFloat()..CornerRadiusMax.toFloat(),
            interactionSource = interactionSource,
            colors =
                SliderDefaults.colors(
                    inactiveTrackColor = colors.surfaceContainerHighest,
                ),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = sliderLabel
                        stateDescription = valueLabel
                    },
        )

        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.thumbnail_corner_radius_value, CornerRadiusMin),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = stringResource(R.string.thumbnail_corner_radius_value, CornerRadiusMax),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CornerRadiusPresetGroup(
    radius: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalConfiguration.current.locales[0]
    val showGlyph = LocalDensity.current.fontScale <= PresetGlyphMaxFontScale
    val isCustom = radius !in CornerRadiusPresets

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .heightIn(min = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.thumbnail_corner_radius_presets),
                style = MaterialTheme.typography.titleSmall,
                color = colors.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            AnimatedVisibility(
                visible = isCustom,
                enter = fadeIn() + scaleIn(initialScale = 0.8f),
                exit = fadeOut() + scaleOut(targetScale = 0.8f),
            ) {
                Text(
                    text = stringResource(R.string.custom),
                    style = MaterialTheme.typography.labelMedium,
                    color = colors.onTertiaryContainer,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .background(colors.tertiaryContainer, CircleShape)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .selectableGroup(),
        ) {
            CornerRadiusPresets.forEachIndexed { index, preset ->
                val presetLabel = stringResource(R.string.thumbnail_corner_radius_value, preset)
                ToggleButton(
                    checked = radius == preset,
                    onCheckedChange = { onSelect(preset) },
                    shapes =
                        when (index) {
                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                            CornerRadiusPresets.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                        },
                    contentPadding = PaddingValues(horizontal = 4.dp),
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 52.dp)
                            .semantics {
                                role = Role.RadioButton
                                contentDescription = presetLabel
                            },
                ) {
                    if (showGlyph) {
                        CornerGlyph(
                            fraction = preset / CornerRadiusMax.toFloat(),
                            color = LocalContentColor.current,
                        )
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = String.format(locale, "%d", preset),
                        style =
                            MaterialTheme.typography.labelLarge.copy(
                                fontFeatureSettings = TabularFigures,
                            ),
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
private fun CornerGlyph(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier.size(12.dp)) {
        val strokeWidth = 2.dp.toPx()
        val inset = strokeWidth / 2f
        val extent = size.minDimension - strokeWidth
        val cornerRadius = extent * fraction.coerceIn(0f, 1f)
        val path =
            Path().apply {
                moveTo(inset, inset + extent)
                lineTo(inset, inset + cornerRadius)
                if (cornerRadius > 0f) {
                    arcTo(
                        rect = Rect(inset, inset, inset + cornerRadius * 2f, inset + cornerRadius * 2f),
                        startAngleDegrees = 180f,
                        sweepAngleDegrees = 90f,
                        forceMoveTo = false,
                    )
                }
                lineTo(inset + extent, inset)
            }
        drawPath(
            path = path,
            color = color,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
