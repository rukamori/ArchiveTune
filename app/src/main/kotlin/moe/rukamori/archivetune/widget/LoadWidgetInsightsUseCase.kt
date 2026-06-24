/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.db.entities.Artist
import moe.rukamori.archivetune.db.entities.Song
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject

internal class LoadWidgetInsightsUseCase
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
        private val repository: WidgetInsightsRepository,
    ) {
        suspend operator fun invoke(nowMs: Long = System.currentTimeMillis()): WidgetInsightsSnapshot {
            val data = repository.load(nowMs)
            val recentSongIds = data.recentSongs.mapTo(HashSet()) { it.id }
            val recommendations =
                data.recommendations
                    .filterNot { it.id in recentSongIds }
                    .ifEmpty { data.recentSongs }
                    .map { it.toWidgetLine() }
                    .distinct()
                    .take(4)

            return WidgetInsightsSnapshot(
                listeningTime = formatListeningTime(data.totals.totalTimeListened),
                totalPlays =
                    context.resources.getQuantityString(
                        R.plurals.widget_total_plays,
                        data.totals.totalPlayCount,
                        data.totals.totalPlayCount,
                    ),
                recentSongs =
                    data.recentSongs
                        .map { it.toWidgetLine() }
                        .distinct()
                        .take(4),
                genres = data.extractGenres(),
                recommendations = recommendations,
                topSongSummary =
                    data.topSongs
                        .firstOrNull()
                        ?.title
                        ?.takeIf { it.isNotBlank() }
                        ?.let { context.getString(R.string.widget_top_song_summary, it) },
            )
        }

        private fun formatListeningTime(totalMs: Long): String {
            val totalMinutes = TimeUnit.MILLISECONDS.toMinutes(totalMs).coerceAtLeast(0L)
            if (totalMinutes <= 0L) return context.getString(R.string.widget_listening_time_empty)

            val hours = totalMinutes / 60L
            val minutes = totalMinutes % 60L
            return if (hours > 0L) {
                context.getString(R.string.widget_listening_time_hours_minutes, hours, minutes)
            } else {
                context.getString(R.string.widget_listening_time_minutes, minutes)
            }
        }

        private fun WidgetInsightsData.extractGenres(): List<String> {
            val textSignals =
                buildList {
                    topMixes.forEach { mix ->
                        add(mix.title)
                        add(mix.description)
                    }
                    recentSongs.forEach { song ->
                        add(song.song.title)
                        add(song.album?.title.orEmpty())
                        song.artists.forEach { artist -> add(artist.name) }
                    }
                    topArtists.forEach { artist -> add(artist.primaryName()) }
                }

            val haystack = textSignals.joinToString(" ").lowercase(Locale.ROOT)
            return genreKeywords
                .mapNotNull { genre ->
                    val matches = Regex("\\b${Regex.escape(genre.keyword)}\\b").findAll(haystack).count()
                    if (matches > 0) genre to matches else null
                }.sortedWith(compareByDescending<Pair<GenreKeyword, Int>> { it.second }.thenBy { it.first.label })
                .map { it.first.label }
                .distinct()
                .take(5)
        }

        private fun Song.toWidgetLine(): String {
            val artist = artists.firstOrNull()?.name.orEmpty()
            return if (artist.isBlank()) song.title else "${song.title} - $artist"
        }

        private fun Artist.primaryName(): String = artist.name

        private data class GenreKeyword(
            val keyword: String,
            val label: String,
        )

        private companion object {
            val genreKeywords =
                listOf(
                    GenreKeyword("pop", "Pop"),
                    GenreKeyword("rock", "Rock"),
                    GenreKeyword("hip hop", "Hip Hop"),
                    GenreKeyword("rap", "Rap"),
                    GenreKeyword("electronic", "Electronic"),
                    GenreKeyword("dance", "Dance"),
                    GenreKeyword("jazz", "Jazz"),
                    GenreKeyword("blues", "Blues"),
                    GenreKeyword("country", "Country"),
                    GenreKeyword("folk", "Folk"),
                    GenreKeyword("classical", "Classical"),
                    GenreKeyword("metal", "Metal"),
                    GenreKeyword("punk", "Punk"),
                    GenreKeyword("indie", "Indie"),
                    GenreKeyword("alternative", "Alternative"),
                    GenreKeyword("r&b", "R&B"),
                    GenreKeyword("soul", "Soul"),
                    GenreKeyword("reggae", "Reggae"),
                    GenreKeyword("ska", "Ska"),
                    GenreKeyword("latin", "Latin"),
                    GenreKeyword("k-pop", "K-Pop"),
                    GenreKeyword("j-pop", "J-Pop"),
                    GenreKeyword("house", "House"),
                    GenreKeyword("techno", "Techno"),
                    GenreKeyword("ambient", "Ambient"),
                    GenreKeyword("experimental", "Experimental"),
                )
        }
    }
