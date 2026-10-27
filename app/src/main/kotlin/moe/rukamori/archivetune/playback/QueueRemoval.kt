/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import moe.rukamori.archivetune.extensions.buildRestoredShuffleOrderIndices

internal class QueueRemoval private constructor(
    private val source: Player,
    private val originalWindows: List<Timeline.Window>,
    private val originalShuffleUids: List<Any>,
    private val removedUids: Set<Any>,
) {
    val removedItems = originalWindows.filter { it.uid in removedUids }.map { it.mediaItem }

    fun restore(player: Player): Boolean {
        if (player !== source) return false

        val currentWindows = player.currentTimeline.windows()
        val currentUids = currentWindows.map { it.uid }
        val originalUids = originalWindows.map { it.uid }
        val survivingUids = originalUids.filterNot { it in removedUids }.toHashSet()
        if (survivingUids.isEmpty()) {
            if (currentUids.isNotEmpty()) return false
        } else if (currentUids.none { it in survivingUids }) {
            return false
        }

        val restoredUids = restoreOrder(originalUids, currentUids)
        val restoredShuffleUids =
            restoreOrder(originalShuffleUids, player.currentTimeline.shuffleUids())
        val removedWindows = originalWindows.filter { it.uid in removedUids }.associateBy { it.uid }
        var index = 0
        while (index < restoredUids.size) {
            if (restoredUids[index] !in removedUids) {
                index++
                continue
            }
            val insertionIndex = index
            val items = buildList {
                while (index < restoredUids.size && restoredUids[index] in removedUids) {
                    add(removedWindows.getValue(restoredUids[index]).mediaItem)
                    index++
                }
            }
            player.addMediaItems(insertionIndex, items)
        }

        if (player is ExoPlayer) {
            val restoredIndices = restoredUids.withIndex().associate { it.value to it.index }
            player.setShuffleOrder(
                DefaultShuffleOrder(
                    buildRestoredShuffleOrderIndices(restoredShuffleUids.map(restoredIndices::getValue)),
                    System.currentTimeMillis(),
                ),
            )
        }
        return true
    }

    private fun restoreOrder(originalOrder: List<Any>, currentOrder: List<Any>): List<Any> {
        val currentUids = currentOrder.toHashSet()
        val before = mutableMapOf<Any, List<Any>>()
        var pending = mutableListOf<Any>()
        var lastSurvivingUid: Any? = null
        originalOrder.forEach { uid ->
            if (uid in removedUids) {
                pending.add(uid)
            } else if (uid in currentUids) {
                if (pending.isNotEmpty()) {
                    before[uid] = pending
                    pending = mutableListOf()
                }
                lastSurvivingUid = uid
            }
        }
        return buildList(currentOrder.size + removedUids.size) {
            if (lastSurvivingUid == null) addAll(pending)
            currentOrder.forEach { uid ->
                before[uid]?.let { addAll(it) }
                add(uid)
                if (uid == lastSurvivingUid) addAll(pending)
            }
        }
    }

    companion object {
        fun remove(player: Player, windows: List<Timeline.Window>): QueueRemoval? {
            val originalWindows = player.currentTimeline.windows()
            val requestedUids = windows.mapTo(HashSet()) { it.uid }
            val removedIndices = originalWindows.indices.filter { originalWindows[it].uid in requestedUids }
            if (removedIndices.isEmpty()) return null

            val removal = QueueRemoval(
                source = player,
                originalWindows = originalWindows,
                originalShuffleUids = player.currentTimeline.shuffleUids(),
                removedUids = removedIndices.mapTo(HashSet()) { originalWindows[it].uid },
            )
            removedIndices.asReversed().forEach(player::removeMediaItem)
            return removal
        }

        private fun Timeline.windows(): List<Timeline.Window> =
            List(windowCount) { index -> getWindow(index, Timeline.Window()) }

        private fun Timeline.shuffleUids(): List<Any> = buildList(windowCount) {
            val window = Timeline.Window()
            var index = getFirstWindowIndex(true)
            while (index != C.INDEX_UNSET) {
                add(getWindow(index, window).uid)
                index = getNextWindowIndex(index, Player.REPEAT_MODE_OFF, true)
            }
        }
    }
}
