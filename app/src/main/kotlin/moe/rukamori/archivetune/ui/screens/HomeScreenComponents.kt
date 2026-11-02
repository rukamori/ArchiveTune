/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.snapping.SnapPosition
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PageSize
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.carousel.CarouselItemScope
import androidx.compose.material3.carousel.HorizontalCenteredHeroCarousel
import androidx.compose.material3.carousel.rememberCarouselState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.QuickPicksDisplayMode
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
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import java.time.LocalTime
import kotlin.math.roundToInt
import kotlin.random.Random

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
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        chips.forEach { chip ->
            ToggleButton(
                checked = chip == selectedChip,
                onCheckedChange = { onChipSelected(chip) },
                colors =
                    ToggleButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        checkedContainerColor = MaterialTheme.colorScheme.primary,
                        checkedContentColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                elevation = null,
            ) {
                Text(
                    text = chip.title,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun HomeGreetingHeader(
    accountName: String,
    modifier: Modifier = Modifier,
) {
    val hour by produceState(initialValue = LocalTime.now().hour) {
        while (true) {
            val now = LocalTime.now()
            delay(((60 - now.minute) * 60L - now.second) * 1_000L)
            value = LocalTime.now().hour
        }
    }
    val greetingRes =
        when (hour) {
            in 5..11 -> R.string.greeting_morning
            in 12..16 -> R.string.greeting_afternoon
            in 17..21 -> R.string.greeting_evening
            else -> R.string.greeting_night
        }

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 12.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(96.dp)
                    .background(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialShapes.Cookie9Sided.toShape(),
                    ),
        ) {
            Icon(
                painter = painterResource(R.drawable.about_appbar),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(56.dp),
            )
        }
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.app_name),
            style = MaterialTheme.typography.labelLargeEmphasized,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
        )
        Text(
            text = stringResource(greetingRes),
            style = MaterialTheme.typography.displaySmallEmphasized,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.semantics { heading() },
        )
        if (accountName.isNotBlank()) {
            Text(
                text = accountName,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
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
                .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        thumbnail?.invoke()
        Column(
            verticalArrangement = Arrangement.Center,
            modifier =
                Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .semantics(mergeDescendants = true) { heading() }
                    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        ) {
            label?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelLargeEmphasized,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        actionContent?.invoke()
        if (onClick != null) {
            FilledTonalIconButton(
                onClick = onClick,
                shapes = IconButtonDefaults.shapes(),
            ) {
                Icon(
                    painter = painterResource(R.drawable.arrow_forward),
                    contentDescription = stringResource(R.string.see_all),
                )
            }
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
                    preferredItemWidth = heroMaxWidth,
                    itemSpacing = 10.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                ) { index ->
                    val song = distinctQuickPicks[index]
                    val isActive = song.id == mediaMetadata?.id
                    QuickPickHeroCard(
                        title = song.song.title,
                        subtitle = song.artists.joinToString { it.name },
                        thumbnailUrl = song.song.thumbnailUrl,
                        requestWidthPx = requestWidthPx,
                        requestHeightPx = requestHeightPx,
                        isActive = isActive,
                        isPlaying = isPlaying,
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
                    )
                }
            }
        }

        QuickPicksDisplayMode.LIST -> {
            SongStackShelf(
                items = distinctQuickPicks,
                key = { it.id },
                state = rememberLazyListState(),
                modifier = modifier,
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
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                    },
                    modifier =
                        Modifier
                            .fillMaxWidth()
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
                    preferredItemWidth = heroMaxWidth,
                    itemSpacing = 10.dp,
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .height(heroHeight),
                ) { index ->
                    val song = songs[index]
                    val isActive = song.id == mediaMetadata?.id
                    QuickPickHeroCard(
                        title = song.title,
                        subtitle = song.artists.joinToString { artist -> artist.name },
                        thumbnailUrl = song.thumbnail,
                        requestWidthPx = requestWidthPx,
                        requestHeightPx = requestHeightPx,
                        isActive = isActive,
                        isPlaying = isPlaying,
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
                    )
                }
            }
        }

        QuickPicksDisplayMode.LIST -> {
            SongStackShelf(
                items = songs,
                key = SongItem::id,
                state = rememberLazyListState(),
                modifier = modifier,
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
                                contentDescription = stringResource(R.string.more_options),
                            )
                        }
                    },
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
                )
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
private fun CarouselItemScope.QuickPickHeroCard(
    title: String,
    subtitle: String,
    thumbnailUrl: String?,
    requestWidthPx: Int,
    requestHeightPx: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val imageRequest =
        remember(thumbnailUrl, requestWidthPx, requestHeightPx) {
            ImageRequest
                .Builder(context)
                .data(thumbnailUrl)
                .size(Size(requestWidthPx, requestHeightPx))
                .crossfade(true)
                .build()
        }
    val isActivelyPlaying = isActive && isPlaying
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val morphProgress by animateFloatAsState(
        targetValue = if (isActivelyPlaying) 1f else 0f,
        animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
        label = "quickPickPlayMorph",
    )
    val playButtonColor by animateColorAsState(
        targetValue = if (isActive) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primary,
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "quickPickPlayColor",
    )
    val playIconColor =
        if (isActive) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimary
    val playLabel = stringResource(if (isActivelyPlaying) R.string.widget_pause else R.string.play)

    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .maskClip(MaterialTheme.shapes.extraLarge)
                .focusable()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
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
                            0.52f to Color.Black.copy(alpha = 0.12f),
                            1f to Color.Black.copy(alpha = 0.88f),
                        ),
                    ),
        )

        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier =
                Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 16.dp, bottom = 16.dp)
                    .graphicsLayer {
                        val drawInfo = carouselItemDrawInfo
                        val visibleFraction =
                            if (drawInfo.maxSize > 0f) drawInfo.size / drawInfo.maxSize else 1f
                        alpha = ((visibleFraction - 0.6f) / 0.4f).coerceIn(0f, 1f)
                    },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(2.dp),
                modifier =
                    Modifier
                        .weight(1f)
                        .padding(bottom = 4.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmallEmphasized,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White.copy(alpha = 0.78f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(64.dp)
                        .clip(EditorialMorphShape(morph, morphProgress))
                        .background(playButtonColor)
                        .clickable(
                            onClickLabel = playLabel,
                            role = Role.Button,
                            onClick = onClick,
                        ),
            ) {
                Icon(
                    painter = painterResource(if (isActivelyPlaying) R.drawable.pause else R.drawable.play),
                    contentDescription = playLabel,
                    tint = playIconColor,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
    }
}

@Composable
private fun <T : Any> SongStackShelf(
    items: List<T>,
    key: (T) -> Any,
    state: LazyListState,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit,
) {
    val stacks = remember(items) { items.chunked(SongStackSize) }
    val layoutDirection = LocalLayoutDirection.current
    val systemBarPadding =
        WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues()

    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val startPadding = systemBarPadding.calculateStartPadding(layoutDirection) + 16.dp
        val endPadding = systemBarPadding.calculateEndPadding(layoutDirection) + 16.dp
        val stackWidth =
            if (maxWidth * 0.475f >= 320.dp) {
                maxWidth * 0.475f
            } else {
                (maxWidth - startPadding - 40.dp).coerceAtLeast(264.dp)
            }

        LazyRow(
            state = state,
            flingBehavior = rememberSnapFlingBehavior(lazyListState = state, snapPosition = SnapPosition.Start),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(start = startPadding, end = endPadding),
        ) {
            items(
                items = stacks,
                key = { stack -> key(stack.first()) },
                contentType = { "song_stack" },
            ) { stack ->
                Surface(
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.width(stackWidth),
                ) {
                    Column(modifier = Modifier.padding(vertical = 8.dp)) {
                        stack.forEach { item -> itemContent(item) }
                    }
                }
            }
        }
    }
}

