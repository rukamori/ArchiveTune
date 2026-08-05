/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics

import android.icu.text.Transliterator
import android.text.format.DateUtils
import com.atilika.kuromoji.ipadic.Tokenizer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.betterlyrics.QRCParser
import moe.rukamori.archivetune.betterlyrics.TTMLParser
import moe.rukamori.archivetune.db.entities.LyricsEntity
import java.lang.Character.UnicodeScript

data class LyricsRomanizationPreferences(
    val romanizeJapanese: Boolean,
    val romanizeKorean: Boolean,
    val romanizeChinese: Boolean,
    val romanizeHindi: Boolean,
    val romanizeOther: Boolean,
) {
    val isEnabled: Boolean
        get() = romanizeJapanese || romanizeKorean || romanizeChinese || romanizeHindi || romanizeOther
}

@Suppress("RegExpRedundantEscape")
object LyricsUtils {
    val LINE_REGEX = Regex("""((\[\d{1,3}:\d{2}(?:[.:]\d{2,3})?\]\s*)+)(.*)""")
    val TIME_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{2,3}))?\]""")
    private val WHITESPACE_REGEX = "\\s+".toRegex()
    private val ENHANCED_LRC_WORD_TIME_REGEX = Regex("""<\d{1,3}:\d{2}(?:[.:]\d{2,3})?>""")
    private val INLINE_MILLISECONDS_TIME_REGEX = Regex("""<\d{1,8}(?:,\d{1,8})?>""")
    private val YRC_LINE_REGEX = Regex("""\[(\d{1,8}),\d{1,8}\](.*)""")
    private val YRC_WORD_TIME_REGEX = Regex("""\(\d{1,8},\d{1,8}(?:,\d{1,8})?\)""")
    private val TTML_SPAN_REGEX =
        Regex(
            pattern = """<span\b[^>]*>""",
            options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        )
    private val TTML_BEGIN_ATTRIBUTE_REGEX = Regex("""\bbegin\s*=""", RegexOption.IGNORE_CASE)
    private val TTML_END_ATTRIBUTE_REGEX = Regex("""\b(?:end|dur)\s*=""", RegexOption.IGNORE_CASE)
    private val INVISIBLE_CHARS_REGEX = Regex("""[\u200B\u200C\u200D\u2060\u00AD]""")
    private const val NBSP = '\u00A0'
    private const val GENERIC_ROMANIZATION_TRANSFORM = "Any-Latin; Latin-ASCII"
    private val OTHER_ROMANIZATION_EXCLUDED_SCRIPTS =
        setOf(
            UnicodeScript.LATIN,
            UnicodeScript.COMMON,
            UnicodeScript.INHERITED,
            UnicodeScript.HAN,
            UnicodeScript.HIRAGANA,
            UnicodeScript.KATAKANA,
            UnicodeScript.HANGUL,
            UnicodeScript.DEVANAGARI,
        )
    private val genericRomanizationTransliterator =
        ThreadLocal.withInitial {
            Transliterator.getInstance(GENERIC_ROMANIZATION_TRANSFORM)
        }

    private val KANA_ROMAJI_MAP: Map<String, String> =
        mapOf(
            // Digraphs (Yōon - combinations like kya, sho)
            "キャ" to "kya",
            "キュ" to "kyu",
            "キョ" to "kyo",
            "シャ" to "sha",
            "シュ" to "shu",
            "ショ" to "sho",
            "チャ" to "cha",
            "チュ" to "chu",
            "チョ" to "cho",
            "ニャ" to "nya",
            "ニュ" to "nyu",
            "ニョ" to "nyo",
            "ヒャ" to "hya",
            "ヒュ" to "hyu",
            "ヒョ" to "hyo",
            "ミャ" to "mya",
            "ミュ" to "myu",
            "ミョ" to "myo",
            "リャ" to "rya",
            "リュ" to "ryu",
            "リョ" to "ryo",
            "ギャ" to "gya",
            "ギュ" to "gyu",
            "ギョ" to "gyo",
            "ジャ" to "ja",
            "ジュ" to "ju",
            "ジョ" to "jo",
            "ヂャ" to "ja",
            "ヂュ" to "ju",
            "ヂョ" to "jo", // ヂ variants, also commonly 'ja', 'ju', 'jo'
            "ビャ" to "bya",
            "ビュ" to "byu",
            "ビョ" to "byo",
            "ピャ" to "pya",
            "ピュ" to "pyu",
            "ピョ" to "pyo",
            // Basic Katakana Characters
            "ア" to "a",
            "イ" to "i",
            "ウ" to "u",
            "エ" to "e",
            "オ" to "o",
            "カ" to "ka",
            "キ" to "ki",
            "ク" to "ku",
            "ケ" to "ke",
            "コ" to "ko",
            "サ" to "sa",
            "シ" to "shi",
            "ス" to "su",
            "セ" to "se",
            "ソ" to "so",
            "タ" to "ta",
            "チ" to "chi",
            "ツ" to "tsu",
            "テ" to "te",
            "ト" to "to",
            "ナ" to "na",
            "ニ" to "ni",
            "ヌ" to "nu",
            "ネ" to "ne",
            "ノ" to "no",
            "ハ" to "ha",
            "ヒ" to "hi",
            "フ" to "fu",
            "ヘ" to "he",
            "ホ" to "ho",
            "マ" to "ma",
            "ミ" to "mi",
            "ム" to "mu",
            "メ" to "me",
            "モ" to "mo",
            "ヤ" to "ya",
            "ユ" to "yu",
            "ヨ" to "yo",
            "ラ" to "ra",
            "リ" to "ri",
            "ル" to "ru",
            "レ" to "re",
            "ロ" to "ro",
            "ワ" to "wa",
            "ヲ" to "o", // ヲ is pronounced 'o'
            "ン" to "n",
            // Dakuten (voiced consonants)
            "ガ" to "ga",
            "ギ" to "gi",
            "グ" to "gu",
            "ゲ" to "ge",
            "ゴ" to "go",
            "ザ" to "za",
            "ジ" to "ji",
            "ズ" to "zu",
            "ゼ" to "ze",
            "ゾ" to "zo",
            "ダ" to "da",
            "ヂ" to "ji",
            "ヅ" to "zu",
            "デ" to "de",
            "ド" to "do", // ヂ and ヅ are often 'ji' and 'zu'
            // Handakuten (p-sounds for 'h' group) / Dakuten for 'h' group
            "バ" to "ba",
            "ビ" to "bi",
            "ブ" to "bu",
            "ベ" to "be",
            "ボ" to "bo", // Dakuten for ハ행 (ha-row)
            "パ" to "pa",
            "ピ" to "pi",
            "プ" to "pu",
            "ペ" to "pe",
            "ポ" to "po", // Handakuten for ハ행 (ha-row)
            // Chōonpu (long vowel mark) - removed as per original logic
            "ー" to "",
        )

    private val HANGUL_ROMAJA_MAP: Map<String, Map<String, String>> =
        mapOf(
            "cho" to
                mapOf(
                    "ᄀ" to "g",
                    "ᄁ" to "kk",
                    "ᄂ" to "n",
                    "ᄃ" to "d",
                    "ᄄ" to "tt",
                    "ᄅ" to "r",
                    "ᄆ" to "m",
                    "ᄇ" to "b",
                    "ᄈ" to "pp",
                    "ᄉ" to "s",
                    "ᄊ" to "ss",
                    "ᄋ" to "",
                    "ᄌ" to "j",
                    "ᄍ" to "jj",
                    "ᄎ" to "ch",
                    "ᄏ" to "k",
                    "ᄐ" to "t",
                    "ᄑ" to "p",
                    "ᄒ" to "h",
                ),
            "jung" to
                mapOf(
                    "ᅡ" to "a",
                    "ᅢ" to "ae",
                    "ᅣ" to "ya",
                    "ᅤ" to "yae",
                    "ᅥ" to "eo",
                    "ᅦ" to "e",
                    "ᅧ" to "yeo",
                    "ᅨ" to "ye",
                    "ᅩ" to "o",
                    "ᅪ" to "wa",
                    "ᅫ" to "wae",
                    "ᅬ" to "oe",
                    "ᅭ" to "yo",
                    "ᅮ" to "u",
                    "ᅯ" to "wo",
                    "ᅰ" to "we",
                    "ᅱ" to "wi",
                    "ᅲ" to "yu",
                    "ᅳ" to "eu",
                    "ᅴ" to "eui",
                    "ᅵ" to "i",
                ),
            "jong" to
                mapOf(
                    "ᆨ" to "k",
                    "ᆨᄋ" to "g",
                    "ᆨᄂ" to "ngn",
                    "ᆨᄅ" to "ngn",
                    "ᆨᄆ" to "ngm",
                    "ᆨᄒ" to "kh",
                    "ᆩ" to "kk",
                    "ᆩᄋ" to "kg",
                    "ᆩᄂ" to "ngn",
                    "ᆩᄅ" to "ngn",
                    "ᆩᄆ" to "ngm",
                    "ᆩᄒ" to "kh",
                    "ᆪ" to "k",
                    "ᆪᄋ" to "ks",
                    "ᆪᄂ" to "ngn",
                    "ᆪᄅ" to "ngn",
                    "ᆪᄆ" to "ngm",
                    "ᆪᄒ" to "kch",
                    "ᆫ" to "n",
                    "ᆫᄅ" to "ll",
                    "ᆬ" to "n",
                    "ᆬᄋ" to "nj",
                    "ᆬᄂ" to "nn",
                    "ᆬᄅ" to "nn",
                    "ᆬᄆ" to "nm",
                    "ᆬㅎ" to "nch",
                    "ᆭ" to "n",
                    "ᆭᄋ" to "nh",
                    "ᆭᄅ" to "nn",
                    "ᆮ" to "t",
                    "ᆮᄋ" to "d",
                    "ᆮᄂ" to "nn",
                    "ᆮᄅ" to "nn",
                    "ᆮᄆ" to "nm",
                    "ᆮᄒ" to "th",
                    "ᆯ" to "l",
                    "ᆯᄋ" to "r",
                    "ᆯᄂ" to "ll",
                    "ᆯᄅ" to "ll",
                    "ᆰ" to "k",
                    "ᆰᄋ" to "lg",
                    "ᆰᄂ" to "ngn",
                    "ᆰᄅ" to "ngn",
                    "ᆰᄆ" to "ngm",
                    "ᆰᄒ" to "lkh",
                    "ᆱ" to "m",
                    "ᆱᄋ" to "lm",
                    "ᆱᄂ" to "mn",
                    "ᆱᄅ" to "mn",
                    "ᆱᄆ" to "mm",
                    "ᆱᄒ" to "lmh",
                    "ᆲ" to "p",
                    "ᆲᄋ" to "lb",
                    "ᆲᄂ" to "mn",
                    "ᆲᄅ" to "mn",
                    "ᆲᄆ" to "mm",
                    "ᆲᄒ" to "lph",
                    "ᆳ" to "t",
                    "ᆳᄋ" to "ls",
                    "ᆳᄂ" to "nn",
                    "ᆳᄅ" to "nn",
                    "ᆳᄆ" to "nm",
                    "ᆳᄒ" to "lsh",
                    "ᆴ" to "t",
                    "ᆴᄋ" to "lt",
                    "ᆴᄂ" to "nn",
                    "ᆴᄅ" to "nn",
                    "ᆴᄆ" to "nm",
                    "ᆴᄒ" to "lth",
                    "ᆵ" to "p",
                    "ᆵᄋ" to "lp",
                    "ᆵᄂ" to "mn",
                    "ᆵᄅ" to "mn",
                    "ᆵᄆ" to "mm",
                    "ᆵᄒ" to "lph",
                    "ᆶ" to "l",
                    "ᆶᄋ" to "lh",
                    "ᆶᄂ" to "ll",
                    "ᆶᄅ" to "ll",
                    "ᆶᄆ" to "lm",
                    "ᆶᄒ" to "lh",
                    "ᆷ" to "m",
                    "ᆷᄅ" to "mn",
                    "ᆸ" to "p",
                    "ᆸᄋ" to "b",
                    "ᆸᄂ" to "mn",
                    "ᆸᄅ" to "mn",
                    "ᆸᄆ" to "mm",
                    "ᆸᄒ" to "ph",
                    "ᆹ" to "p",
                    "ᆹᄋ" to "ps",
                    "ᆹᄂ" to "mn",
                    "ᆹᄅ" to "mn",
                    "ᆹᄆ" to "mm",
                    "ᆹᄒ" to "psh",
                    "ᆺ" to "t",
                    "ᆺᄋ" to "s",
                    "ᆺᄂ" to "nn",
                    "ᆺᄅ" to "nn",
                    "ᆺᄆ" to "nm",
                    "ᆺᄒ" to "sh",
                    "ᆻ" to "t",
                    "ᆻᄋ" to "ss",
                    "ᆻᄂ" to "tn",
                    "ᆻᄅ" to "tn",
                    "ᆻᄆ" to "nm",
                    "ᆻᄒ" to "th",
                    "ᆼ" to "ng",
                    "ᆽ" to "t",
                    "ᆽᄋ" to "j",
                    "ᆽᄂ" to "nn",
                    "ᆽᄅ" to "nn",
                    "ᆽᄆ" to "nm",
                    "ᆽᄒ" to "ch",
                    "ᆾ" to "t",
                    "ᆾᄋ" to "ch",
                    "ᆾᄂ" to "nn",
                    "ᆾᄅ" to "nn",
                    "ᆾᄆ" to "nm",
                    "ᆾᄒ" to "ch",
                    "ᆿ" to "k",
                    "ᆿᄋ" to "k",
                    "ᆿᄂ" to "ngn",
                    "ᆿᄅ" to "ngn",
                    "ᆿᄆ" to "ngm",
                    "ᆿᄒ" to "kh",
                    "ᇀ" to "t",
                    "ᇀᄋ" to "t",
                    "ᇀᄂ" to "nn",
                    "ᇀᄅ" to "nn",
                    "ᇀᄆ" to "nm",
                    "ᇀᄒ" to "th",
                    "ᇁ" to "p",
                    "ᇁᄋ" to "p",
                    "ᇁᄂ" to "mn",
                    "ᇁᄅ" to "mn",
                    "ᇁᄆ" to "mm",
                    "ᇁᄒ" to "ph",
                    "ᇂ" to "t",
                    "ᇂᄋ" to "h",
                    "ᇂᄂ" to "nn",
                    "ᇂᄅ" to "nn",
                    "ᇂᄆ" to "mm",
                    "ᇂᄒ" to "t",
                    "ᇂᄀ" to "k",
                ),
        )

    // Lazy initialized Tokenizer
    private val kuromojiTokenizer: Tokenizer by lazy {
        Tokenizer()
    }

    fun isTtml(lyrics: String): Boolean {
        val trimmed = normalizeLyricsText(lyrics)
        if (!trimmed.startsWith("<")) return false

        return trimmed.contains("<tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true)
    }

    fun isLineSyncedLrc(lyrics: String): Boolean =
        QRCParser.isQrc(normalizeLyricsText(lyrics)) ||
            lyrics.lineSequence().any { line ->
            val trimmedLine = line.trim()
            LINE_REGEX.matches(trimmedLine) || YRC_LINE_REGEX.matches(trimmedLine)
        }

    fun hasWordSyncedLyrics(lyrics: String): Boolean {
        val normalized = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalized)) return QRCParser.hasWordTimings(normalized)
        if (!isTtml(normalized)) return false

        return TTML_SPAN_REGEX.findAll(normalized).any { match ->
            TTML_BEGIN_ATTRIBUTE_REGEX.containsMatchIn(match.value) &&
                TTML_END_ATTRIBUTE_REGEX.containsMatchIn(match.value)
        }
    }

    fun parseTtml(
        lyrics: String,
        durationSeconds: Int? = null,
    ): List<LyricsEntry> {
        val parsedLines = TTMLParser.parseTTML(normalizeLyricsText(lyrics))
        if (parsedLines.isEmpty()) return emptyList()
        val scale = 1.0

        return parsedLines
            .map { line ->
                val words =
                    line.words
                        .filter { it.text.isNotEmpty() }
                        .map { word ->
                            WordTimestamp(
                                text = word.text,
                                startTime = word.startTime * scale,
                                endTime = word.endTime * scale,
                                isBackground = word.isBackground,
                            )
                        }.takeIf { it.isNotEmpty() }

                LyricsEntry(
                    time = (line.startTime * scale * 1000.0).toLong(),
                    text = line.text,
                    words = words,
                    agent = line.agent,
                    providerRomanizedText = line.providerRomanizedText,
                    providerRomanizedWords = line.providerRomanizedWords,
                    providerRomanizedLanguage = line.providerRomanizedLanguage,
                    providerTranslationText = line.providerTranslationText,
                )
            }.sorted()
    }

    fun parseLyrics(lyrics: String): List<LyricsEntry> {
        val normalizedLyrics = normalizeLyricsText(lyrics)
        if (QRCParser.isQrc(normalizedLyrics)) {
            return QRCParser.parseQrc(normalizedLyrics).map { line ->
                LyricsEntry(
                    time = (line.startTime * 1000.0).toLong(),
                    text = line.text,
                    words =
                        line.words
                            .map { word ->
                                WordTimestamp(
                                    text = word.text,
                                    startTime = word.startTime,
                                    endTime = word.endTime,
                                )
                            }.takeIf { it.isNotEmpty() },
                    agent = line.agent,
                    durationMs = ((line.endTime - line.startTime) * 1000.0).toLong().coerceAtLeast(0L),
                )
            }
        }

        val lines = normalizedLyrics.lines()
        val result = mutableListOf<LyricsEntry>()

        for (line in lines) {
            val entries = parseLineSyncedLrcLine(line) ?: parseMillisecondsSyncedLine(line)
            if (entries != null) {
                result.addAll(entries)
            }
        }
        return mergeLineSyncedTranslations(result).sorted()
    }

    fun normalizeLyricsText(lyrics: String): String {
        val raw =
            lyrics
                .replace("\uFEFF", "")
                .replace(INVISIBLE_CHARS_REGEX, "")
                .trim { it.isWhitespace() || it == NBSP }

        val unwrapped = stripCodeFence(raw)
        val normalized =
            if (isEscapedTtml(unwrapped)) {
                unwrapped
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                    .replace("&quot;", "\"")
                    .replace("&#39;", "'")
                    .replace("&apos;", "'")
            } else {
                unwrapped
            }

        return normalized.trim { it.isWhitespace() || it == NBSP }
    }

    fun displayLyricsText(lyrics: String): String {
        val raw = normalizeLyricsText(lyrics)
        if (raw.isEmpty() || raw == LyricsEntity.LYRICS_NOT_FOUND) return ""

        val visibleLines =
            when {
                isTtml(raw) -> runCatching { parseTtml(raw).map { it.text } }.getOrElse { emptyList() }
                isLineSyncedLrc(raw) -> runCatching { parseLyrics(raw).map { it.text } }.getOrElse { emptyList() }
                raw.startsWith("<") -> emptyList()
                else -> raw.lines().map(::cleanInlineWordTimingText)
            }

        return visibleLines
            .map { line ->
                line
                    .replace(WHITESPACE_REGEX, " ")
                    .trim { it.isWhitespace() || it == NBSP }
            }.filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun hasMeaningfulLyricsContent(lyrics: String): Boolean = displayLyricsText(lyrics).isNotEmpty()

    fun lyricsOrNotFound(lyrics: String): String {
        val normalized = normalizeLyricsText(lyrics)
        return normalized.takeIf(::hasMeaningfulLyricsContent) ?: LyricsEntity.LYRICS_NOT_FOUND
    }

    private fun stripCodeFence(lyrics: String): String {
        if (!lyrics.startsWith("```")) return lyrics

        val lines = lyrics.lines()
        if (lines.size <= 1) return lyrics

        val bodyLines =
            lines.drop(1).let { remainingLines ->
                if (remainingLines.lastOrNull()?.trim() == "```") {
                    remainingLines.dropLast(1)
                } else {
                    remainingLines
                }
            }

        return bodyLines.joinToString("\n").trim { it.isWhitespace() || it == NBSP }
    }

    private fun isEscapedTtml(lyrics: String): Boolean {
        val trimmed = lyrics.trimStart()
        return trimmed.startsWith("&lt;tt", ignoreCase = true) ||
            trimmed.contains("&lt;tt", ignoreCase = true) ||
            trimmed.contains("http://www.w3.org/ns/ttml", ignoreCase = true) &&
            trimmed.contains("&lt;", ignoreCase = true)
    }

    fun insertInstrumentalBreaks(
        entries: List<LyricsEntry>,
        songDurationMs: Long = 0L,
    ): List<LyricsEntry> {
        if (entries.isEmpty()) return entries
        val result = mutableListOf<LyricsEntry>()
        insertIntroInstrumentalIfNeeded(entries, result)
        result.addAll(entries)
        insertOutroInstrumentalIfNeeded(entries, songDurationMs, result)
        return result
    }

    private const val INSTRUMENTAL_GAP_THRESHOLD_MS = 5000L
    private const val INSTRUMENTAL_INTRO_START_MS = 1000L
    private const val INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS = 2500L

    private fun insertIntroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        result: MutableList<LyricsEntry>,
    ) {
        val firstTimedVocalEntry = entries.firstOrNull { it.time >= 0L && it.text.isNotBlank() } ?: return
        val introGapMs = firstTimedVocalEntry.time - INSTRUMENTAL_INTRO_START_MS
        if (introGapMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = INSTRUMENTAL_INTRO_START_MS,
                text = "",
                isInstrumental = true,
                durationMs = introGapMs,
            ),
        )
    }

    private fun insertOutroInstrumentalIfNeeded(
        entries: List<LyricsEntry>,
        songDurationMs: Long,
        result: MutableList<LyricsEntry>,
    ) {
        if (songDurationMs <= 0L) return
        val lastVocalEntry = entries.lastOrNull { it.text.isNotBlank() } ?: return
        val outroStartMs = lastVocalEntry.time + INSTRUMENTAL_OUTRO_VOCAL_TAIL_MS
        val outroDurationMs = songDurationMs - outroStartMs
        if (outroDurationMs < INSTRUMENTAL_GAP_THRESHOLD_MS) return

        result.add(
            LyricsEntry(
                time = outroStartMs,
                text = "",
                isInstrumental = true,
                durationMs = outroDurationMs,
            ),
        )
    }

    private fun parseLineSyncedLrcLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = LINE_REGEX.matchEntire(line.trim()) ?: return null
        val times = matchResult.groupValues[1]
        val text = cleanInlineWordTimingText(matchResult.groupValues[3])
        val timeMatchResults = TIME_REGEX.findAll(times)

        return timeMatchResults
            .map { timeMatchResult ->
                val min = timeMatchResult.groupValues[1].toLong()
                val sec = timeMatchResult.groupValues[2].toLong()
                val milString = timeMatchResult.groupValues[3]
                var mil = milString.toLongOrNull() ?: 0L
                when (milString.length) {
                    1 -> mil *= 100
                    2 -> mil *= 10
                }
                val time = min * DateUtils.MINUTE_IN_MILLIS + sec * DateUtils.SECOND_IN_MILLIS + mil
                LyricsEntry(time, text)
            }.toList()
    }

    private fun parseMillisecondsSyncedLine(line: String): List<LyricsEntry>? {
        if (line.isEmpty()) {
            return null
        }
        val matchResult = YRC_LINE_REGEX.matchEntire(line.trim()) ?: return null
        val time = matchResult.groupValues[1].toLongOrNull() ?: return null
        val text = cleanInlineWordTimingText(matchResult.groupValues[2])
        return listOf(LyricsEntry(time, text))
    }

    private fun mergeLineSyncedTranslations(entries: List<LyricsEntry>): List<LyricsEntry> {
        val mergedByTime = linkedMapOf<Long, LyricsEntry>()
        entries.forEach { entry ->
            val existing = mergedByTime[entry.time]
            if (existing == null) {
                mergedByTime[entry.time] = entry
                return@forEach
            }

            val translatedText =
                entry.text
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                    .takeIf { it.isNotEmpty() && !it.equals(existing.text.trim(), ignoreCase = true) }

            if (translatedText != null && existing.providerTranslationText == null) {
                mergedByTime[entry.time] = existing.copy(providerTranslationText = translatedText)
            }
        }
        return mergedByTime.values.toList()
    }

    private fun cleanInlineWordTimingText(text: String): String =
        text
            .replace(ENHANCED_LRC_WORD_TIME_REGEX, "")
            .replace(INLINE_MILLISECONDS_TIME_REGEX, "")
            .replace(YRC_WORD_TIME_REGEX, "")
            .replace(WHITESPACE_REGEX, " ")
            .trim { it.isWhitespace() || it == NBSP }

    fun findCurrentLineIndex(
        lines: List<LyricsEntry>,
        position: Long,
        leadMs: Long = 300L,
    ): Int {
        if (lines.isEmpty()) return -1

        val target = position + leadMs
        var low = 0
        var high = lines.lastIndex

        while (low <= high) {
            val mid = (low + high).ushr(1)
            val midTime = lines[mid].time

            if (midTime < target) {
                low = mid + 1
            } else {
                high = mid - 1
            }
        }

        return high.coerceIn(0, lines.lastIndex)
    }

    /**
     * Converts a Katakana string to Romaji.
     * This optimized version uses a pre-defined map and StringBuilder for better performance
     * compared to chained regex replacements.
     * Expected impact: Significant reduction in object creation (Regex, String) and faster execution.
     */
    fun katakanaToRomaji(katakana: String?): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length) // Initial capacity
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "キャ" before "キ")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    /**
     * Romanizes Japanese text using Kuromoji Tokenizer and the optimized katakanaToRomaji function.
     * Runs on Dispatchers.Default for CPU-intensive work.
     * Expected impact: Faster tokenization due to reused Tokenizer instance and faster
     * per-token romanization.
     */
    suspend fun romanizeJapanese(text: String): String =
        withContext(Dispatchers.Default) {
            // Use the lazily initialized tokenizer
            val tokens = kuromojiTokenizer.tokenize(text)

            val romanizedTokens =
                tokens.mapIndexed { index, token ->
                    val currentReading =
                        if (token.reading.isNullOrEmpty() || token.reading == "*") {
                            token.surface
                        } else {
                            token.reading
                        }

                    // Pass the next token's reading for sokuon handling if applicable
                    val nextTokenReading =
                        if (index + 1 < tokens.size) {
                            tokens[index + 1].reading?.takeIf { it.isNotEmpty() && it != "*" } ?: tokens[index + 1].surface
                        } else {
                            null
                        }
                    katakanaToRomaji(currentReading, nextTokenReading)
                }
            romanizedTokens.joinToString(" ")
        }

    /**
     * Converts a Katakana string to Romaji.
     * This optimized version uses a pre-defined map and StringBuilder for better performance
     * compared to chained regex replacements.
     * Expected impact: Significant reduction in object creation (Regex, String) and faster execution.
     * @param katakana The Katakana string to convert.
     * @param nextKatakana Optional: The next Katakana string (from the next token) to help with sokuon (ッ) gemination.
     */
    fun katakanaToRomaji(
        katakana: String?,
        nextKatakana: String? = null,
    ): String {
        if (katakana.isNullOrEmpty()) return ""

        val romajiBuilder = StringBuilder(katakana.length) // Initial capacity
        var i = 0
        val n = katakana.length
        while (i < n) {
            var consumed = false
            // Prioritize 2-character sequences from the map (e.g., "キャ" before "キ")
            if (i + 1 < n) {
                val twoCharCandidate = katakana.substring(i, i + 2)
                val mappedTwoChar = KANA_ROMAJI_MAP[twoCharCandidate]
                if (mappedTwoChar != null) {
                    romajiBuilder.append(mappedTwoChar)
                    i += 2
                    consumed = true
                }
            }

            // Handle sokuon (ッ) - gemination
            if (!consumed && katakana[i] == 'ッ') {
                val nextCharToDouble = nextKatakana?.getOrNull(0)
                if (nextCharToDouble != null) {
                    val nextCharRomaji =
                        KANA_ROMAJI_MAP[nextCharToDouble.toString()]?.getOrNull(0)?.toString()
                            ?: nextCharToDouble.toString()
                    romajiBuilder.append(nextCharRomaji.lowercase().trim())
                }
                // Sokuon itself doesn't have a direct romaji representation other than geminating the next consonant.
                // We just consume 'ッ' and let the next character (if any within the current token) be processed normally.
                i += 1 // Consume the 'ッ'
                consumed = true
            }

            if (!consumed) {
                // If no 2-character sequence matched, try 1-character
                val oneCharCandidate = katakana[i].toString()
                val mappedOneChar = KANA_ROMAJI_MAP[oneCharCandidate]
                if (mappedOneChar != null) {
                    romajiBuilder.append(mappedOneChar)
                } else {
                    // If the character is not in Katakana map, append it as is.
                    romajiBuilder.append(oneCharCandidate)
                }
                i += 1
            }
        }
        return romajiBuilder.toString().lowercase()
    }

    suspend fun romanizeKorean(text: String): String =
        withContext(Dispatchers.Default) {
            val romajaBuilder = StringBuilder()
            var prevFinal: String? = null

            for (i in text.indices) {
                val char = text[i]

                if (char in '\uAC00'..'\uD7A3') {
                    val syllableIndex = char.code - 0xAC00

                    val choIndex = syllableIndex / (21 * 28)
                    val jungIndex = (syllableIndex % (21 * 28)) / 28
                    val jongIndex = syllableIndex % 28

                    val choChar = (0x1100 + choIndex).toChar().toString()
                    val jungChar = (0x1161 + jungIndex).toChar().toString()
                    val jongChar = if (jongIndex == 0) null else (0x11A7 + jongIndex).toChar().toString()

                    if (prevFinal != null) {
                        val contextKey = prevFinal + choChar
                        val jong =
                            HANGUL_ROMAJA_MAP["jong"]?.get(contextKey)
                                ?: HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal)
                                ?: prevFinal
                        romajaBuilder.append(jong)
                    }

                    val cho = HANGUL_ROMAJA_MAP["cho"]?.get(choChar) ?: choChar
                    val jung = HANGUL_ROMAJA_MAP["jung"]?.get(jungChar) ?: jungChar
                    romajaBuilder.append(cho).append(jung)

                    prevFinal = jongChar
                } else {
                    if (prevFinal != null) {
                        val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                        romajaBuilder.append(jong)
                        prevFinal = null
                    }
                    romajaBuilder.append(char)
                }
            }

            if (prevFinal != null) {
                val jong = HANGUL_ROMAJA_MAP["jong"]?.get(prevFinal) ?: prevFinal
                romajaBuilder.append(jong)
            }

            romajaBuilder.toString()
        }

    /**
     * Checks if the given text contains any Japanese characters (Hiragana, Katakana, or common Kanji).
     * This function is generally efficient due to '.any' and early exit.
     * No major performance bottlenecks expected here for typical inputs.
     */
    fun isJapanese(text: String): Boolean =
        text.any { char ->
            (char in '\u3040'..'\u309F') || // Hiragana
                (char in '\u30A0'..'\u30FF') || // Katakana
                // CJK Unified Ideographs (covers most common Kanji)
                // Note: This range also includes many Chinese Hanzi.
                // Differentiating Japanese Kanji from Chinese Hanzi solely based on Unicode
                // ranges is challenging as they share many characters.
                // For more accurate Japanese detection, one might need to analyze
                // the presence of Hiragana/Katakana alongside Kanji.
                (char in '\u4E00'..'\u9FFF')
        }

    /**
     * Checks if the given text contains any Korean characters (Hangul Syllables, Jamo, etc.).
     */
    fun isKorean(text: String): Boolean =
        text.any { char ->
            (char in '\uAC00'..'\uD7A3') // Hangul Syllables
        }

    /**
     * Checks if the given text contains any Chinese characters (common Hanzi).
     * This function is generally efficient due to '.any' and early exit.
     * To improve accuracy in distinguishing between Chinese and Japanese (which shares Kanji),
     * this function now checks if the text *predominantly* consists of CJK Unified Ideographs
     * and *lacks* significant amounts of Hiragana or Katakana.
     *
     * A simple threshold is used here. More sophisticated methods (e.g., frequency analysis,
     * dictionaries, or machine learning models) would be needed for higher accuracy.
     */
    fun isChinese(text: String): Boolean {
        if (text.isEmpty()) return false

        val hanCharCount = text.count { hasScript(it, UnicodeScript.HAN) }
        if (hanCharCount == 0) return false

        val japaneseKanaCount = text.count { hasScript(it, UnicodeScript.HIRAGANA) || hasScript(it, UnicodeScript.KATAKANA) }
        val hangulCount = text.count { hasScript(it, UnicodeScript.HANGUL) }

        return japaneseKanaCount == 0 && hangulCount == 0
    }

    fun isHindi(text: String): Boolean = text.any { hasScript(it, UnicodeScript.DEVANAGARI) }

    fun hasOtherRomanizableScript(text: String): Boolean {
        return text.any { char ->
            if (!char.isLetter()) return@any false
            val script = UnicodeScript.of(char.code)
            script !in OTHER_ROMANIZATION_EXCLUDED_SCRIPTS
        }
    }

    fun shouldRomanizeLyricsLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
    ): Boolean {
        if (!preferences.isEnabled || text.isBlank()) return false

        return when {
            preferences.romanizeJapanese && looksJapanese(text) -> true
            preferences.romanizeKorean && isKorean(text) -> true
            preferences.romanizeHindi && isHindi(text) -> true
            preferences.romanizeChinese && isChinese(text) -> true
            preferences.romanizeOther && hasOtherRomanizableScript(text) -> true
            else -> false
        }
    }

    fun shouldUseProvidedRomanization(
        originalText: String,
        providerRomanizedText: String?,
        providerRomanizedLanguage: String?,
        preferences: LyricsRomanizationPreferences,
    ): Boolean {
        if (!preferences.isEnabled || originalText.isBlank()) return false
        val normalized =
            providerRomanizedText
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return false
        if (normalized.equals(originalText.trim(), ignoreCase = true)) return false

        val language =
            providerRomanizedLanguage
                ?.substringBefore("-")
                ?.substringBefore("_")
                ?.lowercase()

        return when (language) {
            "ja" -> preferences.romanizeJapanese
            "ko" -> preferences.romanizeKorean
            "zh", "cmn", "yue" -> preferences.romanizeChinese
            "hi", "sa", "mr", "ne" -> preferences.romanizeHindi
            null, "" -> shouldRomanizeLyricsLine(originalText, preferences)
            else -> preferences.romanizeOther || shouldRomanizeLyricsLine(originalText, preferences)
        }
    }

    fun providedRomanizedTextForEntry(
        entry: LyricsEntry,
        preferences: LyricsRomanizationPreferences,
    ): String? =
        entry.providerRomanizedText
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf {
                shouldUseProvidedRomanization(
                    originalText = entry.text,
                    providerRomanizedText = it,
                    providerRomanizedLanguage = entry.providerRomanizedLanguage,
                    preferences = preferences,
                )
            }

    fun providedTranslationTextForEntry(entry: LyricsEntry): String? =
        entry.providerTranslationText
            ?.replace(WHITESPACE_REGEX, " ")
            ?.trim()
            ?.takeIf { it.isNotEmpty() && !it.equals(entry.text.trim(), ignoreCase = true) }

    fun providedRomanizedWordsForEntry(
        entry: LyricsEntry,
        expectedWordCount: Int,
        preferences: LyricsRomanizationPreferences,
    ): List<String?>? {
        if (expectedWordCount <= 0) return null
        if (providedRomanizedTextForEntry(entry, preferences) == null) return null

        val words =
            entry.providerRomanizedWords
                ?.map { word -> word.replace(WHITESPACE_REGEX, " ").trim() }
                ?.filter { it.isNotEmpty() }
                ?.takeIf { it.size == expectedWordCount }
                ?: return null

        return words
    }

    suspend fun romanizeLyricsLine(
        text: String,
        preferences: LyricsRomanizationPreferences,
    ): String? {
        if (!shouldRomanizeLyricsLine(text, preferences)) return null

        val romanized =
            when {
                preferences.romanizeJapanese && looksJapanese(text) -> romanizeJapanese(text)
                preferences.romanizeKorean && isKorean(text) -> romanizeKorean(text)
                preferences.romanizeHindi && isHindi(text) -> romanizeWithIcu(text)
                preferences.romanizeChinese && isChinese(text) -> romanizeWithIcu(text)
                preferences.romanizeOther && hasOtherRomanizableScript(text) -> romanizeWithIcu(text)
                else -> null
            }

        return normalizeRomanizedText(text, romanized)
    }

    suspend fun romanizeLyricsWordWithLineContext(
        word: String,
        lineText: String,
        preferences: LyricsRomanizationPreferences,
    ): String? {
        if (word.isBlank()) return null
        val romanized =
            when {
                preferences.romanizeJapanese && looksJapanese(lineText) -> romanizeJapanese(word)
                preferences.romanizeKorean && isKorean(lineText) -> romanizeKorean(word)
                preferences.romanizeHindi && isHindi(lineText) -> romanizeWithIcu(word)
                preferences.romanizeChinese && isChinese(lineText) -> romanizeWithIcu(word)
                preferences.romanizeOther && hasOtherRomanizableScript(lineText) -> romanizeWithIcu(word)
                else -> null
            }
        return normalizeRomanizedText(word, romanized)
    }

    private suspend fun romanizeWithIcu(text: String): String =
        withContext(Dispatchers.Default) {
            genericRomanizationTransliterator.get().transliterate(text)
        }

    private fun normalizeRomanizedText(
        original: String,
        romanized: String?,
    ): String? {
        val normalized =
            romanized
                ?.replace(WHITESPACE_REGEX, " ")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: return null

        return normalized.takeUnless { it.equals(original.trim(), ignoreCase = true) }
    }

    private fun looksJapanese(text: String): Boolean =
        text.any {
            hasScript(it, UnicodeScript.HIRAGANA) ||
                hasScript(it, UnicodeScript.KATAKANA) ||
                it == '々' ||
                it == '〆' ||
                it == 'ヶ'
        }

    private fun hasScript(
        char: Char,
        script: UnicodeScript,
    ): Boolean = char.isLetter() && UnicodeScript.of(char.code) == script
}
