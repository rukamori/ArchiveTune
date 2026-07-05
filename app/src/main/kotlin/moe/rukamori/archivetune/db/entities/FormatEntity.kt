/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlin.math.roundToInt

@Entity(tableName = "format")
data class FormatEntity(
    @PrimaryKey val id: String,
    val itag: Int,
    val mimeType: String,
    val codecs: String,
    val bitrate: Int,
    val sampleRate: Int?,
    val contentLength: Long,
    val loudnessDb: Double?,
    val perceptualLoudnessDb: Double? = null,
    val playbackUrl: String?,
)

fun FormatEntity.containerLabel(): String = mimeType.substringAfter("/").substringBefore(";").uppercase()

fun FormatEntity.codecLabel(): String {
    val rawCodec = codecs.ifBlank { mimeType.substringAfter("/") }.uppercase()
    val rawMime = mimeType.substringAfter("/").substringBefore(";").uppercase()

    return when {
        rawCodec.contains("FLAC") || rawCodec.contains("ALAC") -> "Lossless"
        rawCodec.contains("OPUS") -> "OPUS"
        rawCodec.contains("AAC") || rawCodec.contains("MP4A") -> "AAC"
        rawCodec.contains("VORBIS") -> "VORBIS"
        rawMime.contains("OPUS") -> "OPUS"
        rawMime.contains("AAC") || rawMime.contains("MP4A") -> "AAC"
        rawMime.contains("VORBIS") -> "VORBIS"
        rawMime.isNotBlank() -> rawMime
        else -> rawCodec
    }
}

fun FormatEntity.formattedBitrate(): String = if (bitrate > 0) "${bitrate / 1000} kbps" else "Unknown"

fun FormatEntity.formattedSampleRate(): String? =
    sampleRate?.takeIf { it > 0 }?.let {
        "${(it / 100.0).roundToInt() / 10.0} kHz"
    }

fun FormatEntity.formattedFileSize(): String =
    contentLength.takeIf { it > 0 }?.let {
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var size = it.toDouble()
        var unitIndex = 0

        while (size >= 1024.0 && unitIndex < units.lastIndex) {
            size /= 1024.0
            unitIndex++
        }

        var rounded = if (size >= 99.95) {
            size.roundToInt().toDouble()
        } else {
            (size * 10.0).roundToInt() / 10.0
        }

        if (rounded >= 1023.95 && unitIndex < units.lastIndex) {
            rounded = 1.0
            unitIndex++
        }

        if (rounded == rounded.toLong().toDouble()) {
            "${rounded.toLong()} ${units[unitIndex]}"
        } else {
            String.format(java.util.Locale.US, "%.1f %s", rounded, units[unitIndex])
        }
    } ?: ""
