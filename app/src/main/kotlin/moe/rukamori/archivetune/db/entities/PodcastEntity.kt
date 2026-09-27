/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "podcast",
    indices = [Index(value = ["playlistId"], unique = true)],
)
data class PodcastEntity(
    @PrimaryKey val browseId: String,
    val playlistId: String?,
    val title: String,
    val authorName: String?,
    val authorId: String?,
    val thumbnailUrl: String?,
    val localSavedAt: LocalDateTime?,
    val remoteSavedAt: LocalDateTime?,
    val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
) {
    val isSaved: Boolean
        get() = localSavedAt != null || remoteSavedAt != null
}
