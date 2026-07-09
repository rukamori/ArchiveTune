/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Cached beat-grid analysis for a track, produced by BeatAnalyzer and consumed by the
 * Automix crossfade planner. Beat k of the track is at firstBeatOffsetMs + k * (60000 / bpm).
 *
 * A row with bpm <= 0 is a negative cache: the track was analyzed and yielded no usable
 * beat grid (too short, beatless, or undecodable) — don't re-analyze it.
 */
@Entity(tableName = "beat_info")
data class BeatInfoEntity(
    @PrimaryKey val id: String,
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val analyzedAt: Long = System.currentTimeMillis(),
    /** First sustained-energy downbeat past the intro; -1 when scanned and none found. */
    val mixInPointMs: Long? = null,
    /** Where the track's body ends (outro starts); -1 when scanned and none found. */
    val mixOutPointMs: Long? = null,
)
