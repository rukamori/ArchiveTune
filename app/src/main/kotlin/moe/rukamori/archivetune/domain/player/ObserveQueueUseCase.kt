/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.domain.player

import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.playback.QueueRepository
import moe.rukamori.archivetune.ui.player.QueueChipUi
import moe.rukamori.archivetune.ui.player.QueueChips
import moe.rukamori.archivetune.ui.player.QueueEntries
import moe.rukamori.archivetune.ui.player.QueueEntryUi
import moe.rukamori.archivetune.ui.player.QueueUiModel
import moe.rukamori.archivetune.utils.makeTimeString
import javax.inject.Inject

class ObserveQueueUseCase @Inject constructor(private val repository: QueueRepository) {
    operator fun invoke() = repository.observe().map { snapshot ->
        snapshot?.let { (playback, extra, locked, autoplay, available) ->
            QueueUiModel(
                title = extra.title,
                entries = QueueEntries(playback.entries.map { entry ->
                    QueueEntryUi(entry.id, entry.metadata.title, entry.metadata.artists.joinToString { it.name },
                        entry.metadata.thumbnailUrl, makeTimeString(entry.metadata.duration.coerceAtLeast(0) * 1000L),
                        entry.current, entry.recommended)
                }),
                duration = makeTimeString(playback.entries.sumOf { it.metadata.duration.coerceAtLeast(0).toLong() } * 1000L),
                currentId = playback.entries.firstOrNull { it.current }?.id,
                playing = playback.playing, liked = extra.liked, shuffle = playback.shuffle, repeatMode = playback.repeat,
                locked = locked || extra.guest, guest = extra.guest, autoplay = autoplay, autoplayAvailable = available,
                canControlPlayback = extra.canControlPlayback,
                episodeQueue = playback.entries.firstOrNull { it.current }?.metadata?.isPodcast == true,
                loadingRecommendations = autoplay && (extra.loading || extra.recommendations.loading),
                chips = QueueChips(if (autoplay && available) extra.recommendations.chips.map { QueueChipUi(it.id, it.title, it.selected) } else emptyList()),
                recommendationsFailed = autoplay && extra.recommendations.failed,
            )
        }
    }
}
