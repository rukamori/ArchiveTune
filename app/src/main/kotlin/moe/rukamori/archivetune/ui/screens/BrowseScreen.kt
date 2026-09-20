/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.browse.BrowseAction
import moe.rukamori.archivetune.browse.BrowseEvent
import moe.rukamori.archivetune.browse.BrowseScreenState
import moe.rukamori.archivetune.browse.BrowseUiState
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MediaDetailStatePanel
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.BrowseViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BrowseScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val unknownErrorMessage = stringResource(R.string.error_unknown)
    val coroutineScope = rememberCoroutineScope()
    val onBack: () -> Unit = remember(navController) { { navController.navigateUp() } }
    val onBackToMain: () -> Unit = remember(navController) { { navController.backToMain() } }
    val onRetry = remember(viewModel) { { viewModel.onAction(BrowseAction.Retry) } }
    val onLoadMore = remember(viewModel) { { viewModel.onAction(BrowseAction.LoadMore) } }
    val onItemClick =
        remember(viewModel) {
            { item: YTItem -> viewModel.onAction(BrowseAction.OpenItem(item.id)) }
        }
    val onItemLongClick =
        remember(viewModel) {
            { item: YTItem -> viewModel.onAction(BrowseAction.OpenItemMenu(item.id)) }
        }

    LaunchedEffect(
        viewModel,
        navController,
        playerConnection,
        menuState,
        coroutineScope,
        snackbarHostState,
        unknownErrorMessage,
    ) {
        viewModel.events.collect { event ->
            when (event) {
                is BrowseEvent.OpenAlbum -> navController.navigate("album/${event.browseId}")
                is BrowseEvent.OpenPlaylist -> navController.navigate("online_playlist/${event.playlistId}")
                is BrowseEvent.OpenArtist -> navController.navigate("artist/${event.browseId}")
                is BrowseEvent.OpenPodcast -> navController.navigate("podcast/${android.net.Uri.encode(event.browseId)}")
                is BrowseEvent.PlayEpisode -> {
                    playerConnection.playQueue(
                        ListQueue(
                            title = event.episode.podcast?.name ?: event.episode.title,
                            items = listOf(event.episode.toMediaItem()),
                        ),
                    )
                }

                is BrowseEvent.ShowItemMenu -> {
                    menuState.show {
                        when (val item = event.item) {
                            is AlbumItem -> {
                                YouTubeAlbumMenu(
                                    albumItem = item,
                                    navController = navController,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is PlaylistItem -> {
                                YouTubePlaylistMenu(
                                    playlist = item,
                                    coroutineScope = coroutineScope,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            is ArtistItem -> {
                                YouTubeArtistMenu(
                                    artist = item,
                                    onDismiss = menuState::dismiss,
                                )
                            }

                            else -> Unit
                        }
                    }
                }

                is BrowseEvent.ShowMessage -> {
                    val message =
                        when (event.messageResId) {
                            R.string.error_unknown -> unknownErrorMessage
                            else -> unknownErrorMessage
                        }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    BrowseScreenContent(
        state = state,
        isPlaying = isPlaying,
        snackbarHostState = snackbarHostState,
        scrollBehavior = scrollBehavior,
        coroutineScope = coroutineScope,
        onBack = onBack,
        onBackToMain = onBackToMain,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        onItemClick = onItemClick,
        onItemLongClick = onItemLongClick,
    )
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun BrowseScreenContent(
    state: BrowseScreenState,
    isPlaying: Boolean,
    snackbarHostState: SnackbarHostState,
    scrollBehavior: TopAppBarScrollBehavior,
    coroutineScope: CoroutineScope,
    onBack: () -> Unit,
    onBackToMain: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
) {
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection),
    ) {
        when (state) {
            BrowseScreenState.Loading -> BrowseLoadingContent()

            is BrowseScreenState.Empty -> {
                MediaDetailStatePanel(
                    title = stringResource(R.string.no_results_found),
                    description = stringResource(R.string.browse_empty_description),
                    iconRes = R.drawable.music_note,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is BrowseScreenState.Error -> {
                MediaDetailStatePanel(
                    title = stringResource(R.string.browse_load_failed),
                    description = stringResource(state.messageResId),
                    iconRes = R.drawable.error,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is BrowseScreenState.Success -> {
                BrowseSuccessContent(
                    uiState = state.uiState,
                    isPlaying = isPlaying,
                    coroutineScope = coroutineScope,
                    onLoadMore = onLoadMore,
                    onItemClick = onItemClick,
                    onItemLongClick = onItemLongClick,
                )
            }
        }

        TopAppBar(
            title = { Text(state.title()) },
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    onLongClick = onBackToMain,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_back),
                        contentDescription = stringResource(R.string.back_button_desc),
                    )
                }
            },
            scrollBehavior = scrollBehavior,
            modifier = Modifier.align(Alignment.TopCenter),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BrowseSuccessContent(
    uiState: BrowseUiState,
    isPlaying: Boolean,
    coroutineScope: CoroutineScope,
    onLoadMore: () -> Unit,
    onItemClick: (YTItem) -> Unit,
    onItemLongClick: (YTItem) -> Unit,
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(gridState, uiState.canLoadMore, uiState.isLoadingMore, uiState.items.size) {
        snapshotFlow {
            val layoutInfo = gridState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 && lastVisibleIndex >= layoutInfo.totalItemsCount - PaginationThreshold
        }.distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad && uiState.canLoadMore && !uiState.isLoadingMore }
            .collect { onLoadMore() }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        state = gridState,
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            items = uiState.items,
            key = YTItem::id,
            contentType = { item -> item::class },
        ) { item ->
            val supportsLongClick = item is AlbumItem || item is PlaylistItem || item is ArtistItem
            YouTubeGridItem(
                item = item,
                isPlaying = isPlaying,
                fillMaxWidth = true,
                coroutineScope = coroutineScope,
                modifier =
                    Modifier.combinedClickable(
                        onClick = { onItemClick(item) },
                        onLongClick = if (supportsLongClick) ({ onItemLongClick(item) }) else null,
                    ),
            )
        }

        if (uiState.isLoadingMore) {
            item(
                key = "browse_loading_more",
                contentType = "browse_loading_more",
                span = { GridItemSpan(maxLineSpan) },
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.padding(24.dp),
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun BrowseLoadingContent() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = GridThumbnailHeight + 24.dp),
        contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
        userScrollEnabled = false,
        modifier = Modifier.fillMaxSize(),
    ) {
        items(
            count = LoadingPlaceholderCount,
            key = { index -> "browse_placeholder_$index" },
            contentType = { "browse_placeholder" },
        ) {
            ShimmerHost {
                GridItemPlaceHolder(fillMaxWidth = true)
            }
        }
    }
}

@Composable
private fun BrowseScreenState.title(): String =
    when (this) {
        is BrowseScreenState.Success ->
            (
                uiState.title
                    ?: uiState.fallbackTitleResId?.let { titleResId -> stringResource(titleResId) }
            ).orEmpty()

        is BrowseScreenState.Empty ->
            (
                title
                    ?: fallbackTitleResId?.let { titleResId -> stringResource(titleResId) }
            ).orEmpty()

        BrowseScreenState.Loading,
        is BrowseScreenState.Error,
        -> ""
    }

private const val LoadingPlaceholderCount = 8
private const val PaginationThreshold = 4
