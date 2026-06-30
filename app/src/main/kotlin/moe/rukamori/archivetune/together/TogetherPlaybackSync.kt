/*
 * ArchiveTune (2026)
 * (c) Rukamori - github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.together

import kotlin.math.abs

internal object TogetherPlaybackSync {
    const val BroadcastIntervalMs = 500L

    private const val OnlineEstimatedDeliveryMs = 350L
    private const val MaxRemoteAgeMs = 5_000L
    private const val LanPlayingSeekThresholdMs = 700L
    private const val OnlinePlayingSeekThresholdMs = 1_200L
    private const val PausedSeekThresholdMs = 150L
    private const val EchoSuppressionMs = 700L

    fun isStaleRoomState(
        sentAtElapsedRealtimeMs: Long,
        lastAppliedSentAtElapsedRealtimeMs: Long,
        force: Boolean,
    ): Boolean =
        !force &&
            sentAtElapsedRealtimeMs > 0L &&
            lastAppliedSentAtElapsedRealtimeMs > 0L &&
            sentAtElapsedRealtimeMs <= lastAppliedSentAtElapsedRealtimeMs

    fun echoSuppressionUntil(nowElapsedRealtimeMs: Long): Long = nowElapsedRealtimeMs + EchoSuppressionMs

    fun targetPositionMs(
        state: TogetherRoomState,
        isOnlineSession: Boolean,
        clockSnapshot: TogetherClockSnapshot?,
        nowElapsedRealtimeMs: Long,
    ): Long {
        val basePosition = state.positionMs.coerceAtLeast(0L)
        if (!state.isPlaying) return basePosition

        val deliveryAgeMs =
            if (isOnlineSession) {
                OnlineEstimatedDeliveryMs
            } else {
                val correctedSentAt =
                    state.sentAtElapsedRealtimeMs +
                        (clockSnapshot?.estimatedOffsetMs ?: 0L)
                (nowElapsedRealtimeMs - correctedSentAt).coerceIn(0L, MaxRemoteAgeMs)
            }

        return (basePosition + deliveryAgeMs).coerceAtLeast(0L)
    }

    fun needsQueueRebuild(
        desiredHash: String,
        desiredIds: List<String>,
        localHash: String,
        localIds: List<String>,
    ): Boolean =
        desiredIds.isNotEmpty() &&
            if (desiredHash.isNotBlank()) {
                desiredHash != localHash
            } else {
                desiredIds != localIds
            }

    fun shouldSeekForDrift(
        currentPositionMs: Long,
        targetPositionMs: Long,
        isPlaying: Boolean,
        isOnlineSession: Boolean,
    ): Boolean {
        val threshold =
            when {
                !isPlaying -> PausedSeekThresholdMs
                isOnlineSession -> OnlinePlayingSeekThresholdMs
                else -> LanPlayingSeekThresholdMs
            }
        return abs(currentPositionMs - targetPositionMs) > threshold
    }
}
