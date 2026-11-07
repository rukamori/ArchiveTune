/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)

package moe.rukamori.archivetune.ui.screens.search

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.add
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.RoundedPolygon
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ALBUM
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_ARTIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_COMMUNITY_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_FEATURED_PLAYLIST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_PODCAST
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_SONG
import moe.rukamori.archivetune.innertube.YouTube.SearchFilter.Companion.FILTER_VIDEO
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.viewmodels.OnlineSearchSort
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel
import moe.rukamori.archivetune.viewmodels.SearchLoadError
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

private enum class SearchResultLayout {
    Rows,
    WideRows,
    Cards,
    Artists,
}

@Immutable
private data class SearchFilterOption(
    val filter: YouTube.SearchFilter?,
    @StringRes val labelRes: Int,
    @DrawableRes val iconRes: Int,
    val layout: SearchResultLayout,
)

private val SearchFilterOptions =
    listOf(
        SearchFilterOption(null, R.string.filter_all, R.drawable.search, SearchResultLayout.Rows),
        SearchFilterOption(FILTER_SONG, R.string.filter_songs, R.drawable.music_note, SearchResultLayout.Rows),
        SearchFilterOption(FILTER_VIDEO, R.string.filter_videos, R.drawable.slow_motion_video, SearchResultLayout.WideRows),
        SearchFilterOption(FILTER_ALBUM, R.string.filter_albums, R.drawable.album, SearchResultLayout.Cards),
        SearchFilterOption(FILTER_ARTIST, R.string.filter_artists, R.drawable.person, SearchResultLayout.Artists),
        SearchFilterOption(FILTER_PODCAST, R.string.podcast, R.drawable.mic, SearchResultLayout.Cards),
        SearchFilterOption(
            FILTER_COMMUNITY_PLAYLIST,
            R.string.filter_community_playlists,
            R.drawable.queue_music,
            SearchResultLayout.Cards,
        ),
        SearchFilterOption(
            FILTER_FEATURED_PLAYLIST,
            R.string.filter_featured_playlists,
            R.drawable.playlist_play,
            SearchResultLayout.Cards,
        ),
    )

private fun SearchFilterOption.badgeShape(): RoundedPolygon =
    when (filter) {
        FILTER_SONG -> MaterialShapes.Cookie9Sided
        FILTER_VIDEO -> MaterialShapes.Pill
        FILTER_ALBUM -> MaterialShapes.Slanted
        FILTER_ARTIST -> MaterialShapes.Clover4Leaf
        FILTER_PODCAST -> MaterialShapes.Arch
        FILTER_COMMUNITY_PLAYLIST -> MaterialShapes.Cookie6Sided
        FILTER_FEATURED_PLAYLIST -> MaterialShapes.Flower
        else -> MaterialShapes.Sunny
    }

