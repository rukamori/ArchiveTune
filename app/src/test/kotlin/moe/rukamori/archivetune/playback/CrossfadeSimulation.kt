/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import kotlin.math.abs
import kotlin.math.roundToLong
import kotlin.random.Random

internal class SimulationClock {
    var nowMs: Long = 0L
}

internal data class SimulatedMedia(
    val mediaId: String,
    val durationMs: Long,
)

internal data class PlayerLatency(
    val crossItemSeekToReadyMs: Long,
    val seekToReadyMs: Long,
    val bufferedStartToAudibleMs: Long,
    val resumeToAudibleMs: Long,
    val jitterMs: Long = 0L,
) {
    companion object {
        val Fast = PlayerLatency(crossItemSeekToReadyMs = 80L, seekToReadyMs = 40L, bufferedStartToAudibleMs = 25L, resumeToAudibleMs = 12L)
        val Typical = PlayerLatency(crossItemSeekToReadyMs = 140L, seekToReadyMs = 90L, bufferedStartToAudibleMs = 45L, resumeToAudibleMs = 20L)
        val Slow = PlayerLatency(crossItemSeekToReadyMs = 900L, seekToReadyMs = 600L, bufferedStartToAudibleMs = 120L, resumeToAudibleMs = 60L)
    }
}

internal class SimulatedPlayer(
    val name: String,
    private val clock: SimulationClock,
    private val playlist: List<SimulatedMedia>,
    private val latency: PlayerLatency,
    private val random: Random,
    private val speed: Float = 1f,
) : CrossfadeSessionDeck,
    CrossfadeTailDeck {
    private enum class State { IDLE, BUFFERING, READY, ENDED }

    private var state = State.IDLE
    private var index = 0
    private var basePositionMs = 0L
    private var audibleSinceMs: Long? = null
    private var readyAtMs: Long? = null
    private var audibleAtMs: Long? = null
    private var failed = false
    private var released = false

    override var playWhenReady: Boolean = false
        private set
    override var volume: Float = 1f
    override val playbackSpeed: Float get() = speed
    var seekCount: Int = 0
        private set
    var acceptsSwitches: Boolean = true

    override val currentMediaId: String? get() = playlist.getOrNull(index)?.mediaId
    override val isReady: Boolean get() = state == State.READY
    override val isEnded: Boolean get() = state == State.ENDED
    override val hasFailed: Boolean get() = failed
    override val isPlaying: Boolean get() = state == State.READY && playWhenReady && !released
    override val durationMs: Long get() = playlist[index].durationMs

    override val positionMs: Long
        get() {
            val since = audibleSinceMs ?: return basePositionMs
            return (basePositionMs + mediaElapsedMs(clock.nowMs - since)).coerceAtMost(durationMs)
        }

    val isAudible: Boolean
        get() = !released && !failed && isPlaying && audibleSinceMs != null && positionMs < durationMs

    private fun mediaElapsedMs(realElapsedMs: Long): Long = (realElapsedMs * speed).roundToLong()

    private fun jittered(valueMs: Long): Long =
        if (latency.jitterMs <= 0L) valueMs else (valueMs + random.nextLong(-latency.jitterMs, latency.jitterMs + 1)).coerceAtLeast(1L)

    fun load(positionMs: Long) {
        freeze()
        basePositionMs = positionMs.coerceIn(0L, durationMs)
        state = State.BUFFERING
        readyAtMs = clock.nowMs + jittered(latency.crossItemSeekToReadyMs)
        audibleAtMs = null
    }

    override fun switchTo(mediaId: String): Boolean {
        val target = playlist.indexOfFirst { it.mediaId == mediaId }
        if (!acceptsSwitches || target < 0) return false
        seek(target, 0L)
        return true
    }

    override fun seekTo(positionMs: Long) = seek(index, positionMs)

    private fun seek(
        targetIndex: Int,
        positionMs: Long,
    ) {
        val crossItem = targetIndex != index
        freeze()
        seekCount++
        index = targetIndex
        basePositionMs = positionMs.coerceIn(0L, durationMs)
        state = State.BUFFERING
        readyAtMs = clock.nowMs + jittered(if (crossItem) latency.crossItemSeekToReadyMs else latency.seekToReadyMs)
        audibleAtMs = null
    }

    override fun setPlayWhenReady(playWhenReady: Boolean) {
        if (playWhenReady == this.playWhenReady) return
        if (playWhenReady) {
            this.playWhenReady = true
            if (state == State.READY) audibleAtMs = clock.nowMs + jittered(latency.resumeToAudibleMs)
        } else {
            freeze()
            this.playWhenReady = false
            audibleAtMs = null
        }
    }

    fun fail() {
        freeze()
        failed = true
        state = State.IDLE
    }

    fun stall(durationMs: Long) {
        freeze()
        state = State.BUFFERING
        readyAtMs = clock.nowMs + durationMs
        audibleAtMs = null
    }

    fun release() {
        freeze()
        released = true
        playWhenReady = false
        state = State.IDLE
    }

    private fun freeze() {
        if (audibleSinceMs != null) {
            basePositionMs = positionMs
            audibleSinceMs = null
        }
    }

    fun step() {
        if (released || failed) return
        val now = clock.nowMs
        val readyAt = readyAtMs
        if (state == State.BUFFERING && readyAt != null && now >= readyAt) {
            state = State.READY
            readyAtMs = null
            if (playWhenReady) audibleAtMs = now + jittered(latency.bufferedStartToAudibleMs)
        }
        val audibleAt = audibleAtMs
        if (state == State.READY && playWhenReady && audibleSinceMs == null && audibleAt != null && now >= audibleAt) {
            audibleSinceMs = now
            audibleAtMs = null
        }
        val since = audibleSinceMs ?: return
        val rawPosition = basePositionMs + mediaElapsedMs(now - since)
        if (rawPosition < durationMs) return
        val overflow = rawPosition - durationMs
        if (index + 1 < playlist.size) {
            index++
            basePositionMs = overflow
            audibleSinceMs = now
        } else {
            basePositionMs = durationMs
            audibleSinceMs = null
            state = State.ENDED
        }
    }
}

