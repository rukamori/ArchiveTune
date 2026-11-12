/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

internal data class EqualPowerGains(
    val outgoing: Float,
    val incoming: Float,
)

internal fun needsForwardCorrectiveCrossfadeSeek(
    primaryPositionMs: Long,
    secondaryPositionMs: Long,
    maximumDriftMs: Long,
): Boolean {
    require(maximumDriftMs >= 0L)
    return secondaryPositionMs > primaryPositionMs &&
        secondaryPositionMs - primaryPositionMs > maximumDriftMs
}

internal fun hasPlaybackPositionAdvanced(
    positionAfterSeekMs: Long,
    currentPositionMs: Long,
): Boolean = currentPositionMs > positionAfterSeekMs

internal fun bothCrossfadePlayersAdvanced(
    primaryIsPlaying: Boolean,
    incomingIsPlaying: Boolean,
    previousPrimaryPositionMs: Long,
    currentPrimaryPositionMs: Long,
    previousIncomingPositionMs: Long,
    currentIncomingPositionMs: Long,
): Boolean =
    primaryIsPlaying &&
        incomingIsPlaying &&
        hasPlaybackPositionAdvanced(previousPrimaryPositionMs, currentPrimaryPositionMs) &&
        hasPlaybackPositionAdvanced(previousIncomingPositionMs, currentIncomingPositionMs)

internal fun shouldSeekToCrossfadeTargetOnAbort(
    primaryAtEnd: Boolean,
    targetAvailable: Boolean,
    primaryAlreadyAtTarget: Boolean,
    repeatOne: Boolean,
): Boolean =
    primaryAtEnd && targetAvailable && (!primaryAlreadyAtTarget || repeatOne)

internal fun equalPowerGains(progress: Float): EqualPowerGains {
    val clampedProgress = progress.coerceIn(0f, 1f)
    val radians = clampedProgress.toDouble() * (PI / 2.0)
    return EqualPowerGains(
        outgoing = cos(radians).toFloat(),
        incoming = sin(radians).toFloat(),
    )
}
