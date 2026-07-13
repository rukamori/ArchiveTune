/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)

package moe.rukamori.archivetune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MediumFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HistorySource
import moe.rukamori.archivetune.constants.InnerTubeCookieKey
import moe.rukamori.archivetune.db.entities.EventWithSong
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.HideOnScrollFAB
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.TopSearch
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.DateAgo
import moe.rukamori.archivetune.viewmodels.HistoryViewModel
import moe.rukamori.archivetune.viewmodels.RemoteHistoryUiState
import java.time.format.DateTimeFormatter
import moe.rukamori.archivetune.ui.component.IconButton as AppIconButton

@Composable
fun HistoryScreen(
    navController: NavController,
    viewModel: HistoryViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val animationsDisabled = LocalAnimationsDisabled.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val historySource by viewModel.historySource.collectAsStateWithLifecycle()
    val events by viewModel.events.collectAsStateWithLifecycle()
    val isLoadingMoreEvents by viewModel.isLoadingMoreEvents.collectAsStateWithLifecycle()
    val canLoadMoreEvents by viewModel.canLoadMoreEvents.collectAsStateWithLifecycle()
    val remoteHistoryState by viewModel.remoteHistoryState.collectAsStateWithLifecycle()

    val innerTubeCookie by rememberPreference(InnerTubeCookieKey, "")
    val isLoggedIn =
        remember(innerTubeCookie) {
            hasYouTubeLoginCookie(innerTubeCookie)
        }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }
    var selectedEventIds by rememberSaveable { mutableStateOf(emptyList<Long>()) }

    val focusRequester = remember { FocusRequester() }
    val localListState = rememberLazyListState()
    val remoteListState = rememberLazyListState()
    val scrollBehavior =
        appBarScrollBehavior(
            canScroll = { !isSearching && selectedEventIds.isEmpty() },
        )

    val searchQuery = query.text.trim()
    val showSearchBar = isSearching || searchQuery.isNotBlank()
    val selectedEventIdSet by remember(selectedEventIds) {
        derivedStateOf { selectedEventIds.toSet() }
    }

    val filteredEvents =
        remember(events, searchQuery) {
            filterLocalEvents(events, searchQuery)
        }
    val localVisibleEvents =
        remember(filteredEvents) {
            filteredEvents.values.flatten()
        }
    val localVisibleEventIds =
        remember(localVisibleEvents) {
            localVisibleEvents.map { it.event.id }
        }
    val localVisibleEventIdSet by remember(localVisibleEventIds) {
        derivedStateOf { localVisibleEventIds.toSet() }
    }
    val selectedSongs =
        remember(localVisibleEvents, selectedEventIdSet) {
            localVisibleEvents.filter { it.event.id in selectedEventIdSet }
        }
    val selectedHistoryEventIds =
        remember(selectedSongs) {
            selectedSongs.map { it.event.id }
        }
    val selectionCount = selectedSongs.size

    val filteredRemoteSections =
        remember(remoteHistoryState, searchQuery) {
            when (remoteHistoryState) {
                is RemoteHistoryUiState.Success -> {
                    filterRemoteSections(
                        (remoteHistoryState as RemoteHistoryUiState.Success).page.sections.orEmpty(),
                        searchQuery,
                    )
                }

                else -> {
                    emptyList()
                }
            }
        }
    val remoteVisibleSongs =
        remember(filteredRemoteSections) {
            filteredRemoteSections.flatMap { it.songs }
        }
    val availableSources =
        remember(isLoggedIn) {
            if (isLoggedIn) {
                listOf(HistorySource.LOCAL, HistorySource.REMOTE)
            } else {
                listOf(HistorySource.LOCAL)
            }
        }
    val activeListState = if (historySource == HistorySource.REMOTE) remoteListState else localListState
    val motionDuration = if (animationsDisabled) 0 else 220

    val clearSelection =
        remember {
            { selectedEventIds = emptyList() }
        }
    val resetSearch =
        remember(focusManager) {
            {
                isSearching = false
                query = TextFieldValue()
                focusManager.clearFocus()
            }
        }

    val dateAgoToString: (DateAgo) -> String =
        remember(context) {
            { dateAgo ->
                when (dateAgo) {
                    DateAgo.Today -> context.getString(R.string.today)
                    DateAgo.Yesterday -> context.getString(R.string.yesterday)
                    DateAgo.ThisWeek -> context.getString(R.string.this_week)
                    DateAgo.LastWeek -> context.getString(R.string.last_week)
                    is DateAgo.Other -> dateAgo.date.format(DateTimeFormatter.ofPattern("yyyy/MM"))
                }
            }
        }

    val currentSourceSummary =
        stringResource(
            if (historySource == HistorySource.LOCAL) {
                R.string.history_local_summary
            } else {
                R.string.history_remote_summary
            },
        )
    val currentVisibleCount =
        if (historySource == HistorySource.REMOTE) {
            remoteVisibleSongs.size
        } else {
            localVisibleEvents.size
        }

    val historySourceDock: @Composable () -> Unit = {
        HistorySourceDock(
            visibleSongCount = currentVisibleCount,
            availableSources = availableSources,
            currentSource = historySource,
            onSourceChange = { newSource ->
                if (newSource == historySource) return@HistorySourceDock

                viewModel.historySource.value = newSource
                if (newSource == HistorySource.REMOTE) {
                    when (remoteHistoryState) {
                        is RemoteHistoryUiState.Error -> {
                            viewModel.fetchRemoteHistory()
                        }

                        is RemoteHistoryUiState.Empty -> {
                            viewModel.enqueueSilentFetch()
                        }

                        else -> {
                            Unit
                        }
                    }
                }
            },
        )
    }

    val historyContent: @Composable (Dp) -> Unit = { topPadding ->
        Crossfade(
            targetState = historySource,
            animationSpec = tween(durationMillis = motionDuration),
            label = "HistorySourceContent",
        ) { source ->
            when (source) {
                HistorySource.REMOTE -> {
                    RemoteHistoryFeed(
                        listState = remoteListState,
                        topPadding = topPadding,
                        headerContent = historySourceDock,
                        remoteHistoryState = remoteHistoryState,
                        filteredSections = filteredRemoteSections,
                        isPlaying = isPlaying,
                        activeMediaId = mediaMetadata?.id,
                        navController = navController,
                        onRetry = viewModel::fetchRemoteHistory,
                        onSongMenu = { song ->
                            menuState.show {
                                YouTubeSongMenu(
                                    song = song,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onSongClick = { song ->
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    YouTubeQueue.radio(song.toMediaMetadata()),
                                )
                            }
                        },
                    )
                }

                HistorySource.LOCAL -> {
                    LocalHistoryFeed(
                        listState = localListState,
                        topPadding = topPadding,
                        headerContent = historySourceDock,
                        filteredEvents = filteredEvents,
                        visibleEvents = localVisibleEvents,
                        isLoadingMore = isLoadingMoreEvents,
                        canLoadMore = canLoadMoreEvents,
                        isSearchActive = searchQuery.isNotBlank(),
                        selectedEventIds = selectedEventIdSet,
                        isPlaying = isPlaying,
                        activeMediaId = mediaMetadata?.id,
                        dateAgoToString = dateAgoToString,
                        navController = navController,
                        onToggleSelection = { eventId ->
                            selectedEventIds =
                                if (eventId in selectedEventIdSet) {
                                    selectedEventIds - eventId
                                } else {
                                    selectedEventIds + eventId
                                }
                        },
                        onStartSelection = { eventId ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            if (eventId !in selectedEventIdSet) {
                                selectedEventIds = selectedEventIds + eventId
                            }
                        },
                        onSongMenu = { event ->
                            menuState.show {
                                SongMenu(
                                    originalSong = event.song,
                                    event = event.event,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }
                        },
                        onSongClick = { dateAgo, songsForDate, index, event ->
                            if (event.song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(
                                    ListQueue(
                                        title = dateAgoToString(dateAgo),
                                        items = songsForDate.map { it.song.toMediaItem() },
                                        startIndex = index,
                                    ),
                                )
                            }
                        },
                        onLoadMore = viewModel::loadMoreEvents,
                    )
                }
            }
        }
    }

    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(historySource, isLoggedIn) {
        if (!isLoggedIn && historySource == HistorySource.REMOTE) {
            viewModel.historySource.value = HistorySource.LOCAL
        }
        if (historySource != HistorySource.LOCAL && selectedEventIds.isNotEmpty()) {
            selectedEventIds = emptyList()
        }
    }

    LaunchedEffect(localVisibleEventIds) {
        if (selectedEventIds.any { it !in localVisibleEventIdSet }) {
            selectedEventIds = selectedEventIds.filter(localVisibleEventIdSet::contains)
        }
    }

    // A. When screen opens + user is logged in → fetch remote history in background
    LaunchedEffect("prefetch", isLoggedIn) {
        if (!isLoggedIn) return@LaunchedEffect
        if (remoteHistoryState is RemoteHistoryUiState.Success) return@LaunchedEffect
        delay(1_000) // wait for screen

        viewModel.fetchRemoteHistorySilent()
    }

    // B. When playback sync happens → retry with backoff
    LaunchedEffect("sync", isLoggedIn) {
        YouTube.historySyncEvent.collect {
            if (!isLoggedIn) return@collect

            // Retry 3 times with increasing delay (handles slow internet)
            repeat(3) { attempt ->
                delay(3000L * (attempt + 1)) // 3s, 6s, 9s
                viewModel.fetchRemoteHistorySilent()
                if (remoteHistoryState is RemoteHistoryUiState.Success) return@collect
            }
        }
    }

    BackHandler(enabled = showSearchBar) {
        resetSearch()
    }

    BackHandler(enabled = selectionCount > 0 && !showSearchBar) {
        clearSelection()
    }

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (!showSearchBar) {
                MediumFlexibleTopAppBar(
                    title = {
                        Text(
                            text =
                                if (selectionCount > 0) {
                                    pluralStringResource(R.plurals.n_song, selectionCount, selectionCount)
                                } else {
                                    stringResource(R.string.history)
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    subtitle = {
                        Text(
                            text =
                                if (selectionCount == 0) {
                                    pluralStringResource(
                                        R.plurals.n_song,
                                        currentVisibleCount,
                                        currentVisibleCount,
                                    ) + " · " + currentSourceSummary
                                } else {
                                    currentSourceSummary
                                },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    navigationIcon = {
                        AppIconButton(
                            onClick = {
                                if (selectionCount > 0) {
                                    clearSelection()
                                } else {
                                    navController.navigateUp()
                                }
                            },
                            onLongClick = {
                                if (selectionCount == 0) {
                                    navController.backToMain()
                                }
                            },
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        if (selectionCount > 0) R.drawable.close else R.drawable.arrow_back,
                                    ),
                                contentDescription = null,
                            )
                        }
                    },
                    actions = {
                        if (selectionCount == 0) {
                            AppIconButton(
                                onClick = { isSearching = true },
                                onLongClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.search),
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                        ),
                )
            }
        },
        floatingActionButton = {
            HideOnScrollFAB(
                visible = !showSearchBar && selectionCount == 0 && currentVisibleCount > 0,
                lazyListState = activeListState,
                icon = R.drawable.shuffle,
                label = stringResource(R.string.shuffle),
                onClick = {
                    if (historySource == HistorySource.REMOTE) {
                        if (remoteVisibleSongs.isNotEmpty()) {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.history),
                                    items = remoteVisibleSongs.map { it.toMediaItem() }.shuffled(),
                                ),
                            )
                        }
                    } else if (localVisibleEvents.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history),
                                items = localVisibleEvents.map { it.song.toMediaItem() }.shuffled(),
                            ),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!showSearchBar) {
                historyContent(innerPadding.calculateTopPadding())
            }

            AnimatedVisibility(
                visible = showSearchBar,
                enter = fadeIn(tween(durationMillis = motionDuration)),
                exit = fadeOut(tween(durationMillis = motionDuration)),
            ) {
                TopSearch(
                    query = query,
                    onQueryChange = { query = it },
                    onSearch = { focusManager.clearFocus() },
                    active = showSearchBar,
                    onActiveChange = { active ->
                        if (active) {
                            isSearching = true
                        } else {
                            resetSearch()
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    placeholder = {
                        Text(text = stringResource(R.string.search))
                    },
                    leadingIcon = {
                        AppIconButton(
                            onClick = { resetSearch() },
                            onLongClick = {
                                if (query.text.isBlank()) {
                                    navController.backToMain()
                                }
                            },
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.arrow_back),
                                contentDescription = null,
                            )
                        }
                    },
                    trailingIcon = {
                        if (query.text.isNotBlank()) {
                            AppIconButton(
                                onClick = { query = TextFieldValue() },
                                onLongClick = {},
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.close),
                                    contentDescription = null,
                                )
                            }
                        }
                    },
                    focusRequester = focusRequester,
                ) {
                    historyContent(0.dp)
                }
            }

            HistorySelectionToolbar(
                visible = selectionCount > 0 && !showSearchBar && historySource == HistorySource.LOCAL,
                allVisibleSelected = localVisibleEvents.isNotEmpty() && selectionCount == localVisibleEvents.size,
                onToggleAll = {
                    selectedEventIds =
                        if (selectionCount == localVisibleEvents.size) {
                            emptyList()
                        } else {
                            localVisibleEvents.map { it.event.id }
                        }
                },
                onMoreClick = {
                    menuState.show {
                        SelectionMediaMetadataMenu(
                            songSelection = selectedSongs.map { it.song.toMediaItem().metadata!! },
                            onDismiss = menuState::dismiss,
                            clearAction = clearSelection,
                            currentItems = emptyList(),
                            onRemoveFromHistory = {
                                viewModel.removeEventsFromHistory(selectedHistoryEventIds)
                            },
                        )
                    }
                },
            )
        }
    }
}

