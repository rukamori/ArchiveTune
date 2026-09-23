@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.sources.SourceKind
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
    SourcesContent(state, onAction, onBack)
}

@Composable
private fun SourcesContent(state: SourcesState, onAction: (SourcesAction) -> Unit, onBack: () -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(stringResource(R.string.sources_title)) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.sources_back)) }
        })
    }) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        val top = padding.calculateTopPadding()
        val modifier = remember(top, insets) {
            Modifier.fillMaxSize().padding(top = top).windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom))
        }
        when (state) {
            SourcesState.Loading -> Box(modifier, contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            is SourcesState.Success -> SourcesList(state.model, onAction, modifier)
            SourcesState.Empty -> Box(modifier, contentAlignment = Alignment.Center) { Text(stringResource(R.string.sources_empty)) }
            is SourcesState.Error -> Box(modifier.padding(24.dp), contentAlignment = Alignment.Center) { Text(stringResource(state.message)) }
        }
    }
}

@Composable
private fun SourcesList(model: SourcesUiModel, onAction: (SourcesAction) -> Unit, modifier: Modifier) {
    val add = remember(onAction) { { onAction(SourcesAction.Add) } }
    val rowKey: (SourceRow) -> Any = remember { { it.id } }
    val rowType: (SourceRow) -> Any = remember { { "source" } }
    val spacing = remember { Arrangement.spacedBy(12.dp) }
    LazyColumn(modifier.padding(horizontal = 16.dp), verticalArrangement = spacing) {
        item(key = "description", contentType = "text") {
            Text(stringResource(R.string.sources_description), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 12.dp))
        }
        if (model.busy) item(key = "progress", contentType = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        if (model.error != null && model.editor == null) item(key = "error", contentType = "text") {
            Text(stringResource(model.error), color = MaterialTheme.colorScheme.error)
        }
        items(model.rows.values, key = rowKey, contentType = rowType) { row -> SourceCard(row, model.busy, onAction) }
        item(key = "add", contentType = "button") { TextButton(onClick = add, enabled = !model.busy) { Text(stringResource(R.string.source_add)) } }
    }
    model.editor?.let { SourceEditDialog(it, model.busy, model.error, onAction) }
}

@Composable
private fun SourceCard(row: SourceRow, busy: Boolean, onAction: (SourcesAction) -> Unit) {
    val enabled: (Boolean) -> Unit = remember(row.id, onAction) { { onAction(SourcesAction.Enable(row.id, it)) } }
    val up = remember(row.id, onAction) { { onAction(SourcesAction.Move(row.id, -1)) } }
    val down = remember(row.id, onAction) { { onAction(SourcesAction.Move(row.id, 1)) } }
    val edit = remember(row.id, onAction) { { onAction(SourcesAction.Edit(row.id)) } }
    val remove = remember(row.id, onAction) { { onAction(SourcesAction.Remove(row.id)) } }
    val check = remember(row.id, onAction) { { onAction(SourcesAction.Check(row.id)) } }
    val cardModifier = remember { Modifier.fillMaxWidth() }
    val bodyModifier = remember { Modifier.padding(16.dp) }
    val rowModifier = remember { Modifier.fillMaxWidth() }
    Card(modifier = cardModifier) {
        Column(bodyModifier) {
            Row(rowModifier, verticalAlignment = Alignment.CenterVertically) {
                Text(row.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (row.kind == SourceKind.YOUTUBE) Text(stringResource(R.string.source_always_on), style = MaterialTheme.typography.labelMedium)
                else Switch(checked = row.enabled, onCheckedChange = enabled, enabled = !busy)
            }
            Text(when (row.kind) {
                SourceKind.MODULE -> stringResource(R.string.source_modules, row.moduleCount)
                SourceKind.ADDON -> stringResource(R.string.source_addon)
                SourceKind.JIOSAAVN -> stringResource(R.string.source_jiosaavn)
                SourceKind.YOUTUBE -> stringResource(R.string.source_youtube)
            }, style = MaterialTheme.typography.bodySmall)
            row.health?.let { Text(stringResource(it), style = MaterialTheme.typography.bodySmall) }
            if (row.kind != SourceKind.YOUTUBE) {
                Row {
                    TextButton(onClick = up, enabled = row.canMoveUp && !busy) { Text(stringResource(R.string.source_move_up)) }
                    TextButton(onClick = down, enabled = row.canMoveDown && !busy) { Text(stringResource(R.string.source_move_down)) }
                    TextButton(onClick = check, enabled = !busy) { Text(stringResource(R.string.source_check)) }
                }
                if (row.kind == SourceKind.ADDON || row.kind == SourceKind.MODULE) {
                    Row {
                        TextButton(onClick = edit, enabled = !busy) { Text(stringResource(R.string.edit)) }
                        TextButton(onClick = remove, enabled = !busy) { Text(stringResource(R.string.delete)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SourceEditDialog(editor: SourceEditor, busy: Boolean, error: Int?, onAction: (SourcesAction) -> Unit) {
    val dismiss = remember(onAction) { { onAction(SourcesAction.Dismiss) } }
    val save = remember(onAction) { { onAction(SourcesAction.Save) } }
    val name: (String) -> Unit = remember(onAction) { { onAction(SourcesAction.Name(it)) } }
    val url: (String) -> Unit = remember(onAction) { { onAction(SourcesAction.Url(it)) } }
    val keyboard = remember { KeyboardOptions(keyboardType = KeyboardType.Uri) }
    AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (editor.id == null) R.string.source_add else R.string.edit)) },
        text = {
            Column {
                OutlinedTextField(editor.name, name, label = { Text(stringResource(R.string.source_name)) }, enabled = !busy, singleLine = true)
                OutlinedTextField(editor.url, url, label = { Text(stringResource(R.string.source_address)) }, keyboardOptions = keyboard, enabled = !busy, singleLine = true)
                Text(stringResource(R.string.source_server_hint), style = MaterialTheme.typography.bodySmall)
                if (error != null) Text(stringResource(error), color = MaterialTheme.colorScheme.error)
                if (busy) LinearProgressIndicator()
            }
        }, confirmButton = { TextButton(save, enabled = !busy && editor.url.isNotBlank()) { Text(stringResource(R.string.save)) } },
        dismissButton = { TextButton(dismiss) { Text(stringResource(R.string.cancel_button)) } })
}
