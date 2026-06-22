/*
 * ArchiveTune (2026)
 * Â© Rukamori â€” github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import javax.inject.Inject
import javax.inject.Singleton

data class SearchDiscoveryData(
    val moodAndGenres: List<MoodAndGenres.Item>,
    val newReleaseAlbums: List<AlbumItem>,
    val chartSections: List<ChartsPage.ChartSection>,
    val topSongs: List<Song>,
    val searchedAlbums: List<AlbumItem>,
    val topArtists: List<Artist>,
)

@Singleton
class SearchDiscoveryRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        suspend fun loadDiscovery(): Result<SearchDiscoveryData> =
            withContext(Dispatchers.IO) {
                try {
                    coroutineScope {
                        val explorePageDeferred = async { YouTube.explore().getOrThrow() }
                        val chartsPageDeferred = async { YouTube.getChartsPage().getOrThrow() }
                        val topSongsDeferred =
                            async {
                                database
                                    .mostPlayedSongs(
                                        fromTimeStamp = AllHistoryTimestamp,
                                        limit = MaxHistoryLookupItems,
                                    ).first()
                            }
                        val searchedAlbumsDeferred =
                            async {
                                searchItems<AlbumItem>(
                                    query = TopAlbumsQuery,
                                    filter = YouTube.SearchFilter.FILTER_ALBUM,
                                )
                            }
                        val topArtistsDeferred =
                            async {
                                database
                                    .mostPlayedArtists(
                                        fromTimeStamp = AllHistoryTimestamp,
                                        limit = MaxHistoryLookupItems,
                                    ).first()
                            }

                        val explorePage = explorePageDeferred.await()
                        val chartsPage = chartsPageDeferred.await()

                        Result.success(
                            SearchDiscoveryData(
                                moodAndGenres = explorePage.moodAndGenres,
                                newReleaseAlbums = explorePage.newReleaseAlbums,
                                chartSections = chartsPage.sections,
                                topSongs = topSongsDeferred.await(),
                                searchedAlbums = searchedAlbumsDeferred.await(),
                                topArtists = topArtistsDeferred.await(),
                            ),
                        )
                    }
                } catch (throwable: Throwable) {
                    if (throwable is CancellationException) throw throwable
                    Result.failure(throwable)
                }
            }

        private suspend inline fun <reified T> searchItems(
            query: String,
            filter: YouTube.SearchFilter,
        ): List<T> =
            try {
                YouTube
                    .search(
                        query = query,
                        filter = filter,
                        useAccountContext = false,
                    ).getOrThrow()
                    .items
                    .filterIsInstance<T>()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                emptyList()
            }

        private companion object {
            const val AllHistoryTimestamp = 0L
            const val MaxHistoryLookupItems = 36
            const val TopAlbumsQuery = "top albums"
        }
    }
