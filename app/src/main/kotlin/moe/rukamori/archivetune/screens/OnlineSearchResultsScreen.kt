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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.compose.foundation.layout.windowInsetsPadding
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.BackButton
import moe.rukamori.archivetune.design.components.EmptyRow
import moe.rukamori.archivetune.design.components.ListItemRow
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.viewmodels.OnlineSearchViewModel

/**
 * オンライン検索の結果画面。
 *
 * クエリはルート引数から渡り、[OnlineSearchViewModel] が `SavedStateHandle` で読む。
 * 画面は絞り込みを1つだけ持ち、選んだ絞り込みの結果を描く。
 *
 * 初回は「曲」に固定する。[OnlineSearchViewModel] は絞り込みを `null` にすると
 * 7種すべてを同時に走らせるため、起動直後はその形を避ける。
 *
 * 「さらに読み込む」は一覧の末尾に到達したときだけ ViewModelへ通知する。
 *
 * @param onBack 戻る矢印。
 */
@Composable
fun OnlineSearchResultsScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnlineSearchViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    val selectedFilter by viewModel.filter.collectAsState()

    // SnapshotStateMap を読むことで、ViewModel が書き換えた瞬間に再描画される。
    val page = viewModel.viewStateMap[selectedFilter?.value.orEmpty()]

    LaunchedEffect(Unit) {
        if (viewModel.filter.value == null) {
            viewModel.filter.value = YouTube.SearchFilter.FILTER_SONG
        }
    }

    val filterOptions =
        remember {
            listOf(
                YouTube.SearchFilter.FILTER_SONG to R.string.songs,
                YouTube.SearchFilter.FILTER_ALBUM to R.string.albums,
                YouTube.SearchFilter.FILTER_ARTIST to R.string.artists,
                YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST to R.string.playlists,
            )
        }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        BackButton(onClick = onBack)

        Text(
            text = viewModel.query,
            style = type.title1,
            color = palette.onBackground,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = metrics.gutter.dp),
        )

        androidx.compose.foundation.layout.Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(
                        horizontal = metrics.gutter.dp,
                        vertical = metrics.sm.dp,
                    ),
        ) {
            filterOptions.forEach { (option, labelId) ->
                val isSelected = option.value == selectedFilter?.value
                Text(
                    text = stringResource(labelId),
                    style =
                        type.subhead.copy(
                            fontWeight =
                                if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                        ),
                    color = if (isSelected) palette.onBackground else palette.labelSecondary,
                    maxLines = 1,
                    modifier =
                        Modifier
                            .padding(end = metrics.md.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.filter.value = option },
                )
            }
        }

        val items = page?.items

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.block.dp),
        ) {
            if (items == null) {
                item(key = "loading") { LoadingState(Modifier.fillMaxSize()) }
                return@LazyColumn
            }

            if (items.isEmpty()) {
                item(key = "empty") {
                    EmptyRow(message = stringResource(R.string.no_results_found))
                }
                return@LazyColumn
            }

            items(items = items, key = { it.id }) { item ->
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
                            onClick = { Unit },
                        )

                    is ArtistItem ->
                        ListItemRow(
                            title = item.title,
                            thumbnailUrl = item.thumbnail,
                            circularArtwork = true,
                            onClick = { Unit },
                        )

                    is PlaylistItem ->
                        ListItemRow(
                            title = item.title,
                            subtitle = item.author?.name,
                            thumbnailUrl = item.thumbnail,
                            onClick = { Unit },
                        )

                    else -> Unit
                }
            }

            // 末尾に到達したら続きを要求する。ページが尽きていれば ViewModel側で無視される。
            if (page?.continuation != null) {
                item(key = "more") {
                    androidx.compose.foundation.layout.Spacer(
                        Modifier
                            .fillMaxWidth()
                            .padding(vertical = metrics.md.dp)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                            ) { viewModel.loadMore() },
                    )
                }
            }
        }
    }
}
