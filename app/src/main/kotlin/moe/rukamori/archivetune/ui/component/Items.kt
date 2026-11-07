/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.component

import android.widget.Toast
import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandIn
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkOut
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.fastForEachIndexed
import androidx.compose.ui.zIndex
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.Download.STATE_COMPLETED
import androidx.media3.exoplayer.offline.Download.STATE_DOWNLOADING
import androidx.media3.exoplayer.offline.Download.STATE_QUEUED
import coil3.compose.AsyncImage
import coil3.imageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.LocalDatabase
import moe.rukamori.archivetune.LocalDownloadUtil
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.CropThumbnailToSquareKey
import moe.rukamori.archivetune.constants.GridThumbnailCornerRadius
import moe.rukamori.archivetune.constants.GridThumbnailHeight
import moe.rukamori.archivetune.constants.ListItemHeight
import moe.rukamori.archivetune.constants.ListThumbnailSize
import moe.rukamori.archivetune.constants.SwipeToSongKey
import moe.rukamori.archivetune.constants.ThumbnailCornerRadius
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.ui.theme.PlayerColorExtractor
import moe.rukamori.archivetune.ui.theme.extractThemeColor
import moe.rukamori.archivetune.ui.utils.YtimgResizePolicy
import moe.rukamori.archivetune.ui.utils.getNextFallbackUrl
import moe.rukamori.archivetune.ui.utils.preferredThumbnailRatio
import moe.rukamori.archivetune.ui.utils.resize
import moe.rukamori.archivetune.ui.utils.thumbnailSourceRatio
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import moe.rukamori.archivetune.utils.rememberPreference
import moe.rukamori.archivetune.utils.reportException
import kotlin.math.roundToInt

const val ActiveBoxAlpha = 0.6f

private val GridArtworkShape = RoundedCornerShape(GridThumbnailCornerRadius)
private val ListArtworkShape = RoundedCornerShape(ThumbnailCornerRadius)
private val ActiveListItemShape = RoundedCornerShape(20.dp)
private val GridArtworkElevation = 10.dp
private val ListArtworkElevation = 4.dp
private val GridArtistHaloWidth = 3.dp
private val ListArtistHaloWidth = 2.dp
private const val ArtworkAmbientShadowAlpha = 0.28f
private const val ArtworkSpotShadowAlpha = 0.38f
private const val ArtworkOutlineAlpha = 0.45f

private fun Modifier.artworkLift(
    shape: Shape,
    elevation: Dp,
    tint: Color,
): Modifier =
    shadow(
        elevation = elevation,
        shape = shape,
        clip = false,
        ambientColor = tint.copy(alpha = ArtworkAmbientShadowAlpha),
        spotColor = tint.copy(alpha = ArtworkSpotShadowAlpha),
    )

@Composable
private fun ArtistHalo(
    ringWidth: Dp,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    val ringBrush =
        remember(colorScheme.primary, colorScheme.tertiary) {
            Brush.linearGradient(listOf(colorScheme.primary, colorScheme.tertiary))
        }
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .border(width = ringWidth, brush = ringBrush, shape = CircleShape)
                .padding(ringWidth * 2),
        content = content,
    )
}

@Composable
private fun ArtworkBadgePill(
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit,
) {
    val containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f)
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Layout(
            content = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    content = badges,
                )
            },
            modifier =
                modifier.drawBehind {
                    if (size.minDimension > 0f) {
                        drawRoundRect(
                            color = containerColor,
                            cornerRadius = CornerRadius(size.height / 2f),
                        )
                    }
                },
        ) { measurables, constraints ->
            val placeable = measurables.first().measure(constraints.copy(minWidth = 0, minHeight = 0))
            if (placeable.width == 0 || placeable.height == 0) {
                layout(0, 0) {}
            } else {
                val horizontal = 6.dp.roundToPx()
                val vertical = 3.dp.roundToPx()
                layout(placeable.width + horizontal * 2, placeable.height + vertical * 2) {
                    placeable.place(horizontal, vertical)
                }
            }
        }
    }
}

@Composable
private fun GridTitle(
    text: String,
    isActive: Boolean = false,
    centered: Boolean = false,
    marquee: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.Bold,
        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier =
            Modifier
                .then(if (marquee) Modifier.basicMarquee() else Modifier)
                .fillMaxWidth(),
    )
}

@Composable
private fun GridSubtitle(
    text: String,
    centered: Boolean = false,
    maxLines: Int = 2,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
        textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: (@Composable RowScope.() -> Unit)? = null,
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    showActiveContainer: Boolean = true,
    thumbnailShape: Shape? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    val effectsSpec = MaterialTheme.motionScheme.defaultEffectsSpec<Color>()
    val showContainer = isActive && showActiveContainer
    val containerColor by animateColorAsState(
        targetValue = if (showContainer) colorScheme.secondaryContainer else colorScheme.secondaryContainer.copy(alpha = 0f),
        animationSpec = effectsSpec,
        label = "listItemContainer",
    )
    val titleColor by animateColorAsState(
        targetValue = if (isActive) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        animationSpec = effectsSpec,
        label = "listItemTitle",
    )
    val subtitleContentColor =
        if (isActive) {
            colorScheme.onSecondaryContainer.copy(alpha = 0.72f)
        } else {
            colorScheme.onSurfaceVariant
        }
    val trailingContentColor =
        if (isActive) {
            colorScheme.onSecondaryContainer
        } else {
            colorScheme.onSurfaceVariant
        }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .focusable()
                .height(ListItemHeight)
                .padding(horizontal = 8.dp)
                .background(containerColor, ActiveListItemShape),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .padding(8.dp)
                    .then(
                        if (thumbnailShape != null) {
                            Modifier.artworkLift(thumbnailShape, ListArtworkElevation, colorScheme.primary)
                        } else {
                            Modifier
                        },
                    ),
        ) { thumbnailContent() }
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = titleColor,
            )
            if (subtitle != null) {
                CompositionLocalProvider(LocalContentColor provides subtitleContentColor) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) { subtitle() }
                }
            }
        }
        CompositionLocalProvider(LocalContentColor provides trailingContentColor) {
            trailingContent()
        }
    }
}

