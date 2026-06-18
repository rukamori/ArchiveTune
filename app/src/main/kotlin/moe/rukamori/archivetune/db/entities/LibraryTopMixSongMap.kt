/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey

@Immutable
@Entity(
    tableName = "library_top_mix_song_map",
    primaryKeys = ["mixId", "position"],
    foreignKeys = [
        ForeignKey(
            entity = LibraryTopMixEntity::class,
            parentColumns = ["id"],
            childColumns = ["mixId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = SongEntity::class,
            parentColumns = ["id"],
            childColumns = ["songId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class LibraryTopMixSongMap(
    @ColumnInfo(index = true) val mixId: String,
    @ColumnInfo(index = true) val songId: String,
    val position: Int,
)
