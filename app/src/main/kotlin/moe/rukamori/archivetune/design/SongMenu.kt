/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.design

import androidx.compose.runtime.staticCompositionLocalOf
import moe.rukamori.archivetune.models.MediaMetadata

/**
 * 曲行右端の「…」を押したときの合図。
 *
 * [moe.rukamori.archivetune.screens.AppShell] が供給し、
 * [moe.rukamori.archivetune.design.components.SongRow] は呼び出し側が
 * `onMoreClick` を渡していないときだけここを参照する。
 *
 * 供給されていない画面（`null`）では「…」自体を描画しない。
 * 個別に表示を切りたい画面は `onMoreClick` を明示的に渡す。
 */
val LocalOpenSongMenu = staticCompositionLocalOf<((MediaMetadata) -> Unit)? { null }
