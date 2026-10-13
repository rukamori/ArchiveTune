/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.layout.windowInsetsPadding
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.EmptyRow
import moe.rukamori.archivetune.design.components.DetailHero
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.viewmodels.OnlinePlaylistViewModel

/**
 * YouTube Music 上のプレイリスト詳細。
 *
 * [OnlinePlaylistViewModel] が init で読み、[OnlinePlaylistViewModel.retry] で取り直す。
 * 曲は [SongItem]（オンライン由来）なので、再生は endpoint 経由の [YouTubeQueue] で行う。
 * 端末内の一覧で使う `ListQueue` とは混ぜない（MediaItem を先に確定させると
 * 後続の自動追加が成立しなくなる）。
 *
 * 曲1件の再生は既存UIが使う構造（`ArtistScreen.kt:851-856`）に合わせている。
 * プレイリスト全体を起点にした起動はヘッダの再生ボタン側で行う。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun OnlinePlaylistScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnlinePlaylistViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()
    val error by viewModel.error.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    val current = playlist

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        if (current == null) {
            if (error != null) {
                ErrorState(
                    title = stringResource(R.string.playlists),
                    message = error ?: stringResource(R.string.no_results_found),
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                LoadingState(Modifier.fillMaxSize())
            }
            return@Column
        }

        val playEndpoint = current.playEndpoint

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.block.dp),
        ) {
            item(key = "hero") {
                DetailHero(
                    title = current.title,
                    thumbnailUrl = current.thumbnail,
                    subtitle = current.author?.name,
                    onBack = onBack,
                    onPlay = playEndpoint?.let { endpoint ->
                        {
                            playerConnection?.playQueue(YouTubeQueue.playlist(endpoint))
                        }
                    },
                )
            }

            itemsIndexed(items = songs, key = { _, song -> song.id }) { _, song ->
                SongRow(
                    song = song.toMediaMetadata(),
                    onClick = {
                        // 曲を直接起点にする。endpoint を持たない曲は videoId から組み立て、
                        // ここで一覧そのものは渡さない（続きの自動追加は endpoint 起点のみ）。
                        playerConnection?.playQueue(
                            YouTubeQueue(
                                endpoint = song.endpoint ?: WatchEndpoint(videoId = song.id),
                                preloadItem = song.toMediaMetadata(),
                            ),
                        )
                    },
                )
            }

            if (isLoading && songs.isEmpty()) {
                item(key = "loading") { LoadingState(Modifier.fillMaxWidth()) }
            }

            if (songs.isEmpty() && !isLoading) {
                item(key = "empty") {
                    EmptyRow(message = stringResource(R.string.no_results_found))
                }
            }
        }
    }
}
