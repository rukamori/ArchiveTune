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
import androidx.compose.material3.Text
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
import moe.rukamori.archivetune.browse.BrowseAction
import moe.rukamori.archivetune.browse.BrowseScreenState
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.ListItemRow
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.viewmodels.BrowseViewModel

/**
 * 「ジャンルから探す」「ランキング」等の一覧（[BrowseViewModel] が取得するページ）。
 *
 * 項目は型が混在する（曲・アルバム・アーティスト・プレイリスト・ポッドキャスト）。
 * 曲だけはそのまま再生できるので、それ以外は専用画面へ送る。
 *
 * ポッドキャストとエピソードは新UIに専用画面を持たないため、ここでは再生だけ扱い、
 * それ以外は対応外として何もしない（存在しない画面へ送るとクラッシュするため）。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun BrowseScreen(
    onBack: () -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenPlaylist: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: BrowseViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    val screenState by viewModel.screenState.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        when (val state = screenState) {
            BrowseScreenState.Loading -> LoadingState(Modifier.fillMaxSize())

            is BrowseScreenState.Empty ->
                EmptyState(
                    title = state.title ?: stringResource(R.string.explore),
                    message = stringResource(R.string.no_results_found),
                    modifier = Modifier.fillMaxSize(),
                )

            is BrowseScreenState.Error ->
                ErrorState(
                    title = stringResource(R.string.explore),
                    message = stringResource(state.messageResId),
                    onRetry = { viewModel.onAction(BrowseAction.Retry) },
                    modifier = Modifier.fillMaxSize(),
                )

            is BrowseScreenState.Success -> {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = metrics.block.dp),
                ) {
                    item(key = "head") {
                        Column(Modifier.fillMaxWidth()) {
                            BackButton(onClick = onBack)
                            Text(
                                text = state.uiState.title ?: stringResource(R.string.explore),
                                style = type.title1,
                                color = palette.onBackground,
                                maxLines = 2,
                                modifier = Modifier.padding(horizontal = metrics.gutter.dp),
                            )
                        }
                    }

                    items(items = state.uiState.items, key = { it.id }) { item ->
                        when (item) {
                            is SongItem ->
                                SongRow(
                                    song = item.toMediaMetadata(),
                                    onClick = { playerConnection?.playYtSong(item) },
                                )

                            is AlbumItem ->
                                ListItemRow(
                                    title = item.title,
                                    thumbnailUrl = item.thumbnail,
                                    onClick = { onOpenAlbum(item.id) },
                                )

                            is ArtistItem ->
                                ListItemRow(
                                    title = item.title,
                                    thumbnailUrl = item.thumbnail,
                                    circularArtwork = true,
                                    onClick = { onOpenArtist(item.id) },
                                )

                            is PlaylistItem ->
                                ListItemRow(
                                    title = item.title,
                                    subtitle = item.author?.name,
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
