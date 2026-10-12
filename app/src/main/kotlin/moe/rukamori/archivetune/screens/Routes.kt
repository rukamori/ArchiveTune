/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.screens

/**
 * 新UIの画面ルート。
 *
 * 引数名は必ず既存 ViewModel が `SavedStateHandle` で読むキーと一致させること
 * （[ArgAlbumId] / [ArgArtistId] / [ArgPlaylistId] / [ArgBrowseId] / [ArgEncodedQuery]）。
 * 違うと ViewModel 生成時に `!!` が落ちてクラッシュする。
 *
 * ルートの文字列自体は既存の `ui/screens/NavigationBuilder.kt` と揃えてあるので、
 * 既存のハンドラ（共有インテント、Android Auto 等）から渡される文字列も新UIで解決できる。
 */
object Routes {
    /** ボトムナビの4タブ。 */
    const val Home = "home"
    const val New = "new"
    const val Library = "library"
    const val Search = "search"

    /** 全画面プレイヤーまわり。 */
    const val Player = "player"
    const val Lyrics = "lyrics"
    const val Queue = "queue"

    const val Settings = "settings"

    /** 詳細画面。`{}` 内は `androidx.navigation.navArgument` の名前。 */
    const val Album = "album/{albumId}"
    const val Artist = "artist/{artistId}"
    const val OnlinePlaylist = "online_playlist/{playlistId}"
    const val LocalPlaylist = "local_playlist/{playlistId}"
    const val Browse = "browse/{browseId}"
    const val OnlineSearch = "search/{encodedQuery}"

    const val ArgAlbumId = "albumId"
    const val ArgArtistId = "artistId"
    const val ArgPlaylistId = "playlistId"
    const val ArgBrowseId = "browseId"
    const val ArgEncodedQuery = "encodedQuery"

    /** ボトムナビに載せるタブのルート順。 */
    val MainTabs = listOf(Home, New, Library, Search)
}