@Composable
private fun LocalHistoryFeed(
    listState: LazyListState,
    topPadding: Dp,
    headerContent: @Composable () -> Unit,
    filteredEvents: Map<DateAgo, List<EventWithSong>>,
    visibleEvents: List<EventWithSong>,
    isLoadingMore: Boolean,
    canLoadMore: Boolean,
    isSearchActive: Boolean,
    selectedEventIds: Set<Long>,
    isPlaying: Boolean,
    activeMediaId: String?,
    dateAgoToString: (DateAgo) -> String,
    navController: NavController,
    onToggleSelection: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onSongMenu: (EventWithSong) -> Unit,
    onSongClick: (DateAgo, List<EventWithSong>, Int, EventWithSong) -> Unit,
    onLoadMore: () -> Unit,
) {
    val isSelectionMode = selectedEventIds.isNotEmpty()

    LaunchedEffect(listState, canLoadMore) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            canLoadMore && lastVisibleIndex >= layoutInfo.totalItemsCount - HISTORY_LOAD_MORE_THRESHOLD
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) onLoadMore()
        }
    }

    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 840.dp)
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item("history_overview") {
            headerContent()
        }

        if (visibleEvents.isEmpty() && isLoadingMore) {
            item("local_history_initial_loading") {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    ContainedLoadingIndicator()
                }
            }
        } else if (visibleEvents.isEmpty()) {
            item("local_history_empty") {
                HistoryStateCard(
                    title =
                        stringResource(
                            if (isSearchActive) R.string.history_no_results_title else R.string.history_local_empty_title,
                        ),
                    description =
                        stringResource(
                            if (isSearchActive) R.string.history_no_results_desc else R.string.history_local_empty_desc,
                        ),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    icon = if (isSearchActive) R.drawable.search else R.drawable.history,
                )
            }
        } else {
            filteredEvents.forEach { (dateAgo, songsForDate) ->
                stickyHeader(key = "header_$dateAgo") {
                    HistorySectionHeader(
                        title = dateAgoToString(dateAgo),
                        songCount = songsForDate.size,
                    )
                }

                itemsIndexed(
                    items = songsForDate,
                    key = { _, event -> event.event.id },
                    contentType = { _, _ -> "local_history_song" },
                ) { index, event ->
                    SongListItem(
                        song = event.song,
                        isActive = event.song.id == activeMediaId,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        isSelected = event.event.id in selectedEventIds,
                        trailingContent = {
                            androidx.compose.material3.IconButton(
                                onClick = {
                                    if (!isSelectionMode) {
                                        onSongMenu(event)
                                    }
                                },
                            ) {
                                Icon(
                                    painter = painterResource(R.drawable.more_vert),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp)
                                .combinedClickable(
                                    onClick = {
                                        if (isSelectionMode) {
                                            onToggleSelection(event.event.id)
                                        } else {
                                            onSongClick(dateAgo, songsForDate, index, event)
                                        }
                                    },
                                    onLongClick = {
                                        onStartSelection(event.event.id)
                                    },
                                ).animateItem(),
                    )
                }
            }

            if (canLoadMore) {
                item("local_history_loading_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            ContainedLoadingIndicator()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RemoteHistoryFeed(
    listState: LazyListState,
    topPadding: Dp,
    headerContent: @Composable () -> Unit,
    remoteHistoryState: RemoteHistoryUiState,
    filteredSections: List<HistoryPage.HistorySection>,
    isPlaying: Boolean,
    activeMediaId: String?,
    navController: NavController,
    onRetry: () -> Unit,
    onSongMenu: (moe.rukamori.archivetune.innertube.models.SongItem) -> Unit,
    onSongClick: (moe.rukamori.archivetune.innertube.models.SongItem) -> Unit,
) {
    LazyColumn(
        state = listState,
        modifier =
            Modifier
                .fillMaxSize()
                .wrapContentWidth(Alignment.CenterHorizontally)
                .widthIn(max = 840.dp)
                .padding(top = topPadding)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item("history_overview") {
            headerContent()
        }

        when (remoteHistoryState) {
            RemoteHistoryUiState.Loading -> {
                item("remote_history_loading") {
                    HistoryStateCard(
                        title = stringResource(R.string.history_remote_loading),
                        description = stringResource(R.string.history_remote_summary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        loading = true,
                    )
                }
            }

            RemoteHistoryUiState.Empty -> {
                item("remote_history_empty") {
                    HistoryStateCard(
                        title = stringResource(R.string.history_remote_empty_title),
                        description = stringResource(R.string.history_remote_empty_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        icon = R.drawable.history,
                    )
                }
            }

            RemoteHistoryUiState.Error -> {
                item("remote_history_error") {
                    HistoryStateCard(
                        title = stringResource(R.string.history_remote_error_title),
                        description = stringResource(R.string.history_remote_error_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        actionLabel = stringResource(R.string.retry),
                        onActionClick = onRetry,
                        icon = R.drawable.history,
                    )
                }
            }

            is RemoteHistoryUiState.Success -> {
                if (filteredSections.isEmpty()) {
                    item("remote_history_search_empty") {
                        HistoryStateCard(
                            title = stringResource(R.string.history_no_results_title),
                            description = stringResource(R.string.history_no_results_desc),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            icon = R.drawable.search,
                        )
                    }
                } else {
                    filteredSections.forEach { section ->
                        stickyHeader(key = "header_${section.title}") {
                            HistorySectionHeader(
                                title = section.title,
                                songCount = section.songs.size,
                            )
                        }

                        items(
                            items = section.songs,
                            key = { song -> "${section.title}_${song.id}" },
                            contentType = { "remote_history_song" },
                        ) { song ->
                            YouTubeListItem(
                                item = song,
                                isActive = song.id == activeMediaId,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    androidx.compose.material3.IconButton(
                                        onClick = { onSongMenu(song) },
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 8.dp)
                                        .combinedClickable(
                                            onClick = { onSongClick(song) },
                                            onLongClick = { onSongMenu(song) },
                                        ).animateItem(),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HistorySourceDock(
    visibleSongCount: Int,
    availableSources: List<HistorySource>,
    currentSource: HistorySource,
    onSourceChange: (HistorySource) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().padding(16.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.history),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.n_song, visibleSongCount, visibleSongCount),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                HistorySourceSelector(
                    currentSource = currentSource,
                    availableSources = availableSources,
                    onSourceChange = onSourceChange,
                )
            }
        }
    }
}

@Composable
private fun HistorySectionHeader(
    title: String,
    songCount: Int,
) {
    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = pluralStringResource(R.plurals.n_song, songCount, songCount),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp),
            )
        }
    }
}

@Composable
private fun HistorySourceSelector(
    currentSource: HistorySource,
    availableSources: List<HistorySource>,
    onSourceChange: (HistorySource) -> Unit,
) {
    if (availableSources.size == 1) {
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        ) {
            Text(
                text = stringResource(R.string.local_history),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            )
        }
        return
    }

    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier = Modifier.fillMaxWidth(),
    ) {
        availableSources.forEachIndexed { index, source ->
            val checked = source == currentSource
            ToggleButton(
                checked = checked,
                onCheckedChange = {
                    if (!checked) {
                        onSourceChange(source)
                    }
                },
                modifier =
                    Modifier
                        .weight(1f)
                        .height(52.dp),
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        availableSources.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.toggleButtonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
            ) {
                Text(
                    text =
                        stringResource(
                            if (source == HistorySource.LOCAL) {
                                R.string.local_history
                            } else {
                                R.string.remote_history
                            },
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun HistoryStateCard(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    loading: Boolean = false,
    icon: Int? = null,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 1.dp,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 32.dp),
        ) {
            if (loading) {
                ContainedLoadingIndicator()
            } else if (icon != null) {
                val stateShape = MaterialShapes.Cookie9Sided.toShape()
                Surface(
                    modifier = Modifier.size(88.dp),
                    shape = stateShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(icon),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.widthIn(max = 420.dp),
            )

            if (actionLabel != null && onActionClick != null) {
                ElevatedButton(
                    onClick = onActionClick,
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = actionLabel)
                }
            }
        }
    }
}

@Composable
private fun BoxScope.HistorySelectionToolbar(
    visible: Boolean,
    allVisibleSelected: Boolean,
    onToggleAll: () -> Unit,
    onMoreClick: () -> Unit,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedVisibility(
        visible = visible,
        enter =
            fadeIn(tween(if (animationsDisabled) 0 else 220)) +
                slideInVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it / 2 },
        exit =
            fadeOut(tween(if (animationsDisabled) 0 else 220)) +
                slideOutVertically(animationSpec = tween(if (animationsDisabled) 0 else 220)) { it / 2 },
        modifier =
            Modifier
                .align(Alignment.BottomCenter)
                .windowInsetsPadding(
                    LocalPlayerAwareWindowInsets.current.only(
                        WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                    ),
                ).padding(16.dp),
    ) {
        HorizontalFloatingToolbar(
            expanded = true,
            floatingActionButton = {
                FloatingToolbarDefaults.VibrantFloatingActionButton(
                    onClick = onMoreClick,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            },
            colors =
                FloatingToolbarDefaults.standardFloatingToolbarColors(
                    toolbarContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
        ) {
            HistoryToolbarAction(
                icon = if (allVisibleSelected) R.drawable.deselect else R.drawable.select_all,
                label = stringResource(if (allVisibleSelected) R.string.clear_selection else R.string.select),
                onClick = onToggleAll,
            )
        }
    }
}

@Composable
private fun HistoryToolbarAction(
    icon: Int,
    label: String,
    onClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier =
            Modifier
                .clip(MaterialTheme.shapes.large)
                .clickable(role = Role.Button, onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

private fun filterLocalEvents(
    events: Map<DateAgo, List<EventWithSong>>,
    query: String,
): Map<DateAgo, List<EventWithSong>> {
    if (query.isBlank()) return events

    return events
        .mapValues { (_, songs) ->
            songs.filter { event ->
                event.song.song.title
                    .contains(query, ignoreCase = true) ||
                    event.song.artists.any { artist ->
                        artist.name.contains(query, ignoreCase = true)
                    }
            }
        }.filterValues { it.isNotEmpty() }
}

private fun filterRemoteSections(
    sections: List<HistoryPage.HistorySection>,
    query: String,
): List<HistoryPage.HistorySection> {
    if (query.isBlank()) return sections

    return sections
        .map { section ->
            section.copy(
                songs =
                    section.songs.filter { song ->
                        song.title.contains(query, ignoreCase = true) ||
                            song.artists.any { artist ->
                                artist.name.contains(query, ignoreCase = true)
                            }
                    },
            )
        }.filter { it.songs.isNotEmpty() }
}

private const val HISTORY_LOAD_MORE_THRESHOLD = 12
