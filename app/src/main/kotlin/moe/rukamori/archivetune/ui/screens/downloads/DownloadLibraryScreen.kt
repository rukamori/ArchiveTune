/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.downloads

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
import moe.rukamori.archivetune.downloads.DownloadSectionUiModel
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatFileSize
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.DownloadLibraryEvent
import moe.rukamori.archivetune.viewmodels.DownloadLibraryScreenState
import moe.rukamori.archivetune.viewmodels.DownloadLibraryTab
import moe.rukamori.archivetune.viewmodels.DownloadLibraryViewModel

@Composable
fun DownloadLibraryScreen(
    navController: NavController,
    viewModel: DownloadLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel, playerConnection) {
        viewModel.events.collect { event ->
            when (event) {
                is DownloadLibraryEvent.Message -> {
                    snackbarHostState.showSnackbar(navController.context.getString(event.messageRes))
                }

                is DownloadLibraryEvent.Navigate -> {
                    navController.navigate(event.route)
                }

                is DownloadLibraryEvent.PlaySong -> {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = event.metadata.title,
                            items = event.queue.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.startIndex,
                        ),
                    )
                }
            }
        }
    }

    DownloadLibraryScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = navController::navigateUp,
        onBackToMain = navController::backToMain,
        onActivateSearch = viewModel::activateSearch,
        onCloseSearch = viewModel::closeSearch,
        onSubmitSearch = viewModel::submitSearch,
        onQueryChange = viewModel::updateQuery,
        onClearQuery = viewModel::clearQuery,
        onTabSelected = viewModel::selectTab,
        onOpenEntry = viewModel::open,
        onPauseEntry = viewModel::pause,
        onResumeEntry = viewModel::resume,
        onRemoveEntry = viewModel::remove,
        onPauseSection = viewModel::pause,
        onResumeSection = viewModel::resume,
        onRemoveSection = viewModel::remove,
    )
}

