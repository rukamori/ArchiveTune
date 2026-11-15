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

/**
 * Renders [value] with the compact tokens measured for [languageTag], e.g. `408 jt`,
 * `40.8 क॰`, `4.08億`.
 *
 * [languageTag] is the language the app should display in, which is not necessarily the content
 * language the payload came from. Returns null for languages without a table entry so callers
 * can decide what to show instead.
 *
 * Precision follows YouTube: three significant digits (`4.08億`, `40.8 क॰`, `408 M`, `6350万`).
 * Rounding can reach the next token, which is decided per language: `999.5K` is `1M`, but
 * `9999.5万` is `1億` and `9995천` is `1만`, so the comparison is against the next multiplier
 * rather than a fixed 1000.
 */
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

    // No sampled language has a token above 10^8; refuse to fake one and print the full number.
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
            // Rounding past the largest sampled token (`999.5M` -> `1000M`) has no honest token:
            // print the full number instead of inventing `1B`.
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

/** [absolute] at [unit], rounded the way YouTube does: three significant digits. */
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

/**
 * Text for one audience stat: the number re-rendered in the display language when it can be
 * derived, otherwise the server text untouched so the stat is never dropped.
 *
 * [rawTextIncludesLabel] is true for `monthlyListenerCount` (YouTube ships a full phrase such as
 * `408 jt audiens bulanan`) and false for `subscriberCountText`, which is a bare count and still
 * needs the app's label appended.
 */
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
