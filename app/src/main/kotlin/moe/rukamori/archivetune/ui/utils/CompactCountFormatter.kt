/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import kotlin.math.absoluteValue

private val HUNDRED = BigDecimal(100)
private val TEN = BigDecimal(10)
private const val SCALED_UNIT_CEILING = 1_000L

fun formatCompactCountLocalized(
    value: Long,
    languageTag: String?,
): String? {
    val tag = normalizeCompactCountTag(languageTag) ?: return null
    val rules = compactCountRulesFor(tag) ?: return null
    val symbols = countSymbols(tag)
    val absolute = value.absoluteValue
    fun plain(): String = formatPlainNumber(value, symbols, rules.plainNumberGrouping)

    val plainNumberLimit = rules.plainNumberLimit
    if (plainNumberLimit != null && absolute < plainNumberLimit) return plain()

    val largestUnit = rules.units.maxByOrNull { it.multiplier } ?: return plain()

    if (absolute >= largestUnit.multiplier * SCALED_UNIT_CEILING) return plain()

    var unit =
        rules.units
            .filter { it.multiplier <= absolute }
            .maxByOrNull { it.multiplier }
            ?: return plain()
    var number = roundedNumber(absolute, unit)
    while (true) {
        val next = rules.units.filter { it.multiplier > unit.multiplier }.minByOrNull { it.multiplier }
        if (next == null) {
            val ceiling = BigDecimal.valueOf(largestUnit.multiplier * SCALED_UNIT_CEILING)
            if (number.multiply(BigDecimal(unit.multiplier)) >= ceiling) return plain()
            break
        }
        if (number.multiply(BigDecimal(unit.multiplier)) < BigDecimal(next.multiplier)) break
        unit = next
        number = roundedNumber(absolute, unit)
    }
    val prefix = if (value < 0) "-" else ""
    return "$prefix${number.toPlainString().replace('.', symbols.decimalSeparator)}${unit.separator}${unit.token}"
}

private fun roundedNumber(
    absolute: Long,
    unit: CompactUnit,
): BigDecimal {
    val scaled = BigDecimal(absolute).divide(BigDecimal(unit.multiplier), 4, RoundingMode.HALF_UP)
    val decimals =
        when {
            scaled >= HUNDRED -> 0
            scaled >= TEN -> 1
            else -> 2
        }
    return scaled.setScale(decimals, RoundingMode.HALF_UP).stripTrailingZeros()
}

private fun formatPlainNumber(
    value: Long,
    symbols: DecimalFormatSymbols,
    grouping: Boolean,
): String = DecimalFormat(if (grouping) "#,##0" else "0", symbols).format(value)

fun artistCountStat(
    rawText: String?,
    label: String,
    contentLanguage: String?,
    displayLanguage: String?,
    rawTextIncludesLabel: Boolean,
): String? {
    val text = rawText?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    val formatted =
        parseCompactCount(text, contentLanguage, isBareCount = !rawTextIncludesLabel)
            ?.let { count -> formatCompactCountLocalized(count, displayLanguage) }
    return when {
        formatted != null -> "$formatted $label"
        rawTextIncludesLabel -> text
        else -> "$text $label"
    }
}
