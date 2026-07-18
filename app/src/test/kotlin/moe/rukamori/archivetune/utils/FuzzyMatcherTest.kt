/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    @Test
    fun normalizeRemovesDiacriticsPunctuationAndExtraWhitespace() {
        assertEquals(
            "ac dc feat beyonce",
            FuzzyMatcher.normalize("  AC/DC feat. Beyoncé!  "),
        )
    }

    @Test
    fun jaroWinklerReturnsKnownSimilarity() {
        assertEquals(
            0.9611,
            FuzzyMatcher.jaroWinkler("MARTHA", "MARHTA"),
            0.0001,
        )
    }

    @Test
    fun jaroWinklerNormalizesValuesBeforeComparing() {
        assertEquals(
            1.0,
            FuzzyMatcher.jaroWinkler("Beyoncé!", "beyonce"),
            0.0,
        )
    }

    @Test
    fun songScoreAcceptsMinorTitleTypoWithMatchingArtist() {
        val score =
            FuzzyMatcher.songScore(
                candidate = song("Smells Like Teen Spirit", "Nirvana"),
                title = "Smells Like Teen Sprit",
                artists = listOf("Nirvana"),
            )

        assertTrue(FuzzyMatcher.isMatch(score))
    }

    @Test
    fun songScoreRejectsExactTitleWithDifferentArtist() {
        val score =
            FuzzyMatcher.songScore(
                candidate = song("Hello", "Adele"),
                title = "Hello",
                artists = listOf("Lionel Richie"),
            )

        assertFalse(FuzzyMatcher.isMatch(score))
    }

    @Test
    fun songScoreMatchesCombinedAndSeparateArtistNames() {
        val score =
            FuzzyMatcher.songScore(
                candidate = song("The Sound of Silence", "Simon", "Garfunkel"),
                title = "The Sound of Silence",
                artists = listOf("Simon & Garfunkel"),
            )

        assertEquals(1.0, score, 0.0)
    }

    @Test
    fun songScoreUsesTitleOnlyWhenArtistMetadataIsMissing() {
        val score =
            FuzzyMatcher.songScore(
                candidate = song("Clair de Lune"),
                title = "Clair de Lune",
                artists = emptyList(),
            )

        assertTrue(FuzzyMatcher.isMatch(score))
    }

    @Test
    fun bestSongMatchSelectsCandidateUsingTitleAndArtist() {
        val expected = song("One", "Metallica")
        val candidates =
            listOf(
                song("One", "U2"),
                expected,
                song("One More Night", "Maroon 5"),
            )

        val result =
            FuzzyMatcher.bestSongMatch(
                candidates = candidates,
                title = "One",
                artists = listOf("Metallica"),
            )

        assertEquals(expected, result)
    }

    @Test
    fun bestSongMatchReturnsNullWhenNoCandidatePassesThreshold() {
        val result =
            FuzzyMatcher.bestSongMatch(
                candidates = listOf(song("Yellow", "Coldplay")),
                title = "Purple Rain",
                artists = listOf("Prince"),
            )

        assertEquals(null, result)
    }

    @Test
    fun isMatchUsesInclusiveDefaultThreshold() {
        assertTrue(FuzzyMatcher.isMatch(FuzzyMatcher.DEFAULT_THRESHOLD))
        assertFalse(FuzzyMatcher.isMatch(FuzzyMatcher.DEFAULT_THRESHOLD - 0.0001))
    }

    private fun song(
        title: String,
        vararg artists: String,
    ): Song =
        Song(
            song = SongEntity(id = "song-$title", title = title),
            artists =
                artists.mapIndexed { index, artist ->
                    ArtistEntity(id = "artist-$index", name = artist)
                },
        )
}
