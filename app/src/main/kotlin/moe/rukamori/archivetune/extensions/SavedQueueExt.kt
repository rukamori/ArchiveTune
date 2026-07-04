/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.extensions

import moe.rukamori.archivetune.db.entities.SavedQueueEntity
import moe.rukamori.archivetune.models.PersistQueue
import moe.rukamori.archivetune.models.QueueSnapshot
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream
import java.time.LocalDateTime

/**
 * Serializes this [PersistQueue] (song order, metadata, playing index/position, queue type) into
 * a byte array so it can be stored as a BLOB column in Room.
 */
fun PersistQueue.toByteArray(): ByteArray =
    ByteArrayOutputStream().use { bos ->
        ObjectOutputStream(bos).use { it.writeObject(this) }
        bos.toByteArray()
    }

/**
 * Reverses [PersistQueue.toByteArray]. Returns null (instead of throwing) if the payload is
 * corrupted or was written by an incompatible app version, so a single broken saved queue can
 * never crash the saved-queues list.
 */
fun ByteArray.toPersistQueue(): PersistQueue? =
    runCatching {
        ByteArrayInputStream(this).use { bis ->
            ObjectInputStream(bis).use { it.readObject() as PersistQueue }
        }
    }.getOrNull()

/**
 * Builds a new, never-before-persisted [SavedQueueEntity] out of a live [QueueSnapshot], using
 * [name] as the user-facing title (this also becomes [PersistQueue.title] for consistency with
 * how the queue title is displayed once restored).
 */
fun QueueSnapshot.toSavedQueueEntity(name: String): SavedQueueEntity {
    val titledQueue = persistQueue.copy(title = name)
    val now = LocalDateTime.now()
    return SavedQueueEntity(
        name = name,
        createdAt = now,
        lastUpdateTime = now,
        songCount = titledQueue.items.size,
        thumbnailUrl =
            titledQueue.items
                .getOrNull(titledQueue.mediaItemIndex)
                ?.thumbnailUrl
                ?: titledQueue.items.firstOrNull()?.thumbnailUrl,
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled,
        queueData = titledQueue.toByteArray(),
    )
}

/** Decodes the stored [SavedQueueEntity.queueData] back into a [PersistQueue], or null if corrupted. */
fun SavedQueueEntity.toPersistQueue(): PersistQueue? = queueData.toPersistQueue()
