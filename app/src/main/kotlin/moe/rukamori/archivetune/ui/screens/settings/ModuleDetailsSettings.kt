/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.modules.ModuleSettingType
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.viewmodels.*

internal object ModuleSettingsLayout {
    val ContentMaxWidth = 720.dp
    val CardGap = 8.dp
    val CardPadding = 16.dp
    val HeroPadding = 20.dp
    val AvatarSize = 44.dp
    val FloatingActionClearance = 96.dp
}

internal enum class ModuleTone { POSITIVE, NEUTRAL, ATTENTION, CRITICAL }

internal fun moduleTone(@StringRes status: Int, failed: Boolean = false): ModuleTone = when {
    failed -> ModuleTone.CRITICAL
    status == R.string.modules_ready || status == R.string.modules_resolved -> ModuleTone.POSITIVE
    status == R.string.modules_needs_configuration || status == R.string.modules_no_match -> ModuleTone.ATTENTION
    status == R.string.modules_failed || status == R.string.modules_unavailable || status == R.string.modules_timeout -> ModuleTone.CRITICAL
    else -> ModuleTone.NEUTRAL
}

@Composable
@ReadOnlyComposable
internal fun ModuleTone.accentColor(): Color = when (this) {
    ModuleTone.POSITIVE -> MaterialTheme.colorScheme.primary
    ModuleTone.ATTENTION -> MaterialTheme.colorScheme.tertiary
    ModuleTone.CRITICAL -> MaterialTheme.colorScheme.error
    ModuleTone.NEUTRAL -> MaterialTheme.colorScheme.outline
}

@Composable
@ReadOnlyComposable
private fun ModuleTone.containerColor(): Color = when (this) {
    ModuleTone.POSITIVE -> MaterialTheme.colorScheme.primaryContainer
    ModuleTone.ATTENTION -> MaterialTheme.colorScheme.tertiaryContainer
    ModuleTone.CRITICAL -> MaterialTheme.colorScheme.errorContainer
    ModuleTone.NEUTRAL -> MaterialTheme.colorScheme.surfaceContainerHighest
}

