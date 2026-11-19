/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberTooltipState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ALBUM
import moe.rukamori.archivetune.constants.CONTENT_TYPE_ARTIST
import moe.rukamori.archivetune.constants.CONTENT_TYPE_HEADER
import moe.rukamori.archivetune.constants.CONTENT_TYPE_SONG
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.localmedia.LocalSongScanSummary
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.PlayingIndicatorBox
import moe.rukamori.archivetune.ui.component.PlaylistThumbnail
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.screens.settings.LocalSongSettingsRoute
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.viewmodels.LocalAlbumGroup
import moe.rukamori.archivetune.viewmodels.LocalArtistGroup
import moe.rukamori.archivetune.viewmodels.LocalLibraryAction
import moe.rukamori.archivetune.viewmodels.LocalLibraryFocus
import moe.rukamori.archivetune.viewmodels.LocalLibrarySort
import moe.rukamori.archivetune.viewmodels.LocalLibraryStats
import moe.rukamori.archivetune.viewmodels.LocalLibraryUiState
import moe.rukamori.archivetune.viewmodels.LocalLibraryView
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsAction
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsEvent
import moe.rukamori.archivetune.viewmodels.LocalSongSettingsState
import moe.rukamori.archivetune.viewmodels.LocalSongSortType
import moe.rukamori.archivetune.viewmodels.LocalSongsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LocalSongScreen(
    navController: NavController,
    viewModel: LocalSongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val menuState = LocalMenuState.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val state by viewModel.libraryState.collectAsStateWithLifecycle()
    val settingsState by viewModel.settingsState.collectAsStateWithLifecycle()
    val onAction = remember(viewModel) { viewModel::onLibraryAction }
    val onSettingsAction = remember(viewModel) { viewModel::onSettingsAction }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    var isSearchActive by rememberSaveable { mutableStateOf(viewModel.query.isNotEmpty()) }
    val ready = state as? LocalLibraryUiState.Ready
    val focus = ready?.focus
    val mainListState = rememberLazyListState()
    val focusListState = rememberSaveable(focus?.key, saver = LazyListState.Saver) { LazyListState() }
    val listState = if (focus == null) mainListState else focusListState
    val scanStatus = remember(settingsState) { settingsState.toScanStatus() }
    val permission =
        remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_AUDIO
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        }
    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            onSettingsAction(LocalSongSettingsAction.PermissionResult(granted))
        }
    val openScanSettings =
        remember(navController) {
            { navController.navigate(LocalSongSettingsRoute) { launchSingleTop = true } }
        }
    val scanDevice = remember(onSettingsAction) { { onSettingsAction(LocalSongSettingsAction.Scan) } }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.onLibraryOpened() }

    LaunchedEffect(viewModel, lifecycleOwner, permission) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.settingsEvents.collect { event ->
                try {
                    when (event) {
                        LocalSongSettingsEvent.RequestAudioPermission -> permissionLauncher.launch(permission)
                        LocalSongSettingsEvent.OpenAppSettings ->
                            context.startActivity(
                                Intent(
                                    Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                    Uri.parse("package:${context.packageName}"),
                                ),
                            )
                        LocalSongSettingsEvent.NavigateBack -> navController.navigateUp()
                        is LocalSongSettingsEvent.PickFolder ->
                            onSettingsAction(LocalSongSettingsAction.FolderSelected(event.included, null))
                    }
                } catch (_: ActivityNotFoundException) {
                    onSettingsAction(LocalSongSettingsAction.ExternalActionFailed)
                } catch (_: SecurityException) {
                    onSettingsAction(LocalSongSettingsAction.ExternalActionFailed)
                }
            }
        }
    }

    val closeSearch = {
        onAction(LocalLibraryAction.QueryChanged(""))
        isSearchActive = false
    }
    BackHandler(enabled = isSearchActive || focus != null) {
        if (isSearchActive) closeSearch() else onAction(LocalLibraryAction.CloseFocus)
    }

    val queueTitle =
        when {
            focus is LocalLibraryFocus.Album -> focus.album.title
            focus is LocalLibraryFocus.Artist -> focus.artist.name
            viewModel.query.isNotBlank() -> stringResource(R.string.queue_searched_songs)
            else -> stringResource(R.string.local_history)
        }
    val playSongs: (List<Song>, Int, Boolean, String) -> Unit =
        remember(playerConnection) {
            { songs, startIndex, shuffle, title ->
                if (songs.isNotEmpty()) {
                    val items = songs.map { it.toMediaItem() }
                    playerConnection.playQueue(
                        ListQueue(
                            title = title,
                            items = if (shuffle) items.shuffled() else items,
                            startIndex = if (shuffle) 0 else startIndex.coerceIn(0, items.lastIndex),
                        ),
                    )
                }
            }
        }
    val showSongMenu: (Song) -> Unit = { song ->
        menuState.show {
            SongMenu(
                originalSong = song,
                navController = navController,
                onDismiss = menuState::dismiss,
            )
        }
    }
    val heroScrolledAway by remember(listState) { derivedStateOf { listState.firstVisibleItemIndex > 0 } }
    val hasLibrary = (ready?.stats?.songCount ?: 0) > 0
    val canShuffleFromFab = ready != null && ready.songs.isNotEmpty() && (focus != null || ready.view == LocalLibraryView.SONGS)
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current

    Scaffold(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0.dp),
        topBar = {
            LocalLibraryTopBar(
                searchActive = isSearchActive,
                query = viewModel.query,
                title =
                    when (focus) {
                        is LocalLibraryFocus.Album -> focus.album.title
                        is LocalLibraryFocus.Artist -> focus.artist.name
                        null -> stringResource(R.string.local_history)
                    },
                titleVisible = !hasLibrary || heroScrolledAway,
                focused = focus != null,
                scrollBehavior = scrollBehavior,
                onQueryChange = { onAction(LocalLibraryAction.QueryChanged(it)) },
                onOpenSearch = { isSearchActive = true },
                onCloseSearch = closeSearch,
                onBack = {
                    if (focus != null) onAction(LocalLibraryAction.CloseFocus) else navController.navigateUp()
                },
                onOpenSettings = openScanSettings,
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = canShuffleFromFab && heroScrolledAway,
                enter =
                    if (animationsDisabled) {
                        EnterTransition.None
                    } else {
                        scaleIn(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec())
                    },
                exit =
                    if (animationsDisabled) {
                        ExitTransition.None
                    } else {
                        scaleOut(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec())
                    },
                modifier =
                    Modifier.windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                    ),
            ) {
                FloatingActionButton(
                    onClick = { ready?.let { playSongs(it.songs, 0, true, queueTitle) } },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.shuffle),
                        contentDescription = stringResource(R.string.shuffle),
                    )
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
    ) { innerPadding ->
        ExpressivePullToRefreshBox(
            isRefreshing = scanStatus == ScanStatus.Scanning,
            onRefresh = scanDevice,
            indicatorOffset = 0.dp,
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding()),
        ) {
            when (val current = state) {
                LocalLibraryUiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        ContainedLoadingIndicator()
                    }
                }

                is LocalLibraryUiState.Error -> {
                    LocalLibraryMessage(
                        icon = R.drawable.error,
                        title = stringResource(current.message),
                        body = null,
                        primaryLabel = stringResource(R.string.retry),
                        onPrimary = { onAction(LocalLibraryAction.Retry) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                is LocalLibraryUiState.Ready -> {
                    LocalLibraryList(
                        state = current,
                        listState = listState,
                        scanStatus = scanStatus,
                        activeSongId = mediaMetadata?.id,
                        isPlaying = isPlaying,
                        onAction = onAction,
                        onScan = scanDevice,
                        onOpenSettings = openScanSettings,
                        onPlay = { songs, index, shuffle -> playSongs(songs, index, shuffle, queueTitle) },
                        onPlayAlbum = { album ->
                            playSongs(current.albumTracks[album.key].orEmpty(), 0, false, album.title)
                        },
                        onSongClick = { songs, index ->
                            val song = songs[index]
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playSongs(songs, index, false, queueTitle)
                            }
                        },
                        onSongMenu = showSongMenu,
                        onSongLongClick = { song ->
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showSongMenu(song)
                        },
                        onPlayNext = { songs ->
                            playerConnection.playNext(songs.map { it.toMediaItem() })
                            val message =
                                context.resources.getQuantityString(R.plurals.local_library_queued_next, songs.size, songs.size)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        onAddToQueue = { songs ->
                            playerConnection.addToQueue(songs.map { it.toMediaItem() })
                            val message =
                                context.resources.getQuantityString(R.plurals.local_library_added_to_queue, songs.size, songs.size)
                            scope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                snackbarHostState.showSnackbar(message)
                            }
                        },
                        onClearSearch = closeSearch,
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalLibraryTopBar(
    searchActive: Boolean,
    query: String,
    title: String,
    titleVisible: Boolean,
    focused: Boolean,
    scrollBehavior: TopAppBarScrollBehavior,
    onQueryChange: (String) -> Unit,
    onOpenSearch: () -> Unit,
    onCloseSearch: () -> Unit,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedContent(
        targetState = searchActive,
        transitionSpec = {
            if (animationsDisabled) {
                EnterTransition.None togetherWith ExitTransition.None
            } else {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            }
        },
        label = "localLibraryTopBar",
    ) { searching ->
        if (searching) {
            val focusRequester = remember { FocusRequester() }
            LaunchedEffect(focusRequester) { focusRequester.requestFocus() }
            SearchBar(
                inputField = {
                    SearchBarDefaults.InputField(
                        query = query,
                        onQueryChange = onQueryChange,
                        onSearch = {},
                        expanded = false,
                        onExpandedChange = {},
                        placeholder = { Text(text = stringResource(R.string.search_library)) },
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
                                    IconButton(onClick = { onQueryChange("") }) {
                                        Icon(
                                            painter = painterResource(R.drawable.close),
                                            contentDescription = stringResource(R.string.local_library_clear_search),
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        modifier = Modifier.focusRequester(focusRequester),
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
            TopAppBar(
                title = {
                    AnimatedVisibility(
                        visible = titleVisible,
                        enter =
                            if (animationsDisabled) {
                                EnterTransition.None
                            } else {
                                fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.defaultSpatialSpec()) { it / 2 }
                            },
                        exit =
                            if (animationsDisabled) {
                                ExitTransition.None
                            } else {
                                fadeOut(motion.fastEffectsSpec()) + slideOutVertically(motion.fastSpatialSpec()) { it / 2 }
                            },
                    ) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleLargeEmphasized,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription =
                                stringResource(
                                    if (focused) R.string.local_library_close_focus else R.string.back_button_desc,
                                ),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSearch) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = stringResource(R.string.search),
                        )
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            painter = painterResource(R.drawable.settings),
                            contentDescription = stringResource(R.string.local_library_scan_settings),
                        )
                    }
                },
                colors =
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                scrollBehavior = scrollBehavior,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalLibraryList(
    state: LocalLibraryUiState.Ready,
    listState: LazyListState,
    scanStatus: ScanStatus,
    activeSongId: String?,
    isPlaying: Boolean,
    onAction: (LocalLibraryAction) -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    onPlay: (List<Song>, Int, Boolean) -> Unit,
    onPlayAlbum: (LocalAlbumGroup) -> Unit,
    onSongClick: (List<Song>, Int) -> Unit,
    onSongMenu: (Song) -> Unit,
    onSongLongClick: (Song) -> Unit,
    onPlayNext: (List<Song>) -> Unit,
    onAddToQueue: (List<Song>) -> Unit,
    onClearSearch: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val fadeSpec = if (animationsDisabled) null else motion.defaultEffectsSpec<Float>()
    val placementSpec = if (animationsDisabled) null else motion.defaultSpatialSpec<IntOffset>()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding() + 88.dp
    val focus = state.focus
    val localPlaying = isPlaying && activeSongId != null && activeSongId in state.localSongIds
    val focusPlaying = localPlaying && state.songs.any { it.id == activeSongId }
    val stickyColor by animateColorAsState(
        targetValue =
            if (listState.canScrollBackward) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.surface
            },
        animationSpec = motion.defaultEffectsSpec(),
        label = "localLibraryStickyColor",
    )

    if (state.stats.songCount == 0) {
        LocalLibraryEmptyLibrary(
            scanStatus = scanStatus,
            onScan = onScan,
            onOpenSettings = onOpenSettings,
            modifier = Modifier.fillMaxSize(),
        )
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val columns = albumColumns(maxWidth)
        val tileWidth = (maxWidth - AlbumGridPadding * 2 - AlbumGridSpacing * (columns - 1)) / columns
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = bottomPadding),
        ) {
            item(key = "hero", contentType = CONTENT_TYPE_HEADER) {
                AnimatedContent(
                    targetState = focus,
                    contentKey = { it?.key },
                    transitionSpec = {
                        if (animationsDisabled) {
                            ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
                        } else {
                            (
                                fadeIn(motion.defaultEffectsSpec()) +
                                    slideInVertically(motion.defaultSpatialSpec()) { it / 8 }
                            ) togetherWith fadeOut(motion.fastEffectsSpec()) using
                                SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
                        }
                    },
                    label = "localLibraryHero",
                ) { targetFocus ->
                    if (targetFocus == null) {
                        LibraryHero(
                            stats = state.stats,
                            view = state.view,
                            spinning = localPlaying,
                            scanStatus = scanStatus,
                            playEnabled = state.songs.isNotEmpty(),
                            onPlay = { onPlay(state.songs, 0, false) },
                            onShuffle = { onPlay(state.songs, 0, true) },
                            onScan = onScan,
                            onOpenSettings = onOpenSettings,
                        )
                    } else {
                        FocusHero(
                            focus = targetFocus,
                            spinning = focusPlaying,
                            actionsEnabled = state.songs.isNotEmpty(),
                            onPlay = { onPlay(state.songs, 0, false) },
                            onShuffle = { onPlay(state.songs, 0, true) },
                            onPlayNext = { onPlayNext(state.songs) },
                            onAddToQueue = { onAddToQueue(state.songs) },
                        )
                    }
                }
            }

            if (focus == null) {
                stickyHeader(key = "controls", contentType = CONTENT_TYPE_HEADER) {
                    Surface(color = stickyColor, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.padding(bottom = 4.dp)) {
                            LibraryViewSwitcher(
                                selected = state.view,
                                onSelect = { onAction(LocalLibraryAction.SelectView(it)) },
                            )
                            AnimatedVisibility(
                                visible = state.view == LocalLibraryView.SONGS,
                                enter = if (animationsDisabled) EnterTransition.None else fadeIn(motion.defaultEffectsSpec()),
                                exit = if (animationsDisabled) ExitTransition.None else fadeOut(motion.fastEffectsSpec()),
                            ) {
                                SongSortBar(
                                    sort = state.sort,
                                    songCount = state.songs.size,
                                    onAction = onAction,
                                )
                            }
                        }
                    }
                }
            } else if (focus is LocalLibraryFocus.Artist) {
                stickyHeader(key = "controls", contentType = CONTENT_TYPE_HEADER) {
                    Surface(color = stickyColor, modifier = Modifier.fillMaxWidth()) {
                        SongSortBar(
                            sort = state.sort,
                            songCount = state.songs.size,
                            onAction = onAction,
                        )
                    }
                }
            }

            val view = if (focus != null) LocalLibraryView.SONGS else state.view
            val searching = state.query.isNotBlank()
            when (view) {
                LocalLibraryView.SONGS -> {
                    if (state.songs.isEmpty()) {
                        messageItem(
                            icon = R.drawable.search,
                            title = R.string.local_songs_no_matches_title,
                            body = R.string.local_songs_no_matches_desc,
                            onClearSearch = onClearSearch.takeIf { searching },
                        )
                    } else {
                        itemsIndexed(
                            items = state.songs,
                            key = { _, song -> "song:${song.id}" },
                            contentType = { _, _ -> CONTENT_TYPE_SONG },
                        ) { index, song ->
                            LocalSongRow(
                                song = song,
                                index = index,
                                count = state.songs.size,
                                showTrackNumber = focus is LocalLibraryFocus.Album,
                                isActive = song.id == activeSongId,
                                isPlaying = isPlaying,
                                onClick = { onSongClick(state.songs, index) },
                                onLongClick = { onSongLongClick(song) },
                                onMenu = { onSongMenu(song) },
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = fadeSpec,
                                        placementSpec = placementSpec,
                                        fadeOutSpec = fadeSpec,
                                    ),
                            )
                        }
                    }
                }

                LocalLibraryView.ALBUMS -> {
                    if (state.albums.isEmpty()) {
                        messageItem(
                            icon = if (searching) R.drawable.search else R.drawable.album,
                            title = if (searching) R.string.local_songs_no_matches_title else R.string.local_library_no_albums_title,
                            body = if (searching) R.string.local_songs_no_matches_desc else R.string.local_library_no_albums_desc,
                            onClearSearch = onClearSearch.takeIf { searching },
                        )
                    } else {
                        val rows = state.albums.chunked(columns)
                        rows.forEachIndexed { rowIndex, row ->
                            item(key = "albums:${row.first().key}", contentType = CONTENT_TYPE_ALBUM) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(AlbumGridSpacing),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = AlbumGridPadding)
                                            .padding(top = if (rowIndex == 0) 8.dp else 0.dp, bottom = AlbumGridSpacing)
                                            .animateItem(
                                                fadeInSpec = fadeSpec,
                                                placementSpec = placementSpec,
                                                fadeOutSpec = fadeSpec,
                                            ),
                                ) {
                                    row.forEach { album ->
                                        AlbumTile(
                                            album = album,
                                            width = tileWidth,
                                            onOpen = { onAction(LocalLibraryAction.OpenAlbum(album.key)) },
                                            onPlay = { onPlayAlbum(album) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                LocalLibraryView.ARTISTS -> {
                    if (state.artists.isEmpty()) {
                        messageItem(
                            icon = if (searching) R.drawable.search else R.drawable.artist,
                            title = if (searching) R.string.local_songs_no_matches_title else R.string.local_library_no_artists_title,
                            body = if (searching) R.string.local_songs_no_matches_desc else R.string.local_library_no_artists_desc,
                            onClearSearch = onClearSearch.takeIf { searching },
                        )
                    } else {
                        itemsIndexed(
                            items = state.artists,
                            key = { _, artist -> "artist:${artist.id}" },
                            contentType = { _, _ -> CONTENT_TYPE_ARTIST },
                        ) { index, artist ->
                            LocalArtistRow(
                                artist = artist,
                                index = index,
                                count = state.artists.size,
                                onClick = { onAction(LocalLibraryAction.OpenArtist(artist.id)) },
                                modifier =
                                    Modifier.animateItem(
                                        fadeInSpec = fadeSpec,
                                        placementSpec = placementSpec,
                                        fadeOutSpec = fadeSpec,
                                    ),
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun LazyListScope.messageItem(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    @StringRes body: Int,
    onClearSearch: (() -> Unit)?,
) {
    item(key = "message", contentType = CONTENT_TYPE_HEADER) {
        LocalLibraryMessage(
            icon = icon,
            title = stringResource(title),
            body = stringResource(body),
            primaryLabel = onClearSearch?.let { stringResource(R.string.local_library_clear_search) },
            onPrimary = onClearSearch,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 24.dp)
                    .animateItem(),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryHero(
    stats: LocalLibraryStats,
    view: LocalLibraryView,
    spinning: Boolean,
    scanStatus: ScanStatus,
    playEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.local_library_overline),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.local_history),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text =
                        stringResource(
                            R.string.local_library_stats,
                            pluralStringResource(R.plurals.n_song, stats.songCount, stats.songCount),
                            pluralStringResource(R.plurals.n_album, stats.albumCount, stats.albumCount),
                            pluralStringResource(R.plurals.n_artist, stats.artistCount, stats.artistCount),
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (stats.durationSeconds > 0) {
                    Text(
                        text = formatLibraryDuration(stats.durationSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            HeroArtwork(
                polygon = view.heroPolygon(),
                thumbnails = stats.artwork,
                spinning = spinning,
                placeholderIcon = R.drawable.library_music,
                haloColor = MaterialTheme.colorScheme.tertiaryContainer,
            )
        }
        Spacer(modifier = Modifier.size(20.dp))
        HeroPrimaryActions(enabled = playEnabled, onPlay = onPlay, onShuffle = onShuffle)
        ScanStatusRow(status = scanStatus, onScan = onScan, onOpenSettings = onOpenSettings)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FocusHero(
    focus: LocalLibraryFocus,
    spinning: Boolean,
    actionsEnabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
) {
    val overline: String
    val title: String
    val subtitle: String?
    val meta: String
    val thumbnail: String?
    val polygon: RoundedPolygon
    @DrawableRes val placeholderIcon: Int
    when (focus) {
        is LocalLibraryFocus.Album -> {
            val album = focus.album
            overline = stringResource(R.string.local_library_focus_album)
            title = album.title
            subtitle = listOfNotNull(album.artist, album.year?.toString()).joinToString(" · ").ifEmpty { null }
            meta = songMeta(album.songCount, album.durationSeconds)
            thumbnail = album.thumbnailUrl
            polygon = MaterialShapes.Cookie9Sided
            placeholderIcon = R.drawable.album
        }
        is LocalLibraryFocus.Artist -> {
            val artist = focus.artist
            overline = stringResource(R.string.local_library_focus_artist)
            title = artist.name
            subtitle =
                if (artist.albumCount > 0) {
                    pluralStringResource(R.plurals.n_album, artist.albumCount, artist.albumCount)
                } else {
                    null
                }
            meta = songMeta(artist.songCount, artist.durationSeconds)
            thumbnail = artist.thumbnailUrl
            polygon = MaterialShapes.Circle
            placeholderIcon = R.drawable.artist
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(top = 8.dp, bottom = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            HeroArtwork(
                polygon = polygon,
                thumbnails = listOfNotNull(thumbnail),
                spinning = spinning,
                placeholderIcon = placeholderIcon,
                haloColor = MaterialTheme.colorScheme.secondaryContainer,
            )
            Spacer(modifier = Modifier.width(20.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = overline,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.semantics { heading() },
                )
                subtitle?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(modifier = Modifier.size(20.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            HeroButton(
                icon = R.drawable.play,
                label = stringResource(R.string.play),
                enabled = actionsEnabled,
                tonal = false,
                onClick = onPlay,
                modifier = Modifier.weight(1f),
            )
            HeroIconAction(
                icon = R.drawable.shuffle,
                label = stringResource(R.string.shuffle),
                enabled = actionsEnabled,
                onClick = onShuffle,
            )
            HeroIconAction(
                icon = R.drawable.playlist_play,
                label = stringResource(R.string.play_next),
                enabled = actionsEnabled,
                onClick = onPlayNext,
            )
            HeroIconAction(
                icon = R.drawable.queue_music,
                label = stringResource(R.string.add_to_queue),
                enabled = actionsEnabled,
                onClick = onAddToQueue,
            )
        }
    }
}

@Composable
private fun HeroPrimaryActions(
    enabled: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        HeroButton(
            icon = R.drawable.play,
            label = stringResource(R.string.play),
            enabled = enabled,
            tonal = false,
            onClick = onPlay,
            modifier = Modifier.weight(1f),
        )
        HeroButton(
            icon = R.drawable.shuffle,
            label = stringResource(R.string.shuffle),
            enabled = enabled,
            tonal = true,
            onClick = onShuffle,
            modifier = Modifier.weight(1f),
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroButton(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    tonal: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val height = ButtonDefaults.MediumContainerHeight
    val buttonModifier = modifier.heightIn(min = height)
    val content: @Composable RowScope.() -> Unit = {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.iconSizeFor(height)),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(height)))
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (tonal) {
        FilledTonalButton(
            onClick = onClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = buttonModifier,
            content = content,
        )
    } else {
        Button(
            onClick = onClick,
            enabled = enabled,
            shapes = ButtonDefaults.shapes(),
            contentPadding = ButtonDefaults.contentPaddingFor(height),
            modifier = buttonModifier,
            content = content,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroIconAction(
    @DrawableRes icon: Int,
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(text = label) } },
        state = rememberTooltipState(),
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            enabled = enabled,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = label,
                modifier = Modifier.size(IconButtonDefaults.mediumIconSize),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HeroArtwork(
    polygon: RoundedPolygon,
    thumbnails: List<String>,
    spinning: Boolean,
    @DrawableRes placeholderIcon: Int,
    haloColor: Color,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motion = MaterialTheme.motionScheme
    val entrance = remember { Animatable(if (animationsDisabled) 1f else 0f) }
    val haloRotation = remember { Animatable(0f) }
    val haloShape = MaterialShapes.Cookie12Sided.toShape()

    LaunchedEffect(animationsDisabled) {
        if (animationsDisabled) entrance.snapTo(1f) else entrance.animateTo(1f, motion.slowSpatialSpec())
    }
    LaunchedEffect(spinning, animationsDisabled) {
        if (spinning && !animationsDisabled) {
            while (isActive) {
                haloRotation.snapTo(haloRotation.value % 360f)
                haloRotation.animateTo(
                    targetValue = haloRotation.value + 360f,
                    animationSpec = tween(HaloRotationMillis, easing = LinearEasing),
                )
            }
        }
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            Modifier
                .size(HeroArtworkSize)
                .graphicsLayer {
                    val value = entrance.value
                    val scale = 0.82f + 0.18f * value
                    scaleX = scale
                    scaleY = scale
                    rotationZ = (1f - value) * -18f
                    alpha = 0.4f + 0.6f * value
                },
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = haloRotation.value }
                    .background(haloColor, haloShape),
        )
        MorphingThumbnail(
            polygon = polygon,
            thumbnails = thumbnails,
            size = HeroArtworkSize - 20.dp,
            placeholderIcon = placeholderIcon,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MorphingThumbnail(
    polygon: RoundedPolygon,
    thumbnails: List<String>,
    size: Dp,
    @DrawableRes placeholderIcon: Int,
    modifier: Modifier = Modifier,
    overlay: @Composable BoxScope.() -> Unit = {},
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motion = MaterialTheme.motionScheme
    var settledPolygon by remember { mutableStateOf(polygon) }
    var morph by remember { mutableStateOf<Morph?>(null) }
    val restingShape = settledPolygon.toShape()
    val progress = remember { Animatable(1f) }

    LaunchedEffect(polygon, animationsDisabled) {
        if (polygon == settledPolygon) return@LaunchedEffect
        val from = settledPolygon
        settledPolygon = polygon
        if (animationsDisabled) {
            morph = null
            return@LaunchedEffect
        }
        morph = Morph(from, polygon)
        progress.snapTo(0f)
        progress.animateTo(1f, motion.defaultSpatialSpec())
        morph = null
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(size)
                .graphicsLayer {
                    shape = morph?.let { EditorialMorphShape(it, progress.value.coerceIn(0f, 1f)) } ?: restingShape
                    clip = true
                }.background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        PlaylistThumbnail(
            thumbnails = thumbnails,
            size = size,
            placeHolder = {
                Icon(
                    painter = painterResource(placeholderIcon),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(size / 3),
                )
            },
            shape = RoundedCornerShape(0.dp),
        )
        overlay()
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ScanStatusRow(
    status: ScanStatus,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    AnimatedContent(
        targetState = status,
        contentKey = { it::class },
        transitionSpec = {
            if (animationsDisabled) {
                ContentTransform(EnterTransition.None, ExitTransition.None, sizeTransform = null)
            } else {
                fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) using
                    SizeTransform(clip = false) { _, _ -> motion.defaultSpatialSpec() }
            }
        },
        label = "localLibraryScanStatus",
    ) { current ->
        if (current == ScanStatus.Idle) {
            Spacer(modifier = Modifier.fillMaxWidth())
            return@AnimatedContent
        }
        val containerColor =
            when (current) {
                is ScanStatus.Failed -> MaterialTheme.colorScheme.errorContainer
                ScanStatus.NeedsAccess -> MaterialTheme.colorScheme.secondaryContainer
                else -> MaterialTheme.colorScheme.surfaceContainerHigh
            }
        val contentColor =
            when (current) {
                is ScanStatus.Failed -> MaterialTheme.colorScheme.onErrorContainer
                ScanStatus.NeedsAccess -> MaterialTheme.colorScheme.onSecondaryContainer
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }
        Surface(
            color = containerColor,
            contentColor = contentColor,
            shape = RoundedCornerShape(20.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp).heightIn(min = 48.dp),
            ) {
                when (current) {
                    ScanStatus.Scanning -> LoadingIndicator(modifier = Modifier.size(28.dp))
                    is ScanStatus.Failed -> Icon(painterResource(R.drawable.error), contentDescription = null, modifier = Modifier.size(20.dp))
                    ScanStatus.NeedsAccess -> Icon(painterResource(R.drawable.lock), contentDescription = null, modifier = Modifier.size(20.dp))
                    is ScanStatus.Finished -> Icon(painterResource(R.drawable.done), contentDescription = null, modifier = Modifier.size(20.dp))
                    ScanStatus.Idle -> Unit
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text =
                        when (current) {
                            ScanStatus.Scanning -> stringResource(R.string.local_library_scanning)
                            is ScanStatus.Failed -> stringResource(current.message)
                            ScanStatus.NeedsAccess -> stringResource(R.string.local_library_access_needed)
                            is ScanStatus.Finished ->
                                if (current.summary.metadataLookupFailed) {
                                    stringResource(R.string.local_songs_metadata_unavailable, current.summary.scannedSongs)
                                } else {
                                    stringResource(
                                        R.string.local_songs_scan_summary,
                                        current.summary.scannedSongs,
                                        current.summary.removedSongs,
                                    )
                                }
                            ScanStatus.Idle -> ""
                        },
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f).padding(vertical = 6.dp),
                )
                when (current) {
                    ScanStatus.Scanning, ScanStatus.Idle -> Unit
                    is ScanStatus.Failed -> {
                        TextButton(onClick = onOpenSettings) { Text(stringResource(R.string.local_library_scan_settings)) }
                    }
                    ScanStatus.NeedsAccess -> {
                        TextButton(onClick = onScan) { Text(stringResource(R.string.local_library_allow_access)) }
                    }
                    is ScanStatus.Finished -> {
                        TextButton(onClick = onScan) { Text(stringResource(R.string.local_library_scan_again)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibraryViewSwitcher(
    selected: LocalLibraryView,
    onSelect: (LocalLibraryView) -> Unit,
) {
    val label = stringResource(R.string.local_library_views)
    val views = LocalLibraryView.entries
    Row(
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween),
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .selectableGroup()
                .semantics { contentDescription = label },
    ) {
        views.forEachIndexed { index, view ->
            ToggleButton(
                checked = view == selected,
                onCheckedChange = { onSelect(view) },
                shapes =
                    when (index) {
                        0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                        views.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                        else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                    },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { role = Role.RadioButton },
            ) {
                Icon(
                    painter = painterResource(view.iconRes()),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(
                    text = stringResource(view.labelRes()),
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SongSortBar(
    sort: LocalLibrarySort,
    songCount: Int,
    onAction: (LocalLibraryAction) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    var menuExpanded by remember { mutableStateOf(false) }
    val sortLabel = stringResource(sort.type.labelRes())
    val sortDescription = stringResource(R.string.local_library_sort_by, sortLabel)
    val arrowRotation by animateFloatAsState(
        targetValue = if (sort.descending) 180f else 0f,
        animationSpec = if (animationsDisabled) tween(0) else motion.defaultSpatialSpec(),
        label = "localLibrarySortArrow",
    )
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 12.dp),
    ) {
        Text(
            text = pluralStringResource(R.plurals.n_song, songCount, songCount),
            style = MaterialTheme.typography.titleSmallEmphasized,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.weight(1f),
        )
        Box {
            TextButton(
                onClick = { menuExpanded = true },
                modifier = Modifier.semantics { contentDescription = sortDescription },
            ) {
                Text(text = sortLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    painter = painterResource(R.drawable.expand_more),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                LocalSongSortType.entries.forEach { type ->
                    val selected = type == sort.type
                    DropdownMenuItem(
                        text = {
                            Text(
                                text = stringResource(type.labelRes()),
                                fontWeight = if (selected) FontWeight.Bold else null,
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onAction(LocalLibraryAction.SortTypeChanged(type))
                        },
                        leadingIcon = {
                            if (selected) {
                                Icon(painter = painterResource(R.drawable.check), contentDescription = null)
                            } else {
                                Spacer(modifier = Modifier.size(24.dp))
                            }
                        },
                        modifier =
                            Modifier.semantics {
                                role = Role.RadioButton
                                this.selected = selected
                            },
                    )
                }
            }
        }
        IconButton(onClick = { onAction(LocalLibraryAction.SortDescendingChanged(!sort.descending)) }) {
            Icon(
                painter = painterResource(R.drawable.arrow_upward),
                contentDescription =
                    stringResource(if (sort.descending) R.string.sort_descending else R.string.sort_ascending),
                modifier = Modifier.graphicsLayer { rotationZ = arrowRotation },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalSongRow(
    song: Song,
    index: Int,
    count: Int,
    showTrackNumber: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val playingLabel = stringResource(if (isPlaying) R.string.playing_desc else R.string.together_playback_paused)
    val artists = song.artists.joinToString { it.name }
    val album = song.song.albumName?.takeIf { it.isNotBlank() && !showTrackNumber }
    val supporting =
        when {
            artists.isNotEmpty() && album != null -> stringResource(R.string.local_library_meta, artists, album)
            artists.isNotEmpty() -> artists
            else -> album
        }
    val duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) }
    SegmentedListItem(
        onClick = onClick,
        onLongClick = onLongClick,
        onLongClickLabel = stringResource(R.string.more_options),
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor =
                    if (isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (showTrackNumber) {
                    Text(
                        text = song.song.trackNumber?.toString() ?: "–",
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.width(28.dp),
                    )
                }
                MorphingThumbnail(
                    polygon = if (isActive) MaterialShapes.Cookie9Sided else MaterialShapes.Square,
                    thumbnails = listOfNotNull(song.song.thumbnailUrl),
                    size = SongArtworkSize,
                    placeholderIcon = R.drawable.music_note,
                    overlay = {
                        if (isActive) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .matchParentSize()
                                        .background(Color.Black.copy(alpha = 0.4f)),
                            ) {
                                PlayingIndicatorBox(
                                    isActive = true,
                                    playWhenReady = isPlaying,
                                    color = Color.White,
                                )
                            }
                        }
                    },
                )
            }
        },
        overlineContent =
            if (isActive) {
                {
                    Text(
                        text = playingLabel,
                        style = MaterialTheme.typography.labelMediumEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            } else {
                null
            },
        supportingContent =
            if (supporting != null) {
                { Text(text = supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            } else {
                null
            },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                duration?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMenu) {
                    Icon(
                        painter = painterResource(R.drawable.more_vert),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = ListItemDefaults.SegmentedGap)
                .semantics { if (isActive) stateDescription = playingLabel },
    ) {
        Text(
            text = song.song.title,
            style = if (isActive) MaterialTheme.typography.titleMediumEmphasized else MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AlbumTile(
    album: LocalAlbumGroup,
    width: Dp,
    onOpen: () -> Unit,
    onPlay: () -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed && !animationsDisabled) 0.95f else 1f,
        animationSpec = motion.fastSpatialSpec(),
        label = "localAlbumPress",
    )
    val artworkSize = width - AlbumTilePadding * 2
    val songs = pluralStringResource(R.plurals.n_song, album.songCount, album.songCount)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        interactionSource = interactionSource,
        modifier =
            Modifier
                .width(width)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        Column(modifier = Modifier.padding(AlbumTilePadding)) {
            Box {
                PlaylistThumbnail(
                    thumbnails = listOfNotNull(album.thumbnailUrl),
                    size = artworkSize,
                    placeHolder = {
                        Icon(
                            painter = painterResource(R.drawable.album),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(artworkSize / 3),
                        )
                    },
                    shape = RoundedCornerShape(20.dp),
                )
                FilledIconButton(
                    onClick = onPlay,
                    modifier =
                        Modifier
                            .align(Alignment.BottomEnd)
                            .padding(8.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.local_library_play_album, album.title),
                    )
                }
            }
            Text(
                text = album.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 10.dp),
            )
            Text(
                text = album.artist?.let { stringResource(R.string.local_library_meta, it, songs) } ?: songs,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 6.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalArtistRow(
    artist: LocalArtistGroup,
    index: Int,
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val songs = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount)
    val supporting =
        if (artist.albumCount > 0) {
            stringResource(
                R.string.local_library_meta,
                songs,
                pluralStringResource(R.plurals.n_album, artist.albumCount, artist.albumCount),
            )
        } else {
            songs
        }
    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        verticalAlignment = Alignment.CenterVertically,
        leadingContent = {
            PlaylistThumbnail(
                thumbnails = listOfNotNull(artist.thumbnailUrl),
                size = SongArtworkSize,
                placeHolder = {
                    Icon(
                        painter = painterResource(R.drawable.artist),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                shape = CircleShape,
            )
        },
        supportingContent = { Text(text = supporting, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        trailingContent = {
            Icon(
                painter = painterResource(R.drawable.navigate_next),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = ListItemDefaults.SegmentedGap),
    ) {
        Text(
            text = artist.name,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalLibraryEmptyLibrary(
    scanStatus: ScanStatus,
    onScan: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (scanStatus) {
        ScanStatus.Scanning -> {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = modifier.padding(32.dp),
            ) {
                ContainedLoadingIndicator(modifier = Modifier.size(72.dp))
                Spacer(modifier = Modifier.size(20.dp))
                Text(
                    text = stringResource(R.string.local_library_scanning),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }

        ScanStatus.NeedsAccess -> {
            LocalLibraryMessage(
                icon = R.drawable.lock,
                title = stringResource(R.string.local_songs_empty_title),
                body = stringResource(R.string.local_songs_permission_body),
                primaryLabel = stringResource(R.string.local_library_allow_access),
                onPrimary = onScan,
                secondaryLabel = stringResource(R.string.local_library_scan_settings),
                onSecondary = onOpenSettings,
                modifier = modifier,
            )
        }

        is ScanStatus.Failed -> {
            LocalLibraryMessage(
                icon = R.drawable.error,
                title = stringResource(R.string.local_songs_empty_title),
                body = stringResource(scanStatus.message),
                primaryLabel = stringResource(R.string.retry),
                onPrimary = onScan,
                secondaryLabel = stringResource(R.string.local_library_scan_settings),
                onSecondary = onOpenSettings,
                modifier = modifier,
            )
        }

        is ScanStatus.Finished, ScanStatus.Idle -> {
            LocalLibraryMessage(
                icon = R.drawable.library_music,
                title = stringResource(R.string.local_songs_empty_title),
                body = stringResource(R.string.local_songs_empty_desc),
                primaryLabel = stringResource(R.string.local_library_scan_now),
                onPrimary = onScan,
                secondaryLabel = stringResource(R.string.local_library_scan_settings),
                onSecondary = onOpenSettings,
                modifier = modifier,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LocalLibraryMessage(
    @DrawableRes icon: Int,
    title: String,
    body: String?,
    primaryLabel: String?,
    onPrimary: (() -> Unit)?,
    modifier: Modifier = Modifier,
    secondaryLabel: String? = null,
    onSecondary: (() -> Unit)? = null,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier.padding(horizontal = 32.dp, vertical = 16.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .background(MaterialTheme.colorScheme.primaryContainer, MaterialShapes.Cookie9Sided.toShape()),
        ) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmallEmphasized,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { heading() },
        )
        body?.let {
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = it,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        if (primaryLabel != null && onPrimary != null) {
            Spacer(modifier = Modifier.size(24.dp))
            Button(onClick = onPrimary, shapes = ButtonDefaults.shapes()) { Text(primaryLabel) }
        }
        if (secondaryLabel != null && onSecondary != null) {
            TextButton(onClick = onSecondary, modifier = Modifier.padding(top = 4.dp)) { Text(secondaryLabel) }
        }
    }
}

@Composable
private fun formatLibraryDuration(seconds: Long): String {
    val totalMinutes = (seconds / 60).toInt()
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return if (hours > 0) {
        stringResource(R.string.local_library_duration_hours_minutes, hours, minutes)
    } else {
        pluralStringResource(R.plurals.n_minute, totalMinutes.coerceAtLeast(1), totalMinutes.coerceAtLeast(1))
    }
}

@Composable
private fun songMeta(songCount: Int, durationSeconds: Long): String {
    val songs = pluralStringResource(R.plurals.n_song, songCount, songCount)
    return if (durationSeconds > 0) {
        stringResource(R.string.local_library_meta, songs, formatLibraryDuration(durationSeconds))
    } else {
        songs
    }
}

private sealed interface ScanStatus {
    data object Idle : ScanStatus
    data object Scanning : ScanStatus
    data object NeedsAccess : ScanStatus
    data class Failed(@param:StringRes val message: Int) : ScanStatus
    data class Finished(val summary: LocalSongScanSummary) : ScanStatus
}

private fun LocalSongSettingsState.toScanStatus(): ScanStatus {
    val model = data
    return when {
        model?.isScanning == true -> ScanStatus.Scanning
        this is LocalSongSettingsState.Error -> ScanStatus.Failed(message)
        model == null -> ScanStatus.Idle
        model.requiresMediaPermission && !model.hasMediaPermission -> ScanStatus.NeedsAccess
        model.summary != null -> ScanStatus.Finished(model.summary)
        else -> ScanStatus.Idle
    }
}

private fun albumColumns(width: Dp): Int {
    val available = width - AlbumGridPadding * 2 + AlbumGridSpacing
    return (available / (AlbumTileMinWidth + AlbumGridSpacing)).toInt().coerceAtLeast(2)
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun LocalLibraryView.heroPolygon(): RoundedPolygon =
    when (this) {
        LocalLibraryView.SONGS -> MaterialShapes.Cookie9Sided
        LocalLibraryView.ALBUMS -> MaterialShapes.Square
        LocalLibraryView.ARTISTS -> MaterialShapes.Circle
    }

@DrawableRes
private fun LocalLibraryView.iconRes(): Int =
    when (this) {
        LocalLibraryView.SONGS -> R.drawable.music_note
        LocalLibraryView.ALBUMS -> R.drawable.album
        LocalLibraryView.ARTISTS -> R.drawable.artist
    }

@StringRes
private fun LocalLibraryView.labelRes(): Int =
    when (this) {
        LocalLibraryView.SONGS -> R.string.songs
        LocalLibraryView.ALBUMS -> R.string.albums
        LocalLibraryView.ARTISTS -> R.string.artists
    }

@StringRes
private fun LocalSongSortType.labelRes(): Int =
    when (this) {
        LocalSongSortType.MODIFIED -> R.string.sort_by_last_updated
        LocalSongSortType.NAME -> R.string.sort_by_name
        LocalSongSortType.ARTIST -> R.string.sort_by_artist
        LocalSongSortType.ALBUM -> R.string.sort_by_album
    }

private val HeroArtworkSize = 136.dp
private val SongArtworkSize = 52.dp
private val AlbumTileMinWidth = 156.dp
private val AlbumTilePadding = 8.dp
private val AlbumGridPadding = 16.dp
private val AlbumGridSpacing = 12.dp
private const val HaloRotationMillis = 24_000
