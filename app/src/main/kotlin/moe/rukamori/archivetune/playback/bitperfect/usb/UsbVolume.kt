/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/usb/UsbVolume.kt, Copyright (C) 2026 BitChord Project,
 * itself adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

package moe.rukamori.archivetune.playback.bitperfect.usb

import kotlin.math.floor

internal fun interface UsbControl {
    fun transfer(
        input: Boolean,
        request: Int,
        value: Int,
        index: Int,
        data: ByteArray,
    )
}

internal object UsbVolume {
    data class Feature(
        val unit: Int,
        val interfaceNumber: Int,
        val uac2: Boolean,
        val channels: List<Int>,
    )

    data class Snapshot(
        val minDb: Double = 0.0,
        val maxDb: Double = 0.0,
        val currentDb: Double = 0.0,
    )

    fun feature(
        raw: ByteArray,
        interfaceNumber: Int,
        terminal: Int,
        channels: Int,
    ): Feature? {
        var active = false
        var uac2 = false
        val sources = HashMap<Int, Int>()
        val features = HashMap<Int, Feature>()
        val outputs = mutableListOf<Int>()
        var offset = 0
        while (offset + 2 <= raw.size) {
            val length = raw[offset].toInt() and 255
            if (length < 2 || length > raw.size - offset) return null
            val d = raw.copyOfRange(offset, offset + length)
            offset += length

            fun u(index: Int) = d[index].toInt() and 255
            if (u(1) == 4 && length >= 9) {
                active = u(2) == interfaceNumber && u(5) == 1 && u(6) == 1
                uac2 = u(7) == 0x20
            } else if (active && u(1) == 0x24 && length >= 4) {
                when (u(2)) {
                    3 ->
                        if (length >= 9) {
                            sources[u(3)] = u(7)
                            outputs += u(3)
                        }

                    6 ->
                        if (length >= 7) {
                            sources[u(3)] = u(4)
                            val size = if (uac2) 4 else u(5)
                            val start = if (uac2) 5 else 6
                            if (size == 0 || size > 4 || length < start + size + 1) continue

                            fun writable(channel: Int): Boolean {
                                val position = start + channel * size
                                if (position + size >= length) return false
                                return if (uac2) (u(position) shr 2) and 3 == 3 else u(position) and 2 != 0
                            }
                            val controlled =
                                when {
                                    writable(0) -> listOf(0)
                                    (1..channels).all(::writable) -> (1..channels).toList()
                                    else -> continue
                                }
                            features[u(3)] = Feature(u(3), interfaceNumber, uac2, controlled)
                        }
                }
            }
        }
        val matches = mutableListOf<Feature>()
        for (output in outputs) {
            var id = output
            val seen = mutableListOf<Int>()
            var candidate: Feature? = null
            while (id != terminal && id !in seen) {
                seen += id
                if (candidate == null) candidate = features[id]
                id = sources[id] ?: break
            }
            if (id == terminal && candidate != null) matches += candidate
        }
        return matches.distinct().singleOrNull()
    }

    private data class Range(
        val min: Int,
        val max: Int,
        val step: Int,
    ) {
        fun floor(value: Int): Int {
            val clamped = value.coerceIn(min, max)
            val effectiveStep = step.coerceAtLeast(1)
            return min + (clamped - min) / effectiveStep * effectiveStep
        }

        companion object {
            fun parse(
                bytes: ByteArray,
                at: Int = 0,
            ): Range {
                val range = Range(s16(bytes, at), s16(bytes, at + 2), s16(bytes, at + 4))
                if (range.min == Short.MIN_VALUE.toInt() || range.min > range.max || range.step < 0) {
                    throw IllegalStateException("Invalid USB volume range")
                }
                return range
            }
        }
    }

    private fun s16(
        bytes: ByteArray,
        at: Int,
    ): Int = (bytes[at].toInt() and 255) or (bytes[at + 1].toInt() shl 8)

    private fun u16(
        bytes: ByteArray,
        at: Int,
    ): Int = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun le16(value: Int) = byteArrayOf(value.toByte(), (value shr 8).toByte())

