/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.db.entities.Song
import java.text.Normalizer
import java.util.Locale

object FuzzyMatcher {
    const val DEFAULT_THRESHOLD = 0.85

    private const val TITLE_WEIGHT = 0.6
    private const val ARTIST_WEIGHT = 0.4
    private const val WINKLER_PREFIX_LIMIT = 4
    private const val WINKLER_SCALING_FACTOR = 0.1
    private const val WINKLER_BOOST_THRESHOLD = 0.7

    private val combiningMarksRegex = Regex("\\p{M}+")
    private val nonAlphaNumericRegex = Regex("[^\\p{L}\\p{N}]+")
    private val whitespaceRegex = Regex("\\s+")

    fun normalize(value: String): String =
        Normalizer
            .normalize(value, Normalizer.Form.NFKD)
            .lowercase(Locale.ROOT)
            .replace(combiningMarksRegex, "")
            .replace(nonAlphaNumericRegex, " ")
            .trim()
            .replace(whitespaceRegex, " ")

    fun jaroWinkler(
        first: String,
        second: String,
    ): Double = jaroWinklerNormalized(normalize(first), normalize(second))

    fun songScore(
        candidate: Song,
        title: String,
        artists: List<String>,
    ): Double {
        val normalizedCandidateTitle = normalize(candidate.title)
        val normalizedTitle = normalize(title)
        if (normalizedCandidateTitle.isEmpty() || normalizedTitle.isEmpty()) return 0.0

        val titleScore = jaroWinklerNormalized(normalizedCandidateTitle, normalizedTitle)
        val candidateArtists = artistVariants(candidate.artists.map { it.name })
        val requestedArtists = artistVariants(artists)
        if (candidateArtists.isEmpty() || requestedArtists.isEmpty()) return titleScore

        val artistScore =
            candidateArtists.maxOf { candidateArtist ->
                requestedArtists.maxOf { requestedArtist ->
                    jaroWinklerNormalized(candidateArtist, requestedArtist)
                }
            }

        return (titleScore * TITLE_WEIGHT) + (artistScore * ARTIST_WEIGHT)
    }

    fun bestSongMatch(
        candidates: List<Song>,
        title: String,
        artists: List<String>,
        threshold: Double = DEFAULT_THRESHOLD,
    ): Song? {
        require(threshold in 0.0..1.0) { "Threshold must be between 0.0 and 1.0" }

        return candidates
            .asSequence()
            .map { candidate -> candidate to songScore(candidate, title, artists) }
            .maxByOrNull { (_, score) -> score }
            ?.takeIf { (_, score) -> isMatch(score, threshold) }
            ?.first
    }

    fun rankedSongMatches(
        candidates: List<Song>,
        title: String,
        artists: List<String>,
        limit: Int,
        minimumScore: Double = 0.45,
    ): List<Song> {
        require(limit >= 0) { "Limit must not be negative" }
        require(minimumScore in 0.0..1.0) { "Minimum score must be between 0.0 and 1.0" }
        if (limit == 0 || normalize(title).isEmpty()) return emptyList()

        return candidates
            .asSequence()
            .map { candidate -> candidate to songScore(candidate, title, artists) }
            .filter { (_, score) -> score >= minimumScore }
            .sortedByDescending { (_, score) -> score }
            .take(limit)
            .map { (candidate, _) -> candidate }
            .toList()
    }

    fun isMatch(
        score: Double,
        threshold: Double = DEFAULT_THRESHOLD,
    ): Boolean {
        require(threshold in 0.0..1.0) { "Threshold must be between 0.0 and 1.0" }
        return score.isFinite() && score >= threshold
    }

    private fun artistVariants(artists: List<String>): List<String> {
        val normalizedArtists = artists.map(::normalize).filter(String::isNotEmpty)
        if (normalizedArtists.isEmpty()) return emptyList()

        val variants = normalizedArtists.toMutableList()
        if (normalizedArtists.size > 1) {
            variants += normalizedArtists.joinToString(" ")
        }
        return variants.distinct()
    }

    private fun jaroWinklerNormalized(
        first: String,
        second: String,
    ): Double {
        if (first == second) return 1.0
        if (first.isEmpty() || second.isEmpty()) return 0.0

        val matchDistance = (maxOf(first.length, second.length) / 2 - 1).coerceAtLeast(0)
        val firstMatches = BooleanArray(first.length)
        val secondMatches = BooleanArray(second.length)
        var matches = 0

        first.indices.forEach { firstIndex ->
            val start = (firstIndex - matchDistance).coerceAtLeast(0)
            val end = (firstIndex + matchDistance + 1).coerceAtMost(second.length)

            for (secondIndex in start until end) {
                if (secondMatches[secondIndex] || first[firstIndex] != second[secondIndex]) continue
                firstMatches[firstIndex] = true
                secondMatches[secondIndex] = true
                matches++
                break
            }
        }

        if (matches == 0) return 0.0

        var transpositions = 0
        var secondIndex = 0
        first.indices.forEach { firstIndex ->
            if (!firstMatches[firstIndex]) return@forEach
            while (!secondMatches[secondIndex]) secondIndex++
            if (first[firstIndex] != second[secondIndex]) transpositions++
            secondIndex++
        }

        val matchesAsDouble = matches.toDouble()
        val jaro =
            (
                (matchesAsDouble / first.length) +
                    (matchesAsDouble / second.length) +
                    ((matchesAsDouble - (transpositions / 2.0)) / matchesAsDouble)
            ) / 3.0

        if (jaro <= WINKLER_BOOST_THRESHOLD) return jaro

        var prefixLength = 0
        val maximumPrefixLength = minOf(WINKLER_PREFIX_LIMIT, first.length, second.length)
        while (prefixLength < maximumPrefixLength && first[prefixLength] == second[prefixLength]) {
            prefixLength++
        }

        return jaro + (prefixLength * WINKLER_SCALING_FACTOR * (1.0 - jaro))
    }
}
