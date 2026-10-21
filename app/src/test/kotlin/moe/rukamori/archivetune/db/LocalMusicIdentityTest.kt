/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LocalMusicIdentityTest {
    @Test
    fun trackKey_normalizesTitleArtistsAndArtistOrdering() {
        val first = LocalMusicIdentity.trackKey(" Song  Name ", listOf("Artist B", "Artist A"), 180, "first")
        val duplicate = LocalMusicIdentity.trackKey("song name", listOf("artist a", "artist b"), 180, "second")

        assertEquals(first, duplicate)
    }

    @Test
    fun trackKey_keepsDifferentDurationsSeparate() {
        val first = LocalMusicIdentity.trackKey("Song", listOf("Artist"), 180, "first")
        val alternate = LocalMusicIdentity.trackKey("Song", listOf("Artist"), 181, "alternate")

        assertNotEquals(first, alternate)
    }

    @Test
    fun trackKey_usesStableIdWhenMetadataIsIncomplete() {
        val first = LocalMusicIdentity.trackKey("Song", emptyList(), 180, "first")
        val second = LocalMusicIdentity.trackKey("Song", emptyList(), 180, "second")

        assertNotEquals(first, second)
    }

    @Test
    fun downloadIdFromExportedFilename_readsTaskIdSuffix() {
        assertEquals(
            "video-id_123",
            LocalMusicIdentity.downloadIdFromExportedFilename("Song - Artist [video-id_123].opus"),
        )
    }

    @Test
    fun downloadIdFromExportedFilename_ignoresFilesWithoutTaskIdSuffix() {
        assertEquals(null, LocalMusicIdentity.downloadIdFromExportedFilename("Song - Artist.opus"))
    }
}
