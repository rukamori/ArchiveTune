/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import org.junit.Assert.assertEquals
import org.junit.Test

class ScrobbleMetadataTest {
    @Test
    fun normalizeLeavesCleanTrackUntouched() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "RATHER LIE",
                artists = listOf("Playboi Carti", "The Weeknd"),
            )
        assertEquals("RATHER LIE", track)
        assertEquals("Playboi Carti, The Weeknd", artist)
    }

    @Test
    fun normalizeExtractsWithArtistFromParentheses() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "RATHER LIE (with The Weeknd)",
                artists = listOf("Playboi Carti"),
            )
        assertEquals("RATHER LIE", track)
        assertEquals("Playboi Carti, The Weeknd", artist)
    }

    @Test
    fun normalizeExtractsWithArtistFromSquareBrackets() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "RATHER LIE [with The Weeknd]",
                artists = listOf("Playboi Carti"),
            )
        assertEquals("RATHER LIE", track)
        assertEquals("Playboi Carti, The Weeknd", artist)
    }

    @Test
    fun normalizeExtractsFeatArtistFromParentheses() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "First Person Shooter (feat. J. Cole)",
                artists = listOf("Drake"),
            )
        assertEquals("First Person Shooter", track)
        assertEquals("Drake, J. Cole", artist)
    }

    @Test
    fun normalizeExtractsMultipleFeaturedArtists() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "Creepin' (with The Weeknd & 21 Savage)",
                artists = listOf("Metro Boomin"),
            )
        assertEquals("Creepin'", track)
        assertEquals("Metro Boomin, The Weeknd, 21 Savage", artist)
    }

    @Test
    fun normalizeDeduplicatesExistingFeaturedArtist() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "RATHER LIE (with The Weeknd)",
                artists = listOf("Playboi Carti", "The Weeknd"),
            )
        assertEquals("RATHER LIE", track)
        assertEquals("Playboi Carti, The Weeknd", artist)
    }

    @Test
    fun normalizeExtractsSuffixFeat() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "Forever - feat. Kanye West, Lil Wayne & Eminem",
                artists = listOf("Drake"),
            )
        assertEquals("Forever", track)
        assertEquals("Drake, Kanye West, Lil Wayne, Eminem", artist)
    }

    @Test
    fun normalizeDoesNotStripBareWordWithInTitle() {
        val (track, artist) =
            normalizeScrobbleMetadata(
                title = "Stay With Me",
                artists = listOf("Sam Smith"),
            )
        assertEquals("Stay With Me", track)
        assertEquals("Sam Smith", artist)
    }
}
