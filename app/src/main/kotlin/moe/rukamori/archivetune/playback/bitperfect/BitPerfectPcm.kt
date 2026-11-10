/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/BitPerfectPcm.kt, Copyright (C) 2026 BitChord Project,
 * itself adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

package moe.rukamori.archivetune.playback.bitperfect

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.round

@OptIn(UnstableApi::class)
internal object BitPerfectPcm {
    private val INTEGER_BITS = listOf(16, 24, 32)
    private const val MEDIA3_FLOAT_FACTOR = 1.0 / 0x7FFFFFFF

    fun outputBits(
        sourceBits: Int,
        supported: Collection<Int>,
    ): Int? = supported.filter { it in INTEGER_BITS && it >= sourceBits }.minOrNull()

    fun sourceBits(encoding: Int): Int? =
        when (encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 16
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 24
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN -> 32
            else -> null
        }

    fun bytesPerSample(encoding: Int): Int =
        when (encoding) {
            C.ENCODING_PCM_16BIT, C.ENCODING_PCM_16BIT_BIG_ENDIAN -> 2
            C.ENCODING_PCM_24BIT, C.ENCODING_PCM_24BIT_BIG_ENDIAN -> 3
            C.ENCODING_PCM_32BIT, C.ENCODING_PCM_32BIT_BIG_ENDIAN, C.ENCODING_PCM_FLOAT -> 4
            else -> 0
        }

    fun convert(
        input: ByteBuffer,
        encoding: Int,
        channelCount: Int,
        outputBits: Int,
        output: ByteBuffer,
    ): Boolean {
        val inBytes = bytesPerSample(encoding)
        val outBytes = outputBits / 8
        val sourceBits = requireNotNull(sourceBits(encoding)) { "Unsupported PCM encoding $encoding" }
        require(outputBits in INTEGER_BITS && outputBits >= sourceBits) { "Output would reduce precision" }
        val frames =
            minOf(
                input.remaining() / (inBytes * channelCount),
                output.remaining() / (outBytes * channelCount),
            )
        val bigEndian = isBigEndian(encoding)
        if (!bigEndian && encoding != C.ENCODING_PCM_FLOAT && inBytes == outBytes) {
            val chunk = input.duplicate()
            chunk.limit(input.position() + frames * inBytes * channelCount)
            output.put(chunk)
            input.position(chunk.position())
            return true
        }
        input.order(if (bigEndian) ByteOrder.BIG_ENDIAN else ByteOrder.LITTLE_ENDIAN)
        output.order(ByteOrder.LITTLE_ENDIAN)
        var exact = true
        repeat(frames * channelCount) {
            val sample =
                when (inBytes) {
                    2 -> input.short.toInt() shl 16
                    3 -> read24(input, bigEndian) shl 8
                    else ->
                        if (encoding == C.ENCODING_PCM_FLOAT) {
                            val value = input.float
                            val recovered = recover24(value)
                            if (exact && !isExact24(value, recovered)) exact = false
                            recovered shl 8
                        } else {
                            input.int
                        }
                }
            putSample(output, sample, outputBits)
        }
        return exact
    }

    private fun putSample(
        output: ByteBuffer,
        sample: Int,
        outputBits: Int,
    ) {
        when (outputBits) {
            16 -> output.putShort((sample shr 16).toShort())
            24 -> {
                output.put((sample shr 8).toByte())
                output.put((sample shr 16).toByte())
                output.put((sample shr 24).toByte())
            }
            else -> output.putInt(sample)
        }
    }

    private fun recover24(value: Float): Int {
        if (value.isNaN()) return 0
        val scaled = round(value.toDouble() * 0x7FFFFFFF / 256.0)
        return scaled.coerceIn(-8_388_608.0, 8_388_607.0).toInt()
    }

    private fun isExact24(
        value: Float,
        sample: Int,
    ): Boolean =
        value == sample / 8_388_608f ||
            value == (MEDIA3_FLOAT_FACTOR * (sample shl 8)).toFloat()

    private fun isBigEndian(encoding: Int): Boolean =
        encoding == C.ENCODING_PCM_16BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_24BIT_BIG_ENDIAN ||
            encoding == C.ENCODING_PCM_32BIT_BIG_ENDIAN

    private fun read24(
        input: ByteBuffer,
        bigEndian: Boolean,
    ): Int {
        val a = input.get().toInt() and 255
        val b = input.get().toInt() and 255
        val c = input.get().toInt()
        return if (bigEndian) {
            (a shl 24 shr 8) or (b shl 8) or (c and 255)
        } else {
            (c shl 16) or (b shl 8) or a
        }
    }
}
