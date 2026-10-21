/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.queues

import androidx.media3.common.MediaItem
import moe.rukamori.archivetune.db.LocalMusicIdentity
import moe.rukamori.archivetune.extensions.metadata
import moe.rukamori.archivetune.models.MediaMetadata
import moe.rukamori.archivetune.utils.isLocalMediaId

class ListQueue(
    val title: String? = null,
    val items: List<MediaItem>,
    val startIndex: Int = 0,
    val position: Long = 0L,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        val uniqueItems = mutableListOf<MediaItem>()
        val localTrackIndexes = mutableMapOf<String, Int>()
        val requestedStartIndex = startIndex.takeIf { items.isNotEmpty() }?.coerceIn(items.indices) ?: 0
        var uniqueStartIndex = 0
        items.forEachIndexed { index, item ->
            val identity = item.localTrackIdentity()
            val existingIndex = identity?.let(localTrackIndexes::get)
            if (existingIndex == null) {
                if (identity != null) localTrackIndexes[identity] = uniqueItems.size
                if (index == requestedStartIndex) uniqueStartIndex = uniqueItems.size
                uniqueItems += item
            } else if (index == requestedStartIndex) {
                uniqueStartIndex = existingIndex
            }
        }
        val safeStartIndex =
            if (uniqueItems.isEmpty()) {
                0
            } else {
                uniqueStartIndex
            }

        return Queue.Status(title, uniqueItems, safeStartIndex, position)
    }

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage() = throw UnsupportedOperationException()

    private fun MediaItem.localTrackIdentity(): String? {
        val metadata = metadata
        if (metadata?.id?.isLocalMediaId() != true) return null
        return LocalMusicIdentity.trackKey(
            title = metadata.title,
            artists = metadata.artists.map { it.name },
            durationSeconds = metadata.duration,
            fallbackId = metadata.id,
        )
    }
}
