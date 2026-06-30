/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import moe.rukamori.archivetune.constants.PlaylistSuggestionSource
import moe.rukamori.archivetune.db.entities.PlaylistSong
import moe.rukamori.archivetune.models.PlaylistSuggestionQuery
import java.time.LocalDateTime
import java.time.Year

object PlaylistSuggestionQueryBuilder {
    private val genreKeywords =
        listOf(
            "pop",
            "rock",
            "hip hop",
            "rap",
            "electronic",
            "dance",
            "jazz",
            "blues",
            "country",
            "folk",
            "classical",
            "metal",
            "punk",
            "indie",
            "alternative",
            "r&b",
            "soul",
            "reggae",
            "ska",
            "latin",
            "k-pop",
            "j-pop",
            "house",
            "techno",
            "ambient",
            "experimental",
        )

    private val moodKeywords =
        listOf(
            "chill",
            "upbeat",
            "energetic",
            "relaxing",
            "romantic",
            "sad",
            "happy",
            "angry",
            "motivational",
            "calm",
            "intense",
            "dreamy",
            "nostalgic",
            "mysterious",
            "epic",
        )

    private val fallbackQueries =
        listOf(
            "trending songs",
            "popular music",
            "new music",
            "top hits",
            "music ${Year.now().value}",
            "viral songs",
            "best songs",
            "music discovery",
            "fresh music",
            "recommended songs",
        )

    fun buildSuggestionQueries(
        playlistName: String,
        playlistSongs: List<PlaylistSong>,
        suggestionSource: PlaylistSuggestionSource = PlaylistSuggestionSource.BOTH,
    ): List<PlaylistSuggestionQuery> {
        val queries = mutableListOf<PlaylistSuggestionQuery>()
        val includeTitleSignals = suggestionSource != PlaylistSuggestionSource.PLAYLIST_CONTENT
        val includePlaylistContentSignals = suggestionSource != PlaylistSuggestionSource.PLAYLIST_TITLE
        val nameGenres = if (includeTitleSignals) extractGenres(playlistName) else emptyList()
        val nameMoods = if (includeTitleSignals) extractMoods(playlistName) else emptyList()
        val artists = if (includePlaylistContentSignals) extractArtists(playlistSongs) else emptyList()

        if (includeTitleSignals && playlistName.isNotBlank()) {
            queries.add(PlaylistSuggestionQuery(playlistName, 1))
        }

        nameGenres.forEach { genre ->
            queries.add(PlaylistSuggestionQuery("$genre music", 2))
        }

        nameMoods.forEach { mood ->
            queries.add(PlaylistSuggestionQuery("$mood music", 3))
        }

        artists.take(3).forEach { artist ->
            queries.add(PlaylistSuggestionQuery("songs like $artist", 4))
        }

        if (includeTitleSignals && includePlaylistContentSignals) {
            nameGenres.take(2).forEach { genre ->
                artists.take(2).forEach { artist ->
                    queries.add(PlaylistSuggestionQuery("$genre music like $artist", 5))
                }
            }

            artists.take(2).forEach { artist ->
                nameMoods.take(2).forEach { mood ->
                    queries.add(PlaylistSuggestionQuery("$mood songs by $artist", 6))
                }
            }
        }

        val timeQuery = getTimeBasedQuery()
        queries.add(PlaylistSuggestionQuery(timeQuery, 7))

        val shuffledFallbacks = fallbackQueries.shuffled()
        shuffledFallbacks.forEachIndexed { index, query ->
            queries.add(PlaylistSuggestionQuery(query, 8 + index))
        }

        return queries.sortedBy { it.priority }
    }

    private fun extractGenres(text: String): List<String> {
        val lowerText = text.lowercase()
        return genreKeywords.filter { genre ->
            lowerText.contains(genre, ignoreCase = true)
        }
    }

    private fun extractMoods(text: String): List<String> {
        val lowerText = text.lowercase()
        return moodKeywords.filter { mood ->
            lowerText.contains(mood, ignoreCase = true)
        }
    }

    private fun extractArtists(playlistSongs: List<PlaylistSong>): List<String> =
        playlistSongs
            .flatMap { it.song.artists }
            .map { it.name }
            .groupBy { it }
            .mapValues { it.value.size }
            .entries
            .sortedByDescending { it.value }
            .map { it.key }
            .filter { it.isNotBlank() }
            .distinct()
            .shuffled()
            .take(5)

    private fun getTimeBasedQuery(): String {
        val currentHour = LocalDateTime.now().hour

        return when (currentHour) {
            in 6..11 -> "morning music"
            in 12..17 -> "afternoon vibes"
            in 18..22 -> "evening music"
            else -> "late night music"
        }
    }

    fun shouldRefreshSuggestions(
        lastTimestamp: Long,
        cacheExpiryHours: Long = 12,
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        val timeDiff = currentTime - lastTimestamp
        val expiryTime = cacheExpiryHours * 60 * 60 * 1000 // Convert hours to milliseconds

        return timeDiff > expiryTime
    }
}
