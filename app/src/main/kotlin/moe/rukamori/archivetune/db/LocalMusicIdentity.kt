/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db

import java.text.Normalizer
import java.util.Locale

object LocalMusicIdentity {
    private val whitespace = Regex("\\s+")
    private val exportedDownloadSuffix = Regex("""\s\[([^\[\]]+)]\.[^.]+$""")

    fun normalize(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFKC)
            .trim()
            .replace(whitespace, " ")
            .lowercase(Locale.ROOT)

    fun downloadIdFromExportedFilename(fileName: String): String? =
        exportedDownloadSuffix.find(fileName)?.groupValues?.getOrNull(1)?.takeIf(String::isNotBlank)

    fun trackKey(
        title: String,
        artists: Iterable<String>,
        durationSeconds: Int,
        fallbackId: String,
    ): String {
        val normalizedTitle = normalize(title)
        val normalizedArtists = artists.map(::normalize).filter(String::isNotEmpty).sorted()
        if (normalizedTitle.isEmpty() || normalizedArtists.isEmpty() || durationSeconds <= 0) {
            return "id:$fallbackId"
        }
        return listOf(
            normalizedTitle,
            normalizedArtists.joinToString("\u0000"),
            durationSeconds.toString(),
        ).joinToString("\u0001")
    }
}
