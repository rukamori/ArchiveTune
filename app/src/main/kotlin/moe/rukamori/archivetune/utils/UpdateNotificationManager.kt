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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.BuildConfig
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.AutomaticUpdateCheckKey
import moe.rukamori.archivetune.constants.EnableUpdateNotificationKey
import moe.rukamori.archivetune.constants.LastNotifiedVersionKey
import moe.rukamori.archivetune.constants.LastUpdateCheckKey
import moe.rukamori.archivetune.constants.UpdateChannel
import moe.rukamori.archivetune.constants.UpdateChannelKey
import moe.rukamori.archivetune.defaultUpdateChannel
import moe.rukamori.archivetune.updates.UpdateCheckScheduler

object UpdateNotificationManager {
    private const val CHANNEL_ID = "update_notification_channel"
    private const val NOTIFICATION_ID = 9999
    private const val CHECK_INTERVAL_MS = 6 * 60 * 60 * 1000L

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
        UpdateCheckScheduler.schedule(context)
    }

    fun cancelPeriodicUpdateCheck(context: Context) {
        UpdateCheckScheduler.cancel(context)
    }

    suspend fun checkForUpdates(context: Context) {
        if (!BuildConfig.UPDATER_AVAILABLE) {
            cancelPeriodicUpdateCheck(context)
            cancelUpdateNotification(context)
            return
        }

        withContext(Dispatchers.IO) {
            try {
                val dataStore = context.dataStore

                val preferences = dataStore.data.first()
                val automaticChecksEnabled = preferences[AutomaticUpdateCheckKey] ?: true
                if (!automaticChecksEnabled) {
                    cancelPeriodicUpdateCheck(context)
                    return@withContext
                }

                schedulePeriodicUpdateCheck(context)

                val notificationsEnabled = preferences[EnableUpdateNotificationKey] ?: false
                if (!notificationsEnabled) return@withContext

                val updateChannel =
                    UpdateChannel.fromStoredName(preferences[UpdateChannelKey], defaultUpdateChannel)

                val lastCheck = preferences[LastUpdateCheckKey] ?: 0L
                val now = System.currentTimeMillis()

                if (now - lastCheck < CHECK_INTERVAL_MS) return@withContext

                dataStore.edit { it[LastUpdateCheckKey] = now }

                val latestVersion =
                    when (updateChannel) {
                        UpdateChannel.ARTIFACT -> Updater.getLatestCanaryVersionName()
                        UpdateChannel.STABLE -> Updater.getLatestVersionName()
                    }.getOrElse { throw it }

                if (Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                    notifyIfNewVersion(context, latestVersion, updateChannel)
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                reportException(exception)
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
            val preferences = dataStore.data.first()
            val automaticChecksEnabled = preferences[AutomaticUpdateCheckKey] ?: true
            if (!automaticChecksEnabled || preferences[EnableUpdateNotificationKey] != true) return
            val lastNotified = preferences[LastNotifiedVersionKey].orEmpty()

            if (latestVersion != lastNotified && Updater.isUpdateAvailable(latestVersion, BuildConfig.VERSION_NAME)) {
                showUpdateNotification(context, latestVersion, updateChannel)
                dataStore.edit { it[LastNotifiedVersionKey] = latestVersion }
            }
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            reportException(exception)
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
                UpdateChannel.ARTIFACT -> Updater.getLatestCanaryDownloadUrl()
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
