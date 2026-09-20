/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.updates

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import dagger.hilt.android.qualifiers.ApplicationContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.utils.UpdateCheckWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UpdateCheckScheduler
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun replace(enabled: Boolean) {
            if (enabled && BuildConfig.UPDATER_AVAILABLE) {
                schedule(context)
            } else {
                cancel(context)
            }
        }

        companion object {
            private const val WORK_NAME = "update_check_work"

            fun schedule(context: Context) {
                if (!BuildConfig.UPDATER_AVAILABLE) {
                    cancel(context)
                    return
                }

                val constraints =
                    Constraints
                        .Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                val request =
                    PeriodicWorkRequestBuilder<UpdateCheckWorker>(
                        6,
                        TimeUnit.HOURS,
                        30,
                        TimeUnit.MINUTES,
                    ).setConstraints(constraints)
                        .build()

                WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                    WORK_NAME,
                    ExistingPeriodicWorkPolicy.KEEP,
                    request,
                )
            }

            fun cancel(context: Context) {
                WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
            }
        }
    }
