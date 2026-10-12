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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.LargeTitleHeader
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.MediaCard
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.home.HomeAction
import moe.rukamori.archivetune.home.HomeEvent
import moe.rukamori.archivetune.home.HomeScreenState
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.HomePage
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.queues.YouTubeQueue
import moe.rukamori.archivetune.utils.isLocalMediaId
import moe.rukamori.archivetune.viewmodels.HomeViewModel

/**
 * Home タブ。
 *
 * データは [HomeViewModel] が init で自動取得するので、画面側の役割は
 * 4状態（読み込み／成功／空／エラー）の分岐と、成功時の並べ方だけ。
 *
 * 成功時は次の順に積む：見出し → カテゴリチップ → YouTube Music の各セクション
 * → クイックピック → アカウントのプレイリスト。
 *
 * @param navController 曲以外（アルバム・アーティスト等）の遷移に使う。
 */
@Composable
fun HomeScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = hiltViewModel()
    val playerConnection = LocalPlayerConnection.current
    val windowInsets = LocalPlayerAwareWindowInsets.current
    val palette = applePalette()
    val metrics = appleMetrics()

    val screenState by viewModel.screenState.collectAsState()

    // ポッドキャスト等、ViewModel から画面へ返るイベントを受け取る。
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is HomeEvent.OpenPodcast -> navController.navigate(Routes.browse(event.browseId))
                is HomeEvent.PlayPodcastEpisode -> Unit
            }
        }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        when (val state = screenState) {
            is HomeScreenState.Loading -> {
                LoadingState(Modifier.fillMaxSize())
            }

            is HomeScreenState.Empty -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitleHeader(title = stringResource(R.string.home))
                    EmptyState(
                        title = stringResource(R.string.home),
                        message = stringResource(R.string.no_results_found),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        actionLabel = stringResource(R.string.refresh),
                        onAction = { viewModel.onAction(HomeAction.Refresh) },
                    )
                }
            }

            is HomeScreenState.Error -> {
                Column(Modifier.fillMaxSize()) {
                    LargeTitleHeader(title = stringResource(R.string.home))
                    ErrorState(
                        title = stringResource(R.string.home),
                        message = stringResource(state.messageResId),
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        onRetry = { viewModel.onAction(HomeAction.Refresh) },
                    )
                }
            }

            is HomeScreenState.Success -> {
                val uiState = state.uiState

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = metrics.block.dp),
                ) {
                    item(key = "header") {
                        LargeTitleHeader(title = stringResource(R.string.home))
                    }

                    val chips = uiState.homePage?.chips.orEmpty()
                    if (uiState.showCategoryChips && chips.isNotEmpty()) {
                        item(key = "chips") {
                            ChipRow(
                                chips = chips,
                                selected = uiState.selectedChip,
                                onSelect = { viewModel.onAction(HomeAction.SelectChip(it)) },
                            )
                        }
                    }

                    uiState.homePage?.sections.orEmpty().forEach { section ->
                        if (section.items.isEmpty()) return@forEach
                        item(key = "section:${section.title}:${section.items.size}") {
                            HomeSection(
                                section = section,
                                onOpenItem = { item ->
                                    if (item is SongItem) {
                                        playerConnection?.playYtSong(item)
                                    } else {
                                        navController.openYtItem(item) { browseId ->
                                            navController.navigate(Routes.browse(browseId))
                                        }
                                    }
                                },
                                onPlayAll = { endpoint ->
                                    playerConnection?.playQueue(YouTubeQueue.playlist(endpoint))
                                },
                            )
                        }
                    }

                    if (uiState.quickPicks.isNotEmpty()) {
                        item(key = "quickPicks") {
                            SectionHeader(title = stringResource(R.string.quick_picks))
                            Column(Modifier.padding(horizontal = metrics.gutter.dp)) {
                                uiState.quickPicks.forEachIndexed { index, song ->
                                    SongRow(
                                        song = song.toMediaMetadata(),
                                        onClick = {
                                            playerConnection?.playLocalSongs(
                                                songs = uiState.quickPicks,
                                                startIndex = index,
                                            )
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (uiState.accountPlaylists.isNotEmpty()) {
                        item(key = "accountPlaylists") {
                            SectionHeader(title = stringResource(R.string.playlists))
                            LazyRow(
                                contentPadding =
                                    PaddingValues(horizontal = metrics.gutter.dp),
                            ) {
                                items(
                                    items = uiState.accountPlaylists,
                                    key = { it.id },
                                ) { playlist ->
                                    MediaCard(
                                        title = playlist.title,
                                        thumbnailUrl = playlist.thumbnail,
                                        subtitle = playlist.author?.name,
                                        onClick = {
                                            navController.navigate(Routes.onlinePlaylist(playlist.id))
                                        },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** YouTube Music の横並びセクション。 */
@Composable
private fun HomeSection(
    section: HomePage.Section,
    onOpenItem: (moe.rukamori.archivetune.innertube.models.YTItem) -> Unit,
    onPlayAll: (moe.rukamori.archivetune.innertube.models.WatchEndpoint) -> Unit,
) {
    val metrics = appleMetrics()

    // playEndpoint は core モジュールの公開 val なので、このモジュールから
    // スマートキャストできない。一旦ローカルへ落とさないとラムダ内でも
    // 「Nullチェックができない」コンパイルエラーになる。
    val playEndpoint = section.playEndpoint

    Column {
        SectionHeader(
            title = section.title,
            actionLabel =
                if (playEndpoint != null) {
                    stringResource(R.string.play_all)
                } else {
                    null
                },
            onAction =
                if (playEndpoint != null) {
                    { onPlayAll(playEndpoint) }
                } else {
                    null
                },
        )

        LazyRow(
            contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
            horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
        ) {
            items(
                items = section.items,
                key = { it.id },
                contentType = { it::class },
            ) { item ->
                MediaCard(
                    title = item.title,
                    thumbnailUrl = item.thumbnail,
                    subtitle = subtitleFor(item),
                    onClick = { onOpenItem(item) },
                )
            }
        }
    }
}

private fun subtitleFor(item: moe.rukamori.archivetune.innertube.models.YTItem): String? =
    when (item) {
        is SongItem -> item.artists.joinToString(", ") { it.name }
        is moe.rukamori.archivetune.innertube.models.AlbumItem ->
            item.artists?.joinToString(", ") { it.name }
        is moe.rukamori.archivetune.innertube.models.ArtistItem -> null
        is moe.rukamori.archivetune.innertube.models.PlaylistItem -> item.author?.name
        else -> null
    }

/** カテゴリチップ。選択中は塗り、未選択は枠のみ。 */
@Composable
private fun ChipRow(
    chips: List<HomePage.Chip>,
    selected: HomePage.Chip?,
    onSelect: (HomePage.Chip?) -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.xs.dp,
                ),
        horizontalArrangement = Arrangement.spacedBy(metrics.xs.dp),
    ) {
        chips.forEach { chip ->
            val isSelected = chip.title == selected?.title
            Text(
                text = chip.title,
                style = type.subhead.copy(fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium),
                color = if (isSelected) palette.onBackground else palette.labelSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier =
                    Modifier
                        .clip(RoundedCornerShape(metrics.roundCorner.dp))
                        .background(if (isSelected) palette.surfaceRaised else palette.surface)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) {
                            onSelect(if (isSelected) null else chip)
                        }
                        .padding(horizontal = metrics.md.dp, vertical = metrics.xs.dp + 2.dp),
            )
        }
    }
}
