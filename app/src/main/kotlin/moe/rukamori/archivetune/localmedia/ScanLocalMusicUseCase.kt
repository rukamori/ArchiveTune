/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import javax.inject.Inject

class ScanLocalMusicUseCase @Inject constructor(
    private val scanner: LocalSongScanner,
) {
    suspend operator fun invoke(config: LocalSongScanConfig): LocalSongScanSummary =
        scanner.scanDevice(
            config.copy(
                minimumDurationSeconds = config.sanitizedMinimumDurationSeconds,
                includedFolders = config.sanitizedIncludedFolders,
                excludedFolders = config.sanitizedExcludedFolders,
            ),
        )
}
