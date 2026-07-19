/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.AlbumItem
import moe.rukamori.archivetune.innertube.models.ArtistItem
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.innertube.pages.ChartsPage
import moe.rukamori.archivetune.innertube.pages.MoodAndGenres
import javax.inject.Inject
import javax.inject.Singleton

data class SearchDiscoveryData(
    val moodAndGenres: List<MoodAndGenres.Item>,
    val newReleaseAlbums: List<AlbumItem>,
    val chartSections: List<ChartsPage.ChartSection>,
    val suggestedSongs: List<SongItem>,
    val searchedAlbums: List<AlbumItem>,
    val suggestedArtists: List<ArtistItem>,
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
                        val suggestedSongsDeferred = async { loadSuggestedSongs() }
                        val searchedAlbumsDeferred =
                            async {
                                searchItems<AlbumItem>(
                                    query = TopAlbumsQuery,
                                    filter = YouTube.SearchFilter.FILTER_ALBUM,
                                )
                            }
                        val suggestedArtistsDeferred = async { loadSuggestedArtists() }

                        val explorePage = explorePageDeferred.await()
                        val chartsPage = chartsPageDeferred.await()

                        Result.success(
                            SearchDiscoveryData(
                                moodAndGenres = explorePage.moodAndGenres,
                                newReleaseAlbums = explorePage.newReleaseAlbums,
                                chartSections = chartsPage.sections,
                                suggestedSongs = suggestedSongsDeferred.await(),
                                searchedAlbums = searchedAlbumsDeferred.await(),
                                suggestedArtists = suggestedArtistsDeferred.await(),
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

        private suspend fun loadSuggestedSongs(): List<SongItem> =
            coroutineScope {
                val seedSongs =
                    database
                        .mostPlayedSongs(
                            fromTimeStamp = AllHistoryTimestamp,
                            limit = MaxHistoryLookupItems,
                        ).first()
                        .filterNot { song -> song.song.isLocal }
                        .take(MaxSuggestionSeedItems)
                val seedSongIds = seedSongs.mapTo(HashSet()) { song -> song.id }

                seedSongs
                    .map { song ->
                        async {
                            loadRelatedSongs(song)
                                .ifEmpty { searchRelatedSongs(song) }
                        }
                    }.awaitAll()
                    .flatten()
                    .filterNot { song -> song.id in seedSongIds }
                    .distinctBy { song -> song.id }
                    .take(MaxSuggestedItems)
            }

        private suspend fun loadRelatedSongs(song: Song): List<SongItem> =
            try {
                val nextResult = YouTube.next(WatchEndpoint(videoId = song.id)).getOrThrow()
                val relatedSongs =
                    nextResult
                        .relatedEndpoint
                        ?.let { endpoint -> YouTube.related(endpoint).getOrNull()?.songs }
                        .orEmpty()
                (relatedSongs + nextResult.items).distinctBy { item -> item.id }
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                emptyList()
            }

        private suspend fun searchRelatedSongs(song: Song): List<SongItem> =
            searchItems(
                query =
                    buildString {
                        append(song.title)
                        song.artists
                            .firstOrNull()
                            ?.name
                            ?.takeIf(String::isNotBlank)
                            ?.let { artistName ->
                                append(' ')
                                append(artistName)
                            }
                    },
                filter = YouTube.SearchFilter.FILTER_SONG,
            )

        private suspend fun loadSuggestedArtists(): List<ArtistItem> =
            coroutineScope {
                val seedArtists =
                    database
                        .mostPlayedArtists(
                            fromTimeStamp = AllHistoryTimestamp,
                            limit = MaxHistoryLookupItems,
                        ).first()
                        .filter { artist -> artist.artist.isYouTubeArtist }
                        .take(MaxSuggestionSeedItems)
                val seedArtistIds = seedArtists.mapTo(HashSet()) { artist -> artist.id }

                seedArtists
                    .map { artist ->
                        async {
                            loadRelatedArtists(artist)
                                .ifEmpty { searchRelatedArtists(artist) }
                        }
                    }.awaitAll()
                    .flatten()
                    .filterNot { artist -> artist.id in seedArtistIds }
                    .distinctBy { artist -> artist.id }
                    .take(MaxSuggestedItems)
            }

        private suspend fun loadRelatedArtists(artist: Artist): List<ArtistItem> =
            try {
                YouTube
                    .artist(artist.id)
                    .getOrThrow()
                    .sections
                    .flatMap { section -> section.items }
                    .filterIsInstance<ArtistItem>()
            } catch (throwable: Throwable) {
                if (throwable is CancellationException) throw throwable
                emptyList()
            }

        private suspend fun searchRelatedArtists(artist: Artist): List<ArtistItem> =
            searchItems(
                query = artist.title,
                filter = YouTube.SearchFilter.FILTER_ARTIST,
            )

        private companion object {
            const val AllHistoryTimestamp = 0L
            const val MaxHistoryLookupItems = 36
            const val MaxSuggestionSeedItems = 6
            const val MaxSuggestedItems = 12
            const val TopAlbumsQuery = "top albums"
        }
    }
