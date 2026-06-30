/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ui.component

import java.text.BreakIterator
import java.util.Locale

internal fun String.toLyricsWrappingUnits(): List<String> {
    if (isEmpty()) return emptyList()

    val units = mutableListOf<String>()
    val currentWord = StringBuilder()
    val characterIterator = BreakIterator.getCharacterInstance(Locale.ROOT)
    characterIterator.setText(this)

    fun flushCurrentWord() {
        if (currentWord.isNotEmpty()) {
            units += currentWord.toString()
            currentWord.clear()
        }
    }

    var start = characterIterator.first()
    var end = characterIterator.next()
    while (end != BreakIterator.DONE) {
        val grapheme = substring(start, end)
        val codePoint = grapheme.codePointAt(0)
        when {
            grapheme.all(Char::isWhitespace) -> {
                currentWord.append(grapheme)
                flushCurrentWord()
            }

            codePoint.isCjkCodePoint() -> {
                flushCurrentWord()
                units += grapheme
            }

            else -> {
                currentWord.append(grapheme)
            }
        }
        start = end
        end = characterIterator.next()
    }
    flushCurrentWord()

    return units
}

private fun Int.isCjkCodePoint(): Boolean =
    when (Character.UnicodeScript.of(this)) {
        Character.UnicodeScript.HAN,
        Character.UnicodeScript.HANGUL,
        Character.UnicodeScript.HIRAGANA,
        Character.UnicodeScript.KATAKANA,
        -> true

        else -> false
    }
