/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import moe.rukamori.archivetune.morideobfuscator.CipherRuntimeStatus
import moe.rukamori.archivetune.morideobfuscator.MoriCipherRuntime
import timber.log.Timber
import java.util.concurrent.TimeUnit

class MoriCipherUpdateWorker(
    context: Context,
    parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val update = MoriCipherRuntime.refresh(force = false)
        if (update.isSuccess) return Result.success()
        Timber.w(update.exceptionOrNull(), "Mori cipher periodic refresh failed")
        return if (MoriCipherRuntime.snapshot.value.status == CipherRuntimeStatus.DEGRADED) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}

object MoriCipherUpdateScheduler {
    private const val WorkName = "mori_cipher_player_refresh"
    private const val InitialWorkName = "mori_cipher_player_initial_refresh"

    fun schedule(context: Context) {
        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()
        val request =
            PeriodicWorkRequestBuilder<MoriCipherUpdateWorker>(
                6,
                TimeUnit.HOURS,
                30,
                TimeUnit.MINUTES,
            ).setConstraints(constraints)
                .build()
        val workManager = WorkManager.getInstance(context)
        workManager.enqueueUniqueWork(
            InitialWorkName,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<MoriCipherUpdateWorker>()
                .setConstraints(constraints)
                .build(),
        )
        workManager.enqueueUniquePeriodicWork(
            WorkName,
            ExistingPeriodicWorkPolicy.KEEP,
            request,
        )
    }
}
