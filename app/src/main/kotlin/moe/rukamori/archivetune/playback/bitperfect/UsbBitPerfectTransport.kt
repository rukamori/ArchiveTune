/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/MixerTransport.kt, Copyright (C) 2026 BitChord Project,
 * itself adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

package moe.rukamori.archivetune.playback.bitperfect

import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioMixerAttributes
import android.media.AudioRouting
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.annotation.RequiresApi
import java.nio.ByteBuffer

@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
internal class UsbBitPerfectTransport private constructor(
    private val manager: AudioManager,
    private val device: AudioDeviceInfo,
    private val mixer: AudioMixerAttributes,
    private val track: AudioTrack,
    override val sampleRate: Int,
    override val channelCount: Int,
    override val outputBits: Int,
) : BitPerfectTransport {
    override val deviceName: String = device.usbDisplayName()
    override val bufferSizeFrames: Long = track.bufferSizeInFrames.toLong()
    override val releasesWhenIdle = false

    @Volatile
    private var failure: String? = null
    private var nextRouteCheckMs = 0L
    private var previousHead = 0L
    private var headWraps = 0L
    private var stopHeadFrames = 0L
    private var stopRealtimeMs = NOT_STOPPED

    private val routingListener =
        AudioRouting.OnRoutingChangedListener { router ->
            val routed = router.routedDevice
            if (routed != null && routed.id != device.id) failure = "USB route changed"
        }
    private val mixerListener =
        AudioManager.OnPreferredMixerAttributesChangedListener { attributes, changedDevice, value ->
            if (attributes.usage == AudioAttributes.USAGE_MEDIA && changedDevice.id == device.id && value != mixer) {
                failure = "Bit-perfect mixer attributes changed"
            }
        }

    init {
        track.addOnRoutingChangedListener(routingListener, Handler(Looper.getMainLooper()))
        manager.addOnPreferredMixerAttributesChangedListener(Runnable::run, mixerListener)
    }

    override fun write(buffer: ByteBuffer): Int {
        checkRoute()
        val written = track.write(buffer, buffer.remaining(), AudioTrack.WRITE_NON_BLOCKING)
        if (written < 0) throw BitPerfectTransportException("USB write failed: $written")
        return written
    }

    override fun play() {
        failure?.let { throw BitPerfectTransportException(it) }
        if (stopRealtimeMs != NOT_STOPPED) return
        track.play()
        if (!isRouteIntact()) {
            track.pause()
            val reason = "USB route could not be verified"
            failure = reason
            throw BitPerfectTransportException(reason)
        }
        nextRouteCheckMs = SystemClock.elapsedRealtime() + ROUTE_CHECK_MS
    }

    override fun pause() {
        if (stopRealtimeMs != NOT_STOPPED) return
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
    }

    override fun flush() {
        if (track.playState == AudioTrack.PLAYSTATE_PLAYING) track.pause()
        track.flush()
        previousHead = 0L
        headWraps = 0L
        stopHeadFrames = 0L
        stopRealtimeMs = NOT_STOPPED
    }

    override fun stop() {
        if (stopRealtimeMs != NOT_STOPPED) return
        stopHeadFrames = readHeadFrames()
        stopRealtimeMs = SystemClock.elapsedRealtime()
        if (track.playState != AudioTrack.PLAYSTATE_STOPPED) track.stop()
    }

    override fun playedFrames(): Long {
        val stoppedAt = stopRealtimeMs
        if (stoppedAt == NOT_STOPPED) return readHeadFrames()
        val elapsedMs = SystemClock.elapsedRealtime() - stoppedAt
        return stopHeadFrames + elapsedMs * sampleRate / 1000L
    }

    override fun release() {
        runCatching { track.removeOnRoutingChangedListener(routingListener) }
        runCatching { manager.removeOnPreferredMixerAttributesChangedListener(mixerListener) }
        runCatching { track.release() }
        runCatching { manager.clearPreferredMixerAttributes(ATTRIBUTES, device) }
    }

    private fun readHeadFrames(): Long {
        val head = track.playbackHeadPosition.toLong() and 0xffffffffL
        if (head < previousHead) headWraps++
        previousHead = head
        return (headWraps shl 32) + head
    }

    private fun checkRoute() {
        failure?.let { throw BitPerfectTransportException(it) }
        val now = SystemClock.elapsedRealtime()
        if (track.playState != AudioTrack.PLAYSTATE_PLAYING || now < nextRouteCheckMs) return
        if (!isRouteIntact()) {
            val reason = "USB route changed"
            failure = reason
            throw BitPerfectTransportException(reason)
        }
        nextRouteCheckMs = now + ROUTE_CHECK_MS
    }

    private fun isRouteIntact(): Boolean {
        val routed = track.routedDevice
        if (routed != null && routed.id != device.id) return false
        return manager.getPreferredMixerAttributes(ATTRIBUTES, device) == mixer
    }

    companion object {
        private const val ROUTE_CHECK_MS = 250L
        private const val NOT_STOPPED = -1L

        val ATTRIBUTES: AudioAttributes =
            AudioAttributes
                .Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .build()

        fun matchingFormat(
            manager: AudioManager,
            device: AudioDeviceInfo,
            sampleRate: Int,
            channelCount: Int,
            sourceBits: Int,
        ): AudioMixerAttributes? {
            val mask = channelMask(channelCount)
            val offered =
                runCatching { manager.getSupportedMixerAttributes(device) }
                    .getOrDefault(emptyList())
                    .filter {
                        it.mixerBehavior == AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT &&
                            it.format.channelMask == mask &&
                            it.format.sampleRate == sampleRate &&
                            bits(it.format.encoding) > 0
                    }
            val outputBits = BitPerfectPcm.outputBits(sourceBits, offered.map { bits(it.format.encoding) }) ?: return null
            return offered.first { bits(it.format.encoding) == outputBits }
        }

        fun open(
            manager: AudioManager,
            sampleRate: Int,
            channelCount: Int,
            sourceBits: Int,
        ): UsbBitPerfectTransport {
            val usb = manager.usbOutputDevice() ?: throw BitPerfectTransportException("no_usb")
            val selected =
                matchingFormat(manager, usb, sampleRate, channelCount, sourceBits)
                    ?: throw BitPerfectTransportException("unsupported")
            val encoding = selected.format.encoding
            val outputBits = bits(encoding)
            if (!manager.setPreferredMixerAttributes(ATTRIBUTES, usb, selected)) {
                throw BitPerfectTransportException("unsupported")
            }
            var track: AudioTrack? = null
            try {
                val minimum = AudioTrack.getMinBufferSize(sampleRate, channelMask(channelCount), encoding)
                if (minimum <= 0) throw BitPerfectTransportException("format")
                val frameBytes = channelCount * (outputBits / 8)
                val bufferBytes = maxOf(minimum * 2, sampleRate / 5 * frameBytes)
                val created =
                    AudioTrack
                        .Builder()
                        .setAudioAttributes(ATTRIBUTES)
                        .setAudioFormat(selected.format)
                        .setTransferMode(AudioTrack.MODE_STREAM)
                        .setBufferSizeInBytes(bufferBytes)
                        .build()
                track = created
                if (created.state != AudioTrack.STATE_INITIALIZED ||
                    !created.setPreferredDevice(usb) ||
                    created.sampleRate != sampleRate ||
                    created.audioFormat != encoding ||
                    created.channelCount != channelCount
                ) {
                    throw BitPerfectTransportException("format")
                }
                created.setVolume(1f)
                return UsbBitPerfectTransport(
                    manager = manager,
                    device = usb,
                    mixer = selected,
                    track = created,
                    sampleRate = sampleRate,
                    channelCount = channelCount,
                    outputBits = outputBits,
                )
            } catch (error: Exception) {
                runCatching { track?.release() }
                runCatching { manager.clearPreferredMixerAttributes(ATTRIBUTES, usb) }
                throw error as? BitPerfectTransportException ?: BitPerfectTransportException("format", error)
            }
        }

        private fun channelMask(channelCount: Int): Int =
            if (channelCount == 1) AudioFormat.CHANNEL_OUT_MONO else AudioFormat.CHANNEL_OUT_STEREO

        private fun bits(encoding: Int): Int =
            when (encoding) {
                AudioFormat.ENCODING_PCM_16BIT -> 16
                AudioFormat.ENCODING_PCM_24BIT_PACKED -> 24
                AudioFormat.ENCODING_PCM_32BIT -> 32
                else -> 0
            }
    }
}
