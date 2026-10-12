/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.spotify

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.playback.queues.Queue
import moe.rukamori.archivetune.spotify.models.SpotifyTrack

class SpotifyPlaylistQueue(
    private val playlistId: String? = null,
    private val title: String? = null,
    private val initialTracks: List<SpotifyTrack> = emptyList(),
    private val startIndex: Int = 0,
    override val preloadItem: MediaMetadata? = null,
) : Queue {
    private val allTracks = mutableListOf<SpotifyTrack>()
    private var resolveOffset = 0
    private var apiFetchOffset = 0
    private var apiTotal = 0
    private var apiHasMore = true

    override suspend fun getInitialStatus(): Queue.Status =
        withContext(Dispatchers.IO) {
            if (initialTracks.isNotEmpty()) {
                allTracks += initialTracks
                apiTotal = initialTracks.size
                apiFetchOffset = apiTotal
                apiHasMore = false
            } else {
                fetchNextApiPage()
            }

            while (startIndex >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }

            if (allTracks.isEmpty()) {
                return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }

            val targetIndex = startIndex.coerceIn(allTracks.indices)
            val resolvedEntries = resolveTrackEntries(allTracks)
            val resolvedItems = resolvedEntries.map { it.second }

            resolveOffset = allTracks.size
            if (resolvedItems.isEmpty()) {
                return@withContext Queue.Status(title = title, items = emptyList(), mediaItemIndex = 0)
            }

            Queue.Status(
                title = title,
                items = resolvedItems,
                mediaItemIndex =
                    resolvedEntries
                        .indexOfFirst { it.first >= targetIndex }
                        .takeIf { it >= 0 }
                        ?: resolvedItems.lastIndex,
            )
        }

    override fun hasNextPage(): Boolean = resolveOffset < allTracks.size || apiHasMore

    override suspend fun nextPage(): List<MediaItem> =
        withContext(Dispatchers.IO) {
            if (resolveOffset >= allTracks.size && apiHasMore) {
                fetchNextApiPage()
            }
            if (resolveOffset >= allTracks.size) return@withContext emptyList()

            val end = (resolveOffset + RESOLVE_BATCH_SIZE).coerceAtMost(allTracks.size)
            val batch = allTracks.subList(resolveOffset, end)
            resolveOffset = end
            resolveTracks(batch)
        }

    private suspend fun resolveTracks(tracks: List<SpotifyTrack>): List<MediaItem> = resolveTrackEntries(tracks).map { it.second }

    private suspend fun resolveTrackEntries(tracks: List<SpotifyTrack>): List<Pair<Int, MediaItem>> =
        buildList {
            tracks.chunked(RESOLVE_BATCH_SIZE).forEachIndexed { chunkIndex, chunk ->
                val chunkOffset = chunkIndex * RESOLVE_BATCH_SIZE
                val resolvedChunk =
                    coroutineScope {
                        chunk
                            .mapIndexed { index, track ->
                                async {
                                    SpotifyPlaybackResolver
                                        .resolveToMediaItem(track)
                                        ?.let { mediaItem -> chunkOffset + index to mediaItem }
                                }
                            }.awaitAll()
                            .filterNotNull()
                    }
                addAll(resolvedChunk)
            }
        }

    private suspend fun fetchNextApiPage() {
        if (!apiHasMore) return
        val result =
            Spotify
                .playlistTracks(
                    playlistId = checkNotNull(playlistId),
                    limit = SPOTIFY_PAGE_SIZE,
                    offset = apiFetchOffset,
                ).getOrThrow()
        apiTotal = result.total
        val fetched = result.items.mapNotNull { it.track?.takeUnless(SpotifyTrack::isLocal) }
        allTracks += fetched
        apiFetchOffset += result.items.size
        apiHasMore = apiFetchOffset < apiTotal
    }

    companion object {
        private const val SPOTIFY_PAGE_SIZE = 50
        private const val RESOLVE_BATCH_SIZE = 20
    }
}
