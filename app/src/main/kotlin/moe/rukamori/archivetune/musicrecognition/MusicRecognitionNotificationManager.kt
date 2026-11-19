/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import moe.rukamori.archivetune.MainActivity
import moe.rukamori.archivetune.R
import javax.inject.Inject

class MusicRecognitionNotificationManager
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        fun createChannel() {
            val channel =
                NotificationChannel(
                    ChannelId,
                    context.getString(R.string.music_recognition_notification_channel_name),
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description =
                        context.getString(R.string.music_recognition_notification_channel_description)
                    setShowBadge(true)
                }
            context.getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }

        internal fun floating(state: BackgroundRecognitionState): Notification {
            val builder =
                when (state) {
                    BackgroundRecognitionState.Idle ->
                        baseBuilder(
                            title = context.getString(R.string.music_recognition_notification_floating_title),
                            text = context.getString(R.string.music_recognition_notification_floating_text),
                            status = R.string.music_recognition,
                            alert = false,
                        ).addAction(
                            R.drawable.close,
                            context.getString(R.string.close),
                            servicePendingIntent(CloseRequestCode, FloatingMusicRecognitionService.closeIntent(context)),
                        )

                    BackgroundRecognitionState.Listening ->
                        baseBuilder(
                            title = context.getString(R.string.music_recognition_notification_listening_title),
                            text = context.getString(R.string.music_recognition_notification_listening_mic_text),
                            status = R.string.music_recognition_notification_status_listening,
                            alert = false,
                        ).setProgress(0, 0, true)
                            .addAction(
                                R.drawable.close,
                                context.getString(R.string.cancel),
                                servicePendingIntent(CancelRequestCode, FloatingMusicRecognitionService.cancelIntent(context)),
                            )

                    BackgroundRecognitionState.Processing ->
                        baseBuilder(
                            title = context.getString(R.string.music_recognition_notification_processing_title),
                            text = context.getString(R.string.music_recognition_notification_processing_text),
                            status = R.string.music_recognition_notification_status_processing,
                            alert = false,
                        ).setProgress(0, 0, true)
                            .addAction(
                                R.drawable.close,
                                context.getString(R.string.cancel),
                                servicePendingIntent(CancelRequestCode, FloatingMusicRecognitionService.cancelIntent(context)),
                            )
                }
            return builder
                .setOngoing(true)
                .setAutoCancel(false)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()
        }

        fun notifyFloating(notification: Notification) {
            NotificationManagerCompat.from(context).notify(ForegroundNotificationId, notification)
        }

        fun result(track: RecognizedTrack): Notification {
            val summary =
                listOfNotNull(track.artist, track.album?.takeIf(String::isNotBlank))
                    .joinToString(" • ")
            val details =
                buildList {
                    add(context.getString(R.string.music_recognition_notification_artist, track.artist))
                    track.album
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            add(context.getString(R.string.music_recognition_notification_album, it))
                        }
                    track.genre
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            add(context.getString(R.string.music_recognition_notification_genre, it))
                        }
                    track.releaseDate
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            add(context.getString(R.string.music_recognition_notification_release, it))
                        }
                    track.label
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            add(context.getString(R.string.music_recognition_notification_label, it))
                        }
                    track.isrc
                        ?.takeIf(String::isNotBlank)
                        ?.let {
                            add(context.getString(R.string.music_recognition_notification_isrc, it))
                        }
                }.joinToString("\n")

            return baseBuilder(
                title = track.title,
                text = summary,
                status = R.string.music_recognition_notification_status_result,
                alert = true,
            ).setContentIntent(resultPendingIntent(track))
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                .setTimeoutAfter(ResultNotificationTimeoutMillis)
                .build()
        }

        fun notify(notification: Notification) {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        }

        private fun baseBuilder(
            title: String,
            text: String,
            @StringRes status: Int,
            alert: Boolean,
        ): NotificationCompat.Builder =
            NotificationCompat
                .Builder(context, ChannelId)
                .setSmallIcon(R.drawable.mic)
                .setContentTitle(title)
                .setContentText(text)
                .setSubText(context.getString(status))
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setAutoCancel(true)
                .setOngoing(false)
                .setSilent(false)
                .setOnlyAlertOnce(!alert)
                .setContentIntent(basePendingIntent())

        private fun basePendingIntent(): PendingIntent {
            val intent = Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            return PendingIntent.getActivity(
                context,
                ResultRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        fun detailsIntent(track: RecognizedTrack): Intent {
            val jsonString = Json.encodeToString(RecognizedTrack.serializer(), track)
            val encodedTrack = Base64.encodeToString(
                jsonString.toByteArray(Charsets.UTF_8),
                Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING
            )
            return Intent(context, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                putExtra("navigate_to", "music_recognition/details/$encodedTrack")
            }
        }

        private fun resultPendingIntent(track: RecognizedTrack): PendingIntent =
            PendingIntent.getActivity(
                context,
                ResultClickRequestCode,
                detailsIntent(track),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun servicePendingIntent(requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getService(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val NotificationId = 9_410
            const val ForegroundNotificationId = 9_414
            private const val ChannelId = "music_recognition"
            private const val ResultRequestCode = 9_411
            private const val CancelRequestCode = 9_412
            private const val ResultClickRequestCode = 9_413
            private const val CloseRequestCode = 9_415
            private const val ResultNotificationTimeoutMillis = 5 * 60 * 1_000L
        }
    }
