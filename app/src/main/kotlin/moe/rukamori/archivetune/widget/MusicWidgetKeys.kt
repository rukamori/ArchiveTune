/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.widget

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

object MusicWidgetKeys {
    val TRACK_TITLE = stringPreferencesKey("widget_track_title")
    val TRACK_ARTIST = stringPreferencesKey("widget_track_artist")
    val ART_PATH = stringPreferencesKey("widget_art_path")
    val IS_PLAYING = booleanPreferencesKey("widget_is_playing")
    val IS_AVAILABLE = booleanPreferencesKey("widget_is_available")
    val DOMINANT_COLOR = intPreferencesKey("widget_dominant_color")
    val PLAYBACK_POSITION = floatPreferencesKey("widget_position")
    val LISTENING_TIME = stringPreferencesKey("widget_listening_time")
    val TOTAL_PLAYS = stringPreferencesKey("widget_total_plays")
    val RECENT_SONGS = stringPreferencesKey("widget_recent_songs")
    val GENRES = stringPreferencesKey("widget_genres")
    val RECOMMENDATIONS = stringPreferencesKey("widget_recommendations")
    val TOP_SONG_SUMMARY = stringPreferencesKey("widget_top_song_summary")
}
