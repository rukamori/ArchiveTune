/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.absoluteValue
import kotlin.math.floor
import kotlin.math.roundToLong

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

private val UNICODE_SPACES_REGEX = Regex("""[\p{Z}\s]+""")
private val NUMBER_WITH_UNIT_REGEX = Regex(
    """(\d+(?:[.,]\d+)?)\s*([^\d\s,.]+)""",
    RegexOption.IGNORE_CASE
)

private val EXACT_MULTIPLIERS = mapOf(
    // 10^3 (Thousand)
    "k" to 1_000L,
    "rb" to 1_000L,
    "ribu" to 1_000L,
    "tsd" to 1_000L,
    "tausend" to 1_000L,
    "тыс" to 1_000L,
    "тысяч" to 1_000L,
    "тысячи" to 1_000L,
    "tys" to 1_000L,
    "bin" to 1_000L,
    "nghìn" to 1_000L,
    "ngàn" to 1_000L,
    "हज़ार" to 1_000L,
    "thousand" to 1_000L,
    "thousands" to 1_000L,

    // 10^5 (Lakh)
    "lakh" to 100_000L,
    "lakhs" to 100_000L,
    "lac" to 100_000L,
    "लाख" to 100_000L,

    // 10^6 (Million)
    "m" to 1_000_000L,
    "jt" to 1_000_000L,
    "juta" to 1_000_000L,
    "mio" to 1_000_000L,
    "mln" to 1_000_000L,
    "mn" to 1_000_000L,
    "млн" to 1_000_000L,
    "tr" to 1_000_000L,
    "triệu" to 1_000_000L,
    "trieu" to 1_000_000L,
    "million" to 1_000_000L,
    "millions" to 1_000_000L,
    "millionen" to 1_000_000L,
    "millones" to 1_000_000L,
    "milhões" to 1_000_000L,
    "milyon" to 1_000_000L,
    "ล้าน" to 1_000_000L,

    // 10^7 (Crore)
    "cr" to 10_000_000L,
    "crore" to 10_000_000L,
    "crores" to 10_000_000L,
    "करोड़" to 10_000_000L,

    // 10^9 (Billion)
    "b" to 1_000_000_000L,
    "mly" to 1_000_000_000L,
    "miliar" to 1_000_000_000L,
    "milyar" to 1_000_000_000L,
    "mrd" to 1_000_000_000L,
    "md" to 1_000_000_000L,
    "млрд" to 1_000_000_000L,
    "billion" to 1_000_000_000L,
    "billions" to 1_000_000_000L,
    "milliarden" to 1_000_000_000L,
    "tỷ" to 1_000_000_000L,
    "ty" to 1_000_000_000L,
    "अरब" to 1_000_000_000L,
)

private val CJK_MULTIPLIERS = mapOf(
    '万' to 10_000L,
    '萬' to 10_000L,
    '만' to 10_000L,
    '亿' to 100_000_000L,
    '億' to 100_000_000L,
    '억' to 100_000_000L,
)

private fun resolveMultiplier(rawUnit: String): Long? {
    val cleanUnit = rawUnit.lowercase(Locale.US).trim { it.isWhitespace() || it == '.' || it == ',' }
    EXACT_MULTIPLIERS[cleanUnit]?.let { return it }

    if (rawUnit.isNotEmpty()) {
        CJK_MULTIPLIERS[rawUnit.first()]?.let { return it }
    }

    return null
}

fun parseCompactNumber(rawText: String?): Long? {
    if (rawText.isNullOrBlank()) return null
    val normalized = rawText.replace(UNICODE_SPACES_REGEX, " ")

    val match = NUMBER_WITH_UNIT_REGEX.find(normalized)
    if (match != null) {
        val rawUnit = match.groupValues[2]
        val multiplier = resolveMultiplier(rawUnit)

        if (multiplier != null) {
            val numStr = match.groupValues[1].replace(',', '.')
            val baseNumber = numStr.toDoubleOrNull() ?: return null
            return (baseNumber * multiplier).roundToLong()
        }
    }

    val digits = normalized.filter { it.isDigit() }
    return digits.toLongOrNull()
}

fun parseArtistCompactCount(rawText: String?): String? {
    if (rawText.isNullOrBlank()) return null
    val normalized = rawText.replace(UNICODE_SPACES_REGEX, " ")

    val match = NUMBER_WITH_UNIT_REGEX.find(normalized)
    if (match != null) {
        val rawUnit = match.groupValues[2]
        val multiplier = resolveMultiplier(rawUnit)

        if (multiplier != null) {
            val numStr = match.groupValues[1].replace(',', '.')
            val baseNumber = numStr.toDoubleOrNull() ?: return null
            val total = baseNumber * multiplier
            return formatScaledCompactMetric(total)
        }
    }

    val digits = normalized.filter { it.isDigit() }
    val count = digits.toLongOrNull() ?: return null
    return formatCompactCount(count)
}

private fun formatScaledCompactMetric(total: Double): String {
    val abs = total.absoluteValue
    val prefix = if (total < 0) "-" else ""

    val (scaled, suffix) = when {
        abs >= 1_000_000_000 -> (abs / 1_000_000_000.0) to "B"
        abs >= 1_000_000 -> (abs / 1_000_000.0) to "M"
        abs >= 1_000 -> (abs / 1_000.0) to "K"
        else -> return prefix + abs.roundToLong().toString()
    }

    val rounded = (scaled * 10).roundToLong() / 10.0
    val numberText = if (rounded >= 100 || rounded % 1.0 == 0.0) {
        rounded.roundToLong().toString()
    } else {
        DecimalFormat("0.#", DecimalFormatSymbols(Locale.US)).format(rounded)
    }

    return "$prefix$numberText$suffix"
}
