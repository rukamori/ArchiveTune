/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.SongItem
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.models.toMediaMetadata
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

object SpotifyPlaybackResolver {
    private const val MIN_MATCH_THRESHOLD = 0.35
    private const val CACHE_MAX_SIZE = 512

    private val mutex = Mutex()
    private val cache =
        object : LinkedHashMap<String, MediaMetadata>(CACHE_MAX_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, MediaMetadata>?): Boolean = size > CACHE_MAX_SIZE
        }

    suspend fun resolveToMediaItem(track: SpotifyTrack): MediaItem? = resolveToMetadata(track)?.toMediaItem()

    suspend fun resolveToSongItem(track: SpotifyTrack): SongItem? {
        val meta = resolveToMetadata(track) ?: return null
        if (meta.id.isBlank()) return null
        return SongItem(
            id = meta.id,
            title = track.name.ifBlank { meta.title },
            artists =
                track.artists.map { moe.rukamori.archivetune.innertube.models.Artist(name = it.name, id = it.id) }
                    .ifEmpty { meta.artists.map { moe.rukamori.archivetune.innertube.models.Artist(name = it.name, id = it.id) } },
            album =
                (track.album?.name ?: meta.album?.title)?.takeIf(String::isNotBlank)?.let { albumTitle ->
                    moe.rukamori.archivetune.innertube.models.Album(
                        name = albumTitle,
                        id = meta.album?.id ?: track.album?.id.orEmpty(),
                    )
                },
            duration = meta.duration.takeIf { it > 0 },
            thumbnail = meta.thumbnailUrl.orEmpty(),
            explicit = meta.explicit,
            endpoint = WatchEndpoint(videoId = meta.id),
        )
    }

    suspend fun resolveToMetadata(track: SpotifyTrack): MediaMetadata? =
        withContext(Dispatchers.IO) {
            mutex.withLock {
                cache[track.id]?.let { return@withContext it }
            }

            val searchResult =
                YouTube
                    .search(
                        query = SpotifyMapper.buildSearchQuery(track),
                        filter = YouTube.SearchFilter.FILTER_SONG,
                    ).getOrNull() ?: return@withContext null

            val candidates =
                searchResult.items
                    .filterIsInstance<SongItem>()
                    .distinctBy { it.id }
            if (candidates.isEmpty()) return@withContext null

            val precomputed =
                mutex.withLock {
                    SpotifyMapper.precompute(
                        title = track.name,
                        artist = track.artists.joinToString(" ") { it.name },
                        durationMs = track.durationMs,
                    )
                }

            val (best, score) =
                mutex.withLock {
                    candidates
                        .map { candidate ->
                            candidate to
                                SpotifyMapper.matchScorePrecomputed(
                                    precomputed = precomputed,
                                    candidateTitle = candidate.title,
                                    candidateArtist = candidate.artists.joinToString(" ") { it.name },
                                    candidateDurationSec = candidate.duration,
                                )
                        }.maxByOrNull { it.second }
                } ?: return@withContext null
            if (score < MIN_MATCH_THRESHOLD) return@withContext null

            val bestMetadata = best.toMediaMetadata()
            val metadata =
                bestMetadata.copy(
                    thumbnailUrl = SpotifyMapper.getTrackThumbnail(track) ?: best.thumbnail,
                    duration = if (track.durationMs > 0) track.durationMs / 1000 else best.duration ?: -1,
                    explicit = track.explicit || best.explicit,
                    album =
                        track.album?.let { MediaMetadata.Album(id = it.id, title = it.name) }
                            ?: bestMetadata.album,
                    spotifyTrackId = track.id.takeIf(String::isNotBlank),
                )

            mutex.withLock {
                cache[track.id] = metadata
            }
            metadata
        }
}
