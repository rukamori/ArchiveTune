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
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.DetailHero
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.LocalAlbumRadio
import moe.rukamori.archivetune.viewmodels.AlbumUiState
import moe.rukamori.archivetune.viewmodels.AlbumViewModel

/**
 * アルバム詳細。
 *
 * [AlbumViewModel] が init で取得を始め、失敗時は [AlbumViewModel.retry] で取り直す。
 * 再生は既存UIと同じく [LocalAlbumRadio] を使う（端末内の曲を先に並べ、尽きたら
 * YouTube Music から続きを補う）。楽曲一覧そのものは [AlbumWithSongs.songs] から読み、
 * 画面側で並べ替えたり作り直したりはしない。
 *
 * @param onBack 戻る矢印。
 * @param onOpenArtist アーティスト名を押したときの行き先。
 */
@Composable
fun AlbumScreen(
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AlbumViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val uiState by viewModel.uiState.collectAsState()
    val albumWithSongs by viewModel.albumWithSongs.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        when (uiState) {
            AlbumUiState.Loading -> LoadingState(Modifier.fillMaxSize())

            AlbumUiState.Empty ->
                EmptyState(
                    title = stringResource(R.string.albums),
                    message = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxSize(),
                )

            is AlbumUiState.Error ->
                ErrorState(
                    title = stringResource(R.string.albums),
                    message = stringResource(R.string.no_results_found),
                    onRetry = { viewModel.retry() },
                    modifier = Modifier.fillMaxSize(),
                )

            AlbumUiState.Success -> {
                val current = albumWithSongs
                if (current == null) {
                    LoadingState(Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = metrics.block.dp),
                    ) {
                        item(key = "hero") {
                            DetailHero(
                                title = current.album.title,
                                thumbnailUrl = current.songs.firstOrNull()?.thumbnailUrl,
                                subtitle = current.artists.joinToString(", ") { it.name },
                                onBack = onBack,
                                onPlay = {
                                    // ヘッダの再生は行位置に依存しない。[LocalAlbumRadio] は
                                    // 曲一覧そのものをキューにするため、ここでは先頭から始める。
                                    playerConnection?.playQueue(LocalAlbumRadio(current))
                                },
                            )
                        }

                        itemsIndexed(items = current.songs, key = { _, song -> song.id }) { index, song ->
                            SongRow(
                                song = song.toMediaMetadata(),
                                onClick = {
                                    // 行を押したときはその曲を先頭にして、同じ一覧を渡す。
                                    playerConnection?.playQueue(
                                        LocalAlbumRadio(current, startIndex = index),
                                    )
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}
