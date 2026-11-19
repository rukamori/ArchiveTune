/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.utils.AppUpdateInstaller
import moe.rukamori.archivetune.utils.Updater
import java.io.File
import javax.inject.Inject

data class AppUpdateRelease(
    val version: String,
    val notes: String?,
    val publishedAt: String,
    val downloadSizeBytes: Long?,
    val downloadUrl: String,
    val isUpdateAvailable: Boolean,
    val isMandatory: Boolean,
    val canInstallInApp: Boolean,
)

class AppUpdateUseCase @Inject constructor(
    private val repository: AppUpdateRepository,
) {
    suspend fun check(forceRefresh: Boolean): AppUpdateRelease? {
        check(BuildConfig.UPDATER_AVAILABLE && BuildConfig.DISTRIBUTION in setOf("gms", "foss")) {
            "Updater is unavailable for this distribution"
        }
        val releases = repository.releases(forceRefresh).filterNot { it.isDraft }
        val stableCandidates = releases.filter { !it.isPrerelease && it.publishedAt.isNotBlank() }
        val release = Updater.findLatestRelease(stableCandidates.ifEmpty { releases }) ?: return null
        val version = Updater.getReleaseVersionName(release)
        return AppUpdateRelease(
            version = version,
            notes = release.body,
            publishedAt = release.publishedAt,
            downloadSizeBytes = release.downloadSizeBytes,
            downloadUrl = Updater.getReleaseDownloadUrl(release),
            isUpdateAvailable = Updater.isUpdateAvailable(version, BuildConfig.VERSION_NAME),
            isMandatory = Updater.newerStableReleaseCount(releases, BuildConfig.VERSION_NAME) >= 2,
            canInstallInApp = BuildConfig.DISTRIBUTION == "gms",
        )
    }

    suspend fun download(release: AppUpdateRelease, onProgress: (AppUpdateInstaller.Progress) -> Unit): File {
        check(release.isUpdateAvailable && release.canInstallInApp) { "Release cannot be installed in app" }
        return repository.download(release.downloadUrl, onProgress)
    }
}
