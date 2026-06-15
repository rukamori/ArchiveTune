/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.localmedia

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.EOFException
import java.io.InputStream
import java.nio.charset.Charset
import java.nio.charset.StandardCharsets
import java.util.Locale

class EmbeddedLyricsExtractor(
    private val contentResolver: ContentResolver,
) {
    fun extract(contentUri: Uri, displayName: String?, mimeType: String?): String? {
        return try {
            extractId3Lyrics(contentUri)
                ?: when {
                    isMp4(displayName, mimeType) -> extractMp4Lyrics(contentUri)
                    isFlac(displayName, mimeType) -> extractFlacLyrics(contentUri)
                    isOgg(displayName, mimeType) -> extractOggLyrics(contentUri)
                    else -> extractVorbisCommentLyrics(contentUri)
                }
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            Timber.tag(LogTag).w(error, "Failed to extract embedded lyrics for %s", contentUri)
            null
        }
    }

    private fun extractId3Lyrics(contentUri: Uri): String? {
        return contentResolver.openInputStream(contentUri)?.buffered()?.use { input ->
            val header = input.readByteArray(10) ?: return null
            if (!header.startsWith(Id3Header)) return null

            val majorVersion = header[3].toInt() and 0xFF
            if (majorVersion !in 2..4) return null

            val tagSize = readSynchsafeInt(header, 6)
            if (tagSize <= 0 || tagSize > MaxMetadataBytes) return null

            val tagBytes = input.readByteArray(tagSize) ?: return null
            val frameBytes = if ((header[5].toInt() and 0x80) != 0) {
                removeId3Unsynchronization(tagBytes)
            } else {
                tagBytes
            }

            parseId3Lyrics(frameBytes, majorVersion)
        }
    }

    private fun parseId3Lyrics(bytes: ByteArray, majorVersion: Int): String? {
        var offset = 0
        var syncedLyrics: String? = null
        var unsyncedLyrics: String? = null

        while (offset < bytes.size) {
            val frame = readId3Frame(bytes, offset, majorVersion) ?: break
            if (frame.size <= 0) break
            if (frame.payloadOffset + frame.size > bytes.size) break

            val payload = bytes.copyOfRange(frame.payloadOffset, frame.payloadOffset + frame.size)
            when (frame.id) {
                "USLT", "ULT" -> unsyncedLyrics = decodeUnsynchronizedLyricsFrame(payload) ?: unsyncedLyrics
                "SYLT", "SLT" -> syncedLyrics = decodeSynchronizedLyricsFrame(payload) ?: syncedLyrics
            }

            offset = frame.payloadOffset + frame.size
            if (!syncedLyrics.isNullOrBlank()) return syncedLyrics
        }

        return unsyncedLyrics
    }

    private fun readId3Frame(bytes: ByteArray, offset: Int, majorVersion: Int): Id3Frame? {
        return if (majorVersion == 2) {
            if (offset + 6 > bytes.size) return null
            val id = bytes.decodeAscii(offset, 3)
            if (id.isBlank() || id.any { it.code == 0 }) return null
            Id3Frame(
                id = id,
                size = readUInt24(bytes, offset + 3),
                payloadOffset = offset + 6,
            )
        } else {
            if (offset + 10 > bytes.size) return null
            val id = bytes.decodeAscii(offset, 4)
            if (id.isBlank() || id.any { it.code == 0 }) return null
            Id3Frame(
                id = id,
                size = if (majorVersion == 4) readSynchsafeInt(bytes, offset + 4) else readInt(bytes, offset + 4),
                payloadOffset = offset + 10,
            )
        }
    }

    private fun decodeUnsynchronizedLyricsFrame(bytes: ByteArray): String? {
        if (bytes.size <= 5) return null
        val encoding = bytes[0].toInt() and 0xFF
        val textStart = findId3Terminator(bytes, start = 4, encoding = encoding) ?: return null
        val start = textStart + terminatorLength(encoding)
        if (start >= bytes.size) return null
        return decodeId3Text(bytes, start, bytes.size - start, encoding)
    }

    private fun decodeSynchronizedLyricsFrame(bytes: ByteArray): String? {
        if (bytes.size <= 7) return null
        val encoding = bytes[0].toInt() and 0xFF
        val timestampFormat = bytes[4].toInt() and 0xFF
        if (timestampFormat != Id3TimestampMilliseconds) return null

        val descriptorEnd = findId3Terminator(bytes, start = 6, encoding = encoding) ?: return null
        var offset = descriptorEnd + terminatorLength(encoding)
        val entries = mutableListOf<Pair<Long, String>>()

        while (offset < bytes.size) {
            val textEnd = findId3Terminator(bytes, start = offset, encoding = encoding) ?: break
            if (textEnd + terminatorLength(encoding) + 4 > bytes.size) break

            val text = decodeId3Text(bytes, offset, textEnd - offset, encoding)
                ?.replace(WhitespaceRegex, " ")
                ?.trim()
            offset = textEnd + terminatorLength(encoding)
            val timestampMs = readInt(bytes, offset).toLong() and 0xFFFFFFFFL
            offset += 4

            if (!text.isNullOrBlank()) {
                entries += timestampMs to text
            }
        }

        if (entries.isEmpty()) return null
        return entries
            .sortedBy { (timeMs, _) -> timeMs }
            .joinToString("\n") { (timeMs, text) -> "${formatLrcTimestamp(timeMs)}$text" }
    }

    private fun extractMp4Lyrics(contentUri: Uri): String? {
        return contentResolver.openInputStream(contentUri)?.buffered()?.use { input ->
            parseMp4Boxes(input, Long.MAX_VALUE)
        }
    }

    private fun parseMp4Boxes(input: InputStream, remainingBytes: Long): String? {
        var remaining = remainingBytes
        while (remaining > Mp4HeaderSize) {
            val header = readMp4BoxHeader(input) ?: return null
            val payloadSize = header.payloadSize
            if (remainingBytes != Long.MAX_VALUE) {
                remaining -= header.size
            }

            when {
                header.type == "meta" -> {
                    if (payloadSize <= 4L) {
                        input.skipFully(payloadSize)
                    } else {
                        input.skipFully(4L)
                        parseMp4Boxes(input, payloadSize - 4L)?.let { return it }
                    }
                }
                header.isLyricsBox -> {
                    if (payloadSize in 1..MaxMetadataBytes.toLong()) {
                        val payload = input.readByteArray(payloadSize.toInt()) ?: return null
                        parseMp4LyricsItem(payload)?.let { return it }
                    } else {
                        input.skipFully(payloadSize)
                    }
                }
                header.type in Mp4ContainerBoxes -> {
                    parseMp4Boxes(input, payloadSize)?.let { return it }
                }
                else -> input.skipFully(payloadSize)
            }
        }
        return null
    }

    private fun parseMp4LyricsItem(bytes: ByteArray): String? {
        var offset = 0
        while (offset + Mp4HeaderSize <= bytes.size) {
            val size = readInt(bytes, offset)
            val type = bytes.decodeAscii(offset + 4, 4)
            if (size < Mp4HeaderSize || offset + size > bytes.size) return null
            if (type == "data") {
            val payloadOffset = offset + Mp4HeaderSize + Mp4DataHeaderSize
                if (payloadOffset >= offset + size) return null
                val payload = bytes.copyOfRange(payloadOffset, offset + size)
                return decodeMp4LyricsPayload(payload)
            }
            offset += size
        }
        return null
    }

    private fun decodeMp4LyricsPayload(bytes: ByteArray): String? {
        val utf8 = bytes.decodeClean(StandardCharsets.UTF_8)
        if (utf8.count { it == '\u0000' } <= utf8.length / 8) return utf8

        val utf16 = bytes.decodeClean(StandardCharsets.UTF_16)
        return utf16.takeIf { it.count { char -> char == '\u0000' } <= it.length / 8 } ?: utf8
    }

    private fun readMp4BoxHeader(input: InputStream): Mp4BoxHeader? {
        val header = input.readByteArray(Mp4HeaderSize) ?: return null
        val smallSize = readInt(header, 0).toLong() and 0xFFFFFFFFL
        val type = header.decodeAscii(4, 4)
        val size = when (smallSize) {
            0L -> return null
            1L -> {
                val extendedSize = input.readByteArray(8) ?: return null
                readLong(extendedSize, 0)
            }
            else -> smallSize
        }
        val headerSize = if (smallSize == 1L) 16 else Mp4HeaderSize
        if (size < headerSize) return null

        return Mp4BoxHeader(
            type = type,
            typeBytes = header.copyOfRange(4, 8),
            size = size,
            payloadSize = size - headerSize,
        )
    }

    private fun extractFlacLyrics(contentUri: Uri): String? {
        return contentResolver.openInputStream(contentUri)?.buffered()?.use { input ->
            val marker = input.readByteArray(4) ?: return null
            if (!marker.startsWith(FlacMarker)) return null

            var isLastBlock = false
            while (!isLastBlock) {
                val header = input.readByteArray(4) ?: return null
                isLastBlock = (header[0].toInt() and 0x80) != 0
                val type = header[0].toInt() and 0x7F
                val length = readUInt24(header, 1)
                if (length < 0 || length > MaxMetadataBytes) return null

                if (type == FlacVorbisCommentBlockType) {
                    val payload = input.readByteArray(length) ?: return null
                    return parseVorbisComments(payload, 0)
                }
                input.skipFully(length.toLong())
            }
            null
        }
    }

    private fun extractOggLyrics(contentUri: Uri): String? = extractVorbisCommentLyrics(contentUri)

    private fun extractVorbisCommentLyrics(contentUri: Uri): String? {
        return contentResolver.openInputStream(contentUri)?.buffered()?.use { input ->
            val bytes = input.readUpTo(MaxMetadataBytes)
            val opusOffset = bytes.indexOf(OpusTagsMarker)
            if (opusOffset >= 0) {
                parseVorbisComments(bytes, opusOffset + OpusTagsMarker.size)?.let { return@use it }
            }

            val vorbisOffset = bytes.indexOf(VorbisCommentMarker)
            if (vorbisOffset >= 0) {
                parseVorbisComments(bytes, vorbisOffset + VorbisCommentMarker.size)
            } else {
                null
            }
        }
    }

    private fun parseVorbisComments(bytes: ByteArray, offset: Int): String? {
        var cursor = offset
        val vendorLength = bytes.readLittleEndianInt(cursor) ?: return null
        if (vendorLength < 0) return null
        cursor += 4 + vendorLength
        if (cursor + 4 > bytes.size) return null

        val commentCount = bytes.readLittleEndianInt(cursor) ?: return null
        if (commentCount < 0) return null
        cursor += 4
        var unsyncedLyrics: String? = null
        var syncedLyrics: String? = null

        repeat(commentCount.coerceIn(0, MaxVorbisComments)) {
            val length = bytes.readLittleEndianInt(cursor) ?: return@repeat
            cursor += 4
            if (length < 0 || cursor + length > bytes.size) return@repeat

            val comment = bytes.decodeString(cursor, length, StandardCharsets.UTF_8)
            cursor += length
            val key = comment.substringBefore('=', missingDelimiterValue = "").uppercase(Locale.ROOT)
            val value = comment.substringAfter('=', missingDelimiterValue = "").trim()
            if (value.isBlank()) return@repeat

            when (key) {
                "SYNCEDLYRICS", "SYNCLYRICS" -> syncedLyrics = value
                "LYRICS", "UNSYNCEDLYRICS", "UNSYNCED LYRICS" -> unsyncedLyrics = value
            }
        }

        return syncedLyrics ?: unsyncedLyrics
    }

    private fun findId3Terminator(bytes: ByteArray, start: Int, encoding: Int): Int? {
        if (encoding == Id3EncodingUtf16 || encoding == Id3EncodingUtf16Be) {
            var index = start
            while (index + 1 < bytes.size) {
                if (bytes[index] == 0.toByte() && bytes[index + 1] == 0.toByte()) return index
                index += 2
            }
            return null
        }

        for (index in start until bytes.size) {
            if (bytes[index] == 0.toByte()) return index
        }
        return null
    }

    private fun decodeId3Text(bytes: ByteArray, offset: Int, length: Int, encoding: Int): String? {
        if (length <= 0 || offset < 0 || offset + length > bytes.size) return null
        val charset = when (encoding) {
            Id3EncodingIso88591 -> StandardCharsets.ISO_8859_1
            Id3EncodingUtf16 -> StandardCharsets.UTF_16
            Id3EncodingUtf16Be -> StandardCharsets.UTF_16BE
            Id3EncodingUtf8 -> StandardCharsets.UTF_8
            else -> StandardCharsets.UTF_8
        }
        return bytes.decodeString(offset, length, charset)
            .removePrefix("\uFEFF")
            .replace("\u0000", "")
            .trim()
            .takeIf(String::isNotBlank)
    }

    private fun terminatorLength(encoding: Int): Int =
        if (encoding == Id3EncodingUtf16 || encoding == Id3EncodingUtf16Be) 2 else 1

    private fun removeId3Unsynchronization(bytes: ByteArray): ByteArray {
        val result = ByteArray(bytes.size)
        var writeIndex = 0
        var readIndex = 0
        while (readIndex < bytes.size) {
            val value = bytes[readIndex]
            result[writeIndex++] = value
            if (value == 0xFF.toByte() && readIndex + 1 < bytes.size && bytes[readIndex + 1] == 0.toByte()) {
                readIndex++
            }
            readIndex++
        }
        return result.copyOf(writeIndex)
    }

    private fun formatLrcTimestamp(timeMs: Long): String {
        val minutes = timeMs / 60_000L
        val seconds = (timeMs % 60_000L) / 1_000L
        val centiseconds = (timeMs % 1_000L) / 10L
        return "[%02d:%02d.%02d]".format(Locale.US, minutes, seconds, centiseconds)
    }

    private fun isMp4(displayName: String?, mimeType: String?): Boolean {
        val extension = displayName.extension()
        val normalizedMimeType = mimeType.normalizedMimeType()
        return extension in Mp4Extensions || normalizedMimeType in Mp4MimeTypes
    }

    private fun isFlac(displayName: String?, mimeType: String?): Boolean {
        val extension = displayName.extension()
        val normalizedMimeType = mimeType.normalizedMimeType()
        return extension == "flac" || normalizedMimeType in FlacMimeTypes
    }

    private fun isOgg(displayName: String?, mimeType: String?): Boolean {
        val extension = displayName.extension()
        val normalizedMimeType = mimeType.normalizedMimeType()
        return extension in OggExtensions || normalizedMimeType in OggMimeTypes
    }

    private fun String?.extension(): String =
        this?.substringAfterLast('.', missingDelimiterValue = "")
            ?.lowercase(Locale.ROOT)
            .orEmpty()

    private fun String?.normalizedMimeType(): String =
        this?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            .orEmpty()

    private fun ByteArray.startsWith(prefix: ByteArray): Boolean {
        if (size < prefix.size) return false
        return prefix.indices.all { index -> this[index] == prefix[index] }
    }

    private fun ByteArray.indexOf(pattern: ByteArray): Int {
        if (pattern.isEmpty() || size < pattern.size) return -1
        for (index in 0..size - pattern.size) {
            var matched = true
            for (patternIndex in pattern.indices) {
                if (this[index + patternIndex] != pattern[patternIndex]) {
                    matched = false
                    break
                }
            }
            if (matched) return index
        }
        return -1
    }

    private fun ByteArray.decodeAscii(offset: Int, length: Int): String =
        decodeString(offset, length, StandardCharsets.ISO_8859_1)

    private fun ByteArray.decodeClean(charset: Charset): String =
        toString(charset)
            .replace("\uFEFF", "")
            .trim { it.isWhitespace() || it == '\u0000' }

    private fun ByteArray.decodeString(offset: Int, length: Int, charset: Charset): String =
        String(this, offset, length, charset)

    private fun ByteArray.readLittleEndianInt(offset: Int): Int? {
        if (offset < 0 || offset + 4 > size) return null
        return (this[offset].toInt() and 0xFF) or
            ((this[offset + 1].toInt() and 0xFF) shl 8) or
            ((this[offset + 2].toInt() and 0xFF) shl 16) or
            ((this[offset + 3].toInt() and 0xFF) shl 24)
    }

    private fun InputStream.readByteArray(length: Int): ByteArray? {
        if (length <= 0) return ByteArray(0)
        val bytes = ByteArray(length)
        var offset = 0
        while (offset < length) {
            val read = read(bytes, offset, length - offset)
            if (read < 0) return null
            offset += read
        }
        return bytes
    }

    private fun InputStream.readUpTo(maxBytes: Int): ByteArray {
        val buffer = ByteArray(DefaultBufferSize)
        val output = ByteArray(maxBytes)
        var total = 0
        while (total < maxBytes) {
            val read = read(buffer, 0, minOf(buffer.size, maxBytes - total))
            if (read < 0) break
            buffer.copyInto(output, destinationOffset = total, startIndex = 0, endIndex = read)
            total += read
        }
        return output.copyOf(total)
    }

    private fun InputStream.skipFully(byteCount: Long) {
        var remaining = byteCount
        while (remaining > 0L) {
            val skipped = skip(remaining)
            if (skipped > 0L) {
                remaining -= skipped
            } else if (read() == -1) {
                throw EOFException()
            } else {
                remaining--
            }
        }
    }

    private fun readInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 24) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 2].toInt() and 0xFF) shl 8) or
            (bytes[offset + 3].toInt() and 0xFF)
    }

    private fun readLong(bytes: ByteArray, offset: Int): Long {
        var value = 0L
        for (index in offset until offset + 8) {
            value = (value shl 8) or (bytes[index].toLong() and 0xFFL)
        }
        return value
    }

    private fun readUInt24(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0xFF) shl 16) or
            ((bytes[offset + 1].toInt() and 0xFF) shl 8) or
            (bytes[offset + 2].toInt() and 0xFF)
    }

    private fun readSynchsafeInt(bytes: ByteArray, offset: Int): Int {
        return ((bytes[offset].toInt() and 0x7F) shl 21) or
            ((bytes[offset + 1].toInt() and 0x7F) shl 14) or
            ((bytes[offset + 2].toInt() and 0x7F) shl 7) or
            (bytes[offset + 3].toInt() and 0x7F)
    }

    private data class Id3Frame(
        val id: String,
        val size: Int,
        val payloadOffset: Int,
    )

    private data class Mp4BoxHeader(
        val type: String,
        val typeBytes: ByteArray,
        val size: Long,
        val payloadSize: Long,
    ) {
        val isLyricsBox: Boolean
            get() = typeBytes.contentEquals(Mp4LyricsBoxBytes)
    }

    private companion object {
        val Id3Header = byteArrayOf('I'.code.toByte(), 'D'.code.toByte(), '3'.code.toByte())
        val FlacMarker = byteArrayOf('f'.code.toByte(), 'L'.code.toByte(), 'a'.code.toByte(), 'C'.code.toByte())
        val OpusTagsMarker = "OpusTags".toByteArray(StandardCharsets.US_ASCII)
        val VorbisCommentMarker = byteArrayOf(
            0x03,
            'v'.code.toByte(),
            'o'.code.toByte(),
            'r'.code.toByte(),
            'b'.code.toByte(),
            'i'.code.toByte(),
            's'.code.toByte(),
        )
        val Mp4LyricsBoxBytes = byteArrayOf(0xA9.toByte(), 'l'.code.toByte(), 'y'.code.toByte(), 'r'.code.toByte())
        val Mp4ContainerBoxes = setOf("moov", "udta", "ilst")
        val Mp4Extensions = setOf("m4a", "m4b", "m4p", "mp4", "3ga", "3gp")
        val Mp4MimeTypes = setOf("audio/mp4", "audio/x-m4a", "video/mp4", "video/3gpp")
        val FlacMimeTypes = setOf("audio/flac", "audio/x-flac")
        val OggExtensions = setOf("oga", "ogg", "opus")
        val OggMimeTypes = setOf("audio/ogg", "audio/opus", "application/ogg")
        val WhitespaceRegex = Regex("\\s+")
        const val MaxMetadataBytes = 2 * 1024 * 1024
        const val MaxVorbisComments = 512
        const val DefaultBufferSize = 8 * 1024
        const val FlacVorbisCommentBlockType = 4
        const val Id3EncodingIso88591 = 0
        const val Id3EncodingUtf16 = 1
        const val Id3EncodingUtf16Be = 2
        const val Id3EncodingUtf8 = 3
        const val Id3TimestampMilliseconds = 2
        const val Mp4HeaderSize = 8
        const val Mp4DataHeaderSize = 8
        const val LogTag = "EmbeddedLyricsExtractor"
    }
}
