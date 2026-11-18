/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback.notification

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import moe.rukamori.archivetune.constants.MediaNotificationActionsKey
import moe.rukamori.archivetune.constants.MediaNotificationNavigationKey
import moe.rukamori.archivetune.constants.MediaNotificationSeekIncrementKey
import moe.rukamori.archivetune.constants.MediaNotificationSleepTimerMinutesKey
import moe.rukamori.archivetune.constants.MediaNotificationStopOnDismissKey
import moe.rukamori.archivetune.playback.MusicService
import moe.rukamori.archivetune.utils.dataStore
import moe.rukamori.archivetune.utils.get
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MediaNotificationSettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val notificationManager = context.getSystemService<NotificationManager>()

    val configuration: Flow<MediaNotificationConfiguration> =
        context.dataStore.data
            .map { preferences -> preferences.toConfiguration() }
            .distinctUntilChanged()

    fun currentConfiguration(): MediaNotificationConfiguration =
        MediaNotificationConfiguration(
            actions = MediaNotificationAction.decode(context.dataStore[MediaNotificationActionsKey]),
            navigation = MediaNotificationNavigation.fromPreference(context.dataStore[MediaNotificationNavigationKey]),
            seekIncrementSeconds = MediaNotificationConfiguration.coerceSeekIncrement(context.dataStore[MediaNotificationSeekIncrementKey]),
            sleepTimerMinutes = MediaNotificationConfiguration.coerceSleepTimerMinutes(context.dataStore[MediaNotificationSleepTimerMinutesKey]),
            stopOnDismiss = context.dataStore.get(MediaNotificationStopOnDismissKey, false),
        )

    suspend fun updateActions(transform: (List<MediaNotificationAction>) -> List<MediaNotificationAction>) {
        context.dataStore.edit { preferences ->
            val current = MediaNotificationAction.decode(preferences[MediaNotificationActionsKey])
            preferences[MediaNotificationActionsKey] = MediaNotificationAction.encode(transform(current))
        }
    }

    suspend fun setNavigation(navigation: MediaNotificationNavigation) {
        context.dataStore.edit { it[MediaNotificationNavigationKey] = navigation.name }
    }

    suspend fun setSeekIncrement(seconds: Int) {
        context.dataStore.edit { it[MediaNotificationSeekIncrementKey] = MediaNotificationConfiguration.coerceSeekIncrement(seconds) }
    }

    suspend fun setSleepTimerMinutes(minutes: Int) {
        context.dataStore.edit { it[MediaNotificationSleepTimerMinutesKey] = MediaNotificationConfiguration.coerceSleepTimerMinutes(minutes) }
    }

    suspend fun setStopOnDismiss(enabled: Boolean) {
        context.dataStore.edit { it[MediaNotificationStopOnDismissKey] = enabled }
    }

    suspend fun resetToDefaults() {
        context.dataStore.edit { preferences ->
            preferences.remove(MediaNotificationActionsKey)
            preferences.remove(MediaNotificationNavigationKey)
            preferences.remove(MediaNotificationSeekIncrementKey)
            preferences.remove(MediaNotificationSleepTimerMinutesKey)
            preferences.remove(MediaNotificationStopOnDismissKey)
        }
    }

    fun systemStatus(): MediaNotificationSystemStatus {
        val permissionRequired =
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        val appEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
        val importance = playerChannelImportance()
        val health =
            when {
                permissionRequired -> MediaNotificationHealth.PERMISSION_REQUIRED
                !appEnabled -> MediaNotificationHealth.APP_BLOCKED
                importance == MediaNotificationChannelImportance.NOT_CREATED -> MediaNotificationHealth.CHANNEL_NOT_CREATED
                importance == MediaNotificationChannelImportance.NONE -> MediaNotificationHealth.CHANNEL_BLOCKED
                else -> MediaNotificationHealth.READY
            }
        return MediaNotificationSystemStatus(
            health = health,
            channelImportance = importance,
            permissionRequestable = permissionRequired,
        )
    }

    private fun playerChannelImportance(): MediaNotificationChannelImportance {
        val channel = notificationManager?.getNotificationChannel(MusicService.CHANNEL_ID)
            ?: return MediaNotificationChannelImportance.NOT_CREATED
        return when (channel.importance) {
            NotificationManager.IMPORTANCE_NONE -> MediaNotificationChannelImportance.NONE
            NotificationManager.IMPORTANCE_MIN -> MediaNotificationChannelImportance.MIN
            NotificationManager.IMPORTANCE_LOW -> MediaNotificationChannelImportance.LOW
            NotificationManager.IMPORTANCE_DEFAULT -> MediaNotificationChannelImportance.DEFAULT
            else -> MediaNotificationChannelImportance.HIGH
        }
    }

    private fun Preferences.toConfiguration(): MediaNotificationConfiguration =
        MediaNotificationConfiguration(
            actions = MediaNotificationAction.decode(this[MediaNotificationActionsKey]),
            navigation = MediaNotificationNavigation.fromPreference(this[MediaNotificationNavigationKey]),
            seekIncrementSeconds = MediaNotificationConfiguration.coerceSeekIncrement(this[MediaNotificationSeekIncrementKey]),
            sleepTimerMinutes = MediaNotificationConfiguration.coerceSleepTimerMinutes(this[MediaNotificationSleepTimerMinutesKey]),
            stopOnDismiss = this[MediaNotificationStopOnDismissKey] ?: false,
        )
}