@Composable
private fun DownloadLibraryScreenContent(
    state: DownloadLibraryScreenState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    onActivateSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onSubmitSearch: () -> Unit,
    onQueryChange: (String) -> Unit,
    onClearQuery: () -> Unit,
    onTabSelected: (DownloadLibraryTab) -> Unit,
    onOpenEntry: (DownloadEntryUiModel) -> Unit,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    val selectedTab = state.selectedTab()
    val query = state.query()
    val isSearchActive = state.isSearchActive()
    val tabs = remember { DownloadLibraryTab.entries }
    val pagerState = rememberPagerState(initialPage = tabs.indexOf(selectedTab), pageCount = { tabs.size })
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    BackHandler(enabled = isSearchActive, onBack = onCloseSearch)

    LaunchedEffect(selectedTab) {
        val targetPage = tabs.indexOf(selectedTab)
        if (pagerState.currentPage != targetPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }
            .distinctUntilChanged()
            .collect { page -> onTabSelected(tabs[page]) }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            AnimatedContent(
                targetState = isSearchActive,
                transitionSpec = {
                    fadeIn(spring(stiffness = Spring.StiffnessMediumLow)) togetherWith
                        fadeOut(spring(stiffness = Spring.StiffnessMediumLow))
                },
                label = "downloadLibraryTopBar",
            ) { searching ->
                if (searching) {
                    SearchBar(
                        inputField = {
                            SearchBarDefaults.InputField(
                                query = query,
                                onQueryChange = onQueryChange,
                                onSearch = { onSubmitSearch() },
                                expanded = false,
                                onExpandedChange = {},
                                placeholder = { Text(stringResource(R.string.search_downloads)) },
                                leadingIcon = {
                                    IconButton(onClick = onCloseSearch) {
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
                                                    contentDescription = stringResource(R.string.close),
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                            )
                        },
                        expanded = false,
                        onExpandedChange = {},
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                                .padding(top = 8.dp, bottom = 4.dp),
                    ) {}
                } else {
                    Column {
                        MediumFlexibleTopAppBar(
                            title = {
                                Text(
                                    text = stringResource(R.string.downloads),
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                moe.rukamori.archivetune.ui.component.IconButton(
                                    onClick = onBack,
                                    onLongClick = onBackToMain,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.arrow_back),
                                        contentDescription = null,
                                    )
                                }
                            },
                            actions = {
                                IconButton(onClick = onActivateSearch) {
                                    Icon(
                                        painter = painterResource(R.drawable.search),
                                        contentDescription = stringResource(R.string.search),
                                    )
                                }
                            },
                            colors =
                                TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                                    scrolledContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.96f),
                                ),
                            scrollBehavior = scrollBehavior,
                        )
                        DownloadTabs(
                            tabs = tabs,
                            pagerState = pagerState,
                            onSelect = { index -> scope.launch { pagerState.animateScrollToPage(index) } },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        contentWindowInsets = WindowInsets(0.dp),
    ) { innerPadding ->
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
                EmptyPlaceholder(
                    icon = R.drawable.error,
                    text = stringResource(state.messageRes),
                    modifier = Modifier.padding(innerPadding),
                )
            }

            is DownloadLibraryScreenState.Empty -> {
                DownloadPager(
                    pagerState = pagerState,
                    query = query,
                    downloadedSections = emptyList(),
                    progressSections = emptyList(),
                    contentPadding = innerPadding,
                    onOpenEntry = onOpenEntry,
                    onPauseEntry = onPauseEntry,
                    onResumeEntry = onResumeEntry,
                    onRemoveEntry = onRemoveEntry,
                    onPauseSection = onPauseSection,
                    onResumeSection = onResumeSection,
                    onRemoveSection = onRemoveSection,
                )
            }

            is DownloadLibraryScreenState.Success -> {
                DownloadPager(
                    pagerState = pagerState,
                    query = query,
                    downloadedSections = state.library.downloadedSections,
                    progressSections = state.library.progressSections,
                    contentPadding = innerPadding,
                    onOpenEntry = onOpenEntry,
                    onPauseEntry = onPauseEntry,
                    onResumeEntry = onResumeEntry,
                    onRemoveEntry = onRemoveEntry,
                    onPauseSection = onPauseSection,
                    onResumeSection = onResumeSection,
                    onRemoveSection = onRemoveSection,
                )
            }
        }
    }
}

@Composable
private fun DownloadTabs(
    tabs: List<DownloadLibraryTab>,
    pagerState: PagerState,
    onSelect: (Int) -> Unit,
) {
    PrimaryTabRow(selectedTabIndex = pagerState.currentPage) {
        tabs.forEachIndexed { index, tab ->
            Tab(
                selected = pagerState.currentPage == index,
                onClick = { onSelect(index) },
                icon = {
                    Icon(
                        painter =
                            painterResource(
                                if (tab == DownloadLibraryTab.DOWNLOADED) {
                                    R.drawable.offline
                                } else {
                                    R.drawable.download
                                },
                            ),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text =
                            stringResource(
                                if (tab == DownloadLibraryTab.DOWNLOADED) {
                                    R.string.filter_downloaded
                                } else {
                                    R.string.download_in_progress
                                },
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun DownloadPager(
    pagerState: PagerState,
    query: String,
    downloadedSections: List<DownloadSectionUiModel>,
    progressSections: List<DownloadSectionUiModel>,
    contentPadding: PaddingValues,
    onOpenEntry: (DownloadEntryUiModel) -> Unit,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        verticalAlignment = Alignment.Top,
    ) { page ->
        val inProgress = page == DownloadLibraryTab.PROGRESS.ordinal
        DownloadSections(
            sections = if (inProgress) progressSections else downloadedSections,
            inProgress = inProgress,
            query = query,
            contentPadding = contentPadding,
            onOpenEntry = onOpenEntry,
            onPauseEntry = onPauseEntry,
            onResumeEntry = onResumeEntry,
            onRemoveEntry = onRemoveEntry,
            onPauseSection = onPauseSection,
            onResumeSection = onResumeSection,
            onRemoveSection = onRemoveSection,
        )
    }
}

@Composable
private fun DownloadSections(
    sections: List<DownloadSectionUiModel>,
    inProgress: Boolean,
    query: String,
    contentPadding: PaddingValues,
    onOpenEntry: (DownloadEntryUiModel) -> Unit,
    onPauseEntry: (DownloadEntryUiModel) -> Unit,
    onResumeEntry: (DownloadEntryUiModel) -> Unit,
    onRemoveEntry: (DownloadEntryUiModel) -> Unit,
    onPauseSection: (DownloadSectionUiModel) -> Unit,
    onResumeSection: (DownloadSectionUiModel) -> Unit,
    onRemoveSection: (DownloadSectionUiModel) -> Unit,
) {
    if (sections.isEmpty()) {
        EmptyPlaceholder(
            icon =
                if (query.isNotBlank()) {
                    R.drawable.search
                } else if (inProgress) {
                    R.drawable.download
                } else {
                    R.drawable.offline
                },
            text =
                stringResource(
                    if (query.isNotBlank()) {
                        R.string.no_matching_downloads
                    } else if (inProgress) {
                        R.string.no_downloads_in_progress
                    } else {
                        R.string.no_downloads
                    },
                ),
            modifier = Modifier.padding(contentPadding),
        )
        return
    }

    val layoutDirection = LocalLayoutDirection.current
    val playerPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        contentPadding =
            PaddingValues(
                start = contentPadding.calculateStartPadding(layoutDirection) + 16.dp,
                top = contentPadding.calculateTopPadding() + 16.dp,
                end = contentPadding.calculateEndPadding(layoutDirection) + 16.dp,
                bottom = playerPadding.calculateBottomPadding() + 24.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        sections.forEach { section ->
            item(
                key = "header:${section.mediaType}",
                contentType = CONTENT_TYPE_SECTION_HEADER,
            ) {
                val pauseAction = remember(section, onPauseSection) { { onPauseSection(section) } }
                val resumeAction = remember(section, onResumeSection) { { onResumeSection(section) } }
                val removeAction = remember(section, onRemoveSection) { { onRemoveSection(section) } }
                DownloadSectionHeader(
                    section = section,
                    inProgress = inProgress,
                    onPause = pauseAction,
                    onResume = resumeAction,
                    onRemove = removeAction,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp).animateItem(),
                )
            }
            items(
                items = section.entries,
                key = DownloadEntryUiModel::id,
                contentType = { CONTENT_TYPE_DOWNLOAD_ENTRY },
            ) { entry ->
                val openAction = remember(entry, onOpenEntry) { { onOpenEntry(entry) } }
                val pauseAction = remember(entry, onPauseEntry) { { onPauseEntry(entry) } }
                val resumeAction = remember(entry, onResumeEntry) { { onResumeEntry(entry) } }
                val removeAction = remember(entry, onRemoveEntry) { { onRemoveEntry(entry) } }
                DownloadEntry(
                    entry = entry,
                    inProgress = inProgress,
                    onOpen = openAction,
                    onPause = pauseAction,
                    onResume = resumeAction,
                    onRemove = removeAction,
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp).animateItem(),
                )
            }
            item(
                key = "spacer:${section.mediaType}",
                contentType = CONTENT_TYPE_SECTION_SPACER,
            ) {
                Spacer(Modifier.height(14.dp))
            }
        }
    }
}

@Composable
private fun DownloadSectionHeader(
    section: DownloadSectionUiModel,
    inProgress: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = stringResource(section.mediaType.titleRes()),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
            },
            supportingContent = {
                Text(
                    text =
                        if (inProgress) {
                            stringResource(
                                R.string.download_progress_summary,
                                section.percent,
                                formatFileSize(section.speedBytesPerSecond),
                            )
                        } else {
                            section.countText()
                        },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            leadingContent = {
                Surface(
                    modifier = Modifier.size(52.dp),
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.primary,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(section.mediaType.iconRes()),
                            contentDescription = null,
                            modifier = Modifier.size(26.dp),
                        )
                    }
                }
            },
            trailingContent = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (inProgress) {
                        PrimaryFilledIconButton(
                            icon = if (section.paused) R.drawable.play else R.drawable.pause,
                            contentDescription =
                                stringResource(
                                    if (section.paused) R.string.resume_download else R.string.pause_download,
                                ),
                            onClick = if (section.paused) onResume else onPause,
                        )
                    }
                    PrimaryFilledIconButton(
                        icon = if (inProgress) R.drawable.close else R.drawable.delete,
                        contentDescription =
                            stringResource(if (inProgress) android.R.string.cancel else R.string.remove_download),
                        onClick = onRemove,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        )
    }
}

@Composable
private fun DownloadEntry(
    entry: DownloadEntryUiModel,
    inProgress: Boolean,
    onOpen: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onOpen,
        modifier = modifier,
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = entry.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entry.supportingText?.let { supportingText ->
                        Text(
                            text = supportingText,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (inProgress) {
                        if (entry.failed) {
                            Text(
                                text = stringResource(R.string.download_failed_state),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = stringResource(R.string.download_progress_percent, entry.percent),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = stringResource(R.string.download_speed, formatFileSize(entry.speedBytesPerSecond)),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                        LinearWavyProgressIndicator(
                            progress = { entry.progress },
                            modifier = Modifier.fillMaxWidth(),
                            amplitude = { if (entry.paused) 0f else 1f },
                        )
                    } else if (entry.destinationRoute != null || entry.totalCount > 1) {
                        Text(
                            text = pluralStringResource(R.plurals.n_song, entry.totalCount, entry.totalCount),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            },
            leadingContent = {
                AsyncImage(
                    model = entry.thumbnailUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    placeholder = painterResource(entry.placeholderIcon()),
                    error = painterResource(entry.placeholderIcon()),
                    modifier = Modifier.size(64.dp).clip(MaterialTheme.shapes.medium),
                )
            },
            trailingContent = {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (!inProgress) {
                        entry.durationSeconds?.let { durationSeconds ->
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                            ) {
                                Text(
                                    text = makeTimeString(durationSeconds * 1_000L),
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                )
                            }
                        }
                    } else {
                        PrimaryFilledIconButton(
                            icon = if (entry.paused) R.drawable.play else R.drawable.pause,
                            contentDescription =
                                stringResource(
                                    if (entry.paused) R.string.resume_download else R.string.pause_download,
                                ),
                            onClick = if (entry.paused) onResume else onPause,
                        )
                    }
                    PrimaryFilledIconButton(
                        icon = if (inProgress) R.drawable.close else R.drawable.delete,
                        contentDescription =
                            stringResource(if (inProgress) android.R.string.cancel else R.string.remove_download),
                        onClick = onRemove,
                    )
                }
            },
            colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        )
    }
}

@Composable
private fun PrimaryFilledIconButton(
    icon: Int,
    contentDescription: String,
    onClick: () -> Unit,
) {
    FilledIconButton(
        onClick = onClick,
        shapes = IconButtonDefaults.shapes(),
        colors =
            IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = contentDescription,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun DownloadSectionUiModel.countText(): String =
    when (mediaType) {
        DownloadMediaType.PLAYLIST -> pluralStringResource(R.plurals.n_playlist, entries.size, entries.size)
        DownloadMediaType.ALBUM -> pluralStringResource(R.plurals.n_album, entries.size, entries.size)
        DownloadMediaType.SONG -> pluralStringResource(R.plurals.n_song, entries.size, entries.size)
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

private fun DownloadEntryUiModel.placeholderIcon(): Int =
    when {
        destinationRoute?.startsWith("album/") == true -> R.drawable.album
        destinationRoute != null -> R.drawable.queue_music
        else -> R.drawable.music_note
    }

private fun DownloadLibraryScreenState.selectedTab(): DownloadLibraryTab =
    when (this) {
        is DownloadLibraryScreenState.Loading -> selectedTab
        is DownloadLibraryScreenState.Success -> selectedTab
        is DownloadLibraryScreenState.Empty -> selectedTab
        is DownloadLibraryScreenState.Error -> selectedTab
    }

private fun DownloadLibraryScreenState.query(): String =
    when (this) {
        is DownloadLibraryScreenState.Loading -> query
        is DownloadLibraryScreenState.Success -> query
        is DownloadLibraryScreenState.Empty -> query
        is DownloadLibraryScreenState.Error -> query
    }

private fun DownloadLibraryScreenState.isSearchActive(): Boolean =
    when (this) {
        is DownloadLibraryScreenState.Loading -> isSearchActive
        is DownloadLibraryScreenState.Success -> isSearchActive
        is DownloadLibraryScreenState.Empty -> isSearchActive
        is DownloadLibraryScreenState.Error -> isSearchActive
    }

private const val CONTENT_TYPE_SECTION_HEADER = "download_section_header"
private const val CONTENT_TYPE_DOWNLOAD_ENTRY = "download_entry"
private const val CONTENT_TYPE_SECTION_SPACER = "download_section_spacer"
