/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/BitPerfectAudioSink.kt, Copyright (C) 2026 BitChord Project,
 * licensed under the Apache License, Version 2.0.
 */

package moe.rukamori.archivetune.playback.bitperfect

import android.media.AudioTrack
import android.os.SystemClock
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.AuxEffectInfo
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.exoplayer.audio.AudioSink
import timber.log.Timber
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

@OptIn(UnstableApi::class)
internal class BitPerfectAudioSink(
    private val output: BitPerfectOutput,
) : AudioSink {
    private class Input(
        val format: Format,
    ) {
        val encoding = format.pcmEncoding
        val sampleRate = format.sampleRate
        val channelCount = format.channelCount
        val sourceBits = BitPerfectPcm.sourceBits(encoding) ?: 0
        val frameBytes = BitPerfectPcm.bytesPerSample(encoding) * channelCount

        fun sameStream(other: Input?) =
            other != null &&
                encoding == other.encoding &&
                sampleRate == other.sampleRate &&
                channelCount == other.channelCount
    }

    private data class Wire(
        val sampleRate: Int,
        val channelCount: Int,
        val outputBits: Int,
        val validBits: Int,
        val deviceName: String,
    ) {
        constructor(transport: BitPerfectTransport) : this(
            transport.sampleRate,
            transport.channelCount,
            transport.outputBits,
            transport.validBits,
            transport.deviceName,
        )
    }

    private var listener: AudioSink.Listener? = null
    private var audioAttributes = AudioAttributes.DEFAULT
    private var configured: Input? = null
    private var current: Input? = null
    private var transport: BitPerfectTransport? = null
    private var transportStarted = false
    private var outputBuffer: ByteBuffer = ByteBuffer.allocateDirect(0)
    private var outputFrameBytes = 0
    private var writtenFrames = 0L
    private var transportFrames = 0L
    private var framesBase = 0L
    private var recent: RecentBytes? = null
    private var pausedAtMs = NOT_PAUSED
    private var suspended = false
    private var suspendedWire: Wire? = null
    private var resumeTail: ByteBuffer? = null
    private var submittedFrames = 0L
    private var startMediaTimeUs = C.TIME_UNSET
    private var needsResync = false
    private var currentInput: ByteBuffer? = null
    private var playing = false
    private var endOfStream = false
    private var stopped = false
    private var exact = true
    private var muted = false
    private var advancingReported = false
    private var pendingFailure: BitPerfectTransportException? = null

    fun holdsNormalOutput(): Boolean = output.normalOutputPending()

    fun canAccept(
        format: Format,
        reportUnsupported: Boolean = false,
    ): Boolean {
        if (format.sampleMimeType != MimeTypes.AUDIO_RAW) return false
        if (format.encoderDelay > 0 || format.encoderPadding > 0) return false
        val bits = BitPerfectPcm.sourceBits(format.pcmEncoding) ?: return false
        if (format.channelCount !in 1..2 || format.sampleRate <= 0) return false
        return output.canOpen(format.sampleRate, format.channelCount, bits, this, reportUnsupported)
    }

    override fun setListener(listener: AudioSink.Listener) {
        this.listener = listener
    }

    override fun supportsFormat(format: Format): Boolean = canAccept(format)

    override fun getFormatSupport(format: Format): Int =
        if (canAccept(format)) AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY else AudioSink.SINK_FORMAT_UNSUPPORTED

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        val format = audioSinkConfig.format
        val mapping = audioSinkConfig.outputChannelMapping
        if (mapping != null && (0 until mapping.length()).any { mapping[it] != it }) {
            throw AudioSink.ConfigurationException("Channel remapping is not bit-perfect", format)
        }
        if (!canAccept(format, reportUnsupported = true)) {
            throw AudioSink.ConfigurationException("Bit-perfect output unavailable", format)
        }
        configured = Input(format)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        pendingFailure?.let { failure ->
            pendingFailure = null
            throw writeException(failure)
        }
        releaseIfIdle()
        val input = configured ?: return true
        if (suspended) {
            if (!playing) return false
            try {
                resume()
            } catch (error: BitPerfectTransportException) {
                throw AudioSink.InitializationException(
                    "Bit-perfect output could not resume: ${error.message}",
                    AudioTrack.STATE_UNINITIALIZED,
                    input.format,
                    true,
                    error,
                )
            }
        }
        if (transport != null && !input.sameStream(current)) {
            if (!drainForReconfiguration()) return false
            closeTransport()
        }
        val active = transport ?: open(input)

        if (buffer !== currentInput) {
            currentInput = buffer
            syncTimestamp(input, presentationTimeUs)
        }
        try {
            if (!writeResumeTail(active)) return false
            if (outputBuffer.hasRemaining()) {
                writeOutput(active)
                if (outputBuffer.hasRemaining()) return false
            }
            while (buffer.remaining() >= input.frameBytes) {
                outputBuffer.clear()
                val before = buffer.position()
                if (!BitPerfectPcm.convert(buffer, input.encoding, input.channelCount, active.outputBits, outputBuffer)) {
                    markInexact(active)
                }
                submittedFrames += (buffer.position() - before) / input.frameBytes
                outputBuffer.flip()
                if (muted || output.silenced) silence(outputBuffer)
                writeOutput(active)
                if (outputBuffer.hasRemaining()) return false
            }
        } catch (error: BitPerfectTransportException) {
            throw writeException(error)
        }
        buffer.position(buffer.limit())
        currentInput = null
        return true
    }

    override fun play() {
        playing = true
        pausedAtMs = NOT_PAUSED
        startIfReady()
    }

    override fun pause() {
        playing = false
        transportStarted = false
        pausedAtMs = SystemClock.elapsedRealtime()
        runCatching { transport?.pause() }
    }

    override fun handleDiscontinuity() {
        needsResync = true
    }

    override fun playToEndOfStream() {
        endOfStream = true
        releaseIfIdle()
        if (suspended) {
            if (!playing) return
            try {
                resume()
            } catch (error: BitPerfectTransportException) {
                throw writeException(error)
            }
        }
        val active = transport ?: return
        try {
            if (!writeResumeTail(active)) return
            if (outputBuffer.hasRemaining()) writeOutput(active)
            if (outputBuffer.hasRemaining()) return
            stopWhenStarted(active)
        } catch (error: BitPerfectTransportException) {
            throw writeException(error)
        }
    }

    override fun isEnded(): Boolean = (transport == null && !suspended) || (endOfStream && stopped && !hasPendingData())

    override fun hasPendingData(): Boolean {
        val held = outputBuffer.hasRemaining() || resumeTail != null
        if (suspended) return held
        val active = transport ?: return false
        return held || playedFrames(active) < writtenFrames
    }

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long {
        val active = transport ?: return AudioSink.CURRENT_POSITION_NOT_SET
        if (startMediaTimeUs == C.TIME_UNSET) return AudioSink.CURRENT_POSITION_NOT_SET
        val played = minOf(playedFrames(active), writtenFrames)
        val playedUs = Util.sampleCountToDurationUs(played, active.sampleRate)
        if (!advancingReported && playing && played > 0) {
            advancingReported = true
            listener?.onPositionAdvancing(System.currentTimeMillis() - playedUs / 1000)
        }
        return startMediaTimeUs + playedUs
    }

    override fun flush() {
        runCatching { transport?.flush() }
        transportStarted = false
        pendingFailure = null
        resetCounters()
    }

    override fun reset() {
        flush()
        closeTransport()
        configured = null
    }

    override fun release() {
        reset()
    }

    override fun getAudioTrackBufferSizeUs(): Long {
        val active = transport ?: return C.TIME_UNSET
        return Util.sampleCountToDurationUs(active.bufferSizeFrames, active.sampleRate)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) = Unit

    override fun getPlaybackParameters(): PlaybackParameters = PlaybackParameters.DEFAULT

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) = Unit

    override fun getSkipSilenceEnabled(): Boolean = false

    override fun setVolume(volume: Float) {
        muted = volume <= 0f
    }

    override fun setAuxEffectInfo(auxEffectInfo: AuxEffectInfo) = Unit

    override fun setAudioSessionId(audioSessionId: Int) = Unit

    override fun setAudioAttributes(audioAttributes: AudioAttributes) {
        this.audioAttributes = audioAttributes
    }

    override fun getAudioAttributes(): AudioAttributes = audioAttributes

    override fun enableTunnelingV21() = Unit

    override fun disableTunneling() = Unit

    private fun open(input: Input): BitPerfectTransport {
        val opened =
            try {
                output.open(input.sampleRate, input.channelCount, input.sourceBits, this)
            } catch (error: BitPerfectTransportException) {
                output.markFailed(input.sampleRate, input.channelCount, input.sourceBits, error.message.orEmpty())
                throw AudioSink.InitializationException(
                    "Bit-perfect output unavailable: ${error.message}",
                    AudioTrack.STATE_UNINITIALIZED,
                    input.format,
                    true,
                    error,
                )
            }
        transport = opened
        current = input
        outputFrameBytes = opened.channelCount * (opened.outputBits / 8)
        val capacity = OUTPUT_CHUNK_FRAMES * outputFrameBytes
        if (outputBuffer.capacity() < capacity) {
            outputBuffer = ByteBuffer.allocateDirect(capacity).order(ByteOrder.LITTLE_ENDIAN)
        }
        recent =
            if (opened.releasesWhenIdle) {
                val frames = (opened.bufferSizeFrames * 2).coerceAtMost(Int.MAX_VALUE.toLong() / outputFrameBytes)
                val bytes = frames.toInt() * outputFrameBytes
                recent?.takeIf { it.capacity == bytes } ?: RecentBytes(bytes)
            } else {
                null
            }
        exact = true
        resetCounters()
        Timber.tag(TAG).i(
            "Opened bit-perfect USB output on %s: %d Hz, %d-bit source as %d-bit",
            opened.deviceName,
            opened.sampleRate,
            input.sourceBits,
            opened.outputBits,
        )
        output.publishActive(opened, exact)
        return opened
    }

    private fun closeTransport() {
        val wasSuspended = suspended
        suspended = false
        suspendedWire = null
        resumeTail = null
        if (wasSuspended) current = null
        val active = transport ?: return
        runCatching { active.release() }
        transport = null
        current = null
        transportStarted = false
        outputBuffer.clear().limit(0)
        output.release(this)
    }

    private fun releaseIfIdle() {
        val active = transport ?: return
        if (playing || !active.releasesWhenIdle) return
        val now = SystemClock.elapsedRealtime()
        if (pausedAtMs == NOT_PAUSED) pausedAtMs = now
        if (now - pausedAtMs >= IDLE_RELEASE_MS) suspend(active)
    }

    private fun suspend(active: BitPerfectTransport) {
        val history = recent ?: return
        val played = runCatching { active.playedFrames() }.getOrDefault(0L).coerceIn(0L, transportFrames)
        val keptBytes = minOf((transportFrames - played) * outputFrameBytes, history.size.toLong()).toInt()
        val unwritten = resumeTail
        val tail = ByteBuffer.allocateDirect(keptBytes + (unwritten?.remaining() ?: 0)).order(ByteOrder.LITTLE_ENDIAN)
        history.copyLast(keptBytes, tail)
        unwritten?.let(tail::put)
        tail.flip()
        framesBase = writtenFrames - tail.remaining() / outputFrameBytes
        resumeTail = tail.takeIf { it.hasRemaining() }
        suspendedWire = Wire(active)
        suspended = true
        Timber.tag(TAG).i("Released %s after a long pause", active.deviceName)
        runCatching { active.release() }
        transport = null
        transportStarted = false
        output.release(this)
    }

    private fun resume() {
        val wire = suspendedWire
        suspended = false
        suspendedWire = null
        if (resumeTail == null && !outputBuffer.hasRemaining()) {
            current = null
            return
        }
        val previous = requireNotNull(current)
        val opened =
            try {
                output.open(previous.sampleRate, previous.channelCount, previous.sourceBits, this)
            } catch (error: BitPerfectTransportException) {
                abandonSuspendedAudio()
                throw error
            }
        if (Wire(opened) != wire) {
            runCatching { opened.release() }
            output.release(this)
            abandonSuspendedAudio()
            throw BitPerfectTransportException("device_changed")
        }
        transport = opened
        transportFrames = 0L
        recent?.clear()
        stopped = false
        Timber.tag(TAG).i("Reopened %s after a long pause", opened.deviceName)
        output.publishActive(opened, exact)
    }

    private fun abandonSuspendedAudio() {
        current = null
        resumeTail = null
        outputBuffer.clear().limit(0)
    }

    private fun writeResumeTail(active: BitPerfectTransport): Boolean {
        val tail = resumeTail ?: return true
        handOver(active, tail)
        startIfReady()
        if (tail.hasRemaining()) return false
        resumeTail = null
        return true
    }

    private fun handOver(
        active: BitPerfectTransport,
        buffer: ByteBuffer,
    ): Int {
        val start = buffer.position()
        val taken = active.write(buffer)
        if (taken > 0) {
            recent?.record(buffer, start, taken)
            transportFrames += taken / outputFrameBytes
        }
        return taken
    }

    private fun playedFrames(active: BitPerfectTransport): Long = framesBase + active.playedFrames()

    private fun drainForReconfiguration(): Boolean {
        val active = transport ?: return true
        try {
            if (!writeResumeTail(active)) return false
            if (outputBuffer.hasRemaining()) writeOutput(active)
            if (outputBuffer.hasRemaining()) return false
            if (!stopWhenStarted(active)) return false
        } catch (error: BitPerfectTransportException) {
            throw writeException(error)
        }
        return playedFrames(active) >= writtenFrames
    }

    private fun stopWhenStarted(active: BitPerfectTransport): Boolean {
        if (stopped) return true
        if (transportFrames > 0L) {
            if (!transportStarted) return false
            active.stop()
        }
        stopped = true
        return true
    }

    private fun writeOutput(active: BitPerfectTransport) {
        val taken = handOver(active, outputBuffer)
        if (taken > 0) writtenFrames += taken / outputFrameBytes
        startIfReady()
    }

    private fun silence(buffer: ByteBuffer) {
        for (index in buffer.position() until buffer.limit()) buffer.put(index, 0)
    }

    private fun startIfReady() {
        val active = transport ?: return
        if (!playing || transportStarted || transportFrames == 0L) return
        try {
            active.play()
            transportStarted = true
        } catch (error: BitPerfectTransportException) {
            pendingFailure = error
        }
    }

    private fun syncTimestamp(
        input: Input,
        presentationTimeUs: Long,
    ) {
        if (startMediaTimeUs == C.TIME_UNSET) {
            startMediaTimeUs = presentationTimeUs
            needsResync = false
            return
        }
        val expectedUs = startMediaTimeUs + Util.sampleCountToDurationUs(submittedFrames, input.sampleRate)
        if (needsResync || abs(expectedUs - presentationTimeUs) > MAX_TIMESTAMP_DRIFT_US) {
            if (expectedUs != presentationTimeUs) {
                startMediaTimeUs += presentationTimeUs - expectedUs
                listener?.onPositionDiscontinuity()
            }
            needsResync = false
        }
    }

    private fun markInexact(active: BitPerfectTransport) {
        if (!exact) return
        exact = false
        Timber.tag(TAG).i("Source is not integer PCM; samples are rounded to the DAC depth")
        output.publishActive(active, exact)
    }

    private fun resetCounters() {
        outputBuffer.clear().limit(0)
        writtenFrames = 0L
        transportFrames = 0L
        framesBase = 0L
        recent?.clear()
        resumeTail = null
        submittedFrames = 0L
        startMediaTimeUs = C.TIME_UNSET
        needsResync = false
        currentInput = null
        endOfStream = false
        stopped = false
        advancingReported = false
    }

    private fun writeException(error: BitPerfectTransportException): AudioSink.WriteException {
        Timber.tag(TAG).w("Bit-perfect output stopped: %s", error.message)
        val format = current?.format ?: configured?.format ?: Format.Builder().build()
        current?.let { output.markFailed(it.sampleRate, it.channelCount, it.sourceBits, error.message.orEmpty()) }
        return AudioSink.WriteException(AudioTrack.ERROR_DEAD_OBJECT, format, true)
    }

    private companion object {
        const val TAG = "BitPerfectAudioSink"
        const val OUTPUT_CHUNK_FRAMES = 4096
        const val MAX_TIMESTAMP_DRIFT_US = 200_000L
        const val IDLE_RELEASE_MS = 30_000L
        const val NOT_PAUSED = -1L
    }
}

private class RecentBytes(
    val capacity: Int,
) {
    private val ring = ByteBuffer.allocateDirect(capacity)
    private var end = 0

    var size = 0
        private set

    fun clear() {
        end = 0
        size = 0
    }

    fun record(
        source: ByteBuffer,
        from: Int,
        count: Int,
    ) {
        if (capacity == 0) return
        var left = minOf(count, capacity)
        var at = from + count - left
        size = minOf(capacity, size + left)
        val view = source.duplicate()
        while (left > 0) {
            val chunk = minOf(left, capacity - end)
            view.limit(at + chunk)
            view.position(at)
            ring.position(end)
            ring.put(view)
            end = (end + chunk) % capacity
            at += chunk
            left -= chunk
        }
    }

    fun copyLast(
        count: Int,
        target: ByteBuffer,
    ) {
        require(count in 0..size) { "Only $size bytes recorded" }
        if (count == 0) return
        var at = (end - count + capacity) % capacity
        var left = count
        val view = ring.duplicate()
        while (left > 0) {
            val chunk = minOf(left, capacity - at)
            view.limit(at + chunk)
            view.position(at)
            target.put(view)
            at = (at + chunk) % capacity
            left -= chunk
        }
    }
}