@Composable
fun OnlineSearchResult(
    navController: NavController,
    searchSort: OnlineSearchSort,
    viewModel: OnlineSearchViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()
    val itemMotion = rememberSearchItemMotion()

    val searchFilter by viewModel.filter.collectAsStateWithLifecycle()
    val selectedOption = SearchFilterOptions.first { it.filter == searchFilter }
    val isScrolled by remember { derivedStateOf { gridState.canScrollBackward } }

    LaunchedEffect(gridState, viewModel) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            layoutInfo.visibleItemsInfo.any { it.key == LoadMoreKey } to layoutInfo.totalItemsCount
        }.distinctUntilChanged()
            .collect { (loadMoreVisible, _) ->
                if (loadMoreVisible) viewModel.loadMore()
            }
    }

    val selectFilter: (YouTube.SearchFilter?) -> Unit = { filter ->
        viewModel.selectFilter(filter)
        coroutineScope.launch { gridState.scrollToItem(0) }
    }
    val openItemMenu: (YTItem) -> Unit = { item ->
        if (item.hasSearchResultMenu()) {
            menuState.show {
                SearchResultMenu(
                    item = item,
                    navController = navController,
                    coroutineScope = coroutineScope,
                    onDismiss = menuState::dismiss,
                )
            }
        }
    }
    val openItem: (YTItem) -> Unit = { item ->
        when (item) {
            is SongItem -> {
                if (item.id == mediaMetadata?.id) {
                    playerConnection.player.togglePlayPause()
                } else {
                    playerConnection.playQueue(
                        YouTubeQueue(
                            WatchEndpoint(videoId = item.id),
                            item.toMediaMetadata(),
                        ),
                    )
                }
            }

            is AlbumItem -> {
                navController.navigateToAlbum(item.id)
            }

            is ArtistItem -> {
                navController.navigateToArtist(item.id)
            }

            is PlaylistItem -> {
                navController.navigate("online_playlist/${item.id}")
            }

            is PodcastItem -> {
                navController.navigate("podcast/${android.net.Uri.encode(item.browseId)}")
            }

            is EpisodeItem -> {
                playerConnection.playQueue(
                    ListQueue(
                        title = item.podcast?.name ?: item.title,
                        items = listOf(item.toMediaItem()),
                    ),
                )
            }
        }
    }
    val isItemActive: (YTItem) -> Boolean = { item ->
        when (item) {
            is SongItem -> mediaMetadata?.id == item.id
            is AlbumItem -> mediaMetadata?.album?.id == item.id
            else -> false
        }
    }
    val actions =
        SearchResultActions(
            isPlaying = isPlaying,
            isActive = isItemActive,
            onOpen = openItem,
            onMenu = openItemMenu,
        )

    val topResults = viewModel.summaryPage?.summaries?.firstOrNull()?.items.orEmpty()
    val allModeSections =
        SearchFilterOptions.drop(1).mapNotNull { option ->
            val filterKey = option.filter?.value ?: return@mapNotNull null
            viewModel.viewStateMap[filterKey]
                ?.items
                ?.takeIf { it.isNotEmpty() }
                ?.let { items -> option to viewModel.sortedItems(items, searchSort) }
        }
    val filterKey = searchFilter?.value
    val itemsPage = filterKey?.let { viewModel.viewStateMap[it] }
    val filteredItems =
        remember(itemsPage, searchSort) {
            viewModel.sortedItems(itemsPage?.items.orEmpty().distinctBy { it.id }, searchSort)
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
    ) {
        SearchFilterBar(
            selectedFilter = searchFilter,
            onSelect = selectFilter,
            elevated = isScrolled,
            modifier =
                Modifier
                    .windowInsetsPadding(
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Top)
                            .add(WindowInsets(top = AppBarHeight)),
                    ).fillMaxWidth(),
        )

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = GridCellMinSize),
            state = gridState,
            contentPadding =
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                    .add(WindowInsets(left = GridHorizontalPadding, top = 4.dp, right = GridHorizontalPadding, bottom = 16.dp))
                    .asPaddingValues(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier =
                Modifier
                    .weight(1f)
                    .fillMaxWidth(),
        ) {
            if (searchFilter == null) {
                allModeContent(
                    query = viewModel.query,
                    topResults = topResults,
                    sections = allModeSections,
                    resolved = viewModel.allModeResolved,
                    error = viewModel.summaryError ?: viewModel.filterErrors.values.firstOrNull(),
                    actions = actions,
                    itemMotion = itemMotion,
                    onSeeAll = selectFilter,
                    onRetry = viewModel::retry,
                )
            } else {
                filteredContent(
                    query = viewModel.query,
                    option = selectedOption,
                    items = filteredItems,
                    loaded = itemsPage != null,
                    hasMore = itemsPage?.continuation != null,
                    error = filterKey?.let { viewModel.filterErrors[it] },
                    loadMoreError = viewModel.loadMoreError,
                    actions = actions,
                    itemMotion = itemMotion,
                    onRetry = viewModel::retry,
                    onLoadMore = viewModel::loadMore,
                )
            }
        }
    }
}

@Immutable
private class SearchResultActions(
    val isPlaying: Boolean,
    val isActive: (YTItem) -> Boolean,
    val onOpen: (YTItem) -> Unit,
    val onMenu: (YTItem) -> Unit,
)

private fun LazyGridItemScope.gridItemModifier(motion: SearchItemMotion): Modifier =
    Modifier.animateItem(
        fadeInSpec = motion.fade,
        placementSpec = motion.placement,
        fadeOutSpec = motion.fade,
    )

