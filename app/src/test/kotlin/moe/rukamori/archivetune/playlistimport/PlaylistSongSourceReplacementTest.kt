/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlistimport

import moe.rukamori.archivetune.db.entities.PlaylistSongMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class PlaylistSongSourceReplacementTest {
    @Test
    fun `replacement preserves playlist row and position`() {
        val original =
            PlaylistSongMap(
                id = 42,
                playlistId = "playlist",
                songId = "local-song",
                position = 7,
                setVideoId = "remote-entry",
            )

        val replacement = original.replaceSongSource("youtube-song")

        assertEquals(42, replacement.id)
        assertEquals("playlist", replacement.playlistId)
        assertEquals(7, replacement.position)
        assertEquals("youtube-song", replacement.songId)
        assertNull(replacement.setVideoId)
    }

    @Test
    fun `blank replacement id is rejected`() {
        val original =
            PlaylistSongMap(
                playlistId = "playlist",
                songId = "original",
            )

        assertThrows(IllegalArgumentException::class.java) {
            original.replaceSongSource(" ")
        }
    }
}
