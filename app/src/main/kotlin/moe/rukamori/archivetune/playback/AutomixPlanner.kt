/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.db.entities.BeatInfoEntity
import kotlin.math.ceil

/**
 * Computes a beat-aligned crossfade from the cached beat grids of the outgoing and
 * incoming tracks. Pure math, no player state: MusicService feeds it BeatInfoEntity rows
 * and playback positions, and falls back to the plain fixed-window crossfade whenever
 * this returns null.
 */
object AutomixPlanner {
    data class Plan(
        /** Outgoing-track position where the blend starts, snapped to an 8-beat phrase boundary. */
        val triggerTimeMs: Long,
        /** Incoming-track position to start from: first downbeat past its intro. */
        val incomingStartMs: Long,
        /** Speed multiplier that matches the incoming track's tempo to the outgoing one. */
        val tempoRatio: Float,
        /** Blend duration: 16 beats of the outgoing track, bounded to sane wall-clock limits. */
        val overlapMs: Long,
    )

    /** Below this beat-grid confidence a track is treated as unanalyzable. */
    const val MIN_CONFIDENCE = 0.3f

    private const val PHRASE_BEATS = 8
    private const val OVERLAP_BEATS = 16
    private const val MIN_OVERLAP_MS = 6_000L
    private const val MAX_OVERLAP_MS = 16_000L

    /** Minimum room a shrunken blend still needs to be worth beat-matching. */
    private const val MIN_USABLE_OVERLAP_MS = 3_000L
    private const val END_GUARD_MS = 500L

    /** Pitch-preserving time-stretch cap; beyond ±8% the stretch is audible. */
    private const val MIN_TEMPO_RATIO = 0.92f
    private const val MAX_TEMPO_RATIO = 1.08f

    fun plan(
        outgoing: BeatInfoEntity?,
        incoming: BeatInfoEntity?,
        outgoingDurationMs: Long,
        currentPositionMs: Long,
    ): Plan? {
        if (outgoing == null || incoming == null) return null
        if (outgoing.confidence < MIN_CONFIDENCE || incoming.confidence < MIN_CONFIDENCE) return null
        if (outgoing.bpm <= 0f || incoming.bpm <= 0f) return null
        if (outgoingDurationMs <= 0L) return null

        val periodMs = (60_000f / outgoing.bpm).toDouble()
        val overlapMs = (OVERLAP_BEATS * periodMs).toLong().coerceIn(MIN_OVERLAP_MS, MAX_OVERLAP_MS)

        // Dynamic mix-out: start the transition where the song's body ends (outro begins)
        // rather than a fixed distance from the end. -1 sentinel means "scanned, none found".
        val latestTrigger = outgoingDurationMs - overlapMs
        val mixOut = outgoing.mixOutPointMs?.takeIf { it > 0 }
        val effectiveTrigger = mixOut?.coerceAtMost(latestTrigger) ?: latestTrigger

        // Snap the blend start onto an 8-beat phrase boundary of the outgoing grid. Anchor
        // past the current position so late re-planning (pause/seek near the end) still
        // lands on the next musical boundary instead of giving up.
        val phraseMs = periodMs * PHRASE_BEATS
        val anchor = maxOf(effectiveTrigger, currentPositionMs + 1_000)
        val k = ((anchor - outgoing.firstBeatOffsetMs) / phraseMs).toLong()
        var triggerTimeMs = (outgoing.firstBeatOffsetMs + k * phraseMs).toLong()
        if (triggerTimeMs < anchor) triggerTimeMs = (outgoing.firstBeatOffsetMs + (k + 1) * phraseMs).toLong()

        // Phrase-snapping can overshoot the latest viable trigger by up to a phrase. Rather
        // than discarding the plan over a few seconds, shrink the overlap to the room left;
        // only bail when that leaves too little to blend at all.
        val roomMs = outgoingDurationMs - END_GUARD_MS - triggerTimeMs
        val effectiveOverlapMs = overlapMs.coerceAtMost(roomMs)
        if (effectiveOverlapMs < MIN_USABLE_OVERLAP_MS || triggerTimeMs >= outgoingDurationMs - MIN_USABLE_OVERLAP_MS) {
            return null
        }

        // Fold octave errors (e.g. one track analyzed at 80, the other at 160), then cap
        // the pitch-preserving stretch; outside the cap, blend without tempo-matching.
        var tempoRatio = outgoing.bpm / incoming.bpm
        while (tempoRatio > 1.5f) tempoRatio /= 2f
        while (tempoRatio < 0.667f) tempoRatio *= 2f
        if (tempoRatio !in MIN_TEMPO_RATIO..MAX_TEMPO_RATIO) tempoRatio = 1f

        // Dynamic mix-in: skip the incoming track's intro, snapped onto its own phrase grid.
        val incomingPeriodMs = (60_000f / incoming.bpm).toDouble()
        val rawStart = incoming.mixInPointMs?.takeIf { it > 0 } ?: incoming.firstBeatOffsetMs
        val incomingPhraseMs = incomingPeriodMs * PHRASE_BEATS
        val incomingK =
            ceil((rawStart - incoming.firstBeatOffsetMs) / incomingPhraseMs)
                .toLong()
                .coerceAtLeast(0L)
        val incomingStartMs = (incoming.firstBeatOffsetMs + incomingK * incomingPhraseMs).toLong()

        return Plan(
            triggerTimeMs = triggerTimeMs,
            incomingStartMs = incomingStartMs,
            tempoRatio = tempoRatio,
            overlapMs = effectiveOverlapMs,
        )
    }

    /**
     * Optional tempo envelope for the incoming player. Quantized steps avoid constant Sonic
     * resampler reconfiguration during the fade.
     */
    fun tempoRatioAt(
        plan: Plan,
        progress: Float,
        steps: Int = 6,
    ): Float {
        if (plan.tempoRatio == 1f) return 1f
        val clamped = progress.coerceIn(0f, 1f)
        val t =
            when {
                clamped < RAMP_IN_END -> clamped / RAMP_IN_END
                clamped <= RAMP_OUT_START -> 1f
                else -> 1f - (clamped - RAMP_OUT_START) / (1f - RAMP_OUT_START)
            }
        val quantized = (t * steps).toInt().coerceIn(0, steps) / steps.toFloat()
        return 1f + (plan.tempoRatio - 1f) * quantized
    }

    private const val RAMP_IN_END = 0.35f
    private const val RAMP_OUT_START = 0.65f
}
