/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/BitPerfectTransport.kt, Copyright (C) 2026 BitChord Project,
 * licensed under the Apache License, Version 2.0.
 */

package moe.rukamori.archivetune.playback.bitperfect

import android.media.AudioDeviceInfo
import android.media.AudioManager
import java.nio.ByteBuffer

internal interface BitPerfectTransport {
    val deviceName: String
    val sampleRate: Int
    val channelCount: Int
    val outputBits: Int
    val validBits: Int get() = outputBits
    val bufferSizeFrames: Long
    val releasesWhenIdle: Boolean

    fun write(buffer: ByteBuffer): Int

    fun play()

    fun pause()

    fun flush()

    fun stop()

    fun playedFrames(): Long

    fun release()
}

internal fun AudioManager.usbOutputDevice(): AudioDeviceInfo? =
    getDevices(AudioManager.GET_DEVICES_OUTPUTS).firstOrNull {
        it.type == AudioDeviceInfo.TYPE_USB_DEVICE || it.type == AudioDeviceInfo.TYPE_USB_HEADSET
    }

internal fun AudioDeviceInfo.usbDisplayName(): String =
    productName
        ?.toString()
        .orEmpty()
        .ifBlank { "USB" }

internal class BitPerfectTransportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
