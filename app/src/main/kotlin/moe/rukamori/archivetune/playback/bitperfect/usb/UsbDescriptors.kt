/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Adapted from BitChord (https://github.com/kushagrasinghx/BitChord),
 * playback/audio/bitperfect/usb/UsbDescriptors.kt, Copyright (C) 2026 BitChord Project,
 * itself adapted from Mornye (https://github.com/mornyeapp/Mornye), Copyright 2026 zarzet.
 * SPDX-License-Identifier of the adapted source: LGPL-3.0-only
 */

package moe.rukamori.archivetune.playback.bitperfect.usb

internal object UsbDescriptors {
    class MalformedDescriptorException(
        message: String,
    ) : Exception(message)

    data class Endpoint(
        val address: Int = 0,
        val attributes: Int = 0,
        val maxPacket: Int = 0,
        val interval: Int = 0,
        val syncAddress: Int = 0,
        val rateControl: Boolean = false,
    )

    data class Alternate(
        val control: Int = 0,
        val interfaceNumber: Int = 0,
        val alternate: Int = 0,
        val uac2: Boolean = false,
        val terminal: Int = 0,
        val clock: Int = 0,
        val channels: Int = 0,
        val bits: Int = 0,
        val subslot: Int = 0,
        val pcm: Boolean = false,
        val rates: List<Pair<Int, Int>> = emptyList(),
        val endpoints: List<Endpoint> = emptyList(),
    )

    data class Device(
        val vendor: Int = 0,
        val product: Int = 0,
        val revision: Int = 0,
        val alternates: List<Alternate> = emptyList(),
    )

    data class OutputFormat(
        val sampleRate: Int,
        val channels: Int,
        val bits: Int,
        val subslot: Int,
    )

    data class Plan(
        val alternate: Alternate,
        val format: OutputFormat,
        val data: Endpoint,
        val feedback: Endpoint?,
    )

    private fun u16le(
        bytes: ByteArray,
        at: Int,
    ) = (bytes[at].toInt() and 255) or ((bytes[at + 1].toInt() and 255) shl 8)

    private fun u24le(
        bytes: ByteArray,
        at: Int,
    ) = u16le(bytes, at) or ((bytes[at + 2].toInt() and 255) shl 16)

    private fun u32le(
        bytes: ByteArray,
        at: Int,
    ) = u16le(bytes, at) or (u16le(bytes, at + 2) shl 16)

    fun parse(bytes: ByteArray): Device {
        var vendor = 0
        var product = 0
        var revision = 0
        val alternates = mutableListOf<Alternate>()
        var current: Alternate? = null
        var control = 0
        var isControl = false
        val clocks = HashMap<Pair<Int, Int>, Int>()
        var offset = 0
        var configurations = 0
        while (offset < bytes.size) {
            val length = bytes[offset].toInt() and 255
            if (length < 2 || offset + length > bytes.size) {
                throw MalformedDescriptorException("Malformed USB descriptor")
            }
            val d = bytes.copyOfRange(offset, offset + length)
            offset += length

            fun u(index: Int) = d[index].toInt() and 255
            when (u(1)) {
                2 -> {
                    configurations++
                    if (configurations > 1) {
                        throw MalformedDescriptorException("Multiple USB configurations are not supported")
                    }
                }

                1 ->
                    if (length >= 18) {
                        vendor = u16le(d, 8)
                        product = u16le(d, 10)
                        revision = u16le(d, 12)
                    }

                4 ->
                    if (length >= 9) {
                        current?.let(alternates::add)
                        current = null
                        isControl = u(5) == 1 && u(6) == 1 && u(7) == 0x20
                        if (u(5) == 1 && u(6) == 1) control = u(2)
                        if (u(5) == 1 && u(6) == 2 && u(3) > 0 && (u(7) == 0 || u(7) == 0x20)) {
                            current =
                                Alternate(
                                    control = control,
                                    interfaceNumber = u(2),
                                    alternate = u(3),
                                    uac2 = u(7) == 0x20,
                                )
                        }
                    }

                0x24 ->
                    if (isControl && length >= 9 && u(2) == 2) {
                        clocks[control to u(3)] = u(7)
                    } else {
                        current = current?.let { classSpecific(it, d, length) }
                    }

                5 ->
                    if (length >= 7) {
                        current =
                            current?.let { alternate ->
                                val packet = u16le(d, 4)
                                alternate.copy(
                                    endpoints =
                                        alternate.endpoints +
                                            Endpoint(
                                                address = u(2),
                                                attributes = u(3),
                                                maxPacket = (packet and 0x7ff) * (1 + ((packet shr 11) and 3)),
                                                interval = u(6),
                                                syncAddress = if (length > 8) u(8) else 0,
                                            ),
                                )
                            }
                    }

                0x25 ->
                    if (length >= 4 && u(2) == 1) {
                        current =
                            current?.let { alternate ->
                                val last = alternate.endpoints.lastOrNull()
                                if (alternate.uac2 || last == null) {
                                    alternate
                                } else {
                                    alternate.copy(
                                        endpoints = alternate.endpoints.dropLast(1) + last.copy(rateControl = u(3) and 1 != 0),
                                    )
                                }
                            }
                    }
            }
        }
        current?.let(alternates::add)
        return Device(
            vendor = vendor,
            product = product,
            revision = revision,
            alternates = alternates.map { it.copy(clock = clocks[it.control to it.terminal] ?: 0) },
        )
    }

    private fun classSpecific(
        alternate: Alternate,
        d: ByteArray,
        length: Int,
    ): Alternate {
        fun u(index: Int) = d[index].toInt() and 255
        var result = alternate
        if (length >= 7 && u(2) == 1) {
            result = result.copy(terminal = u(3))
            if (result.uac2 && length >= 16 && u(5) == 1) {
                result = result.copy(pcm = u32le(d, 6) and 1 != 0, channels = u(10))
            } else if (!result.uac2) {
                result = result.copy(pcm = u16le(d, 5) == 1)
            }
        }
        if (length >= 6 && u(2) == 2 && u(3) == 1) {
            if (result.uac2) {
                result = result.copy(subslot = u(4), bits = u(5))
            } else if (length >= 8) {
                val count = u(7)
                val rates =
                    if (count == 0 && length >= 14) {
                        listOf(u24le(d, 8) to u24le(d, 11))
                    } else if (length >= 8 + count * 3) {
                        (0 until count).map { index ->
                            val rate = u24le(d, 8 + index * 3)
                            rate to rate
                        }
                    } else {
                        emptyList()
                    }
                result = result.copy(channels = u(4), subslot = u(5), bits = u(6), rates = result.rates + rates)
            }
        }
        return result
    }

    private fun isRawDsdAlternate(
        device: Device,
        alternate: Alternate,
    ): Boolean {
        if (!alternate.uac2 || alternate.subslot != 4 || alternate.alternate != 2) return false
        return when (device.vendor to device.product) {
            0x16d0 to 0x071a -> device.revision == 0x0199 || device.revision == 0x019b || device.revision == 0x0203
            0x2772 to 0x0230, 0x20b1 to 0x3089 -> true
            else -> false
        }
    }

    fun formats(
        device: Device,
        sampleRate: Int,
        channels: Int,
        sourceBits: Int,
    ): List<Pair<Alternate, OutputFormat>> {
        if (channels !in 1..2 || sourceBits !in listOf(16, 24, 32) || sampleRate <= 0) return emptyList()
        return device.alternates
            .filter { alternate ->
                alternate.channels == channels &&
                    alternate.subslot in 2..4 &&
                    alternate.pcm &&
                    !isRawDsdAlternate(device, alternate) &&
                    alternate.bits >= sourceBits &&
                    alternate.bits <= alternate.subslot * 8 &&
                    !(alternate.uac2 && alternate.clock == 0) &&
                    (alternate.uac2 || alternate.rates.any { (min, max) -> sampleRate in min..max })
            }.map { alternate ->
                alternate to OutputFormat(sampleRate, channels, alternate.bits, alternate.subslot)
            }.sortedBy { it.second.subslot }
    }

    fun plan(
        alternate: Alternate,
        format: OutputFormat,
    ): Plan? {
        val data =
            alternate.endpoints.firstOrNull {
                it.address and 0x80 == 0 && it.attributes and 3 == 1 && it.attributes and 0x30 == 0
            } ?: return null
        if (data.interval !in 1..4 || data.maxPacket == 0 || data.maxPacket > 3072) return null
        val feedback =
            alternate.endpoints.firstOrNull {
                it.address and 0x80 != 0 &&
                    it.attributes and 3 == 1 &&
                    (it.address == data.syncAddress || it.attributes and 0x30 == 0x10)
            }
        if ((data.attributes shr 2) and 3 == 1 && feedback == null) return null
        if (feedback != null && feedback.maxPacket !in 3..64) return null
        if (!alternate.uac2 && !data.rateControl && alternate.rates != listOf(format.sampleRate to format.sampleRate)) {
            return null
        }
        return Plan(alternate, format, data, feedback)
    }

    fun plans(
        raw: ByteArray,
        sampleRate: Int,
        channels: Int,
        sourceBits: Int,
    ): List<Plan> {
        val device =
            try {
                parse(raw)
            } catch (_: MalformedDescriptorException) {
                return emptyList()
            }
        return formats(device, sampleRate, channels, sourceBits)
            .mapNotNull { (alternate, format) -> plan(alternate, format) }
            .take(32)
    }
}
