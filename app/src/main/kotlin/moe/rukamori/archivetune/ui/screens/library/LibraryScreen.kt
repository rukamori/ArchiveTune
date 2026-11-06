/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.toColorInt
import androidx.navigation.NavController
import kotlin.math.roundToInt
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AppBarHeight
import moe.rukamori.archivetune.constants.ChipSortTypeKey
import moe.rukamori.archivetune.constants.DefaultLibraryFilterOrderPreference
import moe.rukamori.archivetune.constants.DisableBlurKey
import moe.rukamori.archivetune.constants.LibraryChipOrderKey
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.constants.PlaylistTagOrderKey
import moe.rukamori.archivetune.constants.ShowSpotifyPlaylistsKey
import moe.rukamori.archivetune.constants.ShowTagsInLibraryKey
import moe.rukamori.archivetune.constants.toLibraryFilterOrder
import moe.rukamori.archivetune.constants.toPlaylistTagOrder
import moe.rukamori.archivetune.db.entities.TagEntity
import moe.rukamori.archivetune.ui.component.TagsManagementDialog
import moe.rukamori.archivetune.ui.utils.resetHeightOffset
import moe.rukamori.archivetune.utils.rememberEnumPreference
import moe.rukamori.archivetune.utils.rememberPreference

internal val LibraryHeaderContentPadding = 64.dp
internal val LibraryPullToRefreshIndicatorOffset = 0.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val defaultFilter by rememberEnumPreference(ChipSortTypeKey, LibraryFilter.LIBRARY)
    val database = LocalDatabase.current
    val (selectedTagIds, onSelectedTagIdsChange) = rememberPlaylistTagFilterState(database)
    val allTags by database.allTags().collectAsState(initial = emptyList())
    val (showTagsInLibrary) = rememberPreference(ShowTagsInLibraryKey, defaultValue = true)
    val (showSpotifyPlaylists) = rememberPreference(ShowSpotifyPlaylistsKey, defaultValue = false)
    val (disableBlur) = rememberPreference(DisableBlurKey, false)
    val (libraryChipOrderPreference) =
        rememberPreference(
            LibraryChipOrderKey,
            defaultValue = DefaultLibraryFilterOrderPreference,
        )
    val (playlistTagOrderPreference) = rememberPreference(PlaylistTagOrderKey, defaultValue = "")
    var showTagsManagementDialog by rememberSaveable { mutableStateOf(false) }
    val activeSelectedTagIds = if (showTagsInLibrary) selectedTagIds else emptySet()
    val orderedTags =
        remember(allTags, playlistTagOrderPreference) {
            val tagsById = allTags.associateBy(TagEntity::id)
            playlistTagOrderPreference
                .toPlaylistTagOrder(allTags.map(TagEntity::id))
                .mapNotNull { tagId -> tagsById[tagId] }
        }
    val libraryFilters =
        remember(showSpotifyPlaylists, libraryChipOrderPreference) {
            libraryChipOrderPreference
                .toLibraryFilterOrder()
                .filter { filter -> showSpotifyPlaylists || filter != LibraryFilter.SPOTIFY }
        }

    if (showTagsManagementDialog) {
        TagsManagementDialog(
            onDismiss = { showTagsManagementDialog = false },
        )
    }

    val pagerState =
        rememberPagerState(
            initialPage = libraryFilters.indexOf(defaultFilter).takeIf { it >= 0 } ?: 0,
        ) { libraryFilters.size }

    val currentFilter = libraryFilters.getOrElse(pagerState.currentPage) { LibraryFilter.LIBRARY }
    val tonalStart = MaterialTheme.colorScheme.primaryContainer
    val tonalMiddle = MaterialTheme.colorScheme.secondaryContainer
    val tagFilterContent: (@Composable () -> Unit)? =
        if (showTagsInLibrary) {
            {
                PlaylistTagFilterRow(
                    tags = orderedTags,
                    selectedTagIds = selectedTagIds,
                    onSelectedTagIdsChange = onSelectedTagIdsChange,
                    onManageTagsClick = { showTagsManagementDialog = true },
                )
            }
        } else {
            null
        }

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .clipToBounds()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .background(MaterialTheme.colorScheme.background),
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

        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .layout { measurable, constraints ->
                        val offset = scrollBehavior.state.heightOffset.roundToInt().coerceAtMost(0)
                        val placeable =
                            measurable.measure(
                                constraints.copy(
                                    minHeight = (constraints.minHeight - offset).coerceAtLeast(0),
                                    maxHeight = (constraints.maxHeight - offset).coerceAtLeast(0),
                                ),
                            )
                        layout(placeable.width, constraints.maxHeight) {
                            placeable.placeRelative(0, offset)
                        }
                    }.windowInsetsPadding(WindowInsets.statusBars)
                    .padding(top = AppBarHeight),
        ) {
            val tabListState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val selectPage: (Int) -> Unit =
                remember(coroutineScope, pagerState) {
                    { page -> coroutineScope.launch { pagerState.animateScrollToPage(page) } }
                }
            val selectFilter: (LibraryFilter) -> Unit =
                remember(libraryFilters, selectPage) {
                    { filter -> selectPage(libraryFilters.indexOf(filter).takeIf { it >= 0 } ?: 0) }
                }

            LaunchedEffect(defaultFilter, libraryFilters) {
                val selectedFilter = defaultFilter.takeIf { it in libraryFilters } ?: LibraryFilter.LIBRARY
                val selectedPage = libraryFilters.indexOf(selectedFilter).takeIf { it >= 0 } ?: 0
                if (pagerState.currentPage != selectedPage) {
                    pagerState.scrollToPage(selectedPage)
                }
            }

            LaunchedEffect(pagerState.currentPage, libraryFilters) {
                scrollBehavior.state.resetHeightOffset()
                tabListState.centerItem(pagerState.currentPage.coerceIn(0, libraryFilters.lastIndex))
            }

            Box(
                modifier =
                    Modifier
                        .weight(1f)
                        .fillMaxWidth(),
            ) {
                HorizontalPager(
                    state = pagerState,
                    key = { page -> libraryFilters.getOrElse(page) { LibraryFilter.LIBRARY }.name },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    when (libraryFilters.getOrElse(page) { LibraryFilter.LIBRARY }) {
                        LibraryFilter.LIBRARY -> {
                            LibraryMixScreen(
                                navController = navController,
                                filterContent = tagFilterContent,
                                selectedTagIds = activeSelectedTagIds,
                                onTabSelected = selectFilter,
                            )
                        }

                        LibraryFilter.PLAYLISTS -> {
                            LibraryPlaylistsScreen(
                                navController = navController,
                                filterContent = tagFilterContent,
                                selectedTagIds = activeSelectedTagIds,
                            )
                        }

                        LibraryFilter.SPOTIFY -> {
                            LibrarySpotifyPlaylistsScreen(navController = navController)
                        }

                        LibraryFilter.PODCASTS -> {
                            LibraryPodcastsScreen(navController = navController)
                        }

                        LibraryFilter.SONGS -> {
                            LibrarySongsScreen(navController = navController)
                        }

                        LibraryFilter.ARTISTS -> {
                            LibraryArtistsScreen(navController = navController)
                        }

                        LibraryFilter.ALBUMS -> {
                            LibraryAlbumsScreen(navController = navController)
                        }
                    }
                }

                LibrarySectionTabs(
                    filters = libraryFilters,
                    selectedFilter = currentFilter,
                    state = tabListState,
                    onSelect = { filter -> selectFilter(filter) },
                )
            }
        }
    }
}

