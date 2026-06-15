/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.firstOrNull
import moe.rukamori.archivetune.extensions.toMediaItem
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryTopMixEmptyReason
import moe.rukamori.archivetune.viewmodels.LibraryTopMixUiModel
import moe.rukamori.archivetune.viewmodels.LibraryTopMixesUiState
import moe.rukamori.archivetune.viewmodels.LibraryMixViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryMixScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    onTabSelected: (LibraryFilter) -> Unit,
    viewModel: LibraryMixViewModel = hiltViewModel(),
    spotifyLibraryViewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    val likedSongsCount by database.likedSongsCount().collectAsState(initial = 0)
    val recentSongs by database.recentSongs(15).collectAsState(initial = emptyList())

    val albums by viewModel.albums.collectAsStateWithLifecycle()
    val artists by viewModel.artists.collectAsStateWithLifecycle()
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val topMixesUiState by viewModel.topMixesUiState.collectAsStateWithLifecycle()
    val spotifyPlaylists by spotifyLibraryViewModel.playlists.collectAsStateWithLifecycle()
    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, false)
    
    val filteredPlaylistIds by database.playlistIdsByTags(
        if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
    ).collectAsState(initial = emptyList())

    val visiblePlaylists = remember(playlists, selectedTagIds, filteredPlaylistIds) {
        playlists.filter { playlist ->
            val name = playlist.playlist.name
            val matchesName = !name.contains("episode", ignoreCase = true)
            val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
            matchesName && matchesTags
        }
    }
    val visibleSpotifyPlaylists = remember(showSpotifyPlaylists, spotifyPlaylists) {
        if (showSpotifyPlaylists) {
            spotifyPlaylists
        } else {
            emptyList()
        }
    }

    LaunchedEffect(viewModel) {
        viewModel.topMixEvents.collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    val playerAwareBottomPadding = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Bottom)
        .asPaddingValues()
        .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.syncAllLibrary() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyColumn(
            state = rememberLazyListState(),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            contentPadding = PaddingValues(bottom = playerAwareBottomPadding),
            modifier = Modifier.fillMaxSize()
        ) {
            // 1. Favorite Songs Spotlight Card
            item(key = "spotlight_card") {
                val isDark = MaterialTheme.colorScheme.surface.let { 
                    ColorUtils.calculateLuminance(it.toArgb()) < 0.5 
                }
                val primaryColor = MaterialTheme.colorScheme.primary
                val surfaceContainer = MaterialTheme.colorScheme.surfaceContainer
                val spotlightBg = remember(surfaceContainer, primaryColor, isDark) {
                    if (isDark) {
                        Color(ColorUtils.blendARGB(surfaceContainer.toArgb(), primaryColor.toArgb(), 0.12f))
                    } else {
                        Color(ColorUtils.blendARGB(surfaceContainer.toArgb(), primaryColor.toArgb(), 0.08f))
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp)
                        .clip(RoundedCornerShape(32.dp))
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    spotlightBg,
                                    spotlightBg.copy(alpha = 0.9f)
                                )
                            )
                        )
                        .clickable {
                            navController.navigate("auto_playlist/liked")
                        }
                        .padding(16.dp)
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Left collage or standard cover
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(20.dp))
                                    .background(primaryColor.copy(alpha = 0.16f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.favorite),
                                    contentDescription = null,
                                    tint = primaryColor,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(16.dp))

                            // Right text content
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(primaryColor.copy(alpha = 0.16f))
                                        .padding(horizontal = 8.dp, vertical = 2.dp)
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.star),
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(10.dp)
                                    )
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = stringResource(R.string.most_played_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 9.sp,
                                            letterSpacing = 0.5.sp
                                        ),
                                        color = primaryColor
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = stringResource(R.string.favorite_songs_title),
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "$likedSongsCount ${stringResource(R.string.tracks_label)}",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Bottom actions
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        database.likedSongsByCreateDateAsc().firstOrNull()?.let { list ->
                                            if (list.isNotEmpty()) {
                                                playerConnection.playQueue(ListQueue(items = list.map { it.toMediaItem() }))
                                            }
                                        }
                                    }
                                },
                                shape = CircleShape,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                                modifier = Modifier.height(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.play),
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.play_all),
                                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }

                            IconButton(
                                onClick = {
                                    coroutineScope.launch {
                                        database.likedSongsByCreateDateAsc().firstOrNull()?.let { list ->
                                            if (list.isNotEmpty()) {
                                                playerConnection.playQueue(ListQueue(items = list.shuffled().map { it.toMediaItem() }))
                                            }
                                        }
                                    }
                                },
                                colors = IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                    contentColor = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.shuffle),
                                    contentDescription = stringResource(R.string.shuffle),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            // 2. Shortcuts 2x2 Grid
            item(key = "shortcuts_grid") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Liked Songs
                        ShortcutCard(
                            title = stringResource(R.string.liked_songs),
                            countText = "$likedSongsCount ${stringResource(R.string.tracks_label)}",
                            iconRes = R.drawable.favorite,
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                            iconColor = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate("auto_playlist/liked") }
                        )

                        // Offline/Downloaded
                        ShortcutCard(
                            title = stringResource(R.string.offline_shortcut),
                            countText = stringResource(R.string.downloaded_desc),
                            iconRes = R.drawable.offline,
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                            iconColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate("auto_playlist/downloaded") }
                        )
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Cached
                        ShortcutCard(
                            title = stringResource(R.string.cached),
                            countText = stringResource(R.string.instant_playback),
                            iconRes = R.drawable.cached,
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                            iconColor = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate("cache_playlist/cached") }
                        )

                        // Local Files
                        ShortcutCard(
                            title = stringResource(R.string.local_files),
                            countText = stringResource(R.string.on_device),
                            iconRes = R.drawable.snippet_folder,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            iconColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f),
                            onClick = { navController.navigate("local_songs") }
                        )
                    }
                }
            }

            // 3. Recently Played Horizontal Row
            if (recentSongs.isNotEmpty()) {
                item(key = "recently_played") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.recently_played),
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(recentSongs) { song ->
                                Column(
                                    modifier = Modifier
                                        .width(110.dp)
                                        .clickable {
                                            playerConnection.playQueue(ListQueue(items = listOf(song.toMediaItem())))
                                        }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .clip(RoundedCornerShape(28.dp))
                                    ) {
                                        AsyncImage(
                                            model = song.song.thumbnailUrl,
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Play Overlay button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(8.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.play),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = song.song.title,
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = song.artists.joinToString(", ") { it.name },
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            item(key = "top_mixes") {
                TopMixesForYouSection(
                    state = topMixesUiState,
                    onRefresh = viewModel::refreshTopMixes,
                    onConfigureAi = { navController.navigate("settings/ai_integration") },
                    onPlayMix = { mix ->
                        playerConnection.playQueue(
                            ListQueue(
                                items = mix.tracks.map { it.toMediaItem() },
                            ),
                        )
                    },
                )
            }

            val playlistTagFilterContent = filterContent
            if (playlistTagFilterContent != null) {
                item(key = "playlist_tag_filters") {
                    playlistTagFilterContent()
                }
            }

            // Playlists Row
            if (visiblePlaylists.isNotEmpty() || visibleSpotifyPlaylists.isNotEmpty()) {
                item(key = "your_playlists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.your_playlists),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.see_all),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onTabSelected(LibraryFilter.PLAYLISTS) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }

                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(
                                items = visiblePlaylists.take(8),
                                key = { playlist -> "playlist_${playlist.id}" },
                                contentType = { "library_playlist" },
                            ) { playlist ->
                                val cardBgColor = rememberArtworkCardColor(
                                    thumbnailUrl = playlist.thumbnails.getOrNull(0),
                                    fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow
                                )

                                val interactionSource = remember { MutableInteractionSource() }
                                val isPressed by interactionSource.collectIsPressedAsState()
                                val scale by animateFloatAsState(
                                    targetValue = if (isPressed) 0.97f else 1.0f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                    label = "MixPlaylistCardScale"
                                )

                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .graphicsLayer {
                                            scaleX = scale
                                            scaleY = scale
                                        }
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(cardBgColor)
                                        .clickable(
                                            interactionSource = interactionSource,
                                            indication = null,
                                            onClick = {
                                                if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0) {
                                                    navController.navigate("online_playlist/${playlist.playlist.browseId}")
                                                } else {
                                                    navController.navigate("local_playlist/${playlist.id}")
                                                }
                                            }
                                        )
                                        .padding(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(106.dp)
                                            .clip(RoundedCornerShape(24.dp))
                                    ) {
                                        AsyncImage(
                                            model = playlist.thumbnails.getOrNull(0),
                                            contentDescription = null,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                        // Play Overlay button
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .padding(6.dp)
                                                .size(28.dp)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.primary)
                                                .clickable {
                                                    playerConnection.let { conn ->
                                                        coroutineScope.launch {
                                                            database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                                                if (songs.isNotEmpty()) {
                                                                    conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                                                }
                                                            }
                                                        }
                                                    }
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                painter = painterResource(id = R.drawable.play),
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.onPrimary,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = playlist.playlist.name,
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                                    )
                                }
                            }

                            items(
                                items = visibleSpotifyPlaylists.take(8),
                                key = { playlist -> "spotify_playlist_${playlist.id}" },
                                contentType = { "library_spotify_playlist" },
                            ) { playlist ->
                                SpotifyPlaylistCompactCard(
                                    playlist = playlist,
                                    onClick = {
                                        navController.navigate("spotify_playlist/${playlist.id}")
                                    },
                                )
                            }

                            // Ending "More" card
                            item {
                                Column(
                                    modifier = Modifier
                                        .width(130.dp)
                                        .height(168.dp)
                                        .clip(RoundedCornerShape(32.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                                        .clickable {
                                            onTabSelected(LibraryFilter.PLAYLISTS)
                                        },
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(56.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.expand_more),
                                            contentDescription = stringResource(R.string.more_playlists_desc),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = stringResource(R.string.more_label),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 5. Your Artists Row
            if (artists.isNotEmpty()) {
                item(key = "your_artists") {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 24.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = stringResource(R.string.your_artists),
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = stringResource(R.string.see_all),
                                style = MaterialTheme.typography.labelMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable { onTabSelected(LibraryFilter.ARTISTS) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 24.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(artists.take(10)) { item ->
                                val artist = item.artist
                                Column(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .clickable {
                                            navController.navigate("artist/${artist.id}")
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    AsyncImage(
                                        model = artist.thumbnailUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = artist.name,
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }

                            // Ending "+" button
                            item {
                                Column(
                                    modifier = Modifier
                                        .width(80.dp)
                                        .clickable {
                                            onTabSelected(LibraryFilter.ARTISTS)
                                        },
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.surfaceVariant),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            painter = painterResource(id = R.drawable.add),
                                            contentDescription = stringResource(R.string.more_label),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        text = stringResource(R.string.more_label),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpotifyPlaylistCompactCard(
    playlist: SpotifyPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = remember(playlist) { SpotifyMapper.getPlaylistThumbnail(playlist) }
    val cardBgColor = rememberArtworkCardColor(
        thumbnailUrl = thumbnailUrl,
        fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
    )
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "SpotifyPlaylistCompactCardScale",
    )

    Column(
        modifier = modifier
            .width(130.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(32.dp))
            .background(cardBgColor)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick,
            )
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .size(106.dp)
                .clip(RoundedCornerShape(24.dp)),
        ) {
            AsyncImage(
                model = thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp)
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.spotify_icon),
                    contentDescription = stringResource(R.string.spotify_account),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "${playlist.tracks?.total ?: 0} ${stringResource(R.string.tracks_label)}",
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}

@Composable
private fun TopMixesForYouSection(
    state: LibraryTopMixesUiState,
    onRefresh: () -> Unit,
    onConfigureAi: () -> Unit,
    onPlayMix: (LibraryTopMixUiModel) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        LibraryTopMixesUiState.Loading -> TopMixesMessageSection(
            message = stringResource(R.string.library_top_mixes_loading),
            isRefreshing = true,
            onRefresh = onRefresh,
            showRefresh = false,
            modifier = modifier,
        )
        is LibraryTopMixesUiState.Empty -> TopMixesEmptySection(
            reason = state.reason,
            isRefreshing = state.isRefreshing,
            onRefresh = onRefresh,
            onConfigureAi = onConfigureAi,
            modifier = modifier,
        )
        is LibraryTopMixesUiState.Error -> TopMixesMessageSection(
            message = state.message,
            isRefreshing = false,
            onRefresh = onRefresh,
            showRefresh = true,
            modifier = modifier,
        )
        is LibraryTopMixesUiState.Success -> {
            if (state.mixes.isEmpty()) {
                TopMixesMessageSection(
                    message = stringResource(R.string.library_top_mixes_no_recent_history),
                    isRefreshing = state.isRefreshing,
                    onRefresh = onRefresh,
                    showRefresh = true,
                    modifier = modifier,
                )
            } else {
                Column(modifier = modifier.fillMaxWidth()) {
                    TopMixesHeader(
                        isRefreshing = state.isRefreshing,
                        onRefresh = onRefresh,
                        showRefresh = true,
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(
                            items = state.mixes,
                            key = { mix -> mix.id },
                            contentType = { "library_top_mix" },
                        ) { mix ->
                            LibraryTopMixCard(
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
private fun TopMixesMessageSection(
    message: String,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showRefresh: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopMixesHeader(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            showRefresh = showRefresh,
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 24.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TopMixesEmptySection(
    reason: LibraryTopMixEmptyReason,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onConfigureAi: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        TopMixesHeader(
            isRefreshing = isRefreshing,
            onRefresh = onRefresh,
            showRefresh = reason != LibraryTopMixEmptyReason.AI_NOT_CONFIGURED,
        )
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(
                        when (reason) {
                            LibraryTopMixEmptyReason.AI_NOT_CONFIGURED -> R.string.library_top_mixes_ai_not_configured_title
                            LibraryTopMixEmptyReason.NO_RECENT_HISTORY -> R.string.library_top_mixes_no_recent_history_title
                        },
                    ),
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(
                        when (reason) {
                            LibraryTopMixEmptyReason.AI_NOT_CONFIGURED -> R.string.library_top_mixes_ai_not_configured_desc
                            LibraryTopMixEmptyReason.NO_RECENT_HISTORY -> R.string.library_top_mixes_no_recent_history
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (reason == LibraryTopMixEmptyReason.AI_NOT_CONFIGURED) {
                    Button(onClick = onConfigureAi) {
                        Text(text = stringResource(R.string.library_top_mixes_configure_ai))
                    }
                } else {
                    FilledTonalButton(
                        onClick = onRefresh,
                        enabled = !isRefreshing,
                    ) {
                        Text(text = stringResource(R.string.library_top_mixes_refresh))
                    }
                }
            }
        }
    }
}

@Composable
private fun TopMixesHeader(
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    showRefresh: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = stringResource(R.string.top_mixes),
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (showRefresh) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        painter = painterResource(id = R.drawable.sync),
                        contentDescription = stringResource(R.string.library_top_mixes_refresh),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryTopMixCard(
    mix: LibraryTopMixUiModel,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "LibraryTopMixCardScale",
    )

    Card(
        modifier = modifier
            .width(180.dp)
            .height(130.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onPlay,
            ),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = mix.title,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = mix.description,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy((-8).dp)) {
                    mix.tracks.take(3).forEach { track ->
                        val artworkUrl = track.thumbnailUrl
                        if (artworkUrl == null) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            )
                        } else {
                            AsyncImage(
                                model = artworkUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)),
                            )
                        }
                    }
                }

                IconButton(
                    onClick = onPlay,
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(id = R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ShortcutCard(
    title: String,
    countText: String,
    iconRes: Int,
    containerColor: Color,
    iconColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.96f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "ShortcutCardScale"
    )

    val isDark = MaterialTheme.colorScheme.surface.let { 
        ColorUtils.calculateLuminance(it.toArgb()) < 0.5 
    }
    
    val surfaceContainerColor = MaterialTheme.colorScheme.surfaceContainer
    val finalBgColor = remember(surfaceContainerColor, iconColor, isDark) {
        if (isDark) {
            Color(ColorUtils.blendARGB(surfaceContainerColor.toArgb(), iconColor.toArgb(), 0.08f))
        } else {
            Color(ColorUtils.blendARGB(surfaceContainerColor.toArgb(), iconColor.toArgb(), 0.06f))
        }
    }
    
    val iconBgColor = remember(iconColor, isDark) {
        if (isDark) {
            iconColor.copy(alpha = 0.16f)
        } else {
            iconColor.copy(alpha = 0.10f)
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(26.dp))
            .background(finalBgColor)
            .clickable(
                interactionSource = interactionSource,
                onClick = onClick
            )
            .padding(12.dp)
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(iconBgColor),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(16.dp)
                )
            }
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = countText,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

