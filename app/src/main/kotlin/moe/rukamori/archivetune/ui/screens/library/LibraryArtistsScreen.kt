/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ArtistFilter
import moe.rukamori.archivetune.constants.ArtistFilterKey
import moe.rukamori.archivetune.constants.ArtistSongSortType
import moe.rukamori.archivetune.constants.ArtistSortDescendingKey
import moe.rukamori.archivetune.constants.ArtistSortType
import moe.rukamori.archivetune.constants.ArtistSortTypeKey
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryArtistsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current
    val database = LocalDatabase.current

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            ArtistSortTypeKey,
            ArtistSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)

    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)

    LaunchedEffect(Unit) {
        if (ytmSync) {
            withContext(Dispatchers.IO) {
                viewModel.sync()
            }
        }
    }

    val artists by viewModel.allArtists.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    val topArtist = artists.firstOrNull()

    // Issue 2: player-aware bottom padding
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.sync() },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = playerAwareBottomPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            // Featured Spotlight Row
            item(span = { GridItemSpan(2) }, key = "spotlight_row") {
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(IntrinsicSize.Max)
                            .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // Left Card: Top Artist
                    Box(
                        modifier =
                            Modifier
                                .weight(1.3f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(36.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .clickable {
                                    topArtist?.let { navController.navigate("artist/${it.artist.id}") }
                                }.padding(16.dp),
                    ) {
                        Column(
                            modifier = Modifier.fillMaxHeight(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier =
                                        Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(MaterialTheme.colorScheme.primary),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.person),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimary,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.top_artist_badge),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                    Text(
                                        text = topArtist?.artist?.name ?: stringResource(R.string.no_artist_yet),
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.weight(1f))
                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Button(
                                    onClick = {
                                        topArtist?.let { artist ->
                                            coroutineScope.launch {
                                                val songs =
                                                    database
                                                        .artistSongs(
                                                            artist.id,
                                                            ArtistSongSortType.CREATE_DATE,
                                                            true,
                                                        ).first()
                                                        .map { it.toMediaItem() }
                                                if (songs.isNotEmpty()) {
                                                    playerConnection?.playQueue(
                                                        ListQueue(
                                                            title = artist.artist.name,
                                                            items = songs,
                                                        ),
                                                    )
                                                }
                                            }
                                        }
                                    },
                                    shape = CircleShape,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                    modifier = Modifier.height(32.dp),
                                ) {
                                    Text(
                                        text = stringResource(R.string.play_all),
                                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        topArtist?.let { artist ->
                                            menuState.show {
                                                ArtistMenu(
                                                    originalArtist = artist,
                                                    coroutineScope = coroutineScope,
                                                    onDismiss = menuState::dismiss,
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.more_vert),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Right Card: Total Count
                    Box(
                        modifier =
                            Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(36.dp))
                                .background(MaterialTheme.colorScheme.secondaryContainer)
                                .clickable {
                                    filter = if (filter == ArtistFilter.LIKED) ArtistFilter.LIBRARY else ArtistFilter.LIKED
                                }.padding(16.dp),
                    ) {
                        Column(
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .fillMaxHeight(),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = stringResource(R.string.artists),
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                )
                                Icon(
                                    painter = painterResource(id = R.drawable.arrow_forward),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            Spacer(modifier = Modifier.weight(1f))

                            Column {
                                Text(
                                    text = "${artists.size}",
                                    style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Black),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Text(
                                    text = stringResource(R.string.total_label),
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                                )
                            }
                        }
                    }
                }
            }

            // Sub-Header Controls (Sort dropdown, genres, view list/grid, etc)
            item(span = { GridItemSpan(2) }, key = "sub_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    var showSortMenu by remember { mutableStateOf(false) }
                    val currentSortLabel =
                        when (sortType) {
                            ArtistSortType.CREATE_DATE -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.newest_first,
                                    )
                                } else {
                                    stringResource(R.string.oldest_first)
                                }
                            }

                            ArtistSortType.NAME -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.sort_z_to_a,
                                    )
                                } else {
                                    stringResource(R.string.sort_a_to_z)
                                }
                            }

                            ArtistSortType.SONG_COUNT -> {
                                if (sortDescending) {
                                    stringResource(
                                        R.string.most_tracks,
                                    )
                                } else {
                                    stringResource(R.string.least_tracks)
                                }
                            }

                            ArtistSortType.PLAY_TIME -> {
                                stringResource(R.string.play_time)
                            }
                        }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box {
                            Row(
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                        .clickable { showSortMenu = true }
                                        .padding(horizontal = 14.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = currentSortLabel,
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    painter = painterResource(id = R.drawable.expand_more),
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false },
                            ) {
                                ArtistSortType.entries.forEach { type ->
                                    val label =
                                        when (type) {
                                            ArtistSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                            ArtistSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                            ArtistSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                            ArtistSortType.PLAY_TIME -> stringResource(R.string.play_time)
                                        }
                                    DropdownMenuItem(
                                        text = { Text(label) },
                                        onClick = {
                                            onSortTypeChange(type)
                                            if (type == ArtistSortType.NAME) onSortDescendingChange(false)
                                            showSortMenu = false
                                        },
                                    )
                                }
                            }
                        }

                        // Sort direction toggle button
                        Spacer(modifier = Modifier.width(4.dp))
                        Box(
                            modifier =
                                Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable { onSortDescendingChange(!sortDescending) }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                        ) {
                            Icon(
                                painter =
                                    painterResource(
                                        id = if (sortDescending) R.drawable.arrow_downward else R.drawable.arrow_upward,
                                    ),
                                contentDescription =
                                    if (sortDescending) {
                                        stringResource(
                                            R.string.sort_descending,
                                        )
                                    } else {
                                        stringResource(R.string.sort_ascending)
                                    },
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    // Translucent Chips Row (translucent filter indicator)
                    Row(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text =
                                if (filter ==
                                    ArtistFilter.LIKED
                                ) {
                                    stringResource(R.string.subscribed_only)
                                } else {
                                    stringResource(R.string.all_artists_filter)
                                },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            // Artists Grid: 2-column capsule artist cards
            items(artists, key = { it.id }) { artistWrapper ->
                val artist = artistWrapper.artist
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(28.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                            .combinedClickable(
                                onClick = { navController.navigate("artist/${artist.id}") },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        ArtistMenu(
                                            originalArtist = artistWrapper,
                                            coroutineScope = coroutineScope,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ).padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar image circle
                    AsyncImage(
                        model = artist.thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(52.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = artist.name,
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = stringResource(R.string.artist_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                        )
                    }

                    // Play & More button
                    IconButton(
                        onClick = {
                            coroutineScope.launch {
                                val songs =
                                    database
                                        .artistSongs(
                                            artistWrapper.id,
                                            ArtistSongSortType.CREATE_DATE,
                                            true,
                                        ).first()
                                        .map { it.toMediaItem() }
                                if (songs.isNotEmpty()) {
                                    playerConnection?.playQueue(
                                        ListQueue(
                                            title = artistWrapper.artist.name,
                                            items = songs,
                                        ),
                                    )
                                }
                            }
                        },
                        modifier = Modifier.size(28.dp),
                        colors =
                            IconButtonDefaults.iconButtonColors(
                                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                    ) {
                        Icon(
                            painter = painterResource(id = R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
            }

            // Recently Played Artists Section
            if (artists.size > 2) {
                item(span = { GridItemSpan(2) }, key = "recent_artists_header") {
                    Text(
                        text = stringResource(R.string.recently_played_artists),
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                }

                item(span = { GridItemSpan(2) }, key = "recent_artists_row") {
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        items(artists.take(5)) { artistWrapper ->
                            val artist = artistWrapper.artist
                            Column(
                                modifier =
                                    Modifier
                                        .width(72.dp)
                                        .clickable {
                                            navController.navigate("artist/${artist.id}")
                                        },
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                AsyncImage(
                                    model = artist.thumbnailUrl,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier =
                                        Modifier
                                            .size(60.dp)
                                            .clip(CircleShape),
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = artist.name,
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onBackground,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
