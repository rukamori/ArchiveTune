/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToLong

internal class LyricsPlaybackClock {
    private var previousSampleNanos = Long.MIN_VALUE
    private var previousSourcePositionMs = 0L
    private var previousSpeed = 1f
    private var wasAdvancing = false
    private var positionMs = 0.0

    fun reset(positionMs: Long): Long {
        val position = positionMs.coerceAtLeast(0L)
        this.positionMs = position.toDouble()
        previousSampleNanos = Long.MIN_VALUE
        previousSourcePositionMs = position
        wasAdvancing = false
        return position
    }

    fun update(
        sourcePositionMs: Long,
        sampleTimeNanos: Long,
        speed: Float,
        isAdvancing: Boolean,
        durationMs: Long,
    ): Long {
        val source = sourcePositionMs.coerceAtLeast(0L)
        val previousTime = previousSampleNanos
        val advancing = isAdvancing && speed.isFinite() && speed > 0f
        if (
            previousTime == Long.MIN_VALUE || sampleTimeNanos <= previousTime ||
            !advancing || !wasAdvancing || speed != previousSpeed ||
            source < previousSourcePositionMs
        ) {
            positionMs = source.toDouble()
        } else {
            val elapsedMs = (sampleTimeNanos - previousTime) / NANOS_PER_MILLISECOND
            val projectedPosition = positionMs + elapsedMs * speed
            positionMs =
                if (source != previousSourcePositionMs) {
                    val errorMs = source - projectedPosition
                    if (abs(errorMs) > MAX_PHASE_ERROR_MS) {
                        source.toDouble()
                    } else {
                        val correction = errorMs * (1.0 - exp(-elapsedMs / CORRECTION_TIME_MS))
                        (projectedPosition + correction).coerceAtLeast(positionMs)
                    }
                } else {
                    projectedPosition
                }
        }
        val maximumPositionMs = if (durationMs > 0L) durationMs.toDouble() else Long.MAX_VALUE.toDouble()
        positionMs = positionMs.coerceIn(0.0, maximumPositionMs)
        previousSampleNanos = sampleTimeNanos
        previousSourcePositionMs = source
        previousSpeed = speed
        wasAdvancing = advancing
        return positionMs.roundToLong()
    }

    private companion object {
        const val NANOS_PER_MILLISECOND = 1_000_000.0
        const val MAX_PHASE_ERROR_MS = 32.0
        const val CORRECTION_TIME_MS = 60.0
    }
}
