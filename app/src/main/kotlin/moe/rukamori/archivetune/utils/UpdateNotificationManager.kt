/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.datastore.preferences.core.edit
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.LastNotifiedVersionKey
import moe.rukamori.archivetune.constants.LastUpdateCheckKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel
import java.util.concurrent.TimeUnit

object UpdateNotificationManager {
    private const val CHANNEL_ID = "update_notification_channel"
    private const val NOTIFICATION_ID = 9999
    private const val WORK_NAME = "update_check_work"
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun createNotificationChannel(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = context.getString(R.string.update_notification_channel_name)
            val descriptionText = context.getString(R.string.update_notification_channel_desc)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel =
                NotificationChannel(CHANNEL_ID, name, importance).apply {
                    description = descriptionText
                }
            val notificationManager = context.getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    fun schedulePeriodicUpdateCheck(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            cancelPeriodicUpdateCheck(context)
            return
        }

        val constraints =
            Constraints
                .Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .setRequiresBatteryNotLow(true)
                .build()

        val updateCheckRequest =
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
            updateCheckRequest,
        )
    }

    fun cancelPeriodicUpdateCheck(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    fun checkForUpdates(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            cancelPeriodicUpdateCheck(context)
            cancelUpdateNotification(context)
            return
        }

        scope.launch {
            try {
                val dataStore = context.dataStore

                val isEnabled = dataStore.data.map { it[EnableUpdateNotificationKey] ?: false }.first()
                if (!isEnabled) {
                    cancelPeriodicUpdateCheck(context)
                    return@launch
                }

                schedulePeriodicUpdateCheck(context)

                val updateChannel =
                    dataStore.data
                        .map { UpdateChannel.fromStoredName(it[UpdateChannelKey], defaultUpdateChannel) }
                        .first()

                val lastCheck = dataStore.data.map { it[LastUpdateCheckKey] ?: 0L }.first()
                val now = System.currentTimeMillis()

                if (now - lastCheck < CHECK_INTERVAL_MS) return@launch

                dataStore.edit { it[LastUpdateCheckKey] = now }

                val versionResult =
                    when (updateChannel) {
                        UpdateChannel.CANARY -> Updater.getLatestCanaryVersionName()
                        UpdateChannel.STABLE -> Updater.getLatestVersionName()
                    }

                versionResult.onSuccess { latestVersion ->
                    if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                        notifyIfNewVersion(context, latestVersion, updateChannel)
                    }
                }
            } catch (e: Exception) {
                // Silently fail
            }
        }
    }

    suspend fun notifyIfNewVersion(
        context: Context,
        latestVersion: String,
        updateChannel: UpdateChannel = UpdateChannel.STABLE,
    ) {
        if (!BuildConfig.UPDATER_AVAILABLE) return

        try {
            val dataStore = context.dataStore
            val lastNotified = dataStore.data.map { it[LastNotifiedVersionKey] ?: "" }.first()

            if (latestVersion != lastNotified && Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                showUpdateNotification(context, latestVersion, updateChannel)
                dataStore.edit { it[LastNotifiedVersionKey] = latestVersion }
            }
        } catch (e: Exception) {
            // Silently fail
        }
    }

    private fun showUpdateNotification(
        context: Context,
        newVersion: String,
        updateChannel: UpdateChannel = UpdateChannel.STABLE,
    ) {
        createNotificationChannel(context)

        val openAppIntent =
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra("navigate_to", "settings/update")
            }
        val openAppPendingIntent =
            PendingIntent.getActivity(
                context,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val downloadUrl =
            when (updateChannel) {
                UpdateChannel.CANARY -> Updater.getLatestCanaryDownloadUrl()
                UpdateChannel.STABLE -> Updater.getLatestDownloadUrl()
            }
        val downloadIntent = Intent(Intent.ACTION_VIEW, Uri.parse(downloadUrl))
        val downloadPendingIntent =
            PendingIntent.getActivity(
                context,
                1,
                downloadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        val notification =
            NotificationCompat
                .Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.small_icon)
                .setContentTitle(context.getString(R.string.update_notification_title))
                .setContentText(context.getString(R.string.update_notification_text, newVersion))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(openAppPendingIntent)
                .setAutoCancel(true)
                .addAction(
                    R.drawable.download,
                    context.getString(R.string.download),
                    downloadPendingIntent,
                ).build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        } catch (e: SecurityException) {
            // Missing POST_NOTIFICATIONS permission
        }
    }

    fun cancelUpdateNotification(context: Context) {
        NotificationManagerCompat.from(context).cancel(NOTIFICATION_ID)
    }
}
