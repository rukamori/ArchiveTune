/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class BeatAnalyzerTest {
    private val sampleRate = 44100

    /**
     * Synthesizes a percussive click track: short decaying noise bursts at every beat,
     * over quiet noise floor. Deterministic via seeded Random.
     */
    private fun clickTrack(
        bpm: Float,
        seconds: Int,
        firstClickMs: Long = 0L,
    ): FloatArray {
        val samples = FloatArray(sampleRate * seconds)
        val random = Random(42)
        for (i in samples.indices) {
            samples[i] = (random.nextFloat() - 0.5f) * 0.005f
        }
        val periodSamples = (sampleRate * 60f / bpm).toInt()
        val burstLength = sampleRate * 30 / 1000
        var clickStart = (firstClickMs * sampleRate / 1000).toInt()
        while (clickStart < samples.size) {
            for (i in 0 until burstLength) {
                val index = clickStart + i
                if (index >= samples.size) break
                val envelope = exp(-6f * i / burstLength)
                val tone = sin(2.0 * Math.PI * 1500.0 * i / sampleRate).toFloat()
                samples[index] += envelope * (0.6f * tone + 0.4f * (random.nextFloat() - 0.5f))
            }
            clickStart += periodSamples
        }
        return samples
    }

    @Test
    fun detectsTempoOfSteadyClickTrack() {
        val grid = BeatAnalyzer.analyzeBeatGrid(clickTrack(bpm = 120f, seconds = 18), sampleRate, windowStartMs = 0L)

        assertNotNull(grid)
        assertEquals(120f, grid!!.bpm, 2f)
        assertTrue("confidence should be high for a clean click track, was ${grid.confidence}", grid.confidence > 0.3f)
    }

    @Test
    fun foldsTempoIntoCanonicalRange() {
        // 160 BPM folds to 80 BPM; the beat grid stays phase-compatible.
        val grid = BeatAnalyzer.analyzeBeatGrid(clickTrack(bpm = 160f, seconds = 18), sampleRate, windowStartMs = 0L)

        assertNotNull(grid)
        assertEquals(80f, grid!!.bpm, 2f)
    }

    @Test
    fun beatOffsetMatchesFirstClick() {
        val grid =
            BeatAnalyzer.analyzeBeatGrid(
                clickTrack(bpm = 120f, seconds = 18, firstClickMs = 250L),
                sampleRate,
                windowStartMs = 0L,
            )

        assertNotNull(grid)
        // At 120 BPM the period is 500ms; the grid should anchor near 250ms (mod 500).
        val period = 60_000f / grid!!.bpm
        val distance = abs(grid.firstBeatOffsetMs - 250L).toFloat().mod(period)
        val wrapped = minOf(distance, period - distance)
        assertTrue("beat phase off by ${wrapped}ms", wrapped < 60f)
    }

    @Test
    fun clickTrackScoresWellAboveBeatlessNoise() {
        val random = Random(7)
        val noise = FloatArray(sampleRate * 18) { (random.nextFloat() - 0.5f) * 0.2f }

        val noiseGrid = BeatAnalyzer.analyzeBeatGrid(noise, sampleRate, windowStartMs = 0L)
        val clickGrid = BeatAnalyzer.analyzeBeatGrid(clickTrack(bpm = 120f, seconds = 18), sampleRate, windowStartMs = 0L)

        // Autocorrelation may pick a chance peak in noise, but a real pulse must
        // clearly outrank it — this margin is what the planner's threshold leans on.
        assertNotNull(clickGrid)
        val noiseConfidence = noiseGrid?.confidence ?: 0f
        assertTrue(
            "click confidence ${clickGrid!!.confidence} should clearly exceed noise confidence $noiseConfidence",
            clickGrid.confidence > noiseConfidence + 0.2f,
        )
    }

    @Test
    fun detectMixInSkipsQuietIntro() {
        // 6s quiet intro then loud body; 500ms blocks.
        val envelope = FloatArray(32) { if (it < 12) 0.02f else 1f }

        val mixIn = BeatAnalyzer.detectMixIn(envelope, firstBeatOffsetMs = 100L, periodMs = 500f)

        assertNotNull(mixIn)
        // Intro ends at block 12 = 6000ms; snapped forward onto the grid (100 + k*500).
        assertEquals(6100L, mixIn)
    }

    @Test
    fun detectMixInReturnsNullWhenTrackStartsHot() {
        val envelope = FloatArray(32) { 1f }

        assertNull(BeatAnalyzer.detectMixIn(envelope, firstBeatOffsetMs = 0L, periodMs = 500f))
    }

    @Test
    fun detectMixOutFindsOutroStart() {
        // Loud body for 10 blocks, then a quiet outro; window covers the last 16s.
        val envelope = FloatArray(32) { if (it < 10) 1f else 0.02f }
        val durationMs = 200_000L
        val windowStartMs = durationMs - 16_000L

        val mixOut = BeatAnalyzer.detectMixOut(envelope, windowStartMs, durationMs)

        assertNotNull(mixOut)
        // Last loud block is 9; the body ends at windowStart + 10 blocks * 500ms.
        assertEquals(windowStartMs + 5_000L, mixOut)
    }

    @Test
    fun detectMixOutReturnsNullWhenLoudToTheEnd() {
        val envelope = FloatArray(32) { 1f }
        val durationMs = 200_000L

        assertNull(BeatAnalyzer.detectMixOut(envelope, durationMs - 16_000L, durationMs))
    }
}
