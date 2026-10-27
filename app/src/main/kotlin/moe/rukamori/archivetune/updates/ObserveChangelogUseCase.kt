/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.utils.Updater
import java.time.LocalDate
import java.time.format.DateTimeParseException
import javax.inject.Inject

data class ChangelogRelease(
    val tag: String,
    val version: String,
    val title: String,
    val publishedDate: LocalDate?,
    val notes: String?,
)

class ObserveChangelogUseCase @Inject constructor(
    private val repository: ChangelogRepository,
) {
    operator fun invoke(): Flow<List<ChangelogRelease>> =
        repository.observeReleases().map { releases ->
            releases.map { release ->
                ChangelogRelease(
                    tag = release.tagName,
                    version = Updater.getReleaseVersionName(release),
                    title = release.name.ifBlank { release.tagName },
                    publishedDate =
                        try {
                            LocalDate.parse(release.publishedAt.take(10))
                        } catch (_: DateTimeParseException) {
                            null
                        },
                    notes = release.body?.takeIf { it.isNotBlank() },
                )
            }
        }
}
