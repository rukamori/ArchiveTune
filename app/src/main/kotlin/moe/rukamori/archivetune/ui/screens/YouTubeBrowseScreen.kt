/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.PrimaryScrollableTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.browse.GenreBrowseScreenState
import moe.rukamori.archivetune.browse.GenreSource
import moe.rukamori.archivetune.browse.GenreSourceState
import moe.rukamori.archivetune.browse.GenreTab
import moe.rukamori.archivetune.browse.genreKey
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.viewmodels.YouTubeBrowseViewModel
import moe.rukamori.archivetune.ui.component.IconButton as ArchiveIconButton

@Composable
fun YouTubeBrowseScreen(
    navController: NavController,
    viewModel: YouTubeBrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val onBack: () -> Unit = {
        if (state.data.expandedSection != null) viewModel.closeSection() else navController.navigateUp()
    }
    BackHandler(enabled = state.data.expandedSection != null) { viewModel.closeSection() }

    YouTubeBrowseContent(
        state = state,
        activeSongId = mediaMetadata?.id,
        activeAlbumId = mediaMetadata?.album?.id,
        isPlaying = isPlaying,
        onBack = onBack,
        onBackToMain = navController::backToMain,
        onTabSelected = viewModel::selectTab,
        onOpenSection = viewModel::openSection,
        onLoadSource = viewModel::loadSource,
        onLoadCollection = viewModel::loadCollection,
        onItemClick = { item ->
            if (lifecycleOwner.lifecycle.currentState == Lifecycle.State.RESUMED) when (item) {
                is SongItem -> {
                    if (item.id == mediaMetadata?.id) playerConnection.player.togglePlayPause()
                    else playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                }
                is AlbumItem -> navController.navigateToAlbum(item.id)
                is ArtistItem -> navController.navigateToArtist(item.id)
                is PlaylistItem -> navController.navigate("online_playlist/${android.net.Uri.encode(item.id)}") { launchSingleTop = true }
                is PodcastItem -> navController.navigate("podcast/${android.net.Uri.encode(item.browseId)}") { launchSingleTop = true }
                is EpisodeItem -> playerConnection.playQueue(
                    ListQueue(title = item.podcast?.name ?: item.title, items = listOf(item.toMediaItem())),
                )
            }
        },
        onItemMenu = { item ->
            if (item !is PodcastItem && item !is EpisodeItem) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    when (item) {
                        is SongItem -> YouTubeSongMenu(song = item, navController = navController, onDismiss = menuState::dismiss)
                        is AlbumItem -> YouTubeAlbumMenu(albumItem = item, navController = navController, onDismiss = menuState::dismiss)
                        is ArtistItem -> YouTubeArtistMenu(artist = item, onDismiss = menuState::dismiss)
                        is PlaylistItem -> YouTubePlaylistMenu(playlist = item, coroutineScope = coroutineScope, onDismiss = menuState::dismiss)
                        is PodcastItem, is EpisodeItem -> Unit
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun YouTubeBrowseContent(
    state: GenreBrowseScreenState,
    activeSongId: String?,
    activeAlbumId: String?,
    isPlaying: Boolean,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    onTabSelected: (GenreTab) -> Unit,
    onOpenSection: (String) -> Unit,
    onLoadSource: (GenreSource) -> Unit,
    onLoadCollection: (String) -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemMenu: (YTItem) -> Unit,
) {
    val data = state.data
    val viewKey = data.expandedSection ?: data.selectedTab.name
    val savedState = rememberSaveableStateHolder()
    val coroutineScope = rememberCoroutineScope()
    val insets = LocalPlayerAwareWindowInsets.current
        .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom).asPaddingValues()
    savedState.SaveableStateProvider(viewKey) {
        val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
        val gridState = rememberLazyGridState()
        Scaffold(
            modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection),
            topBar = {
                LargeFlexibleTopAppBar(
                    title = {
                        Text(
                            text = data.section?.title ?: data.title.orEmpty(),
                            fontWeight = MaterialTheme.typography.headlineLargeEmphasized.fontWeight,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                    subtitle = if (data.expandedSection != null) {
                        { Text(text = data.title.orEmpty(), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    } else null,
                    navigationIcon = {
                        ArchiveIconButton(onClick = onBack, onLongClick = onBackToMain) {
                            Icon(painterResource(R.drawable.arrow_back), contentDescription = stringResource(R.string.genre_navigate_back))
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    scrollBehavior = scrollBehavior,
                )
            },
        ) { scaffoldPadding ->
            val minimumColumnWidth = if (LocalDensity.current.fontScale >= 1.5f) 240.dp else 160.dp
            val mediaItem: @Composable (YTItem, Boolean) -> Unit = { item, asList ->
                GenreMediaItem(
                    item = item,
                    asList = asList,
                    isActive = if (item is AlbumItem) item.id == activeAlbumId else item.id == activeSongId,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    onClick = { onItemClick(item) },
                    onMenu = { onItemMenu(item) },
                )
            }
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minimumColumnWidth),
                state = gridState,
                contentPadding = insets,
                modifier = Modifier.fillMaxSize().padding(top = scaffoldPadding.calculateTopPadding()),
            ) {
                if (data.expandedSection == null && data.genreTitle != null) {
                    if (data.selectedTab == GenreTab.ALL && data.feed.thumbnail != null) {
                        item(span = { GridItemSpan(maxLineSpan) }, key = "genre_artwork", contentType = "artwork") {
                            Surface(
                                shape = MaterialTheme.shapes.extraLarge,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                            ) {
                                AsyncImage(
                                    model = data.feed.thumbnail,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 7f),
                                )
                            }
                        }
                    }
                    stickyHeader(key = "genre_tabs", contentType = "tabs") {
                        PrimaryScrollableTabRow(selectedTabIndex = data.selectedTab.ordinal, edgePadding = 16.dp) {
                            GenreTab.entries.forEach { tab ->
                                Tab(
                                    selected = data.selectedTab == tab,
                                    onClick = { onTabSelected(tab) },
                                    text = { Text(stringResource(tab.titleRes)) },
                                )
                            }
                        }
                    }
                }
                when {
                    data.expandedSection != null -> {
                        val section = data.section
                        val collection = data.collection
                        genreItems(
                            keyPrefix = "collection",
                            items = collection?.items ?: section?.items.orEmpty(),
                            mediaItem = mediaItem,
                        )
                        item(span = { GridItemSpan(maxLineSpan) }, key = "collection_status", contentType = "status") {
                            if (collection != null) {
                                GenreLoadStatus(
                                    loading = collection.loading,
                                    failed = collection.failed,
                                    canLoadMore = !collection.endpointLoaded || collection.continuations.isNotEmpty(),
                                    label = section?.title ?: stringResource(R.string.explore),
                                    onLoad = { onLoadCollection(collection.sectionId) },
                                )
                                if (collection.items.isEmpty() && !collection.loading && !collection.failed && collection.continuations.isEmpty()) {
                                    GenreEmptyMessage()
                                }
                            } else {
                                GenreSourceStatus(data.feed, onLoadSource)
                                if (data.feed.loaded && !data.feed.loading && !data.feed.failed) GenreEmptyMessage()
                            }
                        }
                    }
                    data.selectedTab == GenreTab.ALL -> {
                        data.feed.sections.forEach { section ->
                            val asList = section.items.isNotEmpty() && section.items.all { it is SongItem }
                            val previewCount = if (asList) 3 else 4
                            val preview = if (data.genreTitle == null) section.items else section.items.take(previewCount)
                            if (section.items.isNotEmpty() || section.endpoint != null || section.continuation != null) {
                                item(span = { GridItemSpan(maxLineSpan) }, key = "heading:${section.id}", contentType = "heading") {
                                    GenreSectionHeading(
                                        title = section.title ?: stringResource(R.string.explore),
                                        onSeeAll = if (section.items.size > preview.size || section.endpoint != null || section.continuation != null) {
                                            { onOpenSection(section.id) }
                                        } else null,
                                    )
                                }
                                genreItems(section.id, preview, mediaItem)
                            }
                        }
                        item(span = { GridItemSpan(maxLineSpan) }, key = "feed_status", contentType = "status") { GenreSourceStatus(data.feed, onLoadSource) }
                        if (data.genreTitle != null) {
                            GenreTab.entries.filter { it != GenreTab.ALL }.forEach { tab ->
                                val additional = data.additionalItems(tab)
                                val sources = data.sources.filter { it.source.tab == tab }
                                if (additional.isNotEmpty() || sources.any { it.loading || it.failed || it.continuation != null }) {
                                    item(span = { GridItemSpan(maxLineSpan) }, key = "more_heading:${tab.name}", contentType = "heading") {
                                        GenreSectionHeading(
                                            title = stringResource(when (tab) {
                                                GenreTab.SONGS -> R.string.genre_more_songs
                                                GenreTab.PLAYLISTS -> R.string.genre_more_playlists
                                                GenreTab.ALBUMS -> R.string.genre_more_albums
                                                GenreTab.ALL -> R.string.explore
                                            }),
                                            onSeeAll = { onTabSelected(tab) },
                                        )
                                    }
                                    genreItems("more:${tab.name}", additional.take(if (tab == GenreTab.SONGS) 3 else 4), mediaItem)
                                    sources.forEach { source ->
                                        item(span = { GridItemSpan(maxLineSpan) }, key = "status:${source.source.name}", contentType = "status") { GenreSourceStatus(source, onLoadSource, showLoadMore = false) }
                                    }
                                }
                            }
                        }
                        if (state is GenreBrowseScreenState.Empty) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty", contentType = "status") { GenreEmptyMessage() }
                        }
                    }
                    else -> {
                        val categoryItems = data.categoryItems(data.selectedTab)
                        genreItems("category", categoryItems, mediaItem)
                        val sources = data.sources.filter { it.source == GenreSource.FEED || it.source.tab == data.selectedTab }
                        sources.forEach { source ->
                            item(span = { GridItemSpan(maxLineSpan) }, key = "status:${source.source.name}", contentType = "status") { GenreSourceStatus(source, onLoadSource) }
                        }
                        if (categoryItems.isEmpty() && sources.none { it.loading || it.failed }) {
                            item(span = { GridItemSpan(maxLineSpan) }, key = "empty", contentType = "status") { GenreEmptyMessage() }
                        }
                    }
                }
            }
        }
    }
}

private fun LazyGridScope.genreItems(
    keyPrefix: String,
    items: List<YTItem>,
    mediaItem: @Composable (YTItem, Boolean) -> Unit,
) {
    items(
        items = items,
        key = { "$keyPrefix:${it.genreKey()}" },
        span = { GridItemSpan(if (it is SongItem) maxLineSpan else 1) },
        contentType = { if (it is SongItem) "song" else "media" },
    ) { mediaItem(it, it is SongItem) }
}

@Composable
private fun GenreSectionHeading(title: String, onSeeAll: (() -> Unit)? = null) {
    val modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)
    if (LocalDensity.current.fontScale >= 1.5f) {
        Column(modifier) {
            Text(title, style = MaterialTheme.typography.titleLargeEmphasized, modifier = Modifier.semantics { heading() })
            if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
        }
    } else {
        Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                modifier = Modifier.weight(1f).semantics { heading() },
            )
            if (onSeeAll != null) TextButton(onClick = onSeeAll) { Text(stringResource(R.string.see_all)) }
        }
    }
}

@Composable
private fun GenreSourceStatus(source: GenreSourceState, onLoad: (GenreSource) -> Unit, showLoadMore: Boolean = true) {
    GenreLoadStatus(
        loading = source.loading,
        failed = source.failed,
        canLoadMore = showLoadMore && source.continuation != null,
        label = stringResource(source.source.titleRes),
        onLoad = { onLoad(source.source) },
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun GenreLoadStatus(loading: Boolean, failed: Boolean, canLoadMore: Boolean, label: String, onLoad: () -> Unit) {
    if (loading || failed || canLoadMore) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LoadingIndicator(modifier = Modifier.size(40.dp))
                    Text(stringResource(R.string.genre_loading_source, label), style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                if (failed) Text(stringResource(R.string.genre_source_error, label), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onLoad) {
                    Text(if (failed) stringResource(R.string.retry) else stringResource(R.string.genre_load_more, label))
                }
            }
        }
    }
}

@Composable
private fun GenreEmptyMessage() {
    Text(
        stringResource(R.string.no_results_found),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth().padding(24.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GenreMediaItem(
    item: YTItem,
    asList: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onClick: () -> Unit,
    onMenu: () -> Unit,
) {
    val hasMenu = item !is PodcastItem && item !is EpisodeItem
    val modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = if (hasMenu) onMenu else null)
    if (asList) {
        YouTubeListItem(
            item = item,
            isActive = isActive,
            isPlaying = isPlaying,
            isSwipeable = false,
            modifier = modifier.heightIn(min = ListItemHeight * LocalDensity.current.fontScale.coerceAtLeast(1f)),
            trailingContent = {
                IconButton(onClick = onMenu) {
                    Icon(painterResource(R.drawable.more_vert), contentDescription = stringResource(R.string.options_label))
                }
            },
        )
    } else {
        YouTubeGridItem(
            item = item,
            isActive = isActive,
            isPlaying = isPlaying,
            coroutineScope = coroutineScope,
            fillMaxWidth = true,
            modifier = modifier.padding(horizontal = 4.dp),
        )
    }
}
