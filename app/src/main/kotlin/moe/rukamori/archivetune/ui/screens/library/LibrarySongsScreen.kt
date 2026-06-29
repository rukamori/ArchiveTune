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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.HideExplicitKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.constants.SongFilter
import moe.rukamori.archivetune.constants.SongFilterKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.library.rememberArtworkGradient
import moe.rukamori.archivetune.ui.utils.ItemWrapper
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibrarySongsViewModel

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibrarySongsScreen(
    navController: NavController,
    onDeselect: () -> Unit,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()
    val coroutineScope = rememberCoroutineScope()
    val isDarkTheme = isSystemInDarkTheme()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            SongSortTypeKey,
            SongSortType.CREATE_DATE,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    val hideExplicit by rememberPreference(key = HideExplicitKey, defaultValue = false)

    val songs by viewModel.allSongs.collectAsState()
    val isRefreshing by viewModel.isRefreshing.collectAsState()

    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)
    val lazyListState = rememberLazyListState()

    // Issue 2: player-aware bottom padding so content is never hidden behind nav bar + miniplayer
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 12.dp

    val wrappedSongs = remember(songs) { songs.map { item -> ItemWrapper(item) }.toMutableList() }

    val filteredSongs =
        remember(wrappedSongs, hideExplicit) {
            if (hideExplicit) {
                wrappedSongs.filter { !it.item.song.explicit }
            } else {
                wrappedSongs
            }
        }

    val totalDurationSec = remember(filteredSongs) { filteredSongs.sumOf { it.item.song.duration } }
    val totalDurationText =
        remember(totalDurationSec) {
            if (totalDurationSec <= 0) {
                ""
            } else {
                val days = totalDurationSec / 86400
                var remaining = totalDurationSec % 86400
                val hours = remaining / 3600
                remaining %= 3600
                val minutes = remaining / 60
                val seconds = remaining % 60

                when {
                    days > 0 -> "${days}d ${hours}h ${minutes}m"
                    hours > 0 -> "${hours}h ${minutes}m"
                    minutes > 0 -> "${minutes}m ${seconds}s"
                    else -> "${seconds}s"
                }
            }
        }

    ExpressivePullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = { viewModel.refresh(filter) },
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Sub-Filters Row (All Songs, Downloaded, Liked)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Liked
                SongSubFilterChip(
                    label = stringResource(R.string.filter_liked),
                    selected = filter == SongFilter.LIKED,
                    onClick = { filter = SongFilter.LIKED },
                )
                // Downloaded
                SongSubFilterChip(
                    label = stringResource(R.string.filter_downloaded),
                    selected = filter == SongFilter.DOWNLOADED,
                    onClick = { filter = SongFilter.DOWNLOADED },
                )
                // All Songs
                SongSubFilterChip(
                    label = stringResource(R.string.all_songs),
                    selected = filter == SongFilter.LIBRARY,
                    onClick = { filter = SongFilter.LIBRARY },
                )

                Spacer(modifier = Modifier.width(8.dp))

                // Dropdown sort trigger
                var showSortMenu by remember { mutableStateOf(false) }
                // Issue 4 fix: A-Z label shows ascending direction arrow
                val currentSortLabel =
                    when (sortType) {
                        SongSortType.CREATE_DATE -> {
                            if (sortDescending) {
                                stringResource(
                                    R.string.newest_first,
                                )
                            } else {
                                stringResource(R.string.oldest_first)
                            }
                        }

                        SongSortType.NAME -> {
                            if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                        }

                        SongSortType.ARTIST -> {
                            stringResource(R.string.sort_artist)
                        }

                        SongSortType.PLAY_TIME -> {
                            stringResource(R.string.most_played_sort)
                        }
                    }

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
                        SongSortType.entries.forEach { type ->
                            val label =
                                when (type) {
                                    SongSortType.CREATE_DATE -> stringResource(R.string.recently_added)

                                    // Issue 4: select NAME always sets ascending (A→Z) by default
                                    SongSortType.NAME -> stringResource(R.string.sort_a_to_z)

                                    SongSortType.ARTIST -> stringResource(R.string.sort_artist)

                                    SongSortType.PLAY_TIME -> stringResource(R.string.most_played_sort)
                                }
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onSortTypeChange(type)
                                    // A-Z sort should default to ascending
                                    if (type == SongSortType.NAME) onSortDescendingChange(false)
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

            Spacer(modifier = Modifier.height(12.dp))

            LazyColumn(
                state = lazyListState,
                // Issue 2: use player-aware window insets for bottom padding
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = playerAwareBottomPadding),
                verticalArrangement = Arrangement.spacedBy(0.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                // Spotlight Collection Card
                item(key = "collection_spotlight") {
                    Box(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(28.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors =
                                            listOf(
                                                MaterialTheme.colorScheme.secondaryContainer,
                                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f),
                                            ),
                                    ),
                                ).padding(20.dp),
                    ) {
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column {
                                    Text(
                                        text = stringResource(R.string.your_collection),
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    val songsCountText =
                                        if (filteredSongs.size ==
                                            1
                                        ) {
                                            "1 ${stringResource(R.string.song_singular)}"
                                        } else {
                                            "${filteredSongs.size} ${stringResource(R.string.songs)}"
                                        }
                                    Text(
                                        text =
                                            if (totalDurationText.isNotEmpty()) {
                                                "$songsCountText • $totalDurationText"
                                            } else {
                                                songsCountText
                                            },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                                    )
                                }

                                // Play Button inside spotlight
                                Button(
                                    onClick = {
                                        if (filteredSongs.isNotEmpty()) {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    items = filteredSongs.map { it.item.toMediaItem() },
                                                ),
                                            )
                                        }
                                    },
                                    shape = CircleShape,
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary,
                                        ),
                                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 10.dp),
                                ) {
                                    Icon(
                                        painter = painterResource(id = R.drawable.play),
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = stringResource(R.string.play),
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }

                itemsIndexed(
                    items = filteredSongs,
                    key = { _, item -> item.item.song.id },
                ) { index, songWrapper ->
                    val song = songWrapper.item
                    val isActive = song.id == mediaMetadata?.id

                    // Issue 7: active song gets fully rounded shape + artwork-based color
                    // inactive songs use theme color and are more rounded than before
                    val activeCardColor =
                        rememberArtworkCardColor(
                            thumbnailUrl = song.song.thumbnailUrl,
                            fallbackColor = MaterialTheme.colorScheme.primaryContainer,
                        )
                    val inactiveCardColor = MaterialTheme.colorScheme.surfaceContainerLow

                    // Issue 6: divider between cards visible in pure black dark theme
                    val showDivider = isDarkTheme && pureBlack && index > 0
                    if (showDivider) {
                        HorizontalDivider(
                            modifier = Modifier.padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            thickness = 0.5.dp,
                        )
                    }

                    // Issue 7: Active corners 36.dp, inactive 24.dp
                    val cornerRadius = if (isActive) 36.dp else 24.dp
                    val topPadding = if (index == 0 || showDivider) 0.dp else 8.dp

                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .padding(top = topPadding, bottom = 0.dp)
                                .clip(RoundedCornerShape(cornerRadius))
                                .background(
                                    if (isActive) activeCardColor else inactiveCardColor,
                                ).combinedClickable(
                                    onClick = {
                                        if (song.id == mediaMetadata?.id) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                ListQueue(
                                                    title = context.getString(R.string.queue_all_songs),
                                                    items = filteredSongs.map { it.item.toMediaItem() },
                                                    startIndex = index,
                                                ),
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // Thumbnail — fully circular when active
                        val thumbCorner = if (isActive) 26.dp else 10.dp
                        AsyncImage(
                            model = song.song.thumbnailUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier =
                                Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(thumbCorner))
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                        )

                        Spacer(modifier = Modifier.width(14.dp))

                        // Song Details (Issue 7: onPrimaryContainer on active dynamic background for legibility)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = song.song.title,
                                style =
                                    MaterialTheme.typography.bodyLarge.copy(
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 16.sp,
                                    ),
                                color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = song.artists.joinToString(", ") { it.name },
                                style = MaterialTheme.typography.bodySmall,
                                color =
                                    if (isActive) {
                                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f)
                                    } else {
                                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.60f)
                                    },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        // Play/Wave indicators & duration pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            if (isActive && isPlaying) {
                                Icon(
                                    painter = painterResource(id = R.drawable.graphic_eq),
                                    contentDescription = stringResource(R.string.playing_desc),
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp),
                                )
                            }

                            // Issue 1: Real duration pill using makeTimeString
                            val durationText = makeTimeString(song.song.duration * 1000L)
                            Box(
                                modifier =
                                    Modifier
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isActive) 0.5f else 0.8f))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                            ) {
                                Text(
                                    text = durationText,
                                    style =
                                        MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                        ),
                                    color =
                                        if (isActive) {
                                            MaterialTheme.colorScheme.onPrimaryContainer
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                )
                            }

                            // More options
                            IconButton(
                                onClick = {
                                    menuState.show {
                                        SongMenu(
                                            originalSong = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Icon(
                                    painter = painterResource(id = R.drawable.more_vert),
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SongSubFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor =
        if (selected) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }

    val contentColor =
        if (selected) {
            MaterialTheme.colorScheme.onSecondary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }

    Box(
        modifier =
            Modifier
                .clip(CircleShape)
                .background(bgColor)
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style =
                MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                ),
            color = contentColor,
        )
    }
}
