/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

@Immutable
data class Playlist(
    @Embedded
    val playlist: PlaylistEntity,
    val songCount: Int,
    @Relation(
        entity = SongEntity::class,
        entityColumn = "id",
        parentColumn = "id",
        projection = ["thumbnailUrl"],
        associateBy =
            Junction(
                value = PlaylistSongMapPreview::class,
                parentColumn = "playlistId",
                entityColumn = "songId",
            ),
    )
    val songThumbnails: List<String?>,
) : LocalItem() {
    override val id: String
        get() = playlist.id
    override val title: String
        get() = playlist.name
    override val thumbnailUrl: String?
        get() = null

    val thumbnails: List<String>
        get() {
            return if (playlist.thumbnailUrl != null) {
                listOf(playlist.thumbnailUrl)
            } else if (playlist.name.startsWith("Discover Settimanale", ignoreCase = true) ||
                playlist.name.startsWith("Weekly Discover", ignoreCase = true) ||
                playlist.name.startsWith("Discover Weekly", ignoreCase = true)
            ) {
                listOf("res://discover_weekly_cover")
            } else if (playlist.name.startsWith("Best of Discover", ignoreCase = true)) {
                listOf("res://best_of_discover_cover")
            } else {
                songThumbnails.filterNotNull()
            }
        }
}
