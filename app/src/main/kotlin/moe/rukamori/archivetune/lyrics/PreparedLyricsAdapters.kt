/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

fun PreparedLyrics.toLyricsEntries(): List<LyricsEntry> =
    lines.map { line ->
        val mainWords = line.main.words.map { word -> word.toWordTimestamp() }
        val backgroundWords =
            line.backgrounds.flatMap { track ->
                track.words.map { word -> word.toWordTimestamp(isBackground = true) }
            }
        val phoneticByWordIndex = line.phonetics.associate { phonetic -> phonetic.wordIndex to phonetic.text }
        val providerWords =
            if (
                line.main.words.isNotEmpty() &&
                line.main.words.indices.all(phoneticByWordIndex::containsKey)
            ) {
                line.main.words.indices.map { wordIndex -> phoneticByWordIndex.getValue(wordIndex) }
            } else {
                null
            }

        LyricsEntry(
            time = line.startMs,
            text = line.text,
            words = (mainWords + backgroundWords).takeIf { words -> words.isNotEmpty() },
            agent =
                when (line.alignment) {
                    LyricsLineAlignment.START -> "v1"
                    LyricsLineAlignment.END -> "v2"
                    LyricsLineAlignment.CENTER -> "group"
                },
            isInstrumental = line.isInstrumental,
            durationMs = (line.endMs - line.startMs).coerceAtLeast(0L),
            providerRomanizedText = line.romanizedText,
            providerRomanizedWords = providerWords,
            providerTranslationText = line.translation,
            isRtl = line.direction == LyricsTextDirection.RTL,
            romanizedTextFlow = MutableStateFlow(line.romanizedText),
        )
    }

private fun PreparedLyricsWord.toWordTimestamp(isBackground: Boolean = false): WordTimestamp =
    WordTimestamp(
        text = text,
        startTime = startMs / MILLIS_PER_SECOND,
        endTime = endMs / MILLIS_PER_SECOND,
        isBackground = isBackground,
    )

private const val MILLIS_PER_SECOND = 1000.0
