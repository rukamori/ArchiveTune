/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.fastForEachIndexed
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
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaDetailAction
import moe.rukamori.archivetune.ui.component.MediaDetailHero
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.ButtonPlaceholder
import moe.rukamori.archivetune.ui.component.shimmer.ListItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.SelectionSongMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.utils.HeaderDownloadItem
import moe.rukamori.archivetune.ui.utils.HeaderDownloadProgressIndicator
import moe.rukamori.archivetune.ui.utils.HeaderDownloadState
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.headerDownloadState
import moe.rukamori.archivetune.ui.utils.sendAddMissingDownloads
import moe.rukamori.archivetune.ui.utils.sendRemoveDownloads
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.AlbumUiState
import moe.rukamori.archivetune.viewmodels.AlbumViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AlbumScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: AlbumViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val database = LocalDatabase.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val scope = rememberCoroutineScope()

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val albumWithSongs by viewModel.albumWithSongs.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val otherVersions by viewModel.otherVersions.collectAsStateWithLifecycle()
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    // System bars padding
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val surfaceColor = MaterialTheme.colorScheme.surface

    val wrappedSongs =
        remember(albumWithSongs, hideExplicit) {
            val filteredSongs =
                if (hideExplicit) {
                    albumWithSongs?.songs?.filter { !it.song.explicit } ?: emptyList()
                } else {
                    albumWithSongs?.songs ?: emptyList()
                }
            filteredSongs.map { item -> ItemWrapper(item) }.toMutableStateList()
        }

    var selection by remember { mutableStateOf(false) }

    if (selection) {
        BackHandler {
            selection = false
        }
    }

    val downloadUtil = LocalDownloadUtil.current
    var downloads by remember { mutableStateOf<Map<String, Download>>(emptyMap()) }
    var downloadState by remember { mutableStateOf<HeaderDownloadState>(HeaderDownloadState.None) }

    LaunchedEffect(albumWithSongs) {
        val songIds = albumWithSongs?.songs?.map { it.id }.orEmpty()
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

    // State for LazyColumn to track scroll
    val lazyListState = rememberLazyListState()

    val showTopBarTitle by remember {
        derivedStateOf {
            lazyListState.firstVisibleItemIndex > 0
        }
    }

    val transparentAppBar by remember {
        derivedStateOf {
            !selection && !showTopBarTitle
        }
    }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(surfaceColor),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    bottom = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding(),
                ),
        ) {
            val albumWithSongs = albumWithSongs
            val hasSongs = albumWithSongs?.songs?.isNotEmpty() == true
            if (hasSongs) {
                item(key = "header") {
                    val artistNames =
                        remember(albumWithSongs.artists) {
                            buildAnnotatedString {
                                albumWithSongs.artists.fastForEachIndexed { index, artist ->
                                    withLink(
                                        LinkAnnotation.Clickable(artist.id) {
                                            navController.navigate("artist/${artist.id}")
                                        },
                                    ) {
                                        append(artist.name)
                                    }
                                    if (index != albumWithSongs.artists.lastIndex) {
                                        append(", ")
                                    }
                                }
                            }
                        }
                    val totalDuration = albumWithSongs.songs.sumOf { it.song.duration }
                    val metadata =
                        listOfNotNull(
                            albumWithSongs.album.year?.toString(),
                            pluralStringResource(
                                R.plurals.n_song,
                                wrappedSongs.size,
                                wrappedSongs.size,
                            ),
                            totalDuration
                                .takeIf { it > 0 }
                                ?.let { makeTimeString(it * 1000L) },
                        ).joinToString(MediaDetailMetadataSeparator)
                    val isBookmarked = albumWithSongs.album.bookmarkedAt != null

                    MediaDetailHero(
                        title = albumWithSongs.album.title,
                        thumbnailUrl = albumWithSongs.album.thumbnailUrl,
                        fallbackIcon = R.drawable.album,
                        systemBarsTopPadding = systemBarsTopPadding,
                        subtitle = artistNames,
                        metadata = metadata,
                        isAdded = isBookmarked,
                        addContentDescription = R.string.add_to_library,
                        removeContentDescription = R.string.remove_from_library,
                        onShuffle =
                            if (albumWithSongs.songs.isEmpty()) {
                                null
                            } else {
                                {
                                    playerConnection.playQueue(
                                        LocalAlbumRadio(
                                            albumWithSongs.copy(
                                                songs = albumWithSongs.songs.shuffled(),
                                            ),
                                        ),
                                    )
                                }
                            },
                        onPlay =
                            if (albumWithSongs.songs.isEmpty()) {
                                null
                            } else {
                                {
                                    playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                                }
                            },
                        onToggleAdd = {
                            database.query {
                                update(albumWithSongs.album.toggleLike())
                            }
                        },
                        additionalPrimaryActions = { contentColor ->
                            if (albumWithSongs.songs.isNotEmpty()) {
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
                                                    songIds = albumWithSongs.songs.map { it.id },
                                                )
                                            }

                                            is HeaderDownloadState.Partial -> {
                                                navController.navigate("auto_playlist/downloaded?tab=progress")
                                            }

                                            HeaderDownloadState.None -> {
                                                sendAddMissingDownloads(
                                                    context = context,
                                                    songs =
                                                        albumWithSongs.songs.map {
                                                            HeaderDownloadItem(
                                                                id = it.id,
                                                                title = it.song.title,
                                                            )
                                                        },
                                                    downloads = downloads,
                                                )
                                                navController.navigate("auto_playlist/downloaded?tab=progress")
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
                        },
                    )
                }

                // Songs Section Header
                item(key = "songs_header") {
                    NavigationTitle(
                        title = stringResource(R.string.songs),
                    )
                }

                // Songs List
                itemsIndexed(
                    items = wrappedSongs,
                    key = { _, song -> song.item.id },
                ) { index, songWrapper ->
                    SongListItem(
                        song = songWrapper.item,
                        albumIndex = index + 1,
                        isActive = songWrapper.item.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        showInLibraryIcon = true,
                        trailingContent = {
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = songWrapper.item,
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
                        isSelected = songWrapper.isSelected && selection,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .combinedClickable(
                                    onClick = {
                                        if (!selection) {
                                            if (songWrapper.item.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    LocalAlbumRadio(albumWithSongs, startIndex = index),
                                                )
                                            }
                                        } else {
                                            songWrapper.isSelected = !songWrapper.isSelected
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!selection) {
                                            selection = true
                                        }
                                        wrappedSongs.forEach { it.isSelected = false }
                                        songWrapper.isSelected = true
                                    },
                                ),
                    )
                }

                // Other Versions Section
                if (otherVersions.isNotEmpty()) {
                    item(key = "other_versions_header") {
                        NavigationTitle(
                            title = stringResource(R.string.other_versions),
                        )
                    }
                    item(key = "other_versions_list") {
                        LazyRow {
                            items(
                                items = otherVersions.distinctBy { it.id },
                                key = { it.id },
                            ) { item ->
                                YouTubeGridItem(
                                    item = item,
                                    isActive = mediaMetadata?.album?.id == item.id,
                                    isPlaying = isPlaying,
                                    coroutineScope = scope,
                                    modifier =
                                        Modifier
                                            .combinedClickable(
                                                onClick = { navController.navigate("album/${item.id}") },
                                                onLongClick = {
                                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                    menuState.show {
                                                        YouTubeAlbumMenu(
                                                            albumItem = item,
                                                            navController = navController,
                                                            onDismiss = menuState::dismiss,
                                                        )
                                                    }
                                                },
                                            ).animateItem(),
                                )
                            }
                        }
                    }
                }
            } else {
                when (val state = uiState) {
                    AlbumUiState.Loading,
                    AlbumUiState.Content,
                    -> {
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
                                            modifier =
                                                Modifier
                                                    .fillMaxWidth(),
                                            horizontalArrangement =
                                                Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
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
                                                            .background(MaterialTheme.colorScheme.onSurface),
                                                )
                                            }
                                        }
                                    }
                                }

                                repeat(6) {
                                    ListItemPlaceHolder()
                                }
                            }
                        }
                    }

                    AlbumUiState.Empty -> {
                        item(key = "empty") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight)
                                        .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text = stringResource(R.string.empty_album),
                                    style = MaterialTheme.typography.titleLarge,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = stringResource(R.string.empty_album_desc),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        }
                    }

                    is AlbumUiState.Error -> {
                        item(key = "error") {
                            Column(
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(top = systemBarsTopPadding + AppBarHeight)
                                        .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                Text(
                                    text =
                                        if (state.isNotFound) {
                                            stringResource(
                                                R.string.album_not_found,
                                            )
                                        } else {
                                            stringResource(R.string.error_unknown)
                                        },
                                    style = MaterialTheme.typography.titleLarge,
                                    color = if (state.isNotFound) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
                                    textAlign = TextAlign.Center,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text =
                                        if (state.isNotFound) {
                                            stringResource(
                                                R.string.album_not_found_desc,
                                            )
                                        } else {
                                            stringResource(R.string.error_unknown)
                                        },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                )
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
            modifier = Modifier.align(Alignment.TopCenter),
            colors = topAppBarColors,
            scrollBehavior = scrollBehavior,
            title = {
                if (selection) {
                    val count = wrappedSongs.count { it.isSelected }
                    Text(
                        text = pluralStringResource(R.plurals.n_song, count, count),
                        style = MaterialTheme.typography.titleLarge,
                    )
                } else if (showTopBarTitle) {
                    Text(
                        text = albumWithSongs?.album?.title.orEmpty(),
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (selection) {
                            selection = false
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!selection) {
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
                                    if (count == wrappedSongs.size) R.drawable.deselect else R.drawable.select_all,
                                ),
                            contentDescription = null,
                        )
                    }

                    IconButton(
                        onClick = {
                            menuState.show {
                                SelectionSongMenu(
                                    songSelection =
                                        wrappedSongs
                                            .filter { it.isSelected }
                                            .map { it.item },
                                    onDismiss = menuState::dismiss,
                                    clearAction = { selection = false },
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
                } else {
                    albumWithSongs?.let { currentAlbum ->
                        IconButton(
                            onClick = {
                                menuState.show {
                                    AlbumMenu(
                                        originalAlbum =
                                            Album(
                                                currentAlbum.album,
                                                currentAlbum.artists,
                                            ),
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
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
    }
}

private const val MediaDetailMetadataSeparator = "  •  "
