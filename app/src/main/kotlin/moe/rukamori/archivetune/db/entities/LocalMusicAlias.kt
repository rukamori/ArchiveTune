/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.Index

@Entity(tableName = "local_music_alias", primaryKeys = ["sourceId", "kind"], indices = [Index("targetId")])
data class LocalMusicAlias(
    val sourceId: String,
    val targetId: String,
    val kind: String,
)
