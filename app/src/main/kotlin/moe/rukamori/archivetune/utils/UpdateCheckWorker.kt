/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel

class UpdateCheckWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            return Result.success()
        }

        return try {
            val dataStore = applicationContext.dataStore

            val isEnabled = dataStore.data.map { it[EnableUpdateNotificationKey] ?: false }.first()
            if (!isEnabled) return Result.success()

            val updateChannel =
                dataStore.data
                    .map { UpdateChannel.fromStoredName(it[UpdateChannelKey], defaultUpdateChannel) }
                    .first()

            when (updateChannel) {
                UpdateChannel.CANARY -> {
                    Updater.getLatestCanaryVersionName().onSuccess { latestVersion ->
                        if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                            UpdateNotificationManager.notifyIfNewVersion(
                                applicationContext,
                                latestVersion,
                                updateChannel,
                            )
                        }
                    }
                }

                UpdateChannel.STABLE -> {
                    Updater.getLatestVersionName().onSuccess { latestVersion ->
                        if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                            UpdateNotificationManager.notifyIfNewVersion(applicationContext, latestVersion)
                        }
                    }
                }
            }

            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
