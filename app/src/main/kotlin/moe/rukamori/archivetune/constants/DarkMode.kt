/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

/**
 * ダークテーマの適用方法。
 *
 * `name` がそのまま [DarkModeKey] に保存されるため、**定数名は変更しないこと**
 * （変更すると既存ユーザーの設定が既定値へ落ちる）。
 *
 * もとは `ui/screens/settings/AppearanceSettings.kt` に置いていたが、
 * そのファイルは旧UIの削除対象なので、UIに依存しない定数としてこちらへ移した。
 */
enum class DarkMode {
    ON,
    OFF,
    AUTO,
}
