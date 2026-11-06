/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PlaylistEditLockKey
import moe.rukamori.archivetune.constants.PlaylistSortDescendingKey
import moe.rukamori.archivetune.constants.PlaylistSortType
import moe.rukamori.archivetune.constants.PlaylistSortTypeKey
import moe.rukamori.archivetune.constants.PureBlackKey
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.extensions.move
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.CreatePlaylistDialog
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryPlaylistsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val coroutineScope = rememberCoroutineScope()
    val database = LocalDatabase.current
    val playerConnection = LocalPlayerConnection.current
    val haptic = LocalHapticFeedback.current

    val (sortType, onSortTypeChange) =
        rememberEnumPreference(
            PlaylistSortTypeKey,
            PlaylistSortType.CUSTOM,
        )
    val (sortDescending, onSortDescendingChange) = rememberPreference(PlaylistSortDescendingKey, true)
    var locked by rememberPreference(PlaylistEditLockKey, defaultValue = true)
    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val downloadedPlaylistIds by viewModel.downloadedPlaylistIds.collectAsStateWithLifecycle()
    val filteredPlaylistIds by database
        .playlistIdsByTags(
            if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
        ).collectAsStateWithLifecycle(initialValue = emptyList())

    var showHidden by rememberSaveable { mutableStateOf(false) }
    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }

    val visiblePlaylists =
        remember(playlists, selectedTagIds, filteredPlaylistIds, showHidden) {
            playlists.filter { playlist ->
                val matchesName = !playlist.playlist.name.contains("episode", ignoreCase = true)
                val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
                val matchesVisibility = showHidden || !playlist.playlist.isHidden
                matchesName && matchesTags && matchesVisibility
            }
        }
    val mutablePlaylists = remember { mutableStateListOf<Playlist>() }
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val refreshLibrary = remember(viewModel) { { viewModel.sync() } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val lazyListState = rememberLazyListState()
    var pendingPlaylistOrderUpdate by remember { mutableStateOf(false) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            val fromIndex = mutablePlaylists.indexOfFirst { playlist -> playlist.id == from.key }
            val toIndex = mutablePlaylists.indexOfFirst { playlist -> playlist.id == to.key }
            if (fromIndex >= 0 && toIndex >= 0) {
                mutablePlaylists.move(fromIndex, toIndex)
                pendingPlaylistOrderUpdate = true
            }
        }

    LaunchedEffect(visiblePlaylists) {
        mutablePlaylists.clear()
        mutablePlaylists.addAll(visiblePlaylists)
    }

    LaunchedEffect(reorderableState.isAnyItemDragging) {
        if (!reorderableState.isAnyItemDragging && pendingPlaylistOrderUpdate) {
            viewModel.updateCustomPlaylistOrder(
                mergeVisiblePlaylistOrder(
                    currentOrder = playlists,
                    visibleOrder = mutablePlaylists,
                ),
            )
            pendingPlaylistOrderUpdate = false
        }
    }

    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding() + 16.dp
    val contentPadding =
        PaddingValues(
            start = LibraryContentHorizontalPadding,
            top = LibraryHeaderContentPadding + 8.dp,
            end = LibraryContentHorizontalPadding,
            bottom = playerAwareBottomPadding,
        )

    val createPlaylist = remember { { showCreatePlaylistDialog = true } }
    val playPlaylist: (Playlist) -> Unit =
        remember(playerConnection, database, coroutineScope) {
            { playlist ->
                playerConnection?.let { connection ->
                    coroutineScope.launch {
                        val songs = database.playlistSongs(playlist.id).firstOrNull().orEmpty()
                        if (songs.isNotEmpty()) {
                            connection.playQueue(
                                ListQueue(
                                    title = playlist.playlist.name,
                                    items = songs.map { it.song.toMediaItem() },
                                ),
                            )
                        }
                    }
                }
            }
        }
    val showPlaylistMenu: (Playlist) -> Unit =
        remember(menuState, coroutineScope) {
            { playlist ->
                menuState.show {
                    PlaylistLibraryMenu(playlist, coroutineScope, menuState)
                }
            }
        }

    val totalSongs = remember(visiblePlaylists) { visiblePlaylists.sumOf { it.displaySongCount() } }
    val downloadedCount =
        remember(visiblePlaylists, downloadedPlaylistIds) {
            visiblePlaylists.count { it.id in downloadedPlaylistIds }
        }
    val heroArtwork =
        remember(visiblePlaylists) {
            visiblePlaylists.asSequence().mapNotNull { it.thumbnails.firstOrNull() }.take(2).toList()
        }
    val heroStats =
        buildList {
            add(
                LibraryHeroStat(
                    value = visiblePlaylists.size.toString(),
                    label = pluralStringResource(R.plurals.library_stat_playlists, visiblePlaylists.size),
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
            if (downloadedCount > 0) {
                add(LibraryHeroStat(value = downloadedCount.toString(), label = stringResource(R.string.filter_downloaded)))
            }
        }

    val header: @Composable () -> Unit = {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            LibraryCollectionHero(
                overline = stringResource(R.string.filter_library),
                title = stringResource(R.string.your_playlists),
                stats = heroStats,
                artworkUrls = heroArtwork,
                fallbackIconRes = R.drawable.queue_music,
                primaryLabel = stringResource(R.string.create_playlist),
                primaryIconRes = R.drawable.add,
                onPrimary = createPlaylist,
            )
            PlaylistControls(
                sortType = sortType,
                sortDescending = sortDescending,
                onSortTypeChange = { type ->
                    onSortTypeChange(type)
                    if (type == PlaylistSortType.NAME) onSortDescendingChange(false)
                },
                onSortDescendingChange = onSortDescendingChange,
                showHidden = showHidden,
                onShowHiddenChange = { showHidden = it },
                isGridView = isGridView,
                onGridViewChange = { isGridView = it },
                reorderEnabled = !locked,
                onReorderEnabledChange = { enabled -> locked = !enabled },
            )
            filterContent?.let { content ->
                Box(modifier = Modifier.libraryHorizontalBleed(LibraryContentHorizontalPadding)) {
                    content()
                }
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
        if (isGridView) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding = contentPadding,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(
                    key = "playlists_header",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library_header",
                ) {
                    header()
                }

                if (visiblePlaylists.isEmpty()) {
                    item(
                        key = "playlists_empty",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "library_empty_state",
                    ) {
                        LibraryEmptyState(
                            iconRes = R.drawable.playlist_add,
                            titleRes = R.string.library_empty_playlists_title,
                            descriptionRes = R.string.library_empty_playlists_desc,
                        )
                    }
                }

                items(
                    items = visiblePlaylists,
                    key = { playlist -> playlist.id },
                    contentType = { "playlist_grid" },
                ) { playlist ->
                    PlaylistGridCard(
                        playlist = playlist,
                        isDownloaded = playlist.id in downloadedPlaylistIds,
                        onClick = { openPlaylist(navController, playlist) },
                        onPlay = { playPlaylist(playlist) },
                        onLongClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            showPlaylistMenu(playlist)
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
        } else {
            val listPlaylists = if (sortType == PlaylistSortType.CUSTOM) mutablePlaylists else visiblePlaylists
            val showDragHandles = sortType == PlaylistSortType.CUSTOM && !locked
            LazyColumn(
                state = lazyListState,
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(key = "playlists_header", contentType = "library_header") {
                    Box(modifier = Modifier.padding(bottom = 8.dp)) {
                        header()
                    }
                }

                if (listPlaylists.isEmpty()) {
                    item(key = "playlists_empty", contentType = "library_empty_state") {
                        LibraryEmptyState(
                            iconRes = R.drawable.playlist_add,
                            titleRes = R.string.library_empty_playlists_title,
                            descriptionRes = R.string.library_empty_playlists_desc,
                        )
                    }
                }

                items(
                    items = listPlaylists,
                    key = { playlist -> playlist.id },
                    contentType = { "playlist_list" },
                ) { playlist ->
                    ReorderableItem(
                        state = reorderableState,
                        key = playlist.id,
                        modifier = Modifier.graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen },
                    ) { isDragging ->
                        val dragScale by animateFloatAsState(
                            targetValue = if (isDragging) 1.03f else 1f,
                            animationSpec = MaterialTheme.motionScheme.fastSpatialSpec(),
                            label = "PlaylistDragScale",
                        )
                        PlaylistListCard(
                            playlist = playlist,
                            isDownloaded = playlist.id in downloadedPlaylistIds,
                            onClick = { openPlaylist(navController, playlist) },
                            onPlay = { playPlaylist(playlist) },
                            onMenuClick = { showPlaylistMenu(playlist) },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                showPlaylistMenu(playlist)
                            },
                            showDragHandle = showDragHandles,
                            dragHandleModifier = Modifier.draggableHandle(),
                            modifier =
                                Modifier.graphicsLayer {
                                    scaleX = dragScale
                                    scaleY = dragScale
                                },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistControls(
    sortType: PlaylistSortType,
    sortDescending: Boolean,
    onSortTypeChange: (PlaylistSortType) -> Unit,
    onSortDescendingChange: (Boolean) -> Unit,
    showHidden: Boolean,
    onShowHiddenChange: (Boolean) -> Unit,
    isGridView: Boolean,
    onGridViewChange: (Boolean) -> Unit,
    reorderEnabled: Boolean,
    onReorderEnabledChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        LibrarySortButton(
            label = stringResource(sortType.labelRes(sortDescending)),
            descending = sortDescending,
            directionEnabled = sortType != PlaylistSortType.CUSTOM,
            onToggleDirection = { onSortDescendingChange(!sortDescending) },
            modifier = Modifier.weight(1f, fill = false),
        ) { dismiss ->
            PlaylistSortType.entries.forEach { type ->
                LibrarySortMenuItem(
                    label = stringResource(type.menuLabelRes()),
                    selected = type == sortType,
                    onClick = {
                        onSortTypeChange(type)
                        dismiss()
                    },
                )
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            DropdownMenuItem(
                text = { Text(stringResource(R.string.hidden_playlists)) },
                onClick = {
                    onShowHiddenChange(!showHidden)
                    dismiss()
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.visibility_off),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    if (showHidden) {
                        Icon(
                            painter = painterResource(R.drawable.check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        if (sortType == PlaylistSortType.CUSTOM && !isGridView) {
            FilledTonalIconToggleButton(
                checked = reorderEnabled,
                onCheckedChange = onReorderEnabledChange,
                shapes = IconButtonDefaults.toggleableShapes(),
            ) {
                Icon(
                    painter = painterResource(if (reorderEnabled) R.drawable.lock_open else R.drawable.lock),
                    contentDescription = stringResource(R.string.library_reorder_playlists),
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        LibraryViewModeToggle(
            isGrid = isGridView,
            onGridChange = onGridViewChange,
        )
    }
}

private fun PlaylistSortType.labelRes(descending: Boolean): Int =
    when (this) {
        PlaylistSortType.CREATE_DATE -> if (descending) R.string.newest_first else R.string.oldest_first
        PlaylistSortType.NAME -> if (descending) R.string.sort_z_to_a else R.string.sort_a_to_z
        PlaylistSortType.SONG_COUNT -> if (descending) R.string.most_tracks else R.string.least_tracks
        PlaylistSortType.LAST_UPDATED -> R.string.recently_updated
        PlaylistSortType.CUSTOM -> R.string.custom_order
    }

private fun PlaylistSortType.menuLabelRes(): Int =
    when (this) {
        PlaylistSortType.CREATE_DATE -> R.string.recently_added
        PlaylistSortType.NAME -> R.string.sort_a_to_z
        PlaylistSortType.SONG_COUNT -> R.string.tracks_count_label
        PlaylistSortType.LAST_UPDATED -> R.string.recently_updated
        PlaylistSortType.CUSTOM -> R.string.custom_order
    }

internal fun Playlist.displaySongCount(): Int = if (songCount > 0) songCount else playlist.remoteSongCount ?: 0

private fun mergeVisiblePlaylistOrder(
    currentOrder: List<Playlist>,
    visibleOrder: List<Playlist>,
): List<Playlist> {
    if (visibleOrder.isEmpty()) return currentOrder

    val visibleIds = visibleOrder.mapTo(HashSet(visibleOrder.size)) { playlist -> playlist.id }
    val reorderedVisible = visibleOrder.iterator()
    return currentOrder.map { playlist ->
        if (playlist.id in visibleIds) {
            reorderedVisible.next()
        } else {
            playlist
        }
    }
}

internal fun openPlaylist(
    navController: NavController,
    playlist: Playlist,
) {
    if (!playlist.playlist.isEditable && playlist.songCount == 0 && playlist.playlist.remoteSongCount != 0) {
        navController.navigate("online_playlist/${playlist.playlist.browseId}")
    } else {
        navController.navigate("local_playlist/${playlist.id}")
    }
}

@Composable
internal fun PlaylistLibraryMenu(
    playlist: Playlist,
    coroutineScope: CoroutineScope,
    menuState: MenuState,
) {
    if (playlist.playlist.isEditable || playlist.songCount != 0) {
        PlaylistMenu(
            playlist = playlist,
            coroutineScope = coroutineScope,
            onDismiss = menuState::dismiss,
        )
    } else {
        playlist.playlist.browseId?.let { browseId ->
            YouTubePlaylistMenu(
                playlist =
                    PlaylistItem(
                        id = browseId,
                        title = playlist.playlist.name,
                        author = null,
                        songCountText = null,
                        thumbnail = playlist.thumbnails.getOrNull(0) ?: "",
                        playEndpoint =
                            WatchEndpoint(
                                playlistId = browseId,
                                params = playlist.playlist.playEndpointParams,
                            ),
                        shuffleEndpoint =
                            WatchEndpoint(
                                playlistId = browseId,
                                params = playlist.playlist.shuffleEndpointParams,
                            ),
                        radioEndpoint =
                            WatchEndpoint(
                                playlistId = "RDAMPL$browseId",
                                params = playlist.playlist.radioEndpointParams,
                            ),
                        isEditable = false,
                    ),
                coroutineScope = coroutineScope,
                onDismiss = menuState::dismiss,
            )
        }
    }
}

@Composable
fun rememberArtworkGradient(
    thumbnailUrl: String?,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): List<Color> {
    val context = LocalContext.current
    var colors by remember(thumbnailUrl) { mutableStateOf(listOf(fallbackColor, fallbackColor.copy(alpha = 0.5f))) }

    LaunchedEffect(thumbnailUrl) {
        if (thumbnailUrl == null) return@LaunchedEffect
        val request =
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                .allowHardware(false)
                .build()

        val result =
            runCatching {
                context.imageLoader.execute(request)
            }.getOrNull()

        if (result != null) {
            val bitmap = result.image?.toBitmap()
            if (bitmap != null) {
                val palette =
                    withContext(Dispatchers.Default) {
                        Palette
                            .from(bitmap)
                            .maximumColorCount(PlayerColorExtractor.Config.MAX_COLOR_COUNT)
                            .resizeBitmapArea(PlayerColorExtractor.Config.BITMAP_AREA)
                            .generate()
                    }

                val extractedColors =
                    PlayerColorExtractor.extractGradientColors(
                        palette = palette,
                        fallbackColor = fallbackColor.toArgb(),
                    )
                if (extractedColors.size >= 2) {
                    colors = extractedColors
                } else if (extractedColors.isNotEmpty()) {
                    colors = listOf(extractedColors[0], extractedColors[0].copy(alpha = 0.5f))
                }
            }
        }
    }
    return colors
}

@Composable
fun rememberArtworkCardColor(
    thumbnailUrl: String?,
    fallbackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
): Color {
    val gradientColors =
        rememberArtworkGradient(
            thumbnailUrl = thumbnailUrl,
            fallbackColor = fallbackColor,
        )
    val surfaceColor = MaterialTheme.colorScheme.surface
    val useDarkTheme = remember(surfaceColor) { ColorUtils.calculateLuminance(surfaceColor.toArgb()) < 0.5 }
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    return remember(gradientColors, useDarkTheme, pureBlack) {
        val baseColor = gradientColors.firstOrNull() ?: fallbackColor
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseColor.toArgb(), hsv)
        val hue = hsv[0]

        if (useDarkTheme) {
            val saturation = (hsv[1] * 0.45f).coerceIn(0.06f, 0.20f)
            val value = if (pureBlack) 0.18f else 0.12f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, value)))
        } else {
            val saturation = (hsv[1] * 0.30f).coerceIn(0.03f, 0.12f)
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, 0.95f)))
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistListCard(
    playlist: Playlist,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    val isPersonal = playlist.playlist.isEditable
    val songCount = playlist.displaySongCount()
    val additionalAction: (@Composable RowScope.() -> Unit)? =
        if (showDragHandle) {
            {
                IconButton(
                    onClick = {},
                    modifier = dragHandleModifier,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.drag_handle),
                        contentDescription = stringResource(R.string.library_reorder_playlists),
                    )
                }
            }
        } else {
            null
        }
    val metadataTrailingContent: (@Composable RowScope.() -> Unit)? =
        if (isDownloaded || playlist.playlist.isHidden) {
            {
                if (isDownloaded) {
                    Icon(
                        painter = painterResource(R.drawable.offline),
                        contentDescription = stringResource(R.string.downloaded_desc),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                if (playlist.playlist.isHidden) {
                    Icon(
                        painter = painterResource(R.drawable.visibility_off),
                        contentDescription = stringResource(R.string.hide_playlist),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        } else {
            null
        }

    LibraryMediaListCard(
        title = playlist.playlist.name,
        subtitle = pluralStringResource(R.plurals.n_song, songCount, songCount),
        artworkUrls = playlist.thumbnails,
        fallbackIconRes = R.drawable.queue_music,
        sourceLabel = stringResource(if (isPersonal) R.string.personal_label else R.string.youtube_synced),
        sourceColor = if (isPersonal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
        onClick = onClick,
        onPlay = onPlay,
        onMenuClick = onMenuClick,
        onLongClick = onLongClick,
        contentAlpha = if (playlist.playlist.isHidden) 0.5f else 1f,
        metadataTrailingContent = metadataTrailingContent,
        additionalAction = additionalAction,
        modifier = modifier,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    isDownloaded: Boolean,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    val songCount = playlist.displaySongCount()
    val hiddenAlpha = if (playlist.playlist.isHidden) 0.5f else 1f

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = hiddenAlpha
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
                artworkUrls = playlist.thumbnails,
                fallbackIconRes = R.drawable.queue_music,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize(),
            )
            Row(
                modifier =
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (isDownloaded) {
                    PlaylistCoverBadge(
                        iconRes = R.drawable.offline,
                        contentDescription = stringResource(R.string.downloaded_desc),
                    )
                }
                if (playlist.playlist.isHidden) {
                    PlaylistCoverBadge(
                        iconRes = R.drawable.visibility_off,
                        contentDescription = stringResource(R.string.hide_playlist),
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
            text = playlist.playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
        Text(
            text = pluralStringResource(R.plurals.n_song, songCount, songCount),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
    }
}

@Composable
private fun PlaylistCoverBadge(
    iconRes: Int,
    contentDescription: String,
) {
    Box(
        modifier =
            Modifier
                .size(28.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = contentDescription,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp),
        )
    }
}
