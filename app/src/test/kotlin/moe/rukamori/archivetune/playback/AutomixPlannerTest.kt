/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.db.entities.BeatInfoEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class AutomixPlannerTest {
    private fun beatInfo(
        bpm: Float,
        firstBeatOffsetMs: Long = 0L,
        confidence: Float = 0.8f,
        mixInPointMs: Long? = null,
        mixOutPointMs: Long? = null,
    ) = BeatInfoEntity(
        id = "song-$bpm-$firstBeatOffsetMs",
        bpm = bpm,
        firstBeatOffsetMs = firstBeatOffsetMs,
        confidence = confidence,
        mixInPointMs = mixInPointMs,
        mixOutPointMs = mixOutPointMs,
    )

    @Test
    fun snapsTriggerOntoOutgoingPhraseGrid() {
        // 120 BPM: beat 500ms, phrase 4000ms. Duration 200s, overlap 16 beats = 8s.
        val plan =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f, firstBeatOffsetMs = 250L),
                incoming = beatInfo(bpm = 120f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )

        assertNotNull(plan)
        // Trigger must be a phrase boundary: 250 + k * 4000.
        assertEquals(250L, (plan!!.triggerTimeMs - 250L) % 4_000L + 250L)
        // And at/after the latest full-overlap point (192s) snapped up.
        assertTrue(plan.triggerTimeMs >= 192_000L)
        // Snapping may overshoot slightly; the overlap shrinks to keep the blend inside the track.
        assertTrue(plan.overlapMs in 6_000L..8_000L)
        assertTrue(plan.triggerTimeMs + plan.overlapMs <= 200_000L - 500L)
    }

    @Test
    fun respectsMixOutPoint() {
        val plan =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f, mixOutPointMs = 150_000L),
                incoming = beatInfo(bpm = 120f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )

        assertNotNull(plan)
        // Blend starts at the outro (snapped up to the next phrase), not near the track end.
        assertTrue(plan!!.triggerTimeMs in 150_000L..154_000L)
    }

    @Test
    fun ignoresMixOutSentinel() {
        val plan =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f, mixOutPointMs = -1L),
                incoming = beatInfo(bpm = 120f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )

        assertNotNull(plan)
        assertTrue(plan!!.triggerTimeMs >= 192_000L)
    }

    @Test
    fun matchesCloseTemposAndFoldsOctaves() {
        // 124 vs 120: ratio 1.0333 (within ±8%).
        val close =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 124f),
                incoming = beatInfo(bpm = 120f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )
        assertNotNull(close)
        assertEquals(124f / 120f, close!!.tempoRatio, 0.001f)

        // 80 vs 156: raw ratio 0.513 folds to 1.026.
        val octave =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 80f),
                incoming = beatInfo(bpm = 156f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )
        assertNotNull(octave)
        assertEquals(160f / 156f, octave!!.tempoRatio, 0.001f)
    }

    @Test
    fun skipsTempoMatchOutsideStretchCap() {
        // 120 vs 100: ratio 1.2 — audible stretch, blend without tempo-matching.
        val plan =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f),
                incoming = beatInfo(bpm = 100f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )

        assertNotNull(plan)
        assertEquals(1f, plan!!.tempoRatio, 0f)
    }

    @Test
    fun incomingStartsOnDownbeatPastIntro() {
        // Incoming: 120 BPM, grid offset 100ms, intro ends at 6100ms.
        // Phrase grid: 100 + k*4000 -> first boundary at/after 6100 is 8100.
        val plan =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f),
                incoming = beatInfo(bpm = 120f, firstBeatOffsetMs = 100L, mixInPointMs = 6_100L),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )

        assertNotNull(plan)
        assertEquals(8_100L, plan!!.incomingStartMs)
    }

    @Test
    fun fallsBackOnMissingOrWeakAnalysis() {
        val good = beatInfo(bpm = 120f)

        assertNull(AutomixPlanner.plan(null, good, 200_000L, 0L))
        assertNull(AutomixPlanner.plan(good, null, 200_000L, 0L))
        assertNull(AutomixPlanner.plan(beatInfo(bpm = 120f, confidence = 0.1f), good, 200_000L, 0L))
        // Negative-cache row: analyzed, no usable grid.
        assertNull(AutomixPlanner.plan(beatInfo(bpm = 0f), good, 200_000L, 0L))
    }

    @Test
    fun fallsBackWhenNoRoomLeft() {
        // Already 3s from the end: nothing left to blend.
        assertNull(
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f),
                incoming = beatInfo(bpm = 120f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 197_500L,
            ),
        )
    }

    @Test
    fun shrinksOverlapWhenPhraseSnapOvershoots() {
        // Late anchor: phrase snap lands close to the end, overlap shrinks but stays usable.
        val plan =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f),
                incoming = beatInfo(bpm = 120f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 193_500L,
            )

        assertNotNull(plan)
        assertTrue(plan!!.overlapMs >= 3_000L)
        assertTrue(plan.triggerTimeMs + plan.overlapMs <= 200_000L - 500L)
    }

    @Test
    fun tempoRampRisesHoldsAndReturnsToUnity() {
        val plan =
            AutomixPlanner.Plan(
                triggerTimeMs = 0L,
                incomingStartMs = 0L,
                tempoRatio = 1.06f,
                overlapMs = 10_000L,
            )

        assertEquals(1f, AutomixPlanner.tempoRatioAt(plan, 0f), 0.001f)
        assertEquals(1.06f, AutomixPlanner.tempoRatioAt(plan, 0.5f), 0.001f)
        assertEquals(1f, AutomixPlanner.tempoRatioAt(plan, 1f), 0.001f)

        // Monotone rise through the ramp-in window.
        var last = 0f
        for (i in 0..10) {
            val ratio = AutomixPlanner.tempoRatioAt(plan, 0.035f * i)
            assertTrue(ratio >= last - 0.0001f)
            last = ratio
        }

        // Fine-grained by design: a coarse staircase clicks on-device.
        val distinct = (0..100).map { AutomixPlanner.tempoRatioAt(plan, it / 100f) }.distinct()
        assertTrue("expected fine-grained ramp, got only ${distinct.size} distinct values", distinct.size >= 25)
    }

    @Test
    fun tempoRampIsIdentityWhenNotMatching() {
        val plan =
            AutomixPlanner.Plan(
                triggerTimeMs = 0L,
                incomingStartMs = 0L,
                tempoRatio = 1f,
                overlapMs = 10_000L,
            )

        for (i in 0..10) {
            assertTrue(abs(AutomixPlanner.tempoRatioAt(plan, i / 10f) - 1f) < 0.0001f)
        }
    }
}