@Composable
fun ListItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String?,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable () -> Unit,
    trailingContent: @Composable RowScope.() -> Unit = {},
    isActive: Boolean = false,
    showActiveContainer: Boolean = true,
    thumbnailShape: Shape? = null,
) = ListItem(
    title = title,
    modifier = modifier,
    isActive = isActive,
    showActiveContainer = showActiveContainer,
    thumbnailShape = thumbnailShape,
    subtitle = {
        badges()
        if (!subtitle.isNullOrEmpty()) {
            Text(
                text = subtitle,
                color = LocalContentColor.current,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    },
    thumbnailContent = thumbnailContent,
    trailingContent = trailingContent,
)

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    subtitle: @Composable () -> Unit,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    thumbnailShape: Shape = GridArtworkShape,
    isArtist: Boolean = false,
    isActive: Boolean = false,
) {
    val colorScheme = MaterialTheme.colorScheme
    val outlineColor by animateColorAsState(
        targetValue =
            if (isActive) {
                colorScheme.primary
            } else {
                colorScheme.outlineVariant.copy(alpha = ArtworkOutlineAlpha)
            },
        animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
        label = "gridArtworkOutline",
    )
    val artworkShape = if (isArtist) CircleShape else thumbnailShape

    Column(
        horizontalAlignment = if (isArtist) Alignment.CenterHorizontally else Alignment.Start,
        modifier =
            if (fillMaxWidth) {
                modifier
                    .focusable()
                    .padding(12.dp)
                    .fillMaxWidth()
            } else {
                modifier
                    .focusable()
                    .padding(12.dp)
                    .width(GridThumbnailHeight * thumbnailRatio)
            },
    ) {
        Box(
            modifier =
                if (fillMaxWidth) {
                    Modifier.fillMaxWidth()
                } else {
                    Modifier.height(GridThumbnailHeight)
                }.aspectRatio(thumbnailRatio),
        ) {
            val artwork: @Composable () -> Unit = {
                BoxWithConstraints(
                    contentAlignment = Alignment.Center,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .artworkLift(artworkShape, GridArtworkElevation, colorScheme.primary)
                            .then(
                                if (isArtist) {
                                    Modifier
                                } else {
                                    Modifier.border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = outlineColor,
                                        shape = artworkShape,
                                    )
                                },
                            ),
                ) {
                    thumbnailContent()
                }
            }

            if (isArtist) {
                ArtistHalo(
                    ringWidth = GridArtistHaloWidth,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    artwork()
                }
            } else {
                artwork()
            }

            ArtworkBadgePill(
                badges = badges,
                modifier =
                    Modifier
                        .align(if (isArtist) Alignment.BottomCenter else Alignment.TopStart)
                        .padding(8.dp),
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        title()

        Spacer(modifier = Modifier.height(2.dp))

        subtitle()
    }
}

@Composable
fun GridItem(
    modifier: Modifier = Modifier,
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailContent: @Composable BoxWithConstraintsScope.() -> Unit,
    thumbnailRatio: Float = 1f,
    fillMaxWidth: Boolean = false,
    thumbnailShape: Shape = GridArtworkShape,
    isArtist: Boolean = false,
    isActive: Boolean = false,
) = GridItem(
    modifier = modifier,
    title = {
        GridTitle(
            text = title,
            isActive = isActive,
            centered = isArtist,
            marquee = false,
        )
    },
    subtitle = {
        if (subtitle.isNotEmpty()) {
            GridSubtitle(
                text = subtitle,
                centered = isArtist,
                maxLines = 1,
            )
        }
    },
    badges = badges,
    thumbnailContent = thumbnailContent,
    thumbnailRatio = thumbnailRatio,
    fillMaxWidth = fillMaxWidth,
    thumbnailShape = thumbnailShape,
    isArtist = isArtist,
    isActive = isActive,
)

@Composable
private fun SongSourceIcon(isLocal: Boolean) {
    val color = if (isLocal) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
    Icon(
        painter = painterResource(if (isLocal) R.drawable.playlist_local else R.drawable.playlist_online),
        contentDescription =
            stringResource(
                if (isLocal) R.string.playlist_source_local else R.string.playlist_source_online,
            ),
        modifier = Modifier.size(12.dp),
        tint = color,
    )
}

@Composable
fun SongListItem(
    song: Song,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    viewCountText: String? = null,
    showSourceIcon: Boolean = false,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    showSongIconPlaceholder: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {
        if (showSourceIcon) {
            SongSourceIcon(isLocal = song.song.isLocal)
        }
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (song.song.explicit) {
            Icon.Explicit()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current
                .getDownload(song.id)
                .collectAsState(initial = null)
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    swipeContentBackgroundColor: Color? = null,
    showActiveContainer: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)
    val resolvedSwipeContentBackgroundColor = swipeContentBackgroundColor ?: MaterialTheme.colorScheme.surface

    val content: @Composable () -> Unit = {
        ListItem(
            title = song.song.title,
            subtitle =
                joinByBullet(
                    song.artists.joinToString { it.name },
                    makeTimeString(song.song.duration * 1000L),
                    viewCountText,
                ),
            badges = badges,
            thumbnailContent = {
                ItemThumbnail(
                    thumbnailUrl = song.song.thumbnailUrl,
                    albumIndex = albumIndex,
                    isSelected = isSelected,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = ListArtworkShape,
                    placeholderIconRes = if (showSongIconPlaceholder) R.drawable.music_note else null,
                    maxSizePx = 200,
                    modifier = Modifier.size(ListThumbnailSize),
                )
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive,
            showActiveContainer = showActiveContainer,
            thumbnailShape = if (albumIndex != null) null else ListArtworkShape,
        )
    }

    if (isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem = song.toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
            contentBackgroundColor = resolvedSwipeContentBackgroundColor,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun SongGridItem(
    song: Song,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    showInLibraryIcon: Boolean = false,
    showDownloadIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        if (showLikedIcon && song.song.liked) {
            Icon.Favorite()
        }
        if (showInLibraryIcon && song.song.inLibrary != null) {
            Icon.Library()
        }
        if (showDownloadIcon) {
            val download by LocalDownloadUtil.current.getDownload(song.id).collectAsState(initial = null)
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        GridTitle(text = song.song.title, isActive = isActive)
    },
    subtitle = {
        GridSubtitle(
            text =
                joinByBullet(
                    song.artists.joinToString { it.name },
                    makeTimeString(song.song.duration * 1000L),
                ),
        )
    },
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = song.song.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = GridArtworkShape,
            modifier = Modifier.size(GridThumbnailHeight),
        )
        OverlayPlayButton(
            visible = !isActive,
        )
    },
    isActive = isActive,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun ArtistListItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon(
                painter = painterResource(R.drawable.favorite),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier =
                    Modifier
                        .size(18.dp)
                        .padding(end = 2.dp),
            )
        }
    },
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        ArtistHalo(
            ringWidth = ListArtistHaloWidth,
            modifier = Modifier.size(ListThumbnailSize),
        ) {
            ItemThumbnail(
                thumbnailUrl = artist.artist.thumbnailUrl,
                isActive = false,
                isPlaying = false,
                shape = CircleShape,
                contentScale = ContentScale.Crop,
                maxSizePx = 200,
                modifier = Modifier.fillMaxSize(),
            )
        }
    },
    trailingContent = trailingContent,
    modifier = modifier,
)

@Composable
fun ArtistGridItem(
    artist: Artist,
    modifier: Modifier = Modifier,
    badges: @Composable RowScope.() -> Unit = {
        if (artist.artist.bookmarkedAt != null) {
            Icon.Favorite()
        }
    },
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = artist.artist.name,
    subtitle = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = artist.artist.thumbnailUrl,
            isActive = false,
            isPlaying = false,
            shape = CircleShape,
            contentScale = ContentScale.Crop,
            maxSizePx = 544,
            modifier = Modifier.fillMaxSize(),
        )
    },
    isArtist = true,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun AlbumListItem(
    album: Album,
    modifier: Modifier = Modifier,
    showLikedIcon: Boolean = true,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember {
            mutableStateOf(emptyList<Song>())
        }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect {
                songs = it
            }
        }

        var downloadState by remember {
            mutableStateOf(Download.STATE_STOPPED)
        }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    when {
                        songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED

                        songs.all {
                            downloads[it.id]?.state in
                                listOf(
                                    STATE_QUEUED,
                                    STATE_DOWNLOADING,
                                    STATE_COMPLETED,
                                )
                        } -> STATE_DOWNLOADING

                        else -> Download.STATE_STOPPED
                    }
            }
        }

        if (showLikedIcon && album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = album.album.title,
    subtitle =
        joinByBullet(
            album.artists.joinToString { it.name },
            pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
            album.album.year?.toString(),
        ),
    badges = badges,
    thumbnailContent = {
        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = ListArtworkShape,
            maxSizePx = 200,
            modifier = Modifier.size(ListThumbnailSize),
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
    isActive = isActive,
    thumbnailShape = ListArtworkShape,
)

@Composable
fun AlbumGridItem(
    album: Album,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val downloadUtil = LocalDownloadUtil.current
        var songs by remember { mutableStateOf(emptyList<Song>()) }

        LaunchedEffect(Unit) {
            database.albumSongs(album.id).collect { songs = it }
        }

        var downloadState by remember { mutableStateOf(Download.STATE_STOPPED) }

        LaunchedEffect(songs) {
            if (songs.isEmpty()) return@LaunchedEffect
            downloadUtil.downloads.collect { downloads ->
                downloadState =
                    when {
                        songs.all { downloads[it.id]?.state == STATE_COMPLETED } -> STATE_COMPLETED

                        songs.all {
                            downloads[it.id]?.state in
                                listOf(
                                    STATE_QUEUED,
                                    STATE_DOWNLOADING,
                                    STATE_COMPLETED,
                                )
                        } -> STATE_DOWNLOADING

                        else -> Download.STATE_STOPPED
                    }
            }
        }

        if (album.album.bookmarkedAt != null) {
            Icon.Favorite()
        }
        if (album.album.explicit) {
            Icon.Explicit()
        }
        Icon.Download(downloadState)
    },
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        GridTitle(text = album.album.title, isActive = isActive)
    },
    subtitle = {
        GridSubtitle(
            text =
                joinByBullet(
                    album.artists.joinToString { it.name },
                    album.album.year?.toString(),
                ),
        )
    },
    badges = badges,
    isActive = isActive,
    thumbnailContent = {
        val database = LocalDatabase.current
        val playerConnection = LocalPlayerConnection.current ?: return@GridItem

        ItemThumbnail(
            thumbnailUrl = album.album.thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = GridArtworkShape,
            maxSizePx = 544,
        )

        AlbumPlayButton(
            visible = !isActive,
            onClick = {
                coroutineScope.launch {
                    database.albumWithSongs(album.id).firstOrNull()?.let { albumWithSongs ->
                        playerConnection.playQueue(LocalAlbumRadio(albumWithSongs))
                    }
                }
            },
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun PlaylistListItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    trailingContent: @Composable RowScope.() -> Unit = {},
) = ListItem(
    title = playlist.playlist.name,
    subtitle =
        if (autoPlaylist) {
            ""
        } else {
            if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.playlist.remoteSongCount,
                    playlist.playlist.remoteSongCount,
                )
            } else {
                pluralStringResource(
                    R.plurals.n_song,
                    playlist.songCount,
                    playlist.songCount,
                )
            }
        },
    badges = badges,
    thumbnailContent = {
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = ListThumbnailSize,
            placeHolder = {
                val painter =
                    when (playlist.playlist.name) {
                        stringResource(R.string.liked) -> R.drawable.favorite_border
                        stringResource(R.string.offline) -> R.drawable.offline
                        stringResource(R.string.cached_playlist) -> R.drawable.cached
                        else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
                    }
                Icon(
                    painter = painterResource(painter),
                    contentDescription = null,
                    tint = LocalContentColor.current.copy(alpha = 0.8f),
                    modifier = Modifier.size(ListThumbnailSize / 2),
                )
            },
            shape = ListArtworkShape,
        )
    },
    trailingContent = trailingContent,
    modifier = modifier,
    thumbnailShape = ListArtworkShape,
)

