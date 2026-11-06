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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
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
import moe.rukamori.archivetune.ui.menu.YouTubeAlbumMenu
import moe.rukamori.archivetune.ui.menu.YouTubeArtistMenu
import moe.rukamori.archivetune.ui.menu.YouTubePlaylistMenu
import moe.rukamori.archivetune.ui.menu.YouTubeSongMenu
import moe.rukamori.archivetune.ui.player.EditorialMorphShape
import moe.rukamori.archivetune.utils.joinByBullet
import moe.rukamori.archivetune.utils.makeTimeString

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
        is SongItem -> joinByBullet(item.artists.joinToString { it.name }, makeTimeString(item.duration?.times(1000L)))
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