private suspend fun LazyListState.centerItem(index: Int) {
    val visibleItem = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
    if (visibleItem == null) {
        animateScrollToItem(index)
    }
    val item = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index } ?: return
    val viewportCenter = (layoutInfo.viewportStartOffset + layoutInfo.viewportEndOffset) / 2
    val itemCenter = item.offset + item.size / 2
    animateScrollBy((itemCenter - viewportCenter).toFloat())
}

@Composable
private fun LibrarySectionTabs(
    filters: List<LibraryFilter>,
    selectedFilter: LibraryFilter,
    state: LazyListState,
    onSelect: (LibraryFilter) -> Unit,
) {
    val sectionsDescription = stringResource(R.string.library_tab_sections)
    LazyRow(
        state = state,
        modifier =
            Modifier
                .fillMaxWidth()
                .height(LibraryHeaderContentPadding)
                .selectableGroup()
                .semantics { contentDescription = sectionsDescription },
        contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(
            items = filters,
            key = { filter -> filter.name },
            contentType = { "library_section_tab" },
        ) { filter ->
            LibrarySectionTab(
                label = stringResource(filter.labelRes()),
                iconRes = filter.iconRes(),
                selected = selectedFilter == filter,
                onClick = { onSelect(filter) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibrarySectionTab(
    label: String,
    @DrawableRes iconRes: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    ToggleButton(
        checked = selected,
        onCheckedChange = { onClick() },
        modifier =
            Modifier
                .heightIn(min = 40.dp)
                .semantics { role = Role.Tab },
    ) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(ButtonDefaults.IconSize),
        )
        Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        Text(
            text = label,
            style =
                if (selected) {
                    MaterialTheme.typography.labelLargeEmphasized
                } else {
                    MaterialTheme.typography.labelLarge
                },
            maxLines = 1,
        )
    }
}

private fun LibraryFilter.labelRes(): Int =
    when (this) {
        LibraryFilter.LIBRARY -> R.string.filter_library
        LibraryFilter.PLAYLISTS -> R.string.playlists
        LibraryFilter.PODCASTS -> R.string.podcast
        LibraryFilter.SPOTIFY -> R.string.spotify_playlists
        LibraryFilter.SONGS -> R.string.songs
        LibraryFilter.ARTISTS -> R.string.artists
        LibraryFilter.ALBUMS -> R.string.albums
    }

@DrawableRes
private fun LibraryFilter.iconRes(): Int =
    when (this) {
        LibraryFilter.LIBRARY -> R.drawable.graphic_eq
        LibraryFilter.PLAYLISTS -> R.drawable.queue_music
        LibraryFilter.PODCASTS -> R.drawable.mic
        LibraryFilter.SPOTIFY -> R.drawable.spotify
        LibraryFilter.SONGS -> R.drawable.music_note
        LibraryFilter.ARTISTS -> R.drawable.person
        LibraryFilter.ALBUMS -> R.drawable.album
    }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistTagFilterRow(
    tags: List<TagEntity>,
    selectedTagIds: Set<String>,
    onSelectedTagIdsChange: (Set<String>) -> Unit,
    onManageTagsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
        contentPadding = PaddingValues(horizontal = LibraryContentHorizontalPadding),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        item(key = "all_playlist_tags", contentType = "playlist_tag_filter") {
            PlaylistTagToggle(
                label = stringResource(R.string.filter_all),
                checked = selectedTagIds.isEmpty(),
                accentColor = null,
                onCheckedChange = { onSelectedTagIdsChange(emptySet()) },
            )
        }

        items(
            items = tags,
            key = TagEntity::id,
            contentType = { "playlist_tag_filter" },
        ) { tag ->
            val accentColor =
                remember(tag.color) {
                    runCatching { Color(tag.color.toColorInt()) }.getOrNull()
                }
            PlaylistTagToggle(
                label = tag.name,
                checked = tag.id in selectedTagIds,
                accentColor = accentColor,
                onCheckedChange = { checked ->
                    onSelectedTagIdsChange(if (checked) selectedTagIds + tag.id else selectedTagIds - tag.id)
                },
            )
        }

        item(key = "manage_playlist_tags", contentType = "playlist_tag_action") {
            FilledTonalButton(
                onClick = onManageTagsClick,
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.heightIn(min = 40.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.add),
                    contentDescription = null,
                    modifier = Modifier.size(ButtonDefaults.IconSize),
                )
                Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                Text(text = stringResource(R.string.manage_tags))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistTagToggle(
    label: String,
    checked: Boolean,
    accentColor: Color?,
    onCheckedChange: (Boolean) -> Unit,
) {
    val motion = MaterialTheme.motionScheme
    ToggleButton(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = Modifier.heightIn(min = 40.dp),
    ) {
        if (accentColor != null) {
            Box(
                modifier =
                    Modifier
                        .size(10.dp)
                        .background(accentColor, CircleShape),
            )
            Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
        } else {
            AnimatedVisibility(
                visible = checked,
                enter = expandHorizontally(motion.fastSpatialSpec()) + fadeIn(motion.fastEffectsSpec()),
                exit = shrinkHorizontally(motion.fastSpatialSpec()) + fadeOut(motion.fastEffectsSpec()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        painter = painterResource(R.drawable.filter_alt),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.IconSpacing))
                }
            }
        }
        Text(
            text = label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