@Composable
fun PlaylistGridItem(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    autoPlaylist: Boolean = false,
    badges: @Composable RowScope.() -> Unit = {},
    fillMaxWidth: Boolean = false,
) = GridItem(
    title = {
        GridTitle(text = playlist.playlist.name)
    },
    subtitle = {
        val subtitle = playlistCountText(playlist = playlist, autoPlaylist = autoPlaylist)
        if (subtitle.isNotEmpty()) {
            GridSubtitle(text = subtitle)
        }
    },
    badges = badges,
    thumbnailContent = {
        val width = maxWidth
        PlaylistThumbnail(
            thumbnails = playlist.thumbnails,
            size = width,
            placeHolder = {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    Icon(
                        painter = painterResource(playlistPlaceholderIcon(playlist, autoPlaylist)),
                        contentDescription = null,
                        tint = LocalContentColor.current.copy(alpha = 0.8f),
                        modifier = Modifier.size(width / 2),
                    )
                }
            },
            shape = GridArtworkShape,
        )
    },
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
private fun playlistCountText(
    playlist: Playlist,
    autoPlaylist: Boolean,
): String =
    if (autoPlaylist) {
        ""
    } else if (playlist.songCount == 0 && playlist.playlist.remoteSongCount != null) {
        pluralStringResource(
            R.plurals.n_song,
            playlist.playlist.remoteSongCount,
            playlist.playlist.remoteSongCount,
        )
    } else {
        pluralStringResource(
            R.plurals.n_song,
            playlist.songCount,
            playlist.songCount,
        )
    }

