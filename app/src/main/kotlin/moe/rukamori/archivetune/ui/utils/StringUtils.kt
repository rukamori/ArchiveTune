/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import java.text.DecimalFormat
import kotlin.math.absoluteValue
import kotlin.math.floor

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
