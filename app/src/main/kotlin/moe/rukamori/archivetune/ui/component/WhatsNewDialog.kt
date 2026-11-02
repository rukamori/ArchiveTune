/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.graphics.shapes.RoundedPolygon
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.WhatsNewCategory
import moe.rukamori.archivetune.viewmodels.WhatsNewContent
import moe.rukamori.archivetune.viewmodels.WhatsNewState
import androidx.compose.ui.window.Dialog as PlatformDialog

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun WhatsNewDialog(
    state: WhatsNewState.Showing,
    disableAnimations: Boolean,
    onDismiss: () -> Unit,
    onReleaseNotes: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val version = state.version.removePrefix("v")
    val dialogTitle = stringResource(R.string.whats_new_title, version)
    val entryColors = ListItemDefaults.segmentedColors(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    )

    PlatformDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = !state.isDismissing,
            dismissOnClickOutside = !state.isDismissing,
            usePlatformDefaultWidth = false,
        ),
    ) {
        Surface(
            modifier = modifier
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .widthIn(max = 560.dp)
                .fillMaxWidth()
                .heightIn(max = 720.dp)
                .semantics { paneTitle = dialogTitle },
            shape = MaterialTheme.shapes.extraLarge,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column {
                key(state.version) {
                    val listState = rememberLazyListState()
                    val badgeRotation = remember { Animatable(if (disableAnimations) 0f else -120f) }
                    val badgeScale = remember { Animatable(if (disableAnimations) 1f else 0.6f) }
                    val spatialSpec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
                    LaunchedEffect(disableAnimations) {
                        if (disableAnimations) {
                            badgeRotation.snapTo(0f)
                            badgeScale.snapTo(1f)
                        } else {
                            coroutineScope {
                                launch { badgeRotation.animateTo(0f, spatialSpec) }
                                launch { badgeScale.animateTo(1f, spatialSpec) }
                            }
                        }
                    }
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(1f, fill = false),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                    ) {
                        item(key = "release_hero", contentType = "release_hero") {
                            WhatsNewHero(
                                version = version,
                                badgeRotation = { badgeRotation.value },
                                badgeScale = { badgeScale.value },
                            )
                        }
                        when (val content = state.content) {
                            is WhatsNewContent.Success -> content.sections.forEach { section ->
                                val style = section.category.style()
                                item(key = section.category.name, contentType = "category_header") {
                                    WhatsNewSectionHeader(
                                        style = style,
                                        changeCount = section.entries.size,
                                        modifier = if (disableAnimations) Modifier else Modifier.animateItem(),
                                    )
                                }
                                itemsIndexed(
                                    items = section.entries,
                                    key = { _, entry -> "${section.category.name}:$entry" },
                                    contentType = { _, _ -> "release_change" },
                                ) { index, entry ->
                                    SegmentedListItem(
                                        modifier = Modifier
                                            .padding(horizontal = 16.dp)
                                            .then(if (disableAnimations) Modifier else Modifier.animateItem()),
                                        shapes = ListItemDefaults.segmentedShapes(index = index, count = section.entries.size),
                                        colors = entryColors,
                                        leadingContent = {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .background(style.containerColor, style.glyph.toShape()),
                                            )
                                        },
                                        content = { Text(entry, style = MaterialTheme.typography.bodyLarge) },
                                    )
                                }
                            }
                            WhatsNewContent.Loading -> item(key = "loading", contentType = "status") {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 24.dp, vertical = 32.dp)
                                        .semantics(mergeDescendants = true) {
                                            liveRegion = LiveRegionMode.Polite
                                            progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate
                                        },
                                    verticalArrangement = Arrangement.spacedBy(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    if (!disableAnimations) LoadingIndicator(modifier = Modifier.clearAndSetSemantics {})
                                    Text(
                                        text = stringResource(R.string.whats_new_loading),
                                        style = MaterialTheme.typography.bodyLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                    )
                                }
                            }
                            WhatsNewContent.Empty -> item(key = "empty", contentType = "status") {
                                WhatsNewNotice(
                                    iconRes = R.drawable.info,
                                    shape = MaterialShapes.Pill.toShape(),
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                    title = stringResource(R.string.whats_new_no_highlights_title),
                                    message = stringResource(R.string.whats_new_no_highlights),
                                )
                            }
                            is WhatsNewContent.Error -> item(key = "error", contentType = "status") {
                                if (content.reason == WhatsNewContent.Reason.LOAD_FAILED) {
                                    WhatsNewNotice(
                                        iconRes = R.drawable.error,
                                        shape = MaterialShapes.SoftBurst.toShape(),
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        title = stringResource(R.string.whats_new_load_failed_title),
                                        message = stringResource(R.string.whats_new_load_failed_message),
                                    ) {
                                        FilledTonalButton(
                                            onClick = onRetry,
                                            enabled = !state.isDismissing,
                                            shapes = ButtonDefaults.shapes(),
                                            contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.sync),
                                                contentDescription = null,
                                                modifier = Modifier.size(ButtonDefaults.IconSize),
                                            )
                                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                            Text(stringResource(R.string.retry))
                                        }
                                    }
                                } else {
                                    WhatsNewNotice(
                                        iconRes = R.drawable.history,
                                        shape = MaterialShapes.Arch.toShape(),
                                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                                        title = stringResource(R.string.release_notes),
                                        message = stringResource(R.string.whats_new_not_published),
                                    )
                                }
                            }
                        }
                    }
                    val contentScrolls = listState.canScrollForward || listState.canScrollBackward
                    HorizontalDivider(
                        modifier = Modifier.graphicsLayer { alpha = if (contentScrolls) 1f else 0f },
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                }
                WhatsNewActions(
                    isDismissing = state.isDismissing,
                    dismissalFailed = state.dismissalFailed,
                    onDismiss = onDismiss,
                    onReleaseNotes = onReleaseNotes,
                )
            }
        }
    }
}

