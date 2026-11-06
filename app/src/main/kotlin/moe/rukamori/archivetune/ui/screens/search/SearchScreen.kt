/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.staggeredgrid.LazyVerticalStaggeredGrid
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridCells
import androidx.compose.foundation.lazy.staggeredgrid.StaggeredGridItemSpan
import androidx.compose.foundation.lazy.staggeredgrid.itemsIndexed
import androidx.compose.foundation.lazy.staggeredgrid.rememberLazyStaggeredGridState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.flow.distinctUntilChanged
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.repository.moodAndGenresArtworkKey
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryScreenState
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryTab
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryViewModel

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    onSearchClick: () -> Unit,
    headerScrollConnection: NestedScrollConnection? = null,
    viewModel: SearchDiscoveryViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val artwork by viewModel.artwork.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current
    val layoutDirection = LocalLayoutDirection.current
    val playerPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer
    val gridState = rememberLazyStaggeredGridState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            gridState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    val genres = (state as? SearchDiscoveryScreenState.Success)?.data?.moodAndGenres.orEmpty()
    LaunchedEffect(gridState, genres, selectedTab, lifecycleOwner, viewModel) {
        val itemsByKey = genres.associateBy {
            "${it.title}:${it.endpoint.browseId}:${it.endpoint.params}"
        }
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            try {
                snapshotFlow { gridState.layoutInfo.visibleItemsInfo.map { it.key } }
                    .distinctUntilChanged()
                    .collect { keys ->
                        viewModel.setVisibleArtwork(
                            if (selectedTab == SearchDiscoveryTab.EXPLORE) {
                                keys.mapNotNull { itemsByKey[it]?.endpoint }
                            } else {
                                emptyList()
                            },
                        )
                    }
            } finally {
                viewModel.setVisibleArtwork(emptyList())
            }
        }
    }

    BoxWithConstraints(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    // Step 2b: attach the shell's floating-header connection here so Search's
                    // scroll/fling writes Search's own header state and can't leak elsewhere.
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        val startPadding = playerPadding.calculateStartPadding(layoutDirection) + 16.dp
        val endPadding = playerPadding.calculateEndPadding(layoutDirection) + 16.dp
        val availableWidth = maxWidth - startPadding - endPadding
        val columnCount =
            if (availableWidth < 320.dp) {
                1
            } else {
                ((availableWidth.value + 8f) / 168f).toInt().coerceAtLeast(2)
            }

        if (!disableBlur) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(430.dp)
                        .align(Alignment.TopCenter)
                        .drawWithCache {
                            val brush =
                                Brush.verticalGradient(
                                    0f to tonalStart.copy(alpha = 0.30f),
                                    0.42f to tonalMiddle.copy(alpha = 0.14f),
                                    1f to Color.Transparent,
                                )
                            onDrawBehind { drawRect(brush) }
                        },
            )
        }

        LazyVerticalStaggeredGrid(
            columns = StaggeredGridCells.Fixed(columnCount),
            state = gridState,
            contentPadding = PaddingValues(
                start = startPadding,
                top = playerPadding.calculateTopPadding(),
                end = endPadding,
                bottom = playerPadding.calculateBottomPadding(),
            ),
            verticalItemSpacing = 8.dp,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                span = StaggeredGridItemSpan.FullLine,
                key = "search_field",
                contentType = "search_field",
            ) {
                SearchEntryField(
                    onClick = onSearchClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .animateItem(),
                )
            }

            item(
                span = StaggeredGridItemSpan.FullLine,
                key = "search_tabs",
                contentType = "search_tabs",
            ) {
                SearchDiscoveryTabs(
                    selectedTab = selectedTab,
                    onTabSelected = viewModel::selectTab,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .animateItem(),
                )
            }

            when (val currentState = state) {
                SearchDiscoveryScreenState.Loading -> {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                        key = "search_loading",
                        contentType = "search_loading",
                    ) {
                        SearchDiscoveryLoading(modifier = Modifier.animateItem())
                    }
                }

                SearchDiscoveryScreenState.Empty -> {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                        key = "search_empty",
                        contentType = "search_empty",
                    ) {
                        SearchStateMessage(
                            message = stringResource(R.string.no_results_found),
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                is SearchDiscoveryScreenState.Error -> {
                    item(
                        span = StaggeredGridItemSpan.FullLine,
                        key = "search_error",
                        contentType = "search_error",
                    ) {
                        SearchStateMessage(
                            message = stringResource(currentState.messageResId),
                            action = {
                                Button(onClick = viewModel::retry) {
                                    Text(stringResource(R.string.retry_button))
                                }
                            },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }

                is SearchDiscoveryScreenState.Success -> {
                    when (selectedTab) {
                        SearchDiscoveryTab.EXPLORE -> {
                            item(
                                span = StaggeredGridItemSpan.FullLine,
                                key = "search_explore_moods_title",
                                contentType = "section_title",
                            ) {
                                Text(
                                    text = stringResource(R.string.mood_and_genres),
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(vertical = 12.dp).semantics { heading() }.animateItem(),
                                )
                            }
                            if (currentState.data.moodAndGenres.isEmpty()) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                    key = "search_explore_empty",
                                    contentType = "search_empty",
                                ) {
                                    SearchStateMessage(
                                        message = stringResource(R.string.no_results_found),
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                            itemsIndexed(
                                items = currentState.data.moodAndGenres,
                                key = { _, item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                                contentType = { _, _ -> "mood_genres_item" },
                            ) { index, item ->
                                SearchGenreTile(
                                    title = item.title,
                                    stripeColor = item.stripeColor,
                                    artwork = artwork[item.endpoint.moodAndGenresArtworkKey()],
                                    tileIndex = index,
                                    onClick = {
                                        navController.navigate(
                                            buildString {
                                                append("youtube_browse/${android.net.Uri.encode(item.endpoint.browseId)}")
                                                append("?genreTitle=${android.net.Uri.encode(item.title)}")
                                                item.endpoint.params?.let { append("&params=${android.net.Uri.encode(it)}") }
                                            },
                                        ) {
                                            launchSingleTop = true
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth().animateItem(),
                                )
                            }
                        }

                        SearchDiscoveryTab.SUGGESTIONS -> {
                            val data = currentState.data
                            if (data.suggestedSongs.isEmpty() && data.suggestedArtists.isEmpty() && data.trendingAlbums.isEmpty()) {
                                item(
                                    span = StaggeredGridItemSpan.FullLine,
                                    key = "search_suggestions_empty",
                                    contentType = "search_empty",
                                ) {
                                    SearchStateMessage(
                                        message = stringResource(R.string.no_results_found),
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }

                            item(
                                span = StaggeredGridItemSpan.FullLine,
                                key = "search_suggestions_songs",
                                contentType = "suggestion_songs",
                            ) {
                                SuggestedSongsShelf(
                                    songs = data.suggestedSongs,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                span = StaggeredGridItemSpan.FullLine,
                                key = "search_suggestions_artists",
                                contentType = "suggestion_artists",
                            ) {
                                SuggestedArtistsShelf(
                                    artists = data.suggestedArtists,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                span = StaggeredGridItemSpan.FullLine,
                                key = "search_suggestions_albums",
                                contentType = "suggestion_albums",
                            ) {
                                TrendingAlbumsShelf(
                                    albums = data.trendingAlbums,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchEntryField(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    SearchBar(
        inputField = {
            SearchBarDefaults.InputField(
                query = "",
                onQueryChange = { onClick() },
                onSearch = { onClick() },
                expanded = false,
                onExpandedChange = { expanded ->
                    if (expanded) onClick()
                },
                placeholder = {
                    Text(
                        text = stringResource(R.string.search_yt_music),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.search),
                        contentDescription = null,
                    )
                },
                trailingIcon = {
                    Icon(
                        painter = painterResource(R.drawable.language),
                        contentDescription = null,
                    )
                },
            )
        },
        expanded = false,
        onExpandedChange = { expanded ->
            if (expanded) onClick()
        },
        windowInsets = WindowInsets(0.dp, 0.dp, 0.dp, 0.dp),
        modifier = modifier.fillMaxWidth(),
    ) {}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchDiscoveryTabs(
    selectedTab: SearchDiscoveryTab,
    onTabSelected: (SearchDiscoveryTab) -> Unit,
    modifier: Modifier = Modifier,
) {
    val tabs = remember { SearchDiscoveryTab.entries }
    PrimaryTabRow(
        selectedTabIndex = tabs.indexOf(selectedTab),
        modifier = modifier,
        containerColor = Color.Transparent,
    ) {
        tabs.forEach { tab ->
            Tab(
                selected = selectedTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    Icon(
                        painter =
                            painterResource(
                                when (tab) {
                                    SearchDiscoveryTab.EXPLORE -> R.drawable.explore_outlined
                                    SearchDiscoveryTab.SUGGESTIONS -> R.drawable.auto_awesome
                                },
                            ),
                        contentDescription = null,
                    )
                },
                text = {
                    Text(
                        text =
                            stringResource(
                                when (tab) {
                                    SearchDiscoveryTab.EXPLORE -> R.string.explore
                                    SearchDiscoveryTab.SUGGESTIONS -> R.string.suggestions
                                },
                            ),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
private fun SearchDiscoveryLoading(modifier: Modifier = Modifier) {
    ShimmerHost(
        modifier = modifier.fillMaxWidth(),
    ) {
        TextPlaceholder(
            height = 56.dp,
            modifier =
                Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
        )
        TextPlaceholder(
            height = 28.dp,
            modifier =
                Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .width(180.dp),
        )
        repeat(6) {
            TextPlaceholder(
                height = 84.dp,
                modifier =
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                        .fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun SearchStateMessage(
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable RowScope.() -> Unit = {},
) {
    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.search_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
            )
            Text(
                text = message,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            androidx.compose.foundation.layout
                .Row(content = action)
        }
    }
}
