/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

class SleepTimer(
    private val scope: CoroutineScope,
    val player: Player,
    private val service: MusicService,
) : Player.Listener {
    private var sleepTimerJob: Job? = null
    var triggerTime by mutableStateOf(-1L)
        private set
    var pauseWhenSongEnd by mutableStateOf(false)
        private set
    val isActive: Boolean
        get() = triggerTime != -1L || pauseWhenSongEnd

    fun start(
        minutes: Int,
        finishCurrentSong: Boolean,
        fadeOut: Boolean,
    ) {
        clear()
        val durationMs = minutes.coerceAtLeast(1).minutes.inWholeMilliseconds
        val target = System.currentTimeMillis() + durationMs
        triggerTime = target
        sleepTimerJob =
            scope.launch {
                if (finishCurrentSong) {
                    delay(durationMs)
                    triggerTime = -1L
                    pauseWhenSongEnd = true
                    if (fadeOut) fadeBeforeSongEnd()
                } else {
                    if (fadeOut) fadeUntil(target) else delay(durationMs)
                    service.pauseFromSleepTimer()
                }
            }
    }

    fun clear() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        pauseWhenSongEnd = false
        triggerTime = -1L
        service.setSleepTimerVolumeFactor(1f)
    }

    private suspend fun fadeUntil(target: Long) {
        val fadeStart = target - FadeOutDurationMs
        val wait = fadeStart - System.currentTimeMillis()
        if (wait > 0) delay(wait)
        while (true) {
            val remaining = target - System.currentTimeMillis()
            if (remaining <= 0L) break
            service.setSleepTimerVolumeFactor(remaining.toFloat() / FadeOutDurationMs)
            delay(FadeOutFrameMs)
        }
        service.setSleepTimerVolumeFactor(0f)
    }

    private suspend fun fadeBeforeSongEnd() {
        while (pauseWhenSongEnd) {
            val duration = player.duration
            if (duration == C.TIME_UNSET || duration <= 0L) {
                service.setSleepTimerVolumeFactor(1f)
                delay(FadeOutIdleCheckMs)
                continue
            }
            val remaining = (duration - player.currentPosition).coerceAtLeast(0L)
            service.setSleepTimerVolumeFactor((remaining.toFloat() / FadeOutDurationMs).coerceIn(0f, 1f))
            val speed = player.playbackParameters.speed.takeIf { it > 0f } ?: 1f
            val untilFade = ((remaining - FadeOutDurationMs) / speed).toLong()
            delay(if (untilFade > 0L) untilFade.coerceAtMost(FadeOutIdleCheckMs) else FadeOutFrameMs)
        }
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        if (pauseWhenSongEnd) {
            service.pauseFromSleepTimer()
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == Player.STATE_ENDED && pauseWhenSongEnd) {
            service.pauseFromSleepTimer()
        }
    }

    companion object {
        const val FadeOutDurationMs = 10_000L
        private const val FadeOutFrameMs = 50L
        private const val FadeOutIdleCheckMs = 500L
    }
}
