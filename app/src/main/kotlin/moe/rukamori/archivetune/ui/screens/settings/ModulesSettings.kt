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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.traversalIndex
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.graphics.shapes.RoundedPolygon
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

private val ModuleSettingsState.uiModel: ModuleSettingsUiModel?
    get() = when (this) {
        is ModuleSettingsState.Success -> model
        is ModuleSettingsState.Empty -> model
        else -> null
    }

private fun ModuleSettingsState.pageKey(): Any = uiModel?.let { model ->
    when (model.page) {
        ModuleSettingsPage.HOME -> ModuleSettingsPage.HOME
        ModuleSettingsPage.MODULE -> "module:${model.module?.row?.id}"
        ModuleSettingsPage.CATALOG -> "catalog:${model.catalog?.row?.id}"
    }
} ?: this::class

private fun ModuleSettingsState.depth(): Int = if (uiModel?.page?.let { it != ModuleSettingsPage.HOME } == true) 1 else 0

@Composable
private fun ModuleSettingsContent(state: ModuleSettingsState, onAction: (ModuleSettingsAction) -> Unit) {
    val model = state.uiModel
    val motion = MaterialTheme.motionScheme
    val layoutDirection = LocalLayoutDirection.current
    val homeListState = rememberLazyListState()
    val onBack = remember(onAction) { { onAction(ModuleSettingsAction.Back) } }
    val scrollBehavior = key(model?.page, model?.module?.row?.id, model?.catalog?.row?.id) {
        TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    }
    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeFlexibleTopAppBar(title = { Text(model?.module?.row?.name?.takeIf { model.page == ModuleSettingsPage.MODULE }
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
                navigationIcon = {
                    FilledTonalIconButton(onClick = onBack, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.arrow_back), stringResource(R.string.modules_back))
                    }
                },
                actions = {
                    if (model != null && model.page == ModuleSettingsPage.HOME) {
                        ModuleMenu(model.menu == "toolbar", model.busy == null,
                            onOpen = remember(onAction) { { onAction(ModuleSettingsAction.Menu("toolbar")) } },
                            onDismiss = remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                            ModuleMenuItem(R.string.modules_check_all, R.drawable.check, model.rows.any { it.enabled }, onAction, ModuleSettingsAction.Check())
                            ModuleMenuItem(R.string.modules_check_updates, R.drawable.update, model.rows.any { it.canUpdate }, onAction, ModuleSettingsAction.Update())
                        }
                    }
                })
    }) { padding ->
        val insets = LocalPlayerAwareWindowInsets.current
        val contentModifier = remember(padding, insets) {
            Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding)
                .windowInsetsPadding(insets.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)).clipToBounds()
        }
        AnimatedContent(
            targetState = state,
            modifier = contentModifier,
            contentAlignment = Alignment.TopCenter,
            contentKey = { it.pageKey() },
            transitionSpec = {
                val forward = targetState.depth() > initialState.depth()
                if (forward || targetState.depth() < initialState.depth()) {
                    val sign = (if (forward) 1 else -1) * (if (layoutDirection == LayoutDirection.Rtl) -1 else 1)
                    (slideInHorizontally(motion.defaultSpatialSpec()) { sign * it / 4 } + fadeIn(motion.defaultEffectsSpec())) togetherWith
                        (slideOutHorizontally(motion.defaultSpatialSpec()) { -sign * it / 4 } + fadeOut(motion.fastEffectsSpec()))
                } else fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            },
            label = "modulePage",
        ) { target ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
                when (target) {
                    ModuleSettingsState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
                    is ModuleSettingsState.Error -> ModuleLoadError(target.message, onAction)
                    else -> target.uiModel?.let { page ->
                        when (page.page) {
                            ModuleSettingsPage.HOME -> ModuleHome(page, homeListState, onAction)
                            ModuleSettingsPage.MODULE -> page.module?.let { ModuleDetails(it, page, onAction) }
                            ModuleSettingsPage.CATALOG -> page.catalog?.let { ModuleCatalogDetails(it, page, onAction) }
                        }
                    }
                }
            }
        }
        if (model != null) ModuleDialogs(model, onAction)
    }
}

