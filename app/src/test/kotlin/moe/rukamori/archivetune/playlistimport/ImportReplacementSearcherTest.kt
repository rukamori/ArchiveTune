/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlistimport

import kotlinx.coroutines.runBlocking
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ImportReplacementSearcherTest {
    @Test
    fun localSearchRanksTitleAndArtistMatches() {
        val expected = song("One", "Metallica", id = "expected")
        val searcher = ImportReplacementSearcher(youtubeSearch = ImportYouTubeSongSearch { emptyList() })

        val results =
            searcher.searchLocal(
                query = "One - Metallica",
                localLibrary =
                    listOf(
                        song("One", "U2"),
                        song("One More Night", "Maroon 5"),
                        expected,
                    ),
            )

        assertEquals(expected, results.first())
    }

    @Test
    fun youtubeSearchReturnsDistinctLimitedResults() =
        runBlocking {
            val first = song("First", "Artist", id = "first")
            val duplicate = song("First duplicate", "Artist", id = "first")
            val second = song("Second", "Artist", id = "second")
            val searcher =
                ImportReplacementSearcher(
                    youtubeSearch = ImportYouTubeSongSearch { listOf(first, duplicate, second) },
                )

            val results = searcher.searchYouTube(query = "query", limit = 2)

            assertEquals(listOf(first, second), results)
        }

    private fun song(
        title: String,
        vararg artists: String,
        id: String = "song-$title",
    ): Song =
        Song(
            song = SongEntity(id = id, title = title),
            artists =
                artists.mapIndexed { index, artist ->
                    ArtistEntity(id = "$id-artist-$index", name = artist)
                },
        )
}