private data class WhatsNewCategoryStyle(
    @param:StringRes val titleRes: Int,
    @param:DrawableRes val iconRes: Int,
    val glyph: RoundedPolygon,
    val containerColor: Color,
    val contentColor: Color,
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhatsNewCategory.style(): WhatsNewCategoryStyle {
    val colors = MaterialTheme.colorScheme
    return when (this) {
        WhatsNewCategory.FEATURES -> WhatsNewCategoryStyle(
            titleRes = R.string.whats_new_features,
            iconRes = R.drawable.auto_awesome,
            glyph = MaterialShapes.Sunny,
            containerColor = colors.primaryContainer,
            contentColor = colors.onPrimaryContainer,
        )
        WhatsNewCategory.IMPROVEMENTS -> WhatsNewCategoryStyle(
            titleRes = R.string.whats_new_improvements,
            iconRes = R.drawable.tune,
            glyph = MaterialShapes.Clover4Leaf,
            containerColor = colors.secondaryContainer,
            contentColor = colors.onSecondaryContainer,
        )
        WhatsNewCategory.FIXES -> WhatsNewCategoryStyle(
            titleRes = R.string.whats_new_fixes,
            iconRes = R.drawable.check,
            glyph = MaterialShapes.Cookie6Sided,
            containerColor = colors.tertiaryContainer,
            contentColor = colors.onTertiaryContainer,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhatsNewHero(
    version: String,
    badgeRotation: () -> Float,
    badgeScale: () -> Float,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 32.dp, end = 24.dp, bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .graphicsLayer {
                    rotationZ = badgeRotation()
                    scaleX = badgeScale()
                    scaleY = badgeScale()
                }
                .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(R.drawable.new_release),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .size(40.dp)
                    .graphicsLayer { rotationZ = -badgeRotation() },
            )
        }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.whats_new_heading),
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            ) {
                Text(
                    text = stringResource(R.string.whats_new_version, version),
                    style = MaterialTheme.typography.labelLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhatsNewSectionHeader(
    style: WhatsNewCategoryStyle,
    changeCount: Int,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 24.dp, end = 24.dp, bottom = 12.dp)
            .semantics(mergeDescendants = true) { heading() },
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .background(style.containerColor, style.glyph.toShape()),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(style.iconRes),
                contentDescription = null,
                tint = style.contentColor,
                modifier = Modifier.size(22.dp),
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = stringResource(style.titleRes),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = pluralStringResource(R.plurals.whats_new_change_count, changeCount, changeCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun WhatsNewNotice(
    @DrawableRes iconRes: Int,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(containerColor, shape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(28.dp),
            )
        }
        Column(
            modifier = Modifier.semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.semantics { heading() },
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        action?.invoke()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun WhatsNewActions(
    isDismissing: Boolean,
    dismissalFailed: Boolean,
    onDismiss: () -> Unit,
    onReleaseNotes: () -> Unit,
) {
    val buttonHeight = ButtonDefaults.MediumContainerHeight
    Column(
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, end = 20.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (dismissalFailed) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.error),
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.whats_new_acknowledgment_failed, stringResource(R.string.got_it)),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onReleaseNotes,
                enabled = !isDismissing,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = buttonHeight),
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
            ) {
                Icon(
                    painter = painterResource(R.drawable.link),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                )
                Spacer(Modifier.width(ButtonDefaults.iconSpacingFor(buttonHeight)))
                Text(
                    text = stringResource(R.string.release_notes),
                    style = ButtonDefaults.textStyleFor(buttonHeight),
                    textAlign = TextAlign.Center,
                )
            }
            Button(
                onClick = onDismiss,
                enabled = !isDismissing,
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = buttonHeight),
                shapes = ButtonDefaults.shapesFor(buttonHeight),
                contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight),
            ) {
                Text(
                    text = stringResource(if (isDismissing) R.string.whats_new_saving else R.string.got_it),
                    style = ButtonDefaults.textStyleFor(buttonHeight),
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
