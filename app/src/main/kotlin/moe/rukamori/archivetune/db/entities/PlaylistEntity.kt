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
import androidx.room.PrimaryKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.innertube.YouTube
import org.apache.commons.lang3.RandomStringUtils
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "playlist")
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isEditable", defaultValue = true.toString())
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    val customOrder: Int? = null,
    @ColumnInfo(name = "isLocal", defaultValue = "0")
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isAutoSync", defaultValue = "0")
    val isAutoSync: Boolean = false,
    @ColumnInfo(defaultValue = "NULL")
    val songSortType: String? = null,
    @ColumnInfo(defaultValue = "NULL")
    val songSortDescending: Boolean? = null,
    @ColumnInfo(name = "isHidden", defaultValue = "0")
    val isHidden: Boolean = false,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

        fun generatePlaylistId() = "LP" + RandomStringUtils.insecure().next(8, true, false)
    }

    val shareLink: String?
        get() {
            return if (browseId != null) {
                "https://music.youtube.com/playlist?list=$browseId"
            } else {
                null
            }
        }

    fun localToggleLike() =
        copy(
            bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now(),
        )

    fun toggleLike() =
        localToggleLike().also {
            if (isLocal) return@also
            CoroutineScope(Dispatchers.IO).launch {
                if (browseId != null) {
                    YouTube.likePlaylist(browseId, bookmarkedAt == null)
                }
                this.cancel()
            }
        }
}
