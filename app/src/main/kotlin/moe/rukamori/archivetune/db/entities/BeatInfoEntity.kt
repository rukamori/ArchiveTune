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
    /** Musical key: 0-11 major C..B, 12-23 minor C..B; null when unknown/too weak. */
    val keyIndex: Int? = null,
    val keyConfidence: Float? = null,
    /** Fraction of spectral energy below 250Hz; null when not analyzed. */
    val bassFraction: Float? = null,
    /** Dominant 500-4000Hz presence-band frequency; null when not analyzed. */
    val midPeakHz: Float? = null,
    /** Analyzer version that produced this row; null = legacy pre-key/spectral analysis. */
    val analysisVersion: Int? = null,
)
