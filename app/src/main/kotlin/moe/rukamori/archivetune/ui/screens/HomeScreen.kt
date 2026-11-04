/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ContainedLoadingIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.QuickPicks
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeEvent
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.home.HomeUiState
import moe.rukamori.archivetune.home.hasVisibleContent
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.viewmodels.HomeViewModel

private val HomeFeedMaxWidth = 1_200.dp
private val HomeSectionSpacing = 28.dp

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    navController: NavController,
    viewModel: HomeViewModel,
    headerScrollConnection: NestedScrollConnection? = null,
) {
    val playerConnection = LocalPlayerConnection.current ?: return
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    val screenState by viewModel.screenState.collectAsStateWithLifecycle()
    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel, playerConnection, navController) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenPodcast -> navController.navigate("podcast/${Uri.encode(event.browseId)}")
                is HomeEvent.PlayPodcastEpisode -> {
                    playerConnection.playQueue(
                        ListQueue(
                            title = event.request.title,
                            items = event.request.items.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.request.startIndex,
                        ),
                    )
                }
            }
        }
    }

    val lazyListState = rememberLazyListState()
    val forgottenFavoritesListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
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

    val successState = screenState as? HomeScreenState.Success
    val uiState = successState?.uiState
    val selectedChip = uiState?.selectedChip

    LaunchedEffect(uiState?.homePage?.continuation) {
        val continuation = uiState?.homePage?.continuation ?: return@LaunchedEffect
        snapshotFlow {
            val layoutInfo = lazyListState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index
            lastVisibleIndex != null && lastVisibleIndex >= layoutInfo.totalItemsCount - 3
        }.collect { shouldLoadMore ->
            if (shouldLoadMore) {
                viewModel.onAction(HomeAction.LoadMore(continuation))
            }
        }
    }

    LaunchedEffect(uiState?.forgottenFavorites) {
        if (uiState != null) {
            forgottenFavoritesListState.scrollToItem(0)
        }
    }

    if (selectedChip != null) {
        BackHandler {
            viewModel.onAction(HomeAction.SelectChip(selectedChip))
        }
    }

    LaunchedEffect(uiState?.showCategoryChips, selectedChip) {
        if (uiState?.showCategoryChips == false && selectedChip != null) {
            viewModel.onAction(HomeAction.SelectChip(selectedChip))
        }
    }

    // Attach the shell's floating-header connection inside this screen (Step 2b) so
    // Home's scroll/fling writes Home's own header state and can't leak into another
    // route's header. Bubbling reaches this ancestor Box before any shell connection.
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .then(
                    if (headerScrollConnection != null) {
                        Modifier.nestedScroll(headerScrollConnection)
                    } else {
                        Modifier
                    },
                ),
    ) {
        when (val state = screenState) {
            HomeScreenState.Loading -> {
                HomeStatePane(
                    iconResId = null,
                    messageResId = null,
                    showLoadingIndicator = true,
                )
            }

            HomeScreenState.Empty -> {
                HomeStatePane(
                    iconResId = R.drawable.music_note,
                    messageResId = R.string.no_results_found,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                )
            }

            is HomeScreenState.Error -> {
                HomeStatePane(
                    iconResId = R.drawable.info,
                    messageResId = state.messageResId,
                    actionResId = R.string.retry,
                    onAction = { viewModel.onAction(HomeAction.Refresh) },
                )
            }

            is HomeScreenState.Success -> {
                HomeContent(
                    uiState = state.uiState,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    haptic = haptic,
                    scope = scope,
                    lazyListState = lazyListState,
                    forgottenFavoritesListState = forgottenFavoritesListState,
                    onAction = viewModel::onAction,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HomeStatePane(
    @DrawableRes iconResId: Int?,
    @StringRes messageResId: Int?,
    modifier: Modifier = Modifier,
    @StringRes actionResId: Int? = null,
    showLoadingIndicator: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .padding(LocalPlayerAwareWindowInsets.current.asPaddingValues()),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp),
        ) {
            if (showLoadingIndicator) {
                ContainedLoadingIndicator()
            } else {
                iconResId?.let {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(112.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                    shape = MaterialShapes.Cookie9Sided.toShape(),
                                ),
                    ) {
                        Icon(
                            painter = painterResource(it),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(48.dp),
                        )
                    }
                }
                messageResId?.let {
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = stringResource(it),
                        style = MaterialTheme.typography.headlineSmallEmphasized,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center,
                    )
                }
                if (actionResId != null && onAction != null) {
                    Spacer(Modifier.height(24.dp))
                    Button(
                        onClick = onAction,
                        shapes = ButtonDefaults.shapes(),
                    ) {
                        Text(stringResource(actionResId))
                    }
                }
            }
        }
    }
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
private fun HomeContent(
    uiState: HomeUiState,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    lazyListState: LazyListState,
    forgottenFavoritesListState: LazyListState,
    onAction: (HomeAction) -> Unit,
    modifier: Modifier = Modifier,
) {
    val remoteQuickPicks =
        uiState
            .takeIf { it.quickPicksMode == QuickPicks.QUICK_PICKS }
            ?.remoteQuickPicks
    val primaryGlow = MaterialTheme.colorScheme.primaryContainer
    val tertiaryGlow = MaterialTheme.colorScheme.tertiaryContainer
    Box(modifier = modifier.fillMaxSize()) {
        if (uiState.showTonalBackdrop) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(520.dp)
                        .align(Alignment.TopCenter)
                        .drawWithCache {
                            val primaryBrush =
                                Brush.radialGradient(
                                    0f to primaryGlow.copy(alpha = 0.55f),
                                    1f to Color.Transparent,
                                    center = Offset(size.width * 0.12f, size.height * 0.16f),
                                    radius = size.height * 0.7f,
                                )
                            val tertiaryBrush =
                                Brush.radialGradient(
                                    0f to tertiaryGlow.copy(alpha = 0.42f),
                                    1f to Color.Transparent,
                                    center = Offset(size.width * 0.94f, size.height * 0.34f),
                                    radius = size.height * 0.5f,
                                )
                            onDrawBehind {
                                drawRect(primaryBrush)
                                drawRect(tertiaryBrush)
                            }
                        },
            )
        }

        ExpressivePullToRefreshBox(
            isRefreshing = uiState.isRefreshing,
            onRefresh = { onAction(HomeAction.Refresh) },
            modifier = Modifier.fillMaxSize(),
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    state = lazyListState,
                    contentPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues(),
                    modifier =
                        Modifier
                            .widthIn(max = HomeFeedMaxWidth)
                            .fillMaxWidth()
                            .align(Alignment.TopCenter),
                ) {
                    if (uiState.showCategoryChips) {
                        item(
                            key = "home_category_chips",
                            contentType = "category_chips",
                        ) {
                            HomeCategoryChips(
                                chips = uiState.homePage?.chips.orEmpty(),
                                selectedChip = uiState.selectedChip,
                                onChipSelected = { onAction(HomeAction.SelectChip(it)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (remoteQuickPicks?.items?.isNotEmpty() == true) {
                        item(
                            key = "home_remote_quick_picks_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.quick_picks),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_remote_quick_picks",
                            contentType = "quick_picks",
                        ) {
                            RemoteQuickPicksSection(
                                section = remoteQuickPicks,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                displayMode = uiState.quickPicksDisplayMode,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    } else if (
                        uiState.quickPicksMode == QuickPicks.LAST_LISTEN &&
                            uiState.quickPicks.isNotEmpty()
                    ) {
                        item(
                            key = "home_quick_picks_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.quick_picks),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_quick_picks",
                            contentType = "quick_picks",
                        ) {
                            QuickPicksSection(
                                quickPicks = uiState.quickPicks,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                displayMode = uiState.quickPicksDisplayMode,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    uiState.communitySection?.takeIf { section -> section.featuredCards.isNotEmpty() }?.let { section ->
                        sectionSpacer("community")
                        item(
                            key = "home_community_header",
                            contentType = "section_header",
                        ) {
                            val playAll =
                                remember(section.playEndpoint, playerConnection) {
                                    section.playEndpoint?.let { endpoint ->
                                        { playerConnection.playQueue(YouTubeQueue.playlist(endpoint)) }
                                    }
                                }
                            HomePageSectionTitle(
                                section = section,
                                navController = navController,
                                onPlayAll = playAll,
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_community",
                            contentType = "featured_playlist_shelf",
                        ) {
                            HomePageSectionContent(
                                section = section,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                onOpenRemoteItem = { itemId -> onAction(HomeAction.OpenRemoteItem(itemId)) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (uiState.speedDialItems.isNotEmpty()) {
                        sectionSpacer("speed_dial")
                        item(
                            key = "home_speed_dial_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.speed_dial),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_speed_dial",
                            contentType = "speed_dial",
                        ) {
                            SpeedDialSection(
                                speedDialItems = uiState.speedDialItems,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (uiState.keepListening.isNotEmpty()) {
                        sectionSpacer("keep_listening")
                        item(
                            key = "home_keep_listening_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.keep_listening),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_keep_listening",
                            contentType = "media_shelf",
                        ) {
                            KeepListeningSection(
                                keepListening = uiState.keepListening,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (uiState.accountPlaylists.isNotEmpty()) {
                        sectionSpacer("account_playlists")
                        item(
                            key = "home_account_playlists",
                            contentType = "media_shelf",
                        ) {
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                shape = MaterialTheme.shapes.extraLarge,
                                modifier =
                                    Modifier
                                        .animateItem()
                                        .padding(horizontal = 12.dp)
                                        .fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
                                    AccountPlaylistsTitle(
                                        accountName = uiState.accountName,
                                        accountImageUrl = uiState.accountImageUrl,
                                        onClick = { navController.navigate("account") },
                                    )
                                    AccountPlaylistsSection(
                                        accountPlaylists = uiState.accountPlaylists,
                                        mediaMetadata = mediaMetadata,
                                        isPlaying = isPlaying,
                                        navController = navController,
                                        playerConnection = playerConnection,
                                        menuState = menuState,
                                        haptic = haptic,
                                        scope = scope,
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.forgottenFavorites.isNotEmpty()) {
                        sectionSpacer("forgotten_favorites")
                        item(
                            key = "home_forgotten_favorites_header",
                            contentType = "section_header",
                        ) {
                            HomeSectionHeader(
                                title = stringResource(R.string.forgotten_favorites),
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item(
                            key = "home_forgotten_favorites",
                            contentType = "song_shelf",
                        ) {
                            ForgottenFavoritesSection(
                                forgottenFavorites = uiState.forgottenFavorites,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                state = forgottenFavoritesListState,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (uiState.similarRecommendations.isNotEmpty()) {
                        sectionSpacer("similar_recommendations")
                        item(
                            key = "home_similar_recommendations",
                            contentType = "discovery_decks",
                        ) {
                            SimilarRecommendationsSection(
                                recommendations = uiState.similarRecommendations,
                                mediaMetadata = mediaMetadata,
                                isPlaying = isPlaying,
                                navController = navController,
                                playerConnection = playerConnection,
                                menuState = menuState,
                                haptic = haptic,
                                scope = scope,
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    uiState.homePage
                        ?.sections
                        .orEmpty()
                        .filter(HomePage.Section::hasVisibleContent)
                        .forEachIndexed { index, section ->
                            val sectionKey = "${section.endpoint?.browseId ?: section.title}_$index"
                            sectionSpacer("remote_$sectionKey")
                            item(
                                key = "home_remote_header_$sectionKey",
                                contentType = "section_header",
                            ) {
                                val playAll =
                                    remember(section.playEndpoint, playerConnection) {
                                        section.playEndpoint?.let { endpoint ->
                                            { playerConnection.playQueue(YouTubeQueue.playlist(endpoint)) }
                                        }
                                    }
                                HomePageSectionTitle(
                                    section = section,
                                    navController = navController,
                                    onPlayAll = playAll,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                            item(
                                key = "home_remote_$sectionKey",
                                contentType = "media_shelf",
                            ) {
                                HomePageSectionContent(
                                    section = section,
                                    mediaMetadata = mediaMetadata,
                                    isPlaying = isPlaying,
                                    navController = navController,
                                    playerConnection = playerConnection,
                                    menuState = menuState,
                                    haptic = haptic,
                                    scope = scope,
                                    onOpenRemoteItem = { itemId -> onAction(HomeAction.OpenRemoteItem(itemId)) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }

                    if (uiState.isLoadingMore) {
                        item(
                            key = "home_loading_more",
                            contentType = "loading",
                        ) {
                            Box(
                                contentAlignment = Alignment.Center,
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp)
                                        .animateItem(),
                            ) {
                                LoadingIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.sectionSpacer(key: String) {
    item(
        key = "home_section_spacer_$key",
        contentType = "section_spacer",
    ) {
        Spacer(Modifier.height(HomeSectionSpacing))
    }
}