private fun LazyGridScope.fullLineItem(
    key: Any,
    contentType: Any,
    content: @Composable LazyGridItemScope.() -> Unit,
) {
    item(
        key = key,
        span = { GridItemSpan(maxLineSpan) },
        contentType = contentType,
        content = content,
    )
}

private fun LazyGridScope.allModeContent(
    query: String,
    topResults: List<YTItem>,
    sections: List<Pair<SearchFilterOption, List<YTItem>>>,
    resolved: Boolean,
    error: SearchLoadError?,
    actions: SearchResultActions,
    itemMotion: SearchItemMotion,
    onSeeAll: (YouTube.SearchFilter?) -> Unit,
    onRetry: () -> Unit,
) {
    val hasContent = topResults.isNotEmpty() || sections.isNotEmpty()

    if (!hasContent) {
        fullLineItem(key = "all_state", contentType = "state") {
            when {
                !resolved -> {
                    SearchLoadingPanel(
                        message = stringResource(R.string.search_results_loading),
                        modifier = gridItemModifier(itemMotion),
                    )
                }

                error != null -> {
                    SearchErrorPanel(error = error, onRetry = onRetry, modifier = gridItemModifier(itemMotion))
                }

                else -> {
                    SearchEmptyPanel(query = query, modifier = gridItemModifier(itemMotion))
                }
            }
        }
        return
    }

    val topResult = topResults.firstOrNull()
    if (topResult != null) {
        val otherTopResults = topResults.drop(1).take(MaxTopResultRows)
        fullLineItem(key = "top_header", contentType = "section_header") {
            DiscoverySectionHeader(
                title = stringResource(R.string.search_top_result),
                iconRes = R.drawable.auto_awesome,
                badgeShape = MaterialShapes.Sunny,
                modifier = gridItemModifier(itemMotion),
            )
        }
        fullLineItem(key = "top_card_${topResult.id}", contentType = "top_result") {
            TopResultCard(
                item = topResult,
                isActive = actions.isActive(topResult),
                isPlaying = actions.isPlaying,
                pureBlack = false,
                onClick = { actions.onOpen(topResult) },
                onMenu = { actions.onMenu(topResult) },
                modifier =
                    gridItemModifier(itemMotion)
                        .padding(bottom = if (otherTopResults.isEmpty()) 0.dp else 8.dp),
            )
        }
        otherTopResults.forEachIndexed { index, item ->
            fullLineItem(key = "top_row_${item.id}", contentType = "result_row") {
                SearchResultRow(
                    item = item,
                    index = index,
                    count = otherTopResults.size,
                    isActive = actions.isActive(item),
                    isPlaying = actions.isPlaying,
                    pureBlack = false,
                    onClick = { actions.onOpen(item) },
                    onMenu = { actions.onMenu(item) },
                    modifier = gridItemModifier(itemMotion),
                )
            }
        }
    }

    sections.forEach { (option, items) ->
        val sectionKey = option.filter?.value ?: return@forEach
        fullLineItem(key = "section_header_$sectionKey", contentType = "section_header") {
            val sectionTitle = stringResource(option.labelRes)
            val seeAllDescription = stringResource(R.string.search_see_all_section, sectionTitle)
            DiscoverySectionHeader(
                title = sectionTitle,
                iconRes = option.iconRes,
                badgeShape = option.badgeShape(),
                modifier = gridItemModifier(itemMotion).padding(top = 8.dp),
                action = {
                    TextButton(
                        onClick = { onSeeAll(option.filter) },
                        modifier = Modifier.semantics { contentDescription = seeAllDescription },
                    ) {
                        Text(stringResource(R.string.see_all))
                    }
                },
            )
        }

        when (option.layout) {
            SearchResultLayout.Rows, SearchResultLayout.WideRows -> {
                val previewItems = items.take(MaxSectionRows)
                previewItems.forEachIndexed { index, item ->
                    fullLineItem(key = "section_${sectionKey}_${item.id}", contentType = "result_row") {
                        SearchResultRow(
                            item = item,
                            index = index,
                            count = previewItems.size,
                            isActive = actions.isActive(item),
                            isPlaying = actions.isPlaying,
                            pureBlack = false,
                            onClick = { actions.onOpen(item) },
                            onMenu = { actions.onMenu(item) },
                            wideThumbnail = option.layout == SearchResultLayout.WideRows,
                            modifier = gridItemModifier(itemMotion),
                        )
                    }
                }
            }

            SearchResultLayout.Cards, SearchResultLayout.Artists -> {
                fullLineItem(key = "section_shelf_$sectionKey", contentType = "result_shelf") {
                    SearchResultShelf(
                        items = items.take(MaxShelfItems),
                        layout = option.layout,
                        actions = actions,
                        modifier = gridItemModifier(itemMotion),
                    )
                }
            }
        }
    }

    if (!resolved) {
        fullLineItem(key = "all_more_loading", contentType = "inline_status") {
            SearchInlineStatus(
                message = stringResource(R.string.search_loading_sections),
                loading = true,
                modifier = gridItemModifier(itemMotion),
            )
        }
    }
}

