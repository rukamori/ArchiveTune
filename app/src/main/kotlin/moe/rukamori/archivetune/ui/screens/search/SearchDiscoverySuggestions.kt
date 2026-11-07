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

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedListItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.component.LocalMenuState
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.ui.utils.navigateToAlbum
import moe.rukamori.archivetune.ui.utils.navigateToArtist
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

@Composable
internal fun SuggestedSongsShelf(
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
    val pages = remember(songs) { songs.chunked(SongsPerPage) }
    val title = stringResource(R.string.search_suggested_songs)
    val listState = rememberLazyListState()
    val snapLayoutInfoProvider =
        remember(listState) {
            SnapLayoutInfoProvider(lazyListState = listState, snapPosition = SnapPosition.Start)
        }

    Column(modifier = modifier.fillMaxWidth()) {
        DiscoverySectionHeader(
            title = title,
            iconRes = R.drawable.music_note,
            badgeShape = MaterialShapes.Cookie9Sided,
            supporting = pluralStringResource(R.plurals.n_song, songs.size, songs.size),
            action = {
                FilledTonalButton(
                    onClick = {
                        playerConnection.playQueue(
                            ListQueue(
                                title = title,
                                items = songs.map { it.toMediaItem() },
                            ),
                        )
                    },
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = stringResource(R.string.play_all),
                        maxLines = 1,
                    )
                }
            },
        )

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val pageWidth =
                when {
                    pages.size <= 1 -> maxWidth
                    maxWidth >= WideShelfBreakpoint -> (maxWidth - ShelfPageSpacing) / 2
                    else -> maxWidth * NarrowPageWidthFraction
                }

            LazyRow(
                state = listState,
                flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
                horizontalArrangement = Arrangement.spacedBy(ShelfPageSpacing),
                modifier = Modifier.fillMaxWidth(),
            ) {
                items(
                    items = pages,
                    key = { page -> "song_page_${page.first().id}" },
                    contentType = { "song_page" },
                ) { page ->
                    Column(
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.width(pageWidth),
                    ) {
                        page.forEachIndexed { index, song ->
                            val isActive = song.id == mediaMetadata?.id
                            DiscoverySongRow(
                                song = song,
                                index = index,
                                count = page.size,
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
                                onMenu = {
                                    menuState.show {
                                        YouTubeSongMenu(
                                            song = song,
                                            navController = navController,
                                            onDismiss = menuState::dismiss,
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
            }
        }
    }
}

@Composable
private fun DiscoverySongRow(
    song: SongItem,
    index: Int,
    count: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    onLongClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val menuLabel = stringResource(R.string.more_options)

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = if (isActive) colorScheme.secondaryContainer else colorScheme.surfaceContainerLow,
            ),
        onLongClick = onLongClick,
        onLongClickLabel = menuLabel,
        leadingContent = {
            ItemThumbnail(
                thumbnailUrl = song.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.size(DiscoverySongThumbnailSize),
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (song.explicit) {
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        modifier =
                            Modifier
                                .padding(end = 4.dp)
                                .size(16.dp),
                    )
                }
                Text(
                    text =
                        joinByBullet(
                            song.artists.joinToString { it.name },
                            makeTimeString(song.duration?.times(1000L)),
                        ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent = {
            IconButton(onClick = onMenu) {
                Icon(
                    painter = painterResource(R.drawable.more_vert),
                    contentDescription = menuLabel,
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = song.title,
            fontWeight = if (isActive) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SuggestedArtistsShelf(
    artists: List<ArtistItem>,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    if (artists.isEmpty()) return

    val menuState = LocalMenuState.current
    val haptic = LocalHapticFeedback.current

    Column(modifier = modifier.fillMaxWidth()) {
        DiscoverySectionHeader(
            title = stringResource(R.string.search_suggested_artists),
            iconRes = R.drawable.artist,
            badgeShape = MaterialShapes.Clover4Leaf,
            supporting = pluralStringResource(R.plurals.n_artist, artists.size, artists.size),
        )

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                items = artists,
                key = { artist -> "artist_${artist.id}" },
                contentType = { "artist_tile" },
            ) { artist ->
                SearchArtistTile(
                    artist = artist,
                    modifier = Modifier.width(ArtistTileWidth),
                    onClick = { navController.navigateToArtist(artist.id) },
                    onLongClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        menuState.show {
                            YouTubeArtistMenu(
                                artist = artist,
                                onDismiss = menuState::dismiss,
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
internal fun SearchArtistTile(
    artist: ArtistItem,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie12Sided) }
    val progress by animateFloatAsState(
        targetValue = if (pressed) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "artistTileMorph",
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .clip(RoundedCornerShape(24.dp))
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.more_options),
                    onLongClick = onLongClick,
                    onClick = onClick,
                ).padding(horizontal = 6.dp, vertical = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer {
                        shape = EditorialMorphShape(morph, progress.coerceIn(0f, 1f))
                        clip = true
                    }.background(MaterialTheme.colorScheme.surfaceContainerHigh),
        ) {
            ItemThumbnail(
                thumbnailUrl = artist.thumbnail,
                isActive = false,
                isPlaying = false,
                shape = RectangleShape,
                contentScale = ContentScale.Crop,
                placeholderIconRes = R.drawable.artist,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = artist.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun TrendingAlbumsShelf(
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
    val listState = rememberLazyListState()
    val snapLayoutInfoProvider =
        remember(listState) {
            SnapLayoutInfoProvider(lazyListState = listState, snapPosition = SnapPosition.Start)
        }

    Column(modifier = modifier.fillMaxWidth()) {
        DiscoverySectionHeader(
            title = stringResource(R.string.search_trending_albums),
            iconRes = R.drawable.album,
            badgeShape = MaterialShapes.Slanted,
            supporting = pluralStringResource(R.plurals.n_album, albums.size, albums.size),
        )

        LazyRow(
            state = listState,
            flingBehavior = rememberSnapFlingBehavior(snapLayoutInfoProvider),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(
                items = albums,
                key = { album -> "album_${album.id}" },
                contentType = { "album_card" },
            ) { album ->
                SearchMediaCard(
                    thumbnailUrl = album.thumbnail,
                    title = album.title,
                    subtitle = joinByBullet(album.artists?.joinToString { it.name }, album.year?.toString()),
                    label = searchResultTypeLabel(album),
                    explicit = album.explicit,
                    isActive = mediaMetadata?.album?.id == album.id,
                    isPlaying = isPlaying,
                    placeholderIconRes = R.drawable.album,
                    modifier = Modifier.width(AlbumCardWidth),
                    onClick = { navController.navigateToAlbum(album.id) },
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
                )
            }
        }
    }
}

@Composable
internal fun SearchMediaCard(
    thumbnailUrl: String?,
    title: String,
    subtitle: String,
    label: String?,
    explicit: Boolean,
    isActive: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    artworkRatio: Float = 1f,
    @DrawableRes placeholderIconRes: Int? = null,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val animationsDisabled = LocalAnimationsDisabled.current
    val scale by animateFloatAsState(
        targetValue = if (pressed) MediaCardPressedScale else 1f,
        animationSpec = if (animationsDisabled) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "mediaCardScale",
    )
    val colorScheme = MaterialTheme.colorScheme

    Column(
        modifier =
            modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(MediaArtworkShape)
                .combinedClickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    role = Role.Button,
                    onLongClickLabel = if (onLongClick != null) stringResource(R.string.more_options) else null,
                    onLongClick = onLongClick,
                    onClick = onClick,
                ).padding(bottom = 8.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(artworkRatio),
        ) {
            ItemThumbnail(
                thumbnailUrl = thumbnailUrl,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = MediaArtworkShape,
                thumbnailRatio = artworkRatio,
                contentScale = if (artworkRatio != 1f) ContentScale.Crop else null,
                placeholderIconRes = placeholderIconRes,
                modifier = Modifier.fillMaxSize(),
            )
            if (!label.isNullOrEmpty()) {
                Surface(
                    shape = CircleShape,
                    color = colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                    contentColor = colorScheme.onSurface,
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .padding(10.dp),
                ) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
        }
        Spacer(Modifier.size(10.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        if (subtitle.isNotEmpty() || explicit) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            ) {
                if (explicit) {
                    Icon(
                        painter = painterResource(R.drawable.explicit),
                        contentDescription = null,
                        tint = colorScheme.onSurfaceVariant,
                        modifier =
                            Modifier
                                .padding(end = 4.dp)
                                .size(14.dp),
                    )
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

private const val SongsPerPage = 4
private const val NarrowPageWidthFraction = 0.88f
private const val MediaCardPressedScale = 0.95f
private val WideShelfBreakpoint = 600.dp
private val ShelfPageSpacing = 12.dp
private val DiscoverySongThumbnailSize = 52.dp
internal val ArtistTileWidth = 124.dp
internal val AlbumCardWidth = 160.dp
private val MediaArtworkShape = RoundedCornerShape(24.dp)
