/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

import moe.rukamori.archivetune.lyrics.LyricsUtils
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.io.StringWriter
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

data class AiLyricsSegment(
    val id: Int,
    val text: String,
)

sealed interface AiLyricsDocument {
    val formatName: String
    val segments: List<AiLyricsSegment>

    fun rebuild(translations: Map<Int, String>): String
}

object AiLyricsDocumentParser {
    fun parse(rawLyrics: String): AiLyricsDocument {
        val cleanLyrics = LyricsUtils.normalizeLyricsText(rawLyrics)
        return if (LyricsUtils.isTtml(cleanLyrics)) {
            parseTtml(cleanLyrics).getOrElse {
                parseTtmlFallback(cleanLyrics).getOrElse {
                    parseTtmlTextFallback(cleanLyrics)
                }
            }
        } else {
            parseLineBased(
                rawLyrics = cleanLyrics,
                formatName = if (cleanLyrics.lineSequence().any { SyncedLineRegex.containsMatchIn(it) }) "synced LRC" else "plain text",
            )
        }
    }

    private fun parseTtmlTextFallback(rawLyrics: String): AiLyricsDocument {
        val plainLyrics = LyricsUtils.displayLyricsText(rawLyrics)
        return parseLineBased(
            rawLyrics = plainLyrics.ifBlank { rawLyrics },
            formatName = "plain text",
        )
    }

    private fun parseLineBased(
        rawLyrics: String,
        formatName: String,
    ): AiLyricsDocument {
        val lines = rawLyrics.split('\n')
        val templates = ArrayList<LineTemplate>(lines.size)
        val segments = ArrayList<AiLyricsSegment>()
        val seenSyncedLineKeys = HashSet<String>()
        lines.forEach { line ->
            val syncedMatch = SyncedLineRegex.matchEntire(line)
            if (syncedMatch != null) {
                val syncedLineKey = syncedMatch.groupValues[1].filterNot { it.isWhitespace() }
                if (!seenSyncedLineKeys.add(syncedLineKey)) return@forEach

                val content = syncedMatch.groupValues[3]
                val segmentId =
                    if (content.isBlank()) {
                        null
                    } else {
                        segments.size.also { segments.add(AiLyricsSegment(it, content.trim())) }
                    }
                templates.add(
                    LineTemplate(
                        prefix = syncedMatch.groupValues[1],
                        separator = syncedMatch.groupValues[2],
                        suffix = syncedMatch.groupValues[4],
                        content = content.trim(),
                        segmentId = segmentId,
                        original = line,
                    ),
                )
            } else {
                val plainMatch = PlainLineRegex.matchEntire(line)
                val content = plainMatch?.groupValues?.get(2).orEmpty()
                val segmentId =
                    if (content.isBlank()) {
                        null
                    } else {
                        segments.size.also { segments.add(AiLyricsSegment(it, content.trim())) }
                    }
                templates.add(
                    LineTemplate(
                        prefix = plainMatch?.groupValues?.get(1).orEmpty(),
                        separator = "",
                        suffix = plainMatch?.groupValues?.get(3).orEmpty(),
                        content = content.trim(),
                        segmentId = segmentId,
                        original = line,
                    ),
                )
            }
        }
        return LineBasedLyricsDocument(formatName = formatName, templates = templates, segments = segments)
    }

    private fun sanitizeXml(rawLyrics: String): String {
        var xml = rawLyrics
        xml = xml.replace(DoctypeRegex, "")
        xml = xml.replace(BareAmpersandRegex, "&amp;")
        return xml
    }

    private fun extractCleanParagraphText(element: Element): String {
        val sb = StringBuilder()
        fun collectText(node: Node) {
            if (node.nodeType == Node.TEXT_NODE) {
                sb.append(node.nodeValue)
            } else if (node.nodeType == Node.ELEMENT_NODE) {
                val elem = node as Element
                val localName = elem.localName ?: elem.tagName.substringAfter(':')
                if (!localName.equals("translation", ignoreCase = true) && !localName.equals("metadata", ignoreCase = true)) {
                    val children = elem.childNodes
                    for (i in 0 until children.length) {
                        children.item(i)?.let { collectText(it) }
                    }
                }
            }
        }
        val children = element.childNodes
        for (i in 0 until children.length) {
            children.item(i)?.let { collectText(it) }
        }
        return sb.toString().replace(WhitespaceRegex, " ").trim()
    }

