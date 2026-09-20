/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyHorizontalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.IconButton
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.MenuState
import moe.rukamori.archivetune.ui.component.SongListItem
import moe.rukamori.archivetune.ui.component.SpeedDialGridItem
import moe.rukamori.archivetune.ui.component.YouTubeGridItem
import moe.rukamori.archivetune.ui.component.YouTubeListItem
import moe.rukamori.archivetune.ui.menu.AlbumMenu
import moe.rukamori.archivetune.ui.menu.ArtistMenu
import moe.rukamori.archivetune.ui.menu.PlaylistMenu
import moe.rukamori.archivetune.ui.menu.SongMenu
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.random.Random
import moe.rukamori.archivetune.ui.utils.SnapLayoutInfoProvider as buildSnapLayoutInfoProvider

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeCategoryChips(
    chips: List<HomePage.Chip>,
    selectedChip: HomePage.Chip?,
    onChipSelected: (HomePage.Chip) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 68.dp)
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        chips.forEach { chip ->
            val selected = chip == selectedChip
            FilterChip(
                selected = selected,
                onClick = { onChipSelected(chip) },
                label = {
                    Text(
                        text = chip.title,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon =
                    if (selected) {
                        {
                            Icon(
                                painter = painterResource(R.drawable.done),
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else {
                        null
                    },
                shapes = FilterChipDefaults.shapes(),
                colors =
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.78f),
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.94f),
                        selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    ),
                border = null,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    label: String? = null,
    thumbnail: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    actionContent: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .then(if (onClick != null && actionContent == null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        thumbnail?.invoke()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .then(if (onClick != null && actionContent != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleLargeEmphasized,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (actionContent != null) {
            actionContent()
        } else if (onClick != null) {
            Icon(
                painter = painterResource(R.drawable.arrow_forward),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
    ExperimentalFoundationApi::class,
)
@Composable
fun QuickPicksSection(
    quickPicks: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    displayMode: QuickPicksDisplayMode,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val distinctQuickPicks = remember(quickPicks) { quickPicks.distinctBy { it.id } }

    when (displayMode) {
        QuickPicksDisplayMode.CARD -> {
            BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
                val heroHeight =
                    when {
                        maxWidth >= 840.dp -> 380.dp
                        maxWidth >= 600.dp -> 356.dp
                        else -> 332.dp
                    }
                val heroMaxWidth =
                    (maxWidth - 48.dp)
                        .coerceAtLeast(232.dp)
                        .coerceAtMost(440.dp)
                val density = LocalDensity.current
                val requestWidthPx = with(density) { heroMaxWidth.roundToPx().coerceAtLeast(1) }
                val requestHeightPx = with(density) { heroHeight.roundToPx().coerceAtLeast(1) }

                HorizontalCenteredHeroCarousel(
                    state = rememberCarouselState { distinctQuickPicks.size },
                    maxItemWidth = heroMaxWidth,
                    itemSpacing = 10.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                ) { index ->
                    val song = distinctQuickPicks[index]
                    val isActive = song.id == mediaMetadata?.id
                    val context = LocalContext.current
                    val imageRequest =
                        remember(song.song.thumbnailUrl, requestWidthPx, requestHeightPx) {
                            ImageRequest
                                .Builder(context)
                                .data(song.song.thumbnailUrl)
                                .size(Size(requestWidthPx, requestHeightPx))
                                .crossfade(true)
                                .build()
                        }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .maskClip(MaterialTheme.shapes.extraLarge)
                                .maskBorder(
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                                    ),
                                    MaterialTheme.shapes.extraLarge,
                                ).focusable()
                                .combinedClickable(
                                    onClick = {
                                        if (isActive) {
                                            playerConnection.player.togglePlayPause()
                                        } else {
                                            playerConnection.playQueue(
                                                if (song.song.isLocal) {
                                                    ListQueue(items = listOf(song.toMediaItem()))
                                                } else {
                                                    YouTubeQueue.radio(song.toMediaMetadata())
                                                },
                                            )
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }
                                    },
                                ),
                    ) {
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.48f to Color.Black.copy(alpha = 0.08f),
                                            1f to Color.Black.copy(alpha = 0.84f),
                                        ),
                                    ),
                        )

                        if (isActive && isPlaying) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                                tonalElevation = 2.dp,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(14.dp)
                                        .size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.volume_up),
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp),
                        ) {
                            Text(
                                text = song.song.title,
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artists.joinToString { it.name },
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        QuickPicksDisplayMode.LIST -> {
            BoxWithConstraints(
                modifier = modifier.fillMaxWidth(),
            ) {
                val widthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                val itemWidth = maxWidth * widthFactor
                val lazyGridState = rememberLazyGridState()
                val snapLayoutInfoProvider =
                    remember(lazyGridState, widthFactor) {
                        buildSnapLayoutInfoProvider(
                            lazyGridState = lazyGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                layoutSize * widthFactor / 2f - itemSize / 2f
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
                            .height(ListItemHeight * 4),
                ) {
                    items(
                        items = distinctQuickPicks,
                        key = { it.id },
                        contentType = { "quick_pick_song" },
                    ) { song ->
                        SongListItem(
                            song = song,
                            showInLibraryIcon = true,
                            isActive = song.id == mediaMetadata?.id,
                            isPlaying = isPlaying,
                            isSwipeable = false,
                            trailingContent = {
                                IconButton(
                                    onClick = {
                                        menuState.show {
                                            SongMenu(
                                                originalSong = song,
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
                                    .width(itemWidth)
                                    .combinedClickable(
                                        onClick = {
                                            if (song.id == mediaMetadata?.id) {
                                                playerConnection.player.togglePlayPause()
                                            } else {
                                                playerConnection.playQueue(
                                                    if (song.song.isLocal) {
                                                        ListQueue(items = listOf(song.toMediaItem()))
                                                    } else {
                                                        YouTubeQueue.radio(song.toMediaMetadata())
                                                    },
                                                )
                                            }
                                        },
                                        onLongClick = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            menuState.show {
                                                SongMenu(
                                                    originalSong = song,
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
}

@OptIn(
    ExperimentalFoundationApi::class,
    ExperimentalMaterial3Api::class,
    ExperimentalMaterial3ExpressiveApi::class,
)
@Composable
fun RemoteQuickPicksSection(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    displayMode: QuickPicksDisplayMode,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val songs =
        remember(section.items) {
            section.items.filterIsInstance<SongItem>().distinctBy(SongItem::id)
        }

    when (displayMode) {
        QuickPicksDisplayMode.CARD -> {
            BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
                val heroHeight =
                    when {
                        maxWidth >= 840.dp -> 380.dp
                        maxWidth >= 600.dp -> 356.dp
                        else -> 332.dp
                    }
                val heroMaxWidth =
                    (maxWidth - 48.dp)
                        .coerceAtLeast(232.dp)
                        .coerceAtMost(440.dp)
                val density = LocalDensity.current
                val requestWidthPx = with(density) { heroMaxWidth.roundToPx().coerceAtLeast(1) }
                val requestHeightPx = with(density) { heroHeight.roundToPx().coerceAtLeast(1) }

                HorizontalCenteredHeroCarousel(
                    state = rememberCarouselState { songs.size },
                    maxItemWidth = heroMaxWidth,
                    itemSpacing = 10.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                ) { index ->
                    val song = songs[index]
                    val isActive = song.id == mediaMetadata?.id
                    val context = LocalContext.current
                    val imageRequest =
                        remember(song.thumbnail, requestWidthPx, requestHeightPx) {
                            ImageRequest
                                .Builder(context)
                                .data(song.thumbnail)
                                .size(Size(requestWidthPx, requestHeightPx))
                                .crossfade(true)
                                .build()
                        }

                    Box(
                        modifier =
                            Modifier
                                .fillMaxSize()
                                .maskClip(MaterialTheme.shapes.extraLarge)
                                .maskBorder(
                                    BorderStroke(
                                        1.dp,
                                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.72f),
                                    ),
                                    MaterialTheme.shapes.extraLarge,
                                ).focusable()
                                .combinedClickable(
                                    onClick = {
                                        if (isActive) {
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
                        AsyncImage(
                            model = imageRequest,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        Box(
                            modifier =
                                Modifier
                                    .fillMaxSize()
                                    .background(
                                        Brush.verticalGradient(
                                            0f to Color.Transparent,
                                            0.48f to Color.Black.copy(alpha = 0.08f),
                                            1f to Color.Black.copy(alpha = 0.84f),
                                        ),
                                    ),
                        )

                        if (isActive && isPlaying) {
                            Surface(
                                color = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary,
                                shape = CircleShape,
                                tonalElevation = 2.dp,
                                modifier =
                                    Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(14.dp)
                                        .size(36.dp),
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        painter = painterResource(R.drawable.volume_up),
                                        contentDescription = null,
                                        modifier = Modifier.size(19.dp),
                                    )
                                }
                            }
                        }

                        Column(
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                            modifier =
                                Modifier
                                    .align(Alignment.BottomStart)
                                    .padding(20.dp),
                        ) {
                            Text(
                                text = song.title,
                                style = MaterialTheme.typography.titleLargeEmphasized,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = song.artists.joinToString { artist -> artist.name },
                                style = MaterialTheme.typography.bodyLarge,
                                color = Color.White.copy(alpha = 0.78f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        QuickPicksDisplayMode.LIST -> {
            BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
                val widthFactor = if (maxWidth * 0.475f >= 320.dp) 0.475f else 0.9f
                val itemWidth = maxWidth * widthFactor
                val lazyGridState = rememberLazyGridState()
                val snapLayoutInfoProvider =
                    remember(lazyGridState, widthFactor) {
                        buildSnapLayoutInfoProvider(
                            lazyGridState = lazyGridState,
                            positionInLayout = { layoutSize, itemSize ->
                                layoutSize * widthFactor / 2f - itemSize / 2f
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
                            .height(ListItemHeight * 4),
                ) {
                    items(
                        items = songs,
                        key = SongItem::id,
                        contentType = { "remote_quick_pick_song" },
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
                                    .width(itemWidth)
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
                        )
                    }
                }
            }
        }
    }
}

private const val SpeedDialGridRows = 3
private const val SpeedDialGridColumns = 3
private const val SpeedDialItemsPerPage = SpeedDialGridRows * SpeedDialGridColumns

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SpeedDialSection(
    speedDialItems: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    data class SpeedDialTile(
        val key: String,
        val localItem: LocalItem?,
        val ytItem: YTItem?,
    )

    val distinctSpeedDial =
        remember(speedDialItems) {
            speedDialItems
                .distinctBy {
                    when (it) {
                        is Song -> "song_${it.id}"
                        is Album -> "album_${it.id}"
                        is Artist -> "artist_${it.id}"
                        is Playlist -> "playlist_${it.id}"
                    }
                }.take(24)
        }
    val speedDialSongs = remember(distinctSpeedDial) { distinctSpeedDial.filterIsInstance<Song>() }
    val speedDialSongIndexById =
        remember(speedDialSongs) {
            speedDialSongs.mapIndexed { index, song -> song.id to index }.toMap()
        }
    val spacing = 10.dp

    val tiles =
        remember(distinctSpeedDial) {
            buildList {
                distinctSpeedDial.forEach { localItem ->
                    val key =
                        when (localItem) {
                            is Song -> "song_${localItem.id}"
                            is Album -> "album_${localItem.id}"
                            is Artist -> "artist_${localItem.id}"
                            is Playlist -> "playlist_${localItem.id}"
                        }
                    val ytItem =
                        when (localItem) {
                            is Song -> {
                                SongItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rukamori.archivetune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    thumbnail = localItem.song.thumbnailUrl.orEmpty(),
                                    explicit = localItem.song.explicit,
                                )
                            }

                            is Album -> {
                                AlbumItem(
                                    browseId = localItem.id,
                                    playlistId = localItem.album.playlistId.orEmpty(),
                                    title = localItem.title,
                                    artists =
                                        localItem.artists.map {
                                            moe.rukamori.archivetune.innertube.models
                                                .Artist(name = it.name, id = it.id)
                                        },
                                    year = localItem.album.year,
                                    thumbnail = localItem.album.thumbnailUrl.orEmpty(),
                                )
                            }

                            is Artist -> {
                                ArtistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    thumbnail = localItem.artist.thumbnailUrl,
                                    channelId = localItem.artist.channelId,
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                )
                            }

                            is Playlist -> {
                                PlaylistItem(
                                    id = localItem.id,
                                    title = localItem.title,
                                    author = null,
                                    songCountText = localItem.songCount.toString(),
                                    thumbnail = localItem.thumbnails.firstOrNull(),
                                    playEndpoint = null,
                                    shuffleEndpoint = null,
                                    radioEndpoint = null,
                                    isEditable = localItem.playlist.isEditable,
                                )
                            }
                        }
                    add(SpeedDialTile(key = key, localItem = localItem, ytItem = ytItem))
                }
                add(SpeedDialTile(key = "random", localItem = null, ytItem = null))
            }
        }
    val tilePages =
        remember(tiles) {
            tiles.chunked(SpeedDialItemsPerPage)
        }
    val visibleGridRows =
        remember(tilePages) {
            if (tilePages.size == 1) {
                ((tilePages.first().size + SpeedDialGridColumns - 1) / SpeedDialGridColumns)
                    .coerceIn(1, SpeedDialGridRows)
            } else {
                SpeedDialGridRows
            }
        }
    val pagerState =
        rememberPagerState(
            pageCount = { tilePages.size },
        )

    LaunchedEffect(tilePages.size) {
        if (pagerState.currentPage > tilePages.lastIndex) {
            pagerState.scrollToPage(tilePages.lastIndex.coerceAtLeast(0))
        }
    }

    fun playSpeedDialQueue(startIndex: Int) {
        if (speedDialSongs.isEmpty()) return
        playerConnection.playQueue(
            ListQueue(
                title = context.getString(R.string.speed_dial),
                items = speedDialSongs.map { it.toMediaItem() },
                startIndex = startIndex,
            ),
        )
    }

    val selectedDotIndex by
        remember(pagerState, tilePages) {
            derivedStateOf {
                (pagerState.currentPage + pagerState.currentPageOffsetFraction)
                    .roundToInt()
                    .coerceIn(0, (tilePages.size - 1).coerceAtLeast(0))
            }
        }
    val motionScheme = MaterialTheme.motionScheme

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 1.dp,
        modifier =
            modifier
                .padding(horizontal = 12.dp)
                .fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(vertical = 12.dp)) {
            BoxWithConstraints(
                modifier =
                    Modifier
                        .padding(horizontal = 12.dp)
                        .fillMaxWidth(),
            ) {
                val tileSize = (maxWidth - spacing * (SpeedDialGridColumns - 1)) / SpeedDialGridColumns
                val gridHeight = (tileSize * visibleGridRows) + (spacing * (visibleGridRows - 1))

                HorizontalPager(
                    state = pagerState,
                    pageSize = PageSize.Fill,
                    pageSpacing = spacing,
                    key = { page ->
                        tilePages.getOrNull(page)?.firstOrNull()?.key ?: "stale_speed_dial_page_$page"
                    },
                    verticalAlignment = Alignment.Top,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(gridHeight),
                ) { page ->
                    val pageTiles = tilePages.getOrNull(page)
                    if (pageTiles == null) {
                        Box(modifier = Modifier.fillMaxSize())
                        return@HorizontalPager
                    }

                    Column(
                        verticalArrangement = Arrangement.spacedBy(spacing),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        pageTiles
                            .chunked(SpeedDialGridColumns)
                            .forEach { rowTiles ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(spacing),
                                    modifier = Modifier.fillMaxWidth(),
                                ) {
                                    rowTiles.forEach { tile ->
                                        val localItem = tile.localItem
                                        val ytItem = tile.ytItem
                                        if (localItem == null || ytItem == null) {
                                            SpeedDialRandomTile(
                                                onClick = {
                                                    if (speedDialSongs.isNotEmpty()) {
                                                        playSpeedDialQueue(Random.nextInt(speedDialSongs.size))
                                                    }
                                                },
                                                modifier = Modifier.size(tileSize),
                                            )
                                        } else {
                                            val isActive =
                                                when (localItem) {
                                                    is Song -> localItem.id == mediaMetadata?.id
                                                    is Album -> localItem.id == mediaMetadata?.album?.id
                                                    is Artist -> false
                                                    is Playlist -> false
                                                }
                                            val songIndex =
                                                if (localItem is Song) speedDialSongIndexById[localItem.id] ?: 0 else 0

                                            Box(
                                                modifier =
                                                    Modifier
                                                        .size(tileSize)
                                                        .clip(MaterialTheme.shapes.large)
                                                        .focusable()
                                                        .combinedClickable(
                                                            onClick = {
                                                                when (localItem) {
                                                                    is Song -> {
                                                                        if (isActive) {
                                                                            playerConnection.player.togglePlayPause()
                                                                        } else {
                                                                            playSpeedDialQueue(songIndex)
                                                                        }
                                                                    }

                                                                    is Album -> {
                                                                        navController.navigate("album/${localItem.id}")
                                                                    }

                                                                    is Artist -> {
                                                                        navController.navigate("artist/${localItem.id}")
                                                                    }

                                                                    is Playlist -> {
                                                                        navController.navigate("local_playlist/${localItem.id}")
                                                                    }
                                                                }
                                                            },
                                                            onLongClick = {
                                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                                menuState.show {
                                                                    when (localItem) {
                                                                        is Song -> {
                                                                            SongMenu(
                                                                                originalSong = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Album -> {
                                                                            AlbumMenu(
                                                                                originalAlbum = localItem,
                                                                                navController = navController,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Artist -> {
                                                                            ArtistMenu(
                                                                                originalArtist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }

                                                                        is Playlist -> {
                                                                            PlaylistMenu(
                                                                                playlist = localItem,
                                                                                coroutineScope = scope,
                                                                                onDismiss = menuState::dismiss,
                                                                            )
                                                                        }
                                                                    }
                                                                }
                                                            },
                                                        ),
                                            ) {
                                                SpeedDialGridItem(
                                                    item = ytItem,
                                                    isPinned = true,
                                                    isActive = isActive,
                                                    isPlaying = isPlaying,
                                                )
                                            }
                                        }
                                    }
                                    repeat(SpeedDialGridColumns - rowTiles.size) {
                                        Spacer(modifier = Modifier.size(tileSize))
                                    }
                                }
                            }
                    }
                }
            }

            if (tilePages.size > 1) {
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    repeat(tilePages.size) { index ->
                        val isSelected = index == selectedDotIndex
                        val dotColor by animateColorAsState(
                            targetValue =
                                if (isSelected) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceContainerHighest
                                },
                            animationSpec = motionScheme.defaultEffectsSpec(),
                            label = "speedDialDotColor",
                        )
                        val dotWidth by animateDpAsState(
                            targetValue = if (isSelected) 22.dp else 8.dp,
                            animationSpec = motionScheme.defaultSpatialSpec(),
                            label = "speedDialDotWidth",
                        )
                        Surface(
                            color = dotColor,
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier =
                                Modifier
                                    .width(dotWidth)
                                    .height(8.dp),
                        ) {}
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SpeedDialRandomTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        shape = MaterialTheme.shapes.large,
        tonalElevation = 2.dp,
        modifier =
            modifier
                .aspectRatio(1f)
                .combinedClickable(onClick = onClick),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                repeat(3) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape,
                        modifier = Modifier.size(18.dp),
                    ) {}
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun KeepListeningSection(
    keepListening: List<LocalItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctItems = remember(keepListening) { keepListening.distinctBy(LocalItem::stableHomeKey) }
    val layoutDirection = LocalLayoutDirection.current
    val systemBarPadding =
        WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val startPadding = systemBarPadding.calculateStartPadding(layoutDirection) + 16.dp
        val endPadding = systemBarPadding.calculateEndPadding(layoutDirection) + 16.dp
        val cardWidth = (maxWidth - startPadding - 40.dp).coerceIn(280.dp, 360.dp)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
        ) {
            items(
                items = distinctItems,
                key = LocalItem::stableHomeKey,
                contentType = LocalItem::homeContentType,
            ) { item ->
                KeepListeningResumeCard(
                    item = item,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    haptic = haptic,
                    scope = scope,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun KeepListeningResumeCard(
    item: LocalItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val isActive =
        when (item) {
            is Song -> item.id == mediaMetadata?.id
            is Album -> item.id == mediaMetadata?.album?.id
            is Artist, is Playlist -> false
        }
    val onClick =
        remember(item, mediaMetadata?.id, playerConnection, navController) {
            {
                when (item) {
                    is Song -> {
                        if (item.id == mediaMetadata?.id) {
                            playerConnection.player.togglePlayPause()
                        } else {
                            playerConnection.playQueue(
                                if (item.song.isLocal) {
                                    ListQueue(items = listOf(item.toMediaItem()))
                                } else {
                                    YouTubeQueue.radio(item.toMediaMetadata())
                                },
                            )
                        }
                    }

                    is Album -> navController.navigate("album/${item.id}")
                    is Artist -> navController.navigate("artist/${item.id}")
                    is Playlist -> navController.navigate("local_playlist/${item.id}")
                }
            }
        }
    val onLongClick =
        remember(item, navController, menuState, haptic, scope) {
            {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                menuState.show {
                    when (item) {
                        is Song -> {
                            SongMenu(
                                originalSong = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }

                        is Album -> {
                            AlbumMenu(
                                originalAlbum = item,
                                navController = navController,
                                onDismiss = menuState::dismiss,
                            )
                        }

                        is Artist -> {
                            ArtistMenu(
                                originalArtist = item,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss,
                            )
                        }

                        is Playlist -> {
                            PlaylistMenu(
                                playlist = item,
                                coroutineScope = scope,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    }
                }
            }
        }
    val subtitle =
        when (item) {
            is Song ->
                joinByBullet(
                    item.artists.joinToString { it.name },
                    makeTimeString(item.song.duration.toLong() * 1000L),
                )

            is Album ->
                joinByBullet(
                    item.artists.joinToString { it.name },
                    pluralStringResource(R.plurals.n_song, item.album.songCount, item.album.songCount),
                )

            is Artist -> pluralStringResource(R.plurals.n_song, item.songCount, item.songCount)
            is Playlist -> pluralStringResource(R.plurals.n_song, item.songCount, item.songCount)
        }
    val thumbnailShape = if (item is Artist) CircleShape else MaterialTheme.shapes.large
    val placeholderIcon =
        when (item) {
            is Song -> R.drawable.music_note
            is Album -> R.drawable.album
            is Artist -> R.drawable.artist
            is Playlist -> R.drawable.queue_music
        }

    Surface(
        color =
            if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            },
        contentColor =
            if (isActive) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier =
            modifier
                .heightIn(min = 120.dp)
                .focusable()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            ItemThumbnail(
                thumbnailUrl = item.homeThumbnailUrl(),
                isActive = isActive,
                isPlaying = isPlaying,
                shape = thumbnailShape,
                placeholderIconRes = placeholderIcon,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(96.dp),
            )
            Column(
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color =
                        if (isActive) {
                            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item is Song) {
                FilledIconButton(
                    onClick = onClick,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter =
                            painterResource(
                                if (isActive && isPlaying) R.drawable.pause else R.drawable.play,
                            ),
                        contentDescription =
                            stringResource(if (isActive && isPlaying) R.string.widget_pause else R.string.play),
                    )
                }
            } else {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.arrow_forward),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun LocalItem.stableHomeKey(): String =
    when (this) {
        is Song -> "song_$id"
        is Album -> "album_$id"
        is Artist -> "artist_$id"
        is Playlist -> "playlist_$id"
    }

private fun LocalItem.homeContentType(): String =
    when (this) {
        is Song -> "keep_listening_song"
        is Album -> "keep_listening_album"
        is Artist -> "keep_listening_artist"
        is Playlist -> "keep_listening_playlist"
    }

private fun LocalItem.homeThumbnailUrl(): String? =
    when (this) {
        is Playlist -> thumbnails.firstOrNull()
        else -> thumbnailUrl
    }

private fun YTItem.stableHomeKey(): String =
    when (this) {
        is SongItem -> "song_$id"
        is AlbumItem -> "album_$id"
        is ArtistItem -> "artist_$id"
        is PlaylistItem -> "playlist_$id"
        is PodcastItem -> "podcast_$id"
        is EpisodeItem -> "episode_$id"
    }

/**
 * Forgotten Favorites section - horizontal grid of songs
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ForgottenFavoritesSection(
    forgottenFavorites: List<Song>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    horizontalLazyGridItemWidth: Dp,
    lazyGridState: LazyGridState,
    snapLayoutInfoProvider: SnapLayoutInfoProvider,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val rows = min(4, forgottenFavorites.size)
    val distinctForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }

    LazyHorizontalGrid(
        state = lazyGridState,
        rows = GridCells.Fixed(rows),
        flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier =
            modifier
                .fillMaxWidth()
                .height(ListItemHeight * rows),
    ) {
        items(
            items = distinctForgottenFavorites,
            key = { it.id },
            contentType = { "forgotten_favorite_song" },
        ) { song ->
            SongListItem(
                song = song,
                showInLibraryIcon = true,
                isActive = song.id == mediaMetadata?.id,
                isPlaying = isPlaying,
                isSwipeable = false,
                trailingContent = {
                    IconButton(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            menuState.show {
                                SongMenu(
                                    originalSong = song,
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
                        .focusable()
                        .combinedClickable(
                            onClick = {
                                if (song.id == mediaMetadata?.id) {
                                    playerConnection.player.togglePlayPause()
                                } else {
                                    playerConnection.playQueue(
                                        if (song.song.isLocal) {
                                            ListQueue(items = listOf(song.toMediaItem()))
                                        } else {
                                            YouTubeQueue.radio(song.toMediaMetadata())
                                        },
                                    )
                                }
                            },
                            onLongClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    SongMenu(
                                        originalSong = song,
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

/**
 * Account Playlists section - horizontal row of YouTube playlists
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AccountPlaylistsSection(
    accountPlaylists: List<PlaylistItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctPlaylists = remember(accountPlaylists) { accountPlaylists.distinctBy { it.id } }

    LazyRow(
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier = modifier,
    ) {
        items(
            items = distinctPlaylists,
            key = { it.id },
            contentType = { "account_playlist" },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SimilarRecommendationsSection(
    recommendations: List<SimilarRecommendation>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val distinctRecommendations =
        remember(recommendations) {
            recommendations.distinctBy { it.title.stableHomeKey() }
        }
    val layoutDirection = LocalLayoutDirection.current
    val systemBarPadding =
        WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val startPadding = systemBarPadding.calculateStartPadding(layoutDirection) + 16.dp
        val endPadding = systemBarPadding.calculateEndPadding(layoutDirection) + 16.dp
        val deckWidth = (maxWidth - startPadding - 24.dp).coerceIn(312.dp, 480.dp)

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
        ) {
            items(
                items = distinctRecommendations,
                key = { recommendation -> recommendation.title.stableHomeKey() },
                contentType = { "similar_discovery_deck" },
            ) { recommendation ->
                SimilarDiscoveryDeck(
                    recommendation = recommendation,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    haptic = haptic,
                    scope = scope,
                    modifier = Modifier.width(deckWidth),
                )
            }
        }
    }
}

@Composable
private fun SimilarDiscoveryDeck(
    recommendation: SimilarRecommendation,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val source = recommendation.title
    val itemRows =
        remember(recommendation.items) {
            recommendation.items
                .distinctBy(YTItem::stableHomeKey)
                .take(4)
                .chunked(2)
        }
    val onHeaderClick: (() -> Unit)? =
        remember(source, navController) {
            when (source) {
                is Song -> source.album?.id?.let { albumId -> ({ navController.navigate("album/$albumId") }) }
                is Album -> ({ navController.navigate("album/${source.id}") })
                is Artist -> ({ navController.navigate("artist/${source.id}") })
                is Playlist -> ({ navController.navigate("local_playlist/${source.id}") })
            }
        }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.extraLarge,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(bottom = 8.dp)) {
            HomeSectionHeader(
                label = stringResource(R.string.similar_to),
                title = source.title,
                thumbnail =
                    source.homeThumbnailUrl()?.let { thumbnailUrl ->
                        {
                            ItemThumbnail(
                                thumbnailUrl = thumbnailUrl,
                                isActive = false,
                                isPlaying = false,
                                shape = if (source is Artist) CircleShape else MaterialTheme.shapes.medium,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(48.dp),
                            )
                        }
                    },
                onClick = onHeaderClick,
            )
            itemRows.forEach { rowItems ->
                Row(modifier = Modifier.fillMaxWidth()) {
                    rowItems.forEach { item ->
                        YouTubeGridItemWrapper(
                            item = item,
                            mediaMetadata = mediaMetadata,
                            isPlaying = isPlaying,
                            navController = navController,
                            playerConnection = playerConnection,
                            menuState = menuState,
                            haptic = haptic,
                            scope = scope,
                            fillMaxWidth = true,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * HomePage Section - a single section from YouTube home page
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomePageSectionContent(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    onOpenRemoteItem: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (section.featuredCards.isNotEmpty()) {
        FeaturedPlaylistCardsSection(
            section = section,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            navController = navController,
            playerConnection = playerConnection,
            menuState = menuState,
            scope = scope,
            modifier = modifier,
        )
        return
    }

    LazyRow(
        contentPadding =
            WindowInsets.systemBars
                .only(WindowInsetsSides.Horizontal)
                .asPaddingValues(),
        modifier = modifier,
    ) {
        items(
            items = section.items,
            key = { it.id },
            contentType = { item -> item::class },
        ) { item ->
            YouTubeGridItemWrapper(
                item = item,
                mediaMetadata = mediaMetadata,
                isPlaying = isPlaying,
                navController = navController,
                playerConnection = playerConnection,
                menuState = menuState,
                haptic = haptic,
                scope = scope,
                onOpenRemoteItem = onOpenRemoteItem,
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedPlaylistCardsSection(
    section: HomePage.Section,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val songsById = remember(section.items) { section.items.filterIsInstance<SongItem>().associateBy(SongItem::id) }
    val horizontalInsets = WindowInsets.systemBars.only(WindowInsetsSides.Horizontal).asPaddingValues()
    val layoutDirection = LocalLayoutDirection.current
    val startPadding = horizontalInsets.calculateStartPadding(layoutDirection) + 16.dp
    val endPadding = horizontalInsets.calculateEndPadding(layoutDirection) + 16.dp

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val cardWidth = (maxWidth - 40.dp).coerceAtLeast(264.dp).coerceAtMost(520.dp)
        LazyRow(
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(
                items = section.featuredCards,
                key = HomePage.Section.FeaturedCard::id,
                contentType = { "featured_playlist_card" },
            ) { card ->
                val songs = remember(card.itemIds, songsById) { card.itemIds.mapNotNull(songsById::get).take(3) }
                FeaturedPlaylistCard(
                    card = card,
                    songs = songs,
                    mediaMetadata = mediaMetadata,
                    isPlaying = isPlaying,
                    navController = navController,
                    playerConnection = playerConnection,
                    menuState = menuState,
                    scope = scope,
                    modifier = Modifier.width(cardWidth),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FeaturedPlaylistCard(
    card: HomePage.Section.FeaturedCard,
    songs: List<SongItem>,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    scope: CoroutineScope,
    modifier: Modifier = Modifier,
) {
    val playlist = remember(card) { card.toPlaylistItem() }
    val openPlaylist =
        remember(playlist.id, navController) {
            { navController.navigate("online_playlist/${playlist.id}") }
        }
    val sequentialEndpoint = remember(card.playEndpoint, card.id) { card.playEndpoint ?: WatchEndpoint(playlistId = card.id) }
    val playPlaylist =
        remember(sequentialEndpoint, playerConnection) {
            { playerConnection.playQueue(YouTubeQueue.playlist(sequentialEndpoint)) }
        }
    val secondaryEndpoint = card.radioEndpoint ?: card.shuffleEndpoint
    val playSecondary =
        remember(secondaryEndpoint, playerConnection) {
            secondaryEndpoint?.let { endpoint -> { playerConnection.playQueue(YouTubeQueue.playlist(endpoint)) } }
        }
    val showPlaylistMenu =
        remember(playlist, menuState, scope) {
            {
                menuState.show {
                    YouTubePlaylistMenu(
                        playlist = playlist,
                        coroutineScope = scope,
                        onDismiss = menuState::dismiss,
                    )
                }
            }
        }

    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.58f)),
        modifier = modifier,
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp)
                        .then(
                            Modifier.combinedClickable(
                                onClick = openPlaylist,
                                onLongClick = showPlaylistMenu,
                            ),
                        ),
            ) {
                card.thumbnail?.let { thumbnail ->
                    AsyncImage(
                        model =
                            thumbnail.resize(
                                width = FeaturedPlaylistArtworkSizePx,
                                height = FeaturedPlaylistArtworkSizePx,
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(96.dp)
                                .clip(MaterialTheme.shapes.medium),
                    )
                } ?: run {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(96.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    card.subtitle?.let { subtitle ->
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }

            songs.forEachIndexed { index, song ->
                val songEndpoint =
                    remember(song.endpoint, song.id, card.id, index) {
                        song.endpoint?.let { endpoint ->
                            endpoint.copy(
                                playlistId = endpoint.playlistId ?: card.id,
                                index = endpoint.index ?: index,
                            )
                        } ?: WatchEndpoint(
                            videoId = song.id,
                            playlistId = card.id,
                            index = index,
                        )
                    }
                val playSong =
                    remember(song.id, songEndpoint, mediaMetadata?.id, playerConnection) {
                        {
                            if (song.id == mediaMetadata?.id) {
                                playerConnection.player.togglePlayPause()
                            } else {
                                playerConnection.playQueue(YouTubeQueue.playlist(songEndpoint))
                            }
                        }
                    }
                YouTubeListItem(
                    item = song,
                    isActive = song.id == mediaMetadata?.id,
                    isPlaying = isPlaying,
                    isSwipeable = false,
                    showActiveContainer = false,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .combinedClickable(
                                onClick = playSong,
                                onLongClick = {
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

            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                FilledIconButton(
                    onClick = playPlaylist,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = stringResource(R.string.play),
                    )
                }
                if (playSecondary != null) {
                    FilledTonalIconButton(
                        onClick = playSecondary,
                        modifier = Modifier.size(56.dp),
                    ) {
                        Icon(
                            painter = painterResource(if (card.radioEndpoint != null) R.drawable.radio else R.drawable.shuffle),
                            contentDescription =
                                stringResource(
                                    if (card.radioEndpoint != null) {
                                        R.string.start_radio
                                    } else {
                                        R.string.shuffle
                                    },
                                ),
                        )
                    }
                }
                FilledTonalIconButton(
                    onClick = showPlaylistMenu,
                    modifier = Modifier.size(56.dp),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.more_horiz),
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            }
        }
    }
}

private fun HomePage.Section.FeaturedCard.toPlaylistItem(): PlaylistItem {
    return PlaylistItem(
        id = id,
        title = title,
        author = subtitle?.let { moe.rukamori.archivetune.innertube.models.Artist(name = it, id = null) },
        songCountText = null,
        thumbnail = thumbnail,
        playEndpoint = playEndpoint,
        shuffleEndpoint = shuffleEndpoint,
        radioEndpoint = radioEndpoint,
    )
}

// ============== Helper Composables ==============

/**
 * Wrapper for YouTube grid items with click handling
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun YouTubeGridItemWrapper(
    item: YTItem,
    mediaMetadata: MediaMetadata?,
    isPlaying: Boolean,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    scope: CoroutineScope,
    onOpenRemoteItem: ((String) -> Unit)? = null,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) {
    YouTubeGridItem(
        item = item,
        isActive = item.id in listOf(mediaMetadata?.album?.id, mediaMetadata?.id),
        isPlaying = isPlaying,
        coroutineScope = scope,
        fillMaxWidth = fillMaxWidth,
        modifier =
            modifier
                .focusable()
                .combinedClickable(
                    onClick = {
                        when (item) {
                            is SongItem -> {
                                playerConnection.playQueue(
                                    YouTubeQueue(
                                        item.endpoint ?: WatchEndpoint(videoId = item.id),
                                        item.toMediaMetadata(),
                                    ),
                                )
                            }

                            is AlbumItem -> {
                                navController.navigate("album/${item.id}")
                            }

                            is ArtistItem -> {
                                navController.navigate("artist/${item.id}")
                            }

                            is PlaylistItem -> {
                                navController.navigate("online_playlist/${item.id}")
                            }

                            is PodcastItem, is EpisodeItem -> onOpenRemoteItem?.invoke(item.id)
                        }
                    },
                    onLongClick =
                        if (item is PodcastItem || item is EpisodeItem) {
                            null
                        } else {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                menuState.show {
                                    when (item) {
                                        is SongItem -> {
                                            YouTubeSongMenu(
                                                song = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }

                                        is AlbumItem -> {
                                            YouTubeAlbumMenu(
                                                albumItem = item,
                                                navController = navController,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }

                                        is ArtistItem -> {
                                            YouTubeArtistMenu(
                                                artist = item,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }

                                        is PlaylistItem -> {
                                            YouTubePlaylistMenu(
                                                playlist = item,
                                                coroutineScope = scope,
                                                onDismiss = menuState::dismiss,
                                            )
                                        }

                                        is PodcastItem, is EpisodeItem -> Unit
                                    }
                                }
                            }
                        },
                ),
    )
}

/**
 * Account playlist navigation title with image
 */
@Composable
fun AccountPlaylistsTitle(
    accountName: String,
    accountImageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    HomeSectionHeader(
        label = stringResource(R.string.your_youtube_playlists),
        title = accountName.ifBlank { stringResource(R.string.account) },
        thumbnail = {
            if (accountImageUrl != null) {
                val context = LocalContext.current
                val avatarSizePx =
                    with(LocalDensity.current) {
                        ListThumbnailSize.roundToPx().coerceAtLeast(1)
                    }
                val imageRequest =
                    remember(accountImageUrl, avatarSizePx) {
                        ImageRequest
                            .Builder(context)
                            .data(accountImageUrl)
                            .size(Size(avatarSizePx, avatarSizePx))
                            .diskCachePolicy(CachePolicy.ENABLED)
                            .diskCacheKey(accountImageUrl)
                            .crossfade(true)
                            .build()
                    }
                AsyncImage(
                    model = imageRequest,
                    placeholder = painterResource(id = R.drawable.person),
                    error = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .size(ListThumbnailSize)
                            .clip(CircleShape),
                )
            } else {
                Icon(
                    painter = painterResource(id = R.drawable.person),
                    contentDescription = null,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            }
        },
        onClick = onClick,
        modifier = modifier,
    )
}

/**
 * HomePage section navigation title
 */
@Composable
fun HomePageSectionTitle(
    section: HomePage.Section,
    navController: NavController,
    modifier: Modifier = Modifier,
    onPlayAll: (() -> Unit)? = null,
) {
    HomeSectionHeader(
        title = section.title,
        label = section.label,
        thumbnail =
            section.thumbnail?.let { thumbnailUrl ->
                {
                    val shape =
                        if (section.endpoint?.isArtistEndpoint == true) {
                            CircleShape
                        } else {
                            RoundedCornerShape(ThumbnailCornerRadius)
                        }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        modifier =
                            Modifier
                                .size(ListThumbnailSize)
                                .clip(shape),
                    )
                }
            },
        onClick =
            section.endpoint?.browseId?.let { browseId ->
                {
                    if (browseId == "FEmusic_moods_and_genres") {
                        navController.navigate(Screens.MoodAndGenres.route)
                    } else {
                        navController.navigate("browse/$browseId")
                    }
                }
            },
        actionContent =
            onPlayAll?.let { playAll ->
                {
                    OutlinedButton(
                        onClick = playAll,
                        modifier = Modifier.heightIn(min = 40.dp).widthIn(min = 88.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.play_all),
                            maxLines = 1,
                        )
                    }
                }
            },
        modifier = modifier,
    )
}

private const val FeaturedPlaylistArtworkSizePx = 320
