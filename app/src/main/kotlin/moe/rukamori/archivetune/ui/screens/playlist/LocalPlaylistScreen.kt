/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastSumBy
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.utils.completed
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalMixQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.AssignTagsDialog
import moe.rukamori.archivetune.ui.component.DefaultDialog
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.EditPlaylistDialog
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaDetailHero
import moe.rukamori.archivetune.ui.component.MediaDetailSupportingAction
import moe.rukamori.archivetune.ui.component.MediaDetailSupportingIconAction
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SortHeader
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.removeSongFromRemotePlaylist
import moe.rukamori.archivetune.ui.screens.playlist.PlaylistSuggestionsSection
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LocalPlaylistViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import java.time.LocalDateTime

@SuppressLint("RememberReturnType")
@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun LocalPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: LocalPlaylistViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val playlist by viewModel.playlist.collectAsStateWithLifecycle()
    val songs by viewModel.playlistSongs.collectAsStateWithLifecycle()
    val viewCounts by viewModel.viewCounts.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val mutableSongs = remember { mutableStateListOf<PlaylistSong>() }
    val playlistLength =
        remember(songs) {
            songs.fastSumBy { it.song.song.duration }
        }
    val sortType by viewModel.sortType.collectAsStateWithLifecycle()
    val sortDescending by viewModel.sortDescending.collectAsStateWithLifecycle()
    val onSortTypeChange: (PlaylistSongSortType) -> Unit = { viewModel.updateSortPreference(it, sortDescending) }
    val onSortDescendingChange: (Boolean) -> Unit = { viewModel.updateSortPreference(sortType, it) }
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val swipeToSongEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)
    var showAssignTagsDialog by remember { mutableStateOf(false) }

    if (showAssignTagsDialog && playlist != null) {
        AssignTagsDialog(
            playlistId = playlist!!.id,
            onDismiss = { showAssignTagsDialog = false },
        )
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs
            } else {
                songs.filter { song ->
                    song.song.song.title
                        .contains(query.text, ignoreCase = true) ||
                        song.song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    var selection by remember { mutableStateOf(false) }
    var selectedSongMapIds by remember { mutableStateOf(emptySet<Int>()) }
    val visibleSongMapIds =
        remember(filteredSongs) {
            filteredSongs.map { it.map.id }.toSet()
        }
    val selectedPlaylistSongs =
        remember(filteredSongs, selectedSongMapIds) {
            filteredSongs.filter { it.map.id in selectedSongMapIds }
        }

    LaunchedEffect(selection, visibleSongMapIds) {
        if (selection) {
            val visibleSelectedSongMapIds = selectedSongMapIds.intersect(visibleSongMapIds)
            if (visibleSelectedSongMapIds.size != selectedSongMapIds.size) {
                selectedSongMapIds = visibleSelectedSongMapIds
            }
        } else if (selectedSongMapIds.isNotEmpty()) {
            selectedSongMapIds = emptySet()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }

    val editable: Boolean = playlist?.playlist?.isEditable == true

    LaunchedEffect(songs) {
        mutableSongs.apply {
            clear()
            addAll(songs)
        }
        val songIds = songs.map { it.song.id }
        if (songIds.isEmpty()) {
            downloads = emptyMap()
            downloadState = HeaderDownloadState.None
            return@LaunchedEffect
        }
        downloadUtil.downloads.collect { currentDownloads ->
            downloads = currentDownloads
            downloadState = headerDownloadState(songIds, currentDownloads)
        }
    }

    val pickCoverLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val oldUriString = playlist?.playlist?.thumbnailUrl
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            if (!oldUriString.isNullOrBlank() && oldUriString != uri.toString()) {
                val oldUri = runCatching { Uri.parse(oldUriString) }.getOrNull()
                if (oldUri?.scheme == "content") {
                    runCatching {
                        context.contentResolver.releasePersistableUriPermission(
                            oldUri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
            }
            val newUriString = uri.toString()
            playlist?.let { p ->
                database.query {
                    update(
                        p.playlist.copy(
                            thumbnailUrl = newUriString,
                            lastUpdateTime = LocalDateTime.now(),
                        ),
                    )
                }
            }
        }

    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        playlist?.let { playlistData ->
            EditPlaylistDialog(
                initialName = playlistData.playlist.name,
                onDismiss = { showEditDialog = false },
                onSave = { name ->
                    database.query {
                        update(
                            playlistData.playlist.copy(
                                name = name,
                                lastUpdateTime = LocalDateTime.now(),
                            ),
                        )
                    }
                    viewModel.viewModelScope.launch(Dispatchers.IO) {
                        playlistData.playlist.browseId?.let { YouTube.renamePlaylist(it, name) }
                    }
                },
            )
        }
    }

    var showRemoveDownloadDialog by remember { mutableStateOf(false) }

    if (showRemoveDownloadDialog) {
        DefaultDialog(
            onDismiss = { showRemoveDownloadDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.remove_download_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showRemoveDownloadDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }

                TextButton(
                    onClick = {
                        showRemoveDownloadDialog = false
                        if (!editable) {
                            database.transaction {
                                playlist?.id?.let { clearPlaylist(it) }
                            }
                        }
                        sendRemoveDownloads(
                            context = context,
                            songIds = songs.map { it.song.id },
                        )
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    var showDeletePlaylistDialog by remember { mutableStateOf(false) }
    if (showDeletePlaylistDialog) {
        DefaultDialog(
            onDismiss = { showDeletePlaylistDialog = false },
            content = {
                Text(
                    text =
                        stringResource(
                            R.string.delete_playlist_confirm,
                            playlist?.playlist!!.name,
                        ),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(horizontal = 18.dp),
                )
            },
            buttons = {
                TextButton(
                    onClick = { showDeletePlaylistDialog = false },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.cancel))
                }
                TextButton(
                    onClick = {
                        showDeletePlaylistDialog = false
                        database.query {
                            playlist?.let { delete(it.playlist) }
                        }
                        viewModel.viewModelScope.launch(Dispatchers.IO) {
                            playlist?.playlist?.browseId?.let { YouTube.deletePlaylist(it) }
                        }
                        navController.popBackStack()
                    },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Text(text = stringResource(android.R.string.ok))
                }
            },
        )
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            val hasContent =
                current != null &&
                    (current.songCount > 0 || current.playlist.remoteSongCount != 0)
            if (hasContent && !isSearching) 2 else 0
        }
    }
    val lazyListState = rememberLazyListState()
    var dragInfo by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (to.index >= headerItems && from.index >= headerItems) {
                val currentDragInfo = dragInfo
                dragInfo =
                    if (currentDragInfo == null) {
                        (from.index - headerItems) to (to.index - headerItems)
                    } else {
                        currentDragInfo.first to (to.index - headerItems)
                    }
                mutableSongs.move(from.index - headerItems, to.index - headerItems)
            }
        }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging) {
            dragInfo?.let { (from, to) ->
                val orderedBeforeMove = songs
                val browseId =
                    viewModel.playlist.value
                        ?.playlist
                        ?.browseId
                val movedSetVideoId = orderedBeforeMove.getOrNull(from)?.map?.setVideoId
                val successorIndex = if (from > to) to else to + 1
                val successorSetVideoId = orderedBeforeMove.getOrNull(successorIndex)?.map?.setVideoId

                coroutineScope.launch(Dispatchers.IO) {
                    database.withTransaction {
                        move(viewModel.playlistId, from, to)
                    }

                    if (browseId != null && movedSetVideoId != null) {
                        runCatching {
                            YouTube
                                .moveSongPlaylist(
                                    browseId,
                                    movedSetVideoId,
                                    successorSetVideoId,
                                ).getOrThrow()
                        }.onFailure {
                            withContext(Dispatchers.Main) {
                                snackbarHostState.showSnackbar(
                                    message = context.getString(R.string.error_unknown),
                                    withDismissAction = true,
                                )
                            }
                        }
                    }
                }
                dragInfo = null
            }
        }
    }

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val surfaceColor = MaterialTheme.colorScheme.surface

    val transparentAppBar by remember {
        derivedStateOf {
            !selection && !showTopBarTitle && !isSearching
        }
    }
    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = viewModel::refresh,
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    bottom =
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues()
                            .calculateBottomPadding(),
                ),
        ) {
            playlist?.let { playlist ->
                if (playlist.songCount == 0 && playlist.playlist.remoteSongCount == 0) {
                    item {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.playlist_is_empty),
                        )
                    }
                } else {
                    if (!isSearching) {
                        item(key = "header") {
                            val songCount =
                                if (
                                    playlist.songCount == 0 &&
                                    playlist.playlist.remoteSongCount != null
                                ) {
                                    playlist.playlist.remoteSongCount
                                } else {
                                    playlist.songCount
                                }
                            val metadata =
                                listOfNotNull(
                                    pluralStringResource(
                                        R.plurals.n_song,
                                        songCount,
                                        songCount,
                                    ),
                                    playlistLength
                                        .takeIf { it > 0 }
                                        ?.let { makeTimeString(it * 1000L) },
                                ).joinToString(MediaDetailMetadataSeparator)
                            val isBookmarked = playlist.playlist.bookmarkedAt != null

                            MediaDetailHero(
                                title = playlist.playlist.name,
                                thumbnailUrl =
                                    playlist.playlist.thumbnailUrl
                                        ?: playlist.thumbnails.firstOrNull(),
                                fallbackIcon = R.drawable.queue_music,
                                systemBarsTopPadding = systemBarsTopPadding,
                                metadata = metadata,
                                isAdded = isBookmarked,
                                addContentDescription = R.string.add_to_library,
                                removeContentDescription = R.string.remove_from_library,
                                onShuffle =
                                    if (songs.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.playlist.name,
                                                    items =
                                                        songs
                                                            .shuffled()
                                                            .map { it.song.toMediaItem() },
                                                ),
                                            )
                                        }
                                    },
                                onPlay =
                                    if (songs.isEmpty()) {
                                        null
                                    } else {
                                        {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = playlist.playlist.name,
                                                    items = songs.map { it.song.toMediaItem() },
                                                ),
                                            )
                                        }
                                    },
                                onToggleAdd = {
                                    database.transaction {
                                        update(playlist.playlist.toggleLike())
                                    }
                                },
                                supportingActions = { contentColor ->
                                    if (editable) {
                                        MediaDetailSupportingIconAction(
                                            icon = R.drawable.delete,
                                            contentDescription = R.string.delete,
                                            contentColor = contentColor,
                                            onClick = { showDeletePlaylistDialog = true },
                                            isDestructive = true,
                                        )
                                    }

                                    MediaDetailSupportingAction(
                                        contentDescription =
                                            if (downloadState == HeaderDownloadState.Completed) {
                                                R.string.remove_download
                                            } else {
                                                R.string.download
                                            },
                                        contentColor = contentColor,
                                        onClick = {
                                            when (downloadState) {
                                                HeaderDownloadState.Completed -> {
                                                    showRemoveDownloadDialog = true
                                                }

                                                is HeaderDownloadState.Partial -> {
                                                    navController.navigate(
                                                        "auto_playlist/downloaded?tab=progress",
                                                    )
                                                }

                                                HeaderDownloadState.None -> {
                                                    sendAddMissingDownloads(
                                                        context = context,
                                                        songs =
                                                            songs.map {
                                                                HeaderDownloadItem(
                                                                    id = it.song.id,
                                                                    title = it.song.song.title,
                                                                )
                                                            },
                                                        downloads = downloads,
                                                    )
                                                    navController.navigate(
                                                        "auto_playlist/downloaded?tab=progress",
                                                    )
                                                }
                                            }
                                        },
                                    ) {
                                        when (val state = downloadState) {
                                            HeaderDownloadState.Completed -> {
                                                Icon(
                                                    painter = painterResource(R.drawable.offline),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }

                                            is HeaderDownloadState.Partial -> {
                                                HeaderDownloadProgressIndicator(
                                                    progress = state.progress,
                                                    paused = state.paused,
                                                )
                                            }

                                            HeaderDownloadState.None -> {
                                                Icon(
                                                    painter = painterResource(R.drawable.download),
                                                    contentDescription = null,
                                                    modifier = Modifier.size(22.dp),
                                                )
                                            }
                                        }
                                    }

                                    MediaDetailSupportingIconAction(
                                        icon = R.drawable.mix,
                                        contentDescription = R.string.start_mix,
                                        contentColor = contentColor,
                                        enabled = songs.isNotEmpty(),
                                        onClick = {
                                            playerConnection.playQueue(
                                                LocalMixQueue(
                                                    database = database,
                                                    playlistId = playlist.id,
                                                    maxMixSize = 50,
                                                ),
                                            )
                                        },
                                    )

                                    MediaDetailSupportingIconAction(
                                        icon = R.drawable.radio,
                                        contentDescription = R.string.start_radio,
                                        contentColor = contentColor,
                                        enabled = songs.isNotEmpty(),
                                        onClick = {
                                            val browseId = playlist.playlist.browseId
                                            if (browseId != null) {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(
                                                        WatchEndpoint(
                                                            playlistId = "RDAMPL$browseId",
                                                            params =
                                                                playlist.playlist
                                                                    .radioEndpointParams,
                                                        ),
                                                    ),
                                                )
                                            } else {
                                                playerConnection.playQueue(
                                                    LocalMixQueue(
                                                        database = database,
                                                        playlistId = playlist.id,
                                                        maxMixSize = 50,
                                                    ),
                                                )
                                            }
                                        },
                                    )

                                    MediaDetailSupportingIconAction(
                                        icon = R.drawable.more_vert,
                                        contentDescription = R.string.more_options,
                                        contentColor = contentColor,
                                        onClick = {
                                            menuState.show {
                                                PlaylistMenu(
                                                    playlist = playlist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                    )

                                    if (editable) {
                                        MediaDetailSupportingIconAction(
                                            icon = R.drawable.edit,
                                            contentDescription = R.string.edit_playlist,
                                            contentColor = contentColor,
                                            onClick = { showEditDialog = true },
                                        )
                                        MediaDetailSupportingIconAction(
                                            icon = R.drawable.image,
                                            contentDescription =
                                                R.string.change_playlist_cover,
                                            contentColor = contentColor,
                                            onClick = {
                                                pickCoverLauncher.launch(arrayOf("image/*"))
                                            },
                                        )
                                    } else if (playlist.playlist.browseId != null) {
                                        MediaDetailSupportingIconAction(
                                            icon = R.drawable.sync,
                                            contentDescription = R.string.sync_playlist,
                                            contentColor = contentColor,
                                            onClick = {
                                                coroutineScope.launch(Dispatchers.IO) {
                                                    val playlistPage =
                                                        YouTube
                                                            .playlist(
                                                                playlist.playlist.browseId,
                                                            ).completed()
                                                            .getOrNull()
                                                            ?: return@launch
                                                    database.transaction {
                                                        clearPlaylist(playlist.id)
                                                        playlistPage.songs
                                                            .map(SongItem::toMediaMetadata)
                                                            .onEach(::insert)
                                                            .mapIndexed { position, song ->
                                                                PlaylistSongMap(
                                                                    songId = song.id,
                                                                    playlistId = playlist.id,
                                                                    position = position,
                                                                    setVideoId = song.setVideoId,
                                                                )
                                                            }.forEach(::insert)
                                                    }
                                                }
                                                coroutineScope.launch(Dispatchers.Main) {
                                                    snackbarHostState.showSnackbar(
                                                        context.getString(
                                                            R.string.playlist_synced,
                                                        ),
                                                    )
                                                }
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (!isSearching) {
                        // Sort Header
                        item(key = "sort_header") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(start = 16.dp),
                            ) {
                                SortHeader(
                                    sortType = sortType,
                                    sortDescending = sortDescending,
                                    onSortTypeChange = onSortTypeChange,
                                    onSortDescendingChange = onSortDescendingChange,
                                    sortTypeText = { sortType ->
                                        when (sortType) {
                                            PlaylistSongSortType.CUSTOM -> R.string.sort_by_custom
                                            PlaylistSongSortType.CREATE_DATE -> R.string.sort_by_create_date
                                            PlaylistSongSortType.NAME -> R.string.sort_by_name
                                            PlaylistSongSortType.ARTIST -> R.string.sort_by_artist
                                            PlaylistSongSortType.PLAY_TIME -> R.string.sort_by_play_time
                                        }
                                    },
                                    modifier = Modifier.weight(1f),
                                )
                                if (editable && sortType == PlaylistSongSortType.CUSTOM) {
                                    IconButton(
                                        onClick = { locked = !locked },
                                        onLongClick = {},
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    ) {
                                        Icon(
                                            painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                            contentDescription = null,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Songs List
            if (!selection) {
                itemsIndexed(
                    items = if (isSearching) filteredSongs else mutableSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                        modifier =
                            Modifier.graphicsLayer {
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            },
                    ) {
                        val currentItem by rememberUpdatedState(song)

                        fun deleteFromPlaylist() {
                            val map = currentItem.map
                            val browseId = playlist?.playlist?.browseId
                            coroutineScope.launch(Dispatchers.IO) {
                                if (browseId != null) {
                                    val remoteResult = removeSongFromRemotePlaylist(browseId, map)
                                    if (remoteResult.isFailure) {
                                        withContext(Dispatchers.Main) {
                                            snackbarHostState.showSnackbar(
                                                message = context.getString(R.string.error_unknown),
                                                withDismissAction = true,
                                            )
                                        }
                                        return@launch
                                    }
                                }
                                database.withTransaction {
                                    move(map.playlistId, map.position, Int.MAX_VALUE)
                                    delete(map.copy(position = Int.MAX_VALUE))
                                }
                            }
                        }

                        val dismissBoxState =
                            rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance },
                                confirmValueChange = { targetValue ->
                                    targetValue == SwipeToDismissBoxValue.Settled || !lazyListState.isScrollInProgress
                                },
                            )
                        var processedDismiss by remember { mutableStateOf(false) }
                        LaunchedEffect(dismissBoxState.currentValue) {
                            val dv = dismissBoxState.currentValue
                            if (!processedDismiss && (
                                    dv == SwipeToDismissBoxValue.StartToEnd ||
                                        dv == SwipeToDismissBoxValue.EndToStart
                                )
                            ) {
                                processedDismiss = true
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss = false
                            }
                        }

                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                viewCountText =
                                    viewCounts[song.song.id]?.let { count -> formatCompactCount(count.toLong()) },
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistSong = song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }

                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !selection && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            onLongClick = {},
                                            modifier =
                                                Modifier
                                                    .draggableHandle()
                                                    .graphicsLayer { alpha = 0.99f },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (song.song.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = playlist!!.playlist.name,
                                                            items = songs.map { it.song.toMediaItem() },
                                                            startIndex = songs.indexOfFirst { it.map.id == song.map.id },
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!selection) {
                                                    selection = true
                                                }
                                                selectedSongMapIds = setOf(song.map.id)
                                            },
                                        ),
                            )
                        }

                        if (locked || selection || swipeToSongEnabled) {
                            content()
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                            ) {
                                content()
                            }
                        }
                    }
                }
            } else {
                itemsIndexed(
                    items = filteredSongs,
                    key = { _, song -> song.map.id },
                ) { index, song ->
                    ReorderableItem(
                        state = reorderableState,
                        key = song.map.id,
                        modifier =
                            Modifier.graphicsLayer {
                                compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                            },
                    ) {
                        val currentItem by rememberUpdatedState(song)

                        fun deleteFromPlaylist() {
                            val map = currentItem.map
                            coroutineScope.launch(Dispatchers.IO) {
                                database.withTransaction {
                                    move(map.playlistId, map.position, Int.MAX_VALUE)
                                    delete(map.copy(position = Int.MAX_VALUE))
                                }
                            }
                        }

                        val dismissBoxState =
                            rememberSwipeToDismissBoxState(
                                positionalThreshold = { totalDistance -> totalDistance },
                                confirmValueChange = { targetValue ->
                                    targetValue == SwipeToDismissBoxValue.Settled || !lazyListState.isScrollInProgress
                                },
                            )
                        var processedDismiss2 by remember { mutableStateOf(false) }
                        LaunchedEffect(dismissBoxState.currentValue) {
                            val dv = dismissBoxState.currentValue
                            if (!processedDismiss2 && (
                                    dv == SwipeToDismissBoxValue.StartToEnd ||
                                        dv == SwipeToDismissBoxValue.EndToStart
                                )
                            ) {
                                processedDismiss2 = true
                                deleteFromPlaylist()
                            }
                            if (dv == SwipeToDismissBoxValue.Settled) {
                                processedDismiss2 = false
                            }
                        }

                        val content: @Composable () -> Unit = {
                            SongListItem(
                                song = song.song,
                                viewCountText =
                                    viewCounts[song.song.id]?.let { count -> formatCompactCount(count.toLong()) },
                                isActive = song.song.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                showInLibraryIcon = true,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song.song,
                                                    playlistBrowseId = playlist?.playlist?.browseId,
                                                    navController = navController,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        },
                                        onLongClick = {},
                                    ) {
                                        Icon(
                                            painter = painterResource(R.drawable.more_vert),
                                            contentDescription = null,
                                        )
                                    }
                                    if (sortType == PlaylistSongSortType.CUSTOM && !locked && !selection && !isSearching && editable) {
                                        IconButton(
                                            onClick = { },
                                            onLongClick = {},
                                            modifier =
                                                Modifier
                                                    .draggableHandle()
                                                    .graphicsLayer { alpha = 0.99f },
                                        ) {
                                            Icon(
                                                painter = painterResource(R.drawable.drag_handle),
                                                contentDescription = null,
                                            )
                                        }
                                    }
                                },
                                isSelected = song.map.id in selectedSongMapIds,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .combinedClickable(
                                            onClick = {
                                                if (!selection) {
                                                    if (song.song.id == mediaMetadata?.id) {
                                                        playerConnection.player.togglePlayPause()
                                                    } else {
                                                        playerConnection.playQueue(
                                                            ListQueue(
                                                                title = playlist!!.playlist.name,
                                                                items = songs.map { it.song.toMediaItem() },
                                                                startIndex = index,
                                                            ),
                                                        )
                                                    }
                                                } else {
                                                    selectedSongMapIds =
                                                        if (song.map.id in selectedSongMapIds) {
                                                            selectedSongMapIds - song.map.id
                                                        } else {
                                                            selectedSongMapIds + song.map.id
                                                        }
                                                }
                                            },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                if (!selection) {
                                                    selection = true
                                                }
                                                selectedSongMapIds = setOf(song.map.id)
                                            },
                                        ),
                            )
                        }

                        if (locked || !editable || swipeToSongEnabled) {
                            content()
                        } else {
                            SwipeToDismissBox(
                                state = dismissBoxState,
                                backgroundContent = {},
                            ) {
                                content()
                            }
                        }
                    }
                }
            }

            // Playlist Suggestions Section
            if (!selection && !isSearching) {
                item {
                    PlaylistSuggestionsSection(
                        modifier = Modifier.padding(vertical = 16.dp),
                    )
                }
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = headerItems,
        )

        // Top App Bar
        val topAppBarColors =
            if (transparentAppBar) {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = Color.White,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White,
                )
            } else {
                TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surface,
                )
            }

        TopAppBar(
            colors = topAppBarColors,
            title = {
                if (selection) {
                    val count = selectedPlaylistSongs.size
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (isSearching) {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = {
                            Text(
                                text = stringResource(R.string.search),
                                style = MaterialTheme.typography.titleLarge,
                            )
                        },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.titleLarge,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors =
                            TextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                            ),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.playlist?.name.orEmpty())
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (selection || isSearching) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (selection) {
                    val count = selectedPlaylistSongs.size
                    IconButton(
                        onClick = {
                            if (count == filteredSongs.size) {
                                selectedSongMapIds = emptySet()
                            } else {
                                selectedSongMapIds = visibleSongMapIds
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == filteredSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        selectedPlaylistSongs.map { it.song },
                                    songPosition =
                                        selectedPlaylistSongs.map { it.map },
                                    onDismiss = menuState::dismiss,
                                    clearAction = {
                                        selection = false
                                        selectedSongMapIds = emptySet()
                                    },
                                )
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = null,
                        )
                    }
                } else if (!isSearching) {
                    IconButton(
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
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime))
                    .align(Alignment.BottomCenter),
        )
    }
}

private const val MediaDetailMetadataSeparator = "  •  "
