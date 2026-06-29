/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.drop
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CONTENT_TYPE_LIST
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.*
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.viewmodels.LocalFilter
import moe.rukamori.archivetune.viewmodels.LocalSearchViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LocalSearchScreen(
    query: String,
    navController: NavController,
    onDismiss: () -> Unit,
    isFromCache: Boolean = false,
    pureBlack: Boolean,
    viewModel: LocalSearchViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val searchFilter by viewModel.filter.collectAsState()
    val result by viewModel.result.collectAsState()

    val lazyListState = rememberLazyListState()

    LaunchedEffect(Unit) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.query.value = query
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(if (pureBlack) Color.Black else MaterialTheme.colorScheme.background),
    ) {
        Surface(
            color = if (pureBlack) Color.Black else MaterialTheme.colorScheme.surface,
            tonalElevation = if (pureBlack) 0.dp else 0.dp,
            shadowElevation = if (pureBlack) 0.dp else 1.dp,
        ) {
            ChipsRow(
                chips =
                    listOf(
                        LocalFilter.ALL to stringResource(R.string.filter_all),
                        LocalFilter.SONG to stringResource(R.string.filter_songs),
                        LocalFilter.ALBUM to stringResource(R.string.filter_albums),
                        LocalFilter.ARTIST to stringResource(R.string.filter_artists),
                        LocalFilter.PLAYLIST to stringResource(R.string.filter_playlists),
                    ),
                currentValue = searchFilter,
                onValueUpdate = { viewModel.filter.value = it },
                icons =
                    mapOf(
                        LocalFilter.ALL to R.drawable.search,
                        LocalFilter.SONG to R.drawable.music_note,
                        LocalFilter.ALBUM to R.drawable.album,
                        LocalFilter.ARTIST to R.drawable.person,
                        LocalFilter.PLAYLIST to R.drawable.queue_music,
                    ),
            )
        }

        LazyColumn(
            state = lazyListState,
            contentPadding = PaddingValues(top = 8.dp),
            modifier = Modifier.weight(1f),
        ) {
            result.map.forEach { (filter, items) ->
                if (result.filter == LocalFilter.ALL) {
                    item(key = filter) {
                        val filterIcon =
                            when (filter) {
                                LocalFilter.SONG -> R.drawable.music_note
                                LocalFilter.ALBUM -> R.drawable.album
                                LocalFilter.ARTIST -> R.drawable.person
                                LocalFilter.PLAYLIST -> R.drawable.queue_music
                                LocalFilter.ALL -> R.drawable.search
                            }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .focusable()
                                    .clickable { viewModel.filter.value = filter }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .size(36.dp)
                                        .background(
                                            color =
                                                if (pureBlack) {
                                                    Color.White.copy(
                                                        alpha = 0.08f,
                                                    )
                                                } else {
                                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
                                                },
                                            shape = RoundedCornerShape(10.dp),
                                        ),
                            ) {
                                Icon(
                                    painter = painterResource(filterIcon),
                                    contentDescription = null,
                                    tint = if (pureBlack) Color.White.copy(alpha = 0.7f) else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp),
                                )
                            }

                            Spacer(Modifier.width(14.dp))

                            Text(
                                text =
                                    stringResource(
                                        when (filter) {
                                            LocalFilter.SONG -> R.string.filter_songs
                                            LocalFilter.ALBUM -> R.string.filter_albums
                                            LocalFilter.ARTIST -> R.string.filter_artists
                                            LocalFilter.PLAYLIST -> R.string.filter_playlists
                                            LocalFilter.ALL -> error("")
                                        },
                                    ),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = if (pureBlack) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                            )

                            Icon(
                                painter = painterResource(R.drawable.navigate_next),
                                contentDescription = null,
                                tint =
                                    if (pureBlack) {
                                        Color.White.copy(
                                            alpha = 0.3f,
                                        )
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    },
                            )
                        }
                    }
                }

                items(
                    items = items.distinctBy { it.id },
                    key = { it.id },
                    contentType = { CONTENT_TYPE_LIST },
                ) { item ->
                    when (item) {
                        is Song -> {
                            SongListItem(
                                song = item,
                                showInLibraryIcon = true,
                                isActive = item.id == mediaMetadata?.id,
                                isPlaying = isPlaying,
                                trailingContent = {
                                    IconButton(
                                        onClick = {
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = item,
                                                    navController = navController,
                                                    onDismiss = {
                                                        onDismiss()
                                                        menuState.dismiss()
                                                    },
                                                    isFromCache = isFromCache,
                                                )
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
                                        .combinedClickable(
                                            onClick = {
                                                if (item.id == mediaMetadata?.id) {
                                                    playerConnection.player.togglePlayPause()
                                                } else {
                                                    val songs =
                                                        result.map
                                                            .getOrDefault(LocalFilter.SONG, emptyList())
                                                            .filterIsInstance<Song>()
                                                            .map { it.toMediaItem() }
                                                    playerConnection.playQueue(
                                                        ListQueue(
                                                            title = context.getString(R.string.queue_searched_songs),
                                                            items = songs,
                                                            startIndex = songs.indexOfFirst { it.mediaId == item.id },
                                                        ),
                                                    )
                                                }
                                            },
                                            onLongClick = {
                                                menuState.show {
                                                    SongMenu(
                                                        originalSong = item,
                                                        navController = navController,
                                                        onDismiss = {
                                                            onDismiss()
                                                            menuState.dismiss()
                                                        },
                                                        isFromCache = isFromCache,
                                                    )
                                                }
                                            },
                                        ).animateItem(),
                            )
                        }

                        is Album -> {
                            AlbumListItem(
                                album = item,
                                isActive = item.id == mediaMetadata?.album?.id,
                                isPlaying = isPlaying,
                                modifier =
                                    Modifier
                                        .clickable {
                                            onDismiss()
                                            navController.navigate("album/${item.id}")
                                        }.animateItem(),
                            )
                        }

                        is Artist -> {
                            ArtistListItem(
                                artist = item,
                                modifier =
                                    Modifier
                                        .clickable {
                                            onDismiss()
                                            navController.navigate("artist/${item.id}")
                                        }.animateItem(),
                            )
                        }

                        is Playlist -> {
                            PlaylistListItem(
                                playlist = item,
                                modifier =
                                    Modifier
                                        .clickable {
                                            onDismiss()
                                            navController.navigate("local_playlist/${item.id}")
                                        }.animateItem(),
                            )
                        }
                    }
                }
            }

            if (result.query.isNotEmpty() && result.map.isEmpty()) {
                item(key = "no_result") {
                    EmptyPlaceholder(
                        icon = R.drawable.search,
                        text = stringResource(R.string.no_results_found),
                    )
                }
            }
        }
    }
}
