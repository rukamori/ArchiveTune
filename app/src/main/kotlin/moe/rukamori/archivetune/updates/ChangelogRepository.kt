/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import moe.rukamori.archivetune.utils.ReleaseInfo
import moe.rukamori.archivetune.utils.Updater
import javax.inject.Inject

class ChangelogRepository @Inject constructor() {
    fun observeReleases(): Flow<List<ReleaseInfo>> =
        flow {
            val cachedReleases = Updater.getCachedReleases()
            if (cachedReleases.isNotEmpty()) emit(cachedReleases)
            emit(Updater.getAllReleases(forceRefresh = true).getOrThrow())
        }.flowOn(Dispatchers.IO)
}