@Composable
private fun ModuleHome(model: ModuleSettingsUiModel, listState: LazyListState, onAction: (ModuleSettingsAction) -> Unit) {
    val haptics = LocalHapticFeedback.current
    val reorder = rememberReorderableLazyListState(listState) { from, to ->
        val fromId = from.key as? String
        val toId = to.key as? String
        if (fromId != null && toId != null && model.rows.any { it.id == fromId } && model.rows.any { it.id == toId }) {
            onAction(ModuleSettingsAction.Drag(fromId, toId))
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }
    val moveUpLabel = stringResource(R.string.modules_move_up)
    val moveDownLabel = stringResource(R.string.modules_move_down)
    val fabVisible by remember(listState) { derivedStateOf { !listState.lastScrolledForward || !listState.canScrollForward } }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(
            state = listState,
            modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight(),
            contentPadding = PaddingValues(start = SettingsDimensions.ScreenHorizontalPadding, end = SettingsDimensions.ScreenHorizontalPadding,
                top = 8.dp, bottom = SettingsDimensions.ScreenBottomPadding + ModuleSettingsLayout.FloatingActionClearance),
            verticalArrangement = Arrangement.spacedBy(ModuleSettingsLayout.CardGap),
        ) {
            item(key = "master", contentType = "control") { ModuleControlDeck(model, onAction, moduleItemMotion()) }
            item(key = "tabs", contentType = "tabs") { ModuleTabs(model, onAction, moduleItemMotion().padding(vertical = 8.dp)) }
            if (model.message != null || model.busy != null) item(key = "message", contentType = "message") {
                ModuleMessage(model, onAction, moduleItemMotion())
            }
            if (model.tab == 0) {
                if (model.rows.size > 1) item(key = "order_hint", contentType = "hint") {
                    Row(moduleItemMotion().fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(painterResource(R.drawable.drag_handle), contentDescription = null, modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.modules_order_hint), style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (model.rows.isEmpty()) item(key = "empty", contentType = "empty") {
                    ModuleEmpty(R.drawable.extension, MaterialShapes.Cookie12Sided, R.string.modules_empty, R.string.modules_empty_description,
                        R.string.modules_import_file, onAction, ModuleSettingsAction.File, moduleItemMotion())
                }
                items(model.rows, key = { it.id }, contentType = { "module" }) { row ->
                    ReorderableItem(reorder, key = row.id, animateItemModifier = moduleItemMotion()) { dragging ->
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
                        ModuleCard(row, model, dragging, onAction, modifier = accessibility) {
                            Box(
                                Modifier.size(48.dp).draggableHandle(
                                    enabled = model.busy == null,
                                    onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.LongPress) },
                                    onDragStopped = { onAction(ModuleSettingsAction.CommitOrder) },
                                ),
                                contentAlignment = Alignment.Center,
                            ) { Icon(painterResource(R.drawable.drag_handle), stringResource(R.string.modules_move, row.name)) }
                        }
                    }
                }
            } else {
                if (model.catalogs.isEmpty()) item(key = "catalogs_empty", contentType = "empty") {
                    ModuleEmpty(R.drawable.language, MaterialShapes.Clover8Leaf, R.string.modules_catalogs_empty, R.string.modules_catalogs_empty_description,
                        R.string.modules_add_catalog, onAction, ModuleSettingsAction.Url(true), moduleItemMotion())
                }
                items(model.catalogs, key = { it.id }, contentType = { "catalog" }) { row ->
                    ModuleCatalogCard(row, model, onAction, moduleItemMotion())
                }
            }
        }
        ModuleAddMenu(visible = model.busy == null && fabVisible, onAction = onAction,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp))
    }
}

