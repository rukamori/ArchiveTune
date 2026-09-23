@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.sources.SourceKind
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.PreferenceEntry
import moe.rukamori.archivetune.ui.component.PreferenceGroup
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.SourceEditor
import moe.rukamori.archivetune.viewmodels.SourceRow
import moe.rukamori.archivetune.viewmodels.SourcesAction
import moe.rukamori.archivetune.viewmodels.SourcesState
import moe.rukamori.archivetune.viewmodels.SourcesUiModel
import moe.rukamori.archivetune.viewmodels.SourcesViewModel

@Composable
fun SourcesScreen(navController: NavController, viewModel: SourcesViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val onBack: () -> Unit = remember(navController) { { navController.navigateUp(); Unit } }
    val onBackToMain: () -> Unit = remember(navController) { { navController.backToMain() } }
    SourcesContent(state, onAction, onBack, onBackToMain)
}

@Composable
private fun SourcesContent(
    state: SourcesState,
    onAction: (SourcesAction) -> Unit,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.sources_title)) }, navigationIcon = {
            IconButton(onClick = onBack, onLongClick = onBackToMain) {
                Icon(painterResource(R.drawable.arrow_back), contentDescription = null)
            }
        })
    }) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        val top = padding.calculateTopPadding()
        val modifier = remember(top, insets) {
            Modifier.fillMaxSize().padding(top = top)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        }
        when (state) {
            SourcesState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is SourcesState.Success -> SourcesList(state.model, onAction, modifier)
            SourcesState.Empty -> Box(modifier, contentAlignment = Alignment.Center) { Text(stringResource(R.string.sources_empty)) }
            is SourcesState.Error -> Box(modifier, contentAlignment = Alignment.Center) { Text(stringResource(state.message)) }
        }
    }
}

