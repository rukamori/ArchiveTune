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
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import dagger.hilt.android.qualifiers.ApplicationContext
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

        fun listening(): Notification =
            baseBuilder(
                title = context.getString(R.string.music_recognition_notification_listening_title),
                text = context.getString(R.string.music_recognition_notification_listening_text),
                status = R.string.music_recognition_notification_status_listening,
                alert = true,
            ).setProgress(0, 0, true)
                .addAction(
                    R.drawable.close,
                    context.getString(R.string.cancel),
                    cancelPendingIntent(),
                ).build()

        fun processing(): Notification =
            baseBuilder(
                title = context.getString(R.string.music_recognition_notification_processing_title),
                text = context.getString(R.string.music_recognition_notification_processing_text),
                status = R.string.music_recognition_notification_status_processing,
                alert = false,
            ).setProgress(0, 0, true)
                .addAction(
                    R.drawable.close,
                    context.getString(R.string.cancel),
                    cancelPendingIntent(),
                ).build()

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
            ).setContentIntent(resultPendingIntent(track.shazamUrl))
                .setStyle(NotificationCompat.BigTextStyle().bigText(details))
                .setTimeoutAfter(ResultNotificationTimeoutMillis)
                .build()
        }

        fun failure(failure: MusicRecognitionFailure): Notification {
            val title =
                when (failure) {
                    MusicRecognitionFailure.NoMatch ->
                        R.string.music_recognition_notification_no_match_title
                    MusicRecognitionFailure.RecordingFailed ->
                        R.string.music_recognition_notification_recording_failed_title
                    MusicRecognitionFailure.SignatureFailed ->
                        R.string.music_recognition_signature_failed
                    MusicRecognitionFailure.RecognitionFailed ->
                        R.string.music_recognition_recognition_failed
                }
            val text =
                when (failure) {
                    MusicRecognitionFailure.NoMatch ->
                        R.string.music_recognition_notification_no_match_text
                    MusicRecognitionFailure.RecordingFailed ->
                        R.string.music_recognition_notification_recording_failed_text
                    MusicRecognitionFailure.SignatureFailed ->
                        R.string.music_recognition_notification_signature_failed_text
                    MusicRecognitionFailure.RecognitionFailed ->
                        R.string.music_recognition_notification_recognition_failed_text
                }

            return baseBuilder(
                title = context.getString(title),
                text = context.getString(text),
                status = R.string.music_recognition_notification_status_result,
                alert = true,
            ).setStyle(NotificationCompat.BigTextStyle().bigText(context.getString(text)))
                .setTimeoutAfter(ResultNotificationTimeoutMillis)
                .build()
        }

        fun notify(notification: Notification) {
            NotificationManagerCompat.from(context).notify(NotificationId, notification)
        }

        fun cancel() {
            NotificationManagerCompat.from(context).cancel(NotificationId)
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
                .setContentIntent(resultPendingIntent(null))

        private fun resultPendingIntent(shazamUrl: String?): PendingIntent {
            val intent =
                shazamUrl
                    ?.takeIf(String::isNotBlank)
                    ?.let { url ->
                        Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                    }
                    ?: Intent(context, MainActivity::class.java).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                    }
            return PendingIntent.getActivity(
                context,
                ResultRequestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }

        private fun cancelPendingIntent(): PendingIntent =
            PendingIntent.getService(
                context,
                CancelRequestCode,
                BackgroundMusicRecognitionService.cancelIntent(context),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        companion object {
            const val NotificationId = 9_410
            private const val ChannelId = "music_recognition"
            private const val ResultRequestCode = 9_411
            private const val CancelRequestCode = 9_412
            private const val ResultNotificationTimeoutMillis = 5 * 60 * 1_000L
        }
    }
