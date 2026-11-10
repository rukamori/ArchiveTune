/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/BitPerfectRoutingSink.kt, Copyright (C) 2026 BitChord Project,
 * licensed under the Apache License, Version 2.0.
 */

package moe.rukamori.archivetune.playback.bitperfect

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.ForwardingAudioSink
import timber.log.Timber
import java.nio.ByteBuffer

@OptIn(UnstableApi::class)
internal class BitPerfectRoutingSink(
    private val normal: AudioSink,
    private val bitPerfect: BitPerfectAudioSink,
) : ForwardingAudioSink(normal) {
    private var active: AudioSink = normal
    private var retiring: AudioSink? = null
    private var lastConfig: AudioSink.AudioSinkConfig? = null
    private var playing = false
    private var requestedParameters = PlaybackParameters.DEFAULT
    private var skipSilenceRequested = false

    override fun configure(audioSinkConfig: AudioSink.AudioSinkConfig) {
        lastConfig = audioSinkConfig
        dropRetiring()
        switchTo(configureTarget(audioSinkConfig))
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int,
    ): Boolean {
        if (!finishRetiring()) return false
        if (active !== bitPerfect) {
            if (bitPerfect.holdsNormalOutput()) return false
            return normal.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        return try {
            bitPerfect.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        } catch (error: AudioSink.InitializationException) {
            fallBack(error)
            false
        } catch (error: AudioSink.WriteException) {
            fallBack(error)
            false
        }
    }

    override fun playToEndOfStream() {
        if (!finishRetiring()) return
        if (active !== bitPerfect) {
            normal.playToEndOfStream()
            return
        }
        try {
            bitPerfect.playToEndOfStream()
        } catch (error: AudioSink.WriteException) {
            fallBack(error)
        }
    }

    override fun isEnded(): Boolean = retiring == null && active.isEnded()

    override fun hasPendingData(): Boolean = retiring?.hasPendingData() == true || active.hasPendingData()

    override fun getCurrentPositionUs(sourceEnded: Boolean): Long = (retiring ?: active).getCurrentPositionUs(sourceEnded)

    override fun play() {
        playing = true
        (retiring ?: active).play()
    }

    override fun pause() {
        playing = false
        retiring?.pause()
        active.pause()
    }

    override fun flush() {
        dropRetiring()
        active.flush()
        reselectOutput()
    }

    override fun reset() {
        retiring = null
        normal.reset()
        bitPerfect.reset()
    }

    override fun release() {
        retiring = null
        normal.release()
        bitPerfect.release()
    }

    override fun handleDiscontinuity() = active.handleDiscontinuity()

    override fun supportsFormat(format: Format): Boolean = wantsBitPerfect(format) || normal.supportsFormat(format)

    override fun getFormatSupport(format: Format): Int =
        if (wantsBitPerfect(format)) {
            AudioSink.SINK_FORMAT_SUPPORTED_DIRECTLY
        } else {
            normal.getFormatSupport(format)
        }

    override fun getAudioTrackBufferSizeUs(): Long = active.getAudioTrackBufferSizeUs()

    override fun setListener(listener: AudioSink.Listener) {
        normal.setListener(listener)
        bitPerfect.setListener(listener)
    }

    override fun setVolume(volume: Float) {
        normal.setVolume(volume)
        bitPerfect.setVolume(volume)
    }

    override fun setPlaybackParameters(playbackParameters: PlaybackParameters) {
        requestedParameters = playbackParameters
        normal.setPlaybackParameters(playbackParameters)
        leaveBitPerfectForProcessing()
    }

    override fun setSkipSilenceEnabled(skipSilenceEnabled: Boolean) {
        skipSilenceRequested = skipSilenceEnabled
        normal.setSkipSilenceEnabled(skipSilenceEnabled)
        leaveBitPerfectForProcessing()
    }

    private fun processingRequested(): Boolean = skipSilenceRequested || requestedParameters != PlaybackParameters.DEFAULT

    private fun wantsBitPerfect(format: Format): Boolean = !processingRequested() && bitPerfect.canAccept(format)

    private fun configureTarget(config: AudioSink.AudioSinkConfig): AudioSink {
        if (wantsBitPerfect(config.format)) {
            try {
                bitPerfect.configure(config)
                return bitPerfect
            } catch (error: AudioSink.ConfigurationException) {
                Timber.tag(TAG).i("Using standard output: %s", error.message)
            }
        }
        normal.configure(config)
        return normal
    }

    private fun switchTo(target: AudioSink) {
        if (target === active) return
        val outgoing = active
        active = target
        if (outgoing.hasPendingData()) {
            retiring = outgoing
        } else {
            outgoing.reset()
            if (playing) target.play()
        }
    }

    private fun finishRetiring(): Boolean {
        val outgoing = retiring ?: return true
        outgoing.playToEndOfStream()
        if (!outgoing.isEnded()) return false
        outgoing.reset()
        retiring = null
        if (playing) active.play()
        return true
    }

    private fun dropRetiring() {
        val outgoing = retiring ?: return
        outgoing.reset()
        retiring = null
        if (playing) active.play()
    }

    private fun reselectOutput() {
        val config = lastConfig ?: return
        if (wantsBitPerfect(config.format) == (active === bitPerfect)) return
        val outgoing = active
        try {
            val target = configureTarget(config)
            if (target === outgoing) return
            active = target
            outgoing.reset()
            if (playing) target.play()
        } catch (error: AudioSink.ConfigurationException) {
            Timber.tag(TAG).w(error, "Could not move playback between bit-perfect and standard output")
        }
    }

    private fun leaveBitPerfectForProcessing() {
        if (active !== bitPerfect || !processingRequested()) return
        val config = lastConfig ?: return
        dropRetiring()
        try {
            normal.configure(config)
        } catch (error: AudioSink.ConfigurationException) {
            Timber.tag(TAG).w(error, "Standard output rejected the current stream")
            return
        }
        switchTo(normal)
    }

    private fun fallBack(error: Exception) {
        Timber.tag(TAG).w(error, "Bit-perfect output failed; continuing on standard output")
        bitPerfect.reset()
        active = normal
        try {
            lastConfig?.let(normal::configure)
        } catch (configurationError: AudioSink.ConfigurationException) {
            Timber.tag(TAG).w(configurationError, "Standard output rejected the current stream")
        }
        if (playing) normal.play()
    }

    private companion object {
        const val TAG = "BitPerfectRoutingSink"
    }
}
