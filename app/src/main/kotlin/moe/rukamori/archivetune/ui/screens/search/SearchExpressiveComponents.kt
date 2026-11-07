/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

@file:OptIn(ExperimentalMaterial3ExpressiveApi::class)

package moe.rukamori.archivetune.ui.screens.search

import androidx.annotation.DrawableRes
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalAnimationsDisabled
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.AlbumReleaseType
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.ui.component.ItemThumbnail
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString
import kotlinx.coroutines.CoroutineScope

@Immutable
internal class SearchItemMotion(
    val fade: FiniteAnimationSpec<Float>?,
    val placement: FiniteAnimationSpec<IntOffset>?,
)

@Composable
internal fun rememberSearchItemMotion(): SearchItemMotion {
    val animationsDisabled = LocalAnimationsDisabled.current
    val motionScheme = MaterialTheme.motionScheme
    val fade = motionScheme.defaultEffectsSpec<Float>()
    val placement = motionScheme.defaultSpatialSpec<IntOffset>()
    return remember(animationsDisabled, fade, placement) {
        if (animationsDisabled) {
            SearchItemMotion(fade = null, placement = null)
        } else {
            SearchItemMotion(fade = fade, placement = placement)
        }
    }
}

@Composable
internal fun SearchMorphBadge(
    @DrawableRes iconRes: Int,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    morphed: Boolean = false,
    restShape: RoundedPolygon = MaterialShapes.Circle,
    activeShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    size: Dp = SearchBadgeSize,
    iconSize: Dp = SearchBadgeIconSize,
    activeRotation: Float = SearchBadgeActiveRotation,
) {
    val animationsDisabled = LocalAnimationsDisabled.current
    val morph = remember(restShape, activeShape) { Morph(restShape, activeShape) }
    val progress by animateFloatAsState(
        targetValue = if (morphed) 1f else 0f,
        animationSpec = if (animationsDisabled) snap() else MaterialTheme.motionScheme.fastSpatialSpec(),
        label = "searchBadgeMorph",
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(size),
    ) {
        Box(
            modifier =
                Modifier
                    .matchParentSize()
                    .graphicsLayer {
                        val value = progress.coerceIn(0f, 1f)
                        rotationZ = progress * activeRotation
                        shape = EditorialMorphShape(morph, value)
                        clip = true
                    }.background(containerColor),
        )
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
internal fun SearchSectionLabel(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(
                    start = SearchListHorizontalPadding + 8.dp,
                    end = SearchListHorizontalPadding,
                    top = 20.dp,
                    bottom = 8.dp,
                ),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier =
                Modifier
                    .weight(1f)
                    .semantics { heading() },
        )
        trailing()
    }
}

