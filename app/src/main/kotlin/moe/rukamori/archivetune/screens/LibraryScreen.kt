/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LibraryFilter
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.ListItemRow
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.viewmodels.LibraryAlbumsViewModel
import moe.rukamori.archivetune.viewmodels.LibraryArtistsViewModel
import moe.rukamori.archivetune.viewmodels.LibraryPlaylistsViewModel
import moe.rukamori.archivetune.viewmodels.LibrarySongsViewModel
import moe.rukamori.archivetune.viewmodels.LibraryViewModel

/**
 * Library タブ。
 *
 * [LibraryViewModel.filter] がタブの選択状態を持つので、そこだけを見て
 * 表示を切り替える。曲・アーティスト・アルバム・プレイリストはそれぞれ
 * 専用 ViewModel が流すので、画面側は読むだけでよい。
 *
 * 初期値は [LibraryFilter.LIBRARY]（混合表示）。ここでは4種のタブしか
 * 持たないため、混合の間は曲と同等に扱い、タブを触った時点で確定する。
 *
 * @param navController アルバム・アーティスト・プレイリストの詳細へ送る。
 */
@Composable
fun LibraryScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val libraryViewModel: LibraryViewModel = hiltViewModel()
    val songsViewModel: LibrarySongsViewModel = hiltViewModel()
    val artistsViewModel: LibraryArtistsViewModel = hiltViewModel()
    val albumsViewModel: LibraryAlbumsViewModel = hiltViewModel()
    val playlistsViewModel: LibraryPlaylistsViewModel = hiltViewModel()

    val windowInsets = LocalPlayerAwareWindowInsets.current
    val playerConnection = LocalPlayerConnection.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val filterState = libraryViewModel.filter
    val songs by songsViewModel.allSongs.collectAsState()
    val artists by artistsViewModel.allArtists.collectAsState()
    val albums by albumsViewModel.allAlbums.collectAsState()
    val playlists by playlistsViewModel.allPlaylists.collectAsState()

    val active =
        when (filterState.value) {
            LibraryFilter.ARTISTS, LibraryFilter.ALBUMS, LibraryFilter.PLAYLISTS -> filterState.value
            else -> LibraryFilter.SONGS
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        LargeTitleHeader(title = stringResource(R.string.filter_library))

        LibraryTabRow(
            active = active,
            onSelect = { filterState.value = it },
        )

        val isEmpty =
            when (active) {
                LibraryFilter.ARTISTS -> artists.isEmpty()
                LibraryFilter.ALBUMS -> albums.isEmpty()
                LibraryFilter.PLAYLISTS -> playlists.isEmpty()
                else -> songs.isEmpty()
            }

        if (isEmpty) {
            EmptyState(
                title = stringResource(R.string.filter_library),
                message = stringResource(R.string.no_results_found),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .weight(1f),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = metrics.block.dp),
            ) {
                when (active) {
                    LibraryFilter.ARTISTS -> {
                        items(items = artists, key = { it.id }) { artist ->
                            ListItemRow(
                                title = artist.title,
                                thumbnailUrl = artist.thumbnailUrl,
                                circularArtwork = true,
                                onClick = { navController.navigate(Routes.artist(artist.id)) },
                            )
                        }
                    }

                    LibraryFilter.ALBUMS -> {
                        items(items = albums, key = { it.id }) { album ->
                            ListItemRow(
                                title = album.title,
                                thumbnailUrl = album.thumbnailUrl,
                                onClick = { navController.navigate(Routes.album(album.id)) },
                            )
                        }
                    }

                    LibraryFilter.PLAYLISTS -> {
                        items(items = playlists, key = { it.id }) { playlist ->
                            ListItemRow(
                                title = playlist.title,
                                subtitle = playlist.songCount.takeIf { it > 0 }?.toString(),
                                thumbnailUrl = playlist.thumbnails.firstOrNull(),
                                onClick = {
                                    navController.navigate(Routes.localPlaylist(playlist.id))
                                },
                            )
                        }
                    }

                    else -> {
                        itemsIndexed(items = songs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song.toMediaMetadata(),
                                onClick = {
                                    // 端末内に置く曲をまとめて再生する。選択行を先頭に
                                    // 置く。オンライン曲をシードにしたラジオ生成とは
                                    // 経路を分けており、キューを作り直さない。
                                    // playLocalSongs の実体は ItemActions.kt。
                                    playerConnection?.playLocalSongs(songs, index)
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/** Library のカテゴリタブ。下線ではなく字の太さと色で現在地を示す。 */
@Composable
private fun LibraryTabRow(
    active: LibraryFilter,
    onSelect: (LibraryFilter) -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    val tabs =
        listOf(
            LibraryFilter.SONGS to R.string.songs,
            LibraryFilter.ARTISTS to R.string.artists,
            LibraryFilter.ALBUMS to R.string.albums,
            LibraryFilter.PLAYLISTS to R.string.playlists,
        )

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.xs.dp,
                ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { (filter, labelId) ->
            val isSelected = filter == active
            // iOS のライブラリフィルタと同じく、選択中のみ丸い地色を敷いて選択外は地抜きにする。
            Text(
                text = stringResource(labelId),
                style =
                    type.subhead.copy(
                        fontWeight =
                            if (isSelected) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
                    ),
                color = if (isSelected) palette.onFill else palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .padding(end = metrics.xs.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) palette.fill else Color.Transparent)
                        .padding(horizontal = metrics.md.dp, vertical = metrics.xs.dp)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(filter) },
            )
        }
    }
}
