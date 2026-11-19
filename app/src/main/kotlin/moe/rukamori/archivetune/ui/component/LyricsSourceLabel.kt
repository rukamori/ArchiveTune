/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.style.TextDecoration

private val LyricsSourceLinkStyles =
    TextLinkStyles(style = SpanStyle(textDecoration = TextDecoration.Underline))

fun buildLyricsSourceLabel(
    text: String,
    providerName: String,
    providerUrl: String?,
    author: String?,
    authorUrl: String?,
): AnnotatedString {
    val providerStart = text.lastIndexOf(providerName)
    if (providerStart < 0 || (providerUrl == null && authorUrl == null)) return AnnotatedString(text)
    val providerEnd = providerStart + providerName.length
    val linkedAuthor = author?.takeIf { it.isNotEmpty() && providerName.endsWith(it) }
    val authorStart = linkedAuthor?.let { providerEnd - it.length }
    val baseNameEnd =
        if (authorStart == null) {
            providerEnd
        } else {
            providerStart + providerName.substring(0, authorStart - providerStart).trimEnd(' ', '-').length
        }

    return AnnotatedString.Builder(text).apply {
        if (providerUrl != null && baseNameEnd > providerStart) {
            addLink(LinkAnnotation.Url(providerUrl, LyricsSourceLinkStyles), providerStart, baseNameEnd)
        }
        if (authorUrl != null && authorStart != null) {
            addLink(LinkAnnotation.Url(authorUrl, LyricsSourceLinkStyles), authorStart, providerEnd)
        }
    }.toAnnotatedString()
}

fun AnnotatedString.withoutLinks(): AnnotatedString =
    if (hasLinkAnnotations(0, length)) AnnotatedString(text) else this