@Composable
@ReadOnlyComposable
private fun ModuleTone.onContainerColor(): Color = when (this) {
    ModuleTone.POSITIVE -> MaterialTheme.colorScheme.onPrimaryContainer
    ModuleTone.ATTENTION -> MaterialTheme.colorScheme.onTertiaryContainer
    ModuleTone.CRITICAL -> MaterialTheme.colorScheme.onErrorContainer
    ModuleTone.NEUTRAL -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
internal fun LazyItemScope.moduleItemMotion(): Modifier {
    val motion = MaterialTheme.motionScheme
    return Modifier.animateItem(fadeInSpec = motion.defaultEffectsSpec(), placementSpec = motion.defaultSpatialSpec(),
        fadeOutSpec = motion.fastEffectsSpec())
}

@Composable
internal fun moduleStatusLabel(row: ModuleRowUi): String =
    if (row.playbackStatus) stringResource(R.string.modules_playback_status, stringResource(row.status)) else stringResource(row.status)

@Composable
internal fun ModuleStatusLine(@StringRes status: Int, label: String, modifier: Modifier = Modifier) {
    val color by animateColorAsState(moduleTone(status).accentColor(), MaterialTheme.motionScheme.defaultEffectsSpec(), label = "moduleStatus")
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(18.dp), contentAlignment = Alignment.Center) {
            if (status == R.string.modules_checking) LoadingIndicator(Modifier.fillMaxSize(), color = color)
            else Box(Modifier.size(8.dp).background(color, CircleShape))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ModuleCounter(value: Int, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    val motion = MaterialTheme.motionScheme
    AnimatedContent(
        targetState = value,
        modifier = modifier,
        transitionSpec = {
            val direction = if (targetState > initialState) 1 else -1
            (slideInVertically(motion.defaultSpatialSpec()) { it * direction } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                (slideOutVertically(motion.defaultSpatialSpec()) { -it * direction } + fadeOut(motion.fastEffectsSpec())) using
                SizeTransform(clip = true)
        },
        label = "moduleCounter",
    ) { Text("%d".format(it), style = style, color = color) }
}

@Composable
internal fun ModuleMorphBadge(active: Boolean, size: Dp, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    val motion = MaterialTheme.motionScheme
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val progress by animateFloatAsState(if (active) 1f else 0f, motion.slowSpatialSpec(), label = "moduleBadgeMorph")
    val container by animateColorAsState(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
        motion.defaultEffectsSpec(), label = "moduleBadgeContainer")
    val contentColor by animateColorAsState(if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.defaultEffectsSpec(), label = "moduleBadgeContent")
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(Modifier.matchParentSize().graphicsLayer { rotationZ = progress * 90f }
            .background(container, EditorialMorphShape(morph, progress.coerceIn(0f, 1f))))
        CompositionLocalProvider(LocalContentColor provides contentColor) { content() }
    }
}

@Composable
internal fun ModuleDetails(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val focus = LocalFocusManager.current
    val save = remember(focus, onAction) { { focus.clearFocus(); onAction(ModuleSettingsAction.Save) } }
    val showSave = detail.fields.isNotEmpty() && detail.dirty
    val bottomPadding by animateDpAsState(
        SettingsDimensions.ScreenBottomPadding + if (showSave) ModuleSettingsLayout.FloatingActionClearance else 0.dp,
        MaterialTheme.motionScheme.defaultSpatialSpec(), label = "moduleDetailsBottomPadding")
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight().imePadding(),
            contentPadding = PaddingValues(start = SettingsDimensions.ScreenHorizontalPadding, end = SettingsDimensions.ScreenHorizontalPadding,
                top = 8.dp, bottom = bottomPadding),
            verticalArrangement = Arrangement.spacedBy(ModuleSettingsLayout.CardGap),
        ) {
            if (model.message != null || model.busy != null) item(key = "message", contentType = "message") {
                ModuleMessage(model, onAction, moduleItemMotion())
            }
            item(key = "hero", contentType = "hero") { ModuleDetailHero(detail, model, onAction, moduleItemMotion()) }
            item(key = "priority", contentType = "priority") { ModulePriorityPanel(detail, model, onAction, moduleItemMotion()) }
            if (detail.information.isNotEmpty()) {
                item(key = "information_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_metadata_information) }
                item(key = "information", contentType = "information") {
                    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.padding(ModuleSettingsLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            detail.information.forEach { ModuleInformation(it, onAction) }
                        }
                    }
                }
            }
            item(key = "configuration_header", contentType = "heading") {
                ModuleSectionHeading(R.string.modules_configuration,
                    if (detail.qualityDeclared) R.string.modules_quality_description else R.string.modules_quality_default)
            }
            items(detail.fields, key = { it.key }, contentType = { it.type.name }) { field ->
                ModuleField(field, model, onAction, moduleItemMotion())
            }
            item(key = "diagnostics_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_diagnostics) }
            item(key = "diagnostics", contentType = "diagnostics") { ModuleDiagnosticsPanel(detail, model, onAction) }
            item(key = "maintenance_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_updates) }
            item(key = "maintenance", contentType = "maintenance") { ModuleMaintenancePanel(detail, model, onAction) }
        }
        ModuleSaveToolbar(visible = showSave, enabled = detail.fields.none { it.error != null } && model.busy == null, onSave = save)
    }
}

@Composable
private fun ModuleDetailHero(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLargeIncreased, color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.padding(ModuleSettingsLayout.HeroPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                ModuleMorphBadge(active = detail.row.enabled && model.enabled, size = 72.dp) {
                    detail.row.icon?.let { ModuleProviderIcon(it, size = 36.dp) }
                        ?: Icon(painterResource(R.drawable.extension), contentDescription = null, modifier = Modifier.size(32.dp))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(detail.origin.ifBlank { stringResource(R.string.modules_file_origin) }, style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    ModuleStatusLine(detail.row.status, moduleStatusLabel(detail.row))
                }
            }
            if (detail.description.isNotBlank()) Text(detail.description, style = MaterialTheme.typography.bodyLarge)
            if (detail.capabilities.isNotEmpty() || detail.row.qualityTags.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.modules_capabilities), style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.semantics { heading() })
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        detail.row.qualityTags.forEach { ModuleBadge(stringResource(it), accent = true) }
                        detail.capabilities.forEach { ModuleBadge(stringResource(it)) }
                    }
                }
            }
            ModuleEnableToggle(detail, model, onAction)
        }
    }
}

