/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.viewmodels.*
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import okio.ByteString

@Composable
fun ModulesSettings(navController: NavController, viewModel: ModuleSettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onAction }
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { onAction(ModuleSettingsAction.FileSelected(it)) }
    }
    val clipboardLabel = stringResource(R.string.modules_diagnostics)
    LaunchedEffect(viewModel, context, clipboardLabel, uriHandler) {
        viewModel.events.collect { event -> when (event) {
            is ModuleSettingsEvent.OpenLink -> try { uriHandler.openUri(event.url) }
                catch (_: ActivityNotFoundException) { onAction(ModuleSettingsAction.LinkFailed) }
                catch (_: IllegalArgumentException) { onAction(ModuleSettingsAction.LinkFailed) }
            ModuleSettingsEvent.PickFile -> picker.launch(arrayOf("*/*"))
            ModuleSettingsEvent.NavigateBack -> navController.navigateUp()
            is ModuleSettingsEvent.Copy -> (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                .setPrimaryClip(ClipData.newPlainText(clipboardLabel, event.text))
        } }
    }
    BackHandler { onAction(ModuleSettingsAction.Back) }
    ModuleSettingsContent(state, onAction)
}

@Composable
private fun ModuleSettingsContent(state: ModuleSettingsState, onAction: (ModuleSettingsAction) -> Unit) {
    val model = when (state) {
        is ModuleSettingsState.Success -> state.model
        is ModuleSettingsState.Empty -> state.model
        else -> null
    }
    val onBack = remember(onAction) { { onAction(ModuleSettingsAction.Back) } }
    val onAdd = remember(onAction) { { onAction(ModuleSettingsAction.Add) } }
    val scrollBehavior = key(model?.page, model?.module?.row?.id, model?.catalog?.row?.id) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            MediumFlexibleTopAppBar(title = { Text(model?.module?.row?.name?.takeIf { model.page == ModuleSettingsPage.MODULE }
                ?: model?.catalog?.row?.name?.takeIf { model.page == ModuleSettingsPage.CATALOG && it.isNotBlank() }
                ?: stringResource(R.string.modules_title), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                subtitle = {
                    Text(when (model?.page) {
                        ModuleSettingsPage.MODULE -> model.module?.row?.subtitle.orEmpty()
                        ModuleSettingsPage.CATALOG -> stringResource(R.string.modules_catalogs)
                        else -> stringResource(R.string.modules_subtitle)
                    }, maxLines = 1, overflow = TextOverflow.Ellipsis)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer),
                scrollBehavior = scrollBehavior,
                navigationIcon = { FilledTonalIconButton(onClick = onBack) { Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.modules_back)) } },
                actions = {
                    if (model != null && model.page == ModuleSettingsPage.HOME) {
                        FilledTonalIconButton(onClick = onAdd, enabled = model.busy == null) { Icon(painterResource(R.drawable.add), stringResource(R.string.add)) }
                        ModuleMenu(model.menu == "toolbar", model.busy == null,
                            onOpen = remember(onAction) { { onAction(ModuleSettingsAction.Menu("toolbar")) } },
                            onDismiss = remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                            ModuleMenuItem(R.string.modules_check_all, model.rows.any { it.enabled }, onAction, ModuleSettingsAction.Check())
                            ModuleMenuItem(R.string.modules_check_updates, model.rows.any { it.canUpdate }, onAction, ModuleSettingsAction.Update())
                        }
                    }
                })
    }) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        val contentModifier = remember(padding, insets) {
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).clipToBounds()
        }
        Box(contentModifier, contentAlignment = Alignment.TopCenter) {
            when (state) {
                ModuleSettingsState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                is ModuleSettingsState.Error -> Column(Modifier.align(Alignment.Center).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(state.message))
                    TextButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.Retry) } }) { Text(stringResource(R.string.retry)) }
                }
                else -> if (model != null) {
                    when (model.page) {
                        ModuleSettingsPage.HOME -> ModuleHome(model, onAction)
                        ModuleSettingsPage.MODULE -> model.module?.let { ModuleDetails(it, model, onAction) }
                        ModuleSettingsPage.CATALOG -> model.catalog?.let { ModuleCatalogDetails(it, model, onAction) }
                    }
                    ModuleDialogs(model, onAction)
                }
            }
        }
    }
}

