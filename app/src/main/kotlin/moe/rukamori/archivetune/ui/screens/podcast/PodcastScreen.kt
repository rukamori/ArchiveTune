/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.podcast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.podcast.PodcastAction
import moe.rukamori.archivetune.podcast.PodcastEpisodeUiModel
import moe.rukamori.archivetune.podcast.PodcastEvent
import moe.rukamori.archivetune.podcast.PodcastScreenState
import moe.rukamori.archivetune.podcast.PodcastUiState
import moe.rukamori.archivetune.ui.component.MediaDetailHero
import moe.rukamori.archivetune.ui.component.MediaDetailStatePanel
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.viewmodels.PodcastViewModel

const val PodcastRoute = "podcast/{browseId}"

@Composable
fun PodcastScreen(
    navController: NavController,
    viewModel: PodcastViewModel = hiltViewModel(),
) {
    val state by viewModel.screenState.collectAsStateWithLifecycle()
    val playerConnection = LocalPlayerConnection.current
    val snackbarHostState = remember { SnackbarHostState() }
    val unknownErrorMessage = stringResource(R.string.error_unknown)
    val loginRequiredMessage = stringResource(R.string.not_logged_in_youtube)
    val syncDisabledMessage = stringResource(R.string.sync_disabled)
    val onRetry = remember(viewModel) { { viewModel.onAction(PodcastAction.Retry) } }
    val onLoadMore = remember(viewModel) { { viewModel.onAction(PodcastAction.LoadMore) } }
    val onPlayAll = remember(viewModel) { { viewModel.onAction(PodcastAction.PlayAll) } }
    val onTogglePodcastSave = remember(viewModel) { { viewModel.onAction(PodcastAction.TogglePodcastSave) } }
    val onPlayEpisode = remember(viewModel) { { id: String -> viewModel.onAction(PodcastAction.PlayEpisode(id)) } }
    val onToggleEpisodeLibrary =
        remember(viewModel) { { id: String -> viewModel.onAction(PodcastAction.ToggleEpisodeLibrary(id)) } }
    val onBack: () -> Unit = remember(navController) { { navController.navigateUp() } }

    LaunchedEffect(viewModel, playerConnection, unknownErrorMessage, loginRequiredMessage, syncDisabledMessage) {
        viewModel.events.collect { event ->
            when (event) {
                is PodcastEvent.Play -> {
                    playerConnection?.playQueue(
                        ListQueue(
                            title = event.request.title,
                            items = event.request.items.map { metadata -> metadata.toMediaItem() },
                            startIndex = event.request.startIndex,
                        ),
                    )
                }

                is PodcastEvent.ShowMessage -> {
                    val message =
                        when (event.messageResId) {
                            R.string.error_unknown -> unknownErrorMessage
                            R.string.not_logged_in_youtube -> loginRequiredMessage
                            R.string.sync_disabled -> syncDisabledMessage
                            else -> unknownErrorMessage
                        }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    PodcastScreenContent(
        state = state,
        snackbarHostState = snackbarHostState,
        onBack = onBack,
        onRetry = onRetry,
        onLoadMore = onLoadMore,
        onPlayAll = onPlayAll,
        onPlayEpisode = onPlayEpisode,
        onTogglePodcastSave = onTogglePodcastSave,
        onToggleEpisodeLibrary = onToggleEpisodeLibrary,
    )
}

@Composable
private fun PodcastScreenContent(
    state: PodcastScreenState,
    snackbarHostState: SnackbarHostState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onTogglePodcastSave: () -> Unit,
    onToggleEpisodeLibrary: (String) -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        when (state) {
            PodcastScreenState.Loading -> {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }

            PodcastScreenState.Empty -> {
                MediaDetailStatePanel(
                    title = stringResource(R.string.episodes),
                    description = stringResource(R.string.podcast_has_no_episodes),
                    iconRes = R.drawable.mic,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is PodcastScreenState.Error -> {
                MediaDetailStatePanel(
                    title = stringResource(R.string.podcast),
                    description = stringResource(state.messageResId),
                    iconRes = R.drawable.error,
                    actionLabel = stringResource(R.string.retry),
                    onAction = onRetry,
                    modifier = Modifier.align(Alignment.Center),
                )
            }

            is PodcastScreenState.Success -> {
                PodcastSuccessContent(
                    uiState = state.uiState,
                    onLoadMore = onLoadMore,
                    onPlayAll = onPlayAll,
                    onPlayEpisode = onPlayEpisode,
                    onTogglePodcastSave = onTogglePodcastSave,
                    onToggleEpisodeLibrary = onToggleEpisodeLibrary,
                )
            }
        }

        PodcastTopAppBar(
            title = (state as? PodcastScreenState.Success)?.uiState?.title.orEmpty(),
            onBack = onBack,
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

@Composable
private fun PodcastSuccessContent(
    uiState: PodcastUiState,
    onLoadMore: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayEpisode: (String) -> Unit,
    onTogglePodcastSave: () -> Unit,
    onToggleEpisodeLibrary: (String) -> Unit,
) {
    val listState = rememberLazyListState()
    val systemBarsTopPadding = WindowInsets.systemBars.asPaddingValues().calculateTopPadding()
    val bottomPadding = LocalPlayerAwareWindowInsets.current.asPaddingValues().calculateBottomPadding()
    val subtitle = remember(uiState.author) { uiState.author?.let(::AnnotatedString) }

    LaunchedEffect(listState, uiState.canLoadMore, uiState.isLoadingMore, uiState.episodes.size) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisibleIndex = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            layoutInfo.totalItemsCount > 0 && lastVisibleIndex >= layoutInfo.totalItemsCount - PaginationThreshold
        }.distinctUntilChanged()
            .filter { shouldLoad -> shouldLoad && uiState.canLoadMore && !uiState.isLoadingMore }
            .collect { onLoadMore() }
    }

    LazyColumn(
        state = listState,
        contentPadding = PaddingValues(bottom = bottomPadding + 16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        item(key = "podcast_header", contentType = "podcast_header") {
            MediaDetailHero(
                title = uiState.title,
                thumbnailUrl = uiState.thumbnailUrl,
                fallbackIcon = R.drawable.mic,
                systemBarsTopPadding = systemBarsTopPadding,
                subtitle = subtitle,
                metadata = stringResource(R.string.episodes),
                description = null,
                isAdded = uiState.isSaved,
                addContentDescription = R.string.add_to_library,
                removeContentDescription = R.string.remove_from_library,
                onShuffle = null,
                onPlay = onPlayAll,
                onToggleAdd = onTogglePodcastSave,
                isToggleAddEnabled = !uiState.isSavePending,
            )
        }

        uiState.description?.takeIf(String::isNotBlank)?.let { description ->
            item(key = "podcast_description", contentType = "podcast_description") {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                )
            }
        }

        items(
            items = uiState.episodes,
            key = PodcastEpisodeUiModel::id,
            contentType = { "podcast_episode" },
        ) { episode ->
            PodcastEpisodeRow(
                episode = episode,
                onClick = remember(episode.id, onPlayEpisode) { { onPlayEpisode(episode.id) } },
                onToggleLibrary =
                    remember(episode.id, onToggleEpisodeLibrary) {
                        { onToggleEpisodeLibrary(episode.id) }
                    },
            )
            HorizontalDivider(modifier = Modifier.padding(start = 120.dp))
        }

        if (uiState.isLoadingMore) {
            item(key = "podcast_loading_more", contentType = "podcast_loading_more") {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(32.dp))
                }
            }
        }
    }
}

@Composable
private fun PodcastEpisodeRow(
    episode: PodcastEpisodeUiModel,
    onClick: () -> Unit,
    onToggleLibrary: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val supportingText =
        remember(episode.dateText, episode.durationText) {
            listOfNotNull(episode.dateText, episode.durationText).joinToString(MetadataSeparator)
        }
    val artworkModel =
        remember(episode.thumbnailUrl) {
            episode.thumbnailUrl.resize(
                width = EpisodeArtworkDecodeSize,
                height = EpisodeArtworkDecodeSize,
                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
            )
        }
    ListItem(
        headlineContent = {
            Text(
                text = episode.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = episode.podcastTitle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (supportingText.isNotBlank()) {
                    Text(
                        text = supportingText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                episode.description?.takeIf(String::isNotBlank)?.let { description ->
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
        leadingContent = {
            AsyncImage(
                model = artworkModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(EpisodeArtworkSize)
                        .clip(MaterialTheme.shapes.medium),
            )
        },
        trailingContent = {
            if (episode.isLibraryPending) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            } else {
                IconButton(onClick = onToggleLibrary) {
                    Icon(
                        painter =
                            painterResource(
                                if (episode.isInLibrary) {
                                    R.drawable.library_add_check
                                } else {
                                    R.drawable.library_add
                                },
                            ),
                        contentDescription =
                            stringResource(
                                if (episode.isInLibrary) {
                                    R.string.remove_from_library
                                } else {
                                    R.string.add_to_library
                                },
                            ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp)
                .clickable(onClick = onClick)
                .padding(horizontal = 8.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PodcastTopAppBar(
    title: String,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.arrow_back),
                    contentDescription = stringResource(R.string.back_button_desc),
                )
            }
        },
        colors =
            TopAppBarDefaults.topAppBarColors(
                containerColor = Color.Transparent,
                scrolledContainerColor = MaterialTheme.colorScheme.surface,
            ),
        modifier = modifier,
    )
}

private const val PaginationThreshold = 4
private const val EpisodeArtworkDecodeSize = 256
private val EpisodeArtworkSize = 88.dp
private const val MetadataSeparator = "  •  "
