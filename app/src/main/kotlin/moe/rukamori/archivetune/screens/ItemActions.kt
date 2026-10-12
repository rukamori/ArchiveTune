/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

import androidx.navigation.NavHostController
import moe.rukamori.archivetune.db.entities.Album
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.LocalItem
import moe.rukamori.archivetune.db.entities.Playlist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.EpisodeItem
import moe.rukamori.archivetune.innertube.models.PlaylistItem
import moe.rukamori.archivetune.innertube.models.PodcastItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.models.YTItem
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.playback.queues.ListQueue
import moe.rukamori.archivetune.playback.queues.YouTubeQueue

/**
 * 一覧画面から1件を再生する。
 *
 * 既存UIが採っているのと同じ組み合わせに揃えている
 * （`ui/screens/search/SearchScreen.kt:531`、`HomeScreenComponents.kt:2102`）。
 * endpoint が無い曲は videoId から組み立て、再生リストは拡張しない。
 *
 * 曲以外（アルバム・アーティスト等）は遷移するだけなのでここでは触らない。
 */
internal fun PlayerConnection.playYtSong(item: SongItem) {
    playQueue(
        YouTubeQueue(
            endpoint = item.endpoint ?: WatchEndpoint(videoId = item.id),
            preloadItem = item.toMediaMetadata(),
        ),
    )
}

/**
 * アートワークからラジオを始める。
 *
 * 放送局カタログは存在しないため、シード曲からの生成
 * （`YouTubeQueue.radio`）でラジオを提供する。
 */
internal fun PlayerConnection.playRadioFrom(song: MediaMetadata) {
    playQueue(YouTubeQueue.radio(song))
}

/**
 * 端末内の曲をまとめて再生する。
 *
 * オンライン曲のラジオ生成とは路線が違うので、必ずこちらを使う。
 * 曲が1件も無ければ何もしない（空キューでプレイヤーを起きたらない）。
 */
internal fun PlayerConnection.playLocalSongs(
    songs: List<Song>,
    startIndex: Int = 0,
) {
    val items = songs.map { it.toMediaItem() }
    if (items.isEmpty()) return
    playQueue(
        ListQueue(
            items = items,
            startIndex = startIndex.coerceIn(items.indices),
        ),
    )
}

/** YouTube Music 由来の1件が持つ遷移先へ送る。 */
internal fun NavHostController.openYtItem(
    item: YTItem,
    onOpenRemoteItem: (String) -> Unit,
) {
    when (item) {
        is AlbumItem -> navigate(Routes.album(item.id))
        is ArtistItem -> navigate(Routes.artist(item.id))
        is PlaylistItem -> navigate(Routes.onlinePlaylist(item.id))
        is PodcastItem, is EpisodeItem -> onOpenRemoteItem(item.id)
        // 曲は再生される側なので、ここでは移動しない。
        else -> Unit
    }
}

/** 端末内データ1件の遷移先へ送る。 */
internal fun NavHostController.openLocalItem(item: LocalItem) {
    when (item) {
        is Album -> navigate(Routes.album(item.id))
        is Artist -> navigate(Routes.artist(item.id))
        is Playlist -> navigate(Routes.localPlaylist(item.id))
        // 曲は再生される側。
        else -> Unit
    }
}

/**
 * 一覧に並ぶ1件を、その型に応じて再生か遷移かへ振り分ける。
 *
 * @param onOpenRemoteItem ポッドキャスト等、専用ハンドラで受ける項目用。
 */
internal fun NavHostController.handleYtItemClick(
    item: YTItem,
    playerConnection: PlayerConnection,
    onOpenRemoteItem: (String) -> Unit,
) {
    if (item is SongItem) {
        playerConnection.playYtSong(item)
    } else {
        openYtItem(item, onOpenRemoteItem)
    }
}
