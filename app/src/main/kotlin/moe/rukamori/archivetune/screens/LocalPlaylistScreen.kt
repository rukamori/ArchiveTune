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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import moe.rukamori.archivetune.design.components.EmptyRow
import moe.rukamori.archivetune.design.components.DetailHero
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.viewmodels.LocalPlaylistViewModel

/**
 * 端末内のプレイリスト詳細。
 *
 * [LocalPlaylistViewModel] が init で再生順の並びを整えたうえで曲を流すので、
 * 画面は `playlistSongs` を読むだけ。並べ替えや削除は既存のDB経路を介するため、
 * ここでは再生と表示に絞る。
 *
 * 再生は [ListQueue]（端末内の曲をそのまま並べる）。オンライン曲をシードにした
 * ラジオ生成とは別の経路なので混ぜない。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun LocalPlaylistScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: LocalPlaylistViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val playlist by viewModel.playlist.collectAsState()
    val songs by viewModel.playlistSongs.collectAsState()

    // `!!` を避けるためローカルへ落とす。これで以降はスマートキャストが効く。
    val current = playlist

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        if (current == null) {
            LoadingState(Modifier.fillMaxSize())
            return@Column
        }

        val title = current.title
        val mediaItems = songs.map { it.song.toMediaItem() }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.block.dp),
        ) {
            item(key = "hero") {
                DetailHero(
                    title = title,
                    thumbnailUrl = current.thumbnails.firstOrNull(),
                    subtitle = current.songCount.takeIf { it > 0 }?.toString(),
                    onBack = onBack,
                    onPlay = {
                        // 空のプレイリストでは再生を起たない。
                        if (mediaItems.isNotEmpty()) {
                            playerConnection?.playQueue(
                                ListQueue(title = title, items = mediaItems),
                            )
                        }
                    },
                )
            }

            itemsIndexed(items = songs, key = { _, song -> song.map.id }) { index, song ->
                SongRow(
                    song = song.song.toMediaMetadata(),
                    // Apple Music のプレイリストと同じく、左端に1始まりの通し番号を出す。
                    index = index + 1,
                    onClick = {
                        if (mediaItems.isNotEmpty()) {
                            playerConnection?.playQueue(
                                ListQueue(
                                    title = title,
                                    items = mediaItems,
                                    startIndex = index.coerceIn(mediaItems.indices),
                                ),
                            )
                        }
                    },
                )
            }

            if (songs.isEmpty()) {
                item(key = "empty") {
                    EmptyRow(message = stringResource(R.string.no_results_found))
                }
            }
        }
    }
}