@Composable
private fun ModuleHome(model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val listState = rememberLazyListState()
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? String
        val toId = to.key as? String
        if (fromId != null && toId != null && model.rows.any { it.id == fromId } && model.rows.any { it.id == toId }) {
            onAction(ModuleSettingsAction.Drag(fromId, toId))
        }
    }
    val moveUpLabel = stringResource(R.string.modules_move_up)
    val moveDownLabel = stringResource(R.string.modules_move_down)
    LazyColumn(
        state = listState,
        modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(start = SettingsDimensions.ScreenHorizontalPadding, end = SettingsDimensions.ScreenHorizontalPadding,
            top = 12.dp, bottom = SettingsDimensions.ScreenBottomPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
    ) {
        item(key = "master", contentType = "control") {
            SegmentedListItem(
                checked = model.enabled,
                onCheckedChange = remember(onAction) { { onAction(ModuleSettingsAction.SetEnabled(it)) } },
                shapes = ListItemDefaults.segmentedShapes(index = 0, count = 1),
                modifier = Modifier.fillMaxWidth().padding(bottom = SettingsDimensions.SectionSpacing).semantics { role = Role.Switch },
                enabled = model.busy == null,
                colors = ListItemDefaults.segmentedColors(),
                verticalAlignment = Alignment.CenterVertically,
                leadingContent = { ModuleListIcon(R.drawable.extension) },
                trailingContent = { Switch(model.enabled, onCheckedChange = null, enabled = model.busy == null) },
                supportingContent = { Text(stringResource(R.string.modules_enabled_description)) },
            ) { Text(stringResource(R.string.modules_enabled), style = MaterialTheme.typography.titleMediumEmphasized) }
        }
        item(key = "tabs", contentType = "tabs") {
            Row(Modifier.fillMaxWidth().selectableGroup().padding(bottom = SettingsDimensions.SectionSpacing),
                horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                ToggleButton(checked = model.tab == 0,
                    onCheckedChange = remember(onAction) { { onAction(ModuleSettingsAction.Tab(0)) } },
                    shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(), modifier = Modifier.weight(1f)
                        .semantics { role = Role.Tab; selected = model.tab == 0 }) {
                    Text(stringResource(R.string.modules_installed))
                }
                ToggleButton(checked = model.tab == 1,
                    onCheckedChange = remember(onAction) { { onAction(ModuleSettingsAction.Tab(1)) } },
                    shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(), modifier = Modifier.weight(1f)
                        .semantics { role = Role.Tab; selected = model.tab == 1 }) {
                    Text(stringResource(R.string.modules_catalogs))
                }
            }
        }
        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction) }
        if (model.tab == 0) {
            item(key = "summary", contentType = "summary") {
                Column(Modifier.padding(horizontal = 4.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.modules_counts, model.rows.size, model.enabledCount),
                        style = MaterialTheme.typography.titleSmallEmphasized)
                    Text(stringResource(R.string.modules_order_hint), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (!model.enabled) Text(stringResource(R.string.modules_off), style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (model.rows.isEmpty()) item(key = "empty", contentType = "empty") {
                ModuleEmpty(R.string.modules_empty, R.string.modules_empty_description, R.string.modules_import_file, onAction, ModuleSettingsAction.File)
            }
            itemsIndexed(model.rows, key = { _, row -> row.id }, contentType = { _, _ -> "module" }) { index, row ->
                ReorderableItem(reorder, key = row.id) { dragging ->
                    val moveUp = remember(row.id, onAction) { { onAction(ModuleSettingsAction.Move(row.id, -1)); true } }
                    val moveDown = remember(row.id, onAction) { { onAction(ModuleSettingsAction.Move(row.id, 1)); true } }
                    val accessibility = remember(row.position, model.rows.size, model.busy, moveUpLabel, moveDownLabel, moveUp, moveDown) {
                        Modifier.semantics {
                            customActions = buildList {
                                if (model.busy == null && row.position > 1) add(CustomAccessibilityAction(moveUpLabel, moveUp))
                                if (model.busy == null && row.position < model.rows.size) add(CustomAccessibilityAction(moveDownLabel, moveDown))
                            }
                        }
                    }
                    ModuleRow(row, model, index, dragging, onAction, modifier = accessibility) {
                        Box(
                            Modifier.size(48.dp).draggableHandle(
                                enabled = model.busy == null,
                                onDragStopped = { onAction(ModuleSettingsAction.CommitOrder) },
                            ),
                            contentAlignment = Alignment.Center,
                        ) { Icon(painterResource(R.drawable.drag_handle), stringResource(R.string.modules_move, row.name)) }
                    }
                }
            }
        } else {
            item(key = "catalog_header", contentType = "summary") {
                Text(stringResource(R.string.modules_catalogs), modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                    style = MaterialTheme.typography.titleSmallEmphasized)
            }
            if (model.catalogs.isEmpty()) item(key = "catalogs_empty", contentType = "empty") {
                ModuleEmpty(R.string.modules_catalogs_empty, R.string.modules_catalogs_empty_description, R.string.modules_add_catalog, onAction, ModuleSettingsAction.Url(true))
            }
            itemsIndexed(model.catalogs, key = { _, row -> row.id }, contentType = { _, _ -> "catalog" }) { index, row ->
                ModuleCatalogRow(row, model, index, onAction)
            }
        }
    }
}

@Composable
private fun ModuleRow(
    row: ModuleRowUi,
    model: ModuleSettingsUiModel,
    index: Int,
    dragging: Boolean,
    onAction: (ModuleSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit,
) {
    SegmentedListItem(
        selected = dragging,
        onClick = remember(row.id, onAction) { { onAction(ModuleSettingsAction.OpenModule(row.id)) } },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = model.rows.size),
        modifier = modifier.fillMaxWidth(),
        enabled = model.busy == null,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = SettingsDimensions.RowVerticalPadding),
        supportingContent = {
            FlowRow(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                row.qualityTags.forEach { ModuleBadge(stringResource(it), accent = true) }
                row.capabilities.forEach { ModuleBadge(stringResource(it)) }
                ModuleBadge(if (row.playbackStatus) stringResource(R.string.modules_playback_status, stringResource(row.status)) else stringResource(row.status))
            }
        },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            handle()
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.name, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    row.icon?.let { ModuleProviderIcon(it, size = SettingsDimensions.RowIconInnerSize) }
                    if (row.subtitle.isNotBlank()) Text(row.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                ModuleMenu(model.menu == row.id, model.busy == null,
                    remember(row.id, onAction) { { onAction(ModuleSettingsAction.Menu(row.id)) } },
                    remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                    ModuleMenuItem(R.string.edit, true, onAction, ModuleSettingsAction.EditMetadata(row.id))
                    ModuleMenuItem(R.string.modules_move_up, row.position > 1, onAction, ModuleSettingsAction.Move(row.id, -1))
                    ModuleMenuItem(R.string.modules_move_down, row.position < model.rows.size, onAction, ModuleSettingsAction.Move(row.id, 1))
                    ModuleMenuItem(R.string.modules_check, true, onAction, ModuleSettingsAction.Check(row.id))
                    ModuleMenuItem(R.string.modules_check_update, row.canUpdate, onAction, ModuleSettingsAction.Update(row.id))
                    ModuleMenuItem(R.string.modules_remove, true, onAction, ModuleSettingsAction.Remove(row.id, false))
                }
                Switch(row.enabled, remember(row.id, onAction) { { onAction(ModuleSettingsAction.Enable(row.id, it)) } },
                    enabled = model.busy == null,
                    modifier = remember(row.name) { Modifier.semantics { contentDescription = row.name } })
            }
        }
    }
}

