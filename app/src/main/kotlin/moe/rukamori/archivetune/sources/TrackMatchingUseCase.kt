package moe.rukamori.archivetune.sources

import java.text.Normalizer
import java.util.Locale
import javax.inject.Inject
import kotlin.math.abs

class TrackMatchingUseCase @Inject constructor() {
    fun ranked(target: TrackIdentity, candidates: List<SourceCandidate>): List<SourceCandidate> {
        if (target.excluded || target.title.isBlank() || target.artist.isBlank()) return emptyList()
        val ranked = candidates.distinctBy { it.moduleId to it.id }.mapNotNull { candidate ->
            score(target, candidate.identity)?.let { candidate to it }
        }.sortedByDescending { it.second }
        val first = ranked.firstOrNull() ?: return emptyList()
        val tied = ranked.takeWhile { it.second == first.second }
        if (tied.map { normalize(it.first.identity.album.orEmpty()) }.filter(String::isNotEmpty).distinct().size > 1) return emptyList()
        return ranked.map { it.first }
    }

    private fun score(wanted: TrackIdentity, found: TrackIdentity): Int? {
        if (title(wanted.title) != title(found.title) || title(wanted.title).isBlank()) return null
        if (versions(wanted.title) != versions(found.title)) return null
        val artists = artistNames(wanted.artist)
        if (artists.isEmpty() || artists.intersect(artistNames(found.artist)).isEmpty()) return null
        if (wanted.explicit != null && found.explicit != null && wanted.explicit != found.explicit) return null
        val difference = if (wanted.durationSeconds != null && found.durationSeconds != null) {
            abs(wanted.durationSeconds - found.durationSeconds).also { if (it > 5) return null }
        } else null
        val album = wanted.album?.let(::normalize)?.takeIf(String::isNotBlank)
        return (if (album != null && album == normalize(found.album.orEmpty())) 100 else 0) +
            (difference?.let { 20 - it } ?: 0) +
            (if (normalize(wanted.artist) == normalize(found.artist)) 10 else 0)
    }

    private fun title(value: String): String = normalize(value.replace(featured, " ")).split(' ').filterNot { it in noise }.joinToString(" ")
    private fun versions(value: String): Set<String> = normalize(value).split(' ').filter { it in versionWords }.toSet()
    private fun artistNames(value: String): Set<String> = value.split(artistSeparator).map(::normalize).filter(String::isNotBlank).toSet()
    private fun normalize(value: String): String = Normalizer.normalize(value, Normalizer.Form.NFKC)
        .lowercase(Locale.ROOT).replace(nonWord, " ").trim().replace(spaces, " ")

    private companion object {
        val featured = Regex("""\s*[(\[]?\b(?:feat|ft|featuring)\.?\s+[^)\]]+[)\]]?""", RegexOption.IGNORE_CASE)
        val nonWord = Regex("[^\\p{L}\\p{N}]+")
        val spaces = Regex("\\s+")
        val artistSeparator = Regex("\\s*(?:[,;&/]|\\bfeat\\.?|\\bft\\.?|\\bfeaturing\\b|\\band\\b)\\s*", RegexOption.IGNORE_CASE)
        val noise = setOf("official", "audio", "video", "lyrics", "lyric", "visualizer", "music", "hd", "hq", "4k")
        val versionWords = setOf("live", "remix", "remastered", "remaster", "acoustic", "cover", "instrumental", "karaoke", "demo", "sped", "slowed", "nightcore", "extended", "edit", "clean")
    }
}
