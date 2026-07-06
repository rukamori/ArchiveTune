/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.artist

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.db.entities.ArtistEntity
import java.time.LocalDateTime
import javax.inject.Inject

@Immutable
data class ArtistBlockRequest(
    val id: String,
    val name: String,
    val channelId: String?,
    val thumbnailUrl: String?,
    val blocked: Boolean,
)

class ArtistBlockRepository
    @Inject
    constructor(
        private val database: MusicDatabase,
    ) {
        fun observeBlocked(artistId: String): Flow<Boolean?> =
            database
                .artist(artistId)
                .map { artist -> artist?.artist?.blockedAt?.let { true } ?: artist?.let { false } }
                .distinctUntilChanged()

        suspend fun setBlocked(request: ArtistBlockRequest) =
            withContext(Dispatchers.IO) {
                database.withTransaction {
                    val current = getArtistById(request.id)
                    val updated =
                        (
                            current ?: ArtistEntity(
                                id = request.id,
                                name = request.name,
                                channelId = request.channelId,
                                thumbnailUrl = request.thumbnailUrl,
                            )
                        ).copy(
                            name = request.name,
                            channelId = request.channelId ?: current?.channelId,
                            thumbnailUrl = request.thumbnailUrl ?: current?.thumbnailUrl,
                            blockedAt = if (request.blocked) LocalDateTime.now() else null,
                        )
                    upsert(updated)
                }
            }
    }
