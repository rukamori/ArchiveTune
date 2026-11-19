/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Badge
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.downloads.DownloadEntryUiModel
import moe.rukamori.archivetune.downloads.DownloadMediaType
import moe.rukamori.archivetune.downloads.DownloadQueueSummary
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.downloads.DownloadStorageSummary
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.DownloadLibraryEvent
import moe.rukamori.archivetune.viewmodels.DownloadLibraryScreenState
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySort
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortField
import moe.rukamori.archivetune.viewmodels.DownloadLibrarySortOrder
import moe.rukamori.archivetune.viewmodels.DownloadLibraryTab
import moe.rukamori.archivetune.viewmodels.DownloadLibraryViewModel
import moe.rukamori.archivetune.viewmodels.DownloadRemovalConfirmation

@Composable
fun DownloadLibraryScreen(
    navController: NavController,
    viewModel: DownloadLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, playerConnection) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadLibraryEvent.Message -> {
                    launch { snackbarHostState.showSnackbar(context.getString(event.messageRes)) }
                }

                is DownloadLibraryEvent.Navigate -> {
                    navController.navigate(event.route)
                }

                is DownloadLibraryEvent.PlayQueue -> {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = event.title ?: event.titleRes?.let(context::getString),
                            items = event.queue.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.startIndex,
                        ),
                    )
                }

                is DownloadLibraryEvent.PlayNext -> {
                    playerConnection?.let { connection ->
                        connection.playNext(event.items.map { metadata -> metadata.toMediaItem() })
                        launch { snackbarHostState.showSnackbar(context.getString(R.string.download_playing_next)) }
                    }
                }

                is DownloadLibraryEvent.AddToQueue -> {
                    playerConnection?.let { connection ->
                        connection.addToQueue(event.items.map { metadata -> metadata.toMediaItem() })
                        launch { snackbarHostState.showSnackbar(context.getString(R.string.download_added_to_queue)) }
                    }
                }
            }
        }
    }

    val actions =
        remember(viewModel, navController) {
            DownloadLibraryActions(
                onBack = navController::navigateUp,
                onBackToMain = navController::backToMain,
                onActivateSearch = viewModel::activateSearch,
                onCloseSearch = viewModel::closeSearch,
                onQueryChange = viewModel::updateQuery,
                onClearQuery = viewModel::clearQuery,
                onClearFilters = viewModel::clearFilters,
                onTabSelected = viewModel::selectTab,
                onTypeFilterSelected = viewModel::selectTypeFilter,
                onSortFieldSelected = viewModel::selectSortField,
                onSortOrderSelected = viewModel::selectSortOrder,
                onOpen = viewModel::open,
                onPlay = viewModel::play,
                onPlayChild = viewModel::playChild,
                onPlayNext = viewModel::playNext,
                onAddToQueue = viewModel::addToQueue,
                onPlayAll = viewModel::playAll,
                onPlaySection = viewModel::playSection,
                onPause = { entry -> viewModel.pause(entry) },
                onResume = { entry -> viewModel.resume(entry) },
                onCancel = { entry -> viewModel.remove(entry) },
                onRequestRemove = { entry -> viewModel.requestRemove(entry) },
                onPauseSection = { section -> viewModel.pause(section) },
                onResumeSection = { section -> viewModel.resume(section) },
                onRequestRemoveSection = { section -> viewModel.requestRemove(section) },
                onSelectSection = viewModel::selectSection,
                onToggleSelection = viewModel::toggleSelection,
                onToggleSelectAll = viewModel::toggleSelectAll,
                onClearSelection = viewModel::clearSelection,
                onPlaySelection = viewModel::playSelection,
                onPlaySelectionNext = viewModel::playSelectionNext,
                onAddSelectionToQueue = viewModel::addSelectionToQueue,
                onPauseSelection = viewModel::pauseSelection,
                onResumeSelection = viewModel::resumeSelection,
                onRequestRemoveSelection = viewModel::requestRemoveSelection,
                onPauseAll = viewModel::pauseAll,
                onResumeAll = viewModel::resumeAll,
                onRetryFailed = viewModel::retryFailed,
                onRequestCancelAll = viewModel::requestCancelAll,
                onConfirmRemove = viewModel::confirmRemove,
                onDismissRemove = viewModel::dismissRemoveConfirmation,
            )
        }

    DownloadLibraryScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        actions = actions,
    )
}

