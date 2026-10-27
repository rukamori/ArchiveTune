/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.datastore.preferences.core.Preferences
import moe.rukamori.archivetune.R

@Immutable
internal data class WidgetInsightsSnapshot(
    val listeningTime: String,
    val totalPlays: String,
    val recentSongs: List<String>,
    val genres: List<String>,
    val recommendations: List<String>,
    val topSongSummary: String?,
) {
    companion object {
        val Empty =
            WidgetInsightsSnapshot(
                listeningTime = "",
                totalPlays = "",
                recentSongs = emptyList(),
                genres = emptyList(),
                recommendations = emptyList(),
                topSongSummary = null,
            )
    }
}

@Immutable
internal data class WidgetInsightSection(
    @StringRes val title: Int,
    @StringRes val emptyMessage: Int,
    val lines: List<String>,
)

@Immutable
internal data class WidgetInsightsState(
    val listeningTime: String,
    val totalPlays: String,
    val recentSongs: WidgetInsightSection,
    val genres: WidgetInsightSection,
    val recommendations: WidgetInsightSection,
    val topSongSummary: String?,
)

internal fun Preferences.toWidgetInsightsState(context: Context): WidgetInsightsState =
    WidgetInsightsState(
        listeningTime =
            this[MusicWidgetKeys.LISTENING_TIME]
                ?.takeIf { it.isNotBlank() }
                ?: context.getString(R.string.widget_listening_time_empty),
        totalPlays =
            this[MusicWidgetKeys.TOTAL_PLAYS]
                ?.takeIf { it.isNotBlank() }
                ?: context.resources.getQuantityString(R.plurals.widget_total_plays, 0, 0),
        recentSongs = WidgetInsightSection(
            title = R.string.recently_played,
            emptyMessage = R.string.widget_no_recent_songs,
            lines = this[MusicWidgetKeys.RECENT_SONGS].toWidgetList(),
        ),
        genres = WidgetInsightSection(
            title = R.string.mood_and_genres,
            emptyMessage = R.string.widget_no_genres,
            lines = this[MusicWidgetKeys.GENRES].toWidgetList(),
        ),
        recommendations = WidgetInsightSection(
            title = R.string.widget_recommendations,
            emptyMessage = R.string.widget_no_recommendations,
            lines = this[MusicWidgetKeys.RECOMMENDATIONS].toWidgetList(),
        ),
        topSongSummary = this[MusicWidgetKeys.TOP_SONG_SUMMARY]?.takeIf { it.isNotBlank() },
    )

internal fun List<String>.toWidgetPreferenceValue(): String =
    joinToString(separator = WidgetListSeparator) { item ->
        item
            .replace(WidgetListSeparator, " ")
            .replace('\n', ' ')
            .trim()
    }

private fun String?.toWidgetList(): List<String> =
    orEmpty()
        .split(WidgetListSeparator)
        .map { it.trim() }
        .filter { it.isNotBlank() }

private const val WidgetListSeparator = "\u001F"
