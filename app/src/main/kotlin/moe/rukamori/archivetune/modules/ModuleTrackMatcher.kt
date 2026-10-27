/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.modules

import moe.rukamori.archivetune.utils.FuzzyMatcher
import kotlin.math.abs

internal object ModuleTrackMatcher {
    data class Evaluation(val score: Double, val rejection: ModuleMatchRejection? = null)

    private val bracketedFeature = Regex("""(?:\(|\[)\s*(?:feat\.?|ft\.?|featuring)\s+[^)\]]*(?:\)|\])""", RegexOption.IGNORE_CASE)
    private val trailingFeature = Regex("""\s+(?:feat\.?|ft\.?|featuring)\s+.+?(?=\s+[-\u2013\u2014]\s+|[\[(]|$)""", RegexOption.IGNORE_CASE)
    private val presentation = Regex("""[\[(]\s*(?:(?:official\s+)?(?:music\s+)?(?:audio|video)|lyrics?|lyric\s+video|hd|hq)\s*[\])]""", RegexOption.IGNORE_CASE)
    private val creditSeparator = Regex("""\s*(?:,|;|\s+(?:feat\.?|ft\.?|featuring|with)\s+|\s+&\s+)\s*""", RegexOption.IGNORE_CASE)
    private val recordingMarkers = listOf("live", "remix", "cover", "karaoke", "instrumental", "acoustic", "sped up", "slowed",
        "remaster", "remastered", "radio edit", "extended", "demo", "clean", "mono", "stereo")
        .map { Regex("(?:^| )${Regex.escape(it)}(?: |$)") }

    private fun title(value: String): String = value.replace(bracketedFeature, " ").replace(trailingFeature, " ").replace(presentation, " ").trim()

    fun query(target: ModuleMatchMetadata): String = "${title(target.title)} ${target.artists.firstOrNull { it.isNotBlank() }.orEmpty()}".trim()

    fun evaluate(target: ModuleMatchMetadata, track: ModuleTrack): Evaluation {
        if (track.title.isBlank() || track.artists.none(String::isNotBlank)) return Evaluation(0.0, ModuleMatchRejection.METADATA)
        val targetTitle = FuzzyMatcher.normalize(title(target.title))
        val candidateTitle = FuzzyMatcher.normalize(title(track.title))
        val titleScore = FuzzyMatcher.jaroWinkler(targetTitle, candidateTitle)
        val primaryArtist = target.artists.firstOrNull { it.isNotBlank() }.orEmpty().replace(trailingFeature, "")
        val artistScore = track.artists.flatMap { listOf(it) + it.split(creditSeparator) }.filter(String::isNotBlank)
            .maxOfOrNull { FuzzyMatcher.jaroWinkler(primaryArtist, it) } ?: 0.0
        val album = track.album
        val albumScore = if (!target.album.isNullOrBlank() && !album.isNullOrBlank()) FuzzyMatcher.jaroWinkler(target.album, album) else 0.0
        val score = titleScore * 0.6 + artistScore * 0.35 + albumScore * 0.05
        val candidateDuration = track.durationSeconds
        val rejection = when {
            targetTitle.isBlank() || candidateTitle.isBlank() -> ModuleMatchRejection.METADATA
            recordingMarkers.any { it.containsMatchIn(targetTitle) != it.containsMatchIn(candidateTitle) } -> ModuleMatchRejection.RECORDING
            titleScore < 0.9 -> ModuleMatchRejection.TITLE
            artistScore < 0.9 -> ModuleMatchRejection.ARTIST
            recordingMarkers.any { it.containsMatchIn(targetTitle) } && titleScore != 1.0 -> ModuleMatchRejection.RECORDING
            target.explicit != null && track.explicit != null && target.explicit != track.explicit -> ModuleMatchRejection.EXPLICIT
            target.duration > 0 && candidateDuration != null -> {
                if (abs(target.duration - candidateDuration) > maxOf(5.0, target.duration * 0.05)) ModuleMatchRejection.DURATION else null
            }
            titleScore != 1.0 || artistScore != 1.0 -> ModuleMatchRejection.METADATA
            else -> null
        }
        return Evaluation(score, rejection)
    }
}
