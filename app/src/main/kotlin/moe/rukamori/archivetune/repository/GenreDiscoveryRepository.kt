/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.browse.GenreSource
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.BrowseEndpoint
import moe.rukamori.archivetune.innertube.pages.BrowseResult
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GenreDiscoveryRepository @Inject constructor() {
    private val requests = Semaphore(3)

    suspend fun load(
        source: GenreSource,
        endpoint: BrowseEndpoint,
        query: String?,
        continuation: String? = null,
    ): Result<BrowseResult> = withContext(Dispatchers.IO) {
        try {
            requests.withPermit {
                val page = if (source == GenreSource.FEED) {
                    if (continuation == null) YouTube.browse(endpoint.browseId, endpoint.params).getOrThrow()
                    else YouTube.browseContinuation(continuation).getOrThrow()
                } else {
                    val filter = when (source) {
                        GenreSource.SONGS -> YouTube.SearchFilter.FILTER_SONG
                        GenreSource.ALBUMS -> YouTube.SearchFilter.FILTER_ALBUM
                        GenreSource.FEATURED_PLAYLISTS -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
                        GenreSource.COMMUNITY_PLAYLISTS -> YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST
                        GenreSource.FEED -> error("Feed is not a search source")
                    }
                    val result = if (continuation == null) {
                        YouTube.search(requireNotNull(query), filter, useAccountContext = false).getOrThrow()
                    } else {
                        YouTube.searchContinuation(continuation, useAccountContext = false).getOrThrow()
                    }
                    BrowseResult(
                        title = null,
                        items = listOf(BrowseResult.Item(title = null, items = result.items.filter(source.tab::accepts))),
                        continuation = result.continuation,
                    )
                }
                Result.success(page)
            }
        } catch (exception: Exception) {
            if (exception is CancellationException) throw exception
            Result.failure(exception)
        }
    }
}
