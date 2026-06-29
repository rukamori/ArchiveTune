/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.palette.graphics.Palette
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
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
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.LibraryPlaylistsViewModel
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LibraryPlaylistsScreen(
    navController: NavController,
    filterContent: (@Composable () -> Unit)?,
    selectedTagIds: Set<String>,
    viewModel: LibraryPlaylistsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
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
    val isDarkTheme = isSystemInDarkTheme()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)

    val playlists by viewModel.allPlaylists.collectAsStateWithLifecycle()
    val filteredPlaylistIds by database
        .playlistIdsByTags(
            if (selectedTagIds.isEmpty()) emptyList() else selectedTagIds.toList(),
        ).collectAsStateWithLifecycle(initialValue = emptyList())

    var showHidden by rememberSaveable { mutableStateOf(false) }

    val visiblePlaylists =
        remember(playlists, selectedTagIds, filteredPlaylistIds, showHidden) {
            playlists.filter { playlist ->
                val name = playlist.playlist.name
                val matchesName = !name.contains("episode", ignoreCase = true)
                val matchesTags = selectedTagIds.isEmpty() || playlist.id in filteredPlaylistIds
                val matchesVisibility = showHidden || !playlist.playlist.isHidden
                matchesName && matchesTags && matchesVisibility
            }
        }
    val mutablePlaylists = remember { mutableStateListOf<Playlist>() }

    var isGridView by rememberSaveable { mutableStateOf(false) }
    var showCreatePlaylistDialog by rememberSaveable { mutableStateOf(false) }
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    var pendingPlaylistOrderUpdate by remember { mutableStateOf(false) }
    val reorderableState =
        rememberReorderableLazyListState(
            lazyListState = lazyListState,
            scrollThresholdPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        ) { from, to ->
            if (from.index in mutablePlaylists.indices && to.index in mutablePlaylists.indices) {
                mutablePlaylists.move(from.index, to.index)
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

    // Dialog launcher
    if (showCreatePlaylistDialog) {
        CreatePlaylistDialog(
            onDismiss = { showCreatePlaylistDialog = false },
        )
    }

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
        Column(modifier = Modifier.fillMaxSize()) {
            // Control row (Sort dropdown, grid/list layout toggle, + add button)
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Left: Sort dropdown
                var showSortMenu by remember { mutableStateOf(false) }
                val currentSortLabel =
                    when (sortType) {
                        PlaylistSortType.CREATE_DATE -> {
                            stringResource(R.string.recently_added)
                        }

                        PlaylistSortType.NAME -> {
                            if (sortDescending) stringResource(R.string.sort_z_to_a) else stringResource(R.string.sort_a_to_z)
                        }

                        PlaylistSortType.SONG_COUNT -> {
                            stringResource(R.string.tracks_count_label)
                        }

                        PlaylistSortType.LAST_UPDATED -> {
                            stringResource(R.string.recently_updated)
                        }

                        PlaylistSortType.CUSTOM -> {
                            stringResource(R.string.custom_order)
                        }
                    }

                val showSortDirection = sortType != PlaylistSortType.CUSTOM
                val sortDirectionRotation by animateFloatAsState(
                    targetValue = if (sortDescending) 0f else 180f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                    label = "PlaylistSortDirectionRotation",
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
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
                            PlaylistSortType.entries.forEach { type ->
                                val label =
                                    when (type) {
                                        PlaylistSortType.CREATE_DATE -> stringResource(R.string.recently_added)
                                        PlaylistSortType.NAME -> stringResource(R.string.sort_a_to_z)
                                        PlaylistSortType.SONG_COUNT -> stringResource(R.string.tracks_count_label)
                                        PlaylistSortType.LAST_UPDATED -> stringResource(R.string.recently_updated)
                                        PlaylistSortType.CUSTOM -> stringResource(R.string.custom_order)
                                    }
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onSortTypeChange(type)
                                        if (type == PlaylistSortType.NAME) {
                                            onSortDescendingChange(false)
                                        }
                                        showSortMenu = false
                                    },
                                )
                            }
                        }
                    }

                    if (showSortDirection) {
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onSortDescendingChange(!sortDescending) },
                            colors =
                                IconButtonDefaults.iconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                ),
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.arrow_downward),
                                contentDescription =
                                    stringResource(
                                        if (sortDescending) {
                                            R.string.sort_order_descending
                                        } else {
                                            R.string.sort_order_ascending
                                        },
                                    ),
                                modifier =
                                    Modifier
                                        .size(16.dp)
                                        .graphicsLayer { rotationZ = sortDirectionRotation },
                            )
                        }
                    }
                }

                // Right: list/grid toggle & add button
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sortType == PlaylistSortType.CUSTOM) {
                        IconButton(
                            onClick = { locked = !locked },
                            modifier = Modifier.size(40.dp),
                        ) {
                            Icon(
                                painter = painterResource(if (locked) R.drawable.lock else R.drawable.lock_open),
                                contentDescription = null,
                            )
                        }

                        Spacer(modifier = Modifier.width(8.dp))
                    }

                    // List/Grid Toggle
                    Row(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (!isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isGridView = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.queue_music),
                                contentDescription = stringResource(R.string.list_view),
                                tint = if (!isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (isGridView) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { isGridView = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.album),
                                contentDescription = stringResource(R.string.grid_view),
                                tint = if (isGridView) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Row(
                        modifier =
                            Modifier
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(if (showHidden) MaterialTheme.colorScheme.primary else Color.Transparent)
                                    .clickable { showHidden = !showHidden },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.visibility_off),
                                contentDescription = stringResource(R.string.show_hidden_playlists),
                                tint = if (showHidden) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                        Box(
                            modifier =
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .clickable { showCreatePlaylistDialog = true },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                painter = painterResource(id = R.drawable.add),
                                contentDescription = stringResource(R.string.create_playlist),
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            val playlistTagFilterContent = filterContent
            if (playlistTagFilterContent != null) {
                playlistTagFilterContent()
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Main Content
            if (isGridView) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = playerAwareBottomPadding),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(
                        items = visiblePlaylists,
                        key = { playlist -> playlist.id },
                        contentType = { "playlist_grid" },
                    ) { playlist ->
                        PlaylistGridCard(
                            playlist = playlist,
                            onClick = {
                                openPlaylist(navController, playlist)
                            },
                            onPlay = {
                                playerConnection?.let { conn ->
                                    coroutineScope.launch {
                                        database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                            if (songs.isNotEmpty()) {
                                                conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                            }
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    triggerPlaylistMenu(playlist, coroutineScope, menuState)
                                }
                            },
                        )
                    }
                }
            } else {
                val spaceBetween = if (isDarkTheme && pureBlack) 0.dp else 12.dp
                val listPlaylists = if (sortType == PlaylistSortType.CUSTOM) mutablePlaylists else visiblePlaylists
                val showDragHandles = sortType == PlaylistSortType.CUSTOM && !locked
                LazyColumn(
                    state = lazyListState,
                    contentPadding = PaddingValues(start = 24.dp, end = 24.dp, bottom = playerAwareBottomPadding),
                    verticalArrangement = Arrangement.spacedBy(spaceBetween),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(
                        items = listPlaylists,
                        key = { _, playlist -> playlist.id },
                        contentType = { _, _ -> "playlist_list" },
                    ) { index, playlist ->
                        ReorderableItem(
                            state = reorderableState,
                            key = playlist.id,
                            modifier =
                                Modifier.graphicsLayer {
                                    compositingStrategy = androidx.compose.ui.graphics.CompositingStrategy.Offscreen
                                },
                        ) {
                            val showDivider = isDarkTheme && pureBlack && index > 0
                            if (showDivider) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                                    thickness = 0.5.dp,
                                )
                            }
                            PlaylistListCard(
                                playlist = playlist,
                                onClick = {
                                    openPlaylist(navController, playlist)
                                },
                                onPlay = {
                                    playerConnection?.let { conn ->
                                        coroutineScope.launch {
                                            database.playlistSongs(playlist.id).firstOrNull()?.let { songs ->
                                                if (songs.isNotEmpty()) {
                                                    conn.playQueue(ListQueue(items = songs.map { it.song.toMediaItem() }))
                                                }
                                            }
                                        }
                                    }
                                },
                                onMenuClick = {
                                    menuState.show {
                                        triggerPlaylistMenu(playlist, coroutineScope, menuState)
                                    }
                                },
                                showDragHandle = showDragHandles,
                                dragHandleModifier =
                                    Modifier
                                        .draggableHandle()
                                        .graphicsLayer { alpha = 0.99f },
                            )
                        }
                    }
                }
            }
        }
    }
}

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

