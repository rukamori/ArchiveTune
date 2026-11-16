/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import java.util.Locale

private const val NO_BREAK_SPACE = "\u00A0"

internal data class CompactUnit(
    val token: String,
    val multiplier: Long,
    val separator: String = NO_BREAK_SPACE,
)

internal data class CompactCountRules(
    val units: List<CompactUnit>,
    val plainNumberLimit: Long? = null,
    val plainNumberGrouping: Boolean = true,
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

private fun ungroupedPlainNumberRules(
    limit: Long,
    vararg units: CompactUnit,
): CompactCountRules = CompactCountRules(units.toList(), limit, plainNumberGrouping = false)

internal fun compactCountRulesFor(languageTag: String?): CompactCountRules? {
    val tag = normalizeCompactCountTag(languageTag) ?: return null
    CompactCountRulesByLanguage[tag]?.let { return it }
    return CompactCountRulesByLanguage[tag.substringBefore('-')]
}

internal fun normalizeCompactCountTag(languageTag: String?): String? {
    val raw = languageTag?.trim()?.replace('_', '-')?.takeIf { it.isNotEmpty() } ?: return null
    val language = LEGACY_LANGUAGE_CODES[raw.substringBefore('-').lowercase()]
        ?: raw.substringBefore('-').lowercase()
    if (language.isEmpty()) return null
    val locale = Locale.forLanguageTag(raw)
    if (language == "zh") {
        when {
            locale.script == "Hant" || locale.country in TRADITIONAL_CHINESE_REGIONS -> return "zh-TW"
            locale.script == "Hans" -> return "zh"
        }
    }
    if (language == "nb" || language == "nn") return "no"
    return buildList {
        add(language)
        if (locale.script.isNotEmpty()) add(locale.script)
        if (locale.country.isNotEmpty()) add(locale.country)
    }.joinToString("-")
}

private val LEGACY_LANGUAGE_CODES = mapOf("in" to "id", "iw" to "he", "ji" to "yi", "jw" to "jv", "mo" to "ro")

private val TRADITIONAL_CHINESE_REGIONS = setOf("TW", "HK", "MO")

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
            ungroupedPlainNumberRules(
                10_000L,
                unit("万", 10_000L, separator = ""),
                unit("億", 100_000_000L, separator = ""),
            ),
        "ko" to
            compactRules(
                unit("천", 1_000L, separator = ""),
                unit("만", 10_000L, separator = ""),
                unit("억", 100_000_000L, separator = ""),
            ),
        "vi" to compactRules(unit("N", 1_000L), unit("Tr", 1_000_000L)),
        "zh" to
            ungroupedPlainNumberRules(
                10_000L,
                unit("万", 10_000L, separator = ""),
                unit("亿", 100_000_000L, separator = ""),
            ),
        "zh-TW" to
            ungroupedPlainNumberRules(
                10_000L,
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
                unit("พัน", 1_000L, separator = " "),
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

internal val knownCompactUnitTokens: Set<String> =
    CompactCountRulesByLanguage.values.flatMap { rules -> rules.units.map { it.token.lowercase() } }.toSet()

internal val unsampledScaleTokens: Set<String> =
    setOf(
        "b",
        "mrd",
        "mld",
        "mlrd",
        "mljrd",
        "mia",
        "md",
        "miliar",
        "miljard",
        "milliard",
        "miliard",
        "milyar",
        "bi",
        "bilhão",
        "tỷ",
        "mr",
        "billion",
        "млрд",
        "миллиард",
        "δισ",
        "مليار",
        "अरब",
        "兆",
        "조",
    )