@Composable
private fun ModuleCatalogRow(row: ModuleCatalogRowUi, model: ModuleSettingsUiModel, index: Int, onAction: (ModuleSettingsAction) -> Unit) {
    SegmentedListItem(
        onClick = remember(row.id, onAction) { { onAction(ModuleSettingsAction.OpenCatalog(row.id)) } },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = model.catalogs.size),
        modifier = Modifier.fillMaxWidth(),
        enabled = model.busy == null,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = SettingsDimensions.RowVerticalPadding),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = { ModuleListIcon(R.drawable.language) },
        trailingContent = {
            ModuleMenu(model.menu == row.id, model.busy == null,
                remember(row.id, onAction) { { onAction(ModuleSettingsAction.Menu(row.id)) } },
                remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                ModuleMenuItem(R.string.modules_refresh, row.origin.isNotBlank(), onAction, ModuleSettingsAction.Refresh(row.id))
                ModuleMenuItem(R.string.modules_edit_catalog, true, onAction, ModuleSettingsAction.Url(true, row.id))
                ModuleMenuItem(R.string.modules_remove, true, onAction, ModuleSettingsAction.Remove(row.id, true))
            }
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (row.origin.isNotBlank() && row.origin != row.name) Text(row.origin, style = MaterialTheme.typography.bodySmall)
                ModuleBadge(stringResource(R.string.modules_catalog_count, row.count), accent = true)
                if (row.refreshed.isNotBlank()) Text(stringResource(R.string.modules_last_refresh, row.refreshed),
                    style = MaterialTheme.typography.bodySmall)
            }
        },
    ) {
        Text(row.name.ifBlank { stringResource(R.string.modules_file_origin) },
            style = MaterialTheme.typography.titleMediumEmphasized,
            maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
internal fun ModuleBadge(label: String, accent: Boolean = false) {
    Surface(shape = MaterialTheme.shapes.small, color = if (accent) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = if (accent) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium)
    }
}

@Composable
internal fun ModuleProviderIcon(icon: ByteString, modifier: Modifier = Modifier, size: Dp = SettingsDimensions.RowIconSize) {
    val context = LocalContext.current
    val pixels = with(LocalDensity.current) { size.roundToPx() }
    val request = remember(context, icon, pixels) {
        ImageRequest.Builder(context).data(icon.asByteBuffer()).size(pixels).memoryCacheKey(icon.sha256().hex()).build()
    }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Fit,
        placeholder = painterResource(R.drawable.extension), error = painterResource(R.drawable.extension), modifier = modifier.size(size))
}

