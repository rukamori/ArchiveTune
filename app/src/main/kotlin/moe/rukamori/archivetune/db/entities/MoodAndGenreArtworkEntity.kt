/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity

@Entity(
    tableName = "mood_genre_artwork",
    primaryKeys = ["browseId", "params"]
)
data class MoodAndGenreArtworkEntity(
    val browseId: String,
    val params: String,
    val thumbnailUrl: String,
    val cachedAt: Long = System.currentTimeMillis()
)
