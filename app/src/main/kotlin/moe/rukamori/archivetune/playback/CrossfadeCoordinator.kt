/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.DeviceInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

internal data class CrossfadeRequest(
    val plan: CrossfadePlan,
    val outgoingIndex: Int,
    val incomingIndex: Int,
    val outgoingItem: MediaItem,
    val outgoingDurationMs: Long,
)

internal class CrossfadeCoordinator(
    private val scope: () -> CoroutineScope,
    private val sessionPlayer: Player,
    private val createTailPlayer: () -> ExoPlayer,
    private val baseVolume: (String) -> Float,
    private val maxVolume: Float,
    private val isAllowed: () -> Boolean,
    private val onFinished: (CrossfadeOutcome, CrossfadePlan) -> Unit,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) {
    private class Entry(
        val request: CrossfadeRequest,
    ) {
        var job: Job? = null
        var tail: ExoPlayer? = null
        var transition: CrossfadeTransition? = null
    }

    private var active: Entry? = null
    private val wakeups = Channel<Unit>(Channel.CONFLATED)
    private val tailListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                wake()
            }

            override fun onPlayerError(error: PlaybackException) {
                Timber.tag(TAG).w(error, "Crossfade tail player failed")
            }
        }

    val isTransitioning: Boolean
        get() = active?.transition?.let { it.outcome == null } == true

    val hasSwitchedSession: Boolean
        get() = active?.transition?.let { it.outcome == null && it.sessionSwitched } == true

    val controlsSessionVolume: Boolean
        get() = active?.transition?.controlsSessionVolume == true

    fun schedule(request: CrossfadeRequest) {
        val current = active
        if (current?.request == request) {
            wake()
            return
        }
        if (hasSwitchedSession) return
        cancel()
        val entry = Entry(request)
        active = entry
        entry.job = scope().launch { run(entry) }
    }

    fun cancel() {
        val entry = active ?: return
        active = null
        entry.job?.cancel()
        entry.transition?.cancel()
        release(entry)
    }

    fun wake() {
        wakeups.trySend(Unit)
    }

    fun setTailPlaybackParameters(playbackParameters: PlaybackParameters) {
        active?.tail?.playbackParameters = playbackParameters
        wake()
    }

    @OptIn(UnstableApi::class)
    fun setTailSkipSilenceEnabled(enabled: Boolean) {
        active?.tail?.skipSilenceEnabled = enabled
    }

    private suspend fun run(entry: Entry) {
        val request = entry.request
        val timeline = request.plan.timeline
        try {
            while (true) {
                if (!isLocalPlayback() || !isOnOutgoingItem(request)) return
                val positionMs = sessionPlayer.currentPosition
                if (positionMs > timeline.latestSwitchPositionMs) {
                    finish(entry, CrossfadeOutcome.Abandoned(CrossfadeAbandonReason.SyncDeadline, sessionSwitched = false))
                    return
                }
                val allowed = isAllowed()
                if (allowed && entry.tail == null && positionMs >= timeline.prepareAtPositionMs) {
                    prepareTail(entry, startPositionMs = maxOf(timeline.syncAtPositionMs, positionMs))
                }
                if (entry.tail != null && positionMs >= timeline.syncAtPositionMs) break
                val nextMilestoneMs = if (entry.tail == null) timeline.prepareAtPositionMs else timeline.syncAtPositionMs
                awaitWakeOrTimeout(if (allowed) waitingPollMs(nextMilestoneMs - positionMs) else WAITING_FAR_POLL_MS)
            }
            val tail = checkNotNull(entry.tail)
            val transition =
                CrossfadeTransition(
                    plan = request.plan,
                    session = SessionDeck(request),
                    tail = TailDeck(tail, request),
                    baseVolume = baseVolume,
                )
            entry.transition = transition
            while (true) {
                if (!isLocalPlayback() || (!transition.sessionSwitched && !isAllowed())) transition.cancel()
                val nextFrameMs = transition.advance(clock())
                val outcome = transition.outcome
                if (outcome != null) {
                    finish(entry, outcome)
                    return
                }
                awaitWakeOrTimeout(nextFrameMs)
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            Timber.tag(TAG).w(error, "Crossfade transition failed")
            val transition = entry.transition
            val sessionSwitched = transition?.sessionSwitched == true
            runCatching { transition?.cancel() }
            finish(entry, CrossfadeOutcome.Abandoned(CrossfadeAbandonReason.TailFailed, sessionSwitched))
        } finally {
            release(entry)
            if (active === entry) active = null
        }
    }

    private fun finish(
        entry: Entry,
        outcome: CrossfadeOutcome,
    ) {
        if (active !== entry) return
        active = null
        release(entry)
        onFinished(outcome, entry.request.plan)
    }

    private fun prepareTail(
        entry: Entry,
        startPositionMs: Long,
    ) {
        val tail = createTailPlayer()
        entry.tail = tail
        tail.addListener(tailListener)
        tail.playbackParameters = sessionPlayer.playbackParameters
        tail.volume = 0f
        tail.playWhenReady = false
        tail.setMediaItem(entry.request.outgoingItem, startPositionMs)
        tail.prepare()
    }

    private fun release(entry: Entry) {
        val tail = entry.tail ?: return
        entry.tail = null
        runCatching { tail.removeListener(tailListener) }
        runCatching { tail.stop() }
        runCatching { tail.clearMediaItems() }
        runCatching { tail.release() }
    }

    private fun isLocalPlayback(): Boolean = sessionPlayer.deviceInfo.playbackType == DeviceInfo.PLAYBACK_TYPE_LOCAL

    private fun isOnOutgoingItem(request: CrossfadeRequest): Boolean =
        sessionPlayer.currentMediaItemIndex == request.outgoingIndex &&
            sessionPlayer.currentMediaItem?.mediaId == request.plan.outgoingMediaId

    private fun waitingPollMs(remainingMs: Long): Long =
        when {
            remainingMs > WAITING_FAR_THRESHOLD_MS -> WAITING_FAR_POLL_MS
            remainingMs > WAITING_NEAR_THRESHOLD_MS -> WAITING_NEAR_POLL_MS
            else -> WAITING_IMMINENT_POLL_MS
        }

    private suspend fun awaitWakeOrTimeout(timeoutMs: Long) {
        withTimeoutOrNull(timeoutMs.coerceAtLeast(1L)) { wakeups.receive() }
    }

    private inner class SessionDeck(
        private val request: CrossfadeRequest,
    ) : CrossfadeSessionDeck {
        override val currentMediaId: String?
            get() = sessionPlayer.currentMediaItem?.mediaId

        override val playWhenReady: Boolean
            get() = sessionPlayer.playWhenReady && sessionPlayer.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE

        override val playbackSpeed: Float
            get() = sessionPlayer.playbackParameters.speed

        override val isPlaying: Boolean
            get() = sessionPlayer.isPlaying

        override val positionMs: Long
            get() = sessionPlayer.currentPosition

        override var volume: Float
            get() = sessionPlayer.volume
            set(value) {
                sessionPlayer.volume = value.coerceIn(0f, maxVolume)
            }

        override fun switchTo(mediaId: String): Boolean {
            val index = request.incomingIndex
            if (sessionPlayer.currentMediaItemIndex != request.outgoingIndex) return false
            if (index !in 0 until sessionPlayer.mediaItemCount) return false
            if (sessionPlayer.getMediaItemAt(index).mediaId != mediaId) return false
            sessionPlayer.seekTo(index, 0L)
            return true
        }
    }

    private inner class TailDeck(
        private val tail: ExoPlayer,
        private val request: CrossfadeRequest,
    ) : CrossfadeTailDeck {
        override val isReady: Boolean
            get() = tail.playbackState == Player.STATE_READY

        override val isEnded: Boolean
            get() = tail.playbackState == Player.STATE_ENDED

        override val hasFailed: Boolean
            get() = tail.playerError != null

        override val durationMs: Long
            get() = tail.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: request.outgoingDurationMs

        override val isPlaying: Boolean
            get() = tail.isPlaying

        override val positionMs: Long
            get() = tail.currentPosition

        override var volume: Float
            get() = tail.volume
            set(value) {
                tail.volume = value.coerceIn(0f, maxVolume)
            }

        override fun setPlayWhenReady(playWhenReady: Boolean) {
            tail.playWhenReady = playWhenReady
        }

        override fun seekTo(positionMs: Long) {
            tail.seekTo(positionMs)
        }
    }

    private companion object {
        const val TAG = "Crossfade"
        const val WAITING_FAR_THRESHOLD_MS = 5_000L
        const val WAITING_NEAR_THRESHOLD_MS = 1_000L
        const val WAITING_FAR_POLL_MS = 1_000L
        const val WAITING_NEAR_POLL_MS = 250L
        const val WAITING_IMMINENT_POLL_MS = 50L
    }
}
