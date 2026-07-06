/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.search

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.search.SearchDiscoveryUiModel
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.screens.MoodAndGenresButton
import moe.rukamori.archivetune.ui.screens.MoodAndGenresButtonHeight
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
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer
    val lazyListState = rememberLazyListState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val scrollToTop =
        backStackEntry
            ?.savedStateHandle
            ?.getStateFlow("scrollToTop", false)
            ?.collectAsStateWithLifecycle()

    LaunchedEffect(scrollToTop?.value) {
        if (scrollToTop?.value == true) {
            lazyListState.animateScrollToItem(0)
            backStackEntry?.savedStateHandle?.set("scrollToTop", false)
        }
    }

    Box(
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

        LazyColumn(
            state = lazyListState,
            contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(
                key = "search_field",
                contentType = "search_field",
            ) {
                SearchEntryField(
                    onClick = onSearchClick,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .animateItem(),
                )
            }

            item(
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
                        key = "search_loading",
                        contentType = "search_loading",
                    ) {
                        SearchDiscoveryLoading(modifier = Modifier.animateItem())
                    }
                }

                SearchDiscoveryScreenState.Empty -> {
                    item(
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
                                key = "search_explore_moods_title",
                                contentType = "section_title",
                            ) {
                                NavigationTitle(
                                    title = stringResource(R.string.mood_and_genres),
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "search_explore_moods",
                                contentType = "mood_genres_grid",
                            ) {
                                SearchMoodAndGenresGrid(
                                    data = currentState.data,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                        SearchDiscoveryTab.SUGGESTIONS -> {
                            item(
                                key = "search_suggestions_songs",
                                contentType = "suggestion_songs",
                            ) {
                                SuggestedSongsSection(
                                    songs = currentState.data.suggestedSongs,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                key = "search_suggestions_artists",
                                contentType = "suggestion_artists",
                            ) {
                                SuggestedArtistsSection(
                                    artists = currentState.data.suggestedArtists,
                                    navController = navController,
                                    modifier = Modifier.animateItem(),
                                )
                            }

                            item(
                                key = "search_suggestions_albums",
                                contentType = "suggestion_albums",
                            ) {
                                TrendingAlbumsSection(
                                    albums = currentState.data.trendingAlbums,
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
private fun SearchMoodAndGenresGrid(
    data: SearchDiscoveryUiModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier =
            modifier
                .fillMaxWidth(),
    ) {
        val columnCount = (maxWidth.value / MoodAndGenresMinCellWidth.value).toInt().coerceAtLeast(1)
        val rowCount = ((data.moodAndGenres.size + columnCount - 1) / columnCount).coerceAtLeast(1)

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = MoodAndGenresMinCellWidth),
            contentPadding = PaddingValues(6.dp),
            userScrollEnabled = false,
            verticalArrangement = Arrangement.spacedBy(0.dp),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height((MoodAndGenresButtonHeight + 12.dp) * rowCount + 12.dp),
        ) {
            items(
                items = data.moodAndGenres,
                key = { item -> "${item.title}:${item.endpoint.browseId}:${item.endpoint.params}" },
                contentType = { "mood_genres_item" },
            ) { item ->
                MoodAndGenresButton(
                    title = item.title,
                    stripeColor = item.stripeColor,
                    endpoint = item.endpoint,
                    onClick = {
                        navController.navigate("youtube_browse/${item.endpoint.browseId}?params=${item.endpoint.params}")
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(6.dp),
                )
            }
        }
    }
}

private val MoodAndGenresMinCellWidth = 180.dp

private val SuggestedSongGroupHorizontalPadding = 12.dp
private val SuggestedSongGroupVerticalPadding = 2.dp
private val SuggestedSongGroupItemSpacing = 2.dp
private val SuggestedSongGroupLargeCorner = 28.dp
private val SuggestedSongGroupSmallCorner = 6.dp

private fun segmentedSuggestedSongShape(
    index: Int,
    count: Int,
): Shape {
    val large = SuggestedSongGroupLargeCorner
    val small = SuggestedSongGroupSmallCorner
    return when {
        count <= 1 -> {
            RoundedCornerShape(large)
        }

        index == 0 -> {
            RoundedCornerShape(
                topStart = large,
                topEnd = large,
                bottomEnd = small,
                bottomStart = small,
            )
        }

        index == count - 1 -> {
            RoundedCornerShape(
                topStart = small,
                topEnd = small,
                bottomEnd = large,
                bottomStart = large,
            )
        }

        else -> {
            RoundedCornerShape(small)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedSongsSection(
    songs: List<SongItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (songs.isEmpty()) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    SectionContainer(
        title = stringResource(R.string.stats_unique_songs),
        modifier = modifier,
    ) {
        val visibleSongs = remember(songs) { songs.take(6) }

        Column(
            verticalArrangement = Arrangement.spacedBy(SuggestedSongGroupItemSpacing),
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = SuggestedSongGroupHorizontalPadding,
                        vertical = SuggestedSongGroupVerticalPadding,
                    ),
        ) {
            visibleSongs.forEachIndexed { index, song ->
                Card(
                    shape = segmentedSuggestedSongShape(index = index, count = visibleSongs.size),
                    colors =
                        CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                        ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = {
                                    if (song.id == mediaMetadata?.id) {
                                        playerConnection.player.togglePlayPause()
                                    } else {
                                        playerConnection.playQueue(
                                            YouTubeQueue(
                                                endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                                                preloadItem = song.toMediaMetadata(),
                                            ),
                                        )
                                    }
                                },
                                onLongClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
                                        )
                                    }
                                },
                            ),
                ) {
                    YouTubeListItem(
                        item = song,
                        albumIndex = index + 1,
                        viewCountText = song.viewCountText,
                        isActive = song.id == mediaMetadata?.id,
                        isPlaying = isPlaying,
                        isSwipeable = false,
                        trailingContent = {
                            YouTubeSongMenuButton(song = song, navController = navController)
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrendingAlbumsSection(
    albums: List<AlbumItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (albums.isEmpty()) return

    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    NavigationTitle(
        title = stringResource(R.string.top_albums),
        modifier = modifier,
    )
    LazyRow(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        items(
            items = albums,
            key = { album -> album.id },
            contentType = { "trending_album" },
        ) { album ->
            YouTubeGridItem(
                item = album,
                isActive = mediaMetadata?.album?.id == album.id,
                isPlaying = isPlaying,
                coroutineScope = coroutineScope,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("album/${album.id}")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeAlbumMenu(
                                        albumItem = album,
                                        navController = navController,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ).animateItem(),
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SuggestedArtistsSection(
    artists: List<ArtistItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) return

    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    NavigationTitle(
        title = stringResource(R.string.stats_unique_artists),
        modifier = modifier,
    )
    LazyRow(
        contentPadding = LocalPlayerAwareWindowInsets.current.only(WindowInsetsSides.Horizontal).asPaddingValues(),
    ) {
        items(
            items = artists,
            key = { artist -> artist.id },
            contentType = { "trending_artist" },
        ) { artist ->
            YouTubeGridItem(
                item = artist,
                modifier =
                    Modifier
                        .combinedClickable(
                            onClick = {
                                navController.navigate("artist/${artist.id}")
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    YouTubeArtistMenu(
                                        artist = artist,
                                        onDismiss = menuState::dismiss,
                                    )
                                }
                            },
                        ).animateItem(),
            )
        }
    }
}

@Composable
private fun SectionContainer(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    NavigationTitle(
        title = title,
        modifier = modifier,
    )
    content()
}

@Composable
private fun YouTubeSongMenuButton(
    song: SongItem,
    navController: NavController,
) {
    val menuState = LocalMenuState.current
    IconButton(
        onClick = {
            menuState.show {
                YouTubeSongMenu(
                    song = song,
                    navController = navController,
                    onDismiss = menuState::dismiss,
                )
            }
        },
    ) {
        Icon(
            painter = painterResource(R.drawable.more_vert),
            contentDescription = null,
        )
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