@Composable
internal fun DiscoverySectionHeader(
    title: String,
    @DrawableRes iconRes: Int,
    badgeShape: RoundedPolygon,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    action: (@Composable () -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(top = 20.dp, bottom = 12.dp),
    ) {
        SearchMorphBadge(
            iconRes = iconRes,
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            restShape = badgeShape,
            activeShape = badgeShape,
            size = DiscoveryBadgeSize,
            iconSize = 22.dp,
        )
        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .semantics(mergeDescendants = true) { heading() },
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!supporting.isNullOrEmpty()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
internal fun searchResultTypeLabel(item: YTItem): String =
    when (item) {
        is SongItem -> {
            stringResource(R.string.search_type_song)
        }

        is AlbumItem -> {
            when (item.releaseType) {
                AlbumReleaseType.SINGLE -> stringResource(R.string.search_type_single)
                AlbumReleaseType.EP -> stringResource(R.string.search_type_ep)
                AlbumReleaseType.ALBUM -> stringResource(R.string.search_type_album)
            }
        }

        is ArtistItem -> {
            stringResource(R.string.artist_subtitle)
        }

        is PlaylistItem -> {
            stringResource(R.string.search_type_playlist)
        }

        is PodcastItem -> {
            stringResource(R.string.podcast)
        }

        is EpisodeItem -> {
            stringResource(R.string.search_type_episode)
        }
    }

internal fun searchResultSubtitle(item: YTItem): String? =
    when (item) {
        is SongItem ->
            joinByBullet(
                item.artists.joinToString { it.name },
                makeTimeString(item.duration?.times(1000L)),
                item.viewCountText,
            )
        is AlbumItem -> joinByBullet(item.artists?.joinToString { it.name }, item.year?.toString())
        is ArtistItem -> null
        is PlaylistItem -> joinByBullet(item.author?.name, item.songCountText)
        is PodcastItem -> item.author?.name
        is EpisodeItem -> joinByBullet(item.podcast?.name, item.dateText, item.durationText)
    }?.takeIf { it.isNotBlank() }

internal fun YTItem.hasSearchResultMenu(): Boolean = this !is PodcastItem && this !is EpisodeItem

@Composable
internal fun SearchResultMenu(
    item: YTItem,
    navController: NavController,
    coroutineScope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    when (item) {
        is SongItem -> {
            YouTubeSongMenu(
                song = item,
                navController = navController,
                onDismiss = onDismiss,
            )
        }

        is AlbumItem -> {
            YouTubeAlbumMenu(
                albumItem = item,
                navController = navController,
                onDismiss = onDismiss,
            )
        }

        is ArtistItem -> {
            YouTubeArtistMenu(
                artist = item,
                onDismiss = onDismiss,
            )
        }

        is PlaylistItem -> {
            YouTubePlaylistMenu(
                playlist = item,
                coroutineScope = coroutineScope,
                onDismiss = onDismiss,
            )
        }

        is PodcastItem, is EpisodeItem -> {
            Unit
        }
    }
}

internal val SearchListHorizontalPadding = 12.dp
internal val SearchBadgeSize = 40.dp
internal val SearchBadgeIconSize = 20.dp
private val DiscoveryBadgeSize = 44.dp
private const val SearchBadgeActiveRotation = 30f

@Composable
internal fun TopResultCard(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val hasMenu = item.hasSearchResultMenu()
    val isPlayable = item is SongItem || item is EpisodeItem
    val showPause = isActive && isPlaying && item is SongItem
    val typeLabel = searchResultTypeLabel(item)
    val subtitle = searchResultSubtitle(item)
    val menuLabel = stringResource(R.string.more_options)
    val containerColor =
        when {
            isActive -> colorScheme.secondaryContainer
            pureBlack -> Color.White.copy(alpha = 0.10f)
            else -> colorScheme.surfaceContainerHigh
        }

    Surface(
        shape = TopResultShape,
        color = containerColor,
        modifier =
            modifier
                .fillMaxWidth()
                .clip(TopResultShape)
                .combinedClickable(
                    onClick = onClick,
                    onLongClickLabel = if (hasMenu) menuLabel else null,
                    onLongClick =
                        if (hasMenu) {
                            {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                onMenu()
                            }
                        } else {
                            null
                        },
                ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SearchResultThumbnail(
                    item = item,
                    isActive = isActive,
                    isPlaying = isPlaying,
                    size = TopResultThumbnailSize,
                    cornerRadius = 20.dp,
                )
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = CircleShape,
                        color = colorScheme.tertiaryContainer,
                        contentColor = colorScheme.onTertiaryContainer,
                    ) {
                        Text(
                            text = typeLabel,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    Spacer(Modifier.size(8.dp))
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (subtitle != null || item.explicit) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(top = 2.dp),
                        ) {
                            if (item.explicit) {
                                Icon(
                                    painter = painterResource(R.drawable.explicit),
                                    contentDescription = null,
                                    tint = colorScheme.onSurfaceVariant,
                                    modifier =
                                        Modifier
                                            .padding(end = 4.dp)
                                            .size(16.dp),
                                )
                            }
                            if (subtitle != null) {
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }

            if (isPlayable || hasMenu) {
                Spacer(Modifier.size(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isPlayable) {
                        Button(
                            onClick = onClick,
                            contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                        ) {
                            Icon(
                                painter = painterResource(if (showPause) R.drawable.pause else R.drawable.play),
                                contentDescription = null,
                                modifier = Modifier.size(ButtonDefaults.IconSize),
                            )
                            Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                            Text(stringResource(if (showPause) R.string.search_pause else R.string.play))
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    if (hasMenu) {
                        IconButton(onClick = onMenu) {
                            Icon(
                                painter = painterResource(R.drawable.more_vert),
                                contentDescription = menuLabel,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun SearchResultRow(
    item: YTItem,
    index: Int,
    count: Int,
    isActive: Boolean,
    isPlaying: Boolean,
    pureBlack: Boolean,
    onClick: () -> Unit,
    onMenu: () -> Unit,
    modifier: Modifier = Modifier,
    wideThumbnail: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    val colorScheme = MaterialTheme.colorScheme
    val hasMenu = item.hasSearchResultMenu()
    val menuLabel = stringResource(R.string.more_options)
    val typeLabel =
        if (wideThumbnail && item is SongItem) {
            stringResource(R.string.search_type_video)
        } else {
            searchResultTypeLabel(item)
        }
    val supporting = joinByBullet(typeLabel, searchResultSubtitle(item))

    SegmentedListItem(
        onClick = onClick,
        shapes = ListItemDefaults.segmentedShapes(index = index, count = count),
        colors =
            ListItemDefaults.segmentedColors(
                containerColor = if (isActive) colorScheme.secondaryContainer else searchRowContainerColor(pureBlack),
            ),
        onLongClick =
            if (hasMenu) {
                {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onMenu()
                }
            } else {
                null
            },
        onLongClickLabel = if (hasMenu) menuLabel else null,
        leadingContent = {
            SearchResultThumbnail(
                item = item,
                isActive = isActive,
                isPlaying = isPlaying,
                size = SearchResultThumbnailSize,
                cornerRadius = 12.dp,
                aspectRatio = if (wideThumbnail) WideThumbnailRatio else 1f,
            )
        },
        supportingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (item.explicit) {
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
                    text = supporting,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        trailingContent =
            if (hasMenu) {
                {
                    IconButton(onClick = onMenu) {
                        Icon(
                            painter = painterResource(R.drawable.more_vert),
                            contentDescription = menuLabel,
                        )
                    }
                }
            } else {
                null
            },
        modifier = modifier.fillMaxWidth(),
    ) {
        Text(
            text = item.title,
            fontWeight = if (isActive) FontWeight.SemiBold else null,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
internal fun SearchResultThumbnail(
    item: YTItem,
    isActive: Boolean,
    isPlaying: Boolean,
    size: Dp,
    cornerRadius: Dp,
    aspectRatio: Float = 1f,
) {
    val isArtist = item is ArtistItem
    ItemThumbnail(
        thumbnailUrl = item.thumbnail,
        isActive = isActive,
        isPlaying = isPlaying,
        shape = if (isArtist) CircleShape else RoundedCornerShape(cornerRadius),
        contentScale = if (isArtist || aspectRatio != 1f) ContentScale.Crop else null,
        thumbnailRatio = if (isArtist) 1f else aspectRatio,
        modifier = Modifier.height(size).width(size * (if (isArtist) 1f else aspectRatio)),
    )
}

@Composable
internal fun searchRowContainerColor(pureBlack: Boolean): Color =
    if (pureBlack) {
        Color.White.copy(alpha = 0.07f)
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }

private val TopResultShape = RoundedCornerShape(28.dp)
private val TopResultThumbnailSize = 96.dp
private val SearchResultThumbnailSize = 56.dp
private const val WideThumbnailRatio = 16f / 9f

@Composable
internal fun SearchStatePanel(
    @DrawableRes iconRes: Int,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    badgeShape: RoundedPolygon = MaterialShapes.Cookie9Sided,
    isError: Boolean = false,
    action: (@Composable () -> Unit)? = null,
) {
    val colorScheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 56.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier =
                Modifier
                    .size(StatePanelBadgeSize)
                    .background(
                        color = if (isError) colorScheme.errorContainer else colorScheme.secondaryContainer,
                        shape = badgeShape.toShape(),
                    ),
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = if (isError) colorScheme.onErrorContainer else colorScheme.onSecondaryContainer,
                modifier = Modifier.size(40.dp),
            )
        }
        Spacer(Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Spacer(Modifier.size(20.dp))
            action()
        }
    }
}

@Composable
internal fun SearchLoadingPanel(
    message: String,
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp, vertical = 72.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        LoadingIndicator(modifier = Modifier.size(72.dp))
        Spacer(Modifier.size(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
internal fun SearchInlineStatus(
    message: String,
    modifier: Modifier = Modifier,
    loading: Boolean = false,
    onRetry: (() -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 12.dp)
                .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
    ) {
        if (loading) {
            LoadingIndicator(modifier = Modifier.size(32.dp))
        } else {
            Icon(
                painter = painterResource(R.drawable.error),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(24.dp),
            )
        }
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        if (onRetry != null) {
            TextButton(onClick = onRetry) {
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

private val StatePanelBadgeSize = 104.dp
