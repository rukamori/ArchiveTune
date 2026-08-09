/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeHandoffPolicyTest {
    @Test
    fun correctiveSeek_isSkipped_whenDriftIsWithinTolerance() {
        assertFalse(
            needsCorrectiveCrossfadeSeek(
                primaryPositionMs = 10_040L,
                secondaryPositionMs = 10_000L,
                maximumDriftMs = 75L,
            ),
        )
        assertFalse(needsCorrectiveCrossfadeSeek(10_075L, 10_000L, maximumDriftMs = 75L))
    }

    @Test
    fun correctiveSeek_isRequired_whenDriftExceedsTolerance_inEitherDirection() {
        assertTrue(needsCorrectiveCrossfadeSeek(9_900L, 10_000L, maximumDriftMs = 75L))
        assertTrue(needsCorrectiveCrossfadeSeek(10_100L, 10_000L, maximumDriftMs = 75L))
    }

    @Test
    fun playbackPositionMustMoveForward_afterSeek() {
        assertFalse(
            hasPlaybackPositionAdvanced(positionAfterSeekMs = 10_000L, currentPositionMs = 10_000L),
        )
        assertFalse(
            hasPlaybackPositionAdvanced(positionAfterSeekMs = 10_000L, currentPositionMs = 9_999L),
        )
        assertTrue(
            hasPlaybackPositionAdvanced(positionAfterSeekMs = 10_000L, currentPositionMs = 10_001L),
        )
    }

    @Test
    fun equalPowerHandoff_preservesPowerAtEndpointsAndMidpoint() {
        val start = equalPowerGains(0f)
        val midpoint = equalPowerGains(0.5f)
        val end = equalPowerGains(1f)

        assertEquals(1f, start.outgoing, 0.0001f)
        assertEquals(0f, start.incoming, 0.0001f)
        assertEquals(1f, end.incoming, 0.0001f)
        assertEquals(0f, end.outgoing, 0.0001f)
        assertEquals(
            1f,
            midpoint.outgoing * midpoint.outgoing + midpoint.incoming * midpoint.incoming,
            0.0001f,
        )
    }
}
