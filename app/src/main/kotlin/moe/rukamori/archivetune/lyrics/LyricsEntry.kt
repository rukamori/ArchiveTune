/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import kotlinx.coroutines.flow.MutableStateFlow

data class WordTimestamp(
    val text: String,
    val startTime: Double,
    val endTime: Double,
    val isBackground: Boolean = false,
)

data class LyricsEntry(
    val time: Long,
    val text: String,
    val words: List<WordTimestamp>? = null,
    val agent: String? = null,
    val isInstrumental: Boolean = false,
    val durationMs: Long = 0L,
    val providerRomanizedText: String? = null,
    val providerRomanizedWords: List<String>? = null,
    val providerRomanizedLanguage: String? = null,
    val providerTranslationText: String? = null,
    val isRtl: Boolean? = null,
    val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
) : Comparable<LyricsEntry> {
    override fun compareTo(other: LyricsEntry): Int = (time - other.time).toInt()

    companion object {
        val HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")
    }
}
