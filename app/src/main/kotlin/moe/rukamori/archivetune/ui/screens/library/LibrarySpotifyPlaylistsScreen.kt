/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.screens.library

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.spotify.SpotifyLibraryViewModel
import moe.rukamori.archivetune.spotify.SpotifyMapper
import moe.rukamori.archivetune.spotify.models.SpotifyPlaylist
import moe.rukamori.archivetune.ui.component.ExpressivePullToRefreshBox

@Composable
fun LibrarySpotifyPlaylistsScreen(
    navController: NavController,
    viewModel: SpotifyLibraryViewModel = hiltViewModel(),
) {
    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val syncFailedMessage = stringResource(R.string.library_spotify_sync_failed)
    val openLikedSongs = remember(navController) { { navController.navigate("spotify_liked_songs") } }
    val playerAwareBottomPadding =
        LocalPlayerAwareWindowInsets.current
            .only(WindowInsetsSides.Bottom)
            .asPaddingValues()
            .calculateBottomPadding()

    LaunchedEffect(errorMessage, syncFailedMessage) {
        if (errorMessage != null) {
            snackbarHostState.showSnackbar(syncFailedMessage)
        }
    }

    val totalTracks = remember(playlists) { playlists.sumOf { it.tracks?.total ?: 0 } }
    val heroArtwork =
        remember(playlists) {
            playlists.asSequence().mapNotNull(SpotifyMapper::getPlaylistThumbnail).take(2).toList()
        }
    val heroStats =
        buildList {
            add(
                LibraryHeroStat(
                    value = playlists.size.toString(),
                    label = pluralStringResource(R.plurals.library_stat_playlists, playlists.size),
                ),
            )
            if (totalTracks > 0) {
                add(
                    LibraryHeroStat(
                        value = totalTracks.toString(),
                        label = pluralStringResource(R.plurals.library_stat_songs, totalTracks),
                    ),
                )
            }
        }

    Box(modifier = Modifier.fillMaxSize()) {
        ExpressivePullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = viewModel::refreshPlaylists,
            modifier = Modifier.fillMaxSize(),
            indicatorOffset = LibraryPullToRefreshIndicatorOffset,
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 156.dp),
                contentPadding =
                    PaddingValues(
                        start = LibraryContentHorizontalPadding,
                        top = LibraryHeaderContentPadding + 8.dp,
                        end = LibraryContentHorizontalPadding,
                        bottom = playerAwareBottomPadding + 16.dp,
                    ),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(
                    key = "spotify_hero",
                    span = { GridItemSpan(maxLineSpan) },
                    contentType = "library_hero",
                ) {
                    LibraryCollectionHero(
                        overline = stringResource(R.string.spotify_account),
                        title = stringResource(R.string.spotify_playlists),
                        stats = heroStats,
                        artworkUrls = heroArtwork,
                        fallbackIconRes = R.drawable.spotify,
                        primaryLabel = stringResource(R.string.liked_songs),
                        primaryIconRes = R.drawable.favorite,
                        onPrimary = openLikedSongs,
                    )
                }

                if (playlists.isEmpty() && !isRefreshing) {
                    item(
                        key = "spotify_empty",
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = "library_empty_state",
                    ) {
                        LibraryEmptyState(
                            iconRes = R.drawable.spotify,
                            titleRes = R.string.library_empty_spotify_title,
                            descriptionRes = R.string.library_empty_spotify_desc,
                            actionLabelRes = R.string.retry,
                            onAction = viewModel::refreshPlaylists,
                        )
                    }
                }

                items(
                    items = playlists,
                    key = { playlist -> playlist.id },
                    contentType = { "spotify_playlist" },
                ) { playlist ->
                    SpotifyPlaylistTile(
                        playlist = playlist,
                        onClick = { navController.navigate("spotify_playlist/${playlist.id}") },
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
internal fun SpotifyPlaylistTile(
    playlist: SpotifyPlaylist,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val thumbnailUrl = remember(playlist) { SpotifyMapper.getPlaylistThumbnail(playlist) }
    val interactionSource = remember { MutableInteractionSource() }
    val scale by rememberLibraryPressScale(interactionSource)
    val trackCount = playlist.tracks?.total ?: 0
    val supportingText =
        listOfNotNull(
            playlist.owner?.displayName?.takeIf(String::isNotBlank),
            pluralStringResource(R.plurals.n_song, trackCount, trackCount),
        ).joinToString(" • ")

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }.clip(libraryGridCardShape())
                .clickable(
                    interactionSource = interactionSource,
                    indication = ripple(),
                    onClick = onClick,
                ).padding(bottom = LibraryGridCardTextInset),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
        ) {
            LibraryArtworkMosaic(
                artworkUrls = listOfNotNull(thumbnailUrl),
                fallbackIconRes = R.drawable.spotify,
                shape = MaterialTheme.shapes.extraLarge,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                modifier =
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(32.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(R.drawable.spotify),
                    contentDescription = stringResource(R.string.spotify_account),
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = playlist.name,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
        Text(
            text = supportingText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = LibraryGridCardTextInset),
        )
    }
}
