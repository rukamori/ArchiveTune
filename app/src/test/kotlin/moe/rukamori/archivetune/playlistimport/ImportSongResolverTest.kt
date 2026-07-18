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
import moe.rukamori.archivetune.models.ImportSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class ImportSongResolverTest {
    @Test
    fun localFirstReturnsLocalMatchWithoutSearchingYouTube() =
        runBlocking {
            var youtubeSearchCount = 0
            val localSong = song("Smells Like Teen Spirit", "Nirvana", id = "local")
            val resolver =
                ImportSongResolver(
                    youtubeSearch =
                        ImportYouTubeSongSearch {
                            youtubeSearchCount++
                            emptyList()
                        },
                )

            val result =
                resolver.resolve(
                    songs = listOf(song("Smells Like Teen Sprit", "Nirvana")),
                    localLibrary = listOf(localSong),
                    localFirst = true,
                ).single()

            assertEquals(ImportSource.LOCAL, result.source)
            assertSame(localSong, result.resolvedSong)
            assertEquals(0, youtubeSearchCount)
        }

    @Test
    fun localFirstFallsBackToYouTubeWhenLocalMatchIsMissing() =
        runBlocking {
            val youtubeSong = song("Purple Rain", "Prince", id = "youtube")
            val resolver = ImportSongResolver(youtubeSearch = ImportYouTubeSongSearch { listOf(youtubeSong) })

            val result =
                resolver.resolve(
                    songs = listOf(song("Purple Rain", "Prince")),
                    localLibrary = listOf(song("Yellow", "Coldplay")),
                    localFirst = true,
                ).single()

            assertEquals(ImportSource.YOUTUBE, result.source)
            assertSame(youtubeSong, result.resolvedSong)
        }

    @Test
    fun youtubeOnlySkipsAnExactLocalMatch() =
        runBlocking {
            val localSong = song("One", "Metallica", id = "local")
            val youtubeSong = song("One", "Metallica", id = "youtube")
            val resolver = ImportSongResolver(youtubeSearch = ImportYouTubeSongSearch { listOf(youtubeSong) })

            val result =
                resolver.resolve(
                    songs = listOf(song("One", "Metallica")),
                    localLibrary = listOf(localSong),
                    localFirst = false,
                ).single()

            assertEquals(ImportSource.YOUTUBE, result.source)
            assertSame(youtubeSong, result.resolvedSong)
        }

    @Test
    fun youtubeFallbackSelectsBestValidatedCandidateInsteadOfFirstResult() =
        runBlocking {
            val misleading = song("One More Night", "Maroon 5", id = "misleading")
            val expected = song("One", "Metallica", id = "expected")
            val resolver =
                ImportSongResolver(
                    youtubeSearch = ImportYouTubeSongSearch { listOf(misleading, expected) },
                )

            val result =
                resolver.resolve(
                    songs = listOf(song("One", "Metallica")),
                    localLibrary = emptyList(),
                    localFirst = false,
                ).single()

            assertEquals(ImportSource.YOUTUBE, result.source)
            assertSame(expected, result.resolvedSong)
        }

    @Test
    fun youtubeFallbackRejectsCandidateBelowAutomaticThreshold() =
        runBlocking {
            val resolver =
                ImportSongResolver(
                    youtubeSearch =
                        ImportYouTubeSongSearch {
                            listOf(song("Never Gonna Give You Up", "Rick Astley", id = "misleading"))
                        },
                )

            val result =
                resolver.resolve(
                    songs = listOf(song("Never Gonna Exist Tonight", "Imaginary Artist")),
                    localLibrary = emptyList(),
                    localFirst = false,
                ).single()

            assertEquals(ImportSource.UNRESOLVED, result.source)
            assertEquals(null, result.resolvedSong)
        }

    @Test
    fun missingYouTubeResultReturnsUnresolved() =
        runBlocking {
            val resolver = ImportSongResolver(youtubeSearch = ImportYouTubeSongSearch { emptyList() })

            val result =
                resolver.resolve(
                    songs = listOf(song("Unknown Track", "Unknown Artist")),
                    localLibrary = emptyList(),
                    localFirst = true,
                ).single()

            assertEquals(ImportSource.UNRESOLVED, result.source)
            assertEquals(null, result.resolvedId)
            assertEquals(null, result.resolvedSong)
        }

    @Test
    fun youtubeFailureReturnsUnresolvedWithoutCancellingImport() =
        runBlocking {
            val resolver =
                ImportSongResolver(
                    youtubeSearch = ImportYouTubeSongSearch { error("Network unavailable") },
                )

            val result =
                resolver.resolve(
                    songs = listOf(song("Unknown Track", "Unknown Artist")),
                    localLibrary = emptyList(),
                    localFirst = false,
                ).single()

            assertEquals(ImportSource.UNRESOLVED, result.source)
        }

    @Test
    fun queryIncludesDecodedArtists() {
        assertEquals(
            "Song Title - Artist Name Guest",
            buildImportSongQuery(song("Song Title", "Artist%20Name", "Guest")),
        )
    }

    private fun song(
        title: String,
        vararg artists: String,
        id: String = "original-$title",
    ): Song =
        Song(
            song = SongEntity(id = id, title = title),
            artists =
                artists.mapIndexed { index, artist ->
                    ArtistEntity(id = "$id-artist-$index", name = artist)
                },
        )
}
