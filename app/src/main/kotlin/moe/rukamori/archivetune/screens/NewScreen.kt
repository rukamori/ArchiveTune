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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.MediaCard
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.viewmodels.NewReleaseViewModel

/**
 * New タブ（新着リリース）。
 *
 * [NewReleaseViewModel] が init で自動ロードし、`retry()` で取り直す。
 * アルバム・シングル・EP の3つを同じ部品 [ReleaseSection] で並べる。
 *
 * アルバム以外は題名の違いだけで構造が同じなので、別コンポーネントにせず
 * ラベルだけ差し替えている。
 *
 * @param navController 曲以外はアルバム詳細へ送る。
 */
@Composable
fun NewScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val viewModel: NewReleaseViewModel = hiltViewModel()
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        when (val state = uiState) {
            is moe.rukamori.archivetune.viewmodels.NewReleaseUiState.Loading -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitleHeader(title = stringResource(R.string.new_releases))
                    LoadingState(Modifier.fillMaxWidth().weight(1f))
                }
            }

            is moe.rukamori.archivetune.viewmodels.NewReleaseUiState.Error -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitleHeader(title = stringResource(R.string.new_releases))
                    ErrorState(
                        title = stringResource(R.string.new_releases),
                        message = stringResource(R.string.no_results_found),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        onRetry = { viewModel.retry() },
                    )
                }
            }

            is moe.rukamori.archivetune.viewmodels.NewReleaseUiState.Empty -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitleHeader(title = stringResource(R.string.new_releases))
                    EmptyState(
                        title = stringResource(R.string.new_releases),
                        message = stringResource(R.string.no_results_found),
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        actionLabel = stringResource(R.string.refresh),
                        onAction = { viewModel.retry() },
                    )
                }
            }

            is moe.rukamori.archivetune.viewmodels.NewReleaseUiState.Success -> {
                val content = state.content
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = metrics.block.dp),
                ) {
                    item(key = "header") {
                        LargeTitleHeader(title = stringResource(R.string.new_releases))
                    }

                    item(key = "albums") {
                        ReleaseSection(
                            label = stringResource(R.string.albums),
                            releases = content.albums,
                            onOpen = { navController.navigate(Routes.album(it.id)) },
                        )
                    }
                    item(key = "singles") {
                        ReleaseSection(
                            label = stringResource(R.string.singles),
                            releases = content.singles,
                            onOpen = { navController.navigate(Routes.album(it.id)) },
                        )
                    }
                    item(key = "eps") {
                        ReleaseSection(
                            label = stringResource(R.string.ep),
                            releases = content.eps,
                            onOpen = { navController.navigate(Routes.album(it.id)) },
                        )
                    }
                }
            }
        }
    }
}

/** 新着1つの縦ブロック（見出し＋横スクロール）。空なら描かない。 */
@Composable
private fun ReleaseSection(
    label: String,
    releases: List<AlbumItem>,
    onOpen: (AlbumItem) -> Unit,
) {
    val metrics = appleMetrics()
    if (releases.isEmpty()) return

    Column {
        SectionHeader(title = label)
        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
            horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
        ) {
            items(items = releases, key = { it.id }) { album ->
                MediaCard(
                    title = album.title,
                    thumbnailUrl = album.thumbnail,
                    subtitle =
                        album.artists
                            ?.joinToString(", ") { it.name }
                            ?: album.year?.toString(),
                    onClick = { onOpen(album) },
                )
            }
        }
    }
}
