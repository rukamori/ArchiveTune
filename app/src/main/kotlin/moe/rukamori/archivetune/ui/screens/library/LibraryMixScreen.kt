/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryMixViewModel
import moe.rukamori.archivetune.viewmodels.LibraryRefreshState
import moe.rukamori.archivetune.viewmodels.LibraryTopMixEmptyReason
import moe.rukamori.archivetune.viewmodels.LibraryTopMixUiModel
import moe.rukamori.archivetune.viewmodels.LibraryTopMixesUiState
import moe.rukamori.archivetune.viewmodels.MostPlayedAlbumUiModel
import moe.rukamori.archivetune.viewmodels.MostPlayedAlbumUiState

private const val LibraryMixRowLimit = 12

@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    onTabSelected: (LibraryFilter) -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
    spotifyLibraryViewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val showMessage: (String) -> Unit =
        remember(coroutineScope, snackbarHostState) {
            { message ->
                coroutineScope.launch { snackbarHostState.showSnackbar(message) }
                Unit
            }
        }
    val database = LocalDatabase.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val isPlaying by playerConnection.isPlaying.collectAsState()

    val likedSongsCount by database.likedSongsCount().collectAsState(initial = 0)
    val recentSongs by database.recentSongs(15).collectAsState(initial = emptyList())
    val topSize by viewModel.topValue.collectAsStateWithLifecycle(initialValue = "50")

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val refreshLibrary = remember(viewModel) { { viewModel.syncAllLibrary() } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val mostPlayedAlbumUiState by viewModel.mostPlayedAlbumUiState.collectAsStateWithLifecycle()
    val topMixesUiState by viewModel.topMixesUiState.collectAsStateWithLifecycle()
    val spotifyPlaylists by spotifyLibraryViewModel.playlists.collectAsStateWithLifecycle()
    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, false)

    val filteredPlaylistIds by database
        .playlistIdsByTags(
            if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
        ).collectAsState(initial = emptyList())

    val visiblePlaylists =
        remember(playlists, selectedTagIds, filteredPlaylistIds) {
            playlists.filter { playlist ->
                val matchesName = !playlist.playlist.name.contains("episode", ignoreCase = true)
                val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
                matchesName && matchesTags && !playlist.playlist.isHidden
            }
        }
    val visibleSpotifyPlaylists = if (showSpotifyPlaylists) spotifyPlaylists else emptyList()
    val recentMediaItems = remember(recentSongs) { recentSongs.map { it.toMediaItem() } }
    val recentlyPlayedTitle = stringResource(R.string.recently_played)

    LaunchedEffect(viewModel) {
        viewModel.topMixEvents.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }
    LibraryRefreshFeedbackEffect(
        state = refreshState,
        snackbarHostState = snackbarHostState,
        onErrorShown = onRefreshErrorShown,
    )

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    val playPlaylist: (Playlist) -> Unit =
        remember(database, playerConnection, coroutineScope) {
            { playlist ->
                coroutineScope.launch {
                    val songs = database.playlistSongs(playlist.id).firstOrNull().orEmpty()
                    if (songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = playlist.playlist.name,
                                items = songs.map { it.song.toMediaItem() },
                            ),
                        )
                    }
                }
            }
        }
    val playAlbum: (Album) -> Unit =
        remember(database, playerConnection, coroutineScope) {
            { album ->
                coroutineScope.launch {
                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                    }
                }
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = refreshState is LibraryRefreshState.Loading,
            onRefresh = refreshLibrary,
            modifier = Modifier.fillMaxSize(),
            indicatorOffset = LibraryPullToRefreshIndicatorOffset,
        ) {
            LazyColumn(
                state = rememberLazyListState(),
                verticalArrangement = Arrangement.spacedBy(28.dp),
                contentPadding =
                    PaddingValues(
                        top = LibraryHeaderContentPadding + 8.dp,
                        bottom = playerAwareBottomPadding + 16.dp,
                    ),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "quick_access", contentType = "quick_access") {
                    LibraryQuickAccessBento(
                        likedSongsCount = likedSongsCount,
                        topSize = topSize,
                        onOpenLiked = { navController.navigate("auto_playlist/liked") },
                        onOpenDownloaded = { navController.navigate("auto_playlist/downloaded") },
                        onOpenCached = { navController.navigate("cache_playlist/cached") },
                        onOpenLocal = { navController.navigate("local_songs") },
                        onOpenTop = { navController.navigate("top_playlist/$topSize") },
                        modifier = Modifier.padding(horizontal = LibraryContentHorizontalPadding),
                    )
                }

                val mostPlayedState = mostPlayedAlbumUiState
                if (mostPlayedState is MostPlayedAlbumUiState.Success) {
                    item(key = "most_played_album", contentType = "spotlight") {
                        val album = mostPlayedState.album
                        MostPlayedAlbumSpotlight(
                            album = album,
                            onOpenAlbum = { navController.navigateToAlbum(album.id) },
                            onPlay = {
                                playerConnection.playQueue(
                                    ListQueue(title = album.title, items = album.tracks.map { it.toMediaItem() }),
                                )
                            },
                            onShuffle = {
                                playerConnection.playQueue(
                                    ListQueue(title = album.title, items = album.tracks.shuffled().map { it.toMediaItem() }),
                                )
                            },
                            modifier = Modifier.padding(horizontal = LibraryContentHorizontalPadding),
                        )
                    }
                }

                if (supportArchiveTuneAvailable) {
                    item(key = "support_archive_tune", contentType = "support_ad") {
                        SupportArchiveTuneSection(
                            onMessage = showMessage,
                            modifier = Modifier.padding(horizontal = LibraryContentHorizontalPadding),
                        )
                    }
                }

                if (recentSongs.isNotEmpty()) {
                    item(key = "recently_played", contentType = "media_row") {
                        LibraryMixSection(title = recentlyPlayedTitle) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                itemsIndexed(
                                    items = recentSongs,
                                    key = { _, song -> song.id },
                                    contentType = { _, _ -> "recent_song" },
                                ) { index, song ->
                                    val isActive = song.id == mediaMetadata?.id
                                    RecentSongCard(
                                        title = song.song.title,
                                        subtitle = song.artists.joinToString(", ") { it.name },
                                        thumbnailUrl = song.song.thumbnailUrl,
                                        isActive = isActive,
                                        isPlaying = isActive && isPlaying,
                                        onClick = {
                                            if (isActive) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    ListQueue(
                                                        title = recentlyPlayedTitle,
                                                        items = recentMediaItems,
                                                        startIndex = index,
                                                    ),
                                                )
                                            }
                                        },
                                    )
                                }
                            }
                        }
                    }
                }

                item(key = "top_mixes", contentType = "top_mixes") {
                    TopMixesSection(
                        state = topMixesUiState,
                        onRefresh = viewModel::refreshTopMixes,
                        onConfigureAi = { navController.navigate("settings/ai_integration") },
                        onPlayMix = { mix ->
                            playerConnection.playQueue(
                                ListQueue(title = mix.title, items = mix.tracks.map { it.toMediaItem() }),
                            )
                        },
                    )
                }

                if (visiblePlaylists.isNotEmpty() || visibleSpotifyPlaylists.isNotEmpty() || filterContent != null) {
                    item(key = "your_playlists", contentType = "media_row") {
                        val playlistCount = visiblePlaylists.size + visibleSpotifyPlaylists.size
                        LibraryMixSection(
                            title = stringResource(R.string.your_playlists),
                            subtitle = pluralStringResource(R.plurals.n_playlist, playlistCount, playlistCount),
                            onSeeAll = { onTabSelected(LibraryFilter.PLAYLISTS) },
                        ) {
                            filterContent?.invoke()
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                modifier = Modifier.animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
                            ) {
                                items(
                                    items = visiblePlaylists.take(LibraryMixRowLimit),
                                    key = { playlist -> "playlist_${playlist.id}" },
                                    contentType = { "library_playlist" },
                                ) { playlist ->
                                    val songCount = playlist.displaySongCount()
                                    LibraryMixArtworkCard(
                                        title = playlist.playlist.name,
                                        subtitle = pluralStringResource(R.plurals.n_song, songCount, songCount),
                                        artworkUrls = playlist.thumbnails,
                                        fallbackIconRes = R.drawable.queue_music,
                                        onClick = { openPlaylist(navController, playlist) },
                                        onPlay = { playPlaylist(playlist) },
                                        modifier =
                                            Modifier.animateItem(
                                                fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                                                placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                                                fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                                            ),
                                    )
                                }
                                items(
                                    items = visibleSpotifyPlaylists.take(LibraryMixRowLimit),
                                    key = { playlist -> "spotify_playlist_${playlist.id}" },
                                    contentType = { "library_spotify_playlist" },
                                ) { playlist ->
                                    SpotifyPlaylistTile(
                                        playlist = playlist,
                                        onClick = { navController.navigate("spotify_playlist/${playlist.id}") },
                                        modifier = Modifier.width(148.dp),
                                    )
                                }
                            }
                        }
                    }
                }

                if (albums.isNotEmpty()) {
                    item(key = "your_albums", contentType = "media_row") {
                        LibraryMixSection(
                            title = stringResource(R.string.library_your_albums),
                            subtitle = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
                            onSeeAll = { onTabSelected(LibraryFilter.ALBUMS) },
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                items(
                                    items = albums.take(LibraryMixRowLimit),
                                    key = { album -> "album_${album.id}" },
                                    contentType = { "library_album" },
                                ) { album ->
                                    LibraryMixArtworkCard(
                                        title = album.album.title,
                                        subtitle = album.subtitleText(),
                                        artworkUrls = listOfNotNull(album.album.thumbnailUrl),
                                        fallbackIconRes = R.drawable.album,
                                        onClick = { navController.navigateToAlbum(album.id) },
                                        onPlay = { playAlbum(album) },
                                    )
                                }
                            }
                        }
                    }
                }

                if (artists.isNotEmpty()) {
                    item(key = "your_artists", contentType = "artist_row") {
                        LibraryMixSection(
                            title = stringResource(R.string.your_artists),
                            subtitle = pluralStringResource(R.plurals.n_artist, artists.size, artists.size),
                            onSeeAll = { onTabSelected(LibraryFilter.ARTISTS) },
                        ) {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                            ) {
                                items(
                                    items = artists.take(LibraryMixRowLimit),
                                    key = { artist -> "artist_${artist.id}" },
                                    contentType = { "library_artist" },
                                ) { artist ->
                                    MixArtistAvatar(
                                        artist = artist,
                                        onClick = { navController.navigateToArtist(artist.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = playerAwareBottomPadding),
        )
    }
}

private fun Album.subtitleText(): String {
    val artistNames = artists.joinToString(", ") { it.name }
    return listOfNotNull(artistNames.ifBlank { null }, album.year?.toString()).joinToString(" • ")
}

@Composable
private fun LibraryMixSection(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibrarySectionHeader(
            title = title,
            subtitle = subtitle,
            actionLabel = onSeeAll?.let { stringResource(R.string.see_all) },
            onAction = onSeeAll,
        )
        content()
    }
}

@Composable
private fun LibraryQuickAccessBento(
    likedSongsCount: Int,
    topSize: String,
    onOpenLiked: () -> Unit,
    onOpenDownloaded: () -> Unit,
    onOpenCached: () -> Unit,
    onOpenLocal: () -> Unit,
    onOpenTop: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val heartShape = MaterialShapes.Heart.toShape()
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            LikedSongsTile(
                count = likedSongsCount,
                iconShape = heartShape,
                onClick = onOpenLiked,
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
            )
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                QuickAccessTile(
                    title = stringResource(R.string.offline_shortcut),
                    subtitle = stringResource(R.string.downloaded_desc),
                    iconRes = R.drawable.offline,
                    accent = MaterialTheme.colorScheme.secondary,
                    onClick = onOpenDownloaded,
                )
                QuickAccessTile(
                    title = stringResource(R.string.cached),
                    subtitle = stringResource(R.string.instant_playback),
                    iconRes = R.drawable.cached,
                    accent = MaterialTheme.colorScheme.tertiary,
                    onClick = onOpenCached,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            QuickAccessTile(
                title = stringResource(R.string.local_files),
                subtitle = stringResource(R.string.on_device),
                iconRes = R.drawable.snippet_folder,
                accent = MaterialTheme.colorScheme.secondary,
                onClick = onOpenLocal,
                modifier = Modifier.weight(1f),
            )
            QuickAccessTile(
                title = "${stringResource(R.string.my_top)} $topSize",
                subtitle = stringResource(R.string.all_time),
                iconRes = R.drawable.trending_up,
                accent = MaterialTheme.colorScheme.tertiary,
                onClick = onOpenTop,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun LikedSongsTile(
    count: Int,
    iconShape: Shape,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        modifier =
            modifier.graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(52.dp)
                        .background(MaterialTheme.colorScheme.primary, iconShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.favorite),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            Column(modifier = Modifier.semantics(mergeDescendants = true) {}) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.displaySmallEmphasized,
                    maxLines = 1,
                )
                Text(
                    text = stringResource(R.string.liked_songs),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun QuickAccessTile(
    title: String,
    subtitle: String,
    @DrawableRes iconRes: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    Surface(
        onClick = onClick,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(40.dp)
                        .background(accent.copy(alpha = 0.16f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmallEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun MostPlayedAlbumSpotlight(
    album: MostPlayedAlbumUiModel,
    onOpenAlbum: () -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val containerColor =
        rememberArtworkCardColor(
            thumbnailUrl = album.thumbnailUrl,
            fallbackColor = MaterialTheme.colorScheme.surfaceContainer,
        )
    Surface(
        onClick = onOpenAlbum,
        shape = MaterialTheme.shapes.extraLarge,
        color = containerColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ItemThumbnail(
                thumbnailUrl = album.thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = MaterialTheme.shapes.large,
                contentScale = ContentScale.Crop,
                showPlaceholder = true,
                placeholderIconRes = R.drawable.album,
                modifier = Modifier.size(112.dp),
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.star),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(12.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.most_played_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = album.title,
                    style = MaterialTheme.typography.titleLargeEmphasized,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, album.trackCount, album.trackCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onPlay,
                        enabled = album.tracks.isNotEmpty(),
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.IconSize),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                        Text(text = stringResource(R.string.play_all), maxLines = 1)
                    }
                    FilledTonalIconButton(
                        onClick = onShuffle,
                        enabled = album.tracks.isNotEmpty(),
                        shapes = IconButtonDefaults.shapes(),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.shuffle),
                            contentDescription = stringResource(R.string.shuffle),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RecentSongCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    Column(
        modifier =
            Modifier
                .width(128.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(MaterialTheme.shapes.large)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ).padding(bottom = LibraryGridCardTextInset),
    ) {
        LibraryNowPlayingArtwork(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = title,
            style = if (isActive) MaterialTheme.typography.titleSmallEmphasized else MaterialTheme.typography.titleSmall,
            color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
    }
}

@Composable
private fun LibraryMixArtworkCard(
    title: String,
    subtitle: String,
    artworkUrls: List<String>,
    @DrawableRes fallbackIconRes: Int,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    Column(
        modifier =
            modifier
                .width(148.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(libraryGridCardShape())
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ).padding(bottom = LibraryGridCardTextInset),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            LibraryArtworkMosaic(
                artworkUrls = artworkUrls,
                fallbackIconRes = fallbackIconRes,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize(),
            )
            FilledIconButton(
                onClick = onPlay,
                shapes = IconButtonDefaults.shapes(),
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
    }
}

@Composable
private fun MixArtistAvatar(
    artist: Artist,
    onClick: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource, pressedScale = 0.92f)
    Column(
        modifier =
            Modifier
                .width(92.dp)
                .clip(MaterialTheme.shapes.large)
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ).padding(start = 6.dp, top = 4.dp, end = 6.dp, bottom = LibraryGridCardTextInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        ItemThumbnail(
            thumbnailUrl = artist.artist.thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            contentScale = ContentScale.Crop,
            showPlaceholder = true,
            placeholderIconRes = R.drawable.person,
            modifier =
                Modifier
                    .size(80.dp)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    },
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = artist.artist.name,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Text(
            text = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TopMixesSection(
    state: LibraryTopMixesUiState,
    onRefresh: () -> Unit,
    onConfigureAi: () -> Unit,
    onPlayMix: (LibraryTopMixUiModel) -> Unit,
) {
    val isRefreshing =
        when (state) {
            LibraryTopMixesUiState.Loading -> true
            is LibraryTopMixesUiState.Empty -> state.isRefreshing
            is LibraryTopMixesUiState.Success -> state.isRefreshing
            is LibraryTopMixesUiState.Error -> false
        }
    val showRefresh =
        state !is LibraryTopMixesUiState.Loading &&
            !(state is LibraryTopMixesUiState.Empty && state.reason == LibraryTopMixEmptyReason.AI_NOT_CONFIGURED)

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .animateContentSize(MaterialTheme.motionScheme.defaultSpatialSpec()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        LibrarySectionHeader(
            title = stringResource(R.string.top_mixes),
            trailingContent = {
                when {
                    isRefreshing -> {
                        LoadingIndicator(
                            modifier =
                                Modifier
                                    .padding(horizontal = 4.dp)
                                    .size(40.dp),
                        )
                    }

                    showRefresh -> {
                        IconButton(
                            onClick = onRefresh,
                            shapes = IconButtonDefaults.shapes(),
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.sync),
                                contentDescription = stringResource(R.string.library_top_mixes_refresh),
                            )
                        }
                    }
                }
            },
        )

        when (state) {
            LibraryTopMixesUiState.Loading -> {
                TopMixesMessage(message = stringResource(R.string.library_top_mixes_loading))
            }

            is LibraryTopMixesUiState.Error -> {
                TopMixesMessage(message = state.message)
            }

            is LibraryTopMixesUiState.Empty -> {
                TopMixesEmptyCard(
                    reason = state.reason,
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    onConfigureAi = onConfigureAi,
                )
            }

            is LibraryTopMixesUiState.Success -> {
                if (state.mixes.isEmpty()) {
                    TopMixesMessage(message = stringResource(R.string.library_top_mixes_no_recent_history))
                } else {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(
                            items = state.mixes,
                            key = { mix -> mix.id },
                            contentType = { "library_top_mix" },
                        ) { mix ->
                            TopMixCard(
                                mix = mix,
                                onPlay = { onPlayMix(mix) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMixesMessage(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = LibraryContentHorizontalPadding),
    )
}

@Composable
private fun TopMixesEmptyCard(
    reason: LibraryTopMixEmptyReason,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onConfigureAi: () -> Unit,
) {
    val sparkleShape = MaterialShapes.Sunny.toShape()
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainer,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = LibraryContentHorizontalPadding),
    ) {
        Row(
            modifier = Modifier.padding(20.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(48.dp)
                        .background(MaterialTheme.colorScheme.tertiaryContainer, sparkleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.auto_awesome),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(
                    text =
                        stringResource(
                            when (reason) {
                                LibraryTopMixEmptyReason.AI_NOT_CONFIGURED -> R.string.library_top_mixes_ai_not_configured_title
                                LibraryTopMixEmptyReason.NO_RECENT_HISTORY -> R.string.library_top_mixes_no_recent_history_title
                            },
                        ),
                    style = MaterialTheme.typography.titleMediumEmphasized,
                )
                Text(
                    text =
                        stringResource(
                            when (reason) {
                                LibraryTopMixEmptyReason.AI_NOT_CONFIGURED -> R.string.library_top_mixes_ai_not_configured_desc
                                LibraryTopMixEmptyReason.NO_RECENT_HISTORY -> R.string.library_top_mixes_no_recent_history
                            },
                        ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (reason == LibraryTopMixEmptyReason.AI_NOT_CONFIGURED) {
                    Button(
                        onClick = onConfigureAi,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(R.string.library_top_mixes_configure_ai))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(text = stringResource(R.string.library_top_mixes_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMixCard(
    mix: LibraryTopMixUiModel,
    onPlay: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    val artworkUrls = remember(mix.tracks) { mix.tracks.mapNotNull { it.thumbnailUrl }.distinct().take(3) }
    val ringColor = MaterialTheme.colorScheme.secondaryContainer

    Surface(
        onClick = onPlay,
        interactionSource = interactionSource,
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier =
            Modifier
                .width(232.dp)
                .heightIn(min = 168.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                },
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(modifier = Modifier.height(48.dp)) {
                artworkUrls.forEachIndexed { index, url ->
                    AsyncImage(
                        model = url,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .offset(x = (index * 32).dp)
                                .size(48.dp)
                                .border(width = 2.dp, color = ringColor, shape = CircleShape)
                                .padding(2.dp)
                                .clip(CircleShape),
                    )
                }
            }
            Column {
                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = mix.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.76f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = pluralStringResource(R.plurals.n_song, mix.tracks.size, mix.tracks.size),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier =
                        Modifier
                            .size(40.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
