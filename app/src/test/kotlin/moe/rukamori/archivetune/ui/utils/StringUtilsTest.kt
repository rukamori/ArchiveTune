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

class StringUtilsTest {

    @Test
    fun parseCompactNumberAndArtistCountInEnglish() {
        assertEquals(40_800_000L, parseCompactNumber("40.8M monthly listeners"))
        assertEquals("40.8M", parseArtistCompactCount("40.8M monthly listeners"))

        assertEquals(1_200_000L, parseCompactNumber("1.2M subscribers"))
        assertEquals("1.2M", parseArtistCompactCount("1.2M subscribers"))

        assertEquals(500_000L, parseCompactNumber("500K subscribers"))
        assertEquals("500K", parseArtistCompactCount("500K subscribers"))

        assertEquals(1_500_000_000L, parseCompactNumber("1.5B views"))
        assertEquals("1.5B", parseArtistCompactCount("1.5B views"))

        assertEquals(850L, parseCompactNumber("850 monthly listeners"))
        assertEquals("850", parseArtistCompactCount("850 monthly listeners"))

        assertEquals(1_250L, parseCompactNumber("1,250 subscribers"))
        assertEquals("1.2K", parseArtistCompactCount("1,250 subscribers"))
    }

    @Test
    fun parseCompactNumberAndArtistCountInIndonesian() {
        assertEquals(40_800_000L, parseCompactNumber("40,8 jt pendengar bulanan"))
        assertEquals("40.8M", parseArtistCompactCount("40,8 jt pendengar bulanan"))

        assertEquals(1_200_000L, parseCompactNumber("1,2 jt subscriber"))
        assertEquals("1.2M", parseArtistCompactCount("1,2 jt subscriber"))

        assertEquals(100_000L, parseCompactNumber("100 rb subscriber"))
        assertEquals("100K", parseArtistCompactCount("100 rb subscriber"))

        assertEquals(1_500_000_000L, parseCompactNumber("1,5 mly tayangan"))
        assertEquals("1.5B", parseArtistCompactCount("1,5 mly tayangan"))

        assertEquals(850L, parseCompactNumber("850 pendengar bulanan"))
        assertEquals("850", parseArtistCompactCount("850 pendengar bulanan"))

        assertEquals(1_250L, parseCompactNumber("1.250 subscriber"))
        assertEquals("1.2K", parseArtistCompactCount("1.250 subscriber"))
    }

    @Test
    fun parseCompactNumberAndArtistCountInGerman() {
        assertEquals(40_800_000L, parseCompactNumber("40,8 Mio. monatliche Hörer"))
        assertEquals("40.8M", parseArtistCompactCount("40,8 Mio. monatliche Hörer"))

        assertEquals(500_000L, parseCompactNumber("500 Tsd. Abonnenten"))
        assertEquals("500K", parseArtistCompactCount("500 Tsd. Abonnenten"))
    }

    @Test
    fun parseCompactNumberAndArtistCountInRussian() {
        assertEquals(40_800_000L, parseCompactNumber("40,8 млн слушателей за месяц"))
        assertEquals("40.8M", parseArtistCompactCount("40,8 млн слушателей за месяц"))

        assertEquals(500_000L, parseCompactNumber("500 тыс. подписчиков"))
        assertEquals("500K", parseArtistCompactCount("500 тыс. подписчиков"))
    }

    @Test
    fun parseCompactNumberAndArtistCountInCJK() {
        // Japanese
        assertEquals(40_800_000L, parseCompactNumber("月間リスナー 4080万人"))
        assertEquals("40.8M", parseArtistCompactCount("月間リスナー 4080万人"))

        assertEquals(1_200_000L, parseCompactNumber("チャンネル登録者数 120万人"))
        assertEquals("1.2M", parseArtistCompactCount("チャンネル登録者数 120万人"))

        // Korean
        assertEquals(40_800_000L, parseCompactNumber("월별 청취자 4080만명"))
        assertEquals("40.8M", parseArtistCompactCount("월별 청취자 4080만명"))

        // Chinese
        assertEquals(40_800_000L, parseCompactNumber("4080万位月听众"))
        assertEquals("40.8M", parseArtistCompactCount("4080万位月听众"))

        assertEquals(120_000_000L, parseCompactNumber("1.2亿次播放"))
        assertEquals("120M", parseArtistCompactCount("1.2亿次播放"))
    }

    @Test
    fun parseCompactNumberAndArtistCountInHindi() {
        assertEquals(40_800_000L, parseCompactNumber("4.08 करोड़ मासिक श्रोता"))
        assertEquals("40.8M", parseArtistCompactCount("4.08 करोड़ मासिक श्रोता"))

        assertEquals(120_000L, parseCompactNumber("1.2 लाख सदस्य"))
        assertEquals("120K", parseArtistCompactCount("1.2 लाख सदस्य"))
    }

    @Test
    fun parseCompactNumberAndArtistCountInVietnamese() {
        assertEquals(40_800_000L, parseCompactNumber("40,8 Tr người nghe hàng tháng"))
        assertEquals("40.8M", parseArtistCompactCount("40,8 Tr người nghe hàng tháng"))

        assertEquals(1_200_000L, parseCompactNumber("1,2 Tr người đăng ký"))
        assertEquals("1.2M", parseArtistCompactCount("1,2 Tr người đăng ký"))
    }

    @Test
    fun handlesWhitespaceAndNonBreakingSpaces() {
        assertEquals(40_800_000L, parseCompactNumber("40,8\u00A0jt pendengar"))
        assertEquals("40.8M", parseArtistCompactCount("40,8\u00A0jt pendengar"))

        assertEquals(40_800_000L, parseCompactNumber("40,8\u202Fмлн"))
        assertEquals("40.8M", parseArtistCompactCount("40,8\u202Fмлн"))
    }

    @Test
    fun handlesNullAndBlankOrInvalidInput() {
        assertNull(parseCompactNumber(null))
        assertNull(parseArtistCompactCount(null))

        assertNull(parseCompactNumber(""))
        assertNull(parseArtistCompactCount(""))

        assertNull(parseCompactNumber("   "))
        assertNull(parseArtistCompactCount("   "))

        assertNull(parseCompactNumber("No subscribers"))
        assertNull(parseArtistCompactCount("No subscribers"))
    }
}
