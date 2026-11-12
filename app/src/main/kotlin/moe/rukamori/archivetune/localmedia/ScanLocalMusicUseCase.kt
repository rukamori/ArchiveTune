/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.net.Uri
import javax.inject.Inject

class ScanLocalMusicUseCase @Inject constructor(
    private val scanner: LocalSongScanner,
    private val foldersRepository: LocalMusicFoldersRepository,
) {
    val configuration = foldersRepository.configuration

    suspend fun currentConfiguration(): LocalSongScanConfig = foldersRepository.currentConfiguration()

    suspend fun readAccess(): LocalMusicAccess = foldersRepository.readAccess()

    suspend fun includeFolder(uri: Uri): LocalSongScanConfig = foldersRepository.addIncludedFolder(uri)

    suspend fun excludeFolder(uri: Uri): LocalSongScanConfig = foldersRepository.addExcludedFolder(uri)

    suspend fun removeFolder(entry: String, included: Boolean): LocalSongScanConfig =
        foldersRepository.removeFolder(entry, included)

    suspend fun setMinimumDuration(seconds: Int): LocalSongScanConfig =
        foldersRepository.setMinimumDuration(seconds.coerceIn(0, MaximumMinimumDurationSeconds))

    suspend fun setAutoScan(enabled: Boolean): LocalSongScanConfig = foldersRepository.setAutoScan(enabled)

    suspend operator fun invoke(config: LocalSongScanConfig): LocalSongScanSummary =
        scanner.scanDevice(
            config.copy(
                minimumDurationSeconds = config.sanitizedMinimumDurationSeconds,
                includedFolders = config.sanitizedIncludedFolders,
                excludedFolders = config.sanitizedExcludedFolders,
            ),
        )

    companion object {
        const val MaximumMinimumDurationSeconds = 180
    }
}
