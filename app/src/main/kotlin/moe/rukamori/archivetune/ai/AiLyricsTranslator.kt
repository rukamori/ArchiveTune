/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ai

class AiLyricsTranslator {
    suspend fun translate(
        config: AiServiceConfig,
        lyrics: String,
        targetLanguage: String,
        customPrompt: String,
    ): String {
        val document = AiLyricsDocumentParser.parse(lyrics)
        if (document.segments.isEmpty()) return lyrics
        val translated = mutableMapOf<Int, String>()
        document.segments.chunkedByBudget().forEach { batch ->
            val batchTranslations =
                AiTextService.translateLines(
                    config = config,
                    targetLanguage = normalizeTargetLanguage(targetLanguage),
                    lines = batch.map { it.text },
                    formatName = document.formatName,
                    customPrompt = customPrompt,
                )
            batch.forEachIndexed { index, segment ->
                translated[segment.id] = batchTranslations[index]
            }
        }
        return document.rebuild(translated)
    }

    private fun List<AiLyricsSegment>.chunkedByBudget(): List<List<AiLyricsSegment>> {
        val chunks = ArrayList<List<AiLyricsSegment>>()
        val current = ArrayList<AiLyricsSegment>()
        var currentChars = 0
        forEach { segment ->
            val nextSize = currentChars + segment.text.length
            if (current.isNotEmpty() && (current.size >= MaxItemsPerBatch || nextSize > MaxCharsPerBatch)) {
                chunks.add(current.toList())
                current.clear()
                currentChars = 0
            }
            current.add(segment)
            currentChars += segment.text.length
        }
        if (current.isNotEmpty()) chunks.add(current.toList())
        return chunks
    }

    private fun normalizeTargetLanguage(language: String): String =
        language
            .ifBlank { "ENGLISH" }
            .replace('_', ' ')
            .lowercase()
            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }

    private companion object {
        const val MaxItemsPerBatch = 80
        const val MaxCharsPerBatch = 6000
    }
}
