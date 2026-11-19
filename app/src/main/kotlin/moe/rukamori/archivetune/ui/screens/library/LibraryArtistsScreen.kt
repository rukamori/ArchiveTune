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
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
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
import moe.rukamori.archivetune.constants.ArtistViewTypeKey
import moe.rukamori.archivetune.constants.LibraryViewType
import moe.rukamori.archivetune.constants.YtmSyncKey
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryArtistsViewModel

private val ArtistFilterOptions = listOf(ArtistFilter.LIKED, ArtistFilter.LIBRARY)

@Composable
fun LibraryArtistsScreen(
    navController: NavController,
    viewModel: LibraryArtistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val playerConnection = LocalPlayerConnection.current ?: return
    val database = LocalDatabase.current

    val (sortType, onSortTypeChange) = rememberEnumPreference(ArtistSortTypeKey, ArtistSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(ArtistSortDescendingKey, true)
    var filter by rememberEnumPreference(ArtistFilterKey, ArtistFilter.LIKED)
    var viewType by rememberEnumPreference(ArtistViewTypeKey, LibraryViewType.GRID)
    val (ytmSync) = rememberPreference(YtmSyncKey, true)
    val isGridView = viewType == LibraryViewType.GRID

    LaunchedEffect(ytmSync, filter) {
        if (ytmSync) {
            viewModel.refresh(filter)
        }
    }

    val artists by viewModel.allArtists.collectAsStateWithLifecycle()
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

    val totalSongs = remember(artists) { artists.sumOf { it.songCount.coerceAtLeast(0) } }
    val heroArtwork =
        remember(artists) {
            artists.asSequence().mapNotNull { it.artist.thumbnailUrl }.distinct().take(2).toList()
        }
    val heroStats =
        buildList {
            add(
                LibraryHeroStat(
                    value = artists.size.toString(),
                    label = pluralStringResource(R.plurals.library_stat_artists, artists.size),
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
        }

    val playArtist: (Artist) -> Unit =
        remember(database, playerConnection, coroutineScope) {
            { artist ->
                coroutineScope.launch {
                    val songs =
                        database
                            .artistSongs(artist.id, ArtistSongSortType.CREATE_DATE, true)
                            .first()
                            .map { it.toMediaItem() }
                    if (songs.isNotEmpty()) {
                        playerConnection.playQueue(
                            ListQueue(
                                title = artist.artist.name,
                                items = songs,
                            ),
                        )
                    }
                }
            }
        }
    val showArtistMenu: (Artist, Boolean) -> Unit =
        remember(menuState, coroutineScope, haptic) {
            { artist, fromLongPress ->
                if (fromLongPress) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    ArtistMenu(
                        originalArtist = artist,
                        coroutineScope = coroutineScope,
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
            columns = if (isGridView) GridCells.Adaptive(minSize = 108.dp) else GridCells.Fixed(1),
            contentPadding =
                PaddingValues(
                    start = LibraryContentHorizontalPadding,
                    top = LibraryHeaderContentPadding + 8.dp,
                    end = LibraryContentHorizontalPadding,
                    bottom = playerAwareBottomPadding,
                ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(if (isGridView) 20.dp else 8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                key = "artists_hero",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "library_hero",
            ) {
                LibraryCollectionHero(
                    overline = stringResource(R.string.filter_library),
                    title = stringResource(if (filter == ArtistFilter.LIKED) R.string.your_artists else R.string.artists),
                    stats = heroStats,
                    artworkUrls = heroArtwork,
                    fallbackIconRes = R.drawable.person,
                    primaryLabel = stringResource(R.string.library_random_artist),
                    primaryIconRes = R.drawable.shuffle,
                    primaryEnabled = artists.isNotEmpty(),
                    onPrimary = { artists.randomOrNull()?.let(playArtist) },
                    circularArtwork = true,
                )
            }

            item(
                key = "artists_controls",
                span = { GridItemSpan(maxLineSpan) },
                contentType = "library_controls",
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                ) {
                    LibraryFilterGroup(
                        options = ArtistFilterOptions,
                        selected = filter,
                        label = { option ->
                            stringResource(
                                if (option == ArtistFilter.LIKED) R.string.subscribed_only else R.string.all_artists_filter,
                            )
                        },
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
                            ArtistSortType.entries.forEach { type ->
                                LibrarySortMenuItem(
                                    label = stringResource(type.menuLabelRes()),
                                    selected = type == sortType,
                                    onClick = {
                                        onSortTypeChange(type)
                                        if (type == ArtistSortType.NAME) onSortDescendingChange(false)
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

            if (artists.isEmpty()) {
                item(
                    key = "artists_empty",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library_empty_state",
                ) {
                    LibraryEmptyState(
                        iconRes = R.drawable.person,
                        titleRes = R.string.library_empty_artists_title,
                        descriptionRes = R.string.library_empty_artists_desc,
                        actionLabelRes = R.string.search_yt_music,
                        onAction = openSearch,
                    )
                }
            }

            items(
                items = artists,
                key = { artist -> artist.id },
                contentType = { if (isGridView) "library_artist_grid" else "library_artist_list" },
            ) { artist ->
                val itemModifier =
                    Modifier.animateItem(
                        fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                        placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                        fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                    )
                if (isGridView) {
                    ArtistGridAvatar(
                        artist = artist,
                        onClick = { navController.navigateToArtist(artist.id) },
                        onLongClick = { showArtistMenu(artist, true) },
                        modifier = itemModifier,
                    )
                } else {
                    ArtistListRow(
                        artist = artist,
                        onClick = { navController.navigateToArtist(artist.id) },
                        onPlay = { playArtist(artist) },
                        onShowMenu = { fromLongPress -> showArtistMenu(artist, fromLongPress) },
                        modifier = itemModifier,
                    )
                }
            }
        }
    }
}

@Composable
private fun ArtistGridAvatar(
    artist: Artist,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource, pressedScale = 0.92f)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(libraryGridCardShape())
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(start = LibraryGridCardTextInset, top = 6.dp, end = LibraryGridCardTextInset, bottom = LibraryGridCardTextInset),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                    }.background(MaterialTheme.colorScheme.secondaryContainer, CircleShape)
                    .padding(3.dp),
        ) {
            ItemThumbnail(
                thumbnailUrl = artist.artist.thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = CircleShape,
                contentScale = ContentScale.Crop,
                showPlaceholder = true,
                placeholderIconRes = R.drawable.person,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = artist.artist.name,
            style = MaterialTheme.typography.titleSmall,
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
private fun ArtistListRow(
    artist: Artist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShowMenu: (fromLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource, pressedScale = 0.98f)

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
        ItemThumbnail(
            thumbnailUrl = artist.artist.thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            contentScale = ContentScale.Crop,
            showPlaceholder = true,
            placeholderIconRes = R.drawable.person,
            modifier = Modifier.size(60.dp),
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = artist.artist.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalIconButton(
            onClick = onPlay,
            enabled = artist.songCount > 0,
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

private fun ArtistSortType.labelRes(descending: Boolean): Int =
    when (this) {
        ArtistSortType.CREATE_DATE -> if (descending) R.string.newest_first else R.string.oldest_first
        ArtistSortType.NAME -> if (descending) R.string.sort_z_to_a else R.string.sort_a_to_z
        ArtistSortType.SONG_COUNT -> if (descending) R.string.most_tracks else R.string.least_tracks
        ArtistSortType.PLAY_TIME -> R.string.play_time
    }

private fun ArtistSortType.menuLabelRes(): Int =
    when (this) {
        ArtistSortType.CREATE_DATE -> R.string.recently_added
        ArtistSortType.NAME -> R.string.sort_a_to_z
        ArtistSortType.SONG_COUNT -> R.string.tracks_count_label
        ArtistSortType.PLAY_TIME -> R.string.play_time
    }
