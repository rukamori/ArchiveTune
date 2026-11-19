/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/usb/UsbDirectTransport.kt, Copyright (C) 2026 BitChord Project,
 * itself adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

package moe.rukamori.archivetune.playback.bitperfect.usb

import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import moe.rukamori.archivetune.playback.bitperfect.BitPerfectTransport
import moe.rukamori.archivetune.playback.bitperfect.BitPerfectTransportException
import java.nio.ByteBuffer

internal object UsbDirectNative {
    init {
        System.loadLibrary("archivetune_usb")
    }

    external fun open(
        fd: Int,
        candidates: IntArray,
    ): Long

    external fun chosen(handle: Long): Int

    external fun capacity(handle: Long): Int

    external fun write(
        handle: Long,
        data: ByteBuffer,
        offset: Int,
        count: Int,
    ): Int

    external fun setPlaying(
        handle: Long,
        playing: Boolean,
    )

    external fun flush(handle: Long)

    external fun frames(handle: Long): Long

    external fun control(
        handle: Long,
        input: Boolean,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray,
    ): ByteArray

    external fun close(handle: Long)

    private const val PCM_ENCODING = 0

    fun encode(plans: List<UsbDescriptors.Plan>): IntArray =
        plans
            .flatMap { plan ->
                val alternate = plan.alternate
                val format = plan.format
                listOf(
                    alternate.control,
                    alternate.interfaceNumber,
                    alternate.alternate,
                    if (alternate.uac2) 1 else 0,
                    alternate.clock,
                    plan.data.address,
                    plan.data.attributes,
                    plan.data.maxPacket,
                    plan.data.interval,
                    if (plan.data.rateControl) 1 else 0,
                    plan.feedback?.address ?: -1,
                    plan.feedback?.maxPacket ?: 0,
                    format.sampleRate,
                    format.channels,
                    format.subslot,
                    format.bits,
                    PCM_ENCODING,
                )
            }.toIntArray()
}

internal class UsbDirectTransport private constructor(
    private val connection: UsbDeviceConnection,
    private var handle: Long,
    val usbDeviceId: Int,
    override val deviceName: String,
    format: UsbDescriptors.OutputFormat,
    private val volumeControl: UsbVolume.Volume?,
) : BitPerfectTransport {
    override val sampleRate = format.sampleRate
    override val channelCount = format.channels
    override val outputBits = format.subslot * 8
    override val validBits = format.bits
    override val bufferSizeFrames: Long = UsbDirectNative.capacity(handle).toLong()
    override val releasesWhenIdle = true

    @Volatile
    private var failure: String? = null
    private var lastFrames = 0L

    private val control = controlFor(handle)

    val hasHardwareVolume: Boolean get() = volumeControl != null

    fun setVolumeFraction(fraction: Float) {
        val volume = volumeControl ?: return
        synchronized(volume) { volume.set(control, decibels(volume.snapshot, fraction)) }
    }

    fun markDetached() {
        failure = "USB disconnected"
    }

    override fun write(buffer: ByteBuffer): Int {
        val count =
            call {
                UsbDirectNative.write(handle, buffer, buffer.position(), minOf(buffer.remaining(), MAX_WRITE))
            }
        buffer.position(buffer.position() + count)
        return count
    }

    override fun play() = call { UsbDirectNative.setPlaying(handle, true) }

    override fun pause() = call { UsbDirectNative.setPlaying(handle, false) }

    override fun flush() {
        call { UsbDirectNative.flush(handle) }
        lastFrames = 0L
    }

    override fun stop() = Unit

    override fun playedFrames(): Long {
        if (handle == 0L || failure != null) return lastFrames
        lastFrames = maxOf(lastFrames, runCatching { UsbDirectNative.frames(handle) }.getOrDefault(lastFrames))
        return lastFrames
    }

    override fun release() {
        if (handle != 0L && failure == null) {
            volumeControl?.let { volume -> runCatching { synchronized(volume) { volume.restore(control) } } }
        }
        if (handle != 0L) UsbDirectNative.close(handle)
        handle = 0L
        runCatching { connection.close() }
    }

    private inline fun <T> call(block: () -> T): T {
        failure?.let { throw BitPerfectTransportException(it) }
        return try {
            block()
        } catch (error: IllegalStateException) {
            val reason = error.message ?: "USB output failed"
            failure = reason
            throw BitPerfectTransportException(reason, error)
        }
    }

    companion object {
        const val REASON_VOLUME_UNAVAILABLE = "volume_unavailable"
        private const val MAX_WRITE = 256 * 1024
        private const val LOWEST_AUDIBLE_DB = -60.0

        private fun controlFor(handle: Long) =
            UsbControl { input, request, value, index, data ->
                val reply = UsbDirectNative.control(handle, input, request, value, index, data)
                if (input) reply.copyInto(data)
            }

        private fun decibels(
            snapshot: UsbVolume.Snapshot,
            fraction: Float,
        ): Double {
            val level = fraction.coerceIn(0f, 1f).toDouble()
            if (level <= 0.0) return snapshot.minDb
            val floorDb = maxOf(snapshot.minDb, LOWEST_AUDIBLE_DB)
            return floorDb + (snapshot.maxDb - floorDb) * level
        }

        fun open(
            manager: UsbManager,
            device: UsbDevice,
            sampleRate: Int,
            channels: Int,
            sourceBits: Int,
            allowFixedVolume: Boolean,
            volumeFraction: Float,
        ): UsbDirectTransport {
            val connection = manager.openDevice(device) ?: throw BitPerfectTransportException("cannot_open_usb")
            var handle = 0L
            try {
                val raw = connection.rawDescriptors ?: throw BitPerfectTransportException("no_descriptors")
                val plans = UsbDescriptors.plans(raw, sampleRate, channels, sourceBits)
                if (plans.isEmpty()) throw BitPerfectTransportException("format")
                handle =
                    try {
                        UsbDirectNative.open(connection.fileDescriptor, UsbDirectNative.encode(plans))
                    } catch (error: IllegalStateException) {
                        throw BitPerfectTransportException("unsupported", error)
                    }
                val plan = plans[UsbDirectNative.chosen(handle)]
                val control = controlFor(handle)
                val volume =
                    UsbVolume
                        .feature(raw, plan.alternate.control, plan.alternate.terminal, channels)
                        ?.let { feature -> runCatching { UsbVolume.Volume.open(control, feature) }.getOrNull() }
                if (volume == null && !allowFixedVolume) throw BitPerfectTransportException(REASON_VOLUME_UNAVAILABLE)
                if (volume != null) {
                    runCatching { volume.set(control, decibels(volume.snapshot, volumeFraction)) }
                }
                return UsbDirectTransport(
                    connection = connection,
                    handle = handle,
                    usbDeviceId = device.deviceId,
                    deviceName = device.productName?.takeIf { it.isNotBlank() } ?: "USB",
                    format = plan.format,
                    volumeControl = volume,
                )
            } catch (error: Exception) {
                if (handle != 0L) UsbDirectNative.close(handle)
                runCatching { connection.close() }
                throw error as? BitPerfectTransportException ?: BitPerfectTransportException("unsupported", error)
            }
        }
    }
}
