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

/**
 * Fixtures are verbatim values captured from WEB_REMIX `browse` responses, with NBSP kept as
 * `\u00A0` because YouTube separates the number from the unit with it in most languages.
 */
class CompactCountParserTest {
    private data class Case(
        val language: String,
        val raw: String,
        val expected: Long,
    )

    private fun assertCases(cases: List<Case>) {
        for (case in cases) {
            assertEquals(
                "${case.language} / ${case.raw.replace('\u00A0', '_')}",
                case.expected,
                parseCompactCount(case.raw, case.language),
            )
        }
    }

    @Test
    fun parsesLatinLanguages() {
        assertCases(
            listOf(
                Case("en", "408M monthly audience", 408_000_000L),
                Case("en", "65.3K monthly audience", 65_300L),
                Case("en", "63.5M", 63_500_000L),
                Case("en-GB", "408m monthly audience", 408_000_000L),
                Case("en-GB", "65.3k monthly audience", 65_300L),
                Case("fr", "408\u00A0M auditeurs/mois", 408_000_000L),
                Case("fr", "65,3 k auditeurs/mois", 65_300L),
                Case("es", "408\u00A0M usuarios mensuales", 408_000_000L),
                Case("es", "65,3 K usuarios mensuales", 65_300L),
                Case("it", "408 Mln ascoltatori mensili", 408_000_000L),
                Case("it", "65,3K ascoltatori mensili", 65_300L),
                Case("nl", "Maandelijks publiek van 408 mln.", 408_000_000L),
                Case("nl", "Maandelijks publiek van 65,3K", 65_300L),
                Case("pl", "408 mln odbiorców miesięcznie", 408_000_000L),
                Case("pl", "65,3 tys. odbiorców miesięcznie", 65_300L),
                Case("tr", "Aylık kitle: 408 Mn", 408_000_000L),
                Case("tr", "Aylık kitle: 65,3 B", 65_300L),
                Case("cs", "408 mil. posluchačů měsíčně", 408_000_000L),
                Case("cs", "65,3 tis. posluchačů měsíčně", 65_300L),
                Case("sk", "408 mil. divákov za uplynulý mesiac", 408_000_000L),
                Case("sk", "65,3 tis. divákov za uplynulý mesiac", 65_300L),
                Case("hr", "Mjesečna publika: 408 mil.", 408_000_000L),
                Case("hr", "Mjesečna publika: 65,3 tis.", 65_300L),
                Case("ro", "408 mil. de ascultători lunari", 408_000_000L),
                Case("ro", "65,3 K de ascultători lunari", 65_300L),
                Case("hu", "408 M havi közönség", 408_000_000L),
                Case("hu", "65,3 E havi közönség", 65_300L),
                Case("fi", "408 milj. katsojaa kuukaudessa", 408_000_000L),
                Case("fi", "65,3 t. katsojaa kuukaudessa", 65_300L),
                Case("et", "408 mln kuulajat kuus", 408_000_000L),
                Case("et", "65,3 tuh kuulajat kuus", 65_300L),
                Case("lt", "Mėn. auditorija: 408 mln.", 408_000_000L),
                Case("lt", "Mėn. auditorija: 65,3 tūkst.", 65_300L),
                Case("lv", "408 milj. skatītāji mēnesī", 408_000_000L),
                Case("lv", "65,3 tūkst. skatītāji mēnesī", 65_300L),
                Case("no", "408 mill. månedlig målgruppe", 408_000_000L),
                Case("no", "65,3k månedlig målgruppe", 65_300L),
                Case("id", "408\u00A0jt\u00A0audiens bulanan", 408_000_000L),
                Case("id", "65,3\u00A0rb\u00A0audiens bulanan", 65_300L),
                Case("id", "63,5\u00A0jt", 63_500_000L),
                Case("ms", "408J khalayak bulanan", 408_000_000L),
                Case("ms", "65.3K khalayak bulanan", 65_300L),
                Case("he", "קהל של 408M צופים בחודש", 408_000_000L),
                Case("he", "קהל של 65.3K צופים בחודש", 65_300L),
            ),
        )
    }

    @Test
    fun parsesPlainGroupedNumbers() {
        assertCases(
            listOf(
                Case("de", "408 Mio. Zuschauer/Monat", 408_000_000L),
                Case("de", "65.300 Zuschauer/Monat", 65_300L),
                Case("de", "63,5 Mio.", 63_500_000L),
                Case("da", "408 mio. månedlige publikummer", 408_000_000L),
                Case("da", "65.300 månedlige publikummer", 65_300L),
                Case("sv", "408 mn lyssnare per månad", 408_000_000L),
                Case("sv", "65\u00A0300 lyssnare per månad", 65_300L),
                Case("sv", "63,5 mn", 63_500_000L),
            ),
        )
    }

