/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveUpdateSettingsUseCase
    @Inject
    constructor(
        private val repository: UpdateSettingsRepository,
    ) {
        operator fun invoke(): Flow<UpdateSettings> = repository.observeSettings()
    }

class UpdateUpdateSettingsUseCase
    @Inject
    constructor(
        private val repository: UpdateSettingsRepository,
        private val scheduler: UpdateCheckScheduler,
    ) {
        suspend fun setAutomaticChecksEnabled(enabled: Boolean) {
            repository.setAutomaticChecksEnabled(enabled)
            scheduler.replace(enabled)
        }

        suspend fun setNotificationsEnabled(enabled: Boolean) {
            repository.setNotificationsEnabled(enabled)
        }
    }
