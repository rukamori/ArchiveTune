/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber

internal data class CrossfadePlan(
    val outgoingMediaId: String,
    val outgoingIndex: Int,
    val incomingMediaId: String,
    val incomingIndex: Int,
    val triggerPositionMs: Long,
    val fadeDurationMs: Long,
) {
    val prepareAtPositionMs: Long
        get() = (triggerPositionMs - CrossfadeHandler.PREPARE_LEAD_MS).coerceAtLeast(0L)

    companion object {
        fun triggerPositionMs(
            durationMs: Long,
            fadeDurationMs: Long,
        ): Long? {
            if (durationMs == C.TIME_UNSET || fadeDurationMs <= 0L || durationMs <= fadeDurationMs) return null
            return durationMs - fadeDurationMs
        }
    }
}

@OptIn(UnstableApi::class)
internal class CrossfadeHandler(
    private val scope: () -> CoroutineScope,
    private val host: SwappableLocalPlayer,
    private val isLocalPlayback: () -> Boolean,
    private val canStart: () -> Boolean,
    private val createPlayer: () -> ExoPlayer,
    private val activatePlayer: (target: ExoPlayer, source: ExoPlayer) -> Unit,
    private val releasePlayer: (ExoPlayer) -> Unit,
    private val volumeFor: (mediaId: String) -> Float,
    private val onFadeFinished: () -> Unit,
    private val onAbandoned: (CrossfadePlan) -> Unit,
) {
    private class ActiveFade(
        val outgoing: ExoPlayer,
        val active: ExoPlayer,
        val outgoingMediaId: String,
        val incomingMediaId: String,
        val outgoingStartMs: Long,
        val outgoingEndMs: Long,
        val fadeDurationMs: Long,
    ) {
        var job: Job? = null
    }

    private var scheduledPlan: CrossfadePlan? = null
    private var prepareMessage: PlayerMessage? = null
    private var startMessage: PlayerMessage? = null
    private var incoming: ExoPlayer? = null
    private var incomingPlan: CrossfadePlan? = null
    private var fade: ActiveFade? = null

    val isFading: Boolean
        get() = fade != null

    private val incomingListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (!events.contains(Player.EVENT_PLAYER_ERROR)) return
                val error = player.playerError ?: return
                Timber.tag(TAG).w(error, "Crossfade incoming player failed")
                scope().launch {
                    if (incoming !== player) return@launch
                    val plan = incomingPlan
                    clearSchedule()
                    plan?.let(onAbandoned)
                }
            }
        }

    private val outgoingListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                val ended = player.playbackState == Player.STATE_ENDED || player.playbackState == Player.STATE_IDLE
                if (!events.containsAny(Player.EVENT_PLAYBACK_STATE_CHANGED, Player.EVENT_PLAYER_ERROR)) return
                if (!ended && player.playerError == null) return
                scope().launch {
                    if (fade?.outgoing === player) finishFade()
                }
            }
        }

    private val activeListener =
        object : Player.Listener {
            override fun onEvents(
                player: Player,
                events: Player.Events,
            ) {
                if (!events.containsAny(
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_PLAYBACK_SUPPRESSION_REASON_CHANGED,
                        Player.EVENT_PLAYBACK_PARAMETERS_CHANGED,
                    )
                ) {
                    return
                }
                fade?.takeIf { it.active === player }?.let(::syncOutgoingTransport)
            }
        }

    fun schedule(plan: CrossfadePlan?) {
        if (plan != null && plan == scheduledPlan && startMessage?.isCanceled == false) {
            val position = host.exoPlayer.currentPosition
            if (position < plan.triggerPositionMs) {
                if (position >= plan.prepareAtPositionMs && incoming == null) prepareIncoming(plan)
                return
            }
        }
        clearSchedule()
        if (plan == null || !host.canSwap) return
        val player = host.exoPlayer
        if (!matchesSource(plan, player)) return
        val position = player.currentPosition
        if (position >= plan.triggerPositionMs) return
        scheduledPlan = plan
        if (position >= plan.prepareAtPositionMs) {
            prepareIncoming(plan)
        } else {
            prepareMessage =
                player.sendMessageAt(plan.outgoingIndex, plan.prepareAtPositionMs) {
                    if (scheduledPlan == plan) prepareIncoming(plan)
                }
        }
        startMessage =
            player.sendMessageAt(plan.outgoingIndex, plan.triggerPositionMs) {
                if (scheduledPlan == plan) start(plan)
            }
    }

    fun clearSchedule() {
        prepareMessage?.cancel()
        prepareMessage = null
        startMessage?.cancel()
        startMessage = null
        scheduledPlan = null
        releaseIncoming()
    }

    fun finishFade() {
        finishFade(notify = true)
    }

    fun cancel() {
        clearSchedule()
        finishFade(notify = false)
    }

    private fun prepareIncoming(plan: CrossfadePlan) {
        prepareMessage?.cancel()
        prepareMessage = null
        val source = host.exoPlayer
        if (!matchesSource(plan, source)) return
        releaseIncoming()
        val next =
            runCatching(createPlayer)
                .onFailure { Timber.tag(TAG).e(it, "Unable to create crossfade player") }
                .getOrNull() ?: return
        next.addListener(incomingListener)
        next.volume = 0f
        next.playWhenReady = false
        loadQueue(next, source, plan)
        next.prepare()
        incoming = next
        incomingPlan = plan
    }

    private fun start(plan: CrossfadePlan) {
        startMessage = null
        prepareMessage?.cancel()
        prepareMessage = null
        scheduledPlan = null
        val source = host.exoPlayer
        if (!source.playWhenReady || !isLocalPlayback() || !canStart() || !matchesSource(plan, source)) {
            releaseIncoming()
            return
        }
        finishFade(notify = false)

        val prepared = incoming?.takeIf { incomingPlan == plan && it.playerError == null }
        if (prepared == null) releaseIncoming()
        incoming = null
        incomingPlan = null
        val next =
            prepared?.also {
                if (queueMatches(it, source, plan)) {
                    syncQueueModes(it, source)
                } else {
                    loadQueue(it, source, plan)
                }
            } ?: runCatching(createPlayer)
                .onFailure { Timber.tag(TAG).e(it, "Unable to create crossfade player") }
                .getOrNull()
                ?.also {
                    loadQueue(it, source, plan)
                    it.prepare()
                }
        if (next == null) {
            onAbandoned(plan)
            return
        }
        next.removeListener(incomingListener)
        activatePlayer(next, source)

        val outgoingEndMs = source.duration.takeIf { it != C.TIME_UNSET && it > 0L } ?: (plan.triggerPositionMs + plan.fadeDurationMs)
        val state =
            ActiveFade(
                outgoing = source,
                active = next,
                outgoingMediaId = plan.outgoingMediaId,
                incomingMediaId = plan.incomingMediaId,
                outgoingStartMs = source.currentPosition.coerceAtMost(outgoingEndMs),
                outgoingEndMs = outgoingEndMs,
                fadeDurationMs = plan.fadeDurationMs,
            )
        fade = state
        next.volume = 0f
        next.playWhenReady = true
        if (!host.swapTo(next)) {
            fade = null
            releasePlayer(next)
            onAbandoned(plan)
            return
        }
        if (fade !== state) return

        retireOutgoing(source)
        source.addListener(outgoingListener)
        next.addListener(activeListener)
        syncOutgoingTransport(state)
        state.job = scope().launch { runFade(state) }
    }

    private suspend fun runFade(state: ActiveFade) {
        while (currentCoroutineContext().isActive && fade === state) {
            if (!isLocalPlayback()) break
            val outgoing = state.outgoing
            if (outgoing.playbackState == Player.STATE_ENDED || outgoing.playbackState == Player.STATE_IDLE) break
            val active = host.exoPlayer
            if (active !== state.active || active.currentMediaItem?.mediaId != state.incomingMediaId) break

            val outgoingProgress = progress(outgoing.currentPosition - state.outgoingStartMs, state.outgoingEndMs - state.outgoingStartMs)
            val incomingProgress = maxOf(outgoingProgress, progress(active.currentPosition, state.fadeDurationMs))
            val fadeOut = (1f - outgoingProgress) * (1f - outgoingProgress)
            val fadeIn = 1f - (1f - incomingProgress) * (1f - incomingProgress)
            outgoing.volume = volumeFor(state.outgoingMediaId) * fadeOut
            active.volume = volumeFor(state.incomingMediaId) * fadeIn
            if (outgoingProgress >= 1f) break

            delay(if (active.playWhenReady) FRAME_MS else IDLE_FRAME_MS)
        }
        if (fade === state) finishFade(notify = true)
    }

    private fun finishFade(notify: Boolean) {
        val state = fade ?: return
        fade = null
        state.job?.cancel()
        state.job = null
        state.active.removeListener(activeListener)
        state.outgoing.removeListener(outgoingListener)
        releasePlayer(state.outgoing)
        if (notify) onFadeFinished()
    }

    private fun syncOutgoingTransport(state: ActiveFade) {
        val active = state.active
        state.outgoing.playbackParameters = active.playbackParameters
        state.outgoing.playWhenReady =
            active.playWhenReady && active.playbackSuppressionReason == Player.PLAYBACK_SUPPRESSION_REASON_NONE
    }

    private fun retireOutgoing(player: ExoPlayer) {
        player.repeatMode = Player.REPEAT_MODE_OFF
        player.shuffleModeEnabled = false
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex + 1 < player.mediaItemCount) player.removeMediaItems(currentIndex + 1, player.mediaItemCount)
        if (currentIndex > 0) player.removeMediaItems(0, currentIndex)
    }

    private fun releaseIncoming() {
        val player = incoming ?: return
        incoming = null
        incomingPlan = null
        player.removeListener(incomingListener)
        releasePlayer(player)
    }

    private fun loadQueue(
        target: ExoPlayer,
        source: ExoPlayer,
        plan: CrossfadePlan,
    ) {
        target.setMediaItems(source.mediaItems(), plan.incomingIndex, 0L)
        syncQueueModes(target, source)
    }

    private fun syncQueueModes(
        target: ExoPlayer,
        source: ExoPlayer,
    ) {
        if (source.shuffleOrder.length == target.mediaItemCount) target.setShuffleOrder(source.shuffleOrder)
        target.repeatMode = source.repeatMode
        target.shuffleModeEnabled = source.shuffleModeEnabled
        target.playbackParameters = source.playbackParameters
    }

    private fun matchesSource(
        plan: CrossfadePlan,
        source: ExoPlayer,
    ): Boolean =
        source.currentMediaItemIndex == plan.outgoingIndex &&
            source.currentMediaItem?.mediaId == plan.outgoingMediaId &&
            plan.incomingIndex in 0 until source.mediaItemCount &&
            source.getMediaItemAt(plan.incomingIndex).mediaId == plan.incomingMediaId

    private fun queueMatches(
        target: ExoPlayer,
        source: ExoPlayer,
        plan: CrossfadePlan,
    ): Boolean {
        if (target.mediaItemCount != source.mediaItemCount) return false
        if (target.currentMediaItemIndex != plan.incomingIndex) return false
        for (index in 0 until source.mediaItemCount) {
            if (target.getMediaItemAt(index).mediaId != source.getMediaItemAt(index).mediaId) return false
        }
        return true
    }

    private fun ExoPlayer.mediaItems(): List<MediaItem> = List(mediaItemCount) { getMediaItemAt(it) }

    private fun ExoPlayer.sendMessageAt(
        mediaItemIndex: Int,
        positionMs: Long,
        action: () -> Unit,
    ): PlayerMessage =
        createMessage { _, _ -> action() }
            .setLooper(Looper.getMainLooper())
            .setPosition(mediaItemIndex, positionMs)
            .send()

    private fun progress(
        elapsedMs: Long,
        totalMs: Long,
    ): Float = if (totalMs <= 0L) 1f else (elapsedMs.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)

    companion object {
        const val PREPARE_LEAD_MS = 10_000L
        private const val TAG = "Crossfade"
        private const val FRAME_MS = 25L
        private const val IDLE_FRAME_MS = 150L
    }
}
