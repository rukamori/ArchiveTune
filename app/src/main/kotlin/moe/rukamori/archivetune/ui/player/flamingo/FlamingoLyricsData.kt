/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 *
 * Lyric data adapter for the Flamingo player port. Converts ArchiveTune's
 * LyricsEntry model (seconds-based word timestamps, agent duet tags, provider
 * translation/romanization) into the List<List<Pair<Float, String>>> shape
 * Flamingo's lyric view consumes (millisecond timings, last pair of each line
 * reserved for the translation slot — see Flamingo's YosLrcFactory).
 */

package moe.rukamori.archivetune.ui.player.flamingo

import androidx.compose.runtime.Stable
import moe.rukamori.archivetune.lyrics.LyricsEntry

@Stable
data class FlamingoLyricsData(
    /** Each line: list of (timeMs, word) pairs; the LAST pair is the translation slot. */
    val lrcEntries: List<List<Pair<Float, String>>>,
    /** End time (ms) of each line, TTML only. */
    val lineEndTimes: List<Float>,
    /** Romanization text per line, TTML only. */
    val lineTransliterations: List<String?>,
    /** Translation text per line, TTML only. */
    val lineSubtitles: List<String?>,
    /** True when lines carry word-level timings. */
    val isTtmlLyrics: Boolean,
    /** Duet alignment flags (right-aligned "other side" lines). */
    val otherSideForLines: List<Boolean>,
)

object FlamingoLyricAdapter {

    fun fromEntries(entries: List<LyricsEntry>): FlamingoLyricsData? {
        if (entries.isEmpty()) return null

        val lrcEntries = mutableListOf<List<Pair<Float, String>>>()
        val lineEndTimes = mutableListOf<Float>()
        val lineTransliterations = mutableListOf<String?>()
        val lineSubtitles = mutableListOf<String?>()
        val otherSideForLines = mutableListOf<Boolean>()

        var anyWordSynced = false

        entries.forEach { entry ->
            val startTimeMs = entry.time.toFloat()
            val words = entry.words
            val hasWords = !words.isNullOrEmpty()
            if (hasWords) anyWordSynced = true

            val otherSide = entry.agent?.lowercase() == "v2"
            otherSideForLines.add(otherSide)

            if (hasWords) {
                // Word-synced line: (timeMs, word) pairs + trailing empty translation slot.
                val line = words.map { word ->
                    Pair((word.startTime * 1000).toFloat(), word.text)
                }.toMutableList()
                line.add(Pair(startTimeMs, ""))
                lrcEntries.add(line)

                lineEndTimes.add((words.last().endTime * 1000).toFloat())
                lineTransliterations.add(entry.providerRomanizedText?.trim()?.takeIf { it.isNotEmpty() })
                lineSubtitles.add(entry.providerTranslationText?.trim()?.takeIf { it.isNotEmpty() })
            } else {
                // Line-synced line: [(timeMs, text)] + translation slot (translation appended
                // as the last pair when present — Flamingo's merged-LRC convention).
                val line = mutableListOf(Pair(startTimeMs, entry.text))
                val translation = entry.providerTranslationText
                    ?.replace(Regex("\\s+"), " ")
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals(entry.text.trim(), ignoreCase = true) }
                line.add(
                    Pair(
                        startTimeMs,
                        translation ?: "",
                    ),
                )
                lrcEntries.add(line)

                lineEndTimes.add(0f)
                lineTransliterations.add(null)
                lineSubtitles.add(null)
            }
        }

        return FlamingoLyricsData(
            lrcEntries = lrcEntries,
            lineEndTimes = lineEndTimes,
            lineTransliterations = lineTransliterations,
            lineSubtitles = lineSubtitles,
            isTtmlLyrics = anyWordSynced,
            otherSideForLines = otherSideForLines,
        )
    }
}
