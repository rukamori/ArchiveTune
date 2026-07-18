/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastAny
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.offline.Download
import androidx.navigation.NavController
import com.valentinilk.shimmer.shimmer
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.db.entities.PlaylistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaDetailAction
import moe.rukamori.archivetune.ui.component.MediaDetailHero
import moe.rukamori.archivetune.ui.component.MediaDetailIconAction
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.SelectionMediaMetadataMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.formatCompactCount
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.OnlinePlaylistViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun OnlinePlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: OnlinePlaylistViewModel = hiltViewModel(),
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
    val dbPlaylist by viewModel.dbPlaylist.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isLoadingMore by viewModel.isLoadingMore.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }

    var selection by remember { mutableStateOf(false) }
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var query by
        rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }

    val filteredSongs =
        remember(songs, query) {
            if (query.text.isEmpty()) {
                songs.mapIndexed { index, song -> index to song }
            } else {
                songs
                    .mapIndexed { index, song -> index to song }
                    .filter { (_, song) ->
                        song.title.contains(query.text, ignoreCase = true) ||
                            song.artists.fastAny { it.name.contains(query.text, ignoreCase = true) }
                    }
            }
        }

    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(isSearching) {
        if (isSearching) {
            focusRequester.requestFocus()
        }
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    } else if (selection) {
        BackHandler { selection = false }
    }

    val wrappedSongs =
        remember(filteredSongs) { filteredSongs.map { item -> ItemWrapper(item) } }
            .toMutableStateList()

    LaunchedEffect(songs) {
        val songIds = songs.map { it.id }
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

    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }

    val surfaceColor = MaterialTheme.colorScheme.surface

    val transparentAppBar by remember {
        derivedStateOf { !selection && !isSearching && !showTopBarTitle }
    }

    val headerItems by remember {
        derivedStateOf {
            val current = playlist
            if (!isLoading && current != null && !isSearching) 1 else 0
        }
    }

    LaunchedEffect(lazyListState) {
        snapshotFlow {
            lazyListState.layoutInfo.visibleItemsInfo
                .lastOrNull()
                ?.index
        }.collect { lastVisibleIndex ->
            if (
                songs.size >= 5 &&
                lastVisibleIndex != null &&
                lastVisibleIndex >= songs.size - 5
            ) {
                viewModel.loadMoreSongs()
            }
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (isSearching) systemBarsTopPadding + AppBarHeight else 0.dp,
                    ),
            contentPadding =
                PaddingValues(
                    bottom =
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.ime)
                            .asPaddingValues()
                            .calculateBottomPadding(),
                ),
        ) {
            playlist.let { playlist ->
                if (isLoading) {
                    item(key = "shimmer") {
                        ShimmerHost {
                            Box(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 560.dp)
                                        .shimmer()
                                        .background(MaterialTheme.colorScheme.surfaceContainerLow),
                            ) {
                                Column(
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .align(Alignment.BottomCenter)
                                            .padding(horizontal = 24.dp, vertical = 24.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                ) {
                                    TextPlaceholder(
                                        height = 36.dp,
                                        modifier = Modifier.fillMaxWidth(0.55f),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    TextPlaceholder(
                                        height = 18.dp,
                                        modifier = Modifier.fillMaxWidth(0.4f),
                                    )
                                    Spacer(modifier = Modifier.height(16.dp))
                                    TextPlaceholder(
                                        height = 14.dp,
                                        modifier = Modifier.fillMaxWidth(0.72f),
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement =
                                            Arrangement.spacedBy(
                                                12.dp,
                                                Alignment.CenterHorizontally,
                                            ),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        repeat(2) { index ->
                                            if (index == 1) {
                                                ButtonPlaceholder(
                                                    modifier =
                                                        Modifier
                                                            .width(132.dp)
                                                            .height(48.dp),
                                                )
                                            }
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(52.dp)
                                                        .clip(CircleShape)
                                                        .background(
                                                            MaterialTheme.colorScheme.onSurface,
                                                        ),
                                            )
                                        }
                                    }
                                }
                            }

                            repeat(6) { ListItemPlaceHolder() }
                        }
                    }
                } else if (playlist != null) {
                    if (!isSearching) {
                        item(key = "header") {
                            val author =
                                playlist.author?.let { artist ->
                                    remember(artist) {
                                        buildAnnotatedString {
                                            if (artist.id != null) {
                                                withLink(
                                                    LinkAnnotation.Clickable(artist.id!!) {
                                                        navController.navigate(
                                                            "artist/${artist.id}",
                                                        )
                                                    },
                                                ) {
                                                    append(artist.name)
                                                }
                                            } else {
                                                append(artist.name)
                                            }
                                        }
                                    }
                                }
                            val isBookmarked = dbPlaylist?.playlist?.bookmarkedAt != null
                            val fallbackPlaySong = songs.firstOrNull()

                            MediaDetailHero(
                                title = playlist.title,
                                thumbnailUrl = playlist.thumbnail,
                                fallbackIcon = R.drawable.queue_music,
                                systemBarsTopPadding = systemBarsTopPadding,
                                subtitle = author,
                                metadata = playlist.songCountText,
                                description = playlist.description,
                                isAdded = isBookmarked,
                                addContentDescription = R.string.add_to_library,
                                removeContentDescription = R.string.remove_from_library,
                                onShuffle =
                                    playlist.shuffleEndpoint?.let { shuffleEndpoint ->
                                        {
                                            playerConnection.playQueue(
                                                YouTubeQueue.playlist(shuffleEndpoint),
                                            )
                                        }
                                    },
                                onPlay =
                                    playlist.playEndpoint?.let { playEndpoint ->
                                        {
                                            playerConnection.playQueue(
                                                YouTubeQueue.playlist(playEndpoint),
                                            )
                                        }
                                    } ?: fallbackPlaySong?.let { firstSong ->
                                        {
                                            playerConnection.playQueue(
                                                YouTubeQueue.playlist(
                                                    endpoint =
                                                        firstSong.toPlaylistPlaybackEndpoint(
                                                            playlistId = playlist.id,
                                                            playlistPlayParams = null,
                                                        ),
                                                    preloadItem = firstSong.toMediaMetadata(),
                                                ),
                                            )
                                        }
                                    },
                                onToggleAdd =
                                    if (playlist.id == "LM") {
                                        null
                                    } else {
                                        {
                                            if (dbPlaylist?.playlist == null) {
                                                database.transaction {
                                                    val existingPlaylist =
                                                        playlistEntityByBrowseId(playlist.id)
                                                    val targetPlaylistId =
                                                        if (existingPlaylist == null) {
                                                            val playlistEntity =
                                                                PlaylistEntity(
                                                                    name = playlist.title,
                                                                    browseId = playlist.id,
                                                                    thumbnailUrl = playlist.thumbnail,
                                                                    isEditable = playlist.isEditable,
                                                                    playEndpointParams =
                                                                        playlist.playEndpoint?.params,
                                                                    shuffleEndpointParams =
                                                                        playlist.shuffleEndpoint?.params,
                                                                    radioEndpointParams =
                                                                        playlist.radioEndpoint?.params,
                                                                ).toggleLike()
                                                            insert(playlistEntity)
                                                            playlistEntityByBrowseId(playlist.id)?.id
                                                                ?: playlistEntity.id
                                                        } else {
                                                            val refreshedPlaylist =
                                                                existingPlaylist.copy(
                                                                    name = playlist.title,
                                                                    browseId = playlist.id,
                                                                    thumbnailUrl = playlist.thumbnail,
                                                                    isEditable = playlist.isEditable,
                                                                    playEndpointParams =
                                                                        playlist.playEndpoint?.params,
                                                                    shuffleEndpointParams =
                                                                        playlist.shuffleEndpoint?.params,
                                                                    radioEndpointParams =
                                                                        playlist.radioEndpoint?.params,
                                                                )
                                                            update(
                                                                if (existingPlaylist.bookmarkedAt == null) {
                                                                    refreshedPlaylist.toggleLike()
                                                                } else {
                                                                    refreshedPlaylist
                                                                },
                                                            )
                                                            existingPlaylist.id
                                                        }
                                                    if (songs.isNotEmpty()) {
                                                        clearPlaylist(targetPlaylistId)
                                                        songs
                                                            .onEach { song ->
                                                                insert(song.toMediaMetadata())
                                                            }.mapIndexed { index, song ->
                                                                PlaylistSongMap(
                                                                    songId = song.id,
                                                                    playlistId = targetPlaylistId,
                                                                    position = index,
                                                                    setVideoId = song.setVideoId,
                                                                )
                                                            }.forEach(::insert)
                                                    }
                                                }
                                            } else {
                                                database.transaction {
                                                    val currentPlaylist = dbPlaylist!!.playlist
                                                    update(currentPlaylist, playlist)
                                                    update(currentPlaylist.toggleLike())
                                                }
                                            }
                                        }
                                    },
                                additionalPrimaryActions = { contentColor ->
                                    if (songs.isNotEmpty()) {
                                        MediaDetailAction(
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
                                                        sendRemoveDownloads(
                                                            context = context,
                                                            songIds = songs.map { it.id },
                                                        )
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
                                                                songs.map { song ->
                                                                    HeaderDownloadItem(
                                                                        id = song.id,
                                                                        title = song.title,
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
                                    }

                                    playlist.shuffleEndpoint?.let { mixEndpoint ->
                                        MediaDetailIconAction(
                                            icon = R.drawable.mix,
                                            contentDescription = R.string.start_mix,
                                            contentColor = contentColor,
                                            onClick = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue.playlist(mixEndpoint),
                                                )
                                            },
                                        )
                                    }

                                    playlist.radioEndpoint?.let { radioEndpoint ->
                                        MediaDetailIconAction(
                                            icon = R.drawable.radio,
                                            contentDescription = R.string.start_radio,
                                            contentColor = contentColor,
                                            onClick = {
                                                playerConnection.playQueue(
                                                    YouTubeQueue(radioEndpoint),
                                                )
                                            },
                                        )
                                    }
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (songs.isEmpty() && !isLoading && error == null) {
                        item(key = "empty") {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_playlist),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_playlist_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }

                    // Songs List
                    items(
                        items = wrappedSongs,
                        key = { it.item.second.setVideoId ?: "${it.item.second.id}-${it.item.first}" },
                        contentType = { "online_playlist_song" },
                    ) { song ->
                        YouTubeListItem(
                            item = song.item.second,
                            showSourceIcon = true,
                            viewCountText =
                                viewCounts[song.item.second.id]?.let { count ->
                                    formatCompactCount(count.toLong())
                                },
                            isActive = mediaMetadata?.id == song.item.second.id,
                            isPlaying = isPlaying,
                            isSelected = song.isSelected && selection,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            YouTubeSongMenu(
                                                song = song.item.second,
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
                            },
                            modifier =
                                Modifier
                                    .combinedClickable(
                                        enabled = !hideExplicit || !song.item.second.explicit,
                                        onClick = {
                                            if (!selection) {
                                                if (song.item.second.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    playerConnection.playQueue(
                                                        YouTubeQueue.playlist(
                                                            endpoint =
                                                                song.item.second
                                                                    .toPlaylistPlaybackEndpoint(
                                                                        playlistId = playlist.id,
                                                                        playlistPlayParams =
                                                                            playlist.playEndpoint
                                                                                ?.params,
                                                                    ),
                                                            preloadItem = song.item.second.toMediaMetadata(),
                                                        ),
                                                    )
                                                }
                                            } else {
                                                song.isSelected = !song.isSelected
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(
                                                HapticFeedbackType.LongPress,
                                            )
                                            if (!selection) {
                                                selection = true
                                            }
                                            wrappedSongs.forEach { it.isSelected = false }
                                            song.isSelected = true
                                        },
                                    ).animateItem(),
                        )
                    }

                    if (viewModel.continuation != null && songs.isNotEmpty() && isLoadingMore) {
                        item(key = "loading_more") {
                            ShimmerHost { repeat(2) { ListItemPlaceHolder() } }
                        }
                    }
                } else {
                    val isPrivatePlaylist = error?.contains("PLAYLIST_PRIVATE") == true
                    item(key = "error") {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            if (isPrivatePlaylist) {
                                Image(
                                    painter = painterResource(R.drawable.anime_blank),
                                    contentDescription = null,
                                    modifier = Modifier.size(120.dp),
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = stringResource(R.string.playlist_private_title),
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.playlist_private_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            } else {
                                Text(
                                    text =
                                        if (error != null) {
                                            stringResource(R.string.error_unknown)
                                        } else {
                                            stringResource(R.string.playlist_not_found)
                                        },
                                    style = MaterialTheme.typography.titleLarge,
                                    color =
                                        if (error != null) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        },
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (error != null) {
                                            error!!
                                        } else {
                                            stringResource(R.string.playlist_not_found_desc)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (error != null) {
                                    Spacer(modifier = Modifier.height(16.dp))
                                    Button(onClick = { viewModel.retry() }, shapes = ButtonDefaults.shapes()) {
                                        Text(stringResource(R.string.retry))
                                    }
                                }
                            }
                        }
                    }
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
                    val count = wrappedSongs.count { it.isSelected }
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                    )
                } else if (showTopBarTitle) {
                    Text(playlist?.title.orEmpty())
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
                        if (!isSearching && !selection) {
                            navController.backToMain()
                        }
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (selection) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    IconButton(
                        onClick = {
                            if (count == wrappedSongs.size) {
                                wrappedSongs.forEach { it.isSelected = false }
                            } else {
                                wrappedSongs.forEach { it.isSelected = true }
                            }
                        },
                        onLongClick = {},
                    ) {
                        Icon(
                            painter =
                                painterResource(
                                    if (count == wrappedSongs.size) {
                                        R.drawable.deselect
                                    } else {
                                        R.drawable.select_all
                                    },
                                ),
                            contentDescription = null,
                        )
                    }
                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionMediaMetadataMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map {
                                                it.item.second
                                                    .toMediaItem()
                                                    .metadata!!
                                            },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
                                    currentItems = emptyList(),
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
                    IconButton(onClick = { isSearching = true }, onLongClick = {}) {
                        Icon(
                            painter = painterResource(R.drawable.search),
                            contentDescription = null,
                        )
                    }
                    playlist?.let { currentPlaylist ->
                        IconButton(
                            onClick = {
                                menuState.show {
                                    YouTubePlaylistMenu(
                                        playlist = currentPlaylist,
                                        songs = songs,
                                        coroutineScope = coroutineScope,
                                        onDismiss = menuState::dismiss,
                                        selectAction = { selection = true },
                                        canSelect = true,
                                        snackbarHostState = snackbarHostState,
                                    )
                                }
                            },
                            onLongClick = {},
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.more_horiz),
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                    }
                }
            },
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .windowInsetsPadding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime),
                    ).align(Alignment.BottomCenter),
        )
    }
}

private fun SongItem.toPlaylistPlaybackEndpoint(
    playlistId: String,
    playlistPlayParams: String?,
): WatchEndpoint {
    val baseEndpoint = endpoint ?: WatchEndpoint(videoId = id)
    return baseEndpoint.copy(
        videoId = baseEndpoint.videoId ?: id,
        playlistId = baseEndpoint.playlistId ?: playlistId,
        playlistSetVideoId = baseEndpoint.playlistSetVideoId ?: setVideoId,
        params = baseEndpoint.params ?: playlistPlayParams,
    )
}
