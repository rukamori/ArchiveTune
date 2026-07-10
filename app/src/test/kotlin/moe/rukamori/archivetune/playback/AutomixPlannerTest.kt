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
        keyIndex: Int? = null,
        keyConfidence: Float? = null,
        bassFraction: Float? = null,
        midPeakHz: Float? = null,
    ) = BeatInfoEntity(
        id = "song-$bpm-$firstBeatOffsetMs",
        bpm = bpm,
        firstBeatOffsetMs = firstBeatOffsetMs,
        confidence = confidence,
        mixInPointMs = mixInPointMs,
        mixOutPointMs = mixOutPointMs,
        keyIndex = keyIndex,
        keyConfidence = keyConfidence,
        bassFraction = bassFraction,
        midPeakHz = midPeakHz,
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

    // Key indices: 0-11 major C..B, 12-23 minor C..B.
    private val cMajor = 0
    private val gMajor = 7
    private val fSharpMajor = 6
    private val aMinor = 21

    @Test
    fun keysCompatibleForSameRelativeAndFifth() {
        val c = beatInfo(bpm = 120f, keyIndex = cMajor, keyConfidence = 0.3f)
        assertEquals(true, AutomixPlanner.keysCompatible(c, beatInfo(bpm = 121f, keyIndex = cMajor, keyConfidence = 0.3f)))
        // Relative minor shares the Camelot number.
        assertEquals(true, AutomixPlanner.keysCompatible(c, beatInfo(bpm = 121f, keyIndex = aMinor, keyConfidence = 0.3f)))
        // A fifth apart, same mode: one step on the wheel.
        assertEquals(true, AutomixPlanner.keysCompatible(c, beatInfo(bpm = 121f, keyIndex = gMajor, keyConfidence = 0.3f)))
    }

    @Test
    fun keysClashAcrossTheWheel() {
        val c = beatInfo(bpm = 120f, keyIndex = cMajor, keyConfidence = 0.3f)
        // Tritone apart: opposite side of the wheel.
        assertEquals(false, AutomixPlanner.keysCompatible(c, beatInfo(bpm = 121f, keyIndex = fSharpMajor, keyConfidence = 0.3f)))
    }

    @Test
    fun unknownOrWeakKeyIsNotAClash() {
        val c = beatInfo(bpm = 120f, keyIndex = cMajor, keyConfidence = 0.3f)
        assertNull(AutomixPlanner.keysCompatible(c, beatInfo(bpm = 121f)))
        assertNull(AutomixPlanner.keysCompatible(c, beatInfo(bpm = 121f, keyIndex = fSharpMajor, keyConfidence = 0.01f)))
    }

    @Test
    fun keyClashShortensOverlap() {
        val compatible =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f, keyIndex = cMajor, keyConfidence = 0.3f),
                incoming = beatInfo(bpm = 120f, keyIndex = gMajor, keyConfidence = 0.3f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )
        val clashing =
            AutomixPlanner.plan(
                outgoing = beatInfo(bpm = 120f, keyIndex = cMajor, keyConfidence = 0.3f),
                incoming = beatInfo(bpm = 120f, keyIndex = fSharpMajor, keyConfidence = 0.3f),
                outgoingDurationMs = 200_000L,
                currentPositionMs = 0L,
            )

        assertNotNull(compatible)
        assertNotNull(clashing)
        assertEquals(true, compatible!!.keysCompatible)
        assertEquals(false, clashing!!.keysCompatible)
        // 16 beats at 120 BPM = 8s compatible; clash halves to 8 beats = 4s (minus end shrink).
        assertTrue("clash overlap ${clashing.overlapMs} should be well under ${compatible.overlapMs}", clashing.overlapMs <= 4_000L)
        assertTrue(compatible.overlapMs > clashing.overlapMs)
    }

    @Test
    fun duckShelfDeepensWithBassEnergy() {
        assertEquals(AutomixPlanner.DEFAULT_DUCK_SHELF_DB, AutomixPlanner.duckShelfDbFor(null, null), 0.001f)
        assertEquals(-6f, AutomixPlanner.duckShelfDbFor(0.1f, 0.15f), 0.3f)
        assertEquals(-14f, AutomixPlanner.duckShelfDbFor(0.5f, 0.6f), 0.001f)
    }

    @Test
    fun duckMidCentersOnPresencePeaks() {
        assertEquals(AutomixPlanner.DEFAULT_DUCK_MID_HZ, AutomixPlanner.duckMidHzFor(null, null), 0.001f)
        // Geometric mean of the pair.
        assertEquals(1_200f, AutomixPlanner.duckMidHzFor(800f, 1_800f), 1f)
        // Clamped into the presence band.
        assertEquals(4_000f, AutomixPlanner.duckMidHzFor(6_000f, 6_000f), 0.001f)
    }
}
