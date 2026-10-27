/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.extensions

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class QueueUndoTest {
    @Test
    fun buildRestoredShuffleOrderIndices_preservesQueueSequence() {
        val originalShuffleOrder = listOf(4, 1, 3, 0, 2)
        val restored = buildRestoredShuffleOrderIndices(originalShuffleOrder)

        assertArrayEquals(intArrayOf(4, 1, 3, 0, 2), restored)
        assertEquals(5, restored.size)
    }

    @Test
    fun buildRestoredShuffleOrderIndices_handlesEmptyList() {
        val restored = buildRestoredShuffleOrderIndices(emptyList())

        assertArrayEquals(intArrayOf(), restored)
        assertEquals(0, restored.size)
    }

    @Test
    fun queueUndo_restoresSingleItemOrder_withoutReShuffling() {
        // Original playback queue order: tracks with timeline indices [3, 0, 2, 1]
        val originalQueueTimelineIndices = listOf(3, 0, 2, 1)

        // User removes track at queue index 1 (timeline index 0)
        val removedTimelineIndex = 0
        val remainingQueue = originalQueueTimelineIndices.filter { it != removedTimelineIndex }
        assertEquals(listOf(3, 2, 1), remainingQueue)

        // When undo is clicked, the restored shuffle order must match originalQueueTimelineIndices exactly
        val restoredOrder = buildRestoredShuffleOrderIndices(originalQueueTimelineIndices)
        assertArrayEquals(intArrayOf(3, 0, 2, 1), restoredOrder)
    }

    @Test
    fun queueUndo_restoresMultipleItemsOrder_withoutReShuffling() {
        val originalQueueTimelineIndices = listOf(5, 2, 0, 4, 1, 3)

        // User removes tracks with timeline indices 2 and 4
        val removedIndices = setOf(2, 4)
        val remainingQueue = originalQueueTimelineIndices.filter { it !in removedIndices }
        assertEquals(listOf(5, 0, 1, 3), remainingQueue)

        // When undo is clicked, original sequence is restored
        val restoredOrder = buildRestoredShuffleOrderIndices(originalQueueTimelineIndices)
        assertArrayEquals(intArrayOf(5, 2, 0, 4, 1, 3), restoredOrder)
    }
}
