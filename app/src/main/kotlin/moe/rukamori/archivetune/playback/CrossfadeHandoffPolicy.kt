/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import moe.rukamori.archivetune.constants.DjTransitionStyle
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

internal data class EqualPowerGains(
    val outgoing: Float,
    val incoming: Float,
)

internal fun needsCorrectiveCrossfadeSeek(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    maximumDriftMs: Long,
): Boolean {
    require(maximumDriftMs >= 0L)
    return abs(primaryPositionMs - secondaryPositionMs) > maximumDriftMs
}

internal fun hasPlaybackPositionAdvanced(
    positionAfterSeekMs: Long,
    currentPositionMs: Long,
): Boolean = currentPositionMs > positionAfterSeekMs

internal fun equalPowerGains(progress: Float): EqualPowerGains {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val radians = clampedProgress.toDouble() * (PI / 2.0)
    return EqualPowerGains(
        outgoing = cos(radians).toFloat(),
        incoming = sin(radians).toFloat(),
    )
}

internal fun smoothDjEqualPowerGains(progress: Float): EqualPowerGains {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val smoothProgress = clampedProgress * clampedProgress * (3f - 2f * clampedProgress)
    return equalPowerGains(smoothProgress)
}

internal fun punchyDjEqualPowerGains(progress: Float): EqualPowerGains {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val outgoingWeight = (1f - clampedProgress).toDouble().pow(2.0)
    val incomingWeight = clampedProgress.toDouble().pow(2.0)
    val shapedProgress =
        if (outgoingWeight + incomingWeight == 0.0) {
            clampedProgress
        } else {
            (incomingWeight / (outgoingWeight + incomingWeight)).toFloat()
        }
    return equalPowerGains(shapedProgress)
}

internal fun djTransitionGains(
    progress: Float,
    style: DjTransitionStyle,
): EqualPowerGains =
    when (style) {
        DjTransitionStyle.CLASSIC -> equalPowerGains(progress)
        DjTransitionStyle.SMOOTH -> smoothDjEqualPowerGains(progress)
        DjTransitionStyle.PUNCHY -> punchyDjEqualPowerGains(progress)
    }

internal fun adaptiveDjCrossfadeDuration(
    configuredDurationMs: Long,
    outgoingDurationMs: Long,
    minimumDurationMs: Long,
    endGuardMs: Long,
    maximumTrackOverlapRatio: Double,
): Long? {
    require(configuredDurationMs >= 0L)
    require(minimumDurationMs > 0L)
    require(endGuardMs >= 0L)
    require(maximumTrackOverlapRatio > 0.0 && maximumTrackOverlapRatio <= 1.0)

    val maximumDurationMs = outgoingDurationMs - endGuardMs
    if (maximumDurationMs < minimumDurationMs) return null

    val shortTrackLimitMs = (outgoingDurationMs * maximumTrackOverlapRatio).toLong()
    return configuredDurationMs
        .coerceAtLeast(minimumDurationMs)
        .coerceAtMost(shortTrackLimitMs.coerceAtLeast(minimumDurationMs))
        .coerceAtMost(maximumDurationMs)
}
