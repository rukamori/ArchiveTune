/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.models

/**
 * A full, self-contained capture of the player's current queue at a point in time: item order,
 * currently playing index/position, and playback modifiers (shuffle/repeat). Produced by
 * `MusicService.captureQueueSnapshot` and consumed by the "Save Queue" feature to create a
 * [moe.rukamori.archivetune.db.entities.SavedQueueEntity].
 */
data class QueueSnapshot(
    val persistQueue: PersistQueue,
    val repeatMode: Int,
    val shuffleModeEnabled: Boolean,
)
