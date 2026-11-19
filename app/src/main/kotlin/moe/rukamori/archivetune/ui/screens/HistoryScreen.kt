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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.snap
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.animateFloatingActionButton
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
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
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.pages.HistoryPage
import moe.rukamori.archivetune.innertube.utils.hasYouTubeLoginCookie
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.TopSearch
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.appBarScrollBehavior
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.DateAgo
import moe.rukamori.archivetune.viewmodels.HistoryViewModel
import moe.rukamori.archivetune.viewmodels.RemoteHistoryUiState
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
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
    val motion = MaterialTheme.motionScheme
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
    val playedAtFormatter = remember { DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT) }

    val currentVisibleCount =
        if (historySource == HistorySource.REMOTE) {
            remoteVisibleSongs.size
        } else {
            localVisibleEvents.size
        }

    val localHeroEvent =
        remember(filteredEvents) {
            filteredEvents.entries.firstOrNull()?.let { (dateAgo, songs) ->
                songs.firstOrNull()?.let { dateAgo to it }
            }
        }
    val remoteHeroSection = filteredRemoteSections.firstOrNull()?.takeIf { it.songs.isNotEmpty() }
    val heroTrack =
        if (searchQuery.isNotBlank()) {
            null
        } else if (historySource == HistorySource.REMOTE) {
            remoteHeroSection?.let { section ->
                val song = section.songs.first()
                HistoryHeroTrack(
                    id = song.id,
                    title = song.title,
                    artists = song.artists.joinToString { it.name },
                    thumbnailUrl = song.thumbnail.heroArtwork(),
                    playedLabel = section.title,
                )
            }
        } else {
            localHeroEvent?.let { (dateAgo, event) ->
                HistoryHeroTrack(
                    id = event.song.id,
                    title = event.song.song.title,
                    artists = event.song.artists.joinToString { it.name },
                    thumbnailUrl = event.song.song.thumbnailUrl?.heroArtwork(),
                    playedLabel =
                        stringResource(
                            R.string.history_played_at,
                            dateAgoToString(dateAgo),
                            event.event.timestamp.format(playedAtFormatter),
                        ),
                )
            }
        }
    val isHeroActive = heroTrack != null && heroTrack.id == mediaMetadata?.id

    val playLocalSection: (DateAgo, List<EventWithSong>, Int) -> Unit = { dateAgo, songsForDate, startIndex ->
        playerConnection.playQueue(
            ListQueue(
                title = dateAgoToString(dateAgo),
                items = songsForDate.map { it.song.toMediaItem() },
                startIndex = startIndex,
            ),
        )
    }
    val playRemoteSection: (HistoryPage.HistorySection) -> Unit = { section ->
        playerConnection.playQueue(
            ListQueue(
                title = section.title,
                items = section.songs.map { it.toMediaItem() },
            ),
        )
    }
    val shuffleVisible: () -> Unit = {
        val items =
            if (historySource == HistorySource.REMOTE) {
                remoteVisibleSongs.map { it.toMediaItem() }
            } else {
                localVisibleEvents.map { it.song.toMediaItem() }
            }
        if (items.isNotEmpty()) {
            playerConnection.playQueue(
                ListQueue(
                    title = context.getString(R.string.history),
                    items = items.shuffled(),
                ),
            )
        }
    }
    val playHero: () -> Unit = {
        if (isHeroActive) {
            playerConnection.player.togglePlayPause()
        } else if (historySource == HistorySource.REMOTE) {
            remoteHeroSection?.songs?.firstOrNull()?.let { song ->
                playerConnection.playQueue(YouTubeQueue.radio(song.toMediaMetadata()))
            }
        } else {
            localHeroEvent?.let { (dateAgo, _) ->
                playLocalSection(dateAgo, filteredEvents.getValue(dateAgo), 0)
            }
        }
    }

    val historyOverview: @Composable () -> Unit = {
        HistoryOverview(
            hero = heroTrack,
            isHeroActive = isHeroActive,
            isPlaying = isPlaying,
            currentSource = historySource,
            availableSources = availableSources,
            visibleSongCount = currentVisibleCount,
            onHeroPlayClick = playHero,
            onShuffleClick = shuffleVisible,
            onSourceChange = { newSource ->
                if (newSource == historySource) return@HistoryOverview

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
        AnimatedContent(
            targetState = historySource,
            transitionSpec = {
                if (animationsDisabled) {
                    fadeIn(snap()) togetherWith fadeOut(snap())
                } else {
                    fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
                }
            },
            label = "HistorySourceContent",
        ) { source ->
            when (source) {
                HistorySource.REMOTE -> {
                    RemoteHistoryFeed(
                        listState = remoteListState,
                        topPadding = topPadding,
                        headerContent = historyOverview,
                        remoteHistoryState = remoteHistoryState,
                        filteredSections = filteredRemoteSections,
                        isPlaying = isPlaying,
                        activeMediaId = mediaMetadata?.id,
                        onRetry = viewModel::fetchRemoteHistory,
                        onPlaySection = playRemoteSection,
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
                        headerContent = historyOverview,
                        filteredEvents = filteredEvents,
                        visibleEvents = localVisibleEvents,
                        isLoadingMore = isLoadingMoreEvents,
                        canLoadMore = canLoadMoreEvents,
                        isSearchActive = searchQuery.isNotBlank(),
                        selectedEventIds = selectedEventIdSet,
                        isPlaying = isPlaying,
                        activeMediaId = mediaMetadata?.id,
                        dateAgoToString = dateAgoToString,
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
                                playLocalSection(dateAgo, songsForDate, index)
                            }
                        },
                        onPlaySection = { dateAgo, songsForDate ->
                            playLocalSection(dateAgo, songsForDate, 0)
                        },
                        onLoadMore = viewModel::loadMoreEvents,
                    )
                }
            }
        }
    }

    val showShuffleFab by remember(activeListState) {
        derivedStateOf { activeListState.firstVisibleItemIndex > 0 }
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
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text =
                                if (selectionCount > 0) {
                                    pluralStringResource(R.plurals.n_selected, selectionCount, selectionCount)
                                } else {
                                    stringResource(R.string.history)
                                },
                            fontWeight = FontWeight.Bold,
                        )
                    },
                    subtitle = {
                        Text(
                            text =
                                stringResource(
                                    if (historySource == HistorySource.LOCAL) {
                                        R.string.history_local_summary
                                    } else {
                                        R.string.history_remote_summary
                                    },
                                ),
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
                                contentDescription =
                                    stringResource(
                                        if (selectionCount > 0) R.string.clear_selection else R.string.back_button_desc,
                                    ),
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
                                    contentDescription = stringResource(R.string.search),
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
            ExtendedFloatingActionButton(
                text = { Text(text = stringResource(R.string.shuffle)) },
                icon = {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = null,
                    )
                },
                onClick = shuffleVisible,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier =
                    Modifier
                        .windowInsetsPadding(
                            LocalPlayerAwareWindowInsets.current.only(
                                WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal,
                            ),
                        ).animateFloatingActionButton(
                            visible = showShuffleFab && !showSearchBar && selectionCount == 0 && currentVisibleCount > 0,
                            alignment = Alignment.BottomEnd,
                        ),
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            if (!showSearchBar) {
                historyContent(innerPadding.calculateTopPadding())
            }

            AnimatedVisibility(
                visible = showSearchBar,
                enter = if (animationsDisabled) fadeIn(snap()) else fadeIn(motion.defaultEffectsSpec()),
                exit = if (animationsDisabled) fadeOut(snap()) else fadeOut(motion.fastEffectsSpec()),
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
                                contentDescription = stringResource(R.string.back_button_desc),
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
                                    contentDescription = stringResource(R.string.history_clear_search),
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
                onPlaySelection = {
                    if (selectedSongs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = context.getString(R.string.history),
                                items = selectedSongs.map { it.song.toMediaItem() },
                            ),
                        )
                        clearSelection()
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
    onToggleSelection: (Long) -> Unit,
    onStartSelection: (Long) -> Unit,
    onSongMenu: (EventWithSong) -> Unit,
    onSongClick: (DateAgo, List<EventWithSong>, Int, EventWithSong) -> Unit,
    onPlaySection: (DateAgo, List<EventWithSong>) -> Unit,
    onLoadMore: () -> Unit,
) {
    val isSelectionMode = selectedEventIds.isNotEmpty()
    val itemMotion = rememberHistoryItemMotion()

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
        modifier = historyFeedModifier(topPadding),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item(key = "history_overview", contentType = "history_overview") {
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
                HistoryStatePanel(
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
            var chapterIndex = 0
            filteredEvents.forEach { (dateAgo, songsForDate) ->
                val headerIndex = chapterIndex++
                stickyHeader(key = "header_$dateAgo", contentType = "history_chapter_header") {
                    val artistsLine =
                        remember(songsForDate) {
                            songsForDate.artistsLine { event -> event.song.artists.map { it.name } }
                        }
                    HistoryChapterHeader(
                        title = dateAgoToString(dateAgo),
                        songCount = songsForDate.size,
                        artistsLine = artistsLine,
                        chapterIndex = headerIndex,
                        containsActive = activeMediaId != null && songsForDate.any { it.song.id == activeMediaId },
                        onPlayAll = { onPlaySection(dateAgo, songsForDate) },
                    )
                }

                itemsIndexed(
                    items = songsForDate,
                    key = { _, event -> event.event.id },
                    contentType = { _, _ -> "local_history_song" },
                ) { index, event ->
                    val isActive = event.song.id == activeMediaId
                    val isSelected = event.event.id in selectedEventIds
                    HistoryTrackSurface(
                        index = index,
                        lastIndex = songsForDate.lastIndex,
                        isSelected = isSelected,
                        isActive = isActive,
                        modifier = itemMotion(),
                    ) { containerColor ->
                        SongListItem(
                            song = event.song,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            showInLibraryIcon = true,
                            isSelected = isSelected,
                            swipeContentBackgroundColor = containerColor,
                            showActiveContainer = false,
                            trailingContent = {
                                androidx.compose.material3.IconButton(
                                    onClick = { onSongMenu(event) },
                                    enabled = !isSelectionMode,
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.more_vert),
                                        contentDescription = stringResource(R.string.more_options),
                                    )
                                }
                            },
                            modifier =
                                Modifier
                                    .fillMaxWidth()
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
                                    ),
                        )
                    }
                }
            }

            if (canLoadMore) {
                item("local_history_loading_more") {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (isLoadingMore) {
                            LoadingIndicator()
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
    onRetry: () -> Unit,
    onPlaySection: (HistoryPage.HistorySection) -> Unit,
    onSongMenu: (SongItem) -> Unit,
    onSongClick: (SongItem) -> Unit,
) {
    val itemMotion = rememberHistoryItemMotion()

    LazyColumn(
        state = listState,
        modifier = historyFeedModifier(topPadding),
        contentPadding = PaddingValues(bottom = 112.dp),
    ) {
        item(key = "history_overview", contentType = "history_overview") {
            headerContent()
        }

        when (remoteHistoryState) {
            RemoteHistoryUiState.Loading -> {
                item("remote_history_loading") {
                    HistoryStatePanel(
                        title = stringResource(R.string.history_remote_loading),
                        description = stringResource(R.string.history_remote_summary),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        loading = true,
                    )
                }
            }

            RemoteHistoryUiState.Empty -> {
                item("remote_history_empty") {
                    HistoryStatePanel(
                        title = stringResource(R.string.history_remote_empty_title),
                        description = stringResource(R.string.history_remote_empty_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        icon = R.drawable.history,
                    )
                }
            }

            RemoteHistoryUiState.Error -> {
                item("remote_history_error") {
                    HistoryStatePanel(
                        title = stringResource(R.string.history_remote_error_title),
                        description = stringResource(R.string.history_remote_error_desc),
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        actionLabel = stringResource(R.string.retry),
                        onActionClick = onRetry,
                        icon = R.drawable.sync,
                    )
                }
            }

            is RemoteHistoryUiState.Success -> {
                if (filteredSections.isEmpty()) {
                    item("remote_history_search_empty") {
                        HistoryStatePanel(
                            title = stringResource(R.string.history_no_results_title),
                            description = stringResource(R.string.history_no_results_desc),
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            icon = R.drawable.search,
                        )
                    }
                } else {
                    filteredSections.forEachIndexed { sectionIndex, section ->
                        stickyHeader(key = "header_${section.title}", contentType = "history_chapter_header") {
                            val artistsLine =
                                remember(section.songs) {
                                    section.songs.artistsLine { song -> song.artists.map { it.name } }
                                }
                            HistoryChapterHeader(
                                title = section.title,
                                songCount = section.songs.size,
                                artistsLine = artistsLine,
                                chapterIndex = sectionIndex,
                                containsActive = activeMediaId != null && section.songs.any { it.id == activeMediaId },
                                onPlayAll = { onPlaySection(section) },
                            )
                        }

                        itemsIndexed(
                            items = section.songs,
                            key = { _, song -> "${section.title}_${song.id}" },
                            contentType = { _, _ -> "remote_history_song" },
                        ) { index, song ->
                            val isActive = song.id == activeMediaId
                            HistoryTrackSurface(
                                index = index,
                                lastIndex = section.songs.lastIndex,
                                isActive = isActive,
                                modifier = itemMotion(),
                            ) { containerColor ->
                                YouTubeListItem(
                                    item = song,
                                    isActive = isActive,
                                    isPlaying = isPlaying,
                                    swipeContentBackgroundColor = containerColor,
                                    showActiveContainer = false,
                                    trailingContent = {
                                        androidx.compose.material3.IconButton(
                                            onClick = { onSongMenu(song) },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.more_vert),
                                                contentDescription = stringResource(R.string.more_options),
                                            )
                                        }
                                    },
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .combinedClickable(
                                                onClick = { onSongClick(song) },
                                                onLongClick = { onSongMenu(song) },
                                            ),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun historyFeedModifier(topPadding: Dp): Modifier =
    Modifier
        .fillMaxSize()
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = 840.dp)
        .padding(top = topPadding)
        .windowInsetsPadding(
            LocalPlayerAwareWindowInsets.current.only(
                WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
            ),
        )

@Composable
private fun rememberHistoryItemMotion(): LazyItemScope.() -> Modifier {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val fadeInSpec = motion.defaultEffectsSpec<Float>()
    val placementSpec = motion.defaultSpatialSpec<IntOffset>()
    val fadeOutSpec = motion.fastEffectsSpec<Float>()
    return remember(animationsDisabled, fadeInSpec, placementSpec, fadeOutSpec) {
        val itemModifier: LazyItemScope.() -> Modifier = {
            if (animationsDisabled) {
                Modifier
            } else {
                Modifier.animateItem(
                    fadeInSpec = fadeInSpec,
                    placementSpec = placementSpec,
                    fadeOutSpec = fadeOutSpec,
                )
            }
        }
        itemModifier
    }
}

private inline fun <T> List<T>.artistsLine(crossinline artistNames: (T) -> List<String>): String =
    asSequence()
        .flatMap { artistNames(it).asSequence() }
        .filter { it.isNotBlank() }
        .distinct()
        .take(HISTORY_CHAPTER_ARTIST_PREVIEW)
        .joinToString()

private fun String.heroArtwork(): String =
    resize(
        width = HISTORY_HERO_ARTWORK_PX,
        height = HISTORY_HERO_ARTWORK_PX,
        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
    )

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
private const val HISTORY_CHAPTER_ARTIST_PREVIEW = 3
private const val HISTORY_HERO_ARTWORK_PX = 544