@Stable
private class DownloadLibraryActions(
    val onBack: () -> Unit,
    val onBackToMain: () -> Unit,
    val onActivateSearch: () -> Unit,
    val onCloseSearch: () -> Unit,
    val onQueryChange: (String) -> Unit,
    val onClearQuery: () -> Unit,
    val onClearFilters: () -> Unit,
    val onTabSelected: (DownloadLibraryTab) -> Unit,
    val onTypeFilterSelected: (DownloadMediaType?) -> Unit,
    val onSortFieldSelected: (DownloadLibrarySortField) -> Unit,
    val onSortOrderSelected: (DownloadLibrarySortOrder) -> Unit,
    val onOpen: (DownloadEntryUiModel) -> Unit,
    val onPlay: (DownloadEntryUiModel, Boolean) -> Unit,
    val onPlayChild: (DownloadEntryUiModel, DownloadEntryUiModel) -> Unit,
    val onPlayNext: (DownloadEntryUiModel) -> Unit,
    val onAddToQueue: (DownloadEntryUiModel) -> Unit,
    val onPlayAll: (Boolean) -> Unit,
    val onPlaySection: (DownloadSectionUiModel, Boolean) -> Unit,
    val onPause: (DownloadEntryUiModel) -> Unit,
    val onResume: (DownloadEntryUiModel) -> Unit,
    val onCancel: (DownloadEntryUiModel) -> Unit,
    val onRequestRemove: (DownloadEntryUiModel) -> Unit,
    val onPauseSection: (DownloadSectionUiModel) -> Unit,
    val onResumeSection: (DownloadSectionUiModel) -> Unit,
    val onRequestRemoveSection: (DownloadSectionUiModel) -> Unit,
    val onSelectSection: (DownloadSectionUiModel) -> Unit,
    val onToggleSelection: (DownloadEntryUiModel) -> Unit,
    val onToggleSelectAll: () -> Unit,
    val onClearSelection: () -> Unit,
    val onPlaySelection: (Boolean) -> Unit,
    val onPlaySelectionNext: () -> Unit,
    val onAddSelectionToQueue: () -> Unit,
    val onPauseSelection: () -> Unit,
    val onResumeSelection: () -> Unit,
    val onRequestRemoveSelection: () -> Unit,
    val onPauseAll: () -> Unit,
    val onResumeAll: () -> Unit,
    val onRetryFailed: () -> Unit,
    val onRequestCancelAll: () -> Unit,
    val onConfirmRemove: () -> Unit,
    val onDismissRemove: () -> Unit,
)

private enum class DownloadTopBarMode {
    DEFAULT,
    SEARCH,
    SELECTION,
}

@Composable
private fun DownloadLibraryScreenContent(
    state: DownloadLibraryScreenState,
    snackbarHostState: SnackbarHostState,
    actions: DownloadLibraryActions,
) {
    val controls = state.controls
    val success = state as? DownloadLibraryScreenState.Success
    val selectedEntries = success?.selectedEntries.orEmpty()
    val selectionActive = selectedEntries.isNotEmpty()
    val tabs = remember { DownloadLibraryTab.entries }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(controls.selectedTab), pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val topBarEnterSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Float>()
    val topBarExitSpec = MaterialTheme.motionScheme.fastEffectsSpec<Float>()
    val topBarMode =
        when {
            selectionActive -> DownloadTopBarMode.SELECTION
            controls.isSearchActive -> DownloadTopBarMode.SEARCH
            else -> DownloadTopBarMode.DEFAULT
        }

    BackHandler(enabled = selectionActive, onBack = actions.onClearSelection)
    BackHandler(enabled = !selectionActive && controls.isSearchActive, onBack = actions.onCloseSearch)

    LaunchedEffect(controls.selectedTab) {
        val targetPage = tabs.indexOf(controls.selectedTab)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> actions.onTabSelected(tabs[page]) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.surface)) {
                AnimatedContent(
                    targetState = topBarMode,
                    transitionSpec = {
                        fadeIn(topBarEnterSpec) togetherWith fadeOut(topBarExitSpec)
                    },
                    label = "downloadLibraryTopBar",
                ) { mode ->
                    when (mode) {
                        DownloadTopBarMode.SELECTION -> {
                            DownloadSelectionTopBar(
                                count = selectedEntries.size,
                                allSelected = success?.allVisibleSelected == true,
                                onClearSelection = actions.onClearSelection,
                                onToggleSelectAll = actions.onToggleSelectAll,
                            )
                        }

                        DownloadTopBarMode.SEARCH -> {
                            DownloadSearchField(
                                query = controls.query,
                                onQueryChange = actions.onQueryChange,
                                onClearQuery = actions.onClearQuery,
                                onClose = actions.onCloseSearch,
                            )
                        }

                        DownloadTopBarMode.DEFAULT -> {
                            DownloadDefaultTopBar(
                                storage = success?.library?.storage,
                                queue = success?.library?.queue,
                                scrollBehavior = scrollBehavior,
                                onBack = actions.onBack,
                                onBackToMain = actions.onBackToMain,
                                onActivateSearch = actions.onActivateSearch,
                            )
                        }
                    }
                }
                AnimatedVisibility(
                    visible = !selectionActive,
                    enter = expandVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) + fadeIn(),
                    exit = shrinkVertically(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(),
                ) {
                    Column(modifier = Modifier.padding(bottom = 8.dp)) {
                        DownloadViewSwitcher(
                            selectedPage = pagerState.currentPage,
                            activeCount = success?.library?.queue?.activeCount ?: 0,
                            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                        DownloadFilterRow(
                            sort = controls.sort,
                            typeFilter = controls.typeFilter,
                            onSortFieldSelected = actions.onSortFieldSelected,
                            onSortOrderSelected = actions.onSortOrderSelected,
                            onTypeFilterSelected = actions.onTypeFilterSelected,
                        )
                    }
                }
            }
        },
        snackbarHost = {
            SnackbarHost(
                hostState = snackbarHostState,
                modifier =
                    Modifier.windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ),
            )
        },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (state) {
                is DownloadLibraryScreenState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(innerPadding),
                        contentAlignment = Alignment.Center,
                    ) {
                        ContainedLoadingIndicator()
                    }
                }

                is DownloadLibraryScreenState.Error -> {
                    DownloadEmptyState(
                        icon = R.drawable.error,
                        title = stringResource(state.messageRes),
                        body = null,
                        actionLabel = null,
                        onAction = null,
                        modifier = Modifier.padding(innerPadding),
                    )
                }

                is DownloadLibraryScreenState.Empty,
                is DownloadLibraryScreenState.Success,
                -> {
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.fillMaxSize(),
                        userScrollEnabled = !selectionActive,
                        verticalAlignment = Alignment.Top,
                    ) { page ->
                        DownloadPage(
                            tab = tabs[page],
                            state = state,
                            contentPadding = innerPadding,
                            selectionActive = selectionActive,
                            actions = actions,
                        )
                    }
                }
            }

            AnimatedVisibility(
                visible = selectionActive,
                modifier =
                    Modifier
                        .align(Alignment.BottomCenter)
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                        ).padding(16.dp),
                enter = slideInVertically(MaterialTheme.motionScheme.defaultSpatialSpec()) { it } + fadeIn(),
                exit = slideOutVertically(MaterialTheme.motionScheme.fastSpatialSpec()) { it } + fadeOut(),
            ) {
                DownloadSelectionToolbar(
                    inProgress = controls.selectedTab == DownloadLibraryTab.PROGRESS,
                    actions = actions,
                )
            }
        }
    }

    controls.pendingRemoval?.let { confirmation ->
        DownloadRemovalDialog(
            confirmation = confirmation,
            onConfirm = actions.onConfirmRemove,
            onDismiss = actions.onDismissRemove,
        )
    }
}

