/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "library_song_artist_map",
    value = """
        SELECT songId, artistId FROM song_artist_map
        UNION
        SELECT song.id AS songId, album_artist_map.artistId
        FROM song JOIN album_artist_map ON song.albumId = album_artist_map.albumId
        WHERE song.isLocal = 1
    """,
)
data class LibrarySongArtistMap(
    val songId: String,
    val artistId: String,
)