internal data class HeardSource(
    val player: String,
    val mediaId: String,
    val positionMs: Long,
    val gain: Float,
)

internal class HeardAudio(
    private val speed: Float = 1f,
) {
    private val frames = mutableListOf<Pair<Long, List<HeardSource>>>()

    fun record(
        nowMs: Long,
        players: List<SimulatedPlayer>,
    ) {
        check(nowMs == frames.size.toLong()) { "frames must be recorded once per millisecond from 0" }
        frames +=
            nowMs to
            players
                .filter { it.isAudible && it.volume > 0f }
                .map { HeardSource(it.name, it.currentMediaId.orEmpty(), it.positionMs, it.volume) }
    }

    data class Jump(
        val atMs: Long,
        val mediaId: String,
        val fromPlayer: String,
        val toPlayer: String,
        val deltaMs: Long,
    )

    fun jumps(
        mediaId: String,
        audibleGain: Float = 0.05f,
        toleranceMs: Long = 1L,
    ): List<Jump> {
        val jumps = mutableListOf<Jump>()
        var last: HeardSource? = null
        var lastAtMs = 0L
        for ((t, heard) in frames) {
            val dominant = heard.filter { it.mediaId == mediaId && it.gain >= audibleGain }.maxByOrNull { it.gain } ?: continue
            val previous = last
            if (previous != null) {
                val delta = dominant.positionMs - (previous.positionMs + ((t - lastAtMs) * speed).roundToLong())
                if (abs(delta) > toleranceMs) jumps += Jump(t, mediaId, previous.player, dominant.player, delta)
            }
            last = dominant
            lastAtMs = t
        }
        return jumps
    }

    data class Silence(
        val fromMs: Long,
        val toMs: Long,
    ) {
        val lengthMs: Long get() = toMs - fromMs
    }

    fun silences(
        fromMs: Long,
        toMs: Long,
        powerFloor: Float = 0.1f,
    ): List<Silence> {
        val silences = mutableListOf<Silence>()
        var startedAt: Long? = null
        for ((t, heard) in frames) {
            if (t < fromMs || t > toMs) continue
            val power = heard.sumOf { (it.gain * it.gain).toDouble() }
            if (power < powerFloor) {
                if (startedAt == null) startedAt = t
            } else if (startedAt != null) {
                silences += Silence(startedAt, t)
                startedAt = null
            }
        }
        startedAt?.let { silences += Silence(it, toMs) }
        return silences
    }

    fun worstSameMediaOffsetMs(
        mediaId: String,
        minGain: Float = 0.05f,
    ): Long =
        frames.maxOfOrNull { (_, heard) ->
            val sources = heard.filter { it.mediaId == mediaId && it.gain >= minGain }
            if (sources.size < 2) 0L else sources.maxOf { it.positionMs } - sources.minOf { it.positionMs }
        } ?: 0L

    fun peakSameMediaAmplitude(mediaId: String): Float =
        frames.maxOfOrNull { (_, heard) ->
            heard.filter { it.mediaId == mediaId }.sumOf { it.gain.toDouble() }.toFloat()
        } ?: 0f

    fun firstHeardAtMs(mediaId: String): Long? = frames.firstOrNull { (_, heard) -> heard.any { it.mediaId == mediaId } }?.first

    fun lastHeardAtMs(mediaId: String): Long? = frames.lastOrNull { (_, heard) -> heard.any { it.mediaId == mediaId } }?.first

    fun heardAt(timeMs: Long): List<HeardSource> = frames.getOrNull(timeMs.toInt())?.second.orEmpty()

    val recordedMs: Long get() = frames.size.toLong()
}