@Composable
private fun playlistPlaceholderIcon(
    playlist: Playlist,
    autoPlaylist: Boolean,
): Int =
    when (playlist.playlist.name) {
        stringResource(R.string.liked) -> R.drawable.favorite_border
        stringResource(R.string.offline) -> R.drawable.offline
        stringResource(R.string.cached_playlist) -> R.drawable.cached
        else -> if (autoPlaylist) R.drawable.trending_up else R.drawable.queue_music
    }

@Composable
fun LibraryPinnedCollectionTile(
    title: String,
    @DrawableRes iconRes: Int,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = MaterialTheme.colorScheme.primary,
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors =
                                listOf(
                                    accentColor.copy(alpha = 0.28f),
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                    MaterialTheme.colorScheme.surfaceContainerLow,
                                ),
                        ),
                    ),
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(14.dp),
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.76f),
                    shape = CircleShape,
                ) {
                    Icon(
                        painter = painterResource(iconRes),
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    subtitle?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

private val LibraryCardThumbnailSize = 72.dp
private val LibraryCardGlowElevation = 34.dp
private const val LibraryCardGlowAmbientAlpha = 0.82f
private const val LibraryCardGlowSpotAlpha = 0.96f

@Composable
fun LibraryPlaylistFeatureCard(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    autoPlaylist: Boolean = false,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitleText = playlistCountText(playlist = playlist, autoPlaylist = autoPlaylist)
    val thumbnailSize = LibraryCardThumbnailSize
    val thumbnailShape = RoundedCornerShape(18.dp)
    val context = LocalContext.current
    val primaryThumbnailUrl = playlist.thumbnails.getOrNull(0)
    var extractedGlowColor by remember(primaryThumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(400),
        label = "playlistItemGlow",
    )
    LaunchedEffect(primaryThumbnailUrl) {
        if (primaryThumbnailUrl == null) return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(primaryThumbnailUrl)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = shape,
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(thumbnailSize)
                        .shadow(
                            elevation = LibraryCardGlowElevation,
                            shape = thumbnailShape,
                            clip = false,
                            ambientColor = glowColor.copy(alpha = LibraryCardGlowAmbientAlpha),
                            spotColor = glowColor.copy(alpha = LibraryCardGlowSpotAlpha),
                        ),
            ) {
                PlaylistThumbnail(
                    thumbnails = playlist.thumbnails,
                    size = thumbnailSize,
                    placeHolder = {
                        Icon(
                            painter = painterResource(playlistPlaceholderIcon(playlist, autoPlaylist)),
                            contentDescription = null,
                            tint = LocalContentColor.current.copy(alpha = 0.8f),
                            modifier = Modifier.size(thumbnailSize / 2),
                        )
                    },
                    shape = thumbnailShape,
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = playlist.playlist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun LibraryAlbumSpotlightCard(
    album: Album,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    onPlay: (() -> Unit)? = null,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val subtitle =
        joinByBullet(
            album.artists.joinToString { it.name },
            pluralStringResource(R.plurals.n_song, album.album.songCount, album.album.songCount),
        )
    val context = LocalContext.current
    var extractedGlowColor by remember(album.album.thumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(400),
        label = "albumItemGlow",
    )
    LaunchedEffect(album.album.thumbnailUrl) {
        val url = album.album.thumbnailUrl ?: return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }

    Card(
        shape = shape,
        colors =
            CardDefaults.cardColors(
                containerColor =
                    if (isActive) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
            ),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LibraryCardThumbnailSize)
                        .shadow(
                            elevation = LibraryCardGlowElevation,
                            shape = RoundedCornerShape(18.dp),
                            clip = false,
                            ambientColor = glowColor.copy(alpha = LibraryCardGlowAmbientAlpha),
                            spotColor = glowColor.copy(alpha = LibraryCardGlowSpotAlpha),
                        ),
            ) {
                LocalThumbnail(
                    thumbnailUrl = album.album.thumbnailUrl,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxSize(),
                )
                if (onPlay != null) {
                    AlbumPlayButton(
                        visible = !isActive,
                        onClick = onPlay,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = album.album.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun LibraryArtistSpotlightCard(
    artist: Artist,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    val context = LocalContext.current
    var extractedGlowColor by remember(artist.artist.thumbnailUrl) { mutableStateOf(Color.Transparent) }
    val glowColor by animateColorAsState(
        targetValue = extractedGlowColor,
        animationSpec = tween(400),
        label = "artistItemGlow",
    )
    LaunchedEffect(artist.artist.thumbnailUrl) {
        val url = artist.artist.thumbnailUrl ?: return@LaunchedEffect
        val bitmap =
            runCatching {
                context.imageLoader
                    .execute(
                        ImageRequest
                            .Builder(context)
                            .data(url)
                            .size(PlayerColorExtractor.Config.IMAGE_SIZE, PlayerColorExtractor.Config.IMAGE_SIZE)
                            .allowHardware(false)
                            .build(),
                    ).image
                    ?.toBitmap()
            }.getOrNull() ?: return@LaunchedEffect
        extractedGlowColor = withContext(Dispatchers.Default) { bitmap.extractThemeColor() }
    }
    Card(
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        modifier = modifier,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(LibraryCardThumbnailSize)
                        .shadow(
                            elevation = LibraryCardGlowElevation,
                            shape = CircleShape,
                            clip = false,
                            ambientColor = glowColor.copy(alpha = LibraryCardGlowAmbientAlpha),
                            spotColor = glowColor.copy(alpha = LibraryCardGlowSpotAlpha),
                        ),
            ) {
                LocalThumbnail(
                    thumbnailUrl = artist.artist.thumbnailUrl,
                    isActive = false,
                    isPlaying = false,
                    shape = CircleShape,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = artist.artist.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = pluralStringResource(R.plurals.n_song, artist.songCount, artist.songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
                modifier = Modifier.padding(start = 12.dp),
            ) {
                trailingContent()
            }
        }
    }
}

@Composable
fun MediaMetadataListItem(
    mediaMetadata: MediaMetadata,
    modifier: Modifier = Modifier,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    shouldLoadImage: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
) {
    ListItem(
        title = mediaMetadata.title,
        subtitle =
            joinByBullet(
                mediaMetadata.artists.joinToString { it.name },
                makeTimeString(mediaMetadata.duration * 1000L),
            ),
        thumbnailContent = {
            ItemThumbnail(
                thumbnailUrl = mediaMetadata.thumbnailUrl,
                albumIndex = null,
                isSelected = isSelected,
                isActive = isActive,
                isPlaying = isPlaying,
                shouldLoadImage = shouldLoadImage,
                shape = ListArtworkShape,
                modifier = Modifier.size(ListThumbnailSize),
            )
        },
        trailingContent = trailingContent,
        modifier = modifier,
        isActive = isActive,
        thumbnailShape = ListArtworkShape,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YouTubeListItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    viewCountText: String? = null,
    showSourceIcon: Boolean = false,
    isSelected: Boolean = false,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    isSwipeable: Boolean = true,
    swipeContentBackgroundColor: Color? = null,
    showActiveContainer: Boolean = true,
    trailingContent: @Composable RowScope.() -> Unit = {},
    badges: @Composable RowScope.() -> Unit = {
        if (showSourceIcon) {
            SongSourceIcon(isLocal = false)
        }
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)

        if ((item is SongItem && song?.song?.liked == true) ||
            (item is AlbumItem && album?.album?.bookmarkedAt != null)
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem && song?.song?.inLibrary != null) {
            Icon.Library()
        }
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            val download = downloads[item.id]
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
) {
    val swipeEnabled by rememberPreference(SwipeToSongKey, defaultValue = true)

    val content: @Composable () -> Unit = {
        ListItem(
            title = item.title,
            subtitle =
                when (item) {
                    is SongItem -> {
                        joinByBullet(
                            item.artists.joinToString { it.name },
                            makeTimeString(item.duration?.times(1000L)),
                            viewCountText,
                        )
                    }

                    is AlbumItem -> {
                        joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                    }

                    is ArtistItem -> {
                        stringResource(R.string.artist_subtitle)
                    }

                    is PlaylistItem -> {
                        joinByBullet(item.author?.name, item.songCountText)
                    }

                    is PodcastItem -> item.author?.name

                    is EpisodeItem -> joinByBullet(item.podcast?.name, item.dateText, item.durationText)
                },
            badges = badges,
            thumbnailContent = {
                if (item is ArtistItem) {
                    ArtistHalo(
                        ringWidth = ListArtistHaloWidth,
                        modifier = Modifier.size(ListThumbnailSize),
                    ) {
                        ItemThumbnail(
                            thumbnailUrl = item.thumbnail,
                            isSelected = isSelected,
                            isActive = isActive,
                            isPlaying = isPlaying,
                            shape = CircleShape,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                } else {
                    ItemThumbnail(
                        thumbnailUrl = item.thumbnail,
                        albumIndex = albumIndex,
                        isSelected = isSelected,
                        isActive = isActive,
                        isPlaying = isPlaying,
                        shape = ListArtworkShape,
                        modifier = Modifier.size(ListThumbnailSize),
                    )
                }
            },
            trailingContent = trailingContent,
            modifier = modifier,
            isActive = isActive,
            showActiveContainer = showActiveContainer,
            thumbnailShape = if (item is ArtistItem || albumIndex != null) null else ListArtworkShape,
        )
    }

    if (item is SongItem && isSwipeable && swipeEnabled) {
        SwipeToSongBox(
            mediaItem =
                item
                    .copy(
                        thumbnail =
                            item.thumbnail.resize(
                                width = 1080,
                                height = 1080,
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        thumbnailWidth = null,
                        thumbnailHeight = null,
                    ).toMediaItem(),
            modifier = Modifier.fillMaxWidth(),
            contentBackgroundColor = swipeContentBackgroundColor,
        ) {
            content()
        }
    } else {
        content()
    }
}

@Composable
fun YouTubeGridItem(
    item: YTItem,
    modifier: Modifier = Modifier,
    coroutineScope: CoroutineScope? = null,
    badges: @Composable RowScope.() -> Unit = {
        val database = LocalDatabase.current
        val song by database.song(item.id).collectAsState(initial = null)
        val album by database.album(item.id).collectAsState(initial = null)

        if (item is SongItem && song?.song?.liked == true ||
            item is AlbumItem && album?.album?.bookmarkedAt != null
        ) {
            Icon.Favorite()
        }
        if (item.explicit) Icon.Explicit()
        if (item is SongItem && song?.song?.inLibrary != null) Icon.Library()
        if (item is SongItem) {
            val downloads by LocalDownloadUtil.current.downloads.collectAsState()
            val download = downloads[item.id]
            Icon.Download(download?.state, percent = download?.percentDownloaded ?: -1f)
        }
    },
    thumbnailRatio: Float? = null,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
) {
    val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
    val resolvedThumbnailRatio = thumbnailRatio ?: item.preferredThumbnailRatio(cropThumbnailToSquare)

    val isArtist = item is ArtistItem

    GridItem(
        title = {
            GridTitle(
                text = item.title,
                isActive = isActive,
                centered = isArtist,
            )
        },
        subtitle = {
            val subtitle =
                when (item) {
                    is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
                    is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
                    is ArtistItem -> stringResource(R.string.artist_subtitle)
                    is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
                    is PodcastItem -> item.author?.name
                    is EpisodeItem -> joinByBullet(item.podcast?.name, item.dateText, item.durationText)
                }
            if (!subtitle.isNullOrEmpty()) {
                GridSubtitle(
                    text = subtitle,
                    centered = isArtist,
                    maxLines = if (isArtist) 1 else 2,
                )
            }
        },
        badges = badges,
        thumbnailContent = {
            val database = LocalDatabase.current
            val playerConnection = LocalPlayerConnection.current ?: return@GridItem

            ItemThumbnail(
                thumbnailUrl = item.thumbnail,
                isActive = isActive,
                isPlaying = isPlaying,
                shape = if (isArtist) CircleShape else GridArtworkShape,
                contentScale = if (isArtist) ContentScale.Crop else null,
                thumbnailRatio = resolvedThumbnailRatio,
                sourceAspectRatio = item.thumbnailSourceRatio,
            )

            OverlayPlayButton(
                visible = (item is SongItem || item is EpisodeItem) && !isActive,
            )

            AlbumPlayButton(
                visible = item is AlbumItem && !isActive,
                onClick = {
                    coroutineScope?.launch(Dispatchers.IO) {
                        var albumWithSongs = database.albumWithSongs(item.id).first()
                        if (albumWithSongs?.songs.isNullOrEmpty()) {
                            YouTube
                                .album(item.id)
                                .onSuccess { albumPage ->
                                    database.transaction { insert(albumPage) }
                                    albumWithSongs = database.albumWithSongs(item.id).first()
                                }.onFailure { reportException(it) }
                        }
                        albumWithSongs?.let {
                            withContext(Dispatchers.Main) {
                                playerConnection.playQueue(LocalAlbumRadio(it))
                            }
                        }
                    }
                },
            )
        },
        thumbnailRatio = resolvedThumbnailRatio,
        isArtist = isArtist,
        isActive = isActive,
        fillMaxWidth = fillMaxWidth,
        modifier = modifier,
    )
}

@Composable
fun LocalSongsGrid(
    title: String,
    subtitle: String,
    badges: @Composable RowScope.() -> Unit = {},
    thumbnailUrl: String?,
    isActive: Boolean = false,
    isPlaying: Boolean = false,
    fillMaxWidth: Boolean = false,
    modifier: Modifier = Modifier,
) = GridItem(
    title = title,
    subtitle = subtitle,
    badges = badges,
    thumbnailContent = {
        LocalThumbnail(
            thumbnailUrl = thumbnailUrl,
            isActive = isActive,
            isPlaying = isPlaying,
            shape = GridArtworkShape,
            modifier = if (fillMaxWidth) Modifier.fillMaxWidth() else Modifier,
            showCenterPlay = true,
            playButtonVisible = false,
        )
    },
    isActive = isActive,
    fillMaxWidth = fillMaxWidth,
    modifier = modifier,
)

@Composable
fun ItemThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    albumIndex: Int? = null,
    isSelected: Boolean = false,
    shouldLoadImage: Boolean = true,
    @DrawableRes placeholderIconRes: Int? = null,
    thumbnailRatio: Float = 1f,
    contentScale: ContentScale? = null,
    showPlaceholder: Boolean = false,
    maxSizePx: Int? = null,
    sizeBuckets: List<Int>? = null,
    sourceAspectRatio: Float? = null,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .fillMaxSize()
                .aspectRatio(thumbnailRatio)
                .clip(shape)
                .let {
                    if (showPlaceholder) {
                        it.background(MaterialTheme.colorScheme.surfaceVariant)
                    } else {
                        it
                    }
                },
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val resolvedContentScale = contentScale ?: if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }

        val guardedWidthPx = if (maxSizePx != null && widthPx != null) minOf(widthPx, maxSizePx) else widthPx
        val guardedHeightPx = if (maxSizePx != null && heightPx != null) minOf(heightPx, maxSizePx) else heightPx

        var currentUrl by remember(thumbnailUrl, guardedWidthPx, guardedHeightPx, sizeBuckets, sourceAspectRatio) {
            mutableStateOf(
                thumbnailUrl?.resize(
                    width = guardedWidthPx,
                    height = guardedHeightPx,
                    maxresAllowed = false,
                    sizeBuckets = sizeBuckets,
                    ytimgResizePolicy = YtimgResizePolicy.MatchSourceAspect,
                    sourceAspectRatio = sourceAspectRatio,
                ),
            )
        }

        if (albumIndex == null) {
            if (placeholderIconRes != null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        painter = painterResource(placeholderIconRes),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(ListThumbnailSize * 0.48f),
                    )
                }
            }

            if (shouldLoadImage && !currentUrl.isNullOrBlank()) {
                val request =
                    remember(currentUrl, guardedWidthPx, guardedHeightPx) {
                        ImageRequest
                            .Builder(context)
                            .data(currentUrl)
                            .allowHardware(true)
                            .apply {
                                if (guardedWidthPx != null && guardedHeightPx != null) {
                                    size(guardedWidthPx, guardedHeightPx)
                                }
                            }.build()
                    }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = resolvedContentScale,
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it },
                    onState = { state ->
                        if (state is coil3.compose.AsyncImagePainter.State.Error) {
                            getNextFallbackUrl(currentUrl)?.let { currentUrl = it }
                        }
                    },
                )
            } else if (placeholderIconRes == null) {
                Box(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }

        if (albumIndex != null) {
            AnimatedVisibility(
                visible = !isActive,
                enter = fadeIn() + expandIn(expandFrom = Alignment.Center),
                exit = shrinkOut(shrinkTowards = Alignment.Center) + fadeOut(),
            ) {
                Text(
                    text = albumIndex.toString(),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        if (isSelected) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .zIndex(1f)
                        .clip(shape)
                        .background(Color.Black.copy(alpha = 0.5f)),
            ) {
                Icon(
                    painter = painterResource(R.drawable.done),
                    contentDescription = null,
                )
            }
        }

        PlayingIndicatorBox(
            isActive = isActive,
            playWhenReady = isPlaying,
            color =
                if (albumIndex != null) {
                    if (isActive) {
                        MaterialTheme.colorScheme.onSecondaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                } else {
                    Color.White
                },
            modifier =
                Modifier
                    .fillMaxSize()
                    .background(
                        color =
                            if (albumIndex != null) {
                                Color.Transparent
                            } else {
                                Color.Black.copy(alpha = ActiveBoxAlpha)
                            },
                        shape = shape,
                    ),
        )
    }
}

@Composable
fun LocalThumbnail(
    thumbnailUrl: String?,
    isActive: Boolean,
    isPlaying: Boolean,
    shape: Shape,
    modifier: Modifier = Modifier,
    showCenterPlay: Boolean = false,
    playButtonVisible: Boolean = false,
    thumbnailRatio: Float = 1f,
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    BoxWithConstraints(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .aspectRatio(thumbnailRatio)
                .clip(shape),
    ) {
        val (cropThumbnailToSquare, _) = rememberPreference(CropThumbnailToSquareKey, false)
        val isYouTubeThumb = thumbnailUrl?.contains("ytimg.com", ignoreCase = true) == true
        val shouldApplySquareCrop = cropThumbnailToSquare && isYouTubeThumb && kotlin.math.abs(thumbnailRatio - 1f) < 0.001f
        val widthPx = if (maxWidth == Dp.Infinity) null else with(density) { maxWidth.roundToPx().coerceAtLeast(1) }
        val heightPx = if (maxHeight == Dp.Infinity) null else with(density) { maxHeight.roundToPx().coerceAtLeast(1) }
        val request =
            remember(thumbnailUrl, widthPx, heightPx) {
                ImageRequest
                    .Builder(context)
                    .data(thumbnailUrl)
                    .allowHardware(true)
                    .apply {
                        if (widthPx != null && heightPx != null) {
                            size(widthPx, heightPx)
                        }
                    }.build()
            }
        AsyncImage(
            model = request,
            contentDescription = null,
            contentScale = if (shouldApplySquareCrop) ContentScale.Crop else ContentScale.Fit,
            modifier = Modifier.fillMaxSize().let { if (shouldApplySquareCrop) it.aspectRatio(1f) else it },
        )

        AnimatedVisibility(
            visible = isActive,
            enter = fadeIn(tween(500)),
            exit = fadeOut(tween(500)),
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f), shape),
            ) {
                if (isPlaying) {
                    PlayingIndicator(
                        color = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                } else {
                    Icon(
                        painter = painterResource(R.drawable.play),
                        contentDescription = null,
                        tint = Color.White,
                    )
                }
            }
        }

        if (showCenterPlay) {
            OverlayPlayButton(visible = !(isActive && isPlaying))
        }

        if (playButtonVisible) {
            OverlayPlayButton(visible = !isActive)
        }
    }
}

@Composable
fun PlaylistThumbnail(
    thumbnails: List<String>,
    size: Dp,
    placeHolder: @Composable () -> Unit,
    shape: Shape,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val sizePx = with(density) { size.roundToPx().coerceAtLeast(1) }

    when (thumbnails.size) {
        0 -> {
            Box(
                contentAlignment = Alignment.Center,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape)
                        .background(MaterialTheme.colorScheme.surfaceContainer),
            ) {
                placeHolder()
            }
        }

        1 -> {
            val request =
                remember(thumbnails, sizePx) {
                    ImageRequest
                        .Builder(context)
                        .data(
                            thumbnails[0].resize(
                                width = (sizePx * 1.5).toInt(),
                                height = (sizePx * 1.5).toInt(),
                                ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                            ),
                        ).size(sizePx, sizePx)
                        .allowHardware(true)
                        .build()
                }
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            )
        }

        else -> {
            Box(
                modifier =
                    Modifier
                        .size(size)
                        .clip(shape),
            ) {
                listOf(
                    Alignment.TopStart,
                    Alignment.TopEnd,
                    Alignment.BottomStart,
                    Alignment.BottomEnd,
                ).fastForEachIndexed { index, alignment ->
                    val halfPx = (sizePx / 2).coerceAtLeast(1)
                    val url = thumbnails.getOrNull(index)
                    val request =
                        remember(url, halfPx) {
                            ImageRequest
                                .Builder(context)
                                .data(
                                    url?.resize(
                                        width = (halfPx * 1.5).toInt(),
                                        height = (halfPx * 1.5).toInt(),
                                        ytimgResizePolicy = YtimgResizePolicy.PreserveOriginal,
                                    ),
                                ).size(halfPx, halfPx)
                                .allowHardware(true)
                                .build()
                        }
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier =
                            Modifier
                                .align(alignment)
                                .size(size / 2),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayGlyph(
    containerColor: Color,
    contentColor: Color,
    elevation: Dp,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier =
            modifier
                .size(36.dp)
                .shadow(elevation = elevation, shape = CircleShape)
                .background(containerColor, CircleShape),
    ) {
        Icon(
            painter = painterResource(R.drawable.play),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
fun BoxScope.OverlayPlayButton(visible: Boolean) {
    val motionScheme = MaterialTheme.motionScheme
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motionScheme.fastEffectsSpec()) + scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.6f),
        exit = fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.6f),
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp),
    ) {
        PlayGlyph(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
            contentColor = MaterialTheme.colorScheme.onSurface,
            elevation = 4.dp,
        )
    }
}

@Composable
fun BoxScope.AlbumPlayButton(
    visible: Boolean,
    onClick: () -> Unit,
) {
    val motionScheme = MaterialTheme.motionScheme
    val playLabel = stringResource(R.string.play)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(motionScheme.fastEffectsSpec()) + scaleIn(motionScheme.fastSpatialSpec(), initialScale = 0.6f),
        exit = fadeOut(motionScheme.fastEffectsSpec()) + scaleOut(motionScheme.fastSpatialSpec(), targetScale = 0.6f),
        modifier =
            Modifier
                .align(Alignment.BottomEnd)
                .padding(4.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(44.dp)
                    .clickable(
                        interactionSource = null,
                        indication = ripple(bounded = false, radius = 22.dp),
                        role = Role.Button,
                        onClick = onClick,
                    ).semantics { contentDescription = playLabel },
        ) {
            PlayGlyph(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                elevation = 6.dp,
            )
        }
    }
}

@Composable
fun SwipeToSongBox(
    modifier: Modifier = Modifier,
    mediaItem: MediaItem,
    contentBackgroundColor: Color? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val ctx = LocalContext.current
    val player = LocalPlayerConnection.current
    val scope = rememberCoroutineScope()
    val offset = remember { mutableStateOf(0f) }
    val threshold = 300f
    val resolvedContentBackgroundColor = contentBackgroundColor ?: MaterialTheme.colorScheme.surface

    val dragState =
        rememberDraggableState { delta ->
            offset.value = (offset.value + delta).coerceIn(-threshold, threshold)
        }

    Box(
        modifier =
            modifier
                .fillMaxWidth()
                .draggable(
                    orientation = Orientation.Horizontal,
                    state = dragState,
                    onDragStopped = {
                        when {
                            offset.value >= threshold -> {
                                player?.playNext(listOf(mediaItem))
                                Toast.makeText(ctx, R.string.play_next, Toast.LENGTH_SHORT).show()
                                reset(offset, scope)
                            }

                            offset.value <= -threshold -> {
                                player?.addToQueue(listOf(mediaItem))
                                Toast.makeText(ctx, R.string.add_to_queue, Toast.LENGTH_SHORT).show()
                                reset(offset, scope)
                            }

                            else -> {
                                reset(offset, scope)
                            }
                        }
                    },
                ),
    ) {
        if (offset.value != 0f) {
            val (iconRes, bg, tint, align) =
                if (offset.value > 0) {
                    Quadruple(
                        R.drawable.playlist_play,
                        MaterialTheme.colorScheme.secondary,
                        MaterialTheme.colorScheme.onSecondary,
                        Alignment.CenterStart,
                    )
                } else {
                    Quadruple(
                        R.drawable.queue_music,
                        MaterialTheme.colorScheme.primary,
                        MaterialTheme.colorScheme.onPrimary,
                        Alignment.CenterEnd,
                    )
                }

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .align(Alignment.Center)
                        .background(bg),
                contentAlignment = align,
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(horizontal = 24.dp)
                            .size(30.dp)
                            .alpha(0.9f),
                    tint = tint,
                )
            }
        }

        Box(
            modifier =
                Modifier
                    .offset { IntOffset(offset.value.roundToInt(), 0) }
                    .fillMaxWidth()
                    .background(resolvedContentBackgroundColor),
            content = content,
        )
    }
}

// Helper to animate reset of swipe offset
private fun reset(
    offset: MutableState<Float>,
    scope: CoroutineScope,
) {
    scope.launch {
        animate(
            initialValue = offset.value,
            targetValue = 0f,
            animationSpec = tween(durationMillis = 300),
        ) { value, _ -> offset.value = value }
    }
}

// Data holder for swipe visuals
data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
)

private object Icon {
    @Composable
    fun Favorite() {
        Icon(
            painter = painterResource(R.drawable.favorite),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier =
                Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
        )
    }

    @Composable
    fun Library() {
        Icon(
            painter = painterResource(R.drawable.library_add_check),
            contentDescription = null,
            modifier =
                Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
        )
    }

    @Composable
    fun Download(
        state: Int?,
        percent: Float = -1f,
    ) {
        when (state) {
            STATE_COMPLETED -> {
                Icon(
                    painter = painterResource(R.drawable.offline),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(18.dp)
                            .padding(end = 2.dp),
                )
            }

            STATE_QUEUED, STATE_DOWNLOADING -> {
                if (percent > 0f) {
                    Box(contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            trackColor = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Text(
                            text = "${percent.toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            fontSize = 8.sp,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                } else {
                    CircularWavyProgressIndicator(
                        modifier =
                            Modifier
                                .size(16.dp)
                                .padding(end = 2.dp),
                    )
                }
            }

            else -> { /* no icon */ }
        }
    }

    @Composable
    fun Explicit() {
        Icon(
            painter = painterResource(R.drawable.explicit),
            contentDescription = null,
            modifier =
                Modifier
                    .size(18.dp)
                    .padding(end = 2.dp),
        )
    }
}
