/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiLyricsDocumentTest {
    @Test
    fun parse_withStandardTtml_extractsParagraphSegments() {
        val ttml = """
            <?xml version="1.0" encoding="utf-8"?>
            <tt xmlns="http://www.w3.org/ns/ttml">
                <head>
                    <metadata>
                        <title>Sample</title>
                    </metadata>
                </head>
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">First line of lyrics</p>
                        <p begin="00:03.500" end="00:06.000">Second line of lyrics</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(ttml)
        assertEquals("TTML", doc.formatName)
        assertEquals(2, doc.segments.size)
        assertEquals("First line of lyrics", doc.segments[0].text)
        assertEquals("Second line of lyrics", doc.segments[1].text)
    }

    @Test
    fun parse_withDoctypeInTtml_doesNotThrowAndExtractsSegments() {
        val ttmlWithDoctype = """
            <?xml version="1.0" encoding="utf-8"?>
            <!DOCTYPE tt PUBLIC "-//W3C//DTD TTML 1.0//EN" "http://www.w3.org/TR/2006/REC-ttaf1-dfxp-20061018/dtd/ttaf1-dfxp.dtd">
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">Apple Music TTML line</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(ttmlWithDoctype)
        assertEquals("TTML", doc.formatName)
        assertEquals(1, doc.segments.size)
        assertEquals("Apple Music TTML line", doc.segments[0].text)
    }

    @Test
    fun parse_withUnescapedAmpersandInTtml_sanitizesAndExtractsSegments() {
        val ttmlWithAmpersand = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">Rock & Roll forever</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(ttmlWithAmpersand)
        assertEquals("TTML", doc.formatName)
        assertEquals(1, doc.segments.size)
        assertEquals("Rock & Roll forever", doc.segments[0].text)
    }

    @Test
    fun parse_withWordLevelSpansInTtml_extractsCleanLineText() {
        val ttmlWithSpans = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">
                            <span begin="00:01.000" end="00:01.500">Never </span>
                            <span begin="00:01.500" end="00:02.000">gonna </span>
                            <span begin="00:02.000" end="00:03.000">give you up</span>
                        </p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(ttmlWithSpans)
        assertEquals("TTML", doc.formatName)
        assertEquals(1, doc.segments.size)
        assertEquals("Never gonna give you up", doc.segments[0].text)
    }

    @Test
    fun parse_withTagsEndingInP_doesNotTreatThemAsParagraphs() {
        val ttmlWithClip = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <head>
                    <metadata>
                        <clip>Metadata clip info</clip>
                        <timestamp>12345</timestamp>
                    </metadata>
                </head>
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">Real lyric line</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(ttmlWithClip)
        assertEquals("TTML", doc.formatName)
        assertEquals(1, doc.segments.size)
        assertEquals("Real lyric line", doc.segments[0].text)
    }

    @Test
    fun rebuild_ttml_embedsTranslationsWithCorrectKeys() {
        val ttml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p key="line-1" begin="00:01.000" end="00:03.000">Hello world</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(ttml)
        val rebuilt = doc.rebuild(mapOf(0 to "Hola mundo"))

        assertTrue(rebuilt.contains("""data-archivetune="translation""""))
        assertTrue(rebuilt.contains("""for="line-1""""))
        assertTrue(rebuilt.contains("Hola mundo"))
    }

    @Test
    fun parse_withMarkdownCodeFenceTtml_stripsFencesAndParses() {
        val fenced = """
            ```xml
            <tt xmlns="http://www.w3.org/ns/ttml">
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">Fenced line</p>
                    </div>
                </body>
            </tt>
            ```
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(fenced)
        assertEquals("TTML", doc.formatName)
        assertEquals(1, doc.segments.size)
        assertEquals("Fenced line", doc.segments[0].text)
    }

    @Test
    fun fallback_withMalformedXmlTtml_extractsCleanParagraphs() {
        val malformedTtml = """
            <tt xmlns="http://www.w3.org/ns/ttml">
                <head><unclosed_tag>
                <body>
                    <div>
                        <p begin="00:01.000" end="00:03.000">Fallback paragraph</p>
                    </div>
                </body>
            </tt>
        """.trimIndent()

        val doc = AiLyricsDocumentParser.parse(malformedTtml)
        assertEquals(1, doc.segments.size)
        assertEquals("Fallback paragraph", doc.segments[0].text)
        assertFalse(doc.segments.any { it.text.contains("<tt") || it.text.contains("<body") })
    }
}