internal class CrossfadeScenario(
    sessionLatency: PlayerLatency = PlayerLatency.Typical,
    tailLatency: PlayerLatency = sessionLatency,
    private val requestedFadeMs: Long = 5_000L,
    val outgoing: SimulatedMedia = SimulatedMedia("outgoing", 20_000L),
    val incoming: SimulatedMedia = SimulatedMedia("incoming", 20_000L),
    val later: SimulatedMedia = SimulatedMedia("later", 20_000L),
    private val baseVolumes: Map<String, Float> = emptyMap(),
    seed: Long = 7L,
    speed: Float = 1f,
) {
    val clock = SimulationClock()
    private val random = Random(seed)
    val session = SimulatedPlayer("session", clock, listOf(outgoing, incoming, later), sessionLatency, random, speed)
    val tail = SimulatedPlayer("tail", clock, listOf(outgoing), tailLatency, random, speed)
    val heard = HeardAudio(speed)
    val timeline: CrossfadeTimeline = checkNotNull(crossfadeTimeline(outgoing.durationMs, requestedFadeMs))

    var transition: CrossfadeTransition? = null
        private set
    var outcome: CrossfadeOutcome? = null
        private set
    var tailReleasedAtMs: Long? = null
        private set
    var tailPlayingWhileSessionPausedMs: Long = 0L
        private set
    private var tailPrepared = false
    private var nextAdvanceAtMs = 0L
    private val actions = sortedMapOf<Long, MutableList<() -> Unit>>()
    private val triggers = mutableListOf<Pair<() -> Boolean, () -> Unit>>()

    fun baseVolume(mediaId: String): Float = baseVolumes[mediaId] ?: 1f

    fun at(
        timeMs: Long,
        action: () -> Unit,
    ) {
        actions.getOrPut(timeMs) { mutableListOf() } += action
    }

    fun atFirst(
        condition: () -> Boolean,
        action: () -> Unit,
    ) {
        triggers += condition to action
    }

    val isHandingOver: Boolean
        get() = session.currentMediaId == outgoing.mediaId && session.volume > 0f && tail.volume > 0f && tail.isPlaying

    val isFading: Boolean
        get() = session.currentMediaId == incoming.mediaId && session.volume > 0.2f && tail.volume > 0.2f && tail.isPlaying

    fun setSessionPlayWhenReady(playWhenReady: Boolean) {
        session.setPlayWhenReady(playWhenReady)
        driveNow()
    }

    fun failTail() {
        tail.fail()
        driveNow()
    }

    fun cancelTransition() {
        checkNotNull(transition).cancel()
        driveTransition(clock.nowMs)
    }

    fun skipSessionTo(mediaId: String) {
        check(session.switchTo(mediaId))
        driveNow()
    }

    fun stallTail(durationMs: Long) {
        tail.stall(durationMs)
        driveNow()
    }

    private fun driveNow() {
        if (transition == null || outcome != null) return
        nextAdvanceAtMs = clock.nowMs
        driveTransition(clock.nowMs)
    }

    fun run(untilMs: Long = outgoing.durationMs + 12_000L): CrossfadeScenario {
        session.load(0L)
        session.setPlayWhenReady(true)
        session.volume = baseVolume(outgoing.mediaId)
        for (t in 0L..untilMs) {
            clock.nowMs = t
            actions.remove(t)?.forEach { it() }
            triggers.filter { (condition, _) -> condition() }.forEach { trigger ->
                triggers.remove(trigger)
                trigger.second()
            }
            session.step()
            tail.step()
            driveTransition(t)
            session.step()
            tail.step()
            heard.record(t, listOf(session, tail))
            if (tail.isPlaying && !session.playWhenReady) tailPlayingWhileSessionPausedMs++
        }
        return this
    }

    private fun driveTransition(nowMs: Long) {
        if (outcome != null) return
        val active = transition
        if (active == null) {
            if (session.currentMediaId != outgoing.mediaId) return
            if (!tailPrepared && session.positionMs >= timeline.prepareAtPositionMs) {
                tail.volume = 0f
                tail.load(timeline.syncAtPositionMs)
                tailPrepared = true
            }
            if (tailPrepared && session.positionMs >= timeline.syncAtPositionMs) {
                transition =
                    CrossfadeTransition(
                        plan = CrossfadePlan(outgoing.mediaId, incoming.mediaId, timeline),
                        session = session,
                        tail = tail,
                        baseVolume = ::baseVolume,
                    )
                nextAdvanceAtMs = nowMs
            } else {
                return
            }
        }
        val current = transition ?: return
        if (current.outcome == null && nowMs >= nextAdvanceAtMs) {
            nextAdvanceAtMs = nowMs + current.advance(nowMs).coerceAtLeast(1L)
        }
        val finished = current.outcome ?: return
        outcome = finished
        tail.release()
        tailReleasedAtMs = nowMs
        session.volume = baseVolume(session.currentMediaId.orEmpty())
    }
}