@Composable
internal fun ModuleListIcon(icon: Int) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer) {
        Box(Modifier.size(SettingsDimensions.RowIconSize), contentAlignment = Alignment.Center) {
            Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(SettingsDimensions.RowIconInnerSize))
        }
    }
}

@Composable
private fun ModuleCatalogDetails(detail: ModuleCatalogDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val refresh = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Refresh(detail.row.id)) } }
    LazyColumn(
        modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(start = SettingsDimensions.ScreenHorizontalPadding, end = SettingsDimensions.ScreenHorizontalPadding,
            top = 8.dp, bottom = SettingsDimensions.ScreenBottomPadding),
        verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap),
    ) {
        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction) }
        item(key = "summary", contentType = "summary") {
            Column(Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.modules_catalog_count, detail.entries.size), style = MaterialTheme.typography.headlineSmallEmphasized)
                if (detail.row.refreshed.isNotBlank()) Text(stringResource(R.string.modules_last_refresh, detail.row.refreshed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.modules_compatibility_pending), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item(key = "refresh", contentType = "action_row") {
            ModuleActionRow(R.string.modules_refresh, R.drawable.update, 0, 1, onClick = refresh,
                enabled = model.busy == null && detail.row.origin.isNotBlank())
        }
        item(key = "entries_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_title) }
        if (detail.entries.isEmpty()) item(key = "empty", contentType = "empty") {
            Text(stringResource(R.string.modules_catalog_entries_empty), modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        itemsIndexed(detail.entries, key = { _, entry -> entry.key }, contentType = { _, _ -> "entry" }) { index, entry ->
            ModuleCatalogEntry(entry, index, detail.entries.size, model, onAction)
        }
    }
}

@Composable
private fun ModuleCatalogEntry(entry: ModuleCatalogEntryUi, index: Int, count: Int, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val toggle = remember(entry.key, onAction) { { onAction(ModuleSettingsAction.ToggleEntry(entry.key)) } }
    val install: () -> Unit = remember(entry.download, entry.sha256, onAction) { { entry.download?.let { onAction(ModuleSettingsAction.Install(it, entry.sha256)) } } }
    val loading = entry.download != null && model.busy == entry.download
    SegmentedListItem(
        onClick = toggle,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        modifier = Modifier.fillMaxWidth(),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        contentPadding = PaddingValues(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = SettingsDimensions.RowVerticalPadding),
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodyMedium)
                if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium,
                    maxLines = if (entry.expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
                if (entry.installed || entry.qualityTags.isNotEmpty() || entry.capabilities.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (entry.installed) ModuleBadge(stringResource(R.string.modules_already_installed), accent = true)
                        entry.qualityTags.forEach { ModuleBadge(stringResource(it), accent = true) }
                        entry.capabilities.forEach { ModuleBadge(stringResource(it)) }
                    }
                }
                if (!entry.supported) Text(stringResource(R.string.modules_unsupported_description),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                if (entry.expanded && entry.capabilities.isEmpty()) {
                    Text(stringResource(R.string.modules_capabilities_pending), style = MaterialTheme.typography.bodySmall)
                }
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    if (entry.supported) FilledTonalButton(onClick = install, shapes = ButtonDefaults.shapes(), enabled = model.busy == null && entry.download != null) {
                        if (loading) {
                            LoadingIndicator(modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(8.dp))
                        }
                        Text(stringResource(if (loading) R.string.modules_loading else if (entry.installed) R.string.modules_check_update else R.string.modules_install))
                    }
                    TextButton(onClick = toggle) {
                        Text(stringResource(if (entry.expanded) R.string.modules_hide_details else R.string.modules_show_details))
                        Spacer(Modifier.width(4.dp))
                        Icon(painterResource(if (entry.expanded) R.drawable.expand_less else R.drawable.expand_more),
                            contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                }
            }
        },
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            entry.icon?.let { ModuleProviderIcon(it) }
            Text(entry.name, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ModuleDialogs(model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val dismiss = remember(onAction) { { onAction(ModuleSettingsAction.Dismiss) } }
    val confirm = remember(onAction) { { onAction(ModuleSettingsAction.Confirm) } }
    when (model.dialog) {
        ModuleSettingsDialog.EDIT_METADATA -> model.metadataEditor?.let { editor ->
            Dialog(onDismissRequest = dismiss, properties = remember { DialogProperties(usePlatformDefaultWidth = false) }) {
                Scaffold(topBar = {
                    TopAppBar(title = { Text(stringResource(R.string.modules_edit_metadata)) },
                        navigationIcon = {
                            IconButton(onClick = dismiss, enabled = model.busy == null) {
                                Icon(painterResource(R.drawable.close), stringResource(android.R.string.cancel))
                            }
                        })
                }) { padding ->
                    Box(Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding), contentAlignment = Alignment.TopCenter) {
                        LazyColumn(
                            modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight().imePadding(),
                            contentPadding = PaddingValues(SettingsDimensions.ScreenHorizontalPadding),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            item(key = "name", contentType = "field") {
                                OutlinedTextField(editor.name,
                                    remember(onAction) { { onAction(ModuleSettingsAction.MetadataChanged(ModuleMetadataField.NAME, it)) } },
                                    label = { Text(stringResource(R.string.modules_metadata_name)) },
                                    supportingText = { if (!editor.valid) Text(stringResource(R.string.modules_field_required)) },
                                    isError = !editor.valid, enabled = model.busy == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            item(key = "author", contentType = "field") {
                                OutlinedTextField(editor.author,
                                    remember(onAction) { { onAction(ModuleSettingsAction.MetadataChanged(ModuleMetadataField.AUTHOR, it)) } },
                                    label = { Text(stringResource(R.string.modules_metadata_author)) },
                                    enabled = model.busy == null, singleLine = true, modifier = Modifier.fillMaxWidth())
                            }
                            item(key = "description", contentType = "field") {
                                OutlinedTextField(editor.description,
                                    remember(onAction) { { onAction(ModuleSettingsAction.MetadataChanged(ModuleMetadataField.DESCRIPTION, it)) } },
                                    label = { Text(stringResource(R.string.description)) },
                                    enabled = model.busy == null, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth())
                            }
                            if (model.busy != null) item(key = "progress", contentType = "progress") { LinearProgressIndicator(Modifier.fillMaxWidth()) }
                            if (model.message != null && model.messageIsError) item(key = "error", contentType = "message") {
                                Text(stringResource(model.message), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            item(key = "save", contentType = "action") {
                                Button(onClick = remember(onAction) { { onAction(ModuleSettingsAction.SaveMetadata) } },
                                    enabled = model.busy == null && editor.valid && editor.dirty,
                                    shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save)) }
                            }
                        }
                    }
                }
            }
        }
        ModuleSettingsDialog.ADD -> ModalBottomSheet(onDismissRequest = dismiss) {
            Column(Modifier.fillMaxWidth().padding(horizontal = SettingsDimensions.ScreenHorizontalPadding)
                .padding(bottom = SettingsDimensions.ScreenBottomPadding), verticalArrangement = Arrangement.spacedBy(SettingsDimensions.SegmentedItemGap)) {
                Text(stringResource(R.string.add), style = MaterialTheme.typography.headlineSmallEmphasized,
                    modifier = Modifier.padding(horizontal = SettingsDimensions.RowHorizontalPadding, vertical = 16.dp))
                ModuleActionRow(R.string.modules_import_file, R.drawable.playlist_import, 0, 3,
                    onClick = remember(onAction) { { onAction(ModuleSettingsAction.File) } })
                ModuleActionRow(R.string.modules_add_url, R.drawable.link, 1, 3,
                    onClick = remember(onAction) { { onAction(ModuleSettingsAction.Url(false)) } })
                ModuleActionRow(R.string.modules_add_catalog, R.drawable.language, 2, 3,
                    onClick = remember(onAction) { { onAction(ModuleSettingsAction.Url(true)) } })
            }
        }
        ModuleSettingsDialog.MODULE_URL, ModuleSettingsDialog.CATALOG_URL -> Dialog(onDismissRequest = dismiss, properties = remember { DialogProperties(usePlatformDefaultWidth = false) }) {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(if (model.dialog == ModuleSettingsDialog.CATALOG_URL) R.string.modules_add_catalog else R.string.modules_add_url)) },
                    navigationIcon = { IconButton(onClick = dismiss, enabled = model.busy == null) { Icon(painterResource(R.drawable.close), stringResource(android.R.string.cancel)) } })
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                LazyColumn(Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxHeight().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    item(key = "url", contentType = "field") {
                        OutlinedTextField(model.url, remember(onAction) { { onAction(ModuleSettingsAction.UrlChanged(it)) } }, label = { Text(stringResource(R.string.modules_url)) },
                            supportingText = { Text(stringResource(R.string.modules_url_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                            keyboardOptions = remember { KeyboardOptions(keyboardType = KeyboardType.Uri) }, enabled = model.busy == null, isError = model.messageIsError)
                    }
                    if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction) }
                    item(key = "submit", contentType = "action") {
                        Button(onClick = remember(onAction) { { onAction(ModuleSettingsAction.SubmitUrl) } }, shapes = ButtonDefaults.shapes(),
                            modifier = Modifier.fillMaxWidth(), enabled = model.url.isNotBlank() && model.busy == null) { Text(stringResource(R.string.modules_continue)) }
                    }
                }
                }
            }
        }
        ModuleSettingsDialog.REVIEW -> model.review?.let { review ->
            AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(if (review.catalogCount != null) R.string.modules_catalog_review else R.string.modules_install_review)) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item(key = "identity", contentType = "identity") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                    review.icon?.let { ModuleProviderIcon(it) }
                                    Text(review.name.ifBlank { stringResource(R.string.modules_file_origin) }, style = MaterialTheme.typography.titleMediumEmphasized, modifier = Modifier.weight(1f))
                                }
                                review.information.forEach { ModuleInformation(it, onAction) }
                                if (review.currentVersion != null) Text(stringResource(R.string.modules_update_versions, review.currentVersion, review.version)) else if (review.version.isNotBlank()) Text(review.version)
                                Text(review.origin.ifBlank { stringResource(R.string.modules_file_origin) }, style = MaterialTheme.typography.bodySmall)
                                if (review.description.isNotBlank()) Text(review.description)
                                if (review.catalogCount != null) Text(stringResource(R.string.modules_catalog_count, review.catalogCount))
                                else Text(stringResource(R.string.modules_install_description))
                                if (review.schemaChanged) Text(stringResource(R.string.modules_configuration_changed))
                                if (review.requiredSettings.isNotEmpty()) Text(stringResource(R.string.modules_required_settings, review.requiredSettings.joinToString()))
                            }
                        }
                        items(review.capabilities, key = { it }, contentType = { "capability" }) { Text(stringResource(it)) }
                        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction) }
                    }
                }, confirmButton = { TextButton(onClick = confirm, enabled = model.busy == null) { Text(stringResource(if (review.currentVersion != null) R.string.modules_replace else R.string.modules_install)) } },
                dismissButton = { TextButton(onClick = dismiss, enabled = model.busy == null) { Text(stringResource(android.R.string.cancel)) } })
        }
        ModuleSettingsDialog.REMOVE_MODULE, ModuleSettingsDialog.REMOVE_CATALOG, ModuleSettingsDialog.DISCARD, ModuleSettingsDialog.DISCARD_METADATA -> {
            val title = when (model.dialog) {
                ModuleSettingsDialog.REMOVE_MODULE -> R.string.modules_remove_module_title
                ModuleSettingsDialog.REMOVE_CATALOG -> R.string.modules_remove_catalog_title
                else -> R.string.modules_discard_title
            }
            val description = when (model.dialog) {
                ModuleSettingsDialog.REMOVE_MODULE -> R.string.modules_remove_module_description
                ModuleSettingsDialog.REMOVE_CATALOG -> R.string.modules_remove_catalog_description
                ModuleSettingsDialog.DISCARD_METADATA -> R.string.modules_discard_metadata_description
                else -> R.string.modules_discard_description
            }
            AlertDialog(onDismissRequest = dismiss, title = { Text(stringResource(title)) }, text = { Text(stringResource(description)) },
                confirmButton = { TextButton(onClick = confirm, enabled = model.busy == null) { Text(stringResource(if (model.dialog == ModuleSettingsDialog.DISCARD || model.dialog == ModuleSettingsDialog.DISCARD_METADATA) R.string.modules_discard else R.string.modules_remove)) } },
                dismissButton = { TextButton(onClick = dismiss, enabled = model.busy == null) { Text(stringResource(android.R.string.cancel)) } })
        }
        ModuleSettingsDialog.DIAGNOSTICS -> model.failure?.let { failure ->
            AlertDialog(
                onDismissRequest = dismiss,
                title = { Text(stringResource(R.string.modules_diagnostics)) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(key = "stage", contentType = "stage") {
                            Text(stringResource(failure.stage), style = MaterialTheme.typography.titleSmall)
                        }
                        item(key = "message", contentType = "message") {
                            model.message?.let { Text(stringResource(it)) }
                        }
                        item(key = "code", contentType = "code") {
                            Text(failure.code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.CopyDiagnostics) } }) {
                        Text(stringResource(R.string.modules_diagnostics_copy))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss) { Text(stringResource(R.string.modules_back)) } },
            )
        }
        null -> Unit
    }
}

