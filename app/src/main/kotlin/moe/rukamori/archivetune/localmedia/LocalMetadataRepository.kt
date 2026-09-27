/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import java.net.SocketTimeoutException
import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.Artist
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem

data class LocalMetadataQuery(
    val title: String,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val durationSeconds: Int,
    val year: Int? = null,
)

data class LocalMetadataMatch(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val thumbnailUrl: String? = null,
    val artistThumbnailUrl: String? = null,
    val artistId: String? = null,
    val albumId: String? = null,
)

class LocalMetadataRepository @Inject constructor() {
    suspend fun find(query: LocalMetadataQuery): LocalMetadataMatch? =
        withContext(Dispatchers.IO) {
            val artistName = query.artist.nonBlank() ?: query.albumArtist.nonBlank()
                ?: return@withContext null
            if (query.title.isBlank() && query.album.isNullOrBlank()) return@withContext null
            val completed = withTimeoutOrNull(15_000L) {
                LookupResult(findMatch(query, artistName))
            } ?: throw SocketTimeoutException("Local metadata lookup timed out")
            completed.match
        }

    private suspend fun findMatch(query: LocalMetadataQuery, artistName: String): LocalMetadataMatch? {
        val song = if (query.title.isNotBlank()) findSong(query, artistName) ?: return null else null
        val songAlbum = song?.album
        val album = when {
            songAlbum != null -> YouTube.album(songAlbum.id, withSongs = false).awaitResult().album
                .takeIf { candidate ->
                    same(candidate.title, songAlbum.name) &&
                        (query.album.isNullOrBlank() || same(candidate.title, query.album)) &&
                        (query.albumArtist.isNullOrBlank() || candidate.artists.orEmpty().matches(query.albumArtist)) &&
                        (query.year == null || candidate.year == null || query.year == candidate.year)
                } ?: return null
            song == null -> findAlbum(query, artistName) ?: return null
            else -> null
        }
        val matchedArtist = song?.artists?.firstOrNull { same(it.name, artistName) }
            ?: album?.artists?.firstOrNull { same(it.name, artistName) }
        val artistId = matchedArtist?.id.nonBlank()
        val artistArtwork = if (artistId != null) {
            YouTube.search(artistName, YouTube.SearchFilter.FILTER_ARTIST, useAccountContext = false)
                .awaitResult().items.filterIsInstance<ArtistItem>()
                .singleOrNull { it.id == artistId && same(it.title, artistName) }
                ?.thumbnail.nonBlank()
        } else null
        return LocalMetadataMatch(
            title = song?.title.nonBlank(),
            artist = song?.artists?.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.name },
            albumArtist = album?.artists?.takeIf { it.isNotEmpty() }?.joinToString("; ") { it.name },
            album = album?.title.nonBlank() ?: song?.album?.name.nonBlank(),
            year = album?.year?.takeIf { it > 0 },
            thumbnailUrl = album?.thumbnail.nonBlank() ?: song?.thumbnail.nonBlank(),
            artistThumbnailUrl = artistArtwork,
            artistId = artistId,
            albumId = album?.id ?: song?.album?.id,
        )
    }

    private suspend fun findSong(query: LocalMetadataQuery, artistName: String): SongItem? {
        if (query.durationSeconds <= 0) return null
        val searchQuery = listOfNotNull(query.title, artistName, query.album.nonBlank()).joinToString(" ")
        val matches = YouTube.search(searchQuery, YouTube.SearchFilter.FILTER_SONG, useAccountContext = false)
            .awaitResult().items.filterIsInstance<SongItem>().filter { candidate ->
                !candidate.isPodcast && same(candidate.title, query.title) &&
                    candidate.artists.matches(artistName) &&
                    (query.album.isNullOrBlank() || same(candidate.album?.name, query.album)) &&
                    candidate.duration?.let { duration ->
                        duration > 0 && kotlin.math.abs(duration.toLong() - query.durationSeconds) <= 3L
                    } == true
            }
        return matches.singleOrNull()
    }

    private suspend fun findAlbum(query: LocalMetadataQuery, artistName: String): AlbumItem? =
        YouTube.search("${query.album.orEmpty()} $artistName", YouTube.SearchFilter.FILTER_ALBUM, useAccountContext = false)
            .awaitResult().items.filterIsInstance<AlbumItem>().singleOrNull { candidate ->
                same(candidate.title, query.album) && candidate.artists.orEmpty().matches(artistName) &&
                    (query.albumArtist.isNullOrBlank() || candidate.artists.orEmpty().matches(query.albumArtist)) &&
                    (query.year == null || candidate.year == null || query.year == candidate.year)
            }

    private fun List<Artist>.matches(name: String): Boolean =
        any { same(it.name, name) } || same(joinToString("; ") { it.name }, name)

    private fun same(first: String?, second: String?): Boolean =
        !first.isNullOrBlank() && !second.isNullOrBlank() && normalize(first) == normalize(second)

    private fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC).lowercase(Locale.ROOT)
            .trim().replace(WHITESPACE, " ")

    private fun String?.nonBlank(): String? = this?.trim()?.takeIf { it.isNotEmpty() }

    private suspend fun <T> Result<T>.awaitResult(): T {
        currentCoroutineContext().ensureActive()
        return getOrThrow()
    }

    private data class LookupResult(val match: LocalMetadataMatch?)

    private companion object {
        val WHITESPACE = Regex("[\\s\\p{Z}]+")
    }
}
