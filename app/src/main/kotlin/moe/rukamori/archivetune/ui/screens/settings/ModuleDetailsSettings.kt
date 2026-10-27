/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.modules.ModuleSettingType
import moe.rukamori.archivetune.viewmodels.*

internal object ModuleSettingsLayout {
    val ContentMaxWidth = 720.dp
}

@Composable
internal fun ModuleDetails(detail: ModuleDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val focus = LocalFocusManager.current
    val save = remember(focus, onAction) { { focus.clearFocus(); onAction(ModuleSettingsAction.Save) } }
    LazyColumn(
        modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight().imePadding(),
        contentPadding = PaddingValues(start = SettingsDimensions.ScreenHorizontalPadding, end = SettingsDimensions.ScreenHorizontalPadding,
            top = 8.dp, bottom = SettingsDimensions.ScreenBottomPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
    ) {
        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction) }
        item(key = "identity", contentType = "identity") {
            Column(Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                detail.row.icon?.let { ModuleProviderIcon(it) }
                if (detail.description.isNotBlank()) Text(detail.description, style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(detail.origin.ifBlank { stringResource(R.string.modules_file_origin) }, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (detail.information.isNotEmpty()) {
            item(key = "information_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_metadata_information) }
            itemsIndexed(detail.information, key = { _, item -> item.label }, contentType = { _, _ -> "information" }) { index, item ->
                Surface(shape = ListItemDefaults.segmentedShapes(index = index, count = detail.information.size).shape,
                    color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    ModuleInformation(item, onAction, Modifier.fillMaxWidth().padding(SettingsDimensions.RowHorizontalPadding))
                }
            }
        }
        item(key = "playback_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_playback) }
        item(key = "enabled", contentType = "switch") {
            SegmentedListItem(
                checked = detail.row.enabled,
                onCheckedChange = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Enable(detail.row.id, it)) } },
                shapes = moduleSettingShapes(index = 0, count = 2),
                modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch }, enabled = model.busy == null,
                trailingContent = { Switch(detail.row.enabled, onCheckedChange = null, enabled = model.busy == null) },
                supportingContent = if (!model.enabled) { { Text(stringResource(R.string.modules_off)) } } else null,
                colors = ListItemDefaults.segmentedColors(),
            ) {
                Text(stringResource(if (detail.row.enabled) R.string.modules_provider_enabled else R.string.modules_provider_disabled),
                    style = MaterialTheme.typography.bodyLargeEmphasized)
            }
        }
        item(key = "priority", contentType = "priority") {
            SegmentedListItem(
                shapes = ListItemDefaults.segmentedShapes(index = 1, count = 2),
                modifier = Modifier.fillMaxWidth(),
                colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                supportingContent = { Text(stringResource(R.string.modules_priority_description), style = MaterialTheme.typography.bodySmall) },
                trailingContent = {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        FilledTonalIconButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Move(detail.row.id, -1)) } },
                            enabled = model.busy == null && detail.row.position > 1) {
                            Icon(painterResource(R.drawable.arrow_upward), stringResource(R.string.modules_move_up))
                        }
                        FilledTonalIconButton(onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Move(detail.row.id, 1)) } },
                            enabled = model.busy == null && detail.row.position < model.rows.size) {
                            Icon(painterResource(R.drawable.arrow_downward), stringResource(R.string.modules_move_down))
                        }
                    }
                },
            ) { Text(stringResource(R.string.modules_position, detail.row.position, model.rows.size), style = MaterialTheme.typography.titleSmallEmphasized) }
        }
        item(key = "capabilities_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_capabilities) }
        item(key = "capabilities", contentType = "capabilities") {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                FlowRow(Modifier.fillMaxWidth().padding(SettingsDimensions.RowHorizontalPadding),
                    horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    detail.capabilities.forEach { capability -> ModuleBadge(stringResource(capability)) }
                }
            }
        }
        item(key = "configuration_header", contentType = "heading") {
            ModuleSectionHeading(R.string.modules_configuration,
                if (detail.qualityDeclared) R.string.modules_quality_description else R.string.modules_quality_default)
        }
        itemsIndexed(detail.fields, key = { _, field -> field.key }, contentType = { _, field -> field.type.name }) { index, field ->
            ModuleField(field, index, detail.fields.size, model, onAction)
        }
        if (detail.fields.isNotEmpty()) item(key = "save", contentType = "action") {
            Column(Modifier.fillMaxWidth().padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (detail.dirty) Text(stringResource(R.string.modules_unsaved_changes), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = SettingsDimensions.RowHorizontalPadding))
                Button(onClick = save, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
                    enabled = detail.dirty && detail.fields.none { it.error != null } && model.busy == null) {
                    Icon(painterResource(R.drawable.check), null, Modifier.size(ButtonDefaults.IconSize))
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.save))
                }
            }
        }
        item(key = "diagnostics_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_diagnostics) }
        if (detail.authentication) item(key = "authorization", contentType = "action_row") {
            ModuleActionRow(R.string.modules_session_verify, R.drawable.login, 0, 1,
                enabled = model.busy == null && !detail.dirty,
                onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Authorize(detail.row.id)) } })
        }
        item(key = "initialization", contentType = "diagnostic") {
            ModuleDiagnosticPanel(R.string.modules_initialization, detail.initialization, 0, 2)
        }
        item(key = "last_playback", contentType = "diagnostic") {
            ModuleDiagnosticPanel(R.string.modules_last_playback, detail.playback, 1, 2)
        }
        item(key = "check", contentType = "action_row") {
            ModuleActionRow(R.string.modules_check, R.drawable.check, 0, 2,
                enabled = model.busy == null, onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Check(detail.row.id)) } },
                modifier = Modifier.padding(top = 8.dp))
        }
        item(key = "copy", contentType = "action_row") {
            ModuleActionRow(R.string.modules_diagnostics_copy, R.drawable.copy, 1, 2,
                onClick = remember(onAction) { { onAction(ModuleSettingsAction.CopyDiagnostics) } })
        }
        item(key = "maintenance_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_updates) }
        if (detail.row.canUpdate) item(key = "update", contentType = "action_row") {
            ModuleActionRow(R.string.modules_check_update, R.drawable.update, 0, 2, enabled = model.busy == null,
                onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Update(detail.row.id)) } })
        } else item(key = "file_update", contentType = "description") {
            Text(stringResource(R.string.modules_updates_file), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 8.dp))
        }
        item(key = "remove", contentType = "action_row") {
            ModuleActionRow(R.string.modules_remove, R.drawable.delete, if (detail.row.canUpdate) 1 else 0,
                if (detail.row.canUpdate) 2 else 1, destructive = true, enabled = model.busy == null,
                onClick = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Remove(detail.row.id, false)) } })
        }
    }
}