@Composable
private fun ModuleEnableToggle(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val motion = MaterialTheme.motionScheme
    val checked = detail.row.enabled
    val interactive = model.busy == null
    val corner by animateDpAsState(if (checked) 32.dp else 12.dp, motion.defaultSpatialSpec(), label = "moduleToggleCorner")
    val container by animateColorAsState(if (checked) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        motion.defaultEffectsSpec(), label = "moduleToggleContainer")
    val content by animateColorAsState(if (checked) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        motion.defaultEffectsSpec(), label = "moduleToggleContent")
    val toggle = remember(detail.row.id, onAction) { { value: Boolean -> onAction(ModuleSettingsAction.Enable(detail.row.id, value)) } }
    Surface(Modifier.fillMaxWidth(), shape = RoundedCornerShape(corner), color = container, contentColor = content) {
        Row(Modifier.toggleable(value = checked, enabled = interactive, role = Role.Switch, onValueChange = toggle)
            .heightIn(min = 64.dp).padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                AnimatedContent(targetState = checked, transitionSpec = {
                    (slideInVertically(motion.fastSpatialSpec()) { it / 2 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutVertically(motion.fastSpatialSpec()) { -it / 2 } + fadeOut(motion.fastEffectsSpec()))
                }, label = "moduleToggleLabel") { enabled ->
                    Text(stringResource(if (enabled) R.string.modules_provider_enabled else R.string.modules_provider_disabled),
                        style = MaterialTheme.typography.titleMediumEmphasized)
                }
                if (!model.enabled) Text(stringResource(R.string.modules_off), style = MaterialTheme.typography.bodySmall)
            }
            Switch(checked, onCheckedChange = null, enabled = interactive)
        }
    }
}

@Composable
private fun ModulePriorityPanel(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.padding(ModuleSettingsLayout.CardPadding), horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.large).clearAndSetSemantics {},
                contentAlignment = Alignment.Center) {
                ModuleCounter(detail.row.position, MaterialTheme.typography.headlineSmallEmphasized, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.modules_position, detail.row.position, model.rows.size), style = MaterialTheme.typography.titleSmallEmphasized)
                Text(stringResource(R.string.modules_priority_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                FilledTonalIconButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Move(detail.row.id, -1)) } },
                    shapes = IconButtonDefaults.shapes(), enabled = model.busy == null && detail.row.position > 1) {
                    Icon(painterResource(R.drawable.arrow_upward), stringResource(R.string.modules_move_up))
                }
                FilledTonalIconButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Move(detail.row.id, 1)) } },
                    shapes = IconButtonDefaults.shapes(), enabled = model.busy == null && detail.row.position < model.rows.size) {
                    Icon(painterResource(R.drawable.arrow_downward), stringResource(R.string.modules_move_down))
                }
            }
        }
    }
}

@Composable
internal fun ModuleSectionHeading(title: Int, description: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(start = SettingsDimensions.SectionHeaderHorizontalPadding,
        end = SettingsDimensions.SectionHeaderHorizontalPadding, top = 20.dp, bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.semantics { heading() })
        description?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun ModuleInformation(item: ModuleInfoUi, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(item.label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (item.link) TextButton(onClick = remember(item.value, onAction) { { onAction(ModuleSettingsAction.OpenLink(item.value)) } }, contentPadding = PaddingValues(0.dp)) {
            Text(item.value, style = MaterialTheme.typography.bodyMedium)
        } else Text(item.value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun ModuleDiagnosticsPanel(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(ModuleSettingsLayout.CardPadding).animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(20.dp)) {
            ModuleDiagnosticTile(R.string.modules_initialization, detail.initialization)
            ModuleDiagnosticTile(R.string.modules_last_playback, detail.playback)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.authentication) FilledTonalButton(
                    onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Authorize(detail.row.id)) } },
                    shapes = ButtonDefaults.shapes(), enabled = model.busy == null && !detail.dirty) {
                    Icon(painterResource(R.drawable.login), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.modules_session_verify))
                }
                FilledTonalButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Check(detail.row.id)) } },
                    shapes = ButtonDefaults.shapes(), enabled = model.busy == null) {
                    Icon(painterResource(R.drawable.check), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.modules_check))
                }
                OutlinedButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.CopyDiagnostics) } }, shapes = ButtonDefaults.shapes()) {
                    Icon(painterResource(R.drawable.copy), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.modules_diagnostics_copy))
                }
            }
        }
    }
}

