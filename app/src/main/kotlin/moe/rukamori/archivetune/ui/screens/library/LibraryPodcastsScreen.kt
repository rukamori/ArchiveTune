/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.podcast.PodcastLibraryAction
import moe.rukamori.archivetune.podcast.PodcastLibraryEvent
import moe.rukamori.archivetune.podcast.PodcastLibraryItemUiModel
import moe.rukamori.archivetune.podcast.PodcastLibraryScreenState
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.component.MenuSurfaceSection
import moe.rukamori.archivetune.ui.component.NewMenuItem
import moe.rukamori.archivetune.ui.screens.Screens
import moe.rukamori.archivetune.viewmodels.PodcastLibraryViewModel

@Composable
fun LibraryPodcastsScreen(
    navController: NavController,
    viewModel: PodcastLibraryViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val refreshState by viewModel.refreshState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current
    val snackbarHostState = remember { SnackbarHostState() }
    val unknownErrorMessage = stringResource(R.string.error_unknown)
    val loginRequiredMessage = stringResource(R.string.not_logged_in_youtube)
    val syncDisabledMessage = stringResource(R.string.sync_disabled)
    val noEpisodesMessage = stringResource(R.string.podcast_has_no_episodes)
    val onRefresh = remember(viewModel) { { viewModel.onAction(PodcastLibraryAction.Refresh) } }
    val onRefreshErrorShown = remember(viewModel) { { viewModel.onRefreshErrorShown() } }
    val openSearch = remember(navController) { { navController.navigate(Screens.Search.route) } }
    val onPodcastClick =
        remember(navController) {
            { browseId: String -> navController.navigate("podcast/${Uri.encode(browseId)}") }
        }
    val onPodcastPlay =
        remember(viewModel) {
            { browseId: String -> viewModel.onAction(PodcastLibraryAction.PlayPodcast(browseId)) }
        }
    val onPodcastRemove =
        remember(viewModel) {
            { browseId: String -> viewModel.onAction(PodcastLibraryAction.RemovePodcast(browseId)) }
        }
    val onPodcastMenu =
        remember(menuState, onPodcastRemove, haptic) {
            { podcast: PodcastLibraryItemUiModel, fromLongPress: Boolean ->
                if (fromLongPress) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    PodcastLibraryMenu(
                        onRemove = { onPodcastRemove(podcast.browseId) },
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    LaunchedEffect(
        viewModel,
        playerConnection,
        unknownErrorMessage,
        loginRequiredMessage,
        syncDisabledMessage,
        noEpisodesMessage,
    ) {
        viewModel.events.collect { event ->
            when (event) {
                is PodcastLibraryEvent.Play -> {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = event.request.title,
                            items = event.request.items.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.request.startIndex,
                        ),
                    )
                }

                is PodcastLibraryEvent.ShowMessage -> {
                    val message =
                        when (event.messageResId) {
                            R.string.not_logged_in_youtube -> loginRequiredMessage
                            R.string.sync_disabled -> syncDisabledMessage
                            R.string.podcast_has_no_episodes -> noEpisodesMessage
                            else -> unknownErrorMessage
                        }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LibraryRefreshContainer(
            state = refreshState,
            onRefresh = onRefresh,
            onErrorShown = onRefreshErrorShown,
            modifier = Modifier.fillMaxSize(),
            indicatorOffset = LibraryPullToRefreshIndicatorOffset,
        ) {
            LibraryPodcastsContent(
                state = state,
                bottomPadding = playerAwareBottomPadding,
                onRetry = onRefresh,
                onSearch = openSearch,
                onPodcastClick = onPodcastClick,
                onPodcastPlay = onPodcastPlay,
                onPodcastMenu = onPodcastMenu,
            )
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier =
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = playerAwareBottomPadding),
        )
    }
}

@Composable
private fun LibraryPodcastsContent(
    state: PodcastLibraryScreenState,
    bottomPadding: Dp,
    onRetry: () -> Unit,
    onSearch: () -> Unit,
    onPodcastClick: (String) -> Unit,
    onPodcastPlay: (String) -> Unit,
    onPodcastMenu: (PodcastLibraryItemUiModel, Boolean) -> Unit,
) {
    LazyColumn(
        contentPadding =
            PaddingValues(
                start = LibraryContentHorizontalPadding,
                top = LibraryHeaderContentPadding + 8.dp,
                end = LibraryContentHorizontalPadding,
                bottom = bottomPadding + 16.dp,
            ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        when (state) {
            PodcastLibraryScreenState.Loading -> {
                item(key = "podcasts_loading", contentType = "library_loading") {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .fillParentMaxHeight(0.7f),
                    ) {
                        LoadingIndicator(modifier = Modifier.size(64.dp))
                    }
                }
            }

            PodcastLibraryScreenState.Empty -> {
                item(key = "podcasts_empty", contentType = "library_empty_state") {
                    LibraryEmptyState(
                        iconRes = R.drawable.mic,
                        titleRes = R.string.library_empty_podcasts_title,
                        descriptionRes = R.string.library_empty_podcasts_desc,
                        actionLabelRes = R.string.search_yt_music,
                        onAction = onSearch,
                    )
                }
            }

            is PodcastLibraryScreenState.Error -> {
                item(key = "podcasts_error", contentType = "library_empty_state") {
                    LibraryEmptyState(
                        iconRes = R.drawable.error,
                        titleRes = R.string.library_your_podcasts,
                        descriptionRes = state.messageResId,
                        actionLabelRes = R.string.retry,
                        onAction = onRetry,
                    )
                }
            }

            is PodcastLibraryScreenState.Success -> {
                val podcasts = state.uiState.podcasts
                item(key = "podcasts_hero", contentType = "library_hero") {
                    PodcastsHero(
                        podcasts = podcasts,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
                items(
                    items = podcasts,
                    key = PodcastLibraryItemUiModel::browseId,
                    contentType = { "library_podcast" },
                ) { podcast ->
                    PodcastLibraryCard(
                        podcast = podcast,
                        onClick = { onPodcastClick(podcast.browseId) },
                        onPlay = { onPodcastPlay(podcast.browseId) },
                        onShowMenu = { fromLongPress -> onPodcastMenu(podcast, fromLongPress) },
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
}

@Composable
private fun PodcastsHero(
    podcasts: List<PodcastLibraryItemUiModel>,
    modifier: Modifier = Modifier,
) {
    val syncedCount = remember(podcasts) { podcasts.count { it.isSavedRemotely } }
    val heroArtwork =
        remember(podcasts) {
            podcasts.asSequence().mapNotNull { it.thumbnailUrl }.distinct().take(2).toList()
        }
    val stats =
        buildList {
            add(
                LibraryHeroStat(
                    value = podcasts.size.toString(),
                    label = pluralStringResource(R.plurals.library_stat_podcasts, podcasts.size),
                ),
            )
            if (syncedCount > 0) {
                add(
                    LibraryHeroStat(
                        value = syncedCount.toString(),
                        label = stringResource(R.string.youtube_synced),
                    ),
                )
            }
        }
    LibraryCollectionHero(
        overline = stringResource(R.string.filter_library),
        title = stringResource(R.string.library_your_podcasts),
        stats = stats,
        artworkUrls = heroArtwork,
        fallbackIconRes = R.drawable.mic,
        modifier = modifier,
    )
}

@Composable
private fun PodcastLibraryCard(
    podcast: PodcastLibraryItemUiModel,
    onClick: () -> Unit,
    onPlay: () -> Unit,
    onShowMenu: (fromLongPress: Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val isRemote = podcast.isSavedRemotely
    LibraryMediaListCard(
        title = podcast.title,
        subtitle = podcast.author ?: stringResource(R.string.podcast),
        artworkUrls = listOfNotNull(podcast.thumbnailUrl),
        fallbackIconRes = R.drawable.mic,
        sourceLabel = stringResource(if (isRemote) R.string.youtube_synced else R.string.personal_label),
        sourceColor = if (isRemote) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
        onClick = onClick,
        onPlay = onPlay,
        onMenuClick = { onShowMenu(false) },
        onLongClick = { onShowMenu(true) },
        modifier = modifier,
    )
}

@Composable
private fun PodcastLibraryMenu(
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    MenuSurfaceSection(modifier = Modifier.padding(vertical = 6.dp)) {
        NewMenuItem(
            headlineContent = { Text(stringResource(R.string.remove_from_library)) },
            leadingContent = {
                Icon(
                    painter = painterResource(R.drawable.library_add_check),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            onClick = {
                onDismiss()
                onRemove()
            },
        )
    }
}
