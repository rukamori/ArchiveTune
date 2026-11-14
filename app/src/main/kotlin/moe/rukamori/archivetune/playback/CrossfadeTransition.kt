/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToLong
import kotlin.math.sin

internal interface CrossfadeDeck {
    val isPlaying: Boolean
    val positionMs: Long
    var volume: Float
}

internal interface CrossfadeSessionDeck : CrossfadeDeck {
    val currentMediaId: String?
    val playWhenReady: Boolean
    val playbackSpeed: Float

    fun switchTo(mediaId: String): Boolean
}

internal interface CrossfadeTailDeck : CrossfadeDeck {
    val isReady: Boolean
    val isEnded: Boolean
    val hasFailed: Boolean
    val durationMs: Long

    fun setPlayWhenReady(playWhenReady: Boolean)

    fun seekTo(positionMs: Long)
}

internal data class CrossfadeTuning(
    val prepareAheadMs: Long = 30_000L,
    val syncWindowMs: Long = 8_000L,
    val incomingStartAllowanceMs: Long = 300L,
    val minFadeMs: Long = 500L,
    val prerollLeadMs: Long = 1_000L,
    val prerollMarginMs: Long = 250L,
    val initialStartLatencyMs: Long = 60L,
    val settleMs: Long = 600L,
    val measureWindowMs: Long = 240L,
    val syncToleranceMs: Long = 15L,
    val holdToleranceMs: Long = 30L,
    val holdSampleCount: Int = 8,
    val switchBlendMs: Long = 64L,
    val switchFrameMs: Long = 8L,
    val activeFrameMs: Long = 16L,
    val idleFrameMs: Long = 250L,
)

internal data class CrossfadeTimeline(
    val prepareAtPositionMs: Long,
    val syncAtPositionMs: Long,
    val switchAtPositionMs: Long,
    val latestSwitchPositionMs: Long,
    val fadeDurationMs: Long,
)

internal fun crossfadeTimeline(
    outgoingDurationMs: Long,
    requestedFadeMs: Long,
    tuning: CrossfadeTuning = CrossfadeTuning(),
): CrossfadeTimeline? {
    if (outgoingDurationMs <= 0L || requestedFadeMs <= 0L) return null
    val latestSwitch = outgoingDurationMs - tuning.minFadeMs - tuning.incomingStartAllowanceMs
    if (latestSwitch <= 0L) return null
    val fade = requestedFadeMs.coerceAtLeast(tuning.minFadeMs).coerceAtMost(latestSwitch + tuning.minFadeMs)
    val switchAt = (outgoingDurationMs - fade - tuning.incomingStartAllowanceMs).coerceAtLeast(0L)
    return CrossfadeTimeline(
        prepareAtPositionMs = (switchAt - tuning.prepareAheadMs).coerceAtLeast(0L),
        syncAtPositionMs = (switchAt - tuning.syncWindowMs).coerceAtLeast(0L),
        switchAtPositionMs = switchAt,
        latestSwitchPositionMs = latestSwitch,
        fadeDurationMs = fade,
    )
}

internal data class EqualPowerGains(
    val outgoing: Float,
    val incoming: Float,
)

internal fun equalPowerGains(progress: Float): EqualPowerGains {
    val radians = progress.coerceIn(0f, 1f).toDouble() * (PI / 2.0)
    return EqualPowerGains(
        outgoing = cos(radians).toFloat(),
        incoming = sin(radians).toFloat(),
    )
}

internal data class CrossfadePlan(
    val outgoingMediaId: String,
    val incomingMediaId: String,
    val timeline: CrossfadeTimeline,
)

internal enum class CrossfadeAbandonReason {
    SessionMoved,
    TailFailed,
    SyncDeadline,
    NextItemMissing,
    Cancelled,
}

internal sealed interface CrossfadeOutcome {
    data object Completed : CrossfadeOutcome

    data class Abandoned(
        val reason: CrossfadeAbandonReason,
        val sessionSwitched: Boolean,
    ) : CrossfadeOutcome
}

