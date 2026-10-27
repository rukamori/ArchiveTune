/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import moe.rukamori.archivetune.constants.PlaylistSongSortType
import moe.rukamori.archivetune.db.entities.AlbumEntity
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.text.Collator
import java.util.Locale

class PlaylistSongSortTest {
    @Test
    fun sortSongsByArtistDoesNotGroupByAlbum() {
        val collator = Collator.getInstance(Locale.getDefault())
        collator.strength = Collator.PRIMARY

        val songZoe =
            PlaylistSong(
                map = PlaylistSongMap(id = 1, playlistId = "p1", songId = "s1"),
                song =
                    Song(
                        song = SongEntity(id = "s1", title = "Track 1"),
                        artists = listOf(ArtistEntity(id = "a1", name = "Zoe")),
                        album = AlbumEntity(id = "alb1", title = "AAA Album"),
                    ),
            )
        val songAlice =
            PlaylistSong(
                map = PlaylistSongMap(id = 2, playlistId = "p1", songId = "s2"),
                song =
                    Song(
                        song = SongEntity(id = "s2", title = "Track 2"),
                        artists = listOf(ArtistEntity(id = "a2", name = "Alice")),
                        album = AlbumEntity(id = "alb2", title = "ZZZ Album"),
                    ),
            )

        val songs = listOf(songZoe, songAlice)

        val sorted =
            songs.sortedWith(
                compareBy(collator) { song ->
                    song.song.artists.joinToString("") { artist -> artist.name }
                },
            )

        assertEquals("Alice", sorted[0].song.artists.first().name)
        assertEquals("Zoe", sorted[1].song.artists.first().name)
    }
}
