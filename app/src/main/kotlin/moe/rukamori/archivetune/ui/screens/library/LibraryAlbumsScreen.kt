/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AlbumFilter
import moe.rukamori.archivetune.constants.AlbumFilterKey
import moe.rukamori.archivetune.constants.AlbumSortDescendingKey
import moe.rukamori.archivetune.constants.AlbumSortType
import moe.rukamori.archivetune.constants.AlbumSortTypeKey
import moe.rukamori.archivetune.constants.AlbumViewTypeKey
import moe.rukamori.archivetune.constants.LibraryViewType
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryAlbumsViewModel

@Composable
fun LibraryAlbumsScreen(
    navController: NavController,
    viewModel: LibraryAlbumsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current

    var filter by rememberEnumPreference(AlbumFilterKey, AlbumFilter.LIKED)
    val (sortType, onSortTypeChange) = rememberEnumPreference(AlbumSortTypeKey, AlbumSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(AlbumSortDescendingKey, true)
    var viewType by rememberEnumPreference(AlbumViewTypeKey, LibraryViewType.GRID)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val isGridView = viewType == LibraryViewType.GRID

    LaunchedEffect(ytmSync, filter) {
        if (ytmSync) {
            viewModel.refresh(filter)
        }
    }

    val albums by viewModel.allAlbums.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val refreshLibrary = remember(viewModel, filter) { { viewModel.refresh(filter) } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val openSearch = remember(navController) { { navController.navigate(Screens.Search.route) } }
    val gridState = rememberLazyGridState()

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 16.dp

    val filterOptions =
        remember(filter) {
            listOf(
                AlbumFilter.LIKED,
                AlbumFilter.LIBRARY,
                if (filter == AlbumFilter.DOWNLOADED) AlbumFilter.DOWNLOADED else AlbumFilter.DOWNLOADED_FULL,
            )
        }
    val totalSongs = remember(albums) { albums.sumOf { it.album.songCount.coerceAtLeast(0) } }
    val totalSeconds = remember(albums) { albums.sumOf { it.album.duration.coerceAtLeast(0).toLong() } }
    val heroArtwork =
        remember(albums) {
            albums.asSequence().mapNotNull { it.album.thumbnailUrl }.distinct().take(2).toList()
        }
    val durationText = libraryDurationText(totalSeconds)
    val heroStats =
        buildList {
            add(
                LibraryHeroStat(
                    value = albums.size.toString(),
                    label = pluralStringResource(R.plurals.library_stat_albums, albums.size),
                ),
            )
            if (totalSongs > 0) {
                add(
                    LibraryHeroStat(
                        value = totalSongs.toString(),
                        label = pluralStringResource(R.plurals.library_stat_songs, totalSongs),
                    ),
                )
            }
            durationText?.let { add(LibraryHeroStat(value = it, label = stringResource(R.string.library_stat_total_length))) }
        }

    val playAlbum: (Album) -> Unit =
        remember(database, playerConnection, coroutineScope) {
            { album ->
                coroutineScope.launch {
                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                        if (albumWithSongs.songs.isNotEmpty()) {
                            playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                        }
                    }
                }
            }
        }
    val showAlbumMenu: (Album, Boolean) -> Unit =
        remember(menuState, navController, haptic) {
            { album, fromLongPress ->
                if (fromLongPress) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    AlbumMenu(
                        originalAlbum = album,
                        navController = navController,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }

    LibraryRefreshContainer(
        state = refreshState,
        onRefresh = refreshLibrary,
        onErrorShown = onRefreshErrorShown,
        modifier = Modifier.fillMaxSize(),
        indicatorOffset = LibraryPullToRefreshIndicatorOffset,
    ) {
        LazyVerticalGrid(
            state = gridState,
            columns = if (isGridView) GridCells.Adaptive(minSize = 150.dp) else GridCells.Fixed(1),
            contentPadding =
                PaddingValues(
                    start = LibraryContentHorizontalPadding,
                    top = LibraryHeaderContentPadding + 8.dp,
                    end = LibraryContentHorizontalPadding,
                    bottom = playerAwareBottomPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isGridView) 16.dp else 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                key = "albums_hero",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "library_hero",
            ) {
                LibraryCollectionHero(
                    overline = stringResource(R.string.filter_library),
                    title = stringResource(filter.titleRes()),
                    stats = heroStats,
                    artworkUrls = heroArtwork,
                    fallbackIconRes = R.drawable.album,
                    primaryLabel = stringResource(R.string.library_random_album),
                    primaryIconRes = R.drawable.shuffle,
                    primaryEnabled = albums.isNotEmpty(),
                    onPrimary = { albums.randomOrNull()?.let(playAlbum) },
                )
            }

            item(
                key = "albums_controls",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "library_controls",
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                ) {
                    LibraryFilterGroup(
                        options = filterOptions,
                        selected = filter,
                        label = { option -> stringResource(option.chipLabelRes()) },
                        onSelect = { option -> filter = option },
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        LibrarySortButton(
                            label = stringResource(sortType.labelRes(sortDescending)),
                            descending = sortDescending,
                            directionEnabled = true,
                            onToggleDirection = { onSortDescendingChange(!sortDescending) },
                            modifier = Modifier.weight(1f, fill = false),
                        ) { dismiss ->
                            AlbumSortType.entries.forEach { type ->
                                LibrarySortMenuItem(
                                    label = stringResource(type.menuLabelRes()),
                                    selected = type == sortType,
                                    onClick = {
                                        onSortTypeChange(type)
                                        if (type == AlbumSortType.NAME) onSortDescendingChange(false)
                                        dismiss()
                                    },
                                )
                            }
                        }
                        Spacer(modifier = Modifier.weight(1f))
                        LibraryViewModeToggle(
                            isGrid = isGridView,
                            onGridChange = { grid -> viewType = if (grid) LibraryViewType.GRID else LibraryViewType.LIST },
                        )
                    }
                }
            }

            if (albums.isEmpty()) {
                item(
                    key = "albums_empty",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library_empty_state",
                ) {
                    LibraryEmptyState(
                        iconRes = R.drawable.album,
                        titleRes = R.string.library_empty_albums_title,
                        descriptionRes = R.string.library_empty_albums_desc,
                        actionLabelRes = R.string.search_yt_music,
                        onAction = openSearch,
                    )
                }
            }

            items(
                items = albums,
                key = { album -> album.id },
                contentType = { if (isGridView) "library_album_grid" else "library_album_list" },
            ) { album ->
                val itemModifier =
                    Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    )
                if (isGridView) {
                    AlbumGridCard(
                        album = album,
                        onClick = { navController.navigateToAlbum(album.id) },
                        onPlay = { playAlbum(album) },
                        onLongClick = { showAlbumMenu(album, true) },
                        modifier = itemModifier,
                    )
                } else {
                    AlbumListRow(
                        album = album,
                        onClick = { navController.navigateToAlbum(album.id) },
                        onPlay = { playAlbum(album) },
                        onShowMenu = { fromLongPress -> showAlbumMenu(album, fromLongPress) },
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun AlbumGridCard(
    album: Album,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    val supportingText = album.gridSupportingText()

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(libraryGridCardShape())
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(bottom = LibraryGridCardTextInset),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            LibraryArtworkMosaic(
                artworkUrls = listOfNotNull(album.album.thumbnailUrl),
                fallbackIconRes = R.drawable.album,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize(),
            )
            if (album.album.explicit) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = stringResource(R.string.library_explicit),
                        modifier =
                            Modifier
                                .padding(5.dp)
                                .size(16.dp),
                    )
                }
            }
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
            text = album.album.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
        if (supportingText.isNotEmpty()) {
            Text(
                text = supportingText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
            )
        }
    }
}

@Composable
private fun AlbumListRow(
    album: Album,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShowMenu: (fromLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource, pressedScale = 0.98f)
    val artistText = remember(album.artists) { album.artists.joinToString(", ") { it.name } }
    val songCount = album.album.songCount
    val metadataText =
        listOfNotNull(
            album.album.year?.toString(),
            songCount.takeIf { it > 0 }?.let { pluralStringResource(R.plurals.n_song, it, it) },
            album.album.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) },
        ).joinToString(" • ")

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(MaterialTheme.shapes.largeIncreased)
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = { onShowMenu(true) },
                ).padding(start = 10.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryArtworkMosaic(
            artworkUrls = listOfNotNull(album.album.thumbnailUrl),
            fallbackIconRes = R.drawable.album,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.size(64.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = album.album.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (album.album.explicit) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = stringResource(R.string.library_explicit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }
            if (artistText.isNotBlank()) {
                Text(
                    text = artistText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (metadataText.isNotEmpty()) {
                Text(
                    text = metadataText,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalIconButton(
            onClick = onPlay,
            shapes = IconButtonDefaults.shapes(),
            modifier = Modifier.size(48.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(22.dp),
            )
        }
        IconButton(onClick = { onShowMenu(false) }) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.options_label),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun Album.gridSupportingText(): String {
    val artistNames = artists.joinToString(", ") { it.name }
    return listOfNotNull(artistNames.ifBlank { null }, album.year?.toString()).joinToString(" • ")
}

private fun AlbumFilter.titleRes(): Int =
    when (this) {
        AlbumFilter.LIKED -> R.string.library_your_albums
        AlbumFilter.LIBRARY -> R.string.albums
        AlbumFilter.DOWNLOADED,
        AlbumFilter.DOWNLOADED_FULL,
        -> R.string.filter_downloaded
    }

private fun AlbumFilter.chipLabelRes(): Int =
    when (this) {
        AlbumFilter.LIKED -> R.string.filter_liked
        AlbumFilter.LIBRARY -> R.string.filter_all
        AlbumFilter.DOWNLOADED,
        AlbumFilter.DOWNLOADED_FULL,
        -> R.string.filter_downloaded
    }

private fun AlbumSortType.labelRes(descending: Boolean): Int =
    when (this) {
        AlbumSortType.CREATE_DATE -> if (descending) R.string.newest_first else R.string.oldest_first
        AlbumSortType.NAME -> if (descending) R.string.sort_z_to_a else R.string.sort_a_to_z
        AlbumSortType.ARTIST -> R.string.sort_artist
        AlbumSortType.YEAR -> if (descending) R.string.newest_year else R.string.oldest_year
        AlbumSortType.SONG_COUNT -> if (descending) R.string.most_tracks else R.string.least_tracks
        AlbumSortType.LENGTH -> if (descending) R.string.longest_duration else R.string.shortest_duration
        AlbumSortType.PLAY_TIME -> R.string.most_played_sort
    }

private fun AlbumSortType.menuLabelRes(): Int =
    when (this) {
        AlbumSortType.CREATE_DATE -> R.string.recently_added
        AlbumSortType.NAME -> R.string.sort_a_to_z
        AlbumSortType.ARTIST -> R.string.sort_artist
        AlbumSortType.YEAR -> R.string.year_sort
        AlbumSortType.SONG_COUNT -> R.string.tracks_count_label
        AlbumSortType.LENGTH -> R.string.duration_sort
        AlbumSortType.PLAY_TIME -> R.string.most_played_sort
    }
