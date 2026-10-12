/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3Api::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.spotify.SpotifyLikedSongUiModel
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsEvent
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsScreenState
import moe.rukamori.archivetune.spotify.SpotifyLikedSongsViewModel
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.ListItem
import moe.rukamori.archivetune.ui.component.MediaDetailHero
import moe.rukamori.archivetune.ui.component.MediaDetailIconAction
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
fun SpotifyLikedSongsScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyLikedSongsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val likedSongsTitle = stringResource(R.string.liked_songs)

    LaunchedEffect(viewModel, playerConnection) {
        viewModel.events.collect { event ->
            when (event) {
                is SpotifyLikedSongsEvent.PlaybackRequested -> {
                    playerConnection?.playQueue(
                        event.queue,
                    )
                }
            }
        }
    }

    SpotifyLikedSongsContent(
        state = state,
        title = likedSongsTitle,
        scrollBehavior = scrollBehavior,
        onNavigateUp = { navController.navigateUp() },
        onNavigateHome = { navController.backToMain() },
        onRefresh = viewModel::reload,
        onPlayAll = viewModel::playAll,
        onPlaySong = viewModel::playSong,
    )
}

@Composable
private fun SpotifyLikedSongsContent(
    state: SpotifyLikedSongsScreenState,
    title: String,
    scrollBehavior: TopAppBarScrollBehavior,
    onNavigateUp: () -> Unit,
    onNavigateHome: () -> Unit,
    onRefresh: () -> Unit,
    onPlayAll: (Boolean) -> Unit,
    onPlaySong: (Int) -> Unit,
) {
    val songs: ImmutableList<SpotifyLikedSongUiModel> =
        (state as? SpotifyLikedSongsScreenState.Success)?.songs ?: ImmutableList.of()
    val lazyListState = rememberLazyListState()
    val showTopBarTitle by remember { derivedStateOf { lazyListState.firstVisibleItemIndex > 0 } }
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val isLoading = state is SpotifyLikedSongsScreenState.Loading
    val surfaceColor = MaterialTheme.colorScheme.surface
    val canPlay = songs.isNotEmpty()
    val onShuffle: (() -> Unit)? =
        remember(canPlay, onPlayAll) {
            if (canPlay) {
                { onPlayAll(true) }
            } else {
                null
            }
        }
    val onPlay: (() -> Unit)? =
        remember(canPlay, onPlayAll) {
            if (canPlay) {
                { onPlayAll(false) }
            } else {
                null
            }
        }
    val metadata =
        if (state is SpotifyLikedSongsScreenState.Success) {
            pluralStringResource(R.plurals.n_song, songs.size, songs.size)
        } else {
            null
        }

    ExpressivePullToRefreshBox(
        isRefreshing = isLoading,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize().background(surfaceColor),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    bottom =
                        LocalPlayerAwareWindowInsets.current
                            .union(WindowInsets.systemBars)
                            .asPaddingValues()
                            .calculateBottomPadding(),
                ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header", contentType = "spotify_liked_songs_header") {
                MediaDetailHero(
                    title = title,
                    thumbnailUrl = null,
                    fallbackIcon = R.drawable.favorite,
                    systemBarsTopPadding = systemBarsTopPadding,
                    metadata = metadata,
                    isAdded = false,
                    addContentDescription = R.string.add_to_library,
                    removeContentDescription = R.string.remove_from_library,
                    onShuffle = onShuffle,
                    onPlay = onPlay,
                    onToggleAdd = null,
                    additionalPrimaryActions = { contentColor ->
                        MediaDetailIconAction(
                            icon = R.drawable.sync,
                            contentDescription = R.string.spotify_reload_playlist,
                            contentColor = contentColor,
                            onClick = onRefresh,
                        )
                    },
                )
            }

            when (state) {
                SpotifyLikedSongsScreenState.Loading -> {
                    item(key = "loading", contentType = "loading") {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxWidth().height(160.dp),
                        ) {
                            CircularWavyProgressIndicator()
                        }
                    }
                }

                SpotifyLikedSongsScreenState.Empty -> {
                    item(key = "empty", contentType = "empty") {
                        EmptyPlaceholder(
                            icon = R.drawable.music_note,
                            text = stringResource(R.string.spotify_no_tracks),
                        )
                    }
                }

                SpotifyLikedSongsScreenState.Error -> {
                    item(key = "error", contentType = "error") {
                        EmptyPlaceholder(
                            icon = R.drawable.spotify_icon,
                            text = stringResource(R.string.error_unknown),
                        )
                    }
                }

                is SpotifyLikedSongsScreenState.Success -> {
                    itemsIndexed(
                        items = songs,
                        key = { index, song -> "${song.id}_$index" },
                        contentType = { _, _ -> "spotify_liked_song" },
                    ) { index, song ->
                        val onSongClick = remember(index, onPlaySong) { { onPlaySong(index) } }
                        SpotifyLikedSongListItem(
                            song = song,
                            onClick = onSongClick,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
        }

        TopAppBar(
            colors =
                if (showTopBarTitle) {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                        scrolledContainerColor = MaterialTheme.colorScheme.surface,
                    )
                } else {
                    TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                        navigationIconContentColor = Color.White,
                        titleContentColor = Color.White,
                    )
                },
            title = {
                if (showTopBarTitle) {
                    Text(text = title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            },
            navigationIcon = {
                IconButton(onClick = onNavigateUp, onLongClick = onNavigateHome) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = null,
                    )
                }
            },
            scrollBehavior = scrollBehavior,
        )
    }
}

@Composable
private fun SpotifyLikedSongListItem(
    song: SpotifyLikedSongUiModel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val duration =
        remember(song.durationMs) {
            song.durationMs.takeIf { it > 0 }?.toLong()?.let(::makeTimeString)
        }
    val subtitle = remember(song.artistNames, duration) { joinByBullet(song.artistNames.joinToString(), duration) }
    val isExplicit = song.isExplicit
    val badges: @Composable RowScope.() -> Unit =
        remember(isExplicit) {
            {
                if (isExplicit) {
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }

    ListItem(
        title = song.title,
        subtitle = subtitle,
        badges = badges,
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = song.thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(ThumbnailCornerRadius),
                placeholderIconRes = R.drawable.music_note,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        modifier = modifier.clickable(onClick = onClick),
    )
}