private const val SongStackSize = 4

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
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
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
                                                                        navController.navigateToAlbum(localItem.id)
                                                                    }

                                                                    is Artist -> {
                                                                        navController.navigateToArtist(localItem.id)
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SpeedDialRandomTile(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = modifier.aspectRatio(1f),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(6.dp),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(44.dp)
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = MaterialShapes.Cookie9Sided.toShape(),
                        ),
            ) {
                Icon(
                    painter = painterResource(R.drawable.shuffle),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(24.dp),
                )
            }
            Text(
                text = stringResource(R.string.shuffle),
                style = MaterialTheme.typography.labelLargeEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
        val cardWidth = if (maxWidth >= 600.dp) 184.dp else 160.dp

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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3ExpressiveApi::class)
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

                    is Album -> navController.navigateToAlbum(item.id)
                    is Artist -> navController.navigateToArtist(item.id)
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
    val thumbnailShape = if (item is Artist) MaterialShapes.Cookie9Sided.toShape() else MaterialTheme.shapes.large
    val placeholderIcon =
        when (item) {
            is Song -> R.drawable.music_note
            is Album -> R.drawable.album
            is Artist -> R.drawable.artist
            is Playlist -> R.drawable.queue_music
        }
    val containerColor by animateColorAsState(
        targetValue =
            if (isActive) {
                MaterialTheme.colorScheme.secondaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "keepListeningContainer",
    )
    val cardShape = MaterialTheme.shapes.extraLarge

    Surface(
        color = containerColor,
        contentColor =
            if (isActive) {
                MaterialTheme.colorScheme.onSecondaryContainer
            } else {
                MaterialTheme.colorScheme.onSurface
            },
        shape = cardShape,
        modifier =
            modifier
                .clip(cardShape)
                .focusable()
                .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Box {
                ItemThumbnail(
                    thumbnailUrl = item.homeThumbnailUrl(),
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = thumbnailShape,
                    placeholderIconRes = placeholderIcon,
                    contentScale = ContentScale.Crop,
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(1f),
                )
                if (item is Song) {
                    FilledIconButton(
                        onClick = onClick,
                        shapes = IconButtonDefaults.shapes(),
                        modifier =
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(6.dp),
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
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleSmallEmphasized,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color =
                    if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.78f)
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
    state: LazyListState,
    navController: NavController,
    playerConnection: PlayerConnection,
    menuState: MenuState,
    haptic: HapticFeedback,
    modifier: Modifier = Modifier,
) {
    val distinctForgottenFavorites = remember(forgottenFavorites) { forgottenFavorites.distinctBy { it.id } }

    SongStackShelf(
        items = distinctForgottenFavorites,
        key = { it.id },
        state = state,
        modifier = modifier,
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
                        contentDescription = stringResource(R.string.more_options),
                    )
                }
            },
            modifier =
                Modifier
                    .fillMaxWidth()
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
    contentPadding: PaddingValues =
        WindowInsets.systemBars
            .only(WindowInsetsSides.Horizontal)
            .asPaddingValues(),
) {
    val distinctPlaylists = remember(accountPlaylists) { accountPlaylists.distinctBy { it.id } }

    LazyRow(
        contentPadding = contentPadding,
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

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                is Song -> source.album?.id?.let { albumId -> ({ navController.navigateToAlbum(albumId) }) }
                is Album -> ({ navController.navigateToAlbum(source.id) })
                is Artist -> ({ navController.navigateToArtist(source.id) })
                is Playlist -> ({ navController.navigate("local_playlist/${source.id}") })
            }
        }

    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Column(modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)) {
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
                                shape = if (source is Artist) MaterialShapes.Cookie9Sided.toShape() else MaterialTheme.shapes.large,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.size(64.dp),
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
        remember(card.id, card.radioEndpoint, secondaryEndpoint, playerConnection) {
            secondaryEndpoint?.let { endpoint ->
                {
                    val queue =
                        if (card.radioEndpoint != null) {
                            YouTubeQueue.playlist(endpoint)
                        } else {
                            YouTubeQueue.shuffledPlaylist(card.id, endpoint)
                        }
                    playerConnection.playQueue(queue)
                }
            }
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
        color = MaterialTheme.colorScheme.surfaceContainer,
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
                        .heightIn(min = 112.dp)
                        .clip(MaterialTheme.shapes.large)
                        .combinedClickable(
                            onClick = openPlaylist,
                            onLongClick = showPlaylistMenu,
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
                                .size(112.dp)
                                .clip(MaterialTheme.shapes.large),
                    )
                } ?: run {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier =
                            Modifier
                                .size(112.dp)
                                .clip(MaterialTheme.shapes.large)
                                .background(MaterialTheme.colorScheme.secondaryContainer),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.queue_music),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = card.title,
                        style = MaterialTheme.typography.titleLargeEmphasized,
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
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            ) {
                val playButtonHeight = ButtonDefaults.MediumContainerHeight
                Button(
                    onClick = playPlaylist,
                    shapes = ButtonDefaults.shapesFor(playButtonHeight),
                    contentPadding = ButtonDefaults.contentPaddingFor(playButtonHeight, hasStartIcon = true),
                    modifier = Modifier.heightIn(min = playButtonHeight),
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.iconSizeFor(playButtonHeight)),
                    )
                    Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(playButtonHeight)))
                    Text(
                        text = stringResource(R.string.play),
                        style = ButtonDefaults.textStyleFor(playButtonHeight),
                        maxLines = 1,
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (playSecondary != null) {
                    FilledTonalIconButton(
                        onClick = playSecondary,
                        shapes = IconButtonDefaults.shapes(),
                        modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
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
                    shapes = IconButtonDefaults.shapes(),
                    modifier = Modifier.size(IconButtonDefaults.mediumContainerSize()),
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
                                navController.navigateToAlbum(item.id)
                            }

                            is ArtistItem -> {
                                navController.navigateToArtist(item.id)
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
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
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
                            MaterialShapes.Cookie9Sided.toShape()
                        } else {
                            MaterialTheme.shapes.large
                        }
                    AsyncImage(
                        model = thumbnailUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .size(56.dp)
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
                    FilledTonalButton(
                        onClick = playAll,
                        shapes = ButtonDefaults.shapes(),
                        contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight, hasStartIcon = true),
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.play),
                            contentDescription = null,
                            modifier = Modifier.size(ButtonDefaults.iconSizeFor(ButtonDefaults.MinHeight)),
                        )
                        Spacer(modifier = Modifier.width(ButtonDefaults.iconSpacingFor(ButtonDefaults.MinHeight)))
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