@Composable
private fun ModuleControlDeck(model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    val enabled = model.enabled
    val container by animateColorAsState(if (enabled) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
        motion.defaultEffectsSpec(), label = "deckContainer")
    val content by animateColorAsState(if (enabled) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
        motion.defaultEffectsSpec(), label = "deckContent")
    val toggle = remember(onAction) { { value: Boolean -> onAction(ModuleSettingsAction.SetEnabled(value)) } }
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLargeIncreased, color = container, contentColor = content) {
        Column {
            Column(Modifier.fillMaxWidth().toggleable(value = enabled, enabled = model.busy == null, role = Role.Switch, onValueChange = toggle)
                .padding(start = ModuleSettingsLayout.HeroPadding, end = ModuleSettingsLayout.HeroPadding, top = ModuleSettingsLayout.HeroPadding, bottom = 12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    ModuleMorphBadge(active = enabled, size = 64.dp) {
                        Icon(painterResource(R.drawable.extension), contentDescription = null, modifier = Modifier.size(28.dp))
                    }
                    Text(stringResource(R.string.modules_enabled), style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.weight(1f))
                    Switch(enabled, onCheckedChange = null, enabled = model.busy == null)
                }
                AnimatedContent(targetState = enabled, transitionSpec = {
                    (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 3 }) togetherWith
                        fadeOut(motion.fastEffectsSpec())
                }, label = "deckDescription") { on ->
                    Text(stringResource(if (on) R.string.modules_enabled_description else R.string.modules_off),
                        style = MaterialTheme.typography.bodyMedium, color = LocalContentColor.current.copy(alpha = 0.85f))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = ModuleSettingsLayout.HeroPadding, end = ModuleSettingsLayout.HeroPadding, bottom = ModuleSettingsLayout.HeroPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleStat(model.enabledCount, R.string.modules_provider_enabled, enabled, Modifier.weight(1f))
                ModuleStat(model.rows.size, R.string.modules_installed, enabled, Modifier.weight(1f))
                ModuleStat(model.catalogs.size, R.string.modules_catalogs, enabled, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ModuleStat(value: Int, label: Int, emphasized: Boolean, modifier: Modifier) {
    val color by animateColorAsState(if (emphasized) MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.55f)
        else MaterialTheme.colorScheme.surfaceContainerHighest, MaterialTheme.motionScheme.defaultEffectsSpec(), label = "statContainer")
    Column(modifier.background(color, MaterialTheme.shapes.large).padding(horizontal = 14.dp, vertical = 10.dp).semantics(mergeDescendants = true) {},
        verticalArrangement = Arrangement.spacedBy(2.dp)) {
        ModuleCounter(value, MaterialTheme.typography.headlineSmallEmphasized)
        Text(stringResource(label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ModuleTabs(model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
        ModuleTab(0, model.tab, R.string.modules_installed, R.drawable.extension, ButtonGroupDefaults.connectedLeadingButtonShapes(), onAction)
        ModuleTab(1, model.tab, R.string.modules_catalogs, R.drawable.language, ButtonGroupDefaults.connectedTrailingButtonShapes(), onAction)
    }
}

@Composable
private fun RowScope.ModuleTab(index: Int, current: Int, label: Int, icon: Int, shapes: ToggleButtonShapes, onAction: (ModuleSettingsAction) -> Unit) {
    val checked = index == current
    val weight by animateFloatAsState(if (checked) 1.3f else 1f, MaterialTheme.motionScheme.defaultSpatialSpec(), label = "tabWeight")
    ToggleButton(checked = checked, onCheckedChange = remember(index, onAction) { { onAction(ModuleSettingsAction.Tab(index)) } },
        shapes = shapes, modifier = Modifier.weight(weight).heightIn(min = ButtonDefaults.MediumContainerHeight)
            .semantics { role = Role.Tab; selected = checked }) {
        Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(ButtonDefaults.IconSize))
        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
        Text(stringResource(label), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun ModuleCard(
    row: ModuleRowUi,
    model: ModuleSettingsUiModel,
    dragging: Boolean,
    onAction: (ModuleSettingsAction) -> Unit,
    modifier: Modifier = Modifier,
    handle: @Composable () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val elevation by animateDpAsState(if (dragging) 8.dp else 0.dp, motion.fastSpatialSpec(), label = "cardElevation")
    val scale by animateFloatAsState(if (dragging) 1.03f else 1f, motion.fastSpatialSpec(), label = "cardScale")
    val corner by animateDpAsState(if (dragging) 32.dp else 20.dp, motion.defaultSpatialSpec(), label = "cardCorner")
    val container by animateColorAsState(when {
        dragging -> MaterialTheme.colorScheme.surfaceContainerHighest
        row.enabled -> MaterialTheme.colorScheme.surfaceContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }, motion.defaultEffectsSpec(), label = "cardContainer")
    Surface(
        onClick = remember(row.id, onAction) { { onAction(ModuleSettingsAction.OpenModule(row.id)) } },
        modifier = modifier.fillMaxWidth().graphicsLayer { scaleX = scale; scaleY = scale },
        enabled = model.busy == null,
        shape = RoundedCornerShape(corner),
        color = container,
        shadowElevation = elevation,
    ) {
        Column(Modifier.padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                handle()
                ModuleAvatar(row.icon, row.position, model.rows.size, row.enabled)
                Column(Modifier.weight(1f).padding(start = 12.dp, end = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(row.name, style = MaterialTheme.typography.titleMediumEmphasized, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    if (row.subtitle.isNotBlank()) Text(row.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                ModuleMenu(model.menu == row.id, model.busy == null,
                    remember(row.id, onAction) { { onAction(ModuleSettingsAction.Menu(row.id)) } },
                    remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                    ModuleMenuItem(R.string.edit, R.drawable.edit, true, onAction, ModuleSettingsAction.EditMetadata(row.id))
                    ModuleMenuItem(R.string.modules_move_up, R.drawable.arrow_upward, row.position > 1, onAction, ModuleSettingsAction.Move(row.id, -1))
                    ModuleMenuItem(R.string.modules_move_down, R.drawable.arrow_downward, row.position < model.rows.size, onAction, ModuleSettingsAction.Move(row.id, 1))
                    ModuleMenuItem(R.string.modules_check, R.drawable.check, true, onAction, ModuleSettingsAction.Check(row.id))
                    ModuleMenuItem(R.string.modules_check_update, R.drawable.update, row.canUpdate, onAction, ModuleSettingsAction.Update(row.id))
                    ModuleMenuItem(R.string.modules_remove, R.drawable.delete, true, onAction, ModuleSettingsAction.Remove(row.id, false))
                }
                Switch(row.enabled, remember(row.id, onAction) { { onAction(ModuleSettingsAction.Enable(row.id, it)) } },
                    enabled = model.busy == null,
                    modifier = remember(row.name) { Modifier.semantics { contentDescription = row.name } })
            }
            Column(Modifier.padding(start = 48.dp, end = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ModuleStatusLine(row.status, moduleStatusLabel(row))
                if (row.qualityTags.isNotEmpty() || row.capabilities.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp),
                        itemVerticalAlignment = Alignment.CenterVertically) {
                        row.qualityTags.forEach { ModuleBadge(stringResource(it), accent = true) }
                        if (row.capabilities.isNotEmpty()) Text(row.capabilities.map { stringResource(it) }.joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleAvatar(icon: ByteString?, position: Int, count: Int, enabled: Boolean) {
    val motion = MaterialTheme.motionScheme
    val positionLabel = stringResource(R.string.modules_position, position, count)
    val rank by animateColorAsState(if (enabled) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outlineVariant,
        motion.defaultEffectsSpec(), label = "avatarRank")
    val onRank by animateColorAsState(if (enabled) MaterialTheme.colorScheme.onTertiary else MaterialTheme.colorScheme.onSurfaceVariant,
        motion.defaultEffectsSpec(), label = "avatarRankContent")
    Box(Modifier.size(ModuleSettingsLayout.AvatarSize + 6.dp)) {
        Box(Modifier.size(ModuleSettingsLayout.AvatarSize).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
            contentAlignment = Alignment.Center) {
            icon?.let { ModuleProviderIcon(it, size = 28.dp) } ?: Icon(painterResource(R.drawable.extension), contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(SettingsDimensions.RowIconInnerSize))
        }
        Box(Modifier.align(Alignment.BottomEnd).sizeIn(minWidth = 22.dp, minHeight = 22.dp).background(rank, CircleShape)
            .padding(horizontal = 5.dp).clearAndSetSemantics { contentDescription = positionLabel }, contentAlignment = Alignment.Center) {
            ModuleCounter(position, MaterialTheme.typography.labelSmall, color = onRank)
        }
    }
}

@Composable
private fun ModuleCatalogCard(row: ModuleCatalogRowUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    Surface(
        onClick = remember(row.id, onAction) { { onAction(ModuleSettingsAction.OpenCatalog(row.id)) } },
        modifier = modifier.fillMaxWidth(),
        enabled = model.busy == null,
        shape = MaterialTheme.shapes.largeIncreased,
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(Modifier.padding(start = ModuleSettingsLayout.CardPadding, top = 14.dp, bottom = 14.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(ModuleSettingsLayout.AvatarSize).background(MaterialTheme.colorScheme.tertiaryContainer, MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.language), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(SettingsDimensions.RowIconInnerSize))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(row.name.ifBlank { stringResource(R.string.modules_file_origin) }, style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                if (row.origin.isNotBlank() && row.origin != row.name) Text(row.origin, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.modules_catalog_count, row.count), style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary)
                if (row.refreshed.isNotBlank()) Text(stringResource(R.string.modules_last_refresh, row.refreshed),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            ModuleMenu(model.menu == row.id, model.busy == null,
                remember(row.id, onAction) { { onAction(ModuleSettingsAction.Menu(row.id)) } },
                remember(onAction) { { onAction(ModuleSettingsAction.Menu(null)) } }) {
                ModuleMenuItem(R.string.modules_refresh, R.drawable.update, row.origin.isNotBlank(), onAction, ModuleSettingsAction.Refresh(row.id))
                ModuleMenuItem(R.string.modules_edit_catalog, R.drawable.edit, true, onAction, ModuleSettingsAction.Url(true, row.id))
                ModuleMenuItem(R.string.modules_remove, R.drawable.delete, true, onAction, ModuleSettingsAction.Remove(row.id, true))
            }
        }
    }
}

@Composable
private fun ModuleAddMenu(visible: Boolean, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    LaunchedEffect(visible) { if (!visible) expanded = false }
    val addLabel = stringResource(R.string.add)
    val stateLabel = stringResource(if (expanded) R.string.modules_add_menu_expanded else R.string.modules_add_menu_collapsed)
    FloatingActionButtonMenu(
        expanded = expanded,
        modifier = modifier,
        button = {
            ToggleFloatingActionButton(
                checked = expanded,
                onCheckedChange = { expanded = it },
                modifier = Modifier.semantics {
                    traversalIndex = -1f
                    stateDescription = stateLabel
                    contentDescription = addLabel
                }.animateFloatingActionButton(visible = visible || expanded, alignment = Alignment.BottomEnd),
            ) {
                val icon by remember { derivedStateOf { if (checkedProgress > 0.5f) R.drawable.close else R.drawable.add } }
                Icon(painterResource(icon), contentDescription = null,
                    modifier = with(ToggleFloatingActionButtonDefaults) { Modifier.animateIcon({ checkedProgress }) })
            }
        },
    ) {
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onAction(ModuleSettingsAction.File) },
            icon = { Icon(painterResource(R.drawable.playlist_import), contentDescription = null) },
            text = { Text(stringResource(R.string.modules_import_file)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onAction(ModuleSettingsAction.Url(false)) },
            icon = { Icon(painterResource(R.drawable.link), contentDescription = null) },
            text = { Text(stringResource(R.string.modules_add_url)) },
        )
        FloatingActionButtonMenuItem(
            onClick = { expanded = false; onAction(ModuleSettingsAction.Url(true)) },
            icon = { Icon(painterResource(R.drawable.language), contentDescription = null) },
            text = { Text(stringResource(R.string.modules_add_catalog)) },
        )
    }
}

@Composable
internal fun ModuleBadge(label: String, accent: Boolean = false) {
    Surface(shape = CircleShape, color = if (accent) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = if (accent) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurfaceVariant) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp), style = MaterialTheme.typography.labelMedium)
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
private fun ModuleCatalogDetails(detail: ModuleCatalogDetailUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit) {
    val refresh = remember(detail.row.id, onAction) { { onAction(ModuleSettingsAction.Refresh(detail.row.id)) } }
    val refreshing = model.busy == detail.row.id
    LazyColumn(
        modifier = Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxWidth().fillMaxHeight(),
        contentPadding = PaddingValues(start = SettingsDimensions.ScreenHorizontalPadding, end = SettingsDimensions.ScreenHorizontalPadding,
            top = 8.dp, bottom = SettingsDimensions.ScreenBottomPadding),
        verticalArrangement = Arrangement.spacedBy(ModuleSettingsLayout.CardGap),
    ) {
        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") {
            ModuleMessage(model, onAction, moduleItemMotion())
        }
        item(key = "summary", contentType = "summary") {
            Surface(moduleItemMotion().fillMaxWidth(), shape = MaterialTheme.shapes.extraLargeIncreased, color = MaterialTheme.colorScheme.surfaceContainer) {
                Column(Modifier.padding(ModuleSettingsLayout.HeroPadding), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).background(MaterialTheme.colorScheme.tertiaryContainer, MaterialShapes.Clover4Leaf.toShape()),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.language), contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.size(28.dp))
                        }
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.modules_catalog_count, detail.entries.size), style = MaterialTheme.typography.headlineSmallEmphasized)
                            if (detail.row.refreshed.isNotBlank()) Text(stringResource(R.string.modules_last_refresh, detail.row.refreshed),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.modules_compatibility_pending), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    FilledTonalButton(onClick = refresh, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
                        enabled = model.busy == null && detail.row.origin.isNotBlank()) {
                        if (refreshing) LoadingIndicator(Modifier.size(ButtonDefaults.IconSize))
                        else Icon(painterResource(R.drawable.update), null, Modifier.size(ButtonDefaults.IconSize))
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.modules_refresh))
                    }
                }
            }
        }
        item(key = "entries_header", contentType = "heading") { ModuleSectionHeading(R.string.modules_title) }
        if (detail.entries.isEmpty()) item(key = "empty", contentType = "empty") {
            Text(stringResource(R.string.modules_catalog_entries_empty), modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        items(detail.entries, key = { it.key }, contentType = { "entry" }) { entry ->
            ModuleCatalogEntry(entry, model, onAction, moduleItemMotion())
        }
    }
}

@Composable
private fun ModuleCatalogEntry(entry: ModuleCatalogEntryUi, model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier) {
    val motion = MaterialTheme.motionScheme
    val toggle = remember(entry.key, onAction) { { onAction(ModuleSettingsAction.ToggleEntry(entry.key)) } }
    val install: () -> Unit = remember(entry.download, entry.sha256, onAction) { { entry.download?.let { onAction(ModuleSettingsAction.Install(it, entry.sha256)) } } }
    val loading = entry.download != null && model.busy == entry.download
    val chevron by animateFloatAsState(if (entry.expanded) 180f else 0f, motion.defaultSpatialSpec(), label = "entryChevron")
    val container by animateColorAsState(if (entry.expanded) MaterialTheme.colorScheme.surfaceContainer else MaterialTheme.colorScheme.surfaceContainerLow,
        motion.defaultEffectsSpec(), label = "entryContainer")
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.largeIncreased, color = container) {
        Column(Modifier.clickable(onClickLabel = stringResource(if (entry.expanded) R.string.modules_hide_details else R.string.modules_show_details),
            onClick = toggle).padding(ModuleSettingsLayout.CardPadding).animateContentSize(motion.defaultSpatialSpec()),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(ModuleSettingsLayout.AvatarSize).background(MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.shapes.medium),
                    contentAlignment = Alignment.Center) {
                    entry.icon?.let { ModuleProviderIcon(it, size = 28.dp) } ?: Icon(painterResource(R.drawable.extension), contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(SettingsDimensions.RowIconInnerSize))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(entry.name, style = MaterialTheme.typography.titleMediumEmphasized)
                    if (entry.subtitle.isNotBlank()) Text(entry.subtitle, style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Icon(painterResource(R.drawable.expand_more), contentDescription = null,
                    modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = chevron }, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (entry.description.isNotBlank()) Text(entry.description, style = MaterialTheme.typography.bodyMedium,
                maxLines = if (entry.expanded) Int.MAX_VALUE else 2, overflow = TextOverflow.Ellipsis)
            if (entry.installed || entry.qualityTags.isNotEmpty() || entry.capabilities.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (entry.installed) ModuleBadge(stringResource(R.string.modules_already_installed), accent = true)
                    entry.qualityTags.forEach { ModuleBadge(stringResource(it), accent = true) }
                    entry.capabilities.forEach { ModuleBadge(stringResource(it)) }
                }
            }
            if (entry.expanded && entry.capabilities.isEmpty()) {
                Text(stringResource(R.string.modules_capabilities_pending), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!entry.supported) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(painterResource(R.drawable.error), contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                Text(stringResource(R.string.modules_unsupported_description), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            } else Button(onClick = install, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth(),
                enabled = model.busy == null && entry.download != null) {
                if (loading) LoadingIndicator(Modifier.size(ButtonDefaults.IconSize), color = LocalContentColor.current)
                else Icon(painterResource(if (entry.installed) R.drawable.update else R.drawable.download), null, Modifier.size(ButtonDefaults.IconSize))
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(if (loading) R.string.modules_loading else if (entry.installed) R.string.modules_check_update else R.string.modules_install))
            }
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
                            IconButton(onClick = dismiss, enabled = model.busy == null, shapes = IconButtonDefaults.shapes()) {
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
                                    isError = !editor.valid, enabled = model.busy == null, singleLine = true, modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium)
                            }
                            item(key = "author", contentType = "field") {
                                OutlinedTextField(editor.author,
                                    remember(onAction) { { onAction(ModuleSettingsAction.MetadataChanged(ModuleMetadataField.AUTHOR, it)) } },
                                    label = { Text(stringResource(R.string.modules_metadata_author)) },
                                    enabled = model.busy == null, singleLine = true, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                            }
                            item(key = "description", contentType = "field") {
                                OutlinedTextField(editor.description,
                                    remember(onAction) { { onAction(ModuleSettingsAction.MetadataChanged(ModuleMetadataField.DESCRIPTION, it)) } },
                                    label = { Text(stringResource(R.string.description)) },
                                    enabled = model.busy == null, minLines = 3, maxLines = 8, modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium)
                            }
                            if (model.busy != null) item(key = "progress", contentType = "progress") { LinearWavyProgressIndicator(Modifier.fillMaxWidth()) }
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
        ModuleSettingsDialog.MODULE_URL, ModuleSettingsDialog.CATALOG_URL -> Dialog(onDismissRequest = dismiss, properties = remember { DialogProperties(usePlatformDefaultWidth = false) }) {
            Scaffold(topBar = {
                TopAppBar(title = { Text(stringResource(if (model.dialog == ModuleSettingsDialog.CATALOG_URL) R.string.modules_add_catalog else R.string.modules_add_url)) },
                    navigationIcon = {
                        IconButton(onClick = dismiss, enabled = model.busy == null, shapes = IconButtonDefaults.shapes()) {
                            Icon(painterResource(R.drawable.close), stringResource(android.R.string.cancel))
                        }
                    })
            }) { padding ->
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
                    LazyColumn(Modifier.widthIn(max = ModuleSettingsLayout.ContentMaxWidth).fillMaxHeight().imePadding(), contentPadding = PaddingValues(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        item(key = "url", contentType = "field") {
                            OutlinedTextField(model.url, remember(onAction) { { onAction(ModuleSettingsAction.UrlChanged(it)) } }, label = { Text(stringResource(R.string.modules_url)) },
                                supportingText = { Text(stringResource(R.string.modules_url_hint)) }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                                leadingIcon = { Icon(painterResource(if (model.dialog == ModuleSettingsDialog.CATALOG_URL) R.drawable.language else R.drawable.link), null) },
                                keyboardOptions = remember { KeyboardOptions(keyboardType = KeyboardType.Uri) }, enabled = model.busy == null, isError = model.messageIsError,
                                shape = MaterialTheme.shapes.medium)
                        }
                        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction, moduleItemMotion()) }
                        item(key = "submit", contentType = "action") {
                            Button(onClick = remember(onAction) { { onAction(ModuleSettingsAction.SubmitUrl) } }, shapes = ButtonDefaults.shapes(),
                                modifier = Modifier.fillMaxWidth(), enabled = model.url.isNotBlank() && model.busy == null) { Text(stringResource(R.string.modules_continue)) }
                        }
                    }
                }
            }
        }
        ModuleSettingsDialog.REVIEW -> model.review?.let { review ->
            AlertDialog(onDismissRequest = dismiss,
                icon = {
                    ModuleMorphBadge(active = true, size = 56.dp) {
                        review.icon?.let { ModuleProviderIcon(it, size = 28.dp) } ?: Icon(painterResource(
                            if (review.catalogCount != null) R.drawable.language else R.drawable.extension), null, Modifier.size(24.dp))
                    }
                },
                title = { Text(stringResource(if (review.catalogCount != null) R.string.modules_catalog_review else R.string.modules_install_review)) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        item(key = "identity", contentType = "identity") {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(review.name.ifBlank { stringResource(R.string.modules_file_origin) }, style = MaterialTheme.typography.titleMediumEmphasized)
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
                        if (review.capabilities.isNotEmpty()) item(key = "capabilities", contentType = "capabilities") {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                review.capabilities.forEach { ModuleBadge(stringResource(it)) }
                            }
                        }
                        if (model.message != null || model.busy != null) item(key = "message", contentType = "message") { ModuleMessage(model, onAction, moduleItemMotion()) }
                    }
                }, confirmButton = {
                    Button(onClick = confirm, enabled = model.busy == null, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(if (review.currentVersion != null) R.string.modules_replace else R.string.modules_install))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss, enabled = model.busy == null, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } })
        }
        ModuleSettingsDialog.REMOVE_MODULE, ModuleSettingsDialog.REMOVE_CATALOG, ModuleSettingsDialog.DISCARD, ModuleSettingsDialog.DISCARD_METADATA -> {
            val removing = model.dialog == ModuleSettingsDialog.REMOVE_MODULE || model.dialog == ModuleSettingsDialog.REMOVE_CATALOG
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
            AlertDialog(onDismissRequest = dismiss,
                icon = { Icon(painterResource(if (removing) R.drawable.delete else R.drawable.edit), null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(title)) }, text = { Text(stringResource(description)) },
                confirmButton = {
                    Button(onClick = confirm, enabled = model.busy == null, shapes = ButtonDefaults.shapes(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError)) {
                        Text(stringResource(if (removing) R.string.modules_remove else R.string.modules_discard))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss, enabled = model.busy == null, shapes = ButtonDefaults.shapes()) { Text(stringResource(android.R.string.cancel)) } })
        }
        ModuleSettingsDialog.DIAGNOSTICS -> model.failure?.let { failure ->
            AlertDialog(
                onDismissRequest = dismiss,
                icon = { Icon(painterResource(R.drawable.error), null, tint = MaterialTheme.colorScheme.error) },
                title = { Text(stringResource(R.string.modules_diagnostics)) },
                text = {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        item(key = "stage", contentType = "stage") {
                            Text(stringResource(failure.stage), style = MaterialTheme.typography.titleSmallEmphasized)
                        }
                        item(key = "message", contentType = "message") {
                            model.message?.let { Text(stringResource(it)) }
                        }
                        item(key = "code", contentType = "code") {
                            Surface(shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
                                Text(failure.code, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.fillMaxWidth().padding(12.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.CopyDiagnostics) } }, shapes = ButtonDefaults.shapes()) {
                        Text(stringResource(R.string.modules_diagnostics_copy))
                    }
                },
                dismissButton = { TextButton(onClick = dismiss, shapes = ButtonDefaults.shapes()) { Text(stringResource(R.string.modules_back)) } },
            )
        }
        null -> Unit
    }
}

@Composable
internal fun ModuleMessage(model: ModuleSettingsUiModel, onAction: (ModuleSettingsAction) -> Unit, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (model.busy != null) LinearWavyProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 4.dp))
        model.message?.let { message ->
            val error = model.messageIsError
            Surface(color = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,
                contentColor = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer,
                shape = MaterialTheme.shapes.largeIncreased) {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(painterResource(if (error) R.drawable.error else R.drawable.info), contentDescription = null, modifier = Modifier.size(20.dp))
                    Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
                        Text(stringResource(message), style = MaterialTheme.typography.bodyMedium)
                        if (model.failure != null) TextButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.FailureDetails) } },
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp)) {
                            Text(stringResource(R.string.modules_show_details))
                        }
                    }
                    IconButton(onClick = remember(onAction) { { onAction(ModuleSettingsAction.ClearMessage) } }, shapes = IconButtonDefaults.shapes()) {
                        Icon(painterResource(R.drawable.close), stringResource(R.string.modules_dismiss))
                    }
                }
            }
        }
    }
}

