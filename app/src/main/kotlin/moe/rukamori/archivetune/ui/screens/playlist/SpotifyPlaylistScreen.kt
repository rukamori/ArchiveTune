/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.playlist

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.SpotifyPlaybackResolver
import moe.rukamori.archivetune.spotify.SpotifyPlaylistQueue
import moe.rukamori.archivetune.spotify.SpotifyPlaylistViewModel
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.ui.component.DraggableScrollbar
import moe.rukamori.archivetune.ui.component.EmptyPlaceholder
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.MediaDetailHero
import moe.rukamori.archivetune.ui.component.MediaDetailIconAction
import moe.rukamori.archivetune.ui.component.SpotifyTrackListItem
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.makeTimeString
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpotifyPlaylistScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: SpotifyPlaylistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val coroutineScope = rememberCoroutineScope()
    val isPlaying by playerConnection?.isPlaying?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf(false) }
    val mediaMetadata by playerConnection?.mediaMetadata?.collectAsStateWithLifecycle()
        ?: remember { mutableStateOf<MediaMetadata?>(null) }
    val playlist = state.playlist
    val tracks = state.tracks
    val lazyListState = rememberLazyListState()
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()

    val showTopBarTitle by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    var isSearching by rememberSaveable { mutableStateOf(false) }
    var resolvingTrackId by remember { mutableStateOf<String?>(null) }
    var query by rememberSaveable(stateSaver = TextFieldValue.Saver) { mutableStateOf(TextFieldValue()) }
    val focusRequester = remember { FocusRequester() }

    val filteredTracks =
        remember(tracks, query.text) {
            if (query.text.isBlank()) {
                tracks
            } else {
                tracks.filter { track ->
                    track.name.contains(query.text, ignoreCase = true) ||
                        track.artists.any { artist -> artist.name.contains(query.text, ignoreCase = true) } ||
                        track.album?.name?.contains(query.text, ignoreCase = true) == true
                }
            }
        }

    val loadedDurationMs =
        remember(tracks) {
            tracks.sumOf { track -> track.durationMs.toLong() }
        }

    val surfaceColor = MaterialTheme.colorScheme.surface

    val thumbnailUrl =
        remember(playlist) {
            playlist?.let { SpotifyMapper.getPlaylistThumbnail(it)?.resize(544, 544) }
        }

    val transparentAppBar by remember {
        derivedStateOf { !showTopBarTitle && !isSearching }
    }

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

    LaunchedEffect(isSearching) {
        if (isSearching) focusRequester.requestFocus()
    }

    if (isSearching) {
        BackHandler {
            isSearching = false
            query = TextFieldValue()
        }
    }

    fun playPlaylist(
        startIndex: Int = 0,
        shuffled: Boolean = false,
    ) {
        val currentPlaylist = playlist ?: return
        val queueTracks = if (shuffled) tracks.shuffled() else tracks
        if (queueTracks.isEmpty()) return
        val boundedStartIndex = startIndex.coerceIn(queueTracks.indices)
        val preloadTrack = queueTracks[boundedStartIndex]
        if (resolvingTrackId != null) return

        coroutineScope.launch {
            resolvingTrackId = preloadTrack.id
            try {
                val preloadItem = SpotifyPlaybackResolver.resolveToMetadata(preloadTrack)
                playerConnection?.playQueue(
                    SpotifyPlaylistQueue(
                        playlistId = currentPlaylist.id,
                        title = currentPlaylist.name,
                        initialTracks = queueTracks,
                        startIndex = boundedStartIndex,
                        preloadItem = preloadItem,
                    ),
                )
            } finally {
                resolvingTrackId = null
            }
        }
    }

    ExpressivePullToRefreshBox(
        isRefreshing = state.isLoading,
        onRefresh = viewModel::reload,
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
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(
                        top = if (isSearching) systemBarsTopPadding + AppBarHeight else 0.dp,
                    ),
        ) {
            if (!isSearching) {
                playlist?.let { currentPlaylist ->
                    item(key = "header") {
                        val trackCount = currentPlaylist.tracks?.total ?: tracks.size
                        val metadata =
                            listOfNotNull(
                                pluralStringResource(R.plurals.n_song, trackCount, trackCount),
                                loadedDurationMs
                                    .takeIf { duration -> duration > 0L }
                                    ?.let(::makeTimeString),
                            ).joinToString(MediaDetailMetadataSeparator)

                        MediaDetailHero(
                            title = currentPlaylist.name,
                            thumbnailUrl = thumbnailUrl,
                            fallbackIcon = R.drawable.queue_music,
                            systemBarsTopPadding = systemBarsTopPadding,
                            subtitle =
                                currentPlaylist.owner
                                    ?.displayName
                                    ?.takeIf(String::isNotBlank)
                                    ?.let(::AnnotatedString),
                            metadata = metadata,
                            description = currentPlaylist.description,
                            isAdded = false,
                            addContentDescription = R.string.add_to_library,
                            removeContentDescription = R.string.remove_from_library,
                            onShuffle =
                                if (tracks.isNotEmpty()) {
                                    { playPlaylist(shuffled = true) }
                                } else {
                                    null
                                },
                            onPlay =
                                if (tracks.isNotEmpty()) {
                                    { playPlaylist() }
                                } else {
                                    null
                                },
                            onToggleAdd = null,
                            additionalPrimaryActions = { contentColor ->
                                MediaDetailIconAction(
                                    icon = R.drawable.sync,
                                    contentDescription = R.string.spotify_reload_playlist,
                                    contentColor = contentColor,
                                    onClick = viewModel::reload,
                                )
                                if (tracks.isNotEmpty()) {
                                    MediaDetailIconAction(
                                        icon = R.drawable.mix,
                                        contentDescription = R.string.start_mix,
                                        contentColor = contentColor,
                                        onClick = { playPlaylist(shuffled = true) },
                                    )
                                }
                            },
                        )
                    }
                }
            }

            if (state.isLoading && tracks.isEmpty()) {
                item(key = "loading") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularWavyProgressIndicator()
                    }
                }
            }

            state.errorMessage?.let { error ->
                item(key = "error") {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                    )
                }
            }

            if (!state.isLoading && state.errorMessage == null && filteredTracks.isEmpty()) {
                item(key = "empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text =
                            stringResource(
                                if (query.text.isBlank()) {
                                    R.string.spotify_no_tracks
                                } else {
                                    R.string.ai_model_no_results
                                },
                            ),
                    )
                }
            }

            itemsIndexed(
                items = filteredTracks,
                key = { index, track -> "spotify_track_${track.id}_$index" },
                contentType = { _, _ -> "spotify_track" },
            ) { index, track ->
                val trackIsActive =
                    remember(track, mediaMetadata) {
                        track.isResolvedAs(mediaMetadata)
                    }
                val trackIsResolving = resolvingTrackId == track.id

                SpotifyTrackListItem(
                    track = track,
                    isActive = trackIsActive || trackIsResolving,
                    isPlaying = isPlaying && !trackIsResolving,
                    trailingContent = {
                        if (trackIsResolving) {
                            CircularWavyProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clickable(enabled = resolvingTrackId == null || trackIsActive) {
                                if (trackIsActive) {
                                    playerConnection?.player?.togglePlayPause()
                                } else {
                                    val startIndex =
                                        tracks
                                            .indexOfFirst { item -> item.id == track.id }
                                            .takeIf { itemIndex -> itemIndex >= 0 }
                                            ?: index
                                    playPlaylist(startIndex = startIndex)
                                }
                            },
                )
            }
        }

        DraggableScrollbar(
            modifier =
                Modifier
                    .padding(
                        LocalPlayerAwareWindowInsets.current.union(WindowInsets.ime).asPaddingValues(),
                    ).align(Alignment.CenterEnd),
            scrollState = lazyListState,
            headerItems = if (!isSearching && playlist != null) 1 else 0,
        )

        TopAppBar(
            colors = topAppBarColors,
            title = {
                if (isSearching) {
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
                    Text(
                        text = playlist?.name ?: stringResource(R.string.spotify_playlists),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            navigationIcon = {
                IconButton(
                    onClick = {
                        if (isSearching) {
                            isSearching = false
                            query = TextFieldValue()
                        } else {
                            navController.navigateUp()
                        }
                    },
                    onLongClick = {
                        if (!isSearching) navController.backToMain()
                    },
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isSearching) R.drawable.close else R.drawable.arrow_back,
                            ),
                        contentDescription = null,
                    )
                }
            },
            actions = {
                if (!isSearching) {
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
            scrollBehavior = scrollBehavior,
        )
    }
}

private fun SpotifyTrack.isResolvedAs(mediaMetadata: MediaMetadata?): Boolean {
    if (mediaMetadata == null) return false

    mediaMetadata.spotifyTrackId?.let { spotifyTrackId ->
        return id.isNotBlank() && spotifyTrackId == id
    }

    val titleMatches = name.equals(mediaMetadata.title, ignoreCase = true)
    val durationMatches =
        durationMs <= 0 ||
            mediaMetadata.duration <= 0 ||
            abs(durationMs.toLong() - mediaMetadata.duration * 1000L) <= 1_000L
    val albumMatches =
        album?.let { spotifyAlbum ->
            val currentAlbum = mediaMetadata.album ?: return false
            spotifyAlbum.id.isNotBlank() && spotifyAlbum.id == currentAlbum.id ||
                spotifyAlbum.name.equals(currentAlbum.title, ignoreCase = true)
        } ?: true
    val artistMatches =
        artists.isEmpty() ||
            mediaMetadata.artists.isEmpty() ||
            artists.any { spotifyArtist ->
                mediaMetadata.artists.any { artist ->
                    spotifyArtist.name.equals(artist.name, ignoreCase = true)
                }
            }
    val thumbnailMatches =
        SpotifyMapper.getTrackThumbnail(this)?.let { thumbnail ->
            thumbnail == mediaMetadata.thumbnailUrl
        } ?: true

    return titleMatches && durationMatches && albumMatches && artistMatches && thumbnailMatches
}

private const val MediaDetailMetadataSeparator = "  •  "