private fun LazyGridScope.filteredContent(
    query: String,
    option: SearchFilterOption,
    items: List<YTItem>,
    loaded: Boolean,
    hasMore: Boolean,
    error: SearchLoadError?,
    loadMoreError: SearchLoadError?,
    actions: SearchResultActions,
    itemMotion: SearchItemMotion,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
) {
    val filterKey = option.filter?.value ?: return

    if (!loaded) {
        fullLineItem(key = "filter_state_$filterKey", contentType = "state") {
            if (error != null) {
                SearchErrorPanel(error = error, onRetry = onRetry, modifier = gridItemModifier(itemMotion))
            } else {
                SearchLoadingPanel(
                    message = stringResource(R.string.search_results_loading),
                    modifier = gridItemModifier(itemMotion),
                )
            }
        }
        return
    }

    if (items.isEmpty() && !hasMore) {
        fullLineItem(key = "filter_empty_$filterKey", contentType = "state") {
            SearchEmptyPanel(query = query, modifier = gridItemModifier(itemMotion))
        }
        return
    }

    fullLineItem(key = "filter_header_$filterKey", contentType = "section_header") {
        DiscoverySectionHeader(
            title = stringResource(option.labelRes),
            iconRes = option.iconRes,
            badgeShape = option.badgeShape(),
            modifier = gridItemModifier(itemMotion),
        )
    }

    when (option.layout) {
        SearchResultLayout.Rows, SearchResultLayout.WideRows -> {
            itemsIndexed(
                items = items,
                key = { _, item -> "filtered_${item.id}" },
                span = { _, _ -> GridItemSpan(maxLineSpan) },
                contentType = { _, _ -> "result_row" },
            ) { index, item ->
                SearchResultRow(
                    item = item,
                    index = index,
                    count = items.size,
                    isActive = actions.isActive(item),
                    isPlaying = actions.isPlaying,
                    pureBlack = false,
                    onClick = { actions.onOpen(item) },
                    onMenu = { actions.onMenu(item) },
                    wideThumbnail = option.layout == SearchResultLayout.WideRows,
                    modifier = gridItemModifier(itemMotion),
                )
            }
        }

        SearchResultLayout.Cards, SearchResultLayout.Artists -> {
            items(
                items = items,
                key = { item -> "filtered_${item.id}" },
                contentType = { item -> if (item is ArtistItem) "artist_tile" else "media_card" },
            ) { item ->
                SearchResultTile(
                    item = item,
                    actions = actions,
                    modifier =
                        gridItemModifier(itemMotion)
                            .fillMaxWidth()
                            .padding(bottom = 10.dp),
                )
            }
        }
    }

    if (hasMore) {
        fullLineItem(key = if (loadMoreError == null) LoadMoreKey else "load_more_error", contentType = "inline_status") {
            if (loadMoreError == null) {
                SearchInlineStatus(
                    message = stringResource(R.string.search_loading_more),
                    loading = true,
                    modifier = gridItemModifier(itemMotion),
                )
            } else {
                SearchInlineStatus(
                    message = stringResource(R.string.search_load_more_failed),
                    onRetry = onLoadMore,
                    modifier = gridItemModifier(itemMotion),
                )
            }
        }
    }
}

