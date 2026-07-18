/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlistimport

import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.utils.FuzzyMatcher
import timber.log.Timber

class ImportReplacementSearcher(
    private val youtubeSearch: ImportYouTubeSongSearch = YouTubeImportSongSearch,
) {
    fun searchLocal(
        query: String,
        localLibrary: List<Song>,
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): List<Song> {
        val terms = query.toImportSearchTerms()
        return FuzzyMatcher.rankedSongMatches(
            candidates = localLibrary,
            title = terms.title,
            artists = terms.artists,
            limit = limit,
        )
    }

    suspend fun searchYouTube(
        query: String,
        limit: Int = DEFAULT_RESULT_LIMIT,
    ): List<Song> =
        try {
            youtubeSearch
                .search(query.trim())
                .distinctBy { it.id }
                .take(limit)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.w(error, "YouTube replacement search failed for query: %s", query)
            emptyList()
        }

    private companion object {
        const val DEFAULT_RESULT_LIMIT = 10
    }
}

private data class ImportSearchTerms(
    val title: String,
    val artists: List<String>,
)

private fun String.toImportSearchTerms(): ImportSearchTerms {
    val normalized = trim()
    val delimiterIndex = normalized.indexOf(" - ")
    if (delimiterIndex < 0) return ImportSearchTerms(title = normalized, artists = emptyList())

    val title = normalized.substring(0, delimiterIndex).trim()
    val artist = normalized.substring(delimiterIndex + 3).trim()
    return ImportSearchTerms(
        title = title,
        artists = listOf(artist).filter(String::isNotBlank),
    )
}
