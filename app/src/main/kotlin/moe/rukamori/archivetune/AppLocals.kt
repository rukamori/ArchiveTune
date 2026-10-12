/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.playback.DownloadUtil
import moe.rukamori.archivetune.playback.PlayerConnection
import moe.rukamori.archivetune.utils.SyncUtils

/**
 * アプリ全体で共有するコンポーネントローカル。
 *
 * かつて [MainActivity] の末尾に宣言されていたものを、エントリポイントから分離して
 * 独立させた。パッケージは [MainActivity] と同一（`moe.rukamori.archivetune`）なので、
 * 既存コードの import 文は変更不要のまま動作する。
 *
 * これらは新旧どちらの UI からも再生エンジンへ接続する唯一の経路である。
 */

/** アプリの [MusicDatabase]。 */
val LocalDatabase = staticCompositionLocalOf<MusicDatabase> { error("No database provided") }

/** 再生サービスへ接続済みの [PlayerConnection]。 */
val LocalPlayerConnection =
    staticCompositionLocalOf<PlayerConnection?> { error("No PlayerConnection provided") }

/** ミニプレイヤーの高さを踏まえた画面インセット。 */
val LocalPlayerAwareWindowInsets =
    compositionLocalOf<WindowInsets> { error("No WindowInsets provided") }

/** ダウンロード進捗の観測ユーティリティ。 */
val LocalDownloadUtil = staticCompositionLocalOf<DownloadUtil> { error("No DownloadUtil provided") }

/** ライブラリの再同期ユーティリティ。 */
val LocalSyncUtils = staticCompositionLocalOf<SyncUtils> { error("No SyncUtils provided") }
