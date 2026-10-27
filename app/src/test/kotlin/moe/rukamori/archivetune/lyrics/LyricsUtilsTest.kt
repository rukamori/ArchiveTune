/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class LyricsUtilsTest {
    @Test
    fun parseLyricsPreservesLyricsWhenPrecededByEmptyLineAtSameTimestamp() {
        val lrc =
            """
            [00:16.50] E ho bisogno di amici che mi facciano stare bene
            [00:21.50]
            [00:21.50] Respiravo una cosa
            [00:24.30] Una cosa pericolosa
            """.trimIndent()

        val parsed = LyricsUtils.parseLyrics(lrc)
        assertEquals(3, parsed.size)
        assertEquals(16500L, parsed[0].time)
        assertEquals("E ho bisogno di amici che mi facciano stare bene", parsed[0].text)
        assertEquals(21500L, parsed[1].time)
        assertEquals("Respiravo una cosa", parsed[1].text)
        assertEquals(24300L, parsed[2].time)
        assertEquals("Una cosa pericolosa", parsed[2].text)
    }

    @Test
    fun parseLyricsIgnoresTrailingEmptyLineAtSameTimestamp() {
        val lrc =
            """
            [00:21.50] Respiravo una cosa
            [00:21.50]
            """.trimIndent()

        val parsed = LyricsUtils.parseLyrics(lrc)
        assertEquals(1, parsed.size)
        assertEquals(21500L, parsed[0].time)
        assertEquals("Respiravo una cosa", parsed[0].text)
    }

    @Test
    fun parseLyricsMergesDuplicateTimestampTranslation() {
        val lrc =
            """
            [00:10.00] Original English
            [00:10.00] Spanish Translation
            """.trimIndent()

        val parsed = LyricsUtils.parseLyrics(lrc)
        assertEquals(1, parsed.size)
        assertEquals(10000L, parsed[0].time)
        assertEquals("Original English", parsed[0].text)
        assertEquals("Spanish Translation", parsed[0].providerTranslationText)
    }
}
