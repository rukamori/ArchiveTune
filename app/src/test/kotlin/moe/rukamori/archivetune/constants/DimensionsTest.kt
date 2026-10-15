/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.constants

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Test

class DimensionsTest {
    @Test
    fun calculateSquareThumbnailSize_inPortrait_isConstrainedByWidth() {
        val width = 400.dp
        val height = 800.dp
        val size = calculateSquareThumbnailSize(width, height)
        // width (400) - padding (32*2 = 64) = 336.dp
        assertEquals(336.dp, size)
    }

    @Test
    fun calculateSquareThumbnailSize_inLandscapeOrExtendedPlayer_isConstrainedByHeight() {
        // Landscape / Tablet form factor: width is larger than height
        val width = 1000.dp
        val height = 450.dp
        val size = calculateSquareThumbnailSize(width, height)
        // height (450) - padding (32*2 = 64) = 386.dp
        assertEquals(386.dp, size)
    }

    @Test
    fun calculateSquareThumbnailSize_neverNegative() {
        val width = 40.dp
        val height = 40.dp
        val size = calculateSquareThumbnailSize(width, height)
        // 40 - 64 is negative, should coerce to 0.dp
        assertEquals(0.dp, size)
    }
}