@Composable
private fun ModuleEmptyArt(icon: Int, shape: RoundedPolygon, container: Color, content: Color) {
    val entrance = remember { Animatable(0f) }
    val spec = MaterialTheme.motionScheme.slowSpatialSpec<Float>()
    LaunchedEffect(Unit) { entrance.animateTo(1f, spec) }
    Box(Modifier.size(112.dp).graphicsLayer {
        val scale = lerp(0.6f, 1f, entrance.value)
        scaleX = scale
        scaleY = scale
        rotationZ = lerp(-60f, 0f, entrance.value)
    }.background(container, shape.toShape()), contentAlignment = Alignment.Center) {
        Icon(painterResource(icon), contentDescription = null, tint = content,
            modifier = Modifier.size(48.dp).graphicsLayer { rotationZ = lerp(60f, 0f, entrance.value) })
    }
}

@Composable
private fun ModuleEmpty(icon: Int, shape: RoundedPolygon, title: Int, description: Int, actionLabel: Int,
    onAction: (ModuleSettingsAction) -> Unit, action: ModuleSettingsAction, modifier: Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 32.dp), verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ModuleEmptyArt(icon, shape, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(title), style = MaterialTheme.typography.headlineSmallEmphasized, textAlign = TextAlign.Center)
        Text(stringResource(description), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center)
        Spacer(Modifier.height(4.dp))
        Button(onClick = remember(action, onAction) { { onAction(action) } }, shapes = ButtonDefaults.shapes()) { Text(stringResource(actionLabel)) }
    }
}

