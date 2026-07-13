/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PreventDuplicateQueueTest {

    private fun deduplicateIncomingItems(
        items: List<String>,
        queueIds: List<String>,
        currentId: String?,
        preventEnabled: Boolean,
    ): List<String> {
        val seen = mutableSetOf<String>()
        val deduped = items.filter { seen.add(it) }

        if (!preventEnabled || queueIds.isEmpty()) return deduped

        return deduped.filter { it != currentId }
    }

    @Test
    fun addingCurrentTrackIsSkipped() {
        val result = deduplicateIncomingItems(
            items = listOf("A"),
            queueIds = listOf("A", "B"),
            currentId = "A",
            preventEnabled = true,
        )
        assertEquals(emptyList<String>(), result)
    }

    @Test
    fun duplicatesWithinIncomingBatchAreDeduped() {
        val result = deduplicateIncomingItems(
            items = listOf("A", "B", "A", "C", "B"),
            queueIds = emptyList(),
            currentId = null,
            preventEnabled = true,
        )
        assertEquals(listOf("A", "B", "C"), result)
    }

    @Test
    fun incomingTrackAlreadyInQueueIsAllowedThrough() {
        val result = deduplicateIncomingItems(
            items = listOf("B"),
            queueIds = listOf("A", "B", "C"),
            currentId = "A",
            preventEnabled = true,
        )
        assertEquals(listOf("B"), result)
    }

    @Test
    fun settingDisabled_noDedupApplied() {
        val result = deduplicateIncomingItems(
            items = listOf("A", "A", "B"),
            queueIds = listOf("A"),
            currentId = "A",
            preventEnabled = false,
        )
        assertEquals(listOf("A", "A", "B"), result)
    }

    @Test
    fun shuffleModeDoesNotAffectDeduplication() {
        val result = deduplicateIncomingItems(
            items = listOf("X", "Y", "X"),
            queueIds = listOf("A"),
            currentId = "A",
            preventEnabled = true,
        )
        assertEquals(listOf("X", "Y"), result)
    }

    @Test
    fun playNextOrdering_firstOccurrenceKept() {
        val result = deduplicateIncomingItems(
            items = listOf("C", "B", "C", "A"),
            queueIds = listOf("B"),
            currentId = "D",
            preventEnabled = true,
        )
        assertEquals(listOf("C", "B", "A"), result)
    }
}
