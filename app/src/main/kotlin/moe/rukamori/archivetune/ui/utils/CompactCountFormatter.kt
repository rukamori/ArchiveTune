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
import java.util.Locale

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
 */
fun formatCompactCountLocalized(
    value: Long,
    languageTag: String?,
): String? {
    val tag = languageTag?.trim()?.replace('_', '-').orEmpty()
    val rules = compactCountRulesFor(tag) ?: return null
    val locale = Locale.forLanguageTag(tag)
    val absolute = value.absoluteValue

    val plainNumberLimit = rules.plainNumberLimit
    if (plainNumberLimit != null && absolute < plainNumberLimit) return formatPlainNumber(value, locale)

    val largestUnit = rules.units.maxByOrNull { it.multiplier } ?: return formatPlainNumber(value, locale)

    // No sampled language has a token above 10^8; refuse to fake one and print the full number.
    if (absolute >= largestUnit.multiplier * SCALED_UNIT_CEILING) return formatPlainNumber(value, locale)

    val unit =
        rules.units
            .filter { it.multiplier <= absolute }
            .maxByOrNull { it.multiplier }
            ?: return formatPlainNumber(value, locale)

    val scaled = BigDecimal(absolute).divide(BigDecimal(unit.multiplier), 4, RoundingMode.HALF_UP)
    val decimals =
        when {
            scaled >= HUNDRED -> 0
            scaled >= TEN -> 1
            else -> 2
        }
    val number =
        scaled
            .setScale(decimals, RoundingMode.HALF_UP)
            .stripTrailingZeros()
            .toPlainString()
            .replace('.', DecimalFormatSymbols.getInstance(locale).decimalSeparator)
    val prefix = if (value < 0) "-" else ""
    return "$prefix$number${unit.separator}${unit.token}"
}

private fun formatPlainNumber(
    value: Long,
    locale: Locale,
): String = DecimalFormat("#,##0", DecimalFormatSymbols.getInstance(locale)).format(value)

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
        parseCompactCount(text, contentLanguage)
            ?.let { count -> formatCompactCountLocalized(count, displayLanguage) }
    return when {
        formatted != null -> "$formatted $label"
        rawTextIncludesLabel -> text
        else -> "$text $label"
    }
}