@Composable
private fun ModuleDiagnosticTile(title: Int, diagnostic: ModuleDiagnosticUi?) {
    val motion = MaterialTheme.motionScheme
    val status = diagnostic?.status ?: R.string.modules_not_checked
    val tone = moduleTone(status, diagnostic?.failure != null)
    val container by animateColorAsState(tone.containerColor(), motion.defaultEffectsSpec(), label = "diagnosticContainer")
    val content by animateColorAsState(tone.onContainerColor(), motion.defaultEffectsSpec(), label = "diagnosticContent")
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(Modifier.size(40.dp).background(container, CircleShape), contentAlignment = Alignment.Center) {
            Icon(painterResource(when (tone) {
                ModuleTone.POSITIVE -> R.drawable.check
                ModuleTone.CRITICAL -> R.drawable.error
                else -> R.drawable.info
            }), contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(status), style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (tone == ModuleTone.CRITICAL) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
            if (diagnostic != null) {
                Text(stringResource(R.string.modules_diagnostic_time, diagnostic.time, diagnostic.elapsedMs), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (diagnostic.status == R.string.modules_ready) Text(stringResource(R.string.modules_ready_description), style = MaterialTheme.typography.bodySmall)
                diagnostic.phase?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                diagnostic.candidateCount?.let { Text(stringResource(R.string.modules_candidates_checked, it), style = MaterialTheme.typography.bodySmall) }
                diagnostic.rejection?.let { Text(stringResource(it), style = MaterialTheme.typography.bodyMedium) }
                diagnostic.failure?.let { Text(stringResource(it), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                if (diagnostic.upstream.isNotBlank()) Text(stringResource(R.string.modules_upstream, diagnostic.upstream), style = MaterialTheme.typography.bodyMedium)
                if (diagnostic.variant.isNotBlank()) Text(stringResource(R.string.modules_variant, diagnostic.variant), style = MaterialTheme.typography.bodyMedium)
                if (diagnostic.code.isNotBlank()) Surface(shape = MaterialTheme.shapes.small, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                    Text(diagnostic.code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp))
                }
            }
        }
    }
}

@Composable
private fun ModuleMaintenancePanel(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val interactive = model.busy == null
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(ModuleSettingsLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (detail.row.canUpdate) FilledTonalButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Update(detail.row.id)) } },
                shapes = ButtonDefaults.shapes(), enabled = interactive, modifier = Modifier.fillMaxWidth()) {
                Icon(painterResource(R.drawable.update), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.modules_check_update))
            } else Text(stringResource(R.string.modules_updates_file), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            OutlinedButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Remove(detail.row.id, false)) } },
                shapes = ButtonDefaults.shapes(), enabled = interactive, modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, if (interactive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))) {
                Icon(painterResource(R.drawable.delete), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.modules_remove))
            }
        }
    }
}

@Composable
private fun BoxScope.ModuleSaveToolbar(visible: Boolean, enabled: Boolean, onSave: () -> Unit) {
    val motion = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter).imePadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + scaleIn(motion.defaultSpatialSpec(), initialScale = 0.85f) +
            fadeIn(motion.defaultEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + scaleOut(motion.fastSpatialSpec(), targetScale = 0.85f) +
            fadeOut(motion.fastEffectsSpec()),
    ) {
        val colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors()
        Surface(shape = CircleShape, color = colors.toolbarContainerColor, contentColor = colors.toolbarContentColor,
            shadowElevation = FloatingToolbarDefaults.ContainerExpandedElevationWithFab) {
            Row(Modifier.heightIn(min = FloatingToolbarDefaults.ContainerSize).padding(start = 24.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.modules_unsaved_changes), style = MaterialTheme.typography.labelLarge,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                Button(onClick = onSave, shapes = ButtonDefaults.shapes(), enabled = enabled) {
                    Icon(painterResource(R.drawable.check), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.save))
                }
            }
        }
    }
}

