/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlistimport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.models.ImportSource
import moe.rukamori.archivetune.models.ImportedSongResult
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.utils.FuzzyMatcher
import timber.log.Timber
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.concurrent.atomic.AtomicInteger

fun interface ImportYouTubeSongSearch {
    suspend fun search(query: String): List<Song>
}

object YouTubeImportSongSearch : ImportYouTubeSongSearch {
    override suspend fun search(query: String): List<Song> =
        YouTube
            .search(query, YouTube.SearchFilter.FILTER_SONG)
            .onFailure { error ->
                Timber.w(error, "YouTube search failed for imported song query: %s", query)
            }.getOrNull()
            ?.items
            ?.filterIsInstance<SongItem>()
            ?.distinctBy { it.id }
            ?.map { it.toImportSong() }
            .orEmpty()
}

class ImportSongResolver(
    private val youtubeSearch: ImportYouTubeSongSearch = YouTubeImportSongSearch,
    private val maxConcurrentResolutions: Int = DEFAULT_MAX_CONCURRENT_RESOLUTIONS,
) {
    init {
        require(maxConcurrentResolutions > 0) { "Maximum concurrent resolutions must be greater than zero" }
    }

    suspend fun resolve(
        songs: List<Song>,
        localLibrary: List<Song>,
        localFirst: Boolean,
        onProgress: suspend (completed: Int, total: Int) -> Unit = { _, _ -> },
    ): List<ImportedSongResult> =
        coroutineScope {
            val resolutionSemaphore = Semaphore(maxConcurrentResolutions)
            val completed = AtomicInteger(0)

            songs
                .map { originalSong ->
                    async {
                        val result =
                            resolutionSemaphore.withPermit {
                                resolveSong(
                                    originalSong = originalSong,
                                    localLibrary = localLibrary,
                                    localFirst = localFirst,
                                )
                            }
                        onProgress(completed.incrementAndGet(), songs.size)
                        result
                    }
                }.awaitAll()
        }

    private suspend fun resolveSong(
        originalSong: Song,
        localLibrary: List<Song>,
        localFirst: Boolean,
    ): ImportedSongResult {
        val artistNames = originalSong.artists.map { it.name }.filter(String::isNotBlank)

        if (localFirst) {
            val localMatch =
                FuzzyMatcher.bestSongMatch(
                    candidates = localLibrary,
                    title = originalSong.title,
                    artists = artistNames,
                )
            if (localMatch != null) {
                return ImportedSongResult(
                    originalSong = originalSong,
                    resolvedId = localMatch.id,
                    resolvedSong = localMatch,
                    source = ImportSource.LOCAL,
                )
            }
        }

        val youtubeMatch =
            try {
                FuzzyMatcher.bestSongMatch(
                    candidates = youtubeSearch.search(buildImportSongQuery(originalSong)),
                    title = originalSong.title,
                    artists = artistNames,
                )
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.w(error, "YouTube lookup failed for imported song: %s", originalSong.title)
                null
            }

        return if (youtubeMatch != null) {
            ImportedSongResult(
                originalSong = originalSong,
                resolvedId = youtubeMatch.id,
                resolvedSong = youtubeMatch,
                source = ImportSource.YOUTUBE,
            )
        } else {
            ImportedSongResult(
                originalSong = originalSong,
                resolvedId = null,
                resolvedSong = null,
                source = ImportSource.UNRESOLVED,
            )
        }
    }

    private companion object {
        const val DEFAULT_MAX_CONCURRENT_RESOLUTIONS = 5
    }
}

internal fun buildImportSongQuery(song: Song): String {
    val artists =
        song.artists
            .map { artist -> decodeImportArtistName(artist.name) }
            .filter(String::isNotBlank)
            .joinToString(" ")
            .trim()
    return if (artists.isEmpty()) song.title.trim() else "${song.title.trim()} - $artists"
}

private fun decodeImportArtistName(value: String): String =
    try {
        URLDecoder.decode(value, StandardCharsets.UTF_8.toString())
    } catch (error: IllegalArgumentException) {
        value
    }

private fun SongItem.toImportSong(): Song {
    val metadata = toMediaMetadata()
    return Song(
        song = metadata.toSongEntity(),
        artists =
            metadata.artists.mapIndexed { index, artist ->
                ArtistEntity(
                    id = artist.id ?: "import_${metadata.id}_artist_$index",
                    name = artist.name,
                    thumbnailUrl = artist.thumbnailUrl,
                )
            },
    )
}