    private fun parseTtml(rawLyrics: String): Result<AiLyricsDocument> =
        runCatching {
            val sanitized = sanitizeXml(rawLyrics)
            val factory =
                DocumentBuilderFactory.newInstance().apply {
                    isNamespaceAware = true
                    runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
                    runCatching { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
                    runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
                }
            val document = factory.newDocumentBuilder().parse(InputSource(StringReader(sanitized)))
            val paragraphs = ArrayList<TtmlParagraph>()
            val segments = ArrayList<AiLyricsSegment>()
            val elements = document.getElementsByTagName("*")
            for (index in 0 until elements.length) {
                val element = elements.item(index) as? Element ?: continue
                val localName = element.localName ?: element.tagName.substringAfter(':')
                if (!localName.equals("p", ignoreCase = true)) continue
                val text = extractCleanParagraphText(element)
                if (text.isBlank()) continue
                val segmentId = segments.size
                val lineKey =
                    readAttributeBySuffix(element, "key")
                        ?: "archivetune-translation-$segmentId".also { key -> element.setAttribute("key", key) }
                segments.add(AiLyricsSegment(segmentId, text))
                paragraphs.add(TtmlParagraph(element = element, segmentId = segmentId, key = lineKey))
            }
            require(segments.isNotEmpty()) { "TTML has no translatable text" }
            TtmlLyricsDocument(
                document = document,
                originalHadDeclaration = rawLyrics.trimStart().startsWith("<?xml", ignoreCase = true),
                paragraphs = paragraphs,
                segments = segments,
            )
        }

    private fun parseTtmlFallback(rawLyrics: String): Result<AiLyricsDocument> =
        runCatching {
            val paragraphs = ArrayList<RegexTtmlParagraph>()
            val segments = ArrayList<AiLyricsSegment>()
            val matches = TtmlParagraphRegex.findAll(rawLyrics).toList()
            require(matches.isNotEmpty()) { "No TTML paragraphs found" }

            matches.forEach { match ->
                val attrString = match.groupValues[1]
                val content = match.groupValues[2]
                val cleanText =
                    content
                        .replace(XmlTagRegex, "")
                        .replace("&amp;", "&")
                        .replace("&lt;", "<")
                        .replace("&gt;", ">")
                        .replace("&quot;", "\"")
                        .replace("&apos;", "'")
                        .replace(WhitespaceRegex, " ")
                        .trim()
                if (cleanText.isNotBlank()) {
                    val segmentId = segments.size
                    val key =
                        KeyAttributeRegex.find(attrString)?.groupValues?.get(1)
                            ?: "archivetune-translation-$segmentId"
                    segments.add(AiLyricsSegment(segmentId, cleanText))
                    paragraphs.add(RegexTtmlParagraph(segmentId = segmentId, key = key))
                }
            }
            require(segments.isNotEmpty()) { "TTML fallback has no translatable text" }
            RegexTtmlLyricsDocument(rawLyrics = rawLyrics, paragraphs = paragraphs, segments = segments)
        }

    private fun readAttributeBySuffix(
        element: Element,
        suffix: String,
    ): String? {
        val directValue = element.getAttribute(suffix).takeIf { it.isNotBlank() }
        if (directValue != null) return directValue

        val attrs = element.attributes ?: return null
        for (i in 0 until attrs.length) {
            val node = attrs.item(i) ?: continue
            val name = node.nodeName ?: continue
            if (name.equals(suffix, ignoreCase = true) || name.endsWith(":$suffix", ignoreCase = true)) {
                val value = node.nodeValue?.trim()
                if (!value.isNullOrEmpty()) return value
            }
        }
        return null
    }

    private val SyncedLineRegex = Regex("""^(\s*(?:\[[^\]]+])+)(\s*)(.*?)(\s*)$""")
    private val PlainLineRegex = Regex("""^(\s*)(.*?)(\s*)$""")
    private val DoctypeRegex = Regex("""<!DOCTYPE[^>]*>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val BareAmpersandRegex = Regex("""&(?!(amp|lt|gt|quot|apos|#\d+|#x[0-9a-fA-F]+);)""")
    private val WhitespaceRegex = Regex("""\s+""")
    private val TtmlParagraphRegex = Regex("""<(?:\w+:)?p\b([^>]*)>(.*?)</(?:\w+:)?p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val XmlTagRegex = Regex("""<[^>]+>""")
    private val KeyAttributeRegex = Regex("""\b(?:\w+:)?key\s*=\s*["']([^"']+)["']""", RegexOption.IGNORE_CASE)
}

private data class LineTemplate(
    val prefix: String,
    val separator: String,
    val suffix: String,
    val content: String,
    val segmentId: Int?,
    val original: String,
)

private data class LineBasedLyricsDocument(
    override val formatName: String,
    private val templates: List<LineTemplate>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override fun rebuild(translations: Map<Int, String>): String =
        templates.joinToString("\n") { template ->
            val id = template.segmentId ?: return@joinToString template.original
            val translated = translations[id]?.trim().orEmpty()
            if (translated.isBlank() || translated.equals(template.content, ignoreCase = true)) {
                template.original
            } else {
                val translatedLine = "${template.prefix}${template.separator}$translated${template.suffix}"
                "${template.original}\n$translatedLine"
            }
        }
}

private data class TtmlParagraph(
    val element: Element,
    val segmentId: Int,
    val key: String,
)

private data class TtmlLyricsDocument(
    private val document: Document,
    private val originalHadDeclaration: Boolean,
    private val paragraphs: List<TtmlParagraph>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override val formatName: String = "TTML"

    override fun rebuild(translations: Map<Int, String>): String {
        removeArchiveTuneTranslationElements(document.documentElement)

        val translatedParagraphs =
            paragraphs.mapNotNull { paragraph ->
                val translated =
                    translations[paragraph.segmentId]
                        ?.trim()
                        ?.takeIf {
                            it.isNotBlank() &&
                                !it.equals(
                                    paragraph.element.textContent
                                        ?.trim()
                                        .orEmpty(),
                                    ignoreCase = true,
                                )
                        }
                        ?: return@mapNotNull null
                paragraph to translated
            }

        if (translatedParagraphs.isNotEmpty()) {
            val metadata = findOrCreateMetadataElement(document, document.documentElement)
            val translationElement =
                document.createElement("translation").apply {
                    setAttribute("data-archivetune", "translation")
                }

            translatedParagraphs.forEach { (paragraph, translated) ->
                val textElement =
                    document.createElement("text").apply {
                        setAttribute("for", paragraph.key)
                        appendChild(document.createTextNode(translated))
                    }
                translationElement.appendChild(textElement)
            }

            metadata.appendChild(translationElement)
        }
        return transform(document, originalHadDeclaration)
    }

    private fun removeArchiveTuneTranslationElements(root: Element) {
        val elements = root.getElementsByTagName("*")
        val removableElements = ArrayList<Element>()
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            val localName = element.localName ?: element.tagName.substringAfter(':')
            if (localName.equals("translation", ignoreCase = true) &&
                element.getAttribute("data-archivetune") == "translation"
            ) {
                removableElements.add(element)
            }
        }
        removableElements.forEach { element ->
            element.parentNode?.removeChild(element)
        }
    }

    private fun findOrCreateMetadataElement(
        document: Document,
        root: Element,
    ): Element {
        val elements = root.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            val localName = element.localName ?: element.tagName.substringAfter(':')
            if (localName.equals("metadata", ignoreCase = true)) return element
        }

        val metadata = document.createElement("metadata")
        val head = findOrCreateHeadElement(document, root)
        head.appendChild(metadata)
        return metadata
    }

    private fun findOrCreateHeadElement(
        document: Document,
        root: Element,
    ): Element {
        val elements = root.getElementsByTagName("*")
        for (index in 0 until elements.length) {
            val element = elements.item(index) as? Element ?: continue
            val localName = element.localName ?: element.tagName.substringAfter(':')
            if (localName.equals("head", ignoreCase = true)) return element
        }

        val head = document.createElement("head")
        root.insertBefore(head, root.firstChild)
        return head
    }

    private fun transform(
        document: Document,
        includeDeclaration: Boolean,
    ): String {
        val transformer =
            TransformerFactory.newInstance().newTransformer().apply {
                setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, if (includeDeclaration) "no" else "yes")
                setOutputProperty(OutputKeys.ENCODING, "UTF-8")
            }
        val writer = StringWriter()
        transformer.transform(DOMSource(document), StreamResult(writer))
        return writer.toString()
    }
}

private data class RegexTtmlParagraph(
    val segmentId: Int,
    val key: String,
)

private data class RegexTtmlLyricsDocument(
    private val rawLyrics: String,
    private val paragraphs: List<RegexTtmlParagraph>,
    override val segments: List<AiLyricsSegment>,
) : AiLyricsDocument {
    override val formatName: String = "TTML"

    override fun rebuild(translations: Map<Int, String>): String {
        val translationElements = buildString {
            append("""<translation data-archivetune="translation">""")
            paragraphs.forEach { p ->
                val text = translations[p.segmentId]?.trim().orEmpty()
                if (text.isNotBlank()) {
                    val escapedText =
                        text
                            .replace("&", "&amp;")
                            .replace("<", "&lt;")
                            .replace(">", "&gt;")
                            .replace("\"", "&quot;")
                            .replace("'", "&apos;")
                    append("""<text for="${p.key}">$escapedText</text>""")
                }
            }
            append("</translation>")
        }
        val cleaned = rawLyrics.replace(ExistingTranslationBlockRegex, "")
        return if (cleaned.contains("</metadata>", ignoreCase = true)) {
            cleaned.replaceFirst(Regex("""</metadata>""", RegexOption.IGNORE_CASE), "$translationElements</metadata>")
        } else if (cleaned.contains("</head>", ignoreCase = true)) {
            cleaned.replaceFirst(Regex("""</head>""", RegexOption.IGNORE_CASE), "<metadata>$translationElements</metadata></head>")
        } else if (cleaned.contains("<body>", ignoreCase = true)) {
            cleaned.replaceFirst(Regex("""<body\b[^>]*>""", RegexOption.IGNORE_CASE), "<head><metadata>$translationElements</metadata></head>$0")
        } else {
            cleaned
        }
    }

    private companion object {
        val ExistingTranslationBlockRegex =
            Regex(
                """<translation\b[^>]*data-archivetune="translation"[^>]*>.*?</translation>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            )
    }
}
