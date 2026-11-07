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

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.SearchHistory
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
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.viewmodels.OnlineSearchSuggestionViewModel
import moe.rukamori.archivetune.viewmodels.SuggestionRemoteStatus
import kotlinx.coroutines.flow.drop

@Composable
fun OnlineSearchScreen(
    query: String,
    onQueryChange: (TextFieldValue) -> Unit,
    navController: NavController,
    onSearch: (String) -> Unit,
    onDismiss: () -> Unit,
    pureBlack: Boolean,
    viewModel: OnlineSearchSuggestionViewModel = hiltViewModel(),
) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val menuState = LocalMenuState.current
    val playerConnection = LocalPlayerConnection.current ?: return

    val isPlaying by playerConnection.isPlaying.collectAsStateWithLifecycle()
    val mediaMetadata by playerConnection.mediaMetadata.collectAsStateWithLifecycle()

    val coroutineScope = rememberCoroutineScope()
    val viewState by viewModel.viewState.collectAsStateWithLifecycle()
    val lazyListState = rememberLazyListState()
    val itemMotion = rememberSearchItemMotion()
    var showClearHistoryDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(lazyListState) {
        snapshotFlow { lazyListState.firstVisibleItemScrollOffset }
            .drop(1)
            .collect {
                keyboardController?.hide()
            }
    }

    LaunchedEffect(query) {
        viewModel.updateQuery(query)
    }

    val trimmedQuery = query.trim()
    val queryMode = trimmedQuery.isNotEmpty()
    val stateMatchesMode = viewState.query.isNotBlank() == queryMode
    val remoteStatus = viewState.remoteStatus
    val isLoading = queryMode && stateMatchesMode && remoteStatus == SuggestionRemoteStatus.Loading

    val submitQuery: (String) -> Unit = { text ->
        onSearch(text)
        onDismiss()
    }
    val fillQuery: (String) -> Unit = { text ->
        onQueryChange(TextFieldValue(text, TextRange(text.length)))
    }
    val openItemMenu: (YTItem) -> Unit = { item ->
        if (item.hasSearchResultMenu()) {
            menuState.show {
                SearchResultMenu(
                    item = item,
                    navController = navController,
                    coroutineScope = coroutineScope,
                    onDismiss = {
                        menuState.dismiss()
                        onDismiss()
                    },
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
                    playerConnection.playQueue(YouTubeQueue.radio(item.toMediaMetadata()))
                    onDismiss()
                }
            }

            is AlbumItem -> {
                navController.navigateToAlbum(item.id)
                onDismiss()
            }

            is ArtistItem -> {
                navController.navigateToArtist(item.id)
                onDismiss()
            }

            is PlaylistItem -> {
                navController.navigate("online_playlist/${item.id}")
                onDismiss()
            }

            is PodcastItem -> {
                navController.navigate("podcast/${android.net.Uri.encode(item.browseId)}")
                onDismiss()
            }

            is EpisodeItem -> {
                playerConnection.playQueue(
                    ListQueue(
                        title = item.podcast?.name ?: item.title,
                        items = listOf(item.toMediaItem()),
                    ),
                )
                onDismiss()
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

    val backgroundColor = if (pureBlack) Color.Black else MaterialTheme.colorScheme.background

    Box(
        contentAlignment = Alignment.TopCenter,
        modifier =
            Modifier
                .fillMaxSize()
                .background(backgroundColor),
    ) {
        LazyColumn(
            state = lazyListState,
            contentPadding =
                PaddingValues(
                    top = 8.dp,
                    bottom =
                        WindowInsets.systemBars
                            .only(WindowInsetsSides.Bottom)
                            .asPaddingValues()
                            .calculateBottomPadding() + 16.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(SearchRowSpacing),
            modifier =
                Modifier
                    .widthIn(max = SearchContentMaxWidth)
                    .fillMaxSize(),
        ) {
            if (!queryMode) {
                if (stateMatchesMode) {
                    if (viewState.history.isEmpty()) {
                        item(key = "idle_hero", contentType = "idle_hero") {
                            SearchIdleHero(modifier = searchItemModifier(itemMotion))
                        }
                    } else {
                        item(key = "recent_header", contentType = "section_header") {
                            SearchSectionLabel(
                                title = stringResource(R.string.search_recent),
                                modifier = searchItemModifier(itemMotion),
                            ) {
                                TextButton(onClick = { showClearHistoryDialog = true }) {
                                    Icon(
                                        painter = painterResource(R.drawable.clear_all),
                                        contentDescription = null,
                                        modifier = Modifier.size(ButtonDefaults.IconSize),
                                    )
                                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                                    Text(stringResource(R.string.clear))
                                }
                            }
                        }
                        historyRows(
                            history = viewState.history,
                            query = "",
                            pureBlack = pureBlack,
                            itemMotion = itemMotion,
                            onSearch = submitQuery,
                            onFill = fillQuery,
                            onDelete = viewModel::deleteHistory,
                        )
                    }
                }
            } else {
                item(key = "query_hero", contentType = "query_hero") {
                    SearchQueryHero(
                        query = trimmedQuery,
                        loading = isLoading,
                        onSearch = { submitQuery(trimmedQuery) },
                        modifier = searchItemModifier(itemMotion),
                    )
                }

                if (stateMatchesMode) {
                    val hasContent =
                        viewState.history.isNotEmpty() ||
                            viewState.suggestions.isNotEmpty() ||
                            viewState.items.isNotEmpty()

                    when (remoteStatus) {
                        SuggestionRemoteStatus.Loading -> {
                            if (!hasContent) {
                                item(key = "status_loading", contentType = "status") {
                                    SuggestionStatusRow(
                                        message = stringResource(R.string.search_suggestions_loading),
                                        loading = true,
                                        modifier = searchItemModifier(itemMotion),
                                    )
                                }
                            }
                        }

                        SuggestionRemoteStatus.Offline, SuggestionRemoteStatus.Failed -> {
                            item(key = "status_error", contentType = "status_error") {
                                SuggestionErrorCard(
                                    offline = remoteStatus == SuggestionRemoteStatus.Offline,
                                    onRetry = viewModel::retry,
                                    modifier = searchItemModifier(itemMotion),
                                )
                            }
                        }

                        SuggestionRemoteStatus.Loaded -> {
                            if (!hasContent) {
                                item(key = "status_empty", contentType = "status") {
                                    SuggestionStatusRow(
                                        message = stringResource(R.string.search_no_quick_matches),
                                        loading = false,
                                        modifier = searchItemModifier(itemMotion),
                                    )
                                }
                            }
                        }

                        SuggestionRemoteStatus.Idle -> {
                            Unit
                        }
                    }

                    if (viewState.history.isNotEmpty()) {
                        item(key = "matching_history_header", contentType = "section_header") {
                            SearchSectionLabel(
                                title = stringResource(R.string.search_recent),
                                modifier = searchItemModifier(itemMotion),
                            )
                        }
                        historyRows(
                            history = viewState.history,
                            query = trimmedQuery,
                            pureBlack = pureBlack,
                            itemMotion = itemMotion,
                            onSearch = submitQuery,
                            onFill = fillQuery,
                            onDelete = viewModel::deleteHistory,
                        )
                    }

                    if (viewState.suggestions.isNotEmpty()) {
                        item(key = "suggestions_header", contentType = "section_header") {
                            SearchSectionLabel(
                                title = stringResource(R.string.suggestions),
                                modifier = searchItemModifier(itemMotion),
                            ) {
                                if (isLoading) {
                                    LoadingIndicator(modifier = Modifier.size(28.dp))
                                }
                            }
                        }
                        itemsIndexed(
                            items = viewState.suggestions,
                            key = { _, suggestion -> "suggestion_$suggestion" },
                            contentType = { _, _ -> "suggestion" },
                        ) { index, suggestion ->
                            SearchSuggestionRow(
                                text = suggestion,
                                query = trimmedQuery,
                                isHistory = false,
                                index = index,
                                count = viewState.suggestions.size,
                                pureBlack = pureBlack,
                                onClick = { submitQuery(suggestion) },
                                onFill = { fillQuery(suggestion) },
                                onDelete = null,
                                modifier = searchItemModifier(itemMotion),
                            )
                        }
                    }

                    if (viewState.items.isNotEmpty()) {
                        val topResult = viewState.items.first()
                        val otherResults = viewState.items.drop(1)

                        item(key = "top_result_header", contentType = "section_header") {
                            SearchSectionLabel(
                                title = stringResource(R.string.search_top_result),
                                modifier = searchItemModifier(itemMotion),
                            )
                        }
                        item(key = "top_result_${topResult.id}", contentType = "top_result") {
                            TopResultCard(
                                item = topResult,
                                isActive = isItemActive(topResult),
                                isPlaying = isPlaying,
                                pureBlack = pureBlack,
                                onClick = { openItem(topResult) },
                                onMenu = { openItemMenu(topResult) },
                                modifier =
                                    searchItemModifier(itemMotion)
                                        .padding(horizontal = SearchListHorizontalPadding),
                            )
                        }

                        if (otherResults.isNotEmpty()) {
                            item(key = "more_results_header", contentType = "section_header") {
                                SearchSectionLabel(
                                    title = stringResource(R.string.top_results),
                                    modifier = searchItemModifier(itemMotion),
                                )
                            }
                            itemsIndexed(
                                items = otherResults,
                                key = { _, item -> "item_${item.id}" },
                                contentType = { _, item -> item::class },
                            ) { index, item ->
                                SearchResultRow(
                                    item = item,
                                    index = index,
                                    count = otherResults.size,
                                    isActive = isItemActive(item),
                                    isPlaying = isPlaying,
                                    pureBlack = pureBlack,
                                    onClick = { openItem(item) },
                                    onMenu = { openItemMenu(item) },
                                    modifier =
                                        searchItemModifier(itemMotion)
                                            .padding(horizontal = SearchListHorizontalPadding),
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            icon = {
                Icon(
                    painter = painterResource(R.drawable.delete_history),
                    contentDescription = null,
                )
            },
            title = { Text(stringResource(R.string.clear_search_history)) },
            text = { Text(stringResource(R.string.clear_search_history_confirm)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearHistory()
                        showClearHistoryDialog = false
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) {
                    Text(stringResource(R.string.clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

private fun LazyItemScope.searchItemModifier(motion: SearchItemMotion): Modifier =
    Modifier.animateItem(
        fadeInSpec = motion.fade,
        placementSpec = motion.placement,
        fadeOutSpec = motion.fade,
    )

private fun LazyListScope.historyRows(
    history: List<SearchHistory>,
    query: String,
    pureBlack: Boolean,
    itemMotion: SearchItemMotion,
    onSearch: (String) -> Unit,
    onFill: (String) -> Unit,
    onDelete: (SearchHistory) -> Unit,
) {
    itemsIndexed(
        items = history,
        key = { _, entry -> "history_${entry.id}" },
        contentType = { _, _ -> "history" },
    ) { index, entry ->
        SearchSuggestionRow(
            text = entry.query,
            query = query,
            isHistory = true,
            index = index,
            count = history.size,
            pureBlack = pureBlack,
            onClick = { onSearch(entry.query) },
            onFill = { onFill(entry.query) },
            onDelete = { onDelete(entry) },
            modifier = searchItemModifier(itemMotion),
        )
    }
}

@Composable
private fun SearchIdleHero(modifier: Modifier = Modifier) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val spin: State<Float>? =
        if (animationsDisabled) {
            null
        } else {
            rememberInfiniteTransition(label = "searchIdleHero").animateFloat(
                initialValue = 0f,
                targetValue = 360f,
                animationSpec = infiniteRepeatable(tween(IdleHeroRotationMillis, easing = LinearEasing)),
                label = "searchIdleHeroSpin",
            )
        }
    val heroShape = MaterialShapes.Cookie12Sided.toShape()
    val accentShape = MaterialShapes.Sunny.toShape()

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 48.dp)
                .semantics(mergeDescendants = true) {},
    ) {
        Box(modifier = Modifier.size(IdleHeroSize)) {
            Box(
                modifier =
                    Modifier
                        .matchParentSize()
                        .graphicsLayer { rotationZ = spin?.value ?: 0f }
                        .background(MaterialTheme.colorScheme.primaryContainer, heroShape),
            )
            Icon(
                painter = painterResource(R.drawable.search),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier =
                    Modifier
                        .align(Alignment.Center)
                        .size(52.dp),
            )
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .size(IdleHeroAccentSize)
                        .graphicsLayer { rotationZ = -(spin?.value ?: 0f) }
                        .background(MaterialTheme.colorScheme.tertiaryContainer, accentShape),
            ) {
                Icon(
                    painter = painterResource(R.drawable.music_note),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onTertiaryContainer,
                    modifier =
                        Modifier
                            .size(20.dp)
                            .graphicsLayer { rotationZ = spin?.value ?: 0f },
                )
            }
        }
        Spacer(Modifier.size(24.dp))
        Text(
            text = stringResource(R.string.search_yt_music),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = stringResource(R.string.search_idle_message),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SearchQueryHero(
    query: String,
    loading: Boolean,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()

    Surface(
        onClick = onSearch,
        shape = QueryHeroShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        interactionSource = interactionSource,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SearchListHorizontalPadding, vertical = 4.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .heightIn(min = 76.dp)
                    .padding(start = 12.dp, end = 20.dp, top = 12.dp, bottom = 12.dp),
        ) {
            SearchMorphBadge(
                iconRes = R.drawable.search,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                morphed = pressed || loading,
                size = 52.dp,
                iconSize = 24.dp,
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.search_for),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.76f),
                )
                Text(
                    text = query,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun SuggestionStatusRow(
    message: String,
    loading: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SearchListHorizontalPadding + 8.dp, vertical = 16.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(36.dp))
        } else {
            Icon(
                painter = painterResource(R.drawable.search_off),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SuggestionErrorCard(
    offline: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SearchListHorizontalPadding, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text =
                    stringResource(
                        if (offline) R.string.error_no_internet else R.string.search_suggestions_failed,
                    ),
                style = MaterialTheme.typography.bodyMedium,
                modifier =
                    Modifier
                        .weight(1f)
                        .semantics { liveRegion = LiveRegionMode.Polite },
            )
            TextButton(
                onClick = onRetry,
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onErrorContainer),
            ) {
                Icon(
                    painter = painterResource(R.drawable.replay),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                Text(stringResource(R.string.retry))
            }
        }
    }
}

@Composable
private fun SearchSuggestionRow(
    text: String,
    query: String,
    isHistory: Boolean,
    index: Int,
    count: Int,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onFill: () -> Unit,
    onDelete: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val colorScheme = MaterialTheme.colorScheme

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors = ListItemDefaults.segmentedColors(containerColor = searchRowContainerColor(pureBlack)),
        interactionSource = interactionSource,
        onLongClick = {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            onFill()
        },
        onLongClickLabel = stringResource(R.string.search_insert_query),
        leadingContent = {
            SearchMorphBadge(
                iconRes = if (isHistory) R.drawable.history else R.drawable.search,
                containerColor = if (isHistory) colorScheme.secondaryContainer else colorScheme.surfaceContainerHighest,
                contentColor = if (isHistory) colorScheme.onSecondaryContainer else colorScheme.onSurfaceVariant,
                morphed = pressed,
                restShape = if (isHistory) MaterialShapes.Cookie4Sided else MaterialShapes.Circle,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    IconButton(onClick = onDelete) {
                        Icon(
                            painter = painterResource(R.drawable.close),
                            contentDescription = stringResource(R.string.remove_from_history),
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                }
                IconButton(onClick = onFill) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_top_left),
                        contentDescription = stringResource(R.string.search_insert_query),
                        tint = colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        },
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = SearchListHorizontalPadding),
    ) {
        Text(
            text = highlightQueryMatch(text = text, query = query),
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun highlightQueryMatch(
    text: String,
    query: String,
): AnnotatedString {
    val emphasis = MaterialTheme.colorScheme.onSurface
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    return remember(text, query, emphasis, muted) {
        val start = if (query.isEmpty()) -1 else text.indexOf(query, ignoreCase = true)
        buildAnnotatedString {
            if (start < 0) {
                append(text)
            } else {
                val end = start + query.length
                withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(0, start))
                }
                withStyle(SpanStyle(color = muted)) {
                    append(text.substring(start, end))
                }
                withStyle(SpanStyle(color = emphasis, fontWeight = FontWeight.SemiBold)) {
                    append(text.substring(end))
                }
            }
        }
    }
}

private val SearchContentMaxWidth = 720.dp
private val SearchRowSpacing = 2.dp
private val QueryHeroShape = RoundedCornerShape(28.dp)
private val IdleHeroSize = 144.dp
private val IdleHeroAccentSize = 48.dp
private const val IdleHeroRotationMillis = 24_000
