/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.media3.common.Player
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.db.entities.SongEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DiscordPresencePolicyTest {
    @Test
    fun semanticState_isPausedByUser_whenPlayWhenReadyIsFalse() {
        val semanticState =
            derivePlaybackSemanticState(
                DiscordPresenceInputs(
                    enabled = true,
                    hasToken = true,
                    song = testSong(),
                    isPlaying = false,
                    playWhenReady = false,
                    playbackState = Player.STATE_READY,
                    showWhenPaused = true,
                ),
            )

        assertEquals(PlaybackSemanticState.PausedByUser, semanticState)
    }

    @Test
    fun semanticState_isPlayingReady_whenPlayerIsActivelyPlaying() {
        val semanticState =
            derivePlaybackSemanticState(
                DiscordPresenceInputs(
                    enabled = true,
                    hasToken = true,
                    song = testSong(),
                    isPlaying = true,
                    playWhenReady = true,
                    playbackState = Player.STATE_READY,
                    showWhenPaused = true,
                ),
            )

        assertEquals(PlaybackSemanticState.PlayingReady, semanticState)
    }

    @Test
    fun semanticState_isBufferingWhilePlayRequested_whenBuffering() {
        val semanticState =
            derivePlaybackSemanticState(
                DiscordPresenceInputs(
                    enabled = true,
                    hasToken = true,
                    song = testSong(),
                    isPlaying = false,
                    playWhenReady = true,
                    playbackState = Player.STATE_BUFFERING,
                    showWhenPaused = true,
                ),
            )

        assertEquals(PlaybackSemanticState.BufferingWhilePlayRequested, semanticState)
    }

    @Test
    fun semanticState_isInactive_whenSongIsMissing() {
        val semanticState =
            derivePlaybackSemanticState(
                DiscordPresenceInputs(
                    enabled = true,
                    hasToken = true,
                    song = null,
                    isPlaying = false,
                    playWhenReady = false,
                    playbackState = Player.STATE_IDLE,
                    showWhenPaused = true,
                ),
            )

        assertEquals(PlaybackSemanticState.Inactive, semanticState)
    }

    @Test
    fun semanticState_isInactive_whenPlaybackStateIsIdleWhileSongExists() {
        val semanticState =
            derivePlaybackSemanticState(
                DiscordPresenceInputs(
                    enabled = true,
                    hasToken = true,
                    song = testSong(),
                    isPlaying = false,
                    playWhenReady = true,
                    playbackState = Player.STATE_IDLE,
                    showWhenPaused = true,
                ),
            )

        assertEquals(PlaybackSemanticState.Inactive, semanticState)
    }

    @Test
    fun rawDecision_returnsServiceStoppingBeforeAnyOtherCondition() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong(),
                        isPlaying = true,
                        playWhenReady = true,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = true,
                        serviceStopping = true,
                    ),
                semanticState = PlaybackSemanticState.PlayingReady,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.ServiceStopping),
            result,
        )
    }

    @Test
    fun rawDecision_returnsDisabledWhenRpcIsOff() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = false,
                        hasToken = true,
                        song = testSong(),
                        isPlaying = true,
                        playWhenReady = true,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.PlayingReady,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.Disabled),
            result,
        )
    }

    @Test
    fun rawDecision_returnsNoTokenWhenTokenIsMissing() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = false,
                        song = testSong(),
                        isPlaying = true,
                        playWhenReady = true,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.PlayingReady,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoToken),
            result,
        )
    }

    @Test
    fun rawDecision_returnsNoSongWhenSongIsMissing() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = null,
                        isPlaying = false,
                        playWhenReady = false,
                        playbackState = Player.STATE_IDLE,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.Inactive,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoSong),
            result,
        )
    }

    @Test
    fun rawDecision_returnsPausedByPreferenceWhenPlaybackIsPausedAndPreferenceIsOff() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong(),
                        isPlaying = false,
                        playWhenReady = false,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = false,
                    ),
                semanticState = PlaybackSemanticState.PausedByUser,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PausedByPreference),
            result,
        )
    }

    @Test
    fun rawDecision_returnsPausedByNotificationDismissWhenGateOverridesPreference() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong(),
                        isPlaying = false,
                        playWhenReady = false,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = true,
                        pausedPresenceGate = PausedPresenceGate.HiddenByNotificationDismiss,
                    ),
                semanticState = PlaybackSemanticState.PausedByUser,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PausedByNotificationDismiss),
            result,
        )
    }

    @Test
    fun rawDecision_returnsVisiblePlayingWhenReady() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong("song-playing"),
                        isPlaying = true,
                        playWhenReady = true,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.PlayingReady,
            )

        assertEquals(
            DiscordPresenceDecision.Visible(
                songId = "song-playing",
                mode = PresenceMode.Playing,
            ),
            result,
        )
    }

    @Test
    fun rawDecision_returnsVisiblePausedWhenPreferenceAllowsPausedPresence() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong("song-paused"),
                        isPlaying = false,
                        playWhenReady = false,
                        playbackState = Player.STATE_READY,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.PausedByUser,
            )

        assertEquals(
            DiscordPresenceDecision.Visible(
                songId = "song-paused",
                mode = PresenceMode.Paused,
            ),
            result,
        )
    }

    @Test
    fun rawDecision_returnsHoldWhenBufferingWhilePlaybackIsRequested() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong("song-buffering"),
                        isPlaying = false,
                        playWhenReady = true,
                        playbackState = Player.STATE_BUFFERING,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.BufferingWhilePlayRequested,
            )

        assertEquals(
            DiscordPresenceDecision.Hold(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-buffering",
            ),
            result,
        )
    }

    @Test
    fun rawDecision_returnsPlaybackStalledWhenInactiveButSongStillExists() {
        val result =
            deriveRawDiscordPresenceDecision(
                input =
                    DiscordPresenceInputs(
                        enabled = true,
                        hasToken = true,
                        song = testSong("song-stalled"),
                        isPlaying = false,
                        playWhenReady = true,
                        playbackState = Player.STATE_IDLE,
                        showWhenPaused = true,
                    ),
                semanticState = PlaybackSemanticState.Inactive,
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled),
            result,
        )
    }

    @Test
    fun resolution_returnsNoStablePlaybackYetWhenHoldingWithoutPreviousVisibleState() {
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        reason = HoldReason.BufferingWhilePlayRequested,
                        songId = "song-hold",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 1_000L,
                        activeHoldState = null,
                        lastAppliedVisiblePresence = null,
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.NoStablePlaybackYet),
            resolution.decision,
        )
        assertEquals(null, resolution.nextHoldState)
    }

    @Test
    fun resolution_startsNewHoldWhenPreviousVisibleStateExists() {
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        reason = HoldReason.BufferingWhilePlayRequested,
                        songId = "song-hold",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 2_000L,
                        activeHoldState = null,
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence(
                                songId = "song-hold",
                                mode = PresenceMode.Playing,
                                appliedAtMs = 1_000L,
                            ),
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Hold(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-hold",
            ),
            resolution.decision,
        )
        assertEquals(
            ActiveHoldState(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-hold",
                startedAtMs = 2_000L,
            ),
            resolution.nextHoldState,
        )
    }

    @Test
    fun resolution_reusesSameHoldStateWhenEquivalentHoldContinuesWithoutTimeout() {
        val existingHold =
            ActiveHoldState(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-hold",
                startedAtMs = 2_000L,
            )

        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        reason = HoldReason.BufferingWhilePlayRequested,
                        songId = "song-hold",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 5_000L,
                        activeHoldState = existingHold,
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence(
                                songId = "song-hold",
                                mode = PresenceMode.Playing,
                                appliedAtMs = 1_000L,
                            ),
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Hold(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-hold",
            ),
            resolution.decision,
        )
        assertSame(existingHold, resolution.nextHoldState)
    }

    @Test
    fun resolution_timesOutExistingHoldAndFallsBackToPlaybackStalled() {
        val existingHold =
            ActiveHoldState(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-hold",
                startedAtMs = 2_000L,
            )

        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        reason = HoldReason.BufferingWhilePlayRequested,
                        songId = "song-hold",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 10_000L,
                        activeHoldState = existingHold,
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence(
                                songId = "song-hold",
                                mode = PresenceMode.Playing,
                                appliedAtMs = 1_000L,
                            ),
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.PlaybackStalled),
            resolution.decision,
        )
        assertEquals(null, resolution.nextHoldState)
    }

    @Test
    fun resolution_resetsHoldWhenSongChangesDuringBuffering() {
        val existingHold =
            ActiveHoldState(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-a",
                startedAtMs = 2_000L,
            )

        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Hold(
                        reason = HoldReason.BufferingWhilePlayRequested,
                        songId = "song-b",
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 3_000L,
                        activeHoldState = existingHold,
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence(
                                songId = "song-a",
                                mode = PresenceMode.Playing,
                                appliedAtMs = 1_000L,
                            ),
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Hold(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-b",
            ),
            resolution.decision,
        )
        assertEquals(
            ActiveHoldState(
                reason = HoldReason.BufferingWhilePlayRequested,
                songId = "song-b",
                startedAtMs = 3_000L,
            ),
            resolution.nextHoldState,
        )
    }

    @Test
    fun resolution_clearsHoldStateWhenVisibleDecisionIsApplied() {
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision =
                    DiscordPresenceDecision.Visible(
                        songId = "song-visible",
                        mode = PresenceMode.Playing,
                    ),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 1_000L,
                        activeHoldState =
                            ActiveHoldState(
                                reason = HoldReason.BufferingWhilePlayRequested,
                                songId = "song-visible",
                                startedAtMs = 100L,
                            ),
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence(
                                songId = "song-visible",
                                mode = PresenceMode.Playing,
                                appliedAtMs = 50L,
                            ),
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Visible(
                songId = "song-visible",
                mode = PresenceMode.Playing,
            ),
            resolution.decision,
        )
        assertEquals(null, resolution.nextHoldState)
    }

    @Test
    fun resolution_clearsHoldStateWhenHiddenDecisionIsApplied() {
        val resolution =
            resolveDiscordPresenceDecision(
                rawDecision = DiscordPresenceDecision.Hidden(HiddenReason.Disabled),
                holdContext =
                    DiscordHoldContext(
                        nowMs = 1_000L,
                        activeHoldState =
                            ActiveHoldState(
                                reason = HoldReason.BufferingWhilePlayRequested,
                                songId = "song-visible",
                                startedAtMs = 100L,
                            ),
                        lastAppliedVisiblePresence =
                            LastAppliedVisiblePresence(
                                songId = "song-visible",
                                mode = PresenceMode.Playing,
                                appliedAtMs = 50L,
                            ),
                        holdTimeoutMs = 7_000L,
                    ),
            )

        assertEquals(
            DiscordPresenceDecision.Hidden(HiddenReason.Disabled),
            resolution.decision,
        )
        assertEquals(null, resolution.nextHoldState)
    }

    private fun testSong(id: String = "song-id"): Song =
        Song(
            song = SongEntity(id = id, title = "Test song"),
            artists = emptyList(),
        )
}
