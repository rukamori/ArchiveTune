/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeTransitionTest {
    private fun CrossfadeScenario.assertSeamlessCrossfade() {
        val outgoingId = outgoing.mediaId
        val incomingId = incoming.mediaId
        assertEquals(CrossfadeOutcome.Completed, outcome)

        val incomingJumps = heard.jumps(incomingId)
        assertTrue("next song must never jump: $incomingJumps", incomingJumps.isEmpty())
        val outgoingJumps = heard.jumps(outgoingId).filter { abs(it.deltaMs) > SYNC_TOLERANCE_MS }
        assertTrue("outgoing song must stay continuous across players: $outgoingJumps", outgoingJumps.isEmpty())

        val firstHeardMs = checkNotNull(heard.firstHeardAtMs(outgoingId))
        val silences = heard.silences(fromMs = firstHeardMs, toMs = outgoing.durationMs + 8_000L)
        assertTrue("no dropouts during the transition: $silences", silences.isEmpty())

        val doubledOffset = heard.worstSameMediaOffsetMs(outgoingId)
        assertTrue("both players must play the outgoing song in sync, offset=$doubledOffset", doubledOffset <= SYNC_TOLERANCE_MS)
        val outgoingPeak = heard.peakSameMediaAmplitude(outgoingId)
        assertTrue("moving the outgoing song between players must not boost it, peak=$outgoingPeak", outgoingPeak <= baseVolume(outgoingId) + 0.01f)

        assertEquals("the next song is started once from its beginning and never re-seeked", 1, session.seekCount)
        assertEquals(incomingId, session.currentMediaId)
        assertEquals(baseVolume(incomingId), session.volume, 0.0001f)
        assertTrue("tail player is released", tailReleasedAtMs != null)
    }

    private fun CrossfadeScenario.assertSongsOverlap() {
        val overlapping =
            (0L until heard.recordedMs).any { t ->
                val heardNow = heard.heardAt(t)
                heardNow.any { it.mediaId == outgoing.mediaId && it.gain >= 0.3f } &&
                    heardNow.any { it.mediaId == incoming.mediaId && it.gain >= 0.3f }
            }
        assertTrue("the songs must actually crossfade", overlapping)
    }

    @Test
    fun nextSongStartsFromItsBeginning_andOutgoingSongNeverJumps_onTypicalDevice() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical).run()

        scenario.assertSeamlessCrossfade()
        scenario.assertSongsOverlap()
    }

    @Test
    fun staysSeamless_onFastAndSlowDevices_withLatencyJitter() {
        val devices = listOf(PlayerLatency.Fast, PlayerLatency.Typical, PlayerLatency.Slow)
        for (device in devices) {
            for (seed in 1L..12L) {
                val scenario = CrossfadeScenario(sessionLatency = device.copy(jitterMs = 6L), seed = seed).run()
                try {
                    scenario.assertSeamlessCrossfade()
                    scenario.assertSongsOverlap()
                } catch (error: AssertionError) {
                    throw AssertionError("device=$device seed=$seed: ${error.message}", error)
                }
            }
        }
    }

    @Test
    fun fallsBackToNormalGaplessTransition_whenTheTailPlayerCannotSyncInTime() {
        val unusableTail = PlayerLatency(crossItemSeekToReadyMs = 60_000L, seekToReadyMs = 60_000L, bufferedStartToAudibleMs = 45L, resumeToAudibleMs = 20L)
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical, tailLatency = unusableTail).run()

        val outcome = scenario.outcome
        assertTrue("expected an abandoned transition, got $outcome", outcome is CrossfadeOutcome.Abandoned && !outcome.sessionSwitched)
        assertEquals("session player must not be touched", 0, scenario.session.seekCount)
        assertTrue("outgoing song keeps playing untouched", scenario.heard.jumps(scenario.outgoing.mediaId).isEmpty())
        assertTrue("next song plays normally", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
        val firstHeardMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.outgoing.mediaId))
        assertTrue(scenario.heard.silences(firstHeardMs, scenario.outgoing.durationMs + 8_000L).isEmpty())
        val incomingStartMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.incoming.mediaId))
        val outgoingEndMs = checkNotNull(scenario.heard.lastHeardAtMs(scenario.outgoing.mediaId))
        assertTrue("gapless hand-over at the end of the song", incomingStartMs - outgoingEndMs in 0L..1L)
        assertEquals(1f, scenario.session.volume, 0.0001f)
        assertTrue("tail player is released", scenario.tailReleasedAtMs != null)
    }

    @Test
    fun pausingDuringTheFade_silencesBothSongs_andResumingContinuesBothWhereTheyWere() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        val pauseAtMs = scenario.timeline.switchAtPositionMs + 2_500L
        val resumeAtMs = pauseAtMs + 3_000L
        scenario.at(pauseAtMs) { scenario.setSessionPlayWhenReady(false) }
        scenario.at(resumeAtMs) { scenario.setSessionPlayWhenReady(true) }
        scenario.run(untilMs = resumeAtMs + 12_000L)

        val heardWhilePaused = (pauseAtMs until resumeAtMs).flatMap { scenario.heard.heardAt(it) }
        assertTrue("nothing may be heard while paused: ${heardWhilePaused.take(3)}", heardWhilePaused.isEmpty())
        assertEquals("tail player must pause with the session", 0L, scenario.tailPlayingWhileSessionPausedMs)
        scenario.assertResumesWherePaused(scenario.outgoing.mediaId, pauseAtMs, resumeAtMs)
        scenario.assertResumesWherePaused(scenario.incoming.mediaId, pauseAtMs, resumeAtMs)
        val silences = scenario.heard.silences(fromMs = resumeAtMs + RESUME_ALLOWANCE_MS, toMs = resumeAtMs + 8_000L)
        assertTrue("no dropouts after resuming: $silences", silences.isEmpty())
        assertEquals(CrossfadeOutcome.Completed, scenario.outcome)
        assertEquals(1, scenario.session.seekCount)
        assertEquals(1f, scenario.session.volume, 0.0001f)
    }

    @Test
    fun pausingWhileTheTailIsSyncing_resyncsAfterResuming_andStillCrossfadesSeamlessly() {
        for (pauseAfterSyncStartMs in listOf(150L, 900L, 1_400L, 3_500L)) {
            val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
            val pauseAtMs = scenario.timeline.syncAtPositionMs + pauseAfterSyncStartMs
            val resumeAtMs = pauseAtMs + 2_000L
            scenario.at(pauseAtMs) { scenario.setSessionPlayWhenReady(false) }
            scenario.at(resumeAtMs) { scenario.setSessionPlayWhenReady(true) }
            scenario.run(untilMs = scenario.outgoing.durationMs + 14_000L)
            try {
                val heardWhilePaused = (pauseAtMs until resumeAtMs).flatMap { scenario.heard.heardAt(it) }
                assertTrue("nothing may be heard while paused: ${heardWhilePaused.take(3)}", heardWhilePaused.isEmpty())
                assertEquals("tail player must pause with the session", 0L, scenario.tailPlayingWhileSessionPausedMs)
                scenario.assertResumesWherePaused(scenario.outgoing.mediaId, pauseAtMs, resumeAtMs)
                assertEquals(CrossfadeOutcome.Completed, scenario.outcome)
                assertTrue("next song must never jump", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
                val offset = scenario.heard.worstSameMediaOffsetMs(scenario.outgoing.mediaId)
                assertTrue("tail must be re-synced after resuming, offset=$offset", offset <= SYNC_TOLERANCE_MS)
                val silences = scenario.heard.silences(fromMs = resumeAtMs + RESUME_ALLOWANCE_MS, toMs = scenario.outgoing.durationMs + 10_000L)
                assertTrue("no dropouts after resuming: $silences", silences.isEmpty())
                assertEquals(1, scenario.session.seekCount)
                assertEquals(1f, scenario.session.volume, 0.0001f)
                scenario.assertSongsOverlap()
            } catch (error: AssertionError) {
                throw AssertionError("paused ${pauseAfterSyncStartMs}ms after sync start: ${error.message}", error)
            }
        }
    }

    @Test
    fun tailFailingDuringTheHandOver_keepsTheOutgoingSongOnTheSession_andFallsBackToAGaplessTransition() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        var failedAtMs = -1L
        scenario.atFirst({ scenario.isHandingOver }) {
            failedAtMs = scenario.clock.nowMs
            scenario.failTail()
        }
        scenario.run()

        assertTrue("hand-over must have started", failedAtMs >= 0L)
        val outcome = scenario.outcome
        assertTrue("expected an abandoned transition, got $outcome", outcome is CrossfadeOutcome.Abandoned && !outcome.sessionSwitched)
        assertEquals("the outgoing song is back at full volume right away", 1f, scenario.heard.heardAt(failedAtMs).single().gain, 0.0001f)
        assertEquals("session player must not be moved", 0, scenario.session.seekCount)
        val outgoingJumps = scenario.heard.jumps(scenario.outgoing.mediaId).filter { abs(it.deltaMs) > SYNC_TOLERANCE_MS }
        assertTrue("outgoing song keeps playing: $outgoingJumps", outgoingJumps.isEmpty())
        assertTrue("next song plays normally", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
        val firstHeardMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.outgoing.mediaId))
        val silences = scenario.heard.silences(firstHeardMs, scenario.outgoing.durationMs + 8_000L)
        assertTrue("no dropouts: $silences", silences.isEmpty())
        val incomingStartMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.incoming.mediaId))
        val outgoingEndMs = checkNotNull(scenario.heard.lastHeardAtMs(scenario.outgoing.mediaId))
        assertTrue("gapless hand-over at the end of the song", incomingStartMs - outgoingEndMs in 0L..1L)
        assertEquals(1f, scenario.session.volume, 0.0001f)
        assertTrue("tail player is released", scenario.tailReleasedAtMs != null)
    }

    @Test
    fun tailStallingDuringTheHandOver_keepsTheOutgoingSongOnTheSession_andCrossfadesOnceResynced() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        var stalledAtMs = -1L
        scenario.atFirst({ scenario.isHandingOver }) {
            stalledAtMs = scenario.clock.nowMs
            scenario.stallTail(1_000L)
        }
        scenario.run()

        assertTrue("hand-over must have started", stalledAtMs >= 0L)
        assertEquals("the outgoing song is back at full volume right away", 1f, scenario.heard.heardAt(stalledAtMs).single().gain, 0.0001f)
        scenario.assertSeamlessCrossfade()
        scenario.assertSongsOverlap()
    }

    @Test
    fun tailFailingDuringTheFade_bringsTheNextSongToFullVolumeRightAway() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        var failedAtMs = -1L
        scenario.atFirst({ scenario.isFading }) {
            failedAtMs = scenario.clock.nowMs
            scenario.failTail()
        }
        scenario.run()

        assertTrue("fade must have started", failedAtMs >= 0L)
        val outcome = scenario.outcome
        assertTrue("expected the fade to be abandoned on the next song, got $outcome", outcome is CrossfadeOutcome.Abandoned && outcome.sessionSwitched)
        val heardAtFailure = scenario.heard.heardAt(failedAtMs).single()
        assertEquals(scenario.incoming.mediaId, heardAtFailure.mediaId)
        assertEquals("the next song is at full volume right away", 1f, heardAtFailure.gain, 0.0001f)
        assertTrue("next song must never jump", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
        val silences = scenario.heard.silences(failedAtMs, failedAtMs + 8_000L)
        assertTrue("no dropouts: $silences", silences.isEmpty())
        assertEquals(1, scenario.session.seekCount)
        assertEquals(1f, scenario.session.volume, 0.0001f)
        assertTrue("tail player is released", scenario.tailReleasedAtMs != null)
    }

    @Test
    fun skippingToAnotherSongDuringTheFade_silencesTheOutgoingSong_andPlaysTheChosenSongAtItsOwnVolume() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical, baseVolumes = mapOf("later" to 0.6f))
        var skippedAtMs = -1L
        scenario.atFirst({ scenario.isFading }) {
            skippedAtMs = scenario.clock.nowMs
            scenario.skipSessionTo(scenario.later.mediaId)
        }
        scenario.run()

        assertTrue("fade must have started", skippedAtMs >= 0L)
        val outcome = scenario.outcome
        assertTrue("expected the fade to be abandoned, got $outcome", outcome is CrossfadeOutcome.Abandoned && outcome.sessionSwitched)
        val outgoingAfterSkip = (skippedAtMs until scenario.heard.recordedMs).filter { t -> scenario.heard.heardAt(t).any { it.mediaId == scenario.outgoing.mediaId } }
        assertTrue("outgoing song must stop with the skip, heard at ${outgoingAfterSkip.take(3)}", outgoingAfterSkip.isEmpty())
        val laterStartMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.later.mediaId))
        assertEquals(0.6f, scenario.heard.heardAt(laterStartMs).single().gain, 0.0001f)
        assertTrue("chosen song must never jump", scenario.heard.jumps(scenario.later.mediaId).isEmpty())
        assertEquals(scenario.later.mediaId, scenario.session.currentMediaId)
        assertEquals(0.6f, scenario.session.volume, 0.0001f)
        assertTrue("tail player is released", scenario.tailReleasedAtMs != null)
    }

    @Test
    fun cancellingBeforeTheHandOver_leavesTheOutgoingSongUntouched() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        val cancelAtMs = scenario.timeline.syncAtPositionMs + 3_000L
        scenario.at(cancelAtMs) { scenario.cancelTransition() }
        scenario.run()

        val outcome = scenario.outcome
        assertTrue("expected a cancelled transition, got $outcome", outcome is CrossfadeOutcome.Abandoned && !outcome.sessionSwitched)
        assertEquals(cancelAtMs, scenario.tailReleasedAtMs)
        assertEquals("session player must not be moved", 0, scenario.session.seekCount)
        assertTrue("outgoing song keeps playing", scenario.heard.jumps(scenario.outgoing.mediaId).isEmpty())
        val firstHeardMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.outgoing.mediaId))
        assertTrue(scenario.heard.silences(firstHeardMs, scenario.outgoing.durationMs + 8_000L).isEmpty())
        val incomingStartMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.incoming.mediaId))
        val outgoingEndMs = checkNotNull(scenario.heard.lastHeardAtMs(scenario.outgoing.mediaId))
        assertTrue("gapless hand-over at the end of the song", incomingStartMs - outgoingEndMs in 0L..1L)
        assertEquals(1f, scenario.session.volume, 0.0001f)
    }

    @Test
    fun cancellingDuringTheFade_finishesOnTheNextSongAtFullVolume() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        var cancelledAtMs = -1L
        scenario.atFirst({ scenario.isFading }) {
            cancelledAtMs = scenario.clock.nowMs
            scenario.cancelTransition()
        }
        scenario.run()

        assertTrue("fade must have started", cancelledAtMs >= 0L)
        val outcome = scenario.outcome
        assertTrue("expected a cancelled fade on the next song, got $outcome", outcome is CrossfadeOutcome.Abandoned && outcome.sessionSwitched)
        val heardAtCancel = scenario.heard.heardAt(cancelledAtMs).single()
        assertEquals(scenario.incoming.mediaId, heardAtCancel.mediaId)
        assertEquals(1f, heardAtCancel.gain, 0.0001f)
        assertTrue("next song must never jump", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
        assertEquals(cancelledAtMs, scenario.tailReleasedAtMs)
        assertEquals(1f, scenario.session.volume, 0.0001f)
    }

    @Test
    fun pausingDuringTheHandOver_keepsTheOutgoingSongOnTheSession_andCrossfadesAfterResuming() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        var pauseAtMs = -1L
        var resumeAtMs = -1L
        scenario.atFirst({ scenario.isHandingOver }) {
            pauseAtMs = scenario.clock.nowMs
            resumeAtMs = pauseAtMs + 1_500L
            scenario.setSessionPlayWhenReady(false)
            scenario.at(resumeAtMs) { scenario.setSessionPlayWhenReady(true) }
        }
        scenario.run(untilMs = scenario.outgoing.durationMs + 14_000L)

        assertTrue("hand-over must have started", pauseAtMs >= 0L)
        val heardWhilePaused = (pauseAtMs until resumeAtMs).flatMap { scenario.heard.heardAt(it) }
        assertTrue("nothing may be heard while paused: ${heardWhilePaused.take(3)}", heardWhilePaused.isEmpty())
        assertEquals("tail player must pause with the session", 0L, scenario.tailPlayingWhileSessionPausedMs)
        scenario.assertResumesWherePaused(scenario.outgoing.mediaId, pauseAtMs, resumeAtMs)
        assertEquals(CrossfadeOutcome.Completed, scenario.outcome)
        assertTrue("next song must never jump", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
        val offset = scenario.heard.worstSameMediaOffsetMs(scenario.outgoing.mediaId)
        assertTrue("tail must be re-synced after resuming, offset=$offset", offset <= SYNC_TOLERANCE_MS)
        val silences = scenario.heard.silences(fromMs = resumeAtMs + RESUME_ALLOWANCE_MS, toMs = scenario.outgoing.durationMs + 10_000L)
        assertTrue("no dropouts after resuming: $silences", silences.isEmpty())
        assertEquals(1, scenario.session.seekCount)
        assertEquals(1f, scenario.session.volume, 0.0001f)
        scenario.assertSongsOverlap()
    }

    @Test
    fun nextSongThatStartsSlowerThanTheOutgoingSongLasts_startsAtFullVolumeOnceTheOutgoingSongEnds() {
        val slowNextSong = PlayerLatency.Typical.copy(crossItemSeekToReadyMs = 7_000L)
        val scenario = CrossfadeScenario(sessionLatency = slowNextSong, tailLatency = PlayerLatency.Typical).run(untilMs = 40_000L)

        assertEquals(CrossfadeOutcome.Completed, scenario.outcome)
        val outgoingJumps = scenario.heard.jumps(scenario.outgoing.mediaId).filter { abs(it.deltaMs) > SYNC_TOLERANCE_MS }
        assertTrue("outgoing song must stay continuous: $outgoingJumps", outgoingJumps.isEmpty())
        val outgoingEnd = scenario.heard.heardAt(checkNotNull(scenario.heard.lastHeardAtMs(scenario.outgoing.mediaId))).single { it.mediaId == scenario.outgoing.mediaId }
        assertEquals("outgoing song plays to its end", scenario.outgoing.durationMs - 1L, outgoingEnd.positionMs)
        val incomingStartMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.incoming.mediaId))
        val incomingStart = scenario.heard.heardAt(incomingStartMs).single()
        assertEquals(0L, incomingStart.positionMs)
        assertEquals(1f, incomingStart.gain, 0.0001f)
        assertTrue("next song must never jump", scenario.heard.jumps(scenario.incoming.mediaId).isEmpty())
        assertEquals(1, scenario.session.seekCount)
        assertEquals(1f, scenario.session.volume, 0.0001f)
        assertTrue("tail player is released", scenario.tailReleasedAtMs != null)
    }

    @Test
    fun songsWithDifferentNormalizedVolumes_crossfadeBetweenTheirOwnVolumes() {
        for (seed in 1L..6L) {
            val scenario =
                CrossfadeScenario(
                    sessionLatency = PlayerLatency.Typical.copy(jitterMs = 6L),
                    baseVolumes = mapOf("outgoing" to 0.5f, "incoming" to 0.8f),
                    seed = seed,
                ).run()
            try {
                scenario.assertSeamlessCrossfade()
                scenario.assertSongsOverlap()
                val outgoingBeforeFade = scenario.heard.heardAt(scenario.timeline.syncAtPositionMs).single()
                assertEquals(0.5f, outgoingBeforeFade.gain, 0.0001f)
            } catch (error: AssertionError) {
                throw AssertionError("seed=$seed: ${error.message}", error)
            }
        }
    }

    @Test
    fun atOtherPlaybackSpeeds_staysSeamless_andFadesTheOutgoingSongOutThroughItsEnding() {
        for (speed in listOf(0.75f, 1.5f)) {
            val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical, speed = speed)
            scenario.run(untilMs = (scenario.outgoing.durationMs / speed).toLong() + 8_000L)
            try {
                scenario.assertSeamlessCrossfade()
                scenario.assertSongsOverlap()
                val lastOutgoing = scenario.heard.heardAt(checkNotNull(scenario.heard.lastHeardAtMs(scenario.outgoing.mediaId))).first { it.mediaId == scenario.outgoing.mediaId }
                assertTrue("outgoing song must fade out through its ending, last heard at ${lastOutgoing.positionMs}", lastOutgoing.positionMs >= scenario.outgoing.durationMs - 400L)
            } catch (error: AssertionError) {
                throw AssertionError("speed=$speed: ${error.message}", error)
            }
        }
    }

    @Test
    fun nextSongNoLongerQueuedAtTheHandOver_keepsTheOutgoingSongPlayingOnTheSession() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        var handOverAtMs = -1L
        scenario.atFirst({ scenario.isHandingOver }) {
            handOverAtMs = scenario.clock.nowMs
            scenario.session.acceptsSwitches = false
        }
        scenario.run()

        assertTrue("hand-over must have started", handOverAtMs >= 0L)
        val outcome = scenario.outcome
        assertTrue("expected an abandoned transition, got $outcome", outcome is CrossfadeOutcome.Abandoned && !outcome.sessionSwitched)
        assertEquals("session player must not be moved", 0, scenario.session.seekCount)
        val outgoingJumps = scenario.heard.jumps(scenario.outgoing.mediaId).filter { abs(it.deltaMs) > SYNC_TOLERANCE_MS }
        assertTrue("outgoing song keeps playing: $outgoingJumps", outgoingJumps.isEmpty())
        val firstHeardMs = checkNotNull(scenario.heard.firstHeardAtMs(scenario.outgoing.mediaId))
        val silences = scenario.heard.silences(firstHeardMs, scenario.outgoing.durationMs + 8_000L)
        assertTrue("no dropouts: $silences", silences.isEmpty())
        val afterHandOver = scenario.heard.heardAt(scenario.checkNotNullTailRelease())
        assertEquals("outgoing song is back on the session at full volume", listOf("session" to 1f), afterHandOver.map { it.player to it.gain })
        assertEquals(1f, scenario.session.volume, 0.0001f)
    }

    @Test
    fun tailThatSeeksSlowerThanThePreRollLead_stillSyncsAndCrossfadesSeamlessly() {
        val slowSeekingTail = PlayerLatency.Slow.copy(seekToReadyMs = 1_800L)
        for (seed in 1L..4L) {
            val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical, tailLatency = slowSeekingTail.copy(jitterMs = 6L), seed = seed).run()
            try {
                scenario.assertSeamlessCrossfade()
                scenario.assertSongsOverlap()
            } catch (error: AssertionError) {
                throw AssertionError("seed=$seed: ${error.message}", error)
            }
        }
    }

    @Test
    fun ownsTheSessionVolumeOnlyFromTheHandOverUntilTheTransitionFinishes() {
        val scenario = CrossfadeScenario(sessionLatency = PlayerLatency.Typical)
        val ownership = mutableListOf<Pair<String, Boolean>>()
        val sample = { label: String -> ownership += label to checkNotNull(scenario.transition).controlsSessionVolume }
        scenario.at(scenario.timeline.syncAtPositionMs + 2_000L) { sample("syncing") }
        scenario.atFirst({ scenario.isHandingOver }) { sample("handing over") }
        scenario.atFirst({ scenario.isFading }) { sample("fading") }
        scenario.atFirst({ scenario.outcome != null }) { sample("finished") }
        scenario.run()

        assertEquals(
            listOf("syncing" to false, "handing over" to true, "fading" to true, "finished" to false),
            ownership,
        )
    }

    @Test
    fun fadeGains_keepTheCombinedPowerConstant_fromTheOutgoingToTheIncomingSong() {
        val start = equalPowerGains(0f)
        val quarter = equalPowerGains(0.25f)
        val midpoint = equalPowerGains(0.5f)
        val end = equalPowerGains(1f)

        assertEquals(1f, start.outgoing, 0.0001f)
        assertEquals(0f, start.incoming, 0.0001f)
        assertEquals(0f, end.outgoing, 0.0001f)
        assertEquals(1f, end.incoming, 0.0001f)
        assertEquals(0.7071f, midpoint.outgoing, 0.0001f)
        assertEquals(0.7071f, midpoint.incoming, 0.0001f)
        assertEquals(1f, quarter.outgoing * quarter.outgoing + quarter.incoming * quarter.incoming, 0.0001f)
        assertEquals(start, equalPowerGains(-1f))
        assertEquals(end, equalPowerGains(2f))
    }

    private fun CrossfadeScenario.checkNotNullTailRelease(): Long = checkNotNull(tailReleasedAtMs) { "tail player is released" }

    private fun CrossfadeScenario.assertResumesWherePaused(
        mediaId: String,
        pauseAtMs: Long,
        resumeAtMs: Long,
    ) {
        val beforePause = heard.heardAt(pauseAtMs - 1L).first { it.mediaId == mediaId }
        val resumedAtMs = (resumeAtMs..resumeAtMs + RESUME_ALLOWANCE_MS).first { t -> heard.heardAt(t).any { it.mediaId == mediaId } }
        val afterResume = heard.heardAt(resumedAtMs).first { it.mediaId == mediaId }
        assertEquals("$mediaId continues from where it was paused", beforePause.positionMs + 1L, afterResume.positionMs)
        val otherJumps = heard.jumps(mediaId).filter { it.atMs != resumedAtMs && abs(it.deltaMs) > SYNC_TOLERANCE_MS }
        assertTrue("$mediaId never jumps: $otherJumps", otherJumps.isEmpty())
    }

    private companion object {
        const val SYNC_TOLERANCE_MS = 15L
        const val RESUME_ALLOWANCE_MS = 200L
    }
}
