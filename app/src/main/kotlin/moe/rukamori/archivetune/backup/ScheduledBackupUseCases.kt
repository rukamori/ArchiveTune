/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.net.Uri
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class ObserveScheduledBackupSettingsUseCase
    @Inject
    constructor(
        private val repository: ScheduledBackupRepository,
    ) {
        operator fun invoke(): Flow<ScheduledBackupSettings?> = repository.observeSettings()
    }

class UpdateScheduledBackupUseCase
    @Inject
    constructor(
        private val repository: ScheduledBackupRepository,
        private val scheduler: ScheduledBackupScheduler,
    ) {
        suspend fun setEnabled(enabled: Boolean): ScheduledBackupSettings = repository.updateEnabled(enabled).also(scheduler::replace)

        suspend fun setFrequency(frequency: ScheduledBackupFrequency): ScheduledBackupSettings =
            repository.updateFrequency(frequency).also(scheduler::replace)

        suspend fun setCustomDate(epochDay: Long): ScheduledBackupSettings = repository.updateCustomDate(epochDay).also(scheduler::replace)

        suspend fun setDirectory(uri: Uri): ScheduledBackupSettings = repository.updateDirectory(uri).also(scheduler::replace)

        suspend fun setOverwrite(overwriteExisting: Boolean): ScheduledBackupSettings = repository.updateOverwrite(overwriteExisting)
    }

class CreateBackupUseCase
    @Inject
    constructor(
        private val repository: BackupArchiveRepository,
    ) {
        suspend operator fun invoke(
            uri: Uri,
            categories: Set<BackupArchiveCategory>,
            onProgress: (BackupArchiveProgress) -> Unit = {},
        ) = repository.createBackup(uri, categories, onProgress)
    }
