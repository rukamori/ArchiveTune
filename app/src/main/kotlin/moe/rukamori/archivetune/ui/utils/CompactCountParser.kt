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
private val DECIMAL_SEPARATORS = charArrayOf('.', ',')

fun parseCompactCount(
    rawText: String?,
    languageTag: String?,
    isBareCount: Boolean = false,
): Long? {
    val text = rawText?.trim().orEmpty()
    if (text.isEmpty()) return null
    val tag = normalizeCompactCountTag(languageTag) ?: return null
    val rules = compactCountRulesFor(tag) ?: return null
    val symbols = countSymbols(tag)

    val unitMatch = findUnitMatch(text, rules)
    if (unitMatch == null) return parsePlainCount(text, rules, symbols, isBareCount)

    val numberText = numberBefore(text, unitMatch.second) ?: return null
    val value = decimalValue(numberText, symbols) ?: return null
    return value
        .multiply(BigDecimal(unitMatch.first.multiplier))
        .setScale(0, RoundingMode.HALF_UP)
        .toLong()
}

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
        val beforeOk = index == 0 || !text[index - 1].isWordChar()
        var afterIndex = index + token.length
        if (afterIndex < text.length && text[afterIndex] == '.') afterIndex++
        val afterOk =
            afterIndex >= text.length ||
                !text[afterIndex].isWordChar() ||
                token.first().isCjkScript()
        if (beforeOk && afterOk) return index
        fromIndex = index + 1
    }
    return null
}

private fun numberBefore(
    text: String,
    endExclusive: Int,
): String? {
    var end = endExclusive
    while (end > 0 && text[end - 1].isNumberPadding()) end--
    var start = end
    var digits = 0
    while (start > 0) {
        val char = text[start - 1]
        val previous = if (start >= 2) text[start - 2] else null
        val partOfNumber =
            when {
                char.isDigit() -> true
                char in DECIMAL_SEPARATORS -> previous != null && previous.isDigit()
                char in NUMBER_GROUPING_WHITESPACE -> previous != null && previous.isDigit()
                else -> false
            }
        if (!partOfNumber) break
        if (char.isDigit()) digits++
        start--
    }
    if (digits == 0) return null
    return text.substring(start, end)
}

private fun parsePlainCount(
    text: String,
    rules: CompactCountRules,
    symbols: DecimalFormatSymbols,
    isBareCount: Boolean,
): Long? {
    val start = text.indexOfFirst(Char::isDigit)
    if (start < 0) return null
    if (start > 0 && text[start - 1].isLetter()) return null
    var end = start
    while (end < text.length) {
        val char = text[end]
        val next = text.getOrNull(end + 1)
        val partOfNumber =
            char.isDigit() ||
                ((char in DECIMAL_SEPARATORS || char in NUMBER_GROUPING_WHITESPACE) && next?.isDigit() == true)
        if (!partOfNumber) break
        end++
    }
    val value = decimalValue(text.substring(start, end), symbols) ?: return null
    if (value.stripTrailingZeros().scale() > 0) return null

    val rest = text.substring(end)
    if (isBareCount) {
        if (rest.any { !it.isNumberPadding() }) return null
    } else {
        val glued = text.getOrNull(end)
        if (glued != null && glued.isLetter() && !glued.isCjkCounter()) return null
        val nextWord =
            rest
                .trim { it.isNumberPadding() }
                .takeWhile { !it.isNumberPadding() }
                .trimEnd('.', ',')
        if (nextWord.isNotEmpty() && isUnknownUnitToken(nextWord)) return null
    }

    val limit = rules.plainNumberLimit
    return when {
        limit != null && value < BigDecimal.valueOf(limit) -> value.toLong()
        value < PLAIN_NUMBER_LIMIT -> value.toLong()
        else -> null
    }
}

private fun isUnknownUnitToken(word: String): Boolean {
    val lower = word.lowercase()
    if (lower in knownCompactUnitTokens || lower in unsampledScaleTokens) return true
    return knownCompactUnitTokens.any { it.length >= 3 && lower.contains(it) }
}

internal fun countSymbols(languageTag: String): DecimalFormatSymbols =
    DecimalFormatSymbols.getInstance(Locale.forLanguageTag(languageTag)).apply {
        zeroDigit = '0'
        if (languageTag.substringBefore('-') == "ar") {
            decimalSeparator = '.'
            groupingSeparator = ','
        }
    }

private fun decimalValue(
    numberText: String,
    symbols: DecimalFormatSymbols,
): BigDecimal? {
    val cleaned =
        buildString {
            for (char in numberText) {
                val digit = Character.digit(char, 10)
                when {
                    digit >= 0 -> append('0' + digit)
                    char == symbols.decimalSeparator -> append('.')
                    char == symbols.groupingSeparator -> Unit
                    char in NUMBER_GROUPING_WHITESPACE -> Unit
                    else -> return null
                }
            }
        }
    return cleaned.toBigDecimalOrNull()
}

private fun Char.isWordChar(): Boolean =
    isLetter() || category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

private fun Char.isNumberPadding(): Boolean =
    isWhitespace() || category == CharCategory.FORMAT || this in NUMBER_GROUPING_WHITESPACE

private fun Char.isCjkCounter(): Boolean {
    val lower = toString().lowercase()
    return isCjkScript() && lower !in knownCompactUnitTokens && lower !in unsampledScaleTokens
}

private fun Char.isCjkScript(): Boolean =
    when (Character.UnicodeScript.of(code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }
