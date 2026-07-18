/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlistimport

import moe.rukamori.archivetune.db.entities.PlaylistSongMap

internal fun PlaylistSongMap.replaceSongSource(replacementSongId: String): PlaylistSongMap {
    require(replacementSongId.isNotBlank()) { "Replacement song ID must not be blank" }
    return copy(
        songId = replacementSongId,
        setVideoId = null,
    )
}