@Composable
private fun SearchFilterBar(
    selectedFilter: YouTube.SearchFilter?,
    onSelect: (YouTube.SearchFilter?) -> Unit,
    elevated: Boolean,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    val animationsDisabled = LocalAnimationsDisabled.current
    val containerColor by animateColorAsState(
        targetValue = if (elevated) colorScheme.surfaceContainer else colorScheme.background,
        animationSpec = if (animationsDisabled) snap() else MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "searchFilterBarContainer",
    )
    val rowState = rememberLazyListState()
    val selectedIndex = SearchFilterOptions.indexOfFirst { it.filter == selectedFilter }.coerceAtLeast(0)
    val filterLabel = stringResource(R.string.search_filter_label)

    LaunchedEffect(selectedIndex) {
        rowState.animateScrollToItem((selectedIndex - 1).coerceAtLeast(0))
    }

    Surface(
        color = containerColor,
        modifier = modifier,
    ) {
        LazyRow(
            state = rowState,
            contentPadding = PaddingValues(horizontal = GridHorizontalPadding, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = filterLabel },
        ) {
            items(
                items = SearchFilterOptions,
                key = { option -> option.filter?.value ?: "all" },
                contentType = { "filter_toggle" },
            ) { option ->
                val checked = option.filter == selectedFilter
                ToggleButton(
                    checked = checked,
                    onCheckedChange = { onSelect(option.filter) },
                    modifier = Modifier.semantics { role = Role.RadioButton },
                ) {
                    Icon(
                        painter = painterResource(option.iconRes),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(option.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultShelf(
    items: List<YTItem>,
    layout: SearchResultLayout,
    actions: SearchResultActions,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(if (layout == SearchResultLayout.Artists) 4.dp else 12.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        items(
            items = items,
            key = { item -> "shelf_${item.id}" },
            contentType = { item -> if (item is ArtistItem) "artist_tile" else "media_card" },
        ) { item ->
            SearchResultTile(
                item = item,
                actions = actions,
                modifier = Modifier.width(if (item is ArtistItem) ArtistTileWidth else AlbumCardWidth),
            )
        }
    }
}

@Composable
private fun SearchResultTile(
    item: YTItem,
    actions: SearchResultActions,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val longClick: (() -> Unit)? =
        if (item.hasSearchResultMenu()) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                actions.onMenu(item)
            }
        } else {
            null
        }

    if (item is ArtistItem) {
        SearchArtistTile(
            artist = item,
            onClick = { actions.onOpen(item) },
            onLongClick = { longClick?.invoke() },
            modifier = modifier,
        )
    } else {
        SearchMediaCard(
            thumbnailUrl = item.thumbnail,
            title = item.title,
            subtitle = searchResultSubtitle(item).orEmpty(),
            label = if (item is AlbumItem) searchResultTypeLabel(item) else null,
            explicit = item.explicit,
            isActive = actions.isActive(item),
            isPlaying = actions.isPlaying,
            onClick = { actions.onOpen(item) },
            onLongClick = longClick,
            placeholderIconRes =
                when (item) {
                    is AlbumItem -> R.drawable.album
                    is PlaylistItem -> R.drawable.queue_music
                    is PodcastItem, is EpisodeItem -> R.drawable.mic
                    else -> R.drawable.music_note
                },
            modifier = modifier,
        )
    }
}

@Composable
private fun SearchErrorPanel(
    error: SearchLoadError,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchStatePanel(
        iconRes = R.drawable.error,
        title = stringResource(if (error == SearchLoadError.Offline) R.string.error_no_internet else R.string.search_results_failed),
        message =
            stringResource(
                if (error == SearchLoadError.Offline) R.string.search_results_offline_message else R.string.search_results_failed_message,
            ),
        badgeShape = MaterialShapes.SoftBurst,
        isError = true,
        modifier = modifier,
        action = {
            Button(
                onClick = onRetry,
                contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
            ) {
                Icon(
                    painter = painterResource(R.drawable.replay),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.retry))
            }
        },
    )
}

@Composable
private fun SearchEmptyPanel(
    query: String,
    modifier: Modifier = Modifier,
) {
    SearchStatePanel(
        iconRes = R.drawable.search_off,
        title = stringResource(R.string.search_results_empty_title, query),
        message = stringResource(R.string.search_results_empty_message),
        modifier = modifier,
    )
}

private const val LoadMoreKey = "loading"
private const val MaxTopResultRows = 4
private const val MaxSectionRows = 4
private const val MaxShelfItems = 12
private val GridCellMinSize = 150.dp
private val GridHorizontalPadding = 12.dp
