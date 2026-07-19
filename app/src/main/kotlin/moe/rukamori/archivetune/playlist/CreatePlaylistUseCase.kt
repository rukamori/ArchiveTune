/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playlist

import kotlinx.coroutines.CancellationException
import moe.rukamori.archivetune.repository.PlaylistCreationRepository
import timber.log.Timber
import javax.inject.Inject

data class CreatePlaylistOptions(
    val isSignedIn: Boolean,
    val isSyncEnabled: Boolean,
)

class GetCreatePlaylistOptionsUseCase
    @Inject
    constructor(
        private val repository: PlaylistCreationRepository,
    ) {
        suspend operator fun invoke(): CreatePlaylistOptions {
            val availability = repository.getAvailability()
            return CreatePlaylistOptions(
                isSignedIn = availability.isSignedIn,
                isSyncEnabled = availability.isSyncEnabled,
            )
        }
    }

class CreatePlaylistUseCase
    @Inject
    constructor(
        private val repository: PlaylistCreationRepository,
    ) {
        suspend operator fun invoke(
            name: String,
            syncRequested: Boolean,
        ) {
            val normalizedName = name.trim()
            require(normalizedName.isNotEmpty())

            val browseId =
                if (syncRequested) {
                    repository
                        .createRemotePlaylist(normalizedName)
                        .onFailure { error ->
                            if (error is CancellationException) throw error
                            Timber.w(error, "Remote playlist creation failed; creating a local playlist")
                        }.getOrNull()
                } else {
                    null
                }

            repository.createLocalPlaylist(
                name = normalizedName,
                browseId = browseId,
            )
        }
    }
