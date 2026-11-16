/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CompactCountFormatterTest {
    @Test
    fun matchesMeasuredShapes() {
        assertEquals("408M", formatCompactCountLocalized(408_000_000L, "en"))
        assertEquals("65.3K", formatCompactCountLocalized(65_300L, "en"))
        assertEquals("408m", formatCompactCountLocalized(408_000_000L, "en-GB"))
        assertEquals("65.3k", formatCompactCountLocalized(65_300L, "en-GB"))
        assertEquals("408\u00A0jt", formatCompactCountLocalized(408_000_000L, "id"))
        assertEquals("65,3\u00A0rb", formatCompactCountLocalized(65_300L, "id"))
        assertEquals("40.8\u00A0क॰", formatCompactCountLocalized(408_000_000L, "hi"))
        assertEquals("4.56\u00A0लाख", formatCompactCountLocalized(456_000L, "hi"))
        assertEquals("65.3\u00A0हज़ार", formatCompactCountLocalized(65_300L, "hi"))
        assertEquals("4.08億", formatCompactCountLocalized(408_000_000L, "ja"))
        assertEquals("6350万", formatCompactCountLocalized(63_500_000L, "ja"))
        assertEquals("1億", formatCompactCountLocalized(99_995_000L, "ja"))
        assertEquals("4.08억", formatCompactCountLocalized(408_000_000L, "ko"))
        assertEquals("4.08億", formatCompactCountLocalized(408_000_000L, "zh-TW"))
        assertEquals("6.53万", formatCompactCountLocalized(65_300L, "zh-CN"))
        assertEquals("408 ล้าน", formatCompactCountLocalized(408_000_000L, "th"))
        assertEquals("6.53 หมื่น", formatCompactCountLocalized(65_300L, "th"))
        assertEquals("65,3\u00A0B", formatCompactCountLocalized(65_300L, "tr"))
        assertEquals("408\u00A0Mn", formatCompactCountLocalized(408_000_000L, "tr"))
        assertEquals("65,3K", formatCompactCountLocalized(65_300L, "it"))
        assertEquals("408\u00A0Mln", formatCompactCountLocalized(408_000_000L, "it"))
        assertEquals("65,3k", formatCompactCountLocalized(65_300L, "no"))
        assertEquals("408J", formatCompactCountLocalized(408_000_000L, "ms"))
        assertEquals("408M", formatCompactCountLocalized(408_000_000L, "he"))
        assertEquals("65,3\u00A0тыс", formatCompactCountLocalized(65_300L, "ru"))
        assertEquals("65,3\u00A0mi", formatCompactCountLocalized(65_300_000L, "pt"))
    }

    @Test
    fun matchesPlainNumberLanguages() {
        assertEquals("65.300", formatCompactCountLocalized(65_300L, "de"))
        assertEquals("408\u00A0Mio", formatCompactCountLocalized(408_000_000L, "de"))
        assertEquals("65.300", formatCompactCountLocalized(65_300L, "da"))
        assertEquals("65\u00A0300", formatCompactCountLocalized(65_300L, "sv"))
        assertEquals("408\u00A0mn", formatCompactCountLocalized(408_000_000L, "sv"))
        assertEquals("850", formatCompactCountLocalized(850L, "en"))
        assertEquals("850", formatCompactCountLocalized(850L, "de"))
    }

    @Test
    fun roundTripsEverySampledMagnitude() {
        val languages = CompactCountRulesByLanguage.keys
        val values = longArrayOf(850L, 12_300L, 65_300L, 456_000L, 1_200_000L, 63_500_000L, 408_000_000L)
        for (language in languages) {
            for (value in values) {
                val formatted = formatCompactCountLocalized(value, language)
                assertEquals("$language cannot format $value", value, parseCompactCount(formatted, language))
            }
        }
    }

    @Test
    fun promotesToTheNextTokenOfThatLanguage() {
        assertEquals("1M", formatCompactCountLocalized(999_500L, "en"))
        assertEquals("1\u00A0लाख", formatCompactCountLocalized(99_950L, "hi"))
        assertEquals("1 หมื่น", formatCompactCountLocalized(9_995L, "th"))
        assertEquals("1만", formatCompactCountLocalized(9_995L, "ko"))
        assertEquals("999,950,000", formatCompactCountLocalized(999_950_000L, "en"))
        assertEquals("6350万", formatCompactCountLocalized(63_500_000L, "ja"))
    }

    @Test
    fun rendersMeasuredThousands() {
        assertEquals("2.49천", formatCompactCountLocalized(2_490L, "ko"))
        assertEquals("2.49 พัน", formatCompactCountLocalized(2_490L, "th"))
        assertEquals("2490", formatCompactCountLocalized(2_490L, "ja"))
        assertEquals("2490", formatCompactCountLocalized(2_490L, "zh-TW"))
    }

    @Test
    fun keepsLatinDigitsForArabic() {
        assertEquals("63.5\u00A0مليون", formatCompactCountLocalized(63_500_000L, "ar-EG"))
        assertEquals("850", formatCompactCountLocalized(850L, "ar-EG"))
    }

    @Test
    fun resolvesDeviceTagForms() {
        assertEquals("2490", formatCompactCountLocalized(2_490L, "zh-Hant-TW"))
        assertEquals("65,3k", formatCompactCountLocalized(65_300L, "nb-NO"))
        assertEquals("408\u00A0jt", formatCompactCountLocalized(408_000_000L, "in-ID"))
        assertEquals("408M", formatCompactCountLocalized(408_000_000L, "iw-IL"))
    }

    @Test
    fun refusesUnknownLanguage() {
        assertNull(formatCompactCountLocalized(1_000L, "xx"))
        assertNull(formatCompactCountLocalized(1_000L, null))
    }

    @Test
    fun keepsServerTextWhenItCannotBeRendered() {
        assertEquals(
            "408\u00A0jt\u00A0audiens bulanan",
            artistCountStat("408\u00A0jt\u00A0audiens bulanan", "Pendengar bulanan", "id", "jv", rawTextIncludesLabel = true),
        )
        assertEquals(
            "63,5\u00A0jt Subscriber",
            artistCountStat("63,5\u00A0jt", "Subscriber", "id", "jv", rawTextIncludesLabel = false),
        )
        assertEquals(
            "4 080万 monthly",
            artistCountStat("4 080万 monthly", "Pendengar bulanan", "xx", "id", rawTextIncludesLabel = true),
        )
    }

    @Test
    fun reRendersParsableCountsWithLabelOnce() {
        assertEquals(
            "408\u00A0jt Pendengar bulanan",
            artistCountStat("408M monthly audience", "Pendengar bulanan", "en", "id", rawTextIncludesLabel = true),
        )
        assertEquals(
            "63,5\u00A0jt Subscriber",
            artistCountStat("63.5M", "Subscriber", "en", "id", rawTextIncludesLabel = false),
        )
        assertNull(artistCountStat(null, "Subscriber", "en", "id", rawTextIncludesLabel = false))
        assertNull(artistCountStat("   ", "Subscriber", "en", "id", rawTextIncludesLabel = false))
    }
}
