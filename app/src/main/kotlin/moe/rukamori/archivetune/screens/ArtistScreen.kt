/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.DetailHero
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.MediaCard
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.viewmodels.ArtistViewModel

/**
 * アーティスト詳細。
 *
 * 端末内に持つ楽曲（[ArtistViewModel.librarySongs]）と、YouTube Music 側の
 * [ArtistViewModel.artistPage] を同じ画面に並べる。両者は由来が違うので再生方法も分ける。
 *
 * - 端末内 → [ListQueue]（MediaItem を確定させて並べる）
 * - オンライン → [YouTubeQueue]（endpoint を起点に続きを自動追加する）
 *
 * 画面の並び順はViewModelが用意したものをそのまま使う。並べ替えや絞り込みは持たない。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun ArtistScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ArtistViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val artistPage by viewModel.artistPage.collectAsState()
    val libraryArtist by viewModel.libraryArtist.collectAsState()
    val librarySongs by viewModel.librarySongs.collectAsState()
    val libraryAlbums by viewModel.libraryAlbums.collectAsState()

    val page = artistPage
    val remoteArtist = page?.artist

    val name =
        libraryArtist?.artist?.name
            ?: remoteArtist?.title
            ?: ""

    val thumbnailUrl =
        libraryArtist?.artist?.thumbnailUrl
            ?: remoteArtist?.thumbnail

    val localItems = librarySongs.map { it.toMediaItem() }

    LazyColumn(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
        contentPadding = PaddingValues(bottom = metrics.block.dp),
    ) {
        if (name.isEmpty() && page == null && libraryArtist == null) {
            item(key = "loading") { LoadingState(Modifier.fillMaxSize()) }
            return@LazyColumn
        }

        item(key = "hero") {
            DetailHero(
                title = name,
                thumbnailUrl = thumbnailUrl,
                onBack = onBack,
                onPlay = {
                    when {
                        // 端末内に曲があれば、そこを起点にする。
                        localItems.isNotEmpty() ->
                            playerConnection?.playQueue(
                                ListQueue(title = name, items = localItems),
                            )
                        // 無ければオンラインの再生 endpoint を起点にする。
                        else ->
                            remoteArtist?.playEndpoint?.let { endpoint ->
                                playerConnection?.playQueue(YouTubeQueue(endpoint))
                            }
                    }
                },
            )
        }

        if (librarySongs.isNotEmpty()) {
            item(key = "localSongsHead") { SectionHeader(title = stringResource(R.string.songs)) }
            itemsIndexed(
                items = librarySongs.take(LocalPreviewLimit),
                key = { _, song -> "local:${song.id}" },
            ) { index, song ->
                SongRow(
                    song = song.toMediaMetadata(),
                    onClick = {
                        playerConnection?.playQueue(
                            ListQueue(
                                title = name,
                                items = localItems,
                                startIndex = index.coerceIn(localItems.indices),
                            ),
                        )
                    },
                )
            }
        }

        if (libraryAlbums.isNotEmpty()) {
            item(key = "localAlbumsHead") { SectionHeader(title = stringResource(R.string.albums)) }
            item(key = "localAlbumsRow") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = libraryAlbums, key = { it.id }) { album ->
                        MediaCard(
                            title = album.title,
                            thumbnailUrl = album.thumbnailUrl,
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                }
            }
        }

        page?.sections?.forEach { section ->
            val songs = section.items.filterIsInstance<SongItem>()
            val others = section.items.filterNot { it is SongItem }

            if (songs.isNotEmpty()) {
                item(key = "songs:${section.title}") { SectionHeader(title = section.title) }
                items(items = songs, key = { "remote:${it.id}" }) { song ->
                    SongRow(
                        song = song.toMediaMetadata(),
                        onClick = {
                            // オンラインの曲は endpoint 起点。endpoint が無い曲は videoId から組み立てる。
                            playerConnection?.playQueue(
                                YouTubeQueue(
                                    endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                                    preloadItem = song.toMediaMetadata(),
                                ),
                            )
                        },
                    )
                }
            }

            if (others.isNotEmpty()) {
                item(key = "others:${section.title}") { SectionHeader(title = section.title) }
                item(key = "othersRow:${section.title}") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                        horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                    ) {
                        items(items = others, key = { it.id }) { item ->
                            when (item) {
                                is AlbumItem ->
                                    MediaCard(
                                        title = item.title,
                                        thumbnailUrl = item.thumbnail,
                                        onClick = { onOpenAlbum(item.id) },
                                    )

                                is ArtistItem ->
                                    // アーティストからアーティストへは進まない（循環するため）。
                                    MediaCard(
                                        title = item.title,
                                        thumbnailUrl = item.thumbnail,
                                        onClick = { onOpenPlaylist(item.id) },
                                    )

                                is PlaylistItem ->
                                    MediaCard(
                                        title = item.title,
                                        thumbnailUrl = item.thumbnail,
                                        onClick = { onOpenPlaylist(item.id) },
                                    )

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 画面に並べる上限。全曲を描くと一覧が長くなりすぎるので頭に絞る。 */
private const val LocalPreviewLimit = 20
