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
import androidx.room.Index
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import java.time.LocalDateTime

@Immutable
@Entity(
    tableName = "song",
    indices = [
        Index(
            value = ["albumId"],
        ),
    ],
)
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1, // in seconds
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0")
    val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null, // ID3 tag property
    val dateModified: LocalDateTime? = null, // file property
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0, // in milliseconds
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isMusicVideo", defaultValue = "0")
    val isMusicVideo: Boolean = false,
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
) {
    fun localToggleLike() =
        copy(
            liked = !liked,
            likedDate = if (!liked) LocalDateTime.now() else null,
        )

    fun toggleLike() =
        if (isLocal) {
            localToggleLike()
        } else {
            copy(
                liked = !liked,
                likedDate = if (!liked) LocalDateTime.now() else null,
                inLibrary = if (!liked) inLibrary ?: LocalDateTime.now() else inLibrary,
            ).also {
                CoroutineScope(Dispatchers.IO).launch {
                    YouTube.likeVideo(id, !liked)
                    this.cancel()
                }
            }
        }

    fun toggleLibrary() =
        copy(
            liked = if (inLibrary == null) liked else false,
            inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
            likedDate = if (inLibrary == null) likedDate else null,
        )
}
