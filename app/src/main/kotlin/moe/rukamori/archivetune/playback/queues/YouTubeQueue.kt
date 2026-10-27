/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.extensions.toMediaItem
import moe.rukamori.archivetune.innertube.YouTube
import moe.rukamori.archivetune.innertube.models.WatchEndpoint
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.innertube.pages.QueueChip
import moe.rukamori.archivetune.innertube.pages.NextResult
import moe.rukamori.archivetune.playback.withQueueEntry

class YouTubeQueue(
    internal var endpoint: WatchEndpoint,
    override val preloadItem: MediaMetadata? = null,
    internal val followAutomixPreview: Boolean = false,
    private val expandToFullQueueWhenAutoLoadMoreDisabled: Boolean = false,
    initialContinuation: String? = null,
) : Queue {
    internal var continuation: String? = initialContinuation
        private set
    override val isRadio: Boolean = followAutomixPreview || endpoint.playlistId?.startsWith("RD") == true
    var chips: List<QueueChip> = emptyList()
        private set
    override val queueChips: List<QueueChip> get() = chips

    override suspend fun getInitialStatus(): Queue.Status {
        val nextResult =
            withContext(IO) {
                YouTube
                    .next(
                        endpoint = endpoint,
                        continuation = continuation,
                        followAutomixPreview = followAutomixPreview,
                    ).getOrThrow()
            }
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        chips = nextResult.queueChips
        return Queue.Status(
            title = nextResult.title,
            items = nextResult.items.map { it.toMediaItem().withQueueEntry(recommended = isRadio) },
            mediaItemIndex = nextResult.currentIndex ?: 0,
        )
    }

    override fun hasNextPage(): Boolean = continuation != null

    override fun shouldExpandToFullQueueWhenAutoLoadMoreDisabled(): Boolean = expandToFullQueueWhenAutoLoadMoreDisabled

    override suspend fun nextPage(): List<MediaItem> {
        val nextResult = fetchNextPage()
        acceptNextPage(nextResult)
        return nextResult.items.map { it.toMediaItem().withQueueEntry(recommended = isRadio) }
    }

    internal suspend fun fetchNextPage(): NextResult = withContext(IO) {
        YouTube.next(endpoint = endpoint, continuation = continuation, followAutomixPreview = followAutomixPreview).getOrThrow()
    }

    internal fun updateChips(values: List<QueueChip>) {
        chips = values
    }

    internal fun acceptNextPage(nextResult: NextResult) {
        endpoint = nextResult.endpoint
        continuation = nextResult.continuation
        if (nextResult.queueChips.isNotEmpty()) chips = nextResult.queueChips
    }

    companion object {
        fun shuffledPlaylist(
            playlistId: String,
            endpoint: WatchEndpoint,
        ): Queue {
            val normalizedId = playlistId.removePrefix("VL")
            return if (normalizedId.startsWith("RD")) {
                playlist(endpoint)
            } else {
                YouTubePlaylistShuffleQueue(normalizedId)
            }
        }

        fun playlist(
            endpoint: WatchEndpoint,
            preloadItem: MediaMetadata? = null,
        ) = YouTubeQueue(
            endpoint = endpoint,
            preloadItem = preloadItem,
            expandToFullQueueWhenAutoLoadMoreDisabled = true,
        )

        fun radio(song: MediaMetadata) =
            YouTubeQueue(
                endpoint = WatchEndpoint(videoId = song.id),
                preloadItem = song,
                followAutomixPreview = true,
            )
    }
}
