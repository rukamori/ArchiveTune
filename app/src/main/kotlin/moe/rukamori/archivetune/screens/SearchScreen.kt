/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.navigation.NavHostController
import moe.rukamori.archivetune.LocalPlayerAwareWindowInsets
import moe.rukamori.archivetune.LocalPlayerConnection
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.design.appleMetrics
import moe.rukamori.archivetune.design.applePalette
import moe.rukamori.archivetune.design.appleType
import moe.rukamori.archivetune.design.components.EmptyState
import moe.rukamori.archivetune.design.components.ErrorState
import moe.rukamori.archivetune.design.components.ListItemRow
import moe.rukamori.archivetune.design.components.LoadingState
import moe.rukamori.archivetune.design.components.MediaCard
import moe.rukamori.archivetune.design.components.SectionHeader
import moe.rukamori.archivetune.design.components.SongRow
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.screens.onlineSearchResultRoute
import moe.rukamori.archivetune.viewmodels.LocalFilter
import moe.rukamori.archivetune.viewmodels.LocalSearchViewModel
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryScreenState
import moe.rukamori.archivetune.viewmodels.SearchDiscoveryViewModel
import moe.rukamori.archivetune.db.entities.Artist as LocalArtist
import moe.rukamori.archivetune.db.entities.Album as LocalAlbum
import moe.rukamori.archivetune.db.entities.Playlist as LocalPlaylist

/**
 * Search タブ。
 *
 * 入力中は端末内の検索（[LocalSearchViewModel]）だけを行い、
 * クエリが無い間は検索ディスカバリー（[SearchDiscoveryViewModel]）を出す。
 * キーボードの「検索」を押したときだけ、オンライン検索結果の画面へ送る。
 *
 * オンライン検索は入力と同時に走らせない（リクエストが過剰になるため）。
 *
 * @param navController アルバム・アーティスト等の遷移、オンライン結果への送り込みに使う。
 */
@Composable
fun SearchScreen(
    navController: NavHostController,
    modifier: Modifier = Modifier,
) {
    val localSearchViewModel: LocalSearchViewModel = hiltViewModel()
    val discoveryViewModel: SearchDiscoveryViewModel = hiltViewModel()

    val windowInsets = LocalPlayerAwareWindowInsets.current
    val playerConnection = LocalPlayerConnection.current
    val palette = applePalette()
    val metrics = appleMetrics()
    val focusManager = LocalFocusManager.current

    val query by localSearchViewModel.query.collectAsState()
    val localResult by localSearchViewModel.result.collectAsState()
    val discoveryState by discoveryViewModel.state.collectAsState()

    fun submitSearch() {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        focusManager.clearFocus()
        navController.navigate(onlineSearchResultRoute(trimmed))
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(palette.background)
                .windowInsetsPadding(windowInsets),
    ) {
        SearchField(
            value = query,
            onValueChange = { localSearchViewModel.query.value = it },
            onSubmit = { submitSearch() },
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = metrics.block.dp),
        ) {
            if (query.isBlank()) {
                // ディスカバリーは項目数が決まっておらず、
                // 「見出し＋横スクロール」の繰り返しなので1つの item にまとめる。
                // item の中だけがコンポジションなので、そこで各部品を呼ぶ。
                item(key = "discovery") {
                    DiscoveryContent(
                        state = discoveryState,
                        onRetry = { discoveryViewModel.retry() },
                        onOpenSong = { item -> playerConnection?.playYtSong(item) },
                        onOpenAlbum = { id -> navController.navigate(Routes.album(id)) },
                        onOpenArtist = { id -> navController.navigate(Routes.artist(id)) },
                        onOpenBrowse = { id -> navController.navigate(Routes.browse(id)) },
                    )
                }
            } else {
                val orderedKeys =
                    listOf(
                        LocalFilter.SONG,
                        LocalFilter.ALBUM,
                        LocalFilter.ARTIST,
                        LocalFilter.PLAYLIST,
                    )

                // 絞り込み未指定時は ALL に全件が入る。そこにだけ見出しつけ、
                // 後続はフィルタ別に見出しを付ける。
                val allItems = localResult.map[LocalFilter.ALL].orEmpty()
                if (allItems.isNotEmpty()) {
                    items(items = allItems, key = { "all:${it.id}" }) { item ->
                        LocalItemRow(
                            item = item,
                            allSongs = allItems,
                            onPlay = { index ->
                                playerConnection?.playSongsStartingAt(allItems, index)
                            },
                            onOpen = { navController.openLocalItem(it) },
                        )
                    }
                } else {
                    orderedKeys.forEach { key ->
                        val entries = localResult.map[key].orEmpty()
                        if (entries.isEmpty()) return@forEach

                        item(key = "head:$key") {
                            SectionHeader(title = localFilterLabel(key))
                        }
                        items(items = entries, key = { "${key}:${it.id}" }) { item ->
                            LocalItemRow(
                                item = item,
                                allSongs = entries,
                                onPlay = { index ->
                                    playerConnection?.playSongsStartingAt(entries, index)
                                },
                                onOpen = { navController.openLocalItem(it) },
                            )
                        }
                    }
                }

                if (allItems.isEmpty() && orderedKeys.all { localResult.map[it].isNullOrEmpty() }) {
                    item(key = "empty") {
                        EmptyState(
                            title = stringResource(R.string.search),
                            message = stringResource(R.string.no_results_found),
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .padding(top = metrics.block.dp),
                        )
                    }
                }
            }
        }
    }
}

