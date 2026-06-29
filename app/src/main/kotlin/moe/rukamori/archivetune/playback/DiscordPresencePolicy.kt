/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.Player
import moe.rukamori.archivetune.db.entities.Song

enum class PausedPresenceGate {
    FollowPreference,
    HiddenByNotificationDismiss,
}

enum class PresenceMode {
    Playing,
    Paused,
}

enum class PlaybackSemanticState {
    PlayingReady,
    PausedByUser,
    BufferingWhilePlayRequested,
    Inactive,
}

enum class HiddenReason {
    Disabled,
    NoToken,
    NoSong,
    PausedByPreference,
    PausedByNotificationDismiss,
    ServiceStopping,
    NoStablePlaybackYet,
    PlaybackStalled,
}

enum class HoldReason {
    BufferingWhilePlayRequested,
}

sealed interface DiscordPresenceDecision {
    data class Visible(
        val songId: String,
        val mode: PresenceMode,
    ) : DiscordPresenceDecision {
        val isPaused: Boolean
            get() = mode == PresenceMode.Paused
    }

    data class Hidden(
        val reason: HiddenReason,
    ) : DiscordPresenceDecision

    data class Hold(
        val reason: HoldReason,
        val songId: String?,
    ) : DiscordPresenceDecision
}

data class DiscordPresenceSnapshot(
    val song: Song,
    val positionMs: Long,
    val isPaused: Boolean,
)

data class ActiveHoldState(
    val reason: HoldReason,
    val songId: String?,
    val startedAtMs: Long,
)

data class LastAppliedVisiblePresence(
    val songId: String,
    val mode: PresenceMode,
    val appliedAtMs: Long,
)

data class DiscordHoldContext(
    val nowMs: Long,
    val activeHoldState: ActiveHoldState?,
    val lastAppliedVisiblePresence: LastAppliedVisiblePresence?,
    val holdTimeoutMs: Long,
)

data class DiscordPresenceResolution(
    val decision: DiscordPresenceDecision,
    val nextHoldState: ActiveHoldState?,
)

data class DiscordPresenceInputs(
    val enabled: Boolean,
    val hasToken: Boolean,
    val song: Song?,
    val isPlaying: Boolean,
    val showWhenPaused: Boolean,
    val pausedPresenceGate: PausedPresenceGate = PausedPresenceGate.FollowPreference,
    val serviceStopping: Boolean = false,
    val playWhenReady: Boolean = isPlaying,
    val playbackState: Int = if (isPlaying) Player.STATE_READY else Player.STATE_IDLE,
)

fun derivePlaybackSemanticState(input: DiscordPresenceInputs): PlaybackSemanticState {
    if (input.song == null) {
        return PlaybackSemanticState.Inactive
    }
    if (!input.playWhenReady) {
        return PlaybackSemanticState.PausedByUser
    }

    return when (input.playbackState) {
        Player.STATE_READY -> {
            if (input.isPlaying) {
                PlaybackSemanticState.PlayingReady
            } else {
                PlaybackSemanticState.BufferingWhilePlayRequested
            }
        }

        Player.STATE_BUFFERING -> {
            PlaybackSemanticState.BufferingWhilePlayRequested
        }

        Player.STATE_IDLE,
        Player.STATE_ENDED,
        -> {
            PlaybackSemanticState.Inactive
        }

        else -> {
            PlaybackSemanticState.Inactive
        }
    }
}

fun deriveRawDiscordPresenceDecision(
    input: DiscordPresenceInputs,
    semanticState: PlaybackSemanticState,
): DiscordPresenceDecision {
    if (input.serviceStopping) {
        return DiscordPresenceDecision.Hidden(HiddenReason.ServiceStopping)
    }
    if (!input.enabled) {
        return DiscordPresenceDecision.Hidden(HiddenReason.Disabled)
    }
    if (!input.hasToken) {
        return DiscordPresenceDecision.Hidden(HiddenReason.NoToken)
    }

    val song = input.song ?: return DiscordPresenceDecision.Hidden(HiddenReason.NoSong)

    return when (semanticState) {
        PlaybackSemanticState.PlayingReady -> {
            DiscordPresenceDecision.Visible(
                songId = song.song.id,
                mode = PresenceMode.Playing,
            )
        }

        PlaybackSemanticState.PausedByUser -> {
            when {
                !input.showWhenPaused -> {
                    DiscordPresenceDecision.Hidden(HiddenReason.PausedByPreference)
                }

                input.pausedPresenceGate == PausedPresenceGate.HiddenByNotificationDismiss -> {
                    DiscordPresenceDecision.Hidden(HiddenReason.PausedByNotificationDismiss)
                }

                else -> {
                    DiscordPresenceDecision.Visible(
                        songId = song.song.id,
                        mode = PresenceMode.Paused,
                    )
                }
            }
        }

        PlaybackSemanticState.BufferingWhilePlayRequested -> {
            DiscordPresenceDecision.Hold(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = song.song.id,
            )
        }

        PlaybackSemanticState.Inactive -> {
            DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled)
        }
    }
}

fun resolveDiscordPresenceDecision(
    rawDecision: DiscordPresenceDecision,
    holdContext: DiscordHoldContext,
): DiscordPresenceResolution =
    when (rawDecision) {
        is DiscordPresenceDecision.Visible -> {
            DiscordPresenceResolution(
                decision = rawDecision,
                nextHoldState = null,
            )
        }

        is DiscordPresenceDecision.Hidden -> {
            DiscordPresenceResolution(
                decision = rawDecision,
                nextHoldState = null,
            )
        }

        is DiscordPresenceDecision.Hold -> {
            val lastAppliedVisible =
                holdContext.lastAppliedVisiblePresence
                    ?: return DiscordPresenceResolution(
                        decision = DiscordPresenceDecision.Hidden(HiddenReason.NoStablePlaybackYet),
                        nextHoldState = null,
                    )

            val activeHold = holdContext.activeHoldState
            val isSameHold =
                activeHold != null &&
                    activeHold.reason == rawDecision.reason &&
                    activeHold.songId == rawDecision.songId

            if (isSameHold) {
                val elapsedMs = holdContext.nowMs - activeHold.startedAtMs
                if (elapsedMs >= holdContext.holdTimeoutMs) {
                    return DiscordPresenceResolution(
                        decision = DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled),
                        nextHoldState = null,
                    )
                }

                return DiscordPresenceResolution(
                    decision = rawDecision,
                    nextHoldState = activeHold,
                )
            }

            DiscordPresenceResolution(
                decision = rawDecision,
                nextHoldState =
                    ActiveHoldState(
                        reason = rawDecision.reason,
                        songId = rawDecision.songId ?: lastAppliedVisible.songId,
                        startedAtMs = holdContext.nowMs,
                    ),
            )
        }
    }

fun deriveDiscordPresenceDecision(input: DiscordPresenceInputs): DiscordPresenceDecision =
    deriveFinalDiscordPresenceDecision(
        input = input,
        holdContext =
            DiscordHoldContext(
                nowMs = 0L,
                activeHoldState = null,
                lastAppliedVisiblePresence = null,
                holdTimeoutMs = 0L,
            ),
    ).decision

fun deriveFinalDiscordPresenceDecision(
    input: DiscordPresenceInputs,
    holdContext: DiscordHoldContext,
): DiscordPresenceResolution {
    val semanticState = derivePlaybackSemanticState(input)
    val rawDecision = deriveRawDiscordPresenceDecision(input, semanticState)
    return resolveDiscordPresenceDecision(rawDecision, holdContext)
}
