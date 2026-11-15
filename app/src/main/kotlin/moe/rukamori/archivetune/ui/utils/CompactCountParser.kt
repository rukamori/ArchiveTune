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

/**
 * Reads a YouTube Music compact count, e.g. `63,5 jt`, `408 Mio.`, `4.08億 人`.
 *
 * [languageTag] must be the content language the payload was requested with, not the UI
 * locale: the server formats the number in `hl`, while the app renders its own label.
 *
 * [isBareCount] is true for `subscriberCountText`, which is a number and nothing else. Bare
 * counts are matched strictly, so an unknown token (`1B`, `2 Mrd.`) is refused instead of being
 * read as the digits in front of it.
 *
 * Returns null whenever the value cannot be determined exactly, including languages that are
 * not in [CompactCountRulesByLanguage] and unit tokens that were never sampled. Callers are
 * expected to drop the stat instead of showing a guess.
 */
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
        val beforeOk = index == 0 || !text[index - 1].isWordChar()
        var afterIndex = index + token.length
        // YouTube appends the abbreviation dot in several languages ("тыс.", "Mio.", "mil.").
        if (afterIndex < text.length && text[afterIndex] == '.') afterIndex++
        // CJK units glue to the counter behind them ("4.08억명", "4.08億位"), which is measured.
        // Every other script spaces its unit off the label, so the boundary check stays on and
        // "тыс" no longer matches inside a longer Cyrillic word.
        val afterOk =
            afterIndex >= text.length ||
                !text[afterIndex].isWordChar() ||
                token.first().isCjkScript()
        if (beforeOk && afterOk) return index
        fromIndex = index + 1
    }
    return null
}

/**
 * Number that belongs to the unit token starting at [endExclusive].
 *
 * A separator only counts as part of the number when it sits between two digits, so sentence
 * punctuation never leaks in (`approx. 408M` stays `408`) while grouped forms keep working
 * (`60 300`, `65\u00A0300`, `2.46\u00A0مليون`).
 */
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
    // A number glued to a letter in front of it is part of a word, not a count.
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
    // Unsampled units carry the decimals ("1,2 Mrd."); a plain count is always an integer.
    if (value.stripTrailingZeros().scale() > 0) return null

    val rest = text.substring(end)
    if (isBareCount) {
        if (rest.any { !it.isNumberPadding() }) return null
    } else {
        val glued = text.getOrNull(end)
        // "850명" and "2490位" are counters glued to a count; "408M" and "2천" are units we
        // either know (and would have matched) or cannot read, so they are refused.
        if (glued != null && glued.isLetter() && !glued.isCjkCounter()) return null
        // The word after the number, up to the next gap, minus the abbreviation dot ("Mrd.").
        // Written as a run instead of a letter run: Thai and Hindi spell parts of a word with
        // combining marks, which `isLetter` does not cover.
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
        // Counts below one thousand never carry a unit token in any sampled language.
        value < PLAIN_NUMBER_LIMIT -> value.toLong()
        else -> null
    }
}

/**
 * A unit token that is known to exist but was never sampled (10^9 and above), either in this
 * language or in another one: a payload that still contains one cannot be read exactly.
 */
private fun isUnknownUnitToken(word: String): Boolean {
    val lower = word.lowercase()
    if (lower in knownCompactUnitTokens || lower in unsampledScaleTokens) return true
    // A compound or spelled-out unit carries a known token inside it (`พันล้าน` is 10^9,
    // `miljard` is 10^9). Tokens shorter than three characters are skipped because labels
    // collide with them.
    return knownCompactUnitTokens.any { it.length >= 3 && lower.contains(it) }
}

/** Latin digits and the separators YouTube ships for [languageTag], never the device's ICU. */
internal fun countSymbols(languageTag: String): DecimalFormatSymbols =
    DecimalFormatSymbols.getInstance(Locale.forLanguageTag(languageTag)).apply {
        zeroDigit = '0'
        // YouTube keeps the Latin convention for Arabic (`63.5 مليون`), while ICU uses Arabic
        // separators for `ar-EG`/`ar-SA` and swaps them for `ar-MA`/`ar-DZ`.
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

/**
 * Letter or the combining mark that belongs to it. Thai and the Indic scripts split a word
 * across base letters and spacing/non-spacing marks (`พัน`, `हज़ार`), so a boundary after the
 * base letter alone would cut the word in half.
 */
private fun Char.isWordChar(): Boolean =
    isLetter() || category == CharCategory.NON_SPACING_MARK || category == CharCategory.COMBINING_SPACING_MARK

/** Whitespace, non-breaking grouping spaces and bidi control marks that surround payload text. */
private fun Char.isNumberPadding(): Boolean =
    isWhitespace() || category == CharCategory.FORMAT || this in NUMBER_GROUPING_WHITESPACE

/** CJK counter glued to the number (`명`, `位`), as opposed to a CJK unit token (`천`, `억`). */
private fun Char.isCjkCounter(): Boolean =
    isCjkScript() && toString().lowercase() !in knownCompactUnitTokens

/** Han, Hiragana, Katakana and Hangul are the scripts whose units glue to their label. */
private fun Char.isCjkScript(): Boolean =
    when (Character.UnicodeScript.of(code)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        Character.UnicodeScript.HANGUL,
        -> true
        else -> false
    }