@Composable
private fun ModuleField(field: ModuleFieldUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    val change = remember(field.key, onAction) { { value: String -> onAction(ModuleSettingsAction.Field(field.key, value)) } }
    val interactive = model.busy == null
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = if (field.error != null) BorderStroke(1.dp, MaterialTheme.colorScheme.error) else null) {
        if (field.type == ModuleSettingType.BOOLEAN) {
            Row(Modifier.toggleable(value = field.checked, enabled = interactive, role = Role.Switch,
                onValueChange = remember(change) { { checked: Boolean -> change(checked.toString()) } })
                .padding(horizontal = ModuleSettingsLayout.CardPadding, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(field.label, style = MaterialTheme.typography.bodyLargeEmphasized)
                    ModuleFieldSupportingText(field)
                }
                Switch(field.checked, onCheckedChange = null, enabled = interactive)
            }
        } else Column(Modifier.padding(ModuleSettingsLayout.CardPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (field.type) {
                ModuleSettingType.SELECT -> {
                    val expanded = model.menu == "field:${field.key}"
                    ExposedDropdownMenuBox(expanded = expanded,
                        onExpandedChange = remember(field.key, onAction) { { open -> onAction(ModuleSettingsAction.Menu(if (open) "field:${field.key}" else null)) } }) {
                        OutlinedTextField(value = field.selectedLabel, onValueChange = remember { {} }, readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = interactive),
                            label = { Text(field.label) }, enabled = interactive, isError = field.error != null,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                            maxLines = 3, shape = MaterialTheme.shapes.medium)
                        ExposedDropdownMenu(expanded = expanded, onDismissRequest = remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                            field.options.forEach { option ->
                                DropdownMenuItem(text = {
                                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text(option.label)
                                        if (option.description.isNotBlank()) Text(option.description, style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                    onClick = remember(option.json, change, onAction) { { change(option.json); onAction(ModuleSettingsAction.Menu(null)) } },
                                    trailingIcon = if (option.json == field.json) { { Icon(painterResource(R.drawable.check), null) } } else null,
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding)
                            }
                        }
                    }
                }
                ModuleSettingType.UNSUPPORTED -> {
                    Text(field.label, style = MaterialTheme.typography.bodyLargeEmphasized)
                    Text(stringResource(R.string.modules_setting_unsupported), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                else -> {
                    val transformation = remember(field.sensitive, field.revealed) {
                        if (field.sensitive && !field.revealed) PasswordVisualTransformation() else VisualTransformation.None
                    }
                    val keyboard = remember(field.type, field.sensitive) { KeyboardOptions(keyboardType = when {
                        field.sensitive -> KeyboardType.Password
                        field.type == ModuleSettingType.NUMBER -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }) }
                    OutlinedTextField(value = field.text, onValueChange = change, modifier = Modifier.fillMaxWidth(),
                        label = { Text(field.label) }, enabled = interactive, singleLine = true,
                        isError = field.error != null, visualTransformation = transformation, keyboardOptions = keyboard,
                        shape = MaterialTheme.shapes.medium,
                        trailingIcon = if (field.sensitive) { {
                            IconButton(onClick = remember(field.key, onAction) { { onAction(ModuleSettingsAction.Reveal(field.key)) } },
                                shapes = IconButtonDefaults.shapes()) {
                                Icon(if (field.revealed) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    stringResource(if (field.revealed) R.string.modules_hide_password else R.string.modules_show_password))
                            }
                        } } else null)
                }
            }
            ModuleFieldSupportingText(field)
        }
    }
}

@Composable
private fun ModuleFieldSupportingText(field: ModuleFieldUi) {
    if (field.description.isNotBlank()) Text(field.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    field.error?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
}