@Composable
internal fun ModuleSectionHeading(title: Int, description: Int? = null) {
    Column(Modifier.fillMaxWidth().padding(start = SettingsDimensions.SectionHeaderHorizontalPadding,
        end = SettingsDimensions.SectionHeaderHorizontalPadding, top = 24.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(stringResource(title), style = MaterialTheme.typography.titleSmallEmphasized, color = MaterialTheme.colorScheme.primary)
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
internal fun ModuleActionRow(label: Int, icon: Int, index: Int, count: Int, modifier: Modifier = Modifier,
    enabled: Boolean = true, destructive: Boolean = false, onClick: () -> Unit) {
    SegmentedListItem(
        onClick = onClick, shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = modifier.fillMaxWidth(), enabled = enabled,
        leadingContent = {
            Icon(painterResource(icon), null, Modifier.size(SettingsDimensions.RowIconInnerSize),
                tint = if (destructive && enabled) MaterialTheme.colorScheme.error else LocalContentColor.current)
        },
        trailingContent = { Icon(painterResource(R.drawable.arrow_forward), null, Modifier.size(SettingsDimensions.ChevronSize)) },
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Text(stringResource(label), style = MaterialTheme.typography.bodyLargeEmphasized,
            color = if (destructive && enabled) MaterialTheme.colorScheme.error else LocalContentColor.current)
    }
}

@Composable
private fun moduleSettingShapes(index: Int, count: Int): ListItemShapes {
    val shapes = ListItemDefaults.segmentedShapes(index = index, count = count)
    return remember(shapes) { shapes.copy(selectedShape = shapes.shape) }
}

@Composable
private fun ModuleDiagnosticPanel(title: Int, diagnostic: ModuleDiagnosticUi?, index: Int, count: Int) {
    Surface(shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(SettingsDimensions.RowHorizontalPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(stringResource(diagnostic?.status ?: R.string.modules_not_checked), style = MaterialTheme.typography.titleMediumEmphasized,
                color = if (diagnostic?.failure != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
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
                if (diagnostic.code.isNotBlank()) Text(diagnostic.code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

@Composable
private fun ModuleField(field: ModuleFieldUi, index: Int, count: Int, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val change = remember(field.key, onAction) { { value: String -> onAction(ModuleSettingsAction.Field(field.key, value)) } }
    if (field.type == ModuleSettingType.BOOLEAN) {
        SegmentedListItem(checked = field.checked,
            onCheckedChange = remember(change) { { change(it.toString()) } },
            shapes = moduleSettingShapes(index = index, count = count),
            modifier = Modifier.fillMaxWidth().semantics { role = Role.Switch }, enabled = model.busy == null,
            colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            trailingContent = { Switch(field.checked, onCheckedChange = null, enabled = model.busy == null) },
            supportingContent = { ModuleFieldSupportingText(field) },
        ) { Text(field.label, style = MaterialTheme.typography.bodyLargeEmphasized) }
        return
    }
    Surface(shape = ListItemDefaults.segmentedShapes(index = index, count = count).shape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(SettingsDimensions.RowHorizontalPadding), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            when (field.type) {
                ModuleSettingType.SELECT -> {
                    val expanded = model.menu == "field:${field.key}"
                    ExposedDropdownMenuBox(expanded = expanded,
                        onExpandedChange = remember(field.key, onAction) { { open -> onAction(ModuleSettingsAction.Menu(if (open) "field:${field.key}" else null)) } }) {
                        OutlinedTextField(value = field.selectedLabel, onValueChange = remember { {} }, readOnly = true,
                            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = model.busy == null),
                            label = { Text(field.label) }, enabled = model.busy == null, isError = field.error != null,
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
                        label = { Text(field.label) }, enabled = model.busy == null, singleLine = true,
                        isError = field.error != null, visualTransformation = transformation, keyboardOptions = keyboard,
                        shape = MaterialTheme.shapes.medium,
                        trailingIcon = if (field.sensitive) { {
                            IconButton(onClick = remember(field.key, onAction) { { onAction(ModuleSettingsAction.Reveal(field.key)) } }) {
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
