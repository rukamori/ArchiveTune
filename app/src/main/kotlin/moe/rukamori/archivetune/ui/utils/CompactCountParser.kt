/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormatSymbols
import java.util.Locale

private val PLAIN_NUMBER_LIMIT = BigDecimal(1_000)
private val NUMBER_GROUPING_WHITESPACE = charArrayOf(' ', '\u00A0', '\u202F', '\u2009')

/**
 * Reads a YouTube Music compact count, e.g. `63,5 jt`, `408 Mio.`, `月間視聴者数: 4.08億 人`.
 *
 * [languageTag] must be the content language the payload was requested with, not the UI
 * locale: the server formats the number in `hl`, while the app renders its own label.
 *
 * Returns null whenever the value cannot be determined exactly, including languages that are
 * not in [CompactCountRulesByLanguage] and unit tokens that were never sampled. Callers are
 * expected to drop the stat instead of showing a guess.
 */
fun parseCompactCount(
    rawText: String?,
    languageTag: String?,
): Long? {
    val text = rawText?.trim().orEmpty()
    if (text.isEmpty()) return null
    val tag = languageTag?.trim()?.replace('_', '-').orEmpty()
    val rules = compactCountRulesFor(tag) ?: return null
    val locale = Locale.forLanguageTag(tag)

    val unitMatch = findUnitMatch(text, rules)
    if (unitMatch == null) return parsePlainCount(text, rules, locale)

    val numberText = numberBefore(text, unitMatch.second) ?: return null
    val value = decimalValue(numberText, locale) ?: return null
    return value
        .multiply(BigDecimal(unitMatch.first.multiplier))
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
}

/** First (unit, tokenStart) pair, or null when no unit appears or two different ones do. */
private fun findUnitMatch(
    text: String,
    rules: CompactCountRules,
): Pair<CompactUnit, Int>? {
    var match: Pair<CompactUnit, Int>? = null
    for (unit in rules.units.sortedByDescending { it.token.length }) {
        val index = indexOfToken(text, unit.token) ?: continue
        if (match != null && match.second != index) return null
        match = unit to index
    }
    return match
}

private fun indexOfToken(
    text: String,
    token: String,
): Int? {
    var fromIndex = 0
    while (fromIndex <= text.length - token.length) {
        val index = text.indexOf(token, fromIndex, ignoreCase = true)
        if (index < 0) return null
        val beforeOk = index == 0 || !text[index - 1].isLetter()
        var afterIndex = index + token.length
        // YouTube appends the abbreviation dot in several languages ("тыс.", "Mio.", "mil.").
        if (afterIndex < text.length && text[afterIndex] == '.') afterIndex++
        // CJK units glue to their label ("4.08억명", "4.08億位"); Latin ones never do, and the
        // strict check keeps "mi" from matching inside "mil".
        val afterOk =
            afterIndex >= text.length ||
                !text[afterIndex].isLetter() ||
                !token.isLatinScript()
        if (beforeOk && afterOk) return index
        fromIndex = index + 1
    }
    return null
}

private fun numberBefore(
    text: String,
    endExclusive: Int,
): String? {
    var index = endExclusive
    while (index > 0 && isNumberChar(text[index - 1])) index--
    val candidate = text.substring(index, endExclusive).trim()
    return candidate.takeIf { it.any(Char::isDigit) }
}

private fun parsePlainCount(
    text: String,
    rules: CompactCountRules,
    locale: Locale,
): Long? {
    val start = text.indexOfFirst(Char::isDigit)
    if (start < 0) return null
    var end = start
    while (end < text.length && isNumberChar(text[end])) end++
    val value = decimalValue(text.substring(start, end), locale) ?: return null

    val limit = rules.plainNumberLimit
    return when {
        limit != null && value < BigDecimal.valueOf(limit) -> value.setScale(0, RoundingMode.HALF_UP).toLong()
        // Counts below one thousand never carry a unit token in any sampled language.
        value.stripTrailingZeros().scale() <= 0 && value < PLAIN_NUMBER_LIMIT -> value.toLong()
        else -> null
    }
}

private fun decimalValue(
    numberText: String,
    locale: Locale,
): BigDecimal? {
    val symbols = DecimalFormatSymbols.getInstance(locale)
    val decimalSeparator = symbols.decimalSeparator
    val cleaned =
        buildString {
            for (char in numberText) {
                when {
                    char == decimalSeparator -> append('.')
                    char.isDigit() -> append(char)
                    char == symbols.groupingSeparator -> Unit
                    char in NUMBER_GROUPING_WHITESPACE -> Unit
                    else -> return null
                }
            }
        }
    return cleaned.toBigDecimalOrNull()
}

private fun isNumberChar(char: Char): Boolean =
    char.isDigit() || char == '.' || char == ',' || char in NUMBER_GROUPING_WHITESPACE

/** Basic Latin, Latin-1 Supplement and Latin Extended-A/B; used to tell CJK units from Latin ones. */
private fun String.isLatinScript(): Boolean = all { it.code < 0x0250 }
