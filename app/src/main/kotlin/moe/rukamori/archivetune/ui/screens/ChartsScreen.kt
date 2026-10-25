/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.NavigationTitle
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.component.shimmer.GridItemPlaceHolder
import moe.rukamori.archivetune.ui.component.shimmer.ShimmerHost
import moe.rukamori.archivetune.ui.component.shimmer.TextPlaceholder
import moe.rukamori.archivetune.ui.component.tvCombinedClickable
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.SnapLayoutInfoProvider
import moe.rukamori.archivetune.ui.utils.backToMain
import moe.rukamori.archivetune.viewmodels.ChartsViewModel

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ChartsScreen(
    navController: NavController,
    viewModel: ChartsViewModel = hiltViewModel(),
) {
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val playerConnection = LocalPlayerConnection.current ?: return
    val isPlaying by playerConnection.isPlaying.collectAsState()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsState()

    val chartsPage by viewModel.chartsPage.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        if (chartsPage == null) {
            viewModel.loadCharts()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.charts)) },
                navigationIcon = {
                    IconButton(
                        onClick = { navController.navigateUp() },
                        onLongClick = { navController.backToMain() },
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.arrow_back),
                            contentDescription = null,
                        )
                    }
                },
            )
        },
    ) { paddingValues ->
        BoxWithConstraints(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
        ) {
            if (isLoading || chartsPage == null) {
                ShimmerHost(
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                    ) {
                        TextPlaceholder(
                            height = 36.dp,
                            modifier =
                                Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(0.5f),
                        )
                        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                            val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                            val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                            LazyHorizontalGrid(
                                rows = GridCells.Fixed(4),
                                contentPadding = PaddingValues(start = 4.dp),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .height(ListItemHeight * 4),
                            ) {
                                items(4) {
                                    Row(
                                        modifier =
                                            Modifier
                                                .width(horizontalLazyGridItemWidth)
                                                .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier =
                                                Modifier
                                                    .size(ListItemHeight - 16.dp)
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(MaterialTheme.colorScheme.onSurface),
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Column(
                                            modifier = Modifier.fillMaxHeight(),
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .height(16.dp)
                                                        .width(120.dp)
                                                        .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Box(
                                                modifier =
                                                    Modifier
                                                        .height(12.dp)
                                                        .width(80.dp)
                                                        .background(MaterialTheme.colorScheme.onSurface),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        TextPlaceholder(
                            height = 36.dp,
                            modifier =
                                Modifier
                                    .padding(vertical = 12.dp, horizontal = 12.dp)
                                    .width(250.dp),
                        )
                        Row {
                            repeat(2) {
                                GridItemPlaceHolder()
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    state = lazyListState,
                    contentPadding =
                        LocalPlayerAwareWindowInsets.current
                            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
                            .asPaddingValues(),
                ) {
                    chartsPage?.sections?.filter { it.title != "Top music videos" }?.forEach { section ->
                        item {
                            NavigationTitle(
                                title =
                                    when (section.title) {
                                        "Trending" -> stringResource(R.string.trending)
                                        else -> section.title ?: stringResource(R.string.charts)
                                    },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item {
                            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                                val horizontalLazyGridItemWidthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                                val horizontalLazyGridItemWidth = maxWidth * horizontalLazyGridItemWidthFactor

                                val lazyGridState = rememberLazyGridState()
                                val snapLayoutInfoProvider =
                                    remember(lazyGridState) {
                                        SnapLayoutInfoProvider(
                                            lazyGridState = lazyGridState,
                                            positionInLayout = { layoutSize, itemSize ->
                                                (layoutSize * horizontalLazyGridItemWidthFactor / 2f - itemSize / 2f)
                                            },
                                        )
                                    }

                                LazyHorizontalGrid(
                                    state = lazyGridState,
                                    rows = GridCells.Fixed(4),
                                    flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                                    contentPadding =
                                        WindowInsets.systemBars
                                            .only(WindowInsetsSides.Horizontal)
                                            .asPaddingValues(),
                                    modifier =
                                        Modifier
                                            .fillMaxWidth()
                                            .height(ListItemHeight * 4)
                                            .animateItem(),
                                ) {
                                    items(
                                        items = section.items.filterIsInstance<SongItem>().distinctBy { it.id },
                                        key = { it.id },
                                    ) { song ->
                                        YouTubeListItem(
                                            item = song,
                                            isActive = song.id == mediaMetadata?.id,
                                            isPlaying = isPlaying,
                                            isSwipeable = false,
                                            trailingContent = {
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
                                            },
                                            modifier =
                                                Modifier
                                                    .width(horizontalLazyGridItemWidth)
                                                    .tvCombinedClickable(
                                                        onClick = {
                                                            if (song.id == mediaMetadata?.id) {
                                                                playerConnection.player.togglePlayPause()
                                                            } else {
                                                                playerConnection.playQueue(
                                                                    YouTubeQueue(
                                                                        endpoint = WatchEndpoint(videoId = song.id),
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
                                        )
                                    }
                                }
                            }
                        }
                    }

                    chartsPage?.sections?.find { it.title == "Top music videos" }?.let { topVideosSection ->
                        item {
                            NavigationTitle(
                                title = stringResource(R.string.top_music_videos),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item {
                            LazyRow(
                                contentPadding =
                                    WindowInsets.systemBars
                                        .only(WindowInsetsSides.Horizontal)
                                        .asPaddingValues(),
                                modifier = Modifier.animateItem(),
                            ) {
                                items(
                                    items = topVideosSection.items.filterIsInstance<SongItem>().distinctBy { it.id },
                                    key = { it.id },
                                ) { video ->
                                    YouTubeGridItem(
                                        item = video,
                                        isActive = video.id == mediaMetadata?.id,
                                        isPlaying = isPlaying,
                                        coroutineScope = coroutineScope,
                                        modifier =
                                            Modifier
                                                .tvCombinedClickable(
                                                    onClick = {
                                                        if (video.id == mediaMetadata?.id) {
                                                            playerConnection.player.togglePlayPause()
                                                        } else {
                                                            playerConnection.playQueue(
                                                                YouTubeQueue(
                                                                    endpoint = WatchEndpoint(videoId = video.id),
                                                                    preloadItem = video.toMediaMetadata(),
                                                                ),
                                                            )
                                                        }
                                                    },
                                                    onLongClick = {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        menuState.show {
                                                            YouTubeSongMenu(
                                                                song = video,
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
                    }
                }
            }
        }
    }
}
