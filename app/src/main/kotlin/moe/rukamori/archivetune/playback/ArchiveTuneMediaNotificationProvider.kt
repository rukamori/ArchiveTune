/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.annotation.DrawableRes
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import moe.rukamori.archivetune.R

@UnstableApi
class ArchiveTuneMediaNotificationProvider(
    private val context: Context,
    @DrawableRes smallIconResId: Int,
) : MediaNotification.Provider {
    private val delegate =
        DefaultMediaNotificationProvider(
            context,
            { MusicService.NOTIFICATION_ID },
            MusicService.CHANNEL_ID,
            R.string.music_player,
        ).apply {
            setSmallIcon(smallIconResId)
        }

    @Volatile
    var latestNotification: MediaNotification? = null
        private set

    fun clearLatestNotification() {
        latestNotification = null
    }

    override fun createNotification(
        mediaSession: MediaSession,
        mediaButtonPreferences: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        onNotificationChangedCallback: MediaNotification.Provider.Callback,
    ): MediaNotification =
        decorate(
            delegate.createNotification(
                mediaSession,
                mediaButtonPreferences,
                actionFactory,
            ) { updatedNotification ->
                onNotificationChangedCallback.onNotificationChanged(decorate(updatedNotification))
            },
        )

    override fun handleCustomCommand(
        session: MediaSession,
        action: String,
        extras: Bundle,
    ): Boolean = delegate.handleCustomCommand(session, action, extras)

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo = delegate.notificationChannelInfo

    private fun decorate(mediaNotification: MediaNotification): MediaNotification {
        val notification = mediaNotification.notification
        notification.deleteIntent?.let { originalDeleteIntent ->
            notification.deleteIntent =
                PendingIntent.getService(
                    context,
                    mediaNotification.notificationId,
                    Intent(context, MusicService::class.java).apply {
                        action = MusicService.ACTION_MEDIA_NOTIFICATION_DISMISSED
                        putExtra(
                            MusicService.EXTRA_MEDIA_NOTIFICATION_DELETE_INTENT,
                            originalDeleteIntent,
                        )
                    },
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
        }
        latestNotification = mediaNotification
        return mediaNotification
    }
}