@Composable
private fun ModuleLoadError(message: Int, onAction: (ModuleSettingsAction) -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally) {
        ModuleEmptyArt(R.drawable.error, MaterialShapes.Cookie9Sided, MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.onErrorContainer)
        Text(stringResource(message), style = MaterialTheme.typography.bodyLarge, textAlign = TextAlign.Center)
        Button(onClick = remember(onAction) { { onAction(ModuleSettingsAction.Retry) } }, shapes = ButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.update), null, Modifier.size(ButtonDefaults.IconSize))
            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
            Text(stringResource(R.string.retry))
        }
    }
}

@Composable
private fun ModuleMenu(expanded: Boolean, enabled: Boolean, onOpen: () -> Unit, onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box {
        IconButton(onClick = onOpen, enabled = enabled, shapes = IconButtonDefaults.shapes()) {
            Icon(painterResource(R.drawable.more_vert), stringResource(R.string.more))
        }
        DropdownMenu(expanded, onDismiss, shape = MaterialTheme.shapes.large, content = content)
    }
}

@Composable
private fun ModuleMenuItem(label: Int, icon: Int, enabled: Boolean, onAction: (ModuleSettingsAction) -> Unit, action: ModuleSettingsAction) {
    DropdownMenuItem(text = { Text(stringResource(label)) }, enabled = enabled,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null, modifier = Modifier.size(20.dp)) },
        onClick = remember(action, onAction) { { onAction(ModuleSettingsAction.Menu(null)); onAction(action) } })
}
