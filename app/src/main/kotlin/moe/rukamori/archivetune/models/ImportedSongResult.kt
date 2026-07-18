/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.models

import moe.rukamori.archivetune.db.entities.Song

enum class ImportSource {
    LOCAL,
    YOUTUBE,
    UNRESOLVED,
}

data class ImportedSongResult(
    val originalSong: Song,
    val resolvedId: String?,
    val resolvedSong: Song?,
    val source: ImportSource,
) {
    val isResolved: Boolean
        get() = source != ImportSource.UNRESOLVED && resolvedId != null && resolvedSong != null
}
