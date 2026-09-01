/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.lyrics.translation

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import me.bush.translator.Language
import moe.rukamori.archivetune.ai.AiLyricsDocumentParser
import moe.rukamori.archivetune.ai.AiLyricsSegment
import moe.rukamori.archivetune.lyrics.LyricsUtils
import java.util.UUID
import javax.inject.Inject
import kotlin.coroutines.coroutineContext

sealed interface TranslateLyricsResult {
    data object Success : TranslateLyricsResult

    data object Empty : TranslateLyricsResult

    data object UnsupportedLanguage : TranslateLyricsResult
}

class TranslateLyricsUseCase
    @Inject
    constructor(
        private val translationRepository: StandardLyricsTranslationRepository,
        private val translatedLyricsRepository: TranslatedLyricsRepository,
    ) {
        suspend operator fun invoke(
            mediaId: String,
            lyrics: String,
            targetLanguage: String,
        ): TranslateLyricsResult {
            val targetLanguageCode = resolveLanguageCode(targetLanguage) ?: return TranslateLyricsResult.UnsupportedLanguage
            val document = withContext(Dispatchers.Default) { AiLyricsDocumentParser.parse(lyrics) }
            if (document.segments.isEmpty()) return TranslateLyricsResult.Empty

            val translatedSegments = LinkedHashMap<Int, String>(document.segments.size)
            document.segments.chunkedForTranslation().forEach { batch ->
                coroutineContext.ensureActive()
                val separator = uniqueTranslationSeparator(batch)
                val joinedText = batch.joinToString(separator = separator) { segment -> segment.text }
                val translatedText = translationRepository.translate(joinedText, targetLanguageCode)
                val translatedBatch = translatedText.split(separator)
                if (translatedBatch.size != batch.size) {
                    throw StandardLyricsTranslationException("Translation service changed lyric segment boundaries")
                }
                batch.forEachIndexed { index, segment ->
                    translatedSegments[segment.id] = translatedBatch[index]
                }
            }

            val rebuiltLyrics = withContext(Dispatchers.Default) { document.rebuild(translatedSegments) }
            val usableLyrics =
                LyricsUtils
                    .normalizeLyricsText(rebuiltLyrics)
                    .takeIf(LyricsUtils::hasMeaningfulLyricsContent)
                    ?: return TranslateLyricsResult.Empty
            translatedLyricsRepository.replaceLyrics(mediaId = mediaId, lyrics = usableLyrics)
            return TranslateLyricsResult.Success
        }

        private fun resolveLanguageCode(language: String): String? {
            val normalizedLanguage = language.trim().uppercase()
            LanguageCodeAliases[normalizedLanguage]?.let { return it }
            return runCatching { Language(normalizedLanguage, strict = true).code }.getOrNull()
        }

        private fun List<AiLyricsSegment>.chunkedForTranslation(): List<List<AiLyricsSegment>> {
            val chunks = ArrayList<List<AiLyricsSegment>>()
            val current = ArrayList<AiLyricsSegment>()
            var currentChars = 0

            forEach { segment ->
                val separatorChars = if (current.isEmpty()) 0 else TranslationSeparatorLength
                val nextSize = currentChars + separatorChars + segment.text.length
                if (current.isNotEmpty() && (current.size >= MaxItemsPerBatch || nextSize > MaxCharsPerBatch)) {
                    chunks.add(current.toList())
                    current.clear()
                    currentChars = 0
                }
                if (current.isNotEmpty()) currentChars += TranslationSeparatorLength
                current.add(segment)
                currentChars += segment.text.length
            }

            if (current.isNotEmpty()) chunks.add(current.toList())
            return chunks
        }

        private fun uniqueTranslationSeparator(segments: List<AiLyricsSegment>): String {
            var separator = "<<<SEP-${UUID.randomUUID()}>>>"
            while (segments.any { segment -> segment.text.contains(separator) }) {
                separator = "<<<SEP-${UUID.randomUUID()}>>>"
            }
            return separator
        }

        private companion object {
            const val MaxItemsPerBatch = 50
            const val MaxCharsPerBatch = 4_000
            const val TranslationSeparatorLength = 46

            val LanguageCodeAliases =
                mapOf(
                    "ASSAMESE" to "as",
                    "BURMESE" to "my",
                    "HAITIAN_CREOLE" to "ht",
                    "HEBREW" to "he",
                    "KINYARWANDA" to "rw",
                    "NYANJA_CHICHEWA" to "ny",
                    "ODIA_ORIYA" to "or",
                    "SUNDANESE" to "su",
                    "TATAR" to "tt",
                    "TURKMEN" to "tk",
                    "UIGHUR" to "ug",
                )
        }
    }