private fun openPlaylist(
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
private fun triggerPlaylistMenu(
    playlist: Playlist,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    menuState: moe.rukamori.archivetune.ui.component.MenuState,
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
        val baseArgb = baseColor.toArgb()
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(baseArgb, hsv)
        val hue = hsv[0]

        if (useDarkTheme) {
            // Issue 6/3 fix: increased brightness for visibility in pure black mode
            val s = (hsv[1] * 0.45f).coerceIn(0.06f, 0.20f)
            val v = if (pureBlack) 0.18f else 0.12f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
        } else {
            val s = (hsv[1] * 0.30f).coerceIn(0.03f, 0.12f)
            val v = 0.95f
            Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, s, v)))
        }
    }
}

@Composable
fun PlaylistListCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onMenuClick: () -> Unit,
    showDragHandle: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
) {
    val cardBgColor =
        rememberArtworkCardColor(
            thumbnailUrl = playlist.thumbnails.getOrNull(0),
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PlaylistListCardScale",
    )

    val hiddenAlpha = if (playlist.playlist.isHidden) 0.45f else 1f

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = hiddenAlpha
                }.clip(RoundedCornerShape(32.dp))
                .background(cardBgColor)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                ).padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Thumbnail
        AsyncImage(
            model = playlist.thumbnails.getOrNull(0),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
        )

        Spacer(modifier = Modifier.width(16.dp))

        // Text & details
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.playlist.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                )

                // Tag pill
                val tagText =
                    if (playlist.playlist.isEditable) {
                        stringResource(
                            R.string.personal_label,
                        )
                    } else {
                        stringResource(R.string.youtube_synced)
                    }
                val tagColor = if (playlist.playlist.isEditable) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                Box(
                    modifier =
                        Modifier
                            .clip(CircleShape)
                            .background(tagColor.copy(alpha = 0.12f))
                            .padding(horizontal = 8.dp, vertical = 2.dp),
                ) {
                    Text(
                        text = tagText,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = tagColor,
                    )
                }

                if (playlist.playlist.isHidden) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(
                        painter = painterResource(id = R.drawable.visibility_off),
                        contentDescription = stringResource(R.string.hide_playlist),
                        modifier = Modifier.size(12.dp),
                        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                    )
                }
            }
        }

        // Play Button
        IconButton(
            onClick = onPlay,
            colors =
                IconButtonDefaults.iconButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                    contentColor = MaterialTheme.colorScheme.primary,
                ),
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.play),
                contentDescription = stringResource(R.string.play),
                modifier = Modifier.size(16.dp),
            )
        }

        Spacer(modifier = Modifier.width(4.dp))

        // Options Button
        IconButton(
            onClick = onMenuClick,
            modifier = Modifier.size(36.dp),
        ) {
            Icon(
                painter = painterResource(id = R.drawable.more_vert),
                contentDescription = stringResource(R.string.options_label),
            )
        }

        if (showDragHandle) {
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = {},
                modifier = dragHandleModifier.size(36.dp),
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.drag_handle),
                    contentDescription = null,
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistGridCard(
    playlist: Playlist,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onLongClick: () -> Unit,
) {
    val cardBgColor =
        rememberArtworkCardColor(
            thumbnailUrl = playlist.thumbnails.getOrNull(0),
            fallbackColor = MaterialTheme.colorScheme.surfaceContainerLow,
        )

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1.0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "PlaylistGridCardScale",
    )

    val hiddenAlpha = if (playlist.playlist.isHidden) 0.45f else 1f

    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    alpha = hiddenAlpha
                }.clip(RoundedCornerShape(32.dp))
                .background(cardBgColor)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = null,
                    onClick = onClick,
                    onLongClick = onLongClick,
                ).padding(12.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(26.dp)),
        ) {
            AsyncImage(
                model = playlist.thumbnails.getOrNull(0),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Play overlay on bottom right of grid cover
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                        .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.play),
                    contentDescription = stringResource(R.string.play),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }

            if (playlist.playlist.isHidden) {
                Icon(
                    painter = painterResource(id = R.drawable.visibility_off),
                    contentDescription = stringResource(R.string.hide_playlist),
                    modifier =
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(16.dp),
                    tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f),
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = playlist.playlist.name,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "${playlist.songCount} ${stringResource(R.string.tracks_label)}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
        )
    }
}
