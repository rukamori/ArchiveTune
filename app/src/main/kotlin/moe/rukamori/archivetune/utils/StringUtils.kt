/*
 * ArchiveTune (2026)
 * ﾂｩ Rukamori 窶・github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import java.math.BigInteger
import java.security.MessageDigest
import java.text.DecimalFormat
import kotlin.math.absoluteValue
import kotlin.math.floor

fun makeTimeString(duration: Long?): String {
    if (duration == null || duration < 0) return ""
    // Heuristic: if the value looks like an epoch millis (greater than ~1e12),
    // format as a human-readable date/time rather than a duration.
    // (1_000_000_000_000L ~= 2001-09-09 UTC)
    if (duration > 1_000_000_000_000L) {
        val sdf = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
        sdf.timeZone = java.util.TimeZone.getDefault()
        return sdf.format(java.util.Date(duration))
    }
    var sec = duration / 1000
    val day = sec / 86400
    sec %= 86400
    val hour = sec / 3600
    sec %= 3600
    val minute = sec / 60
    sec %= 60
    // More human-friendly duration strings:
    return when {
        day > 0 -> "%dd %dh %dm %ds".format(day, hour, minute, sec)
        hour > 0 -> "%dh %dm %ds".format(hour, minute, sec)
        minute > 0 -> "%d:%02d".format(minute, sec)
        else -> "%d:%02d".format(0, sec)
    }
}
fun md5(str: String): String {
    val md = MessageDigest.getInstance("MD5")
    return BigInteger(1, md.digest(str.toByteArray())).toString(16).padStart(32, '0')
}
fun joinByBullet(vararg str: String?) =
    str
        .filterNot {
            it.isNullOrEmpty()
        }.joinToString(separator = " 窶｢ ")

// --- 同じパッケージへ移設してきたフォーマット群 (元: ui/utils/StringUtils.kt) ---


fun formatFileSize(sizeBytes: Long): String {
    val prefix = if (sizeBytes < 0) "-" else ""
    val absBytes = sizeBytes.absoluteValue.toDouble()

    return when {
        absBytes < 1024 -> {
            "$prefix${absBytes.toLong()} B"
        }

        absBytes < 1024 * 1024 -> {
            val kb = absBytes / 1024
            "$prefix${DecimalFormat("#.#").format(kb)} KB"
        }

        absBytes < 1024 * 1024 * 1024 -> {
            val mb = absBytes / (1024 * 1024)
            "$prefix${DecimalFormat("#.#").format(mb)} MB"
        }

        absBytes < 1024L * 1024 * 1024 * 1024 -> {
            val gb = absBytes / (1024 * 1024 * 1024)
            "$prefix${DecimalFormat("#.##").format(gb)} GB"
        }

        else -> {
            val tb = absBytes / (1024L * 1024 * 1024 * 1024)
            "$prefix${DecimalFormat("#.##").format(tb)} TB"
        }
    }
}

fun numberFormatter(n: Int) =
    DecimalFormat("#,###")
        .format(n)
        .replace(",", ".")

fun formatCompactCount(count: Long): String {
    val abs = count.absoluteValue
    val prefix = if (count < 0) "-" else ""

    fun compactOneDecimal(divisor: Long): String {
        val value = floor(abs.toDouble() / (divisor / 10.0)) / 10.0
        val text = DecimalFormat("#.#").format(value).replace(",", ".")
        return if (text.endsWith(".0")) text.dropLast(2) else text
    }

    return when {
        abs < 1_000 -> "$count"
        abs < 10_000 -> prefix + compactOneDecimal(1_000) + "K"
        abs < 1_000_000 -> prefix + (abs / 1_000) + "K"
        abs < 10_000_000 -> prefix + compactOneDecimal(1_000_000) + "M"
        abs < 1_000_000_000 -> prefix + (abs / 1_000_000) + "M"
        abs < 10_000_000_000 -> prefix + compactOneDecimal(1_000_000_000) + "B"
        else -> prefix + (abs / 1_000_000_000) + "B"
    }
}
