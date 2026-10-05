/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterPolicy
import moe.rukamori.archivetune.aicontentfilter.FilterAiContentUseCase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.ArtistEntity
import moe.rukamori.archivetune.extensions.filterBlockedArtists
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.filterExplicit
import moe.rukamori.archivetune.models.SimilarRecommendation
import moe.rukamori.archivetune.spotify.models.SpotifyTrack
import moe.rukamori.archivetune.utils.reportException
import timber.log.Timber
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LoadSpotifyRecommendationsUseCase
    @Inject
    constructor(
        private val spotifyLibraryRepository: SpotifyLibraryRepository,
        private val database: MusicDatabase,
        private val filterAiContent: FilterAiContentUseCase,
    ) {
        private val cacheMutex = Mutex()
        private val artistIdCache =
            object : LinkedHashMap<String, String>(MAX_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHE_SIZE
            }
        private val trackIdCache =
            object : LinkedHashMap<String, String>(MAX_CACHE_SIZE, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean = size > MAX_CACHE_SIZE
            }

        private val resolutionSemaphore = Semaphore(MAX_CONCURRENT_RESOLUTIONS)

        suspend operator fun invoke(
            hideExplicit: Boolean,
            blockedArtistIds: Set<String>,
            aiContentFilterPolicy: AiContentFilterPolicy,
            fromTimeStamp: Long,
        ): List<SimilarRecommendation>? =
            withContext(Dispatchers.IO) {
                val session = spotifyLibraryRepository.restoreSession()
                if (!session.isAuthenticated) {
                    Timber.d("Spotify is not authenticated, skipping Spotify recommendations")
                    return@withContext null
                }

                val localArtists =
                    database
                        .mostPlayedMusicArtists(fromTimeStamp, limit = 10)
                        .first()
                        .filter { it.artist.blockedAt == null }
                        .shuffled()
                        .take(3)

                val localSongs =
                    database
                        .mostPlayedSongs(fromTimeStamp, limit = 10)
                        .first()
                        .filter { !it.song.isPodcast && it.album != null }
                        .shuffled()
                        .take(2)

                val recommendations = mutableListOf<SimilarRecommendation>()

                // 1. Recommendations seeded by top local artists
                coroutineScope {
                    localArtists.map { artist ->
                        async {
                            try {
                                val spotifyArtistId = getSpotifyArtistId(artist.title) ?: return@async null
                                val result = Spotify.recommendations(seedArtistIds = listOf(spotifyArtistId), limit = 8).getOrNull()
                                val tracks = result?.tracks.orEmpty()
                                if (tracks.isEmpty()) return@async null
                                val resolved = resolveAndFilter(tracks, hideExplicit, blockedArtistIds, aiContentFilterPolicy)
                                if (resolved.isEmpty()) return@async null
                                SimilarRecommendation(title = artist, items = resolved)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                reportException(e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull().let { recommendations.addAll(it) }
                }

                // 2. Recommendations seeded by top local songs
                coroutineScope {
                    localSongs.map { song ->
                        async {
                            try {
                                val spotifyTrackId =
                                    getSpotifyTrackId(song.song.title, song.artists.firstOrNull()?.name)
                                        ?: return@async null
                                val result = Spotify.recommendations(seedTrackIds = listOf(spotifyTrackId), limit = 8).getOrNull()
                                val tracks = result?.tracks.orEmpty()
                                if (tracks.isEmpty()) return@async null
                                val resolved = resolveAndFilter(tracks, hideExplicit, blockedArtistIds, aiContentFilterPolicy)
                                if (resolved.isEmpty()) return@async null
                                SimilarRecommendation(title = song, items = resolved)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                reportException(e)
                                null
                            }
                        }
                    }.awaitAll().filterNotNull().let { recommendations.addAll(it) }
                }

                // 3. Fallback to Spotify account's top artists if local history is sparse
                if (recommendations.isEmpty()) {
                    val topArtists = Spotify.topArtists(limit = 4).getOrNull()?.items.orEmpty()
                    coroutineScope {
                        topArtists.map { topArtist ->
                            async {
                                try {
                                    val result = Spotify.recommendations(seedArtistIds = listOf(topArtist.id), limit = 8).getOrNull()
                                    val tracks = result?.tracks.orEmpty()
                                    if (tracks.isEmpty()) return@async null
                                    val resolved = resolveAndFilter(tracks, hideExplicit, blockedArtistIds, aiContentFilterPolicy)
                                    if (resolved.isEmpty()) return@async null
                                    val syntheticArtist =
                                        Artist(
                                            artist =
                                                ArtistEntity(
                                                    id = topArtist.id,
                                                    name = topArtist.name,
                                                    thumbnailUrl = topArtist.images.firstOrNull()?.url,
                                                ),
                                            songCount = 0,
                                            timeListened = 0,
                                        )
                                    SimilarRecommendation(title = syntheticArtist, items = resolved)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    reportException(e)
                                    null
                                }
                            }
                        }.awaitAll().filterNotNull().let { recommendations.addAll(it) }
                    }
                }

                recommendations.takeIf { it.isNotEmpty() }
            }

        private suspend fun resolveAndFilter(
            tracks: List<SpotifyTrack>,
            hideExplicit: Boolean,
            blockedArtistIds: Set<String>,
            aiContentFilterPolicy: AiContentFilterPolicy,
        ): List<SongItem> {
            val resolved =
                coroutineScope {
                    tracks.take(8).map { track ->
                        async {
                            resolutionSemaphore.withPermit {
                                SpotifyPlaybackResolver.resolveToSongItem(track)
                            }
                        }
                    }.awaitAll().filterNotNull()
                }

            val filtered =
                resolved
                    .filterExplicit(hideExplicit)
                    .filterBlockedArtists(blockedArtistIds)

            return filterAiContent(filtered, aiContentFilterPolicy).shuffled()
        }

        private suspend fun getSpotifyArtistId(name: String): String? {
            val trimmed = name.trim()
            if (trimmed.isBlank()) return null
            val key = trimmed.lowercase(Locale.ROOT)
            cacheMutex.withLock {
                artistIdCache[key]?.let { return it }
            }
            val search = Spotify.search(query = trimmed, types = listOf("artist"), limit = 1).getOrNull()
            val id = search?.artists?.items?.firstOrNull()?.id
            if (id != null) {
                cacheMutex.withLock {
                    artistIdCache[key] = id
                }
            }
            return id
        }

        private suspend fun getSpotifyTrackId(
            title: String,
            artistName: String?,
        ): String? {
            val query = "$title ${artistName.orEmpty()}".trim()
            if (query.isBlank()) return null
            val key = query.lowercase(Locale.ROOT)
            cacheMutex.withLock {
                trackIdCache[key]?.let { return it }
            }
            val search = Spotify.search(query = query, types = listOf("track"), limit = 1).getOrNull()
            val id = search?.tracks?.items?.firstOrNull()?.id
            if (id != null) {
                cacheMutex.withLock {
                    trackIdCache[key] = id
                }
            }
            return id
        }

        private companion object {
            const val MAX_CACHE_SIZE = 256
            const val MAX_CONCURRENT_RESOLUTIONS = 4
        }
    }
