/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.library

import javax.inject.Inject
import kotlinx.coroutines.flow.Flow
import moe.rukamori.archivetune.repository.LibraryTopMixRepository

class ObserveLibraryTopMixesUseCase
@Inject
constructor(
    private val repository: LibraryTopMixRepository,
) {
    operator fun invoke(): Flow<List<LibraryTopMix>> =
        repository.observePersistedTopMixes()
}