internal class CrossfadeTransition(
    private val plan: CrossfadePlan,
    private val session: CrossfadeSessionDeck,
    private val tail: CrossfadeTailDeck,
    private val baseVolume: (String) -> Float,
    private val tuning: CrossfadeTuning = CrossfadeTuning(),
) {
    private sealed interface Phase {
        data object Starting : Phase

        data class PreRolling(
            val targetPositionMs: Long,
            val issuedAtMs: Long,
        ) : Phase

        data class AwaitingTailStart(
            val targetPositionMs: Long,
        ) : Phase

        data class Settling(
            val targetPositionMs: Long,
            val sessionPositionAtPlayMs: Long,
            val playIssuedAtMs: Long,
        ) : Phase

        data object Synced : Phase

        data class Switching(
            val startedAtMs: Long,
        ) : Phase

        data class AwaitingIncoming(
            val firstPlayingPositionMs: Long?,
        ) : Phase

        data class Fading(
            val durationMs: Long,
            val elapsedMs: Long,
            val lastTickMs: Long?,
        ) : Phase
    }

    private var phase: Phase = Phase.Starting
    private var startLatencyMs = tuning.initialStartLatencyMs
    private var prerollLeadMs = tuning.prerollLeadMs
    private val driftSamples = ArrayDeque<Long>()

    var outcome: CrossfadeOutcome? = null
        private set

    val sessionSwitched: Boolean
        get() =
            when (phase) {
                Phase.Starting, is Phase.PreRolling, is Phase.AwaitingTailStart, is Phase.Settling, Phase.Synced, is Phase.Switching -> false
                is Phase.AwaitingIncoming, is Phase.Fading -> true
            }

    val controlsSessionVolume: Boolean
        get() = outcome == null && (phase is Phase.Switching || sessionSwitched)

    private val speed: Float get() = session.playbackSpeed.takeIf { it.isFinite() && it > 0f } ?: 1f

    private fun outgoingVolume(): Float = baseVolume(plan.outgoingMediaId)

    private fun incomingVolume(): Float = baseVolume(plan.incomingMediaId)

    private fun mediaToRealMs(mediaMs: Long): Long = (mediaMs / speed).roundToLong()

    private fun realToMediaMs(realMs: Long): Long = (realMs * speed).roundToLong()

    fun advance(nowMs: Long): Long {
        if (outcome != null) return tuning.activeFrameMs
        if (!sessionSwitched) {
            when {
                session.currentMediaId != plan.outgoingMediaId -> return abandonBeforeSwitch(CrossfadeAbandonReason.SessionMoved)
                tail.hasFailed -> return abandonBeforeSwitch(CrossfadeAbandonReason.TailFailed)
                phase !is Phase.Switching && session.positionMs > plan.timeline.latestSwitchPositionMs -> return abandonBeforeSwitch(CrossfadeAbandonReason.SyncDeadline)
            }
        } else {
            when {
                session.currentMediaId != plan.incomingMediaId -> return abandonAfterSwitch(CrossfadeAbandonReason.SessionMoved)
                tail.hasFailed -> return abandonAfterSwitch(CrossfadeAbandonReason.TailFailed)
            }
        }
        if (!session.playWhenReady) return hold(nowMs)
        return when (val current = phase) {
            Phase.Starting -> beginPreRoll(nowMs)
            is Phase.PreRolling -> advancePreRoll(current, nowMs)
            is Phase.AwaitingTailStart -> advanceTailStart(current, nowMs)
            is Phase.Settling -> advanceSettling(current, nowMs)
            Phase.Synced -> advanceSynced(nowMs)
            is Phase.Switching -> advanceSwitch(current, nowMs)
            is Phase.AwaitingIncoming -> advanceAwaitingIncoming(current, nowMs)
            is Phase.Fading -> advanceFade(current, nowMs)
        }
    }

    fun cancel() {
        if (outcome != null) return
        if (sessionSwitched) {
            abandonAfterSwitch(CrossfadeAbandonReason.Cancelled)
        } else {
            abandonBeforeSwitch(CrossfadeAbandonReason.Cancelled)
        }
    }

    private fun hold(nowMs: Long): Long {
        when (val current = phase) {
            Phase.Starting, is Phase.PreRolling, is Phase.AwaitingTailStart -> Unit
            is Phase.Settling, Phase.Synced, is Phase.Switching -> restartSync()
            is Phase.AwaitingIncoming -> tail.setPlayWhenReady(false)
            is Phase.Fading -> {
                tail.setPlayWhenReady(false)
                phase = current.copy(elapsedMs = fadeElapsedMs(current, nowMs), lastTickMs = null)
            }
        }
        return tuning.idleFrameMs
    }

    private fun restartSync(): Long {
        tail.setPlayWhenReady(false)
        tail.volume = 0f
        session.volume = outgoingVolume()
        driftSamples.clear()
        phase = Phase.Starting
        return tuning.activeFrameMs
    }

    private fun beginPreRoll(nowMs: Long): Long {
        val target = session.positionMs + realToMediaMs(prerollLeadMs)
        driftSamples.clear()
        tail.volume = 0f
        tail.setPlayWhenReady(false)
        tail.seekTo(target)
        phase = Phase.PreRolling(target, nowMs)
        return tuning.activeFrameMs
    }

    private fun advancePreRoll(
        current: Phase.PreRolling,
        nowMs: Long,
    ): Long {
        if (!tail.isReady) return tuning.activeFrameMs
        val neededLeadMs = (nowMs - current.issuedAtMs) + startLatencyMs + tuning.prerollMarginMs
        prerollLeadMs = maxOf(prerollLeadMs, neededLeadMs)
        phase = Phase.AwaitingTailStart(current.targetPositionMs)
        return 1L
    }

    private fun advanceTailStart(
        current: Phase.AwaitingTailStart,
        nowMs: Long,
    ): Long {
        val startAtSessionPositionMs = current.targetPositionMs - realToMediaMs(startLatencyMs)
        val untilStartMs = mediaToRealMs(startAtSessionPositionMs - session.positionMs)
        if (untilStartMs > 0L) return untilStartMs
        tail.setPlayWhenReady(true)
        phase = Phase.Settling(current.targetPositionMs, session.positionMs, nowMs)
        return tuning.settleMs
    }

    private fun advanceSettling(
        current: Phase.Settling,
        nowMs: Long,
    ): Long {
        if (nowMs - current.playIssuedAtMs < tuning.settleMs) return tuning.activeFrameMs
        driftSamples.addLast(tail.positionMs - session.positionMs)
        if (nowMs - current.playIssuedAtMs < tuning.settleMs + tuning.measureWindowMs) return tuning.activeFrameMs
        val driftMs = driftSamples.average().roundToLong()
        val measuredLatencyMs = mediaToRealMs(current.targetPositionMs - current.sessionPositionAtPlayMs - driftMs)
        startLatencyMs = measuredLatencyMs.coerceAtLeast(0L)
        driftSamples.clear()
        if (abs(driftMs) <= tuning.syncToleranceMs) {
            phase = Phase.Synced
            return tuning.activeFrameMs
        }
        return beginPreRoll(nowMs)
    }

    private fun advanceSynced(nowMs: Long): Long {
        driftSamples.addLast(tail.positionMs - session.positionMs)
        while (driftSamples.size > tuning.holdSampleCount) driftSamples.removeFirst()
        if (driftSamples.size == tuning.holdSampleCount && abs(driftSamples.average()) > tuning.holdToleranceMs) {
            return beginPreRoll(nowMs)
        }
        if (session.positionMs < plan.timeline.switchAtPositionMs) {
            val untilSwitchMs = mediaToRealMs(plan.timeline.switchAtPositionMs - session.positionMs)
            return untilSwitchMs.coerceIn(1L, tuning.activeFrameMs)
        }
        val switching = Phase.Switching(nowMs)
        phase = switching
        return advanceSwitch(switching, nowMs)
    }

    private fun advanceSwitch(
        current: Phase.Switching,
        nowMs: Long,
    ): Long {
        if (!tail.isPlaying) return restartSync()
        val progress = ((nowMs - current.startedAtMs).toFloat() / tuning.switchBlendMs.toFloat()).coerceIn(0f, 1f)
        val volume = outgoingVolume()
        session.volume = volume * (1f - progress)
        tail.volume = volume * progress
        if (progress < 1f) return tuning.switchFrameMs
        session.volume = 0f
        tail.volume = volume
        phase = Phase.AwaitingIncoming(firstPlayingPositionMs = null)
        if (session.switchTo(plan.incomingMediaId)) return tuning.activeFrameMs
        phase = current
        return abandonBeforeSwitch(CrossfadeAbandonReason.NextItemMissing)
    }

    private fun advanceAwaitingIncoming(
        current: Phase.AwaitingIncoming,
        nowMs: Long,
    ): Long {
        tail.setPlayWhenReady(true)
        tail.volume = outgoingVolume()
        session.volume = 0f
        if (tail.isEnded) return complete()
        if (!session.isPlaying) return tuning.activeFrameMs
        val firstPosition = current.firstPlayingPositionMs
        if (firstPosition == null) {
            phase = Phase.AwaitingIncoming(session.positionMs)
            return tuning.activeFrameMs
        }
        if (session.positionMs <= firstPosition) return tuning.activeFrameMs
        val tailRemainingMs = mediaToRealMs(tail.durationMs - tail.positionMs).coerceAtLeast(0L)
        val fadeMs = minOf(mediaToRealMs(plan.timeline.fadeDurationMs), tailRemainingMs)
        if (fadeMs <= 0L) return complete()
        val fading = Phase.Fading(durationMs = fadeMs, elapsedMs = 0L, lastTickMs = nowMs)
        phase = fading
        return advanceFade(fading, nowMs)
    }

    private fun advanceFade(
        current: Phase.Fading,
        nowMs: Long,
    ): Long {
        tail.setPlayWhenReady(true)
        val elapsedMs = fadeElapsedMs(current, nowMs)
        val progress = elapsedMs.toFloat() / current.durationMs.toFloat()
        val gains = equalPowerGains(progress)
        tail.volume = outgoingVolume() * gains.outgoing
        session.volume = incomingVolume() * gains.incoming
        if (progress >= 1f || tail.isEnded) return complete()
        phase = current.copy(elapsedMs = elapsedMs, lastTickMs = nowMs)
        return tuning.activeFrameMs
    }

    private fun fadeElapsedMs(
        current: Phase.Fading,
        nowMs: Long,
    ): Long {
        val sinceLastTickMs = current.lastTickMs?.let { nowMs - it } ?: 0L
        return (current.elapsedMs + sinceLastTickMs).coerceIn(0L, current.durationMs)
    }

    private fun abandonBeforeSwitch(reason: CrossfadeAbandonReason): Long {
        tail.setPlayWhenReady(false)
        tail.volume = 0f
        session.volume = outgoingVolume()
        outcome = CrossfadeOutcome.Abandoned(reason, sessionSwitched = false)
        return tuning.activeFrameMs
    }

    private fun abandonAfterSwitch(reason: CrossfadeAbandonReason): Long {
        tail.setPlayWhenReady(false)
        tail.volume = 0f
        session.volume = session.currentMediaId?.let(baseVolume) ?: incomingVolume()
        outcome = CrossfadeOutcome.Abandoned(reason, sessionSwitched = true)
        return tuning.activeFrameMs
    }

    private fun complete(): Long {
        tail.volume = 0f
        session.volume = incomingVolume()
        outcome = CrossfadeOutcome.Completed
        return tuning.activeFrameMs
    }
}