@Composable
private fun DownloadDefaultTopBar(
    storage: DownloadStorageSummary?,
    queue: DownloadQueueSummary?,
    scrollBehavior: TopAppBarScrollBehavior,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    onActivateSearch: () -> Unit,
) {
    val subtitle =
        when {
            queue != null && queue.activeCount > 0 -> {
                pluralStringResource(R.plurals.n_downloads_active, queue.activeCount, queue.activeCount)
            }

            storage != null && storage.songCount > 0 -> {
                stringResource(R.string.download_storage_used, formatFileSize(storage.bytes))
            }

            else -> {
                null
            }
        }

    MediumFlexibleTopAppBar(
        title = {
            Text(
                text = stringResource(R.string.downloads),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        subtitle =
            subtitle?.let { text ->
                {
                    Text(
                        text = text,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
        navigationIcon = {
            moe.rukamori.archivetune.ui.component.IconButton(
                onClick = onBack,
                onLongClick = onBackToMain,
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        },
        actions = {
            IconButton(onClick = onActivateSearch) {
                Icon(
                    painter = painterResource(R.drawable.search),
                    contentDescription = stringResource(R.string.search_downloads),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        scrollBehavior = scrollBehavior,
    )
}

@Composable
private fun DownloadSelectionTopBar(
    count: Int,
    allSelected: Boolean,
    onClearSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
) {
    TopAppBar(
        title = {
            Text(
                text = pluralStringResource(R.plurals.n_selected, count, count),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    painter = painterResource(R.drawable.close),
                    contentDescription = stringResource(R.string.clear_selection),
                )
            }
        },
        actions = {
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    painter = painterResource(if (allSelected) R.drawable.deselect else R.drawable.select_all),
                    contentDescription = stringResource(if (allSelected) R.string.clear_selection else R.string.select),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface,
            ),
    )
}

@Composable
private fun DownloadSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onClose: () -> Unit,
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(focusRequester) {
        focusRequester.requestFocus()
    }

    TextField(
        value = query,
        onValueChange = onQueryChange,
        modifier =
            Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .focusRequester(focusRequester),
        placeholder = { Text(stringResource(R.string.search_downloads)) },
        leadingIcon = {
            IconButton(onClick = onClose) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        },
        trailingIcon =
            if (query.isNotEmpty()) {
                {
                    IconButton(onClick = onClearQuery) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.clear),
                        )
                    }
                }
            } else {
                null
            },
        singleLine = true,
        shape = CircleShape,
        colors =
            TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { keyboardController?.hide() }),
    )
}

@Composable
private fun DownloadViewSwitcher(
    selectedPage: Int,
    activeCount: Int,
    onSelect: (Int) -> Unit,
) {
    val tabs = DownloadLibraryTab.entries
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
    ) {
        tabs.forEachIndexed { index, tab ->
            val checked = selectedPage == index
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelect(index) },
                shapes =
                    if (index == 0) {
                        ButtonGroupDefaults.connectedLeadingButtonShapes()
                    } else {
                        ButtonGroupDefaults.connectedTrailingButtonShapes()
                    },
                modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
            ) {
                Icon(
                    painter = painterResource(if (tab == DownloadLibraryTab.DOWNLOADED) R.drawable.offline else R.drawable.download),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(if (tab == DownloadLibraryTab.DOWNLOADED) R.string.filter_downloaded else R.string.download_in_progress),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (tab == DownloadLibraryTab.PROGRESS && activeCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Badge(
                        containerColor = if (checked) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary,
                        contentColor = if (checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onPrimary,
                    ) {
                        Text(stringResource(R.string.download_count, activeCount))
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadFilterRow(
    sort: DownloadLibrarySort,
    typeFilter: DownloadMediaType?,
    onSortFieldSelected: (DownloadLibrarySortField) -> Unit,
    onSortOrderSelected: (DownloadLibrarySortOrder) -> Unit,
    onTypeFilterSelected: (DownloadMediaType?) -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DownloadSortChip(
            sort = sort,
            onSortFieldSelected = onSortFieldSelected,
            onSortOrderSelected = onSortOrderSelected,
        )
        VerticalDivider(modifier = Modifier.height(24.dp))
        DownloadMediaType.entries.forEach { type ->
            val selected = typeFilter == type
            FilterChip(
                selected = selected,
                onClick = { onTypeFilterSelected(if (selected) null else type) },
                label = { Text(stringResource(type.titleRes())) },
                leadingIcon = {
                    Icon(
                        painter = painterResource(if (selected) R.drawable.check else type.iconRes()),
                        contentDescription = null,
                        modifier = Modifier.size(FilterChipDefaults.IconSize),
                    )
                },
            )
        }
    }
}

@Composable
private fun DownloadSortChip(
    sort: DownloadLibrarySort,
    onSortFieldSelected: (DownloadLibrarySortField) -> Unit,
    onSortOrderSelected: (DownloadLibrarySortOrder) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val sortLabel = stringResource(R.string.sort_downloads)
    val fieldLabel = stringResource(sort.field.labelRes())
    val orderLabel = stringResource(sort.order.labelRes())

    Box {
        AssistChip(
            onClick = { expanded = true },
            label = { Text(fieldLabel) },
            leadingIcon = {
                Icon(
                    painter = painterResource(sort.order.iconRes()),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            trailingIcon = {
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(AssistChipDefaults.IconSize),
                )
            },
            modifier = Modifier.semantics { contentDescription = "$sortLabel, $fieldLabel, $orderLabel" },
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DownloadLibrarySortField.entries.forEach { field ->
                DownloadSortMenuItem(
                    label = stringResource(field.labelRes()),
                    icon = if (field == DownloadLibrarySortField.NAME) R.drawable.text_fields else R.drawable.calendar_today,
                    selected = sort.field == field,
                    onClick = {
                        onSortFieldSelected(field)
                        expanded = false
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DownloadLibrarySortOrder.entries.forEach { order ->
                DownloadSortMenuItem(
                    label = stringResource(order.labelRes()),
                    icon = order.iconRes(),
                    selected = sort.order == order,
                    onClick = {
                        onSortOrderSelected(order)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun DownloadSortMenuItem(
    label: String,
    icon: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        trailingIcon =
            if (selected) {
                { Icon(painterResource(R.drawable.check), contentDescription = null) }
            } else {
                null
            },
        modifier = Modifier.semantics { this.selected = selected },
    )
}

@Immutable
private sealed interface DownloadRow {
    val key: String

    data class Entry(
        val entry: DownloadEntryUiModel,
    ) : DownloadRow {
        override val key: String = entry.id
    }

    data class Child(
        val parent: DownloadEntryUiModel,
        val child: DownloadEntryUiModel,
    ) : DownloadRow {
        override val key: String = "child:${parent.id}:${child.id}"
    }
}

@Composable
private fun DownloadPage(
    tab: DownloadLibraryTab,
    state: DownloadLibraryScreenState,
    contentPadding: PaddingValues,
    selectionActive: Boolean,
    actions: DownloadLibraryActions,
) {
    val controls = state.controls
    val success = state as? DownloadLibraryScreenState.Success
    val sections = success?.sectionsFor(tab).orEmpty()
    val inProgress = tab == DownloadLibraryTab.PROGRESS
    val layoutDirection = LocalLayoutDirection.current
    val playerPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    var expandedIds by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val itemModifier = Modifier.fillMaxWidth().widthIn(max = 840.dp)

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = contentPadding.calculateTopPadding() + 8.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = playerPadding.calculateBottomPadding() + if (selectionActive) 112.dp else 24.dp,
            ),
    ) {
        val storage = success?.library?.storage
        if (!inProgress && storage != null && storage.songCount > 0 && controls.query.isBlank() && !selectionActive) {
            item(key = "overview", contentType = CONTENT_TYPE_OVERVIEW) {
                DownloadedOverview(
                    storage = storage,
                    onPlayAll = { actions.onPlayAll(false) },
                    onShuffleAll = { actions.onPlayAll(true) },
                    modifier = itemModifier.padding(bottom = 16.dp).animateItem(),
                )
            }
        }
        val queue = success?.library?.queue
        if (inProgress && queue != null && queue.activeCount > 0 && !selectionActive) {
            item(key = "queue", contentType = CONTENT_TYPE_OVERVIEW) {
                DownloadQueueOverview(
                    queue = queue,
                    onPauseAll = actions.onPauseAll,
                    onResumeAll = actions.onResumeAll,
                    onRetryFailed = actions.onRetryFailed,
                    onCancelAll = actions.onRequestCancelAll,
                    modifier = itemModifier.padding(bottom = 16.dp).animateItem(),
                )
            }
        }

        if (sections.isEmpty()) {
            item(key = "empty", contentType = CONTENT_TYPE_EMPTY) {
                when {
                    controls.hasActiveFilter -> {
                        DownloadEmptyState(
                            icon = R.drawable.search,
                            title = stringResource(R.string.no_matching_downloads),
                            body = stringResource(R.string.no_matching_downloads_body),
                            actionLabel = stringResource(R.string.clear_filters),
                            onAction = actions.onClearFilters,
                            modifier = Modifier.animateItem(),
                        )
                    }

                    inProgress -> {
                        DownloadEmptyState(
                            icon = R.drawable.download,
                            title = stringResource(R.string.no_downloads_in_progress),
                            body = stringResource(R.string.no_downloads_in_progress_body),
                            actionLabel = if (storage != null && storage.songCount > 0) stringResource(R.string.view_downloaded) else null,
                            onAction = { actions.onTabSelected(DownloadLibraryTab.DOWNLOADED) },
                            modifier = Modifier.animateItem(),
                        )
                    }

                    else -> {
                        DownloadEmptyState(
                            icon = R.drawable.offline,
                            title = stringResource(R.string.no_downloads),
                            body = stringResource(R.string.no_downloads_body),
                            actionLabel = null,
                            onAction = null,
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            }
        }

        sections.forEach { section ->
            item(key = "header:${section.mediaType}", contentType = CONTENT_TYPE_SECTION_HEADER) {
                DownloadSectionHeader(
                    section = section,
                    inProgress = inProgress,
                    selectionActive = selectionActive,
                    actions = actions,
                    modifier = itemModifier.animateItem(),
                )
            }
            val rows = section.rows(if (inProgress || selectionActive) emptyList() else expandedIds)
            itemsIndexed(
                items = rows,
                key = { _, row -> row.key },
                contentType = { _, row -> if (row is DownloadRow.Child) CONTENT_TYPE_CHILD else CONTENT_TYPE_ENTRY },
            ) { index, row ->
                val rowModifier = itemModifier.padding(bottom = ListItemDefaults.SegmentedGap).animateItem()
                when (row) {
                    is DownloadRow.Entry -> {
                        val selected = row.entry.id in controls.selection
                        if (inProgress) {
                            ProgressEntryRow(
                                entry = row.entry,
                                index = index,
                                count = rows.size,
                                selected = selected,
                                selectionActive = selectionActive,
                                actions = actions,
                                modifier = rowModifier,
                            )
                        } else {
                            val expanded = row.entry.id in expandedIds
                            DownloadedEntryRow(
                                entry = row.entry,
                                index = index,
                                count = rows.size,
                                selected = selected,
                                selectionActive = selectionActive,
                                expanded = expanded,
                                onToggleExpanded = {
                                    expandedIds = if (expanded) expandedIds - row.entry.id else expandedIds + row.entry.id
                                },
                                actions = actions,
                                modifier = rowModifier,
                            )
                        }
                    }

                    is DownloadRow.Child -> {
                        DownloadChildRow(
                            parent = row.parent,
                            child = row.child,
                            index = index,
                            count = rows.size,
                            onPlay = actions.onPlayChild,
                            onRemove = actions.onRequestRemove,
                            modifier = rowModifier,
                        )
                    }
                }
            }
            item(key = "spacer:${section.mediaType}", contentType = CONTENT_TYPE_SECTION_SPACER) {
                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun DownloadedOverview(
    storage: DownloadStorageSummary,
    onPlayAll: () -> Unit,
    onShuffleAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.download_meta_separator)
    val details =
        buildList {
            add(stringResource(R.string.download_storage_used, formatFileSize(storage.bytes)))
            if (storage.albumCount > 0) add(pluralStringResource(R.plurals.n_album, storage.albumCount, storage.albumCount))
            if (storage.playlistCount > 0) {
                add(pluralStringResource(R.plurals.n_playlist, storage.playlistCount, storage.playlistCount))
            }
        }.joinToString(separator)
    val badgeShape = MaterialShapes.Cookie6Sided.toShape()
    val buttonHeight = ButtonDefaults.MediumContainerHeight

    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier =
                        Modifier
                            .size(56.dp)
                            .clip(badgeShape)
                            .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.n_songs_offline, storage.songCount, storage.songCount),
                        style = MaterialTheme.typography.titleLargeEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onPlayAll,
                    modifier = Modifier.weight(1f).heightIn(min = buttonHeight),
                    shapes = ButtonDefaults.shapesFor(buttonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                    )
                    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(buttonHeight)))
                    Text(
                        text = stringResource(R.string.play_all),
                        style = ButtonDefaults.textStyleFor(buttonHeight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                FilledTonalButton(
                    onClick = onShuffleAll,
                    modifier = Modifier.weight(1f).heightIn(min = buttonHeight),
                    shapes = ButtonDefaults.shapesFor(buttonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(buttonHeight, hasStartIcon = true),
                    colors =
                        ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                            contentColor = MaterialTheme.colorScheme.onSurface,
                        ),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(buttonHeight)),
                    )
                    Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(buttonHeight)))
                    Text(
                        text = stringResource(R.string.shuffle),
                        style = ButtonDefaults.textStyleFor(buttonHeight),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadQueueOverview(
    queue: DownloadQueueSummary,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onRetryFailed: () -> Unit,
    onCancelAll: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier.size(64.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularWavyProgressIndicator(
                        progress = { queue.progress },
                        modifier = Modifier.fillMaxSize(),
                        amplitude = { if (queue.paused) 0f else 1f },
                    )
                    Text(
                        text = stringResource(R.string.download_progress_percent, queue.percent),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Text(
                        text = pluralStringResource(R.plurals.n_downloads_active, queue.activeCount, queue.activeCount),
                        style = MaterialTheme.typography.titleMediumEmphasized,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text =
                            if (queue.paused) {
                                stringResource(R.string.download_paused_state)
                            } else {
                                stringResource(R.string.download_speed, formatFileSize(queue.speedBytesPerSecond))
                            },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (queue.failedSongIds.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(R.plurals.n_downloads_failed, queue.failedSongIds.size, queue.failedSongIds.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = if (queue.paused) onResumeAll else onPauseAll,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(
                        painter = painterResource(if (queue.paused) R.drawable.play else R.drawable.pause),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(if (queue.paused) R.string.resume_all_downloads else R.string.pause_all_downloads))
                }
                if (queue.failedSongIds.isNotEmpty() && !queue.paused) {
                    OutlinedButton(
                        onClick = onRetryFailed,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.replay),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                        Text(stringResource(R.string.retry_failed_downloads))
                    }
                }
                TextButton(
                    onClick = onCancelAll,
                    shapes = ButtonDefaults.shapes(),
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.close),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.cancel_all_downloads))
                }
            }
        }
    }
}

@Composable
private fun DownloadSectionHeader(
    section: DownloadSectionUiModel,
    inProgress: Boolean,
    selectionActive: Boolean,
    actions: DownloadLibraryActions,
    modifier: Modifier = Modifier,
) {
    val separator = stringResource(R.string.download_meta_separator)
    val summary =
        if (inProgress) {
            if (section.paused) {
                stringResource(R.string.download_paused_progress, section.percent)
            } else {
                stringResource(R.string.download_progress_summary, section.percent, formatFileSize(section.speedBytesPerSecond))
            }
        } else {
            listOf(section.countText(), formatFileSize(section.entries.sumOf(DownloadEntryUiModel::sizeBytes))).joinToString(separator)
        }

    Row(
        modifier = modifier.padding(start = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(section.mediaType.iconRes()),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(section.mediaType.titleRes()),
                style = MaterialTheme.typography.titleMediumEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = summary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (!selectionActive) {
            DownloadSectionMenu(
                section = section,
                inProgress = inProgress,
                actions = actions,
            )
        }
    }
}

@Composable
private fun DownloadSectionMenu(
    section: DownloadSectionUiModel,
    inProgress: Boolean,
    actions: DownloadLibraryActions,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.more_options),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            if (inProgress) {
                DownloadMenuAction(
                    icon = if (section.paused) R.drawable.play else R.drawable.pause,
                    label = stringResource(if (section.paused) R.string.resume_all_downloads else R.string.pause_all_downloads),
                    onClick = {
                        expanded = false
                        if (section.paused) actions.onResumeSection(section) else actions.onPauseSection(section)
                    },
                )
            } else {
                DownloadMenuAction(
                    icon = R.drawable.play,
                    label = stringResource(R.string.play),
                    onClick = {
                        expanded = false
                        actions.onPlaySection(section, false)
                    },
                )
                DownloadMenuAction(
                    icon = R.drawable.shuffle,
                    label = stringResource(R.string.shuffle),
                    onClick = {
                        expanded = false
                        actions.onPlaySection(section, true)
                    },
                )
            }
            DownloadMenuAction(
                icon = R.drawable.select_all,
                label = stringResource(R.string.select),
                onClick = {
                    expanded = false
                    actions.onSelectSection(section)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DownloadMenuAction(
                icon = if (inProgress) R.drawable.close else R.drawable.delete,
                label = stringResource(if (inProgress) R.string.cancel_all_downloads else R.string.remove_all_downloads),
                destructive = true,
                onClick = {
                    expanded = false
                    actions.onRequestRemoveSection(section)
                },
            )
        }
    }
}

@Composable
private fun DownloadMenuAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
    destructive: Boolean = false,
) {
    DropdownMenuItem(
        text = { Text(label) },
        onClick = onClick,
        leadingIcon = { Icon(painterResource(icon), contentDescription = null) },
        colors =
            if (destructive) {
                MenuDefaults.itemColors(
                    textColor = MaterialTheme.colorScheme.error,
                    leadingIconColor = MaterialTheme.colorScheme.error,
                )
            } else {
                MenuDefaults.itemColors()
            },
    )
}

@Composable
private fun DownloadedEntryRow(
    entry: DownloadEntryUiModel,
    index: Int,
    count: Int,
    selected: Boolean,
    selectionActive: Boolean,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    actions: DownloadLibraryActions,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        selected = selected,
        onClick = { if (selectionActive) actions.onToggleSelection(entry) else actions.onOpen(entry) },
        onLongClick = { actions.onToggleSelection(entry) },
        onLongClickLabel = stringResource(R.string.select_download),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        leadingContent = {
            DownloadArtwork(
                thumbnailUrl = entry.thumbnailUrl,
                placeholder = entry.placeholderIcon(),
                selected = selected,
                size = 56.dp,
            )
        },
        supportingContent = {
            Text(
                text = entry.downloadedMeta(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent =
            if (selectionActive) {
                null
            } else {
                {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (entry.children.isNotEmpty()) {
                            val rotation by animateFloatAsState(
                                targetValue = if (expanded) 180f else 0f,
                                animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                label = "downloadExpandRotation",
                            )
                            IconButton(onClick = onToggleExpanded) {
                                Icon(
                                    painter = painterResource(R.drawable.expand_more),
                                    contentDescription =
                                        stringResource(if (expanded) R.string.hide_download_tracks else R.string.show_download_tracks),
                                    modifier = Modifier.graphicsLayer { rotationZ = rotation },
                                )
                            }
                        }
                        DownloadEntryMenu(
                            entry = entry,
                            actions = actions,
                        )
                    }
                }
            },
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadEntryMenu(
    entry: DownloadEntryUiModel,
    actions: DownloadLibraryActions,
) {
    var expanded by remember { mutableStateOf(false) }
    val isCollection = entry.destinationRoute != null

    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.more_options),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            DownloadMenuAction(
                icon = R.drawable.play,
                label = stringResource(R.string.play),
                onClick = {
                    expanded = false
                    actions.onPlay(entry, false)
                },
            )
            if (isCollection) {
                DownloadMenuAction(
                    icon = R.drawable.shuffle,
                    label = stringResource(R.string.shuffle),
                    onClick = {
                        expanded = false
                        actions.onPlay(entry, true)
                    },
                )
            }
            DownloadMenuAction(
                icon = R.drawable.playlist_play,
                label = stringResource(R.string.play_next),
                onClick = {
                    expanded = false
                    actions.onPlayNext(entry)
                },
            )
            DownloadMenuAction(
                icon = R.drawable.queue_music,
                label = stringResource(R.string.add_to_queue),
                onClick = {
                    expanded = false
                    actions.onAddToQueue(entry)
                },
            )
            DownloadMenuAction(
                icon = R.drawable.select_all,
                label = stringResource(R.string.select_download),
                onClick = {
                    expanded = false
                    actions.onToggleSelection(entry)
                },
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DownloadMenuAction(
                icon = R.drawable.delete,
                label = stringResource(R.string.remove_download),
                destructive = true,
                onClick = {
                    expanded = false
                    actions.onRequestRemove(entry)
                },
            )
        }
    }
}

@Composable
private fun DownloadChildRow(
    parent: DownloadEntryUiModel,
    child: DownloadEntryUiModel,
    index: Int,
    count: Int,
    onPlay: (DownloadEntryUiModel, DownloadEntryUiModel) -> Unit,
    onRemove: (DownloadEntryUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    SegmentedListItem(
        selected = false,
        onClick = { onPlay(parent, child) },
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(16.dp))
                DownloadArtwork(
                    thumbnailUrl = child.thumbnailUrl,
                    placeholder = R.drawable.music_note,
                    selected = false,
                    size = 40.dp,
                )
            }
        },
        supportingContent = {
            Text(
                text = child.downloadedMeta(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            IconButton(onClick = { onRemove(child) }) {
                Icon(
                    painter = painterResource(R.drawable.delete),
                    contentDescription = stringResource(R.string.remove_download),
                )
            }
        },
    ) {
        Text(
            text = child.title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ProgressEntryRow(
    entry: DownloadEntryUiModel,
    index: Int,
    count: Int,
    selected: Boolean,
    selectionActive: Boolean,
    actions: DownloadLibraryActions,
    modifier: Modifier = Modifier,
) {
    val status =
        when {
            entry.failed -> stringResource(R.string.download_failed_state)
            entry.paused -> stringResource(R.string.download_paused_progress, entry.percent)
            entry.percent == 0 && entry.speedBytesPerSecond == 0L -> stringResource(R.string.download_waiting_state)
            else -> stringResource(R.string.download_progress_summary, entry.percent, formatFileSize(entry.speedBytesPerSecond))
        }
    val indicatorColor = if (entry.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

    SegmentedListItem(
        selected = selected,
        onClick = { if (selectionActive) actions.onToggleSelection(entry) else actions.onOpen(entry) },
        onLongClick = { actions.onToggleSelection(entry) },
        onLongClickLabel = stringResource(R.string.select_download),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
            ),
        leadingContent = {
            DownloadArtwork(
                thumbnailUrl = entry.thumbnailUrl,
                placeholder = entry.placeholderIcon(),
                selected = selected,
                size = 56.dp,
            )
        },
        supportingContent = {
            Column(
                modifier = Modifier.padding(top = 2.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                entry.supportingText?.let { supportingText ->
                    Text(
                        text = supportingText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(
                    text = status,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (entry.failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                LinearWavyProgressIndicator(
                    progress = { entry.progress },
                    modifier = Modifier.fillMaxWidth(),
                    color = indicatorColor,
                    amplitude = { if (entry.paused || entry.failed) 0f else 1f },
                )
            }
        },
        trailingContent =
            if (selectionActive) {
                null
            } else {
                {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val halted = entry.paused || entry.failed
                        FilledTonalIconButton(
                            onClick = { if (halted) actions.onResume(entry) else actions.onPause(entry) },
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        when {
                                            entry.failed -> R.drawable.replay
                                            entry.paused -> R.drawable.play
                                            else -> R.drawable.pause
                                        },
                                    ),
                                contentDescription =
                                    stringResource(
                                        when {
                                            entry.failed -> R.string.retry
                                            entry.paused -> R.string.resume_download
                                            else -> R.string.pause_download
                                        },
                                    ),
                                modifier = Modifier.size(20.dp),
                            )
                        }
                        IconButton(onClick = { actions.onCancel(entry) }) {
                            Icon(
                                painter = painterResource(R.drawable.close),
                                contentDescription = stringResource(R.string.cancel_download),
                            )
                        }
                    }
                }
            },
    ) {
        Text(
            text = entry.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun DownloadArtwork(
    thumbnailUrl: String?,
    placeholder: Int,
    selected: Boolean,
    size: Dp,
) {
    val selectedShape = MaterialShapes.Cookie9Sided.toShape()

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center,
    ) {
        AsyncImage(
            model = thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(placeholder),
            error = painterResource(placeholder),
            modifier = Modifier.fillMaxSize().clip(MaterialTheme.shapes.medium),
        )
        AnimatedVisibility(
            visible = selected,
            enter = scaleIn(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeIn(MaterialTheme.motionScheme.fastEffectsSpec()),
            exit = scaleOut(MaterialTheme.motionScheme.fastSpatialSpec()) + fadeOut(MaterialTheme.motionScheme.fastEffectsSpec()),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .clip(selectedShape)
                        .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.check),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(size * 0.45f),
                )
            }
        }
    }
}

@Composable
private fun DownloadEmptyState(
    icon: Int,
    title: String,
    body: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val shape = MaterialShapes.Cookie9Sided.toShape()

    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(96.dp)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
        )
        body?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (actionLabel != null && onAction != null) {
            FilledTonalButton(
                onClick = onAction,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
private fun DownloadSelectionToolbar(
    inProgress: Boolean,
    actions: DownloadLibraryActions,
) {
    HorizontalFloatingToolbar(
        expanded = true,
        floatingActionButton = {
            FloatingToolbarDefaults.VibrantFloatingActionButton(
                onClick = actions.onRequestRemoveSelection,
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            ) {
                Icon(
                    painter = painterResource(if (inProgress) R.drawable.close else R.drawable.delete),
                    contentDescription = stringResource(if (inProgress) R.string.cancel_downloads_action else R.string.remove_download),
                )
            }
        },
        colors =
            FloatingToolbarDefaults.standardFloatingToolbarColors(
                toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
    ) {
        if (inProgress) {
            DownloadToolbarAction(R.drawable.pause, stringResource(R.string.pause_download), actions.onPauseSelection)
            DownloadToolbarAction(R.drawable.play, stringResource(R.string.resume_download), actions.onResumeSelection)
        } else {
            DownloadToolbarAction(R.drawable.play, stringResource(R.string.play)) { actions.onPlaySelection(false) }
            DownloadToolbarAction(R.drawable.shuffle, stringResource(R.string.shuffle)) { actions.onPlaySelection(true) }
            DownloadToolbarAction(R.drawable.playlist_play, stringResource(R.string.play_next), actions.onPlaySelectionNext)
            DownloadToolbarAction(R.drawable.queue_music, stringResource(R.string.add_to_queue), actions.onAddSelectionToQueue)
        }
    }
}

@Composable
private fun DownloadToolbarAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = label,
        )
    }
}

@Composable
private fun DownloadRemovalDialog(
    confirmation: DownloadRemovalConfirmation,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cancels = confirmation.cancelsActiveDownloads
    val message =
        when (confirmation) {
            is DownloadRemovalConfirmation.Entry -> {
                stringResource(R.string.remove_download_confirm)
            }

            is DownloadRemovalConfirmation.Section -> {
                stringResource(if (cancels) R.string.cancel_download_section_confirm else R.string.remove_download_section_confirm)
            }

            is DownloadRemovalConfirmation.Selection -> {
                val count = confirmation.entries.size
                pluralStringResource(
                    if (cancels) R.plurals.cancel_downloads_selection_confirm else R.plurals.remove_downloads_selection_confirm,
                    count,
                    count,
                )
            }

            is DownloadRemovalConfirmation.Queue -> {
                stringResource(R.string.cancel_all_downloads_confirm)
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                painter = painterResource(if (cancels) R.drawable.close else R.drawable.delete),
                contentDescription = null,
            )
        },
        title = { Text(stringResource(if (cancels) R.string.cancel_downloads_title else R.string.remove_download)) },
        text = { Text(message) },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
            ) {
                Text(stringResource(if (cancels) R.string.cancel_downloads_action else R.string.delete))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(if (cancels) R.string.keep_downloading else android.R.string.cancel))
            }
        },
    )
}

@Composable
private fun DownloadEntryUiModel.downloadedMeta(): String {
    val separator = stringResource(R.string.download_meta_separator)
    return buildList {
        supportingText?.let(::add)
        if (destinationRoute != null) add(pluralStringResource(R.plurals.n_song, totalCount, totalCount))
        durationSeconds?.let { seconds -> add(makeTimeString(seconds * 1_000L)) }
        if (sizeBytes > 0L) add(formatFileSize(sizeBytes))
    }.joinToString(separator)
}

@Composable
private fun DownloadSectionUiModel.countText(): String =
    when (mediaType) {
        DownloadMediaType.PLAYLIST -> pluralStringResource(R.plurals.n_playlist, entries.size, entries.size)
        DownloadMediaType.ALBUM -> pluralStringResource(R.plurals.n_album, entries.size, entries.size)
        DownloadMediaType.SONG -> pluralStringResource(R.plurals.n_song, entries.size, entries.size)
    }

private fun DownloadSectionUiModel.rows(expandedIds: List<String>): List<DownloadRow> =
    buildList {
        entries.forEach { entry ->
            add(DownloadRow.Entry(entry))
            if (entry.id in expandedIds) {
                entry.children.forEach { child -> add(DownloadRow.Child(parent = entry, child = child)) }
            }
        }
    }

private fun DownloadMediaType.titleRes(): Int =
    when (this) {
        DownloadMediaType.PLAYLIST -> R.string.playlists
        DownloadMediaType.ALBUM -> R.string.albums
        DownloadMediaType.SONG -> R.string.songs
    }

private fun DownloadMediaType.iconRes(): Int =
    when (this) {
        DownloadMediaType.PLAYLIST -> R.drawable.queue_music
        DownloadMediaType.ALBUM -> R.drawable.album
        DownloadMediaType.SONG -> R.drawable.music_note
    }

private fun DownloadLibrarySortField.labelRes(): Int =
    when (this) {
        DownloadLibrarySortField.NAME -> R.string.sort_by_name
        DownloadLibrarySortField.DATE_MODIFIED -> R.string.sort_by_last_updated
    }

private fun DownloadLibrarySortOrder.labelRes(): Int =
    when (this) {
        DownloadLibrarySortOrder.ASCENDING -> R.string.sort_order_ascending
        DownloadLibrarySortOrder.DESCENDING -> R.string.sort_order_descending
    }

private fun DownloadLibrarySortOrder.iconRes(): Int =
    when (this) {
        DownloadLibrarySortOrder.ASCENDING -> R.drawable.arrow_upward
        DownloadLibrarySortOrder.DESCENDING -> R.drawable.arrow_downward
    }

private fun DownloadEntryUiModel.placeholderIcon(): Int =
    when {
        destinationRoute?.startsWith("album/") == true -> R.drawable.album
        destinationRoute != null -> R.drawable.queue_music
        else -> R.drawable.music_note
    }

private const val CONTENT_TYPE_OVERVIEW = "download_overview"
private const val CONTENT_TYPE_EMPTY = "download_empty"
private const val CONTENT_TYPE_SECTION_HEADER = "download_section_header"
private const val CONTENT_TYPE_ENTRY = "download_entry"
private const val CONTENT_TYPE_CHILD = "download_child"
private const val CONTENT_TYPE_SECTION_SPACER = "download_section_spacer"
