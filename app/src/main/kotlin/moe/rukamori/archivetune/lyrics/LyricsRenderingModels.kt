/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import androidx.compose.runtime.Immutable
import com.google.common.collect.ImmutableList

enum class LyricsSyncType {
    PLAIN,
    LINE,
    WORD,
}

enum class LyricsSourceFormat {
    PLAIN,
    LRC,
    QRC,
    TTML,
}

enum class LyricsLineAlignment {
    START,
    END,
    CENTER,
}

enum class LyricsTextDirection {
    LTR,
    RTL,
}

@Immutable
data class PreparedLyricsWord(
    val text: String,
    val startMs: Long,
    val endMs: Long,
)

@Immutable
data class PreparedLyricsTrack(
    val text: String,
    val language: String?,
    val words: ImmutableList<PreparedLyricsWord>,
)

@Immutable
data class PreparedLyricsPhonetic(
    val wordIndex: Int,
    val text: String,
)

@Immutable
data class PreparedLyricsLine(
    val id: String,
    val startMs: Long,
    val endMs: Long,
    val text: String,
    val alignment: LyricsLineAlignment,
    val direction: LyricsTextDirection,
    val main: PreparedLyricsTrack,
    val backgrounds: ImmutableList<PreparedLyricsTrack>,
    val translation: String?,
    val romanizedText: String?,
    val phonetics: ImmutableList<PreparedLyricsPhonetic>,
    val isInstrumental: Boolean,
)

@Immutable
data class LyricsRenderingPreferences(
    val clickEnabled: Boolean,
    val scrollEnabled: Boolean,
    val textSizeSp: Float,
    val lineSpacing: Float,
    val lineBlurEnabled: Boolean,
    val v2BounceFactor: Float,
    val v2GlowFactor: Float,
    val v2FillTransitionWidthDp: Float,
    val v2LrcBounceEnabled: Boolean,
    val romanization: LyricsRomanizationPreferences,
)

@Immutable
data class PreparedLyrics(
    val sourceFormat: LyricsSourceFormat,
    val syncType: LyricsSyncType,
    val lines: ImmutableList<PreparedLyricsLine>,
    val preferences: LyricsRenderingPreferences,
)
