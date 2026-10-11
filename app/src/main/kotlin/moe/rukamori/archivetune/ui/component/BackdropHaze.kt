/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.runtime.staticCompositionLocalOf
import dev.chrisbanes.haze.HazeState

/**
 * 画面全体の「背後コンテンツ」を捕捉する [HazeState]。
 *
 * 浮遊ナビゲーションやミニプレイヤーのように、共通シェルに組み込まれていて
 * 呼び出し元までパラメータを辿れないコンポーネントがこれを読む。
 * ルート側（MainActivity）が [dev.chrisbanes.haze.rememberHazeState] を作って供給する。
 * 供給がない環境では null のままなので、側は半透明の塗りに落ちる。
 */
val LocalBackdropHazeState = staticCompositionLocalOf<HazeState?> { null }