/** 検索文字列の受け皿。枠は丸く塗り、中身は自前の入力で Material の面を使わない。 */
@Composable
private fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    onSubmit: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = metrics.gutter.dp,
                    vertical = metrics.xs.dp,
                )
                .clip(RoundedCornerShape(metrics.roundCorner.dp))
                .background(palette.surface)
                .padding(horizontal = metrics.sm.dp, vertical = metrics.xs.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(R.drawable.search),
            contentDescription = null,
            tint = palette.labelSecondary,
            modifier = Modifier.size(18.dp),
        )

        Spacer(Modifier.width(metrics.xs.dp))

        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty()) {
                Text(
                    text = stringResource(R.string.search),
                    style = type.body,
                    color = palette.labelTertiary,
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = type.body.copy(color = palette.onBackground),
                cursorBrush = SolidColor(palette.accent),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            )
        }
    }
}

/** 端末内の1件を、曲なら再生行、それ以外は遷移行として描く。 */
@Composable
private fun LocalItemRow(
    item: LocalItem,
    allSongs: List<LocalItem>,
    onPlay: (Int) -> Unit,
    onOpen: (LocalItem) -> Unit,
) {
    when (item) {
        is Song -> {
            SongRow(
                song = item.toMediaMetadata(),
                onClick = { onPlay(allSongs.indexOfFirst { it.id == item.id }) },
            )
        }

        is LocalArtist -> {
            ListItemRow(
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                circularArtwork = true,
                onClick = { onOpen(item) },
            )
        }

        is LocalAlbum -> {
            ListItemRow(
                title = item.title,
                thumbnailUrl = item.thumbnailUrl,
                onClick = { onOpen(item) },
            )
        }

        is LocalPlaylist -> {
            ListItemRow(
                title = item.title,
                thumbnailUrl = item.thumbnails.firstOrNull(),
                onClick = { onOpen(item) },
            )
        }
    }
}

/** クエリが無い間に出すディスカバリー内容。1つの item として描かれる。 */
@Composable
private fun DiscoveryContent(
    state: SearchDiscoveryScreenState,
    onRetry: () -> Unit,
    onOpenSong: (SongItem) -> Unit,
    onOpenAlbum: (String) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenBrowse: (String) -> Unit,
) {
    val metrics = appleMetrics()

    when (state) {
        SearchDiscoveryScreenState.Loading -> {
            LoadingState(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.block.dp),
            )
        }

        is SearchDiscoveryScreenState.Error -> {
            ErrorState(
                title = stringResource(R.string.search),
                message = stringResource(state.messageResId),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.block.dp),
                onRetry = onRetry,
            )
        }

        SearchDiscoveryScreenState.Empty -> {
            EmptyState(
                title = stringResource(R.string.search),
                message = stringResource(R.string.no_results_found),
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(top = metrics.block.dp),
            )
        }

        is SearchDiscoveryScreenState.Success -> {
            val data = state.data

            if (data.moodAndGenres.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.mood_and_genres))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.xs.dp),
                ) {
                    items(items = data.moodAndGenres, key = { it.title }) { mood ->
                        MoodTile(
                            title = mood.title,
                            stripeColor = mood.stripeColor,
                            onClick = { onOpenBrowse(mood.endpoint.browseId) },
                        )
                    }
                }
            }

            if (data.suggestedSongs.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.suggestions))
                Column(Modifier.padding(horizontal = metrics.gutter.dp)) {
                    data.suggestedSongs.forEach { song ->
                        SongRow(
                            song = song.toMediaMetadata(),
                            onClick = { onOpenSong(song) },
                        )
                    }
                }
            }

            if (data.trendingAlbums.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.trending))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = data.trendingAlbums, key = { it.id }) { album ->
                        MediaCard(
                            title = album.title,
                            thumbnailUrl = album.thumbnail,
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                }
            }

            if (data.suggestedArtists.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.artists))
                LazyRow(
                    contentPadding = PaddingValues(horizontal = metrics.gutter.dp),
                    horizontalArrangement = Arrangement.spacedBy(metrics.sm.dp),
                ) {
                    items(items = data.suggestedArtists, key = { it.id }) { artist ->
                        MediaCard(
                            title = artist.title,
                            thumbnailUrl = artist.thumbnail,
                            onClick = { onOpenArtist(artist.id) },
                        )
                    }
                }
            }
        }
    }
}

/** 色帯だけのジャンルタイル。アートが無いので色そのものを面にする。 */
@Composable
private fun MoodTile(
    title: String,
    stripeColor: Long,
    onClick: () -> Unit,
) {
    val palette = applePalette()
    val type = appleType()
    val metrics = appleMetrics()

    Box(
        modifier =
            Modifier
                .width(132.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(metrics.cardCorner.dp))
                .background(androidx.compose.ui.graphics.Color(stripeColor))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                ) { onClick() }
                .padding(metrics.sm.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        Text(
            text = title,
            style = type.subhead.copy(fontWeight = FontWeight.SemiBold),
            color = androidx.compose.ui.graphics.Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
        )
    }
}

/** 端末内検索のカテゴリ見出し。 */
@Composable
private fun localFilterLabel(filter: LocalFilter): String =
    when (filter) {
        LocalFilter.SONG -> stringResource(R.string.songs)
        LocalFilter.ALBUM -> stringResource(R.string.albums)
        LocalFilter.ARTIST -> stringResource(R.string.artists)
        LocalFilter.PLAYLIST -> stringResource(R.string.playlists)
        LocalFilter.ALL -> stringResource(R.string.search)
    }

/** 1件を再生キューの先頭にして始める。実体は ItemActions.kt の playLocalSongs。 */
private fun moe.rukamori.archivetune.playback.PlayerConnection.playSongsStartingAt(
    items: List<LocalItem>,
    index: Int,
) {
    val songs = items.filterIsInstance<Song>()
    val target = songs.indexOfFirst { it.id == items.getOrNull(index)?.id }
    playLocalSongs(songs, if (target < 0) 0 else target)
}
