/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

private const val NO_BREAK_SPACE = "\u00A0"

internal data class CompactUnit(
    val token: String,
    val multiplier: Long,
    /** Exactly what YouTube puts between the number and the token: "", " " or NBSP. */
    val separator: String = NO_BREAK_SPACE,
)

internal data class CompactCountRules(
    val units: List<CompactUnit>,
    /** Highest value YouTube still renders as a plain grouped number instead of a unit token. */
    val plainNumberLimit: Long? = null,
)

private fun unit(
    token: String,
    multiplier: Long,
    separator: String = NO_BREAK_SPACE,
): CompactUnit = CompactUnit(token = token, multiplier = multiplier, separator = separator)

private fun compactRules(vararg units: CompactUnit): CompactCountRules = CompactCountRules(units.toList())

private fun plainNumberRules(
    limit: Long,
    vararg units: CompactUnit,
): CompactCountRules = CompactCountRules(units.toList(), limit)

/**
 * Resolves an `hl` tag to its rules: exact match first, then the base language, so `pt-BR`,
 * `ar-EG`, `en-NG` and `zh-CN` inherit from `pt`, `ar`, `en` and `zh`. Unknown tags return null.
 */
internal fun compactCountRulesFor(languageTag: String?): CompactCountRules? {
    val tag = languageTag?.trim()?.replace('_', '-').orEmpty()
    if (tag.isEmpty()) return null
    CompactCountRulesByLanguage[tag]?.let { return it }
    return CompactCountRulesByLanguage[tag.substringBefore('-')]
}

/**
 * Compact count tokens per content language, measured from WEB_REMIX `browse` payloads
 * (`clientName=WEB_REMIX`, one request per `hl`, artist page header fields
 * `musicImmersiveHeaderRenderer.monthlyListenerCount` and
 * `subscriptionButton.subscribeButtonRenderer.subscriberCountText`).
 *
 * These are not CLDR patterns. YouTube deviates from CLDR repeatedly: `es` uses `K` where CLDR
 * uses `mil`, `de`/`da`/`sv` send plain grouped numbers below one million, `it`/`nl`/`no`/`ms`
 * attach the thousand token but space the million one, `ms` mixes `J` for millions with English
 * `K` for thousands, and `he` returns English tokens entirely.
 *
 * Keys are `hl` values from `LanguageCodeToName`. Region variants resolve to their base
 * language (`en-NG`, `pt-BR`, `ar-EG`, `zh-CN`), so only the variants that actually differ are
 * listed: `en-GB` (lowercase tokens) and `zh-TW` (different ideographs).
 *
 * Sampled magnitudes: 10^3, 10^4, 10^5, 10^6, 10^7, 10^8. No 10^9 sample exists, so billion
 * tokens are deliberately absent: a payload using one parses to null instead of a guess.
 */
internal val CompactCountRulesByLanguage: Map<String, CompactCountRules> =
    mapOf(
        "en" to
            compactRules(
                unit("K", 1_000L, separator = ""),
                unit("M", 1_000_000L, separator = ""),
            ),
        "en-GB" to
            compactRules(
                unit("k", 1_000L, separator = ""),
                unit("m", 1_000_000L, separator = ""),
            ),
        "ja" to
            compactRules(
                unit("万", 10_000L, separator = ""),
                unit("億", 100_000_000L, separator = ""),
            ),
        "ko" to
            compactRules(
                unit("만", 10_000L, separator = ""),
                unit("억", 100_000_000L, separator = ""),
            ),
        "vi" to compactRules(unit("N", 1_000L), unit("Tr", 1_000_000L)),
        "zh" to
            compactRules(
                unit("万", 10_000L, separator = ""),
                unit("亿", 100_000_000L, separator = ""),
            ),
        "zh-TW" to
            compactRules(
                unit("萬", 10_000L, separator = ""),
                unit("億", 100_000_000L, separator = ""),
            ),
        "fr" to compactRules(unit("k", 1_000L), unit("M", 1_000_000L)),
        "de" to plainNumberRules(1_000_000L, unit("Mio", 1_000_000L)),
        "es" to compactRules(unit("K", 1_000L), unit("M", 1_000_000L)),
        "pt" to compactRules(unit("mil", 1_000L), unit("mi", 1_000_000L)),
        "ru" to compactRules(unit("тыс", 1_000L), unit("млн", 1_000_000L)),
        "it" to
            compactRules(
                unit("K", 1_000L, separator = ""),
                unit("Mln", 1_000_000L),
            ),
        "nl" to
            compactRules(
                unit("K", 1_000L, separator = ""),
                unit("mln", 1_000_000L),
            ),
        "pl" to compactRules(unit("tys", 1_000L), unit("mln", 1_000_000L)),
        "tr" to compactRules(unit("B", 1_000L), unit("Mn", 1_000_000L)),
        "ar" to compactRules(unit("ألف", 1_000L), unit("مليون", 1_000_000L)),
        "hi" to
            compactRules(
                unit("हज़ार", 1_000L),
                unit("लाख", 100_000L),
                unit("क॰", 10_000_000L),
            ),
        "th" to
            compactRules(
                unit("หมื่น", 10_000L, separator = " "),
                unit("แสน", 100_000L, separator = " "),
                unit("ล้าน", 1_000_000L, separator = " "),
            ),
        "id" to compactRules(unit("rb", 1_000L), unit("jt", 1_000_000L)),
        "ms" to
            compactRules(
                unit("K", 1_000L, separator = ""),
                unit("J", 1_000_000L, separator = ""),
            ),
        "uk" to compactRules(unit("тис", 1_000L), unit("млн", 1_000_000L)),
        "cs" to compactRules(unit("tis", 1_000L), unit("mil", 1_000_000L)),
        "el" to compactRules(unit("χιλ", 1_000L), unit("εκ", 1_000_000L)),
        "he" to
            compactRules(
                unit("K", 1_000L, separator = ""),
                unit("M", 1_000_000L, separator = ""),
            ),
        "hu" to compactRules(unit("E", 1_000L), unit("M", 1_000_000L)),
        "ro" to compactRules(unit("K", 1_000L), unit("mil", 1_000_000L)),
        "fi" to compactRules(unit("t", 1_000L), unit("milj", 1_000_000L)),
        "da" to plainNumberRules(1_000_000L, unit("mio", 1_000_000L)),
        "no" to
            compactRules(
                unit("k", 1_000L, separator = ""),
                unit("mill", 1_000_000L),
            ),
        "sv" to plainNumberRules(1_000_000L, unit("mn", 1_000_000L)),
        "sk" to compactRules(unit("tis", 1_000L), unit("mil", 1_000_000L)),
        "bg" to compactRules(unit("хил", 1_000L), unit("млн", 1_000_000L)),
        "hr" to compactRules(unit("tis", 1_000L), unit("mil", 1_000_000L)),
        "sr" to compactRules(unit("хиљ", 1_000L), unit("мил", 1_000_000L)),
        "lt" to compactRules(unit("tūkst", 1_000L), unit("mln", 1_000_000L)),
        "lv" to compactRules(unit("tūkst", 1_000L), unit("milj", 1_000_000L)),
        "et" to compactRules(unit("tuh", 1_000L), unit("mln", 1_000_000L)),
    )
