/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.SongFilter
import moe.rukamori.archivetune.constants.SongFilterKey
import moe.rukamori.archivetune.constants.SongSortDescendingKey
import moe.rukamori.archivetune.constants.SongSortType
import moe.rukamori.archivetune.constants.SongSortTypeKey
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibrarySongsViewModel

private val SongFilterOptions = listOf(SongFilter.LIKED, SongFilter.DOWNLOADED, SongFilter.LIBRARY)

@Composable
fun LibrarySongsScreen(
    navController: NavController,
    viewModel: LibrarySongsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val (sortType, onSortTypeChange) = rememberEnumPreference(SongSortTypeKey, SongSortType.CREATE_DATE)
    val (sortDescending, onSortDescendingChange) = rememberPreference(SongSortDescendingKey, true)
    var filter by rememberEnumPreference(SongFilterKey, SongFilter.LIKED)

    val songs by viewModel.allSongs.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val refreshLibrary = remember(viewModel, filter) { { viewModel.refresh(filter) } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val lazyListState = rememberLazyListState()
    val openSearch = remember(navController) { { navController.navigate(Screens.Search.route) } }

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 16.dp

    val queueTitle = stringResource(R.string.queue_all_songs)
    val mediaItems = remember(songs) { songs.map { it.toMediaItem() } }
    val totalDurationSeconds = remember(songs) { songs.sumOf { it.song.duration.coerceAtLeast(0).toLong() } }
    val totalListenedSeconds = remember(songs) { songs.sumOf { it.song.totalPlayTime } / 1_000L }
    val heroArtwork =
        remember(songs) {
            songs.asSequence().mapNotNull { it.song.thumbnailUrl }.distinct().take(2).toList()
        }
    val durationText = libraryDurationText(totalDurationSeconds)
    val listenedText = libraryDurationText(totalListenedSeconds)
    val heroStats =
        buildList {
            add(
                LibraryHeroStat(
                    value = songs.size.toString(),
                    label = pluralStringResource(R.plurals.library_stat_songs, songs.size),
                ),
            )
            durationText?.let { add(LibraryHeroStat(value = it, label = stringResource(R.string.library_stat_total_length))) }
            listenedText?.let { add(LibraryHeroStat(value = it, label = stringResource(R.string.library_stat_listened))) }
        }

    val playAll: (Boolean) -> Unit =
        remember(mediaItems, playerConnection, queueTitle) {
            { shuffled ->
                if (mediaItems.isNotEmpty()) {
                    playerConnection.playQueue(
                        ListQueue(
                            title = queueTitle,
                            items = if (shuffled) mediaItems.shuffled() else mediaItems,
                        ),
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
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    start = LibraryContentHorizontalPadding,
                    top = LibraryHeaderContentPadding + 8.dp,
                    end = LibraryContentHorizontalPadding,
                    bottom = playerAwareBottomPadding,
                ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "songs_hero", contentType = "library_hero") {
                LibraryCollectionHero(
                    overline = stringResource(R.string.songs),
                    title = stringResource(filter.titleRes()),
                    stats = heroStats,
                    artworkUrls = heroArtwork,
                    fallbackIconRes = R.drawable.music_note,
                    primaryLabel = stringResource(R.string.play),
                    primaryEnabled = mediaItems.isNotEmpty(),
                    onPrimary = { playAll(false) },
                    onShuffle = { playAll(true) },
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }

            item(key = "songs_controls", contentType = "library_controls") {
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 12.dp),
                ) {
                    LibraryFilterGroup(
                        options = SongFilterOptions,
                        selected = filter,
                        label = { option -> stringResource(option.chipLabelRes()) },
                        onSelect = { option -> filter = option },
                    )
                    LibrarySortButton(
                        label = stringResource(sortType.labelRes(sortDescending)),
                        descending = sortDescending,
                        directionEnabled = true,
                        onToggleDirection = { onSortDescendingChange(!sortDescending) },
                    ) { dismiss ->
                        SongSortType.entries.forEach { type ->
                            LibrarySortMenuItem(
                                label = stringResource(type.menuLabelRes()),
                                selected = type == sortType,
                                onClick = {
                                    onSortTypeChange(type)
                                    if (type == SongSortType.NAME) onSortDescendingChange(false)
                                    dismiss()
                                },
                            )
                        }
                    }
                }
            }

            if (songs.isEmpty()) {
                item(key = "songs_empty", contentType = "library_empty_state") {
                    LibraryEmptyState(
                        iconRes = R.drawable.music_note,
                        titleRes = R.string.library_empty_songs_title,
                        descriptionRes = R.string.library_empty_songs_desc,
                        actionLabelRes = R.string.search_yt_music,
                        onAction = openSearch,
                    )
                }
            }

            itemsIndexed(
                items = songs,
                key = { _, song -> song.song.id },
                contentType = { _, _ -> "library_song" },
            ) { index, song ->
                val isActive = song.id == mediaMetadata?.id
                LibrarySongRow(
                    song = song,
                    isActive = isActive,
                    isPlaying = isActive && isPlaying,
                    showLikedBadge = filter != SongFilter.LIKED,
                    onClick = {
                        if (isActive) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                ListQueue(
                                    title = context.getString(R.string.queue_all_songs),
                                    items = mediaItems,
                                    startIndex = index,
                                ),
                            )
                        }
                    },
                    onShowMenu = { fromLongPress ->
                        if (fromLongPress) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            SongMenu(
                                originalSong = song,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                    modifier =
                        Modifier.animateItem(
                            fadeInSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
                            placementSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
                            fadeOutSpec = MaterialTheme.motionScheme.fastEffectsSpec(),
                        ),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LibrarySongRow(
    song: Song,
    isActive: Boolean,
    isPlaying: Boolean,
    showLikedBadge: Boolean,
    onClick: () -> Unit,
    onShowMenu: (fromLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val motion = MaterialTheme.motionScheme
    val containerColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "LibrarySongRowContainer",
    )
    val contentColor by animateColorAsState(
        targetValue =
            if (isActive) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
        animationSpec = motion.defaultEffectsSpec(),
        label = "LibrarySongRowContent",
    )
    val corner by animateDpAsState(
        targetValue = if (isActive) 28.dp else 16.dp,
        animationSpec = motion.defaultSpatialSpec(),
        label = "LibrarySongRowCorner",
    )
    val supportingText =
        remember(song) {
            val artists = song.artists.joinToString(", ") { it.name }
            val duration = song.song.duration.takeIf { it > 0 }?.let { makeTimeString(it * 1000L) }
            listOfNotNull(artists.ifBlank { null }, duration).joinToString(" • ")
        }

    Row(
        modifier =
            modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(corner))
                .background(containerColor)
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { onShowMenu(true) },
                ).padding(start = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LibraryNowPlayingArtwork(
            thumbnailUrl = song.song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            modifier = Modifier.size(56.dp),
        )

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.song.title,
                style =
                    if (isActive) {
                        MaterialTheme.typography.titleMediumEmphasized
                    } else {
                        MaterialTheme.typography.titleMedium
                    },
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (song.song.explicit) {
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = stringResource(R.string.library_explicit),
                        tint = contentColor.copy(alpha = 0.72f),
                        modifier = Modifier.size(16.dp),
                    )
                }
                if (showLikedBadge && song.song.liked) {
                    Icon(
                        painter = painterResource(R.drawable.favorite),
                        contentDescription = stringResource(R.string.filter_liked),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp),
                    )
                }
                Text(
                    text = supportingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = contentColor.copy(alpha = 0.72f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        IconButton(onClick = { onShowMenu(false) }) {
            Icon(
                painter = painterResource(R.drawable.more_vert),
                contentDescription = stringResource(R.string.options_label),
                tint = contentColor,
            )
        }
    }
}

private fun SongFilter.titleRes(): Int =
    when (this) {
        SongFilter.LIKED -> R.string.liked_songs
        SongFilter.DOWNLOADED -> R.string.downloaded_songs
        SongFilter.LIBRARY -> R.string.all_songs
    }

private fun SongFilter.chipLabelRes(): Int =
    when (this) {
        SongFilter.LIKED -> R.string.filter_liked
        SongFilter.DOWNLOADED -> R.string.filter_downloaded
        SongFilter.LIBRARY -> R.string.filter_all
    }

private fun SongSortType.labelRes(descending: Boolean): Int =
    when (this) {
        SongSortType.CREATE_DATE -> if (descending) R.string.newest_first else R.string.oldest_first
        SongSortType.NAME -> if (descending) R.string.sort_z_to_a else R.string.sort_a_to_z
        SongSortType.ARTIST -> R.string.sort_artist
        SongSortType.PLAY_TIME -> R.string.most_played_sort
    }

private fun SongSortType.menuLabelRes(): Int =
    when (this) {
        SongSortType.CREATE_DATE -> R.string.recently_added
        SongSortType.NAME -> R.string.sort_a_to_z
        SongSortType.ARTIST -> R.string.sort_artist
        SongSortType.PLAY_TIME -> R.string.most_played_sort
    }
