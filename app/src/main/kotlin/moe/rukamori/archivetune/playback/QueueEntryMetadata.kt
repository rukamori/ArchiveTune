/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.os.Bundle
import androidx.media3.common.MediaItem
import java.util.UUID

private const val ENTRY_ID = "archivetune.queue.entry"
private const val RECOMMENDATION = "archivetune.queue.recommendation"

internal val MediaItem.queueEntryId: String?
    get() = mediaMetadata.extras?.getString(ENTRY_ID)

internal val MediaItem.isQueueRecommendation: Boolean
    get() = mediaMetadata.extras?.getBoolean(RECOMMENDATION, false) == true

internal fun MediaItem.withQueueEntry(
    recommended: Boolean = isQueueRecommendation,
    id: String = queueEntryId ?: UUID.randomUUID().toString(),
): MediaItem = buildUpon().setMediaMetadata(
    mediaMetadata.buildUpon().setExtras(
        Bundle(mediaMetadata.extras ?: Bundle()).apply {
            putString(ENTRY_ID, id)
            putBoolean(RECOMMENDATION, recommended)
        },
    ).build(),
).build()