    class Volume private constructor(
        private val feature: Feature,
        private val ranges: List<List<Range>>,
        private val original: List<Int>,
        snapshot: Snapshot,
    ) {
        var snapshot: Snapshot = snapshot
            private set

        fun set(
            control: UsbControl,
            db: Double,
        ): Snapshot {
            require(db.isFinite()) { "Invalid USB volume" }
            val target = floor(db.coerceIn(snapshot.minDb, snapshot.maxDb) * 256.0).toInt()
            return setTargets(control, List(feature.channels.size) { target })
        }

        fun restore(control: UsbControl): Snapshot = setTargets(control, original)

        private fun setTargets(
            control: UsbControl,
            targets: List<Int>,
        ): Snapshot {
            val index = (feature.unit shl 8) or feature.interfaceNumber
            var loudest = Short.MIN_VALUE.toInt()
            for ((position, channel) in feature.channels.withIndex()) {
                val target = targets[position]
                val value =
                    if (target == Short.MIN_VALUE.toInt()) {
                        target
                    } else {
                        ranges[position].filter { it.min <= target }.maxOfOrNull { it.floor(target) }
                            ?: throw IllegalStateException("USB volume below supported range")
                    }
                val selector = 0x200 or channel
                control.transfer(false, SET_CUR, selector, index, le16(value))
                val readback = ByteArray(2)
                control.transfer(true, if (feature.uac2) UAC2_CUR else UAC1_GET_CUR, selector, index, readback)
                val actual = s16(readback, 0)
                if (actual != value) throw IllegalStateException("USB volume read-back mismatch")
                loudest = maxOf(loudest, actual)
            }
            snapshot = snapshot.copy(currentDb = loudest / 256.0)
            return snapshot
        }

        companion object {
            private const val SET_CUR = 1
            private const val UAC2_CUR = 1
            private const val UAC2_RANGE = 2
            private const val UAC1_GET_CUR = 0x81
            private const val STARTUP_DB = -40

            fun open(
                control: UsbControl,
                feature: Feature,
            ): Volume {
                val index = (feature.unit shl 8) or feature.interfaceNumber
                val ranges = mutableListOf<List<Range>>()
                var current = Short.MIN_VALUE.toInt()
                val startup = mutableListOf<Int>()
                val original = mutableListOf<Int>()
                for (channel in feature.channels) {
                    val selector = 0x200 or channel
                    val data = ByteArray(2)
                    control.transfer(true, if (feature.uac2) UAC2_CUR else UAC1_GET_CUR, selector, index, data)
                    val level = s16(data, 0)
                    current = maxOf(current, level)
                    original += level
                    startup += minOf(level, STARTUP_DB * 256)
                    ranges +=
                        if (feature.uac2) {
                            control.transfer(true, UAC2_RANGE, selector, index, data)
                            val count = u16(data, 0)
                            if (count !in 1..16) throw IllegalStateException("Unsupported USB volume ranges")
                            val all = ByteArray(2 + 6 * count)
                            control.transfer(true, UAC2_RANGE, selector, index, all)
                            if (u16(all, 0) != count) throw IllegalStateException("USB volume range changed")
                            (0 until count).map { Range.parse(all, 2 + it * 6) }
                        } else {
                            val all = ByteArray(6)
                            for ((part, request) in (0x82..0x84).withIndex()) {
                                val bytes = ByteArray(2)
                                control.transfer(true, request, selector, index, bytes)
                                bytes.copyInto(all, part * 2)
                            }
                            listOf(Range.parse(all))
                        }
                }
                val min = ranges.maxOf { channelRanges -> channelRanges.minOf { it.min } }
                val max = minOf(ranges.minOf { channelRanges -> channelRanges.maxOf { it.max } }, 0)
                if (min >= max || min > STARTUP_DB * 256) {
                    throw IllegalStateException("USB volume cannot provide startup attenuation")
                }
                val volume =
                    Volume(
                        feature,
                        ranges,
                        original,
                        Snapshot(minDb = min / 256.0, maxDb = max / 256.0, currentDb = current / 256.0),
                    )
                volume.setTargets(control, startup)
                return volume
            }
        }
    }
}