@Composable
internal fun ModuleMessage(model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    if (model.busy != null) LinearProgressIndicator(Modifier.fillMaxWidth().padding(bottom = 8.dp))
    model.message?.let { message ->
        Surface(color = if (model.messageIsError) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
            shape = MaterialTheme.shapes.medium) {
            Row(Modifier.fillMaxWidth().padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f).padding(vertical = 12.dp)) {
                    Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                    if (model.failure != null) TextButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.FailureDetails) } }) {
                        Text(stringResource(R.string.modules_show_details))
                    }
                }
                IconButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.ClearMessage) } }) { Icon(painterResource(R.drawable.close), stringResource(R.string.modules_dismiss)) }
            }
        }
    }
}

@Composable
private fun ModuleEmpty(title: Int, description: Int, actionLabel: Int, onAction: (ModuleSettingsAction) -> Unit, action: ModuleSettingsAction) {
    Column(Modifier.fillMaxWidth().padding(vertical = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(title), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium)
        Button(onClick = remember(action, onAction) { { onAction(action) } }, shapes = ButtonDefaults.shapes()) { Text(stringResource(actionLabel)) }
    }
}

@Composable
private fun ModuleMenu(expanded: Boolean, enabled: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box {
        IconButton(onClick = onOpen, enabled = enabled) { Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more)) }
        DropdownMenu(expanded, onDismiss, content = content)
    }
}

@Composable
private fun ModuleMenuItem(label: Int, enabled: Boolean, onAction: (ModuleSettingsAction) -> Unit, action: ModuleSettingsAction) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled,
        onClick = remember(action, onAction) { { onAction(ModuleSettingsAction.Menu(null)); onAction(action) } })
}