@Composable
private fun SourcesList(model: SourcesUiModel, onAction: (SourcesAction) -> Unit, modifier: Modifier) {
    val add = remember(onAction) { { onAction(SourcesAction.Add) } }
    val scroll = rememberScrollState()
    val body = remember(modifier, scroll) {
        modifier.verticalScroll(scroll).padding(bottom = SettingsDimensions.ScreenBottomPadding)
    }
    val note = remember { Modifier.padding(horizontal = 24.dp, vertical = 12.dp) }
    val fullWidth = remember { Modifier.fillMaxWidth() }
    Column(body) {
        if (model.busy) LinearProgressIndicator(fullWidth)
        PreferenceGroup(title = stringResource(R.string.source_order)) {
            model.rows.values.forEach { row ->
                item { key(row.id) { SourcePreference(row, model.busy, onAction) } }
            }
        }
        PreferenceGroup {
            item {
                PreferenceEntry(
                    title = { Text(stringResource(R.string.source_add)) },
                    icon = { Icon(painterResource(R.drawable.add), contentDescription = null) },
                    onClick = add,
                    isEnabled = !model.busy,
                )
            }
        }
        Text(stringResource(R.string.sources_description), modifier = note,
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (model.error != null && model.editor == null && model.selected == null) {
            Text(stringResource(model.error), modifier = note, color = MaterialTheme.colorScheme.error)
        }
    }
    model.selected?.let { SourceActionsDialog(it, model.busy, model.error, onAction) }
    model.editor?.let { SourceEditDialog(it, model.busy, model.error, onAction) }
}

@Composable
private fun SourcePreference(row: SourceRow, busy: Boolean, onAction: (SourcesAction) -> Unit) {
    val enable: (Boolean) -> Unit = remember(row.id, onAction) { { onAction(SourcesAction.Enable(row.id, it)) } }
    val manage = remember(row.id, onAction) { { onAction(SourcesAction.Manage(row.id)) } }
    val switchModifier = remember(row.name) { Modifier.semantics { contentDescription = row.name } }
    val description = when (row.kind) {
        SourceKind.MODULE -> stringResource(R.string.source_modules, row.moduleCount)
        SourceKind.ADDON -> stringResource(R.string.source_addon)
        SourceKind.JIOSAAVN -> stringResource(R.string.source_jiosaavn)
        SourceKind.YOUTUBE -> stringResource(R.string.source_always_on)
    }
    val health = row.health?.let { stringResource(it) }
    val summary = remember(description, health) { listOfNotNull(description, health).joinToString(" · ") }
    PreferenceEntry(
        title = { Text(row.name) },
        description = summary,
        icon = { Icon(painterResource(R.drawable.music_note), contentDescription = null) },
        trailingContent = {
            if (row.kind != SourceKind.YOUTUBE) Switch(modifier = switchModifier, checked = row.enabled, onCheckedChange = enable, enabled = !busy)
        },
        onClick = if (row.kind == SourceKind.YOUTUBE) null else manage,
        isEnabled = !busy,
    )
}

@Composable
private fun SourceActionsDialog(row: SourceRow, busy: Boolean, error: Int?, onAction: (SourcesAction) -> Unit) {
    val dismiss = remember(onAction) { { onAction(SourcesAction.Dismiss) } }
    val up = remember(row.id, onAction) { { onAction(SourcesAction.Move(row.id, -1)) } }
    val down = remember(row.id, onAction) { { onAction(SourcesAction.Move(row.id, 1)) } }
    val edit = remember(row.id, onAction) { { onAction(SourcesAction.Edit(row.id)) } }
    val remove = remember(row.id, onAction) { { onAction(SourcesAction.Remove(row.id)) } }
    val check = remember(row.id, onAction) { { onAction(SourcesAction.Check(row.id)) } }
    DefaultDialog(onDismiss = dismiss, title = { Text(row.name) }, contentScrollable = true,
        buttons = { TextButton(onClick = dismiss) { Text(stringResource(android.R.string.ok)) } }) {
        PreferenceGroup {
            item {
                PreferenceEntry(title = { Text(stringResource(R.string.source_check)) },
                    description = row.health?.let { stringResource(it) }, onClick = check, isEnabled = !busy)
            }
            item(visible = row.canMoveUp) {
                PreferenceEntry(title = { Text(stringResource(R.string.source_move_up)) }, onClick = up, isEnabled = !busy)
            }
            item(visible = row.canMoveDown) {
                PreferenceEntry(title = { Text(stringResource(R.string.source_move_down)) }, onClick = down, isEnabled = !busy)
            }
            item(visible = row.kind == SourceKind.ADDON || row.kind == SourceKind.MODULE) {
                PreferenceEntry(title = { Text(stringResource(R.string.edit)) }, onClick = edit, isEnabled = !busy)
            }
            item(visible = row.kind == SourceKind.ADDON || row.kind == SourceKind.MODULE) {
                PreferenceEntry(title = { Text(stringResource(R.string.delete)) }, onClick = remove, isEnabled = !busy)
            }
        }
        if (busy) LinearProgressIndicator()
        if (error != null) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
    }
}

@Composable
private fun SourceEditDialog(editor: SourceEditor, busy: Boolean, error: Int?, onAction: (SourcesAction) -> Unit) {
    val dismiss = remember(onAction) { { onAction(SourcesAction.Dismiss) } }
    val save = remember(onAction) { { onAction(SourcesAction.Save) } }
    val name: (String) -> Unit = remember(onAction) { { onAction(SourcesAction.Name(it)) } }
    val url: (String) -> Unit = remember(onAction) { { onAction(SourcesAction.Url(it)) } }
    val keyboard = remember { KeyboardOptions(keyboardType = KeyboardType.Uri) }
    val fullWidth = remember { Modifier.fillMaxWidth() }
    val spacing = remember { Arrangement.spacedBy(12.dp) }
    DefaultDialog(onDismiss = dismiss,
        title = { Text(stringResource(if (editor.id == null) R.string.source_add else R.string.edit)) },
        contentScrollable = true,
        buttons = {
            TextButton(onClick = dismiss) { Text(stringResource(R.string.cancel_button)) }
            TextButton(onClick = save, enabled = !busy && editor.url.isNotBlank()) { Text(stringResource(R.string.save)) }
        }) {
        Column(modifier = fullWidth, verticalArrangement = spacing) {
            OutlinedTextField(editor.url, url, modifier = fullWidth,
                label = { Text(stringResource(R.string.source_address)) }, keyboardOptions = keyboard,
                enabled = !busy, singleLine = true, isError = error != null)
            OutlinedTextField(editor.name, name, modifier = fullWidth,
                label = { Text(stringResource(R.string.source_name)) }, enabled = !busy, singleLine = true)
            Text(stringResource(R.string.source_server_hint), style = MaterialTheme.typography.bodySmall)
            if (error != null) Text(stringResource(error), color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
            if (busy) LinearProgressIndicator(fullWidth)
        }
    }
}
