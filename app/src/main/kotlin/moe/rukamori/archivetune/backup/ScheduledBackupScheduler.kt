/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.backup

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.LocalDate
import java.time.ZonedDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScheduledBackupScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun replace(settings: ScheduledBackupSettings) {
            val workManager = WorkManager.getInstance(context)
            val runAt =
                nextRunAt(settings) ?: run {
                    workManager.cancelUniqueWork(WORK_NAME)
                    return
                }
            workManager.enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                buildRequest(runAt),
            )
        }

        fun appendNext(settings: ScheduledBackupSettings) {
            if (settings.frequency == ScheduledBackupFrequency.CUSTOM) return
            val runAt = nextRunAt(settings) ?: return
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildRequest(runAt),
            )
        }

        private fun buildRequest(runAt: ZonedDateTime) =
            OneTimeWorkRequestBuilder<ScheduledBackupWorker>()
                .setConstraints(
                    Constraints
                        .Builder()
                        .setRequiresBatteryNotLow(true)
                        .setRequiresStorageNotLow(true)
                        .build(),
                ).setInitialDelay(
                    Duration.between(ZonedDateTime.now(), runAt).toMillis().coerceAtLeast(MINIMUM_DELAY_MS),
                    TimeUnit.MILLISECONDS,
                ).build()

        private fun nextRunAt(settings: ScheduledBackupSettings): ZonedDateTime? {
            if (!settings.enabled || settings.directoryUri == null) return null
            val now = ZonedDateTime.now()
            val nextDate =
                when (settings.frequency) {
                    ScheduledBackupFrequency.DAILY -> {
                        now.toLocalDate().plusDays(1)
                    }

                    ScheduledBackupFrequency.WEEKLY -> {
                        now.toLocalDate().plusWeeks(1)
                    }

                    ScheduledBackupFrequency.MONTHLY -> {
                        now.toLocalDate().plusMonths(1)
                    }

                    ScheduledBackupFrequency.CUSTOM -> {
                        val epochDay = settings.customDateEpochDay ?: return null
                        LocalDate.ofEpochDay(epochDay).takeUnless { it.isBefore(now.toLocalDate()) } ?: return null
                    }
                }
            return nextDate.atTime(BACKUP_HOUR, 0).atZone(now.zone)
        }

        companion object {
            private const val WORK_NAME = "scheduled_auto_backup"
            private const val BACKUP_HOUR = 2
            private const val MINIMUM_DELAY_MS = 1_000L
        }
    }
