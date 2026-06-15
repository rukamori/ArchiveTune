/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val id: String,
    val lyrics: String,
    @ColumnInfo(defaultValue = "'REMOTE'")
    val source: String = Source.REMOTE.value,
    @ColumnInfo(defaultValue = "0")
    val updatedAt: Long = System.currentTimeMillis(),
) {
    fun hasGenericSource(): Boolean =
        source == Source.REMOTE.value ||
            source == Source.USER_SELECTION.value ||
            source == Source.EMBEDDED.value

    companion object {
        const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"
    }

    enum class Source(val value: String) {
        REMOTE("REMOTE"),
        EMBEDDED("EMBEDDED"),
        USER_SELECTION("USER_SELECTION"),
        USER_EDIT("USER_EDIT"),
        AI_TRANSLATION("AI_TRANSLATION"),
    }
}