    @Test
    fun parsesCyrillicAndGreekLanguages() {
        assertCases(
            listOf(
                Case("ru", "408\u00A0млн слушателей в месяц", 408_000_000L),
                Case("ru", "65,3\u00A0тыс. слушателей в месяц", 65_300L),
                Case("ru", "63,5\u00A0млн", 63_500_000L),
                Case("uk", "408 млн слухачів на місяць", 408_000_000L),
                Case("uk", "65,3 тис. слухачів на місяць", 65_300L),
                Case("bg", "408 млн. слушатели месечно", 408_000_000L),
                Case("bg", "65,3 хил. слушатели месечно", 65_300L),
                Case("sr", "408 мил. гледалаца месечно", 408_000_000L),
                Case("sr", "65,3 хиљ. гледалаца месечно", 65_300L),
                Case("el", "Κοινό 408 εκ. ατόμων τον μήνα", 408_000_000L),
                Case("el", "Κοινό 65,3 χιλ. ατόμων τον μήνα", 65_300L),
            ),
        )
    }

    @Test
    fun parsesArabicHindiAndThai() {
        assertCases(
            listOf(
                Case("ar", "408 مليون مشاهد شهريًا", 408_000_000L),
                Case("ar", "65.3 ألف مشاهد شهريًا", 65_300L),
                Case("ar-EG", "456 ألف مشاهد شهريًا", 456_000L),
                Case("ar", "63.5 مليون", 63_500_000L),
                Case("hi", "हर महीने 40.8\u00A0क॰ दर्शक", 408_000_000L),
                Case("hi", "हर महीने 65.3\u00A0हज़ार दर्शक", 65_300L),
                Case("hi", "हर महीने 4.56\u00A0लाख दर्शक", 456_000L),
                Case("hi", "6.35\u00A0क॰", 63_500_000L),
                Case("th", "มีผู้ชมรายเดือน 408 ล้าน คน", 408_000_000L),
                Case("th", "มีผู้ชมรายเดือน 6.53 หมื่น คน", 65_300L),
                Case("th", "มีผู้ชมรายเดือน 4.56 แสน คน", 456_000L),
            ),
        )
    }

    @Test
    fun parsesJapaneseKoreanChineseAndVietnamese() {
        assertCases(
            listOf(
                Case("ja", "月間視聴者数: 4.08億 人", 408_000_000L),
                Case("ja", "月間視聴者数: 6.53万 人", 65_300L),
                Case("ja", "6350万", 63_500_000L),
                Case("ko", "월간 시청자 4.08억명", 408_000_000L),
                Case("ko", "월간 시청자 6.53만명", 65_300L),
                Case("ko", "6350만", 63_500_000L),
                Case("zh-CN", "每月听众:4.08亿", 408_000_000L),
                Case("zh-CN", "每月听众:6.53万", 65_300L),
                Case("zh-TW", "4.08億位每月觀眾", 408_000_000L),
                Case("zh-TW", "6.53萬位每月觀眾", 65_300L),
                Case("zh", "6350万", 63_500_000L),
                Case("vi", "408 Tr khán thính giả mỗi tháng", 408_000_000L),
                Case("vi", "65,3 N khán thính giả mỗi tháng", 65_300L),
                Case("vi", "63,5 Tr", 63_500_000L),
            ),
        )
    }

    @Test
    fun parsesCountsBelowOneThousand() {
        assertCases(
            listOf(
                Case("en", "850 monthly audience", 850L),
                Case("id", "850 audiens bulanan", 850L),
                Case("en", "18", 18L),
            ),
        )
    }

    @Test
    fun refusesToGuess() {
        // Language missing from the table.
        assertNull(parseCompactCount("408M monthly audience", "xx"))
        assertNull(parseCompactCount("408M monthly audience", null))
        // Unit that was never sampled: 10^9 has no measured token in any language.
        assertNull(parseCompactCount("1,2 млрд слушателей в месяц", "ru"))
        assertNull(parseCompactCount("1.2B monthly audience", "en"))
        // Fractional number with an unknown trailing token: the token is not a label either.
        assertNull(parseCompactCount("40.8 कXYZ दर्शक", "hi"))
        // No digits at all.
        assertNull(parseCompactCount("No subscribers", "en"))
        assertNull(parseCompactCount("", "en"))
        assertNull(parseCompactCount(null, "en"))
    }
}
