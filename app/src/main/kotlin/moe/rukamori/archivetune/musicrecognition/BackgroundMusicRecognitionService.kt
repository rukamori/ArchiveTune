/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.musicrecognition

import android.app.Activity
import android.app.Service
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.service.quicksettings.TileService
import androidx.core.app.ServiceCompat
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@AndroidEntryPoint
class BackgroundMusicRecognitionService : Service() {
    @Inject
    lateinit var recognizeMusic: RecognizeMusicUseCase

    @Inject
    lateinit var notificationManager: MusicRecognitionNotificationManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var recognitionJob: Job? = null
    private var mediaProjection: MediaProjection? = null
    private var projectionCallback: MediaProjection.Callback? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ActionCancel -> cancelRecognition()
            ActionRecognizePlayback -> startPlaybackRecognition(intent)
            ActionRecognizeMicrophone -> startMicrophoneRecognition()
            else -> stopSelf(startId)
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        recognitionJob?.cancel()
        releaseProjection()
        MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Idle)
        requestTileRefresh()
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun startPlaybackRecognition(intent: Intent) {
        if (recognitionJob?.isActive == true) return

        val resultCode = intent.getIntExtra(ExtraResultCode, Activity.RESULT_CANCELED)
        val resultData = intent.parcelableIntentExtra(ExtraResultData)
        if (resultCode != Activity.RESULT_OK || resultData == null) {
            stopSelf()
            return
        }

        startRecognitionForeground(ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION)
        val projectionManager =
            getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        val projection =
            try {
                projectionManager.getMediaProjection(resultCode, resultData)
            } catch (throwable: Throwable) {
                Timber.e(throwable, "Unable to create media projection for music recognition")
                publishFailure(MusicRecognitionFailure.RecordingFailed)
                return
            } ?: run {
                Timber.e("Media projection was unavailable for music recognition")
                publishFailure(MusicRecognitionFailure.RecordingFailed)
                return
            }
        mediaProjection = projection
        recognitionJob =
            serviceScope.launch(start = CoroutineStart.LAZY) {
                runRecognition {
                    recognizeMusic.fromDevicePlayback(
                        mediaProjection = projection,
                        onPhaseChanged = ::onPhaseChanged,
                    )
                }
            }
        registerProjectionCallback(projection)
        recognitionJob?.start()
    }

    private fun startMicrophoneRecognition() {
        if (recognitionJob?.isActive == true) return

        startRecognitionForeground(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        recognitionJob =
            serviceScope.launch {
                runRecognition {
                    recognizeMusic(onPhaseChanged = ::onPhaseChanged)
                }
            }
    }

    private fun startRecognitionForeground(foregroundServiceType: Int) {
        MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Listening)
        requestTileRefresh()
        ServiceCompat.startForeground(
            this,
            MusicRecognitionNotificationManager.NotificationId,
            notificationManager.listening(),
            foregroundServiceType,
        )
    }

    private suspend fun runRecognition(
        recognize: suspend () -> Result<RecognizedTrack>,
    ) {
        try {
            recognize().fold(
                onSuccess = { track ->
                    finishForeground()
                    notificationManager.notify(notificationManager.result(track))
                },
                onFailure = { throwable ->
                    if (throwable is CancellationException) throw throwable
                    Timber.e(throwable, "Background music recognition failed")
                    val failure =
                        (throwable as? MusicRecognitionException)?.failure
                            ?: MusicRecognitionFailure.RecognitionFailed
                    finishForeground()
                    notificationManager.notify(notificationManager.failure(failure))
                },
            )
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (throwable: Throwable) {
            Timber.e(throwable, "Unexpected background music recognition failure")
            finishForeground()
            notificationManager.notify(
                notificationManager.failure(MusicRecognitionFailure.RecognitionFailed),
            )
        } finally {
            releaseProjection()
            recognitionJob = null
            MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Idle)
            requestTileRefresh()
            stopSelf()
        }
    }

    private fun onPhaseChanged(phase: RecognitionPhase) {
        when (phase) {
            RecognitionPhase.Listening -> {
                MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Listening)
                notificationManager.notify(notificationManager.listening())
            }
            RecognitionPhase.Processing -> {
                MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Processing)
                notificationManager.notify(notificationManager.processing())
            }
        }
        requestTileRefresh()
    }

    private fun registerProjectionCallback(projection: MediaProjection) {
        val callback =
            object : MediaProjection.Callback() {
                override fun onStop() {
                    recognitionJob?.cancel(CancellationException("Media projection stopped"))
                }
            }
        projectionCallback = callback
        projection.registerCallback(callback, Handler(Looper.getMainLooper()))
    }

    private fun cancelRecognition() {
        recognitionJob?.cancel()
        recognitionJob = null
        releaseProjection()
        MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Idle)
        requestTileRefresh()
        stopForeground(STOP_FOREGROUND_REMOVE)
        notificationManager.cancel()
        stopSelf()
    }

    private fun publishFailure(failure: MusicRecognitionFailure) {
        finishForeground()
        notificationManager.notify(notificationManager.failure(failure))
        MusicRecognitionRuntimeState.update(BackgroundRecognitionState.Idle)
        requestTileRefresh()
        stopSelf()
    }

    private fun finishForeground() {
        stopForeground(STOP_FOREGROUND_DETACH)
    }

    private fun releaseProjection() {
        val projection = mediaProjection ?: return
        projectionCallback?.let(projection::unregisterCallback)
        projectionCallback = null
        mediaProjection = null
        runCatching(projection::stop)
    }

    private fun requestTileRefresh() {
        TileService.requestListeningState(
            this,
            ComponentName(this, MusicRecognitionTileService::class.java),
        )
    }

    private fun Intent.parcelableIntentExtra(key: String): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(key, Intent::class.java)
        } else {
            @Suppress("DEPRECATION")
            getParcelableExtra(key)
        }

    companion object {
        private const val ActionRecognizePlayback =
            "moe.rukamori.archivetune.action.RECOGNIZE_DEVICE_PLAYBACK"
        private const val ActionRecognizeMicrophone =
            "moe.rukamori.archivetune.action.RECOGNIZE_MICROPHONE"
        private const val ActionCancel =
            "moe.rukamori.archivetune.action.CANCEL_BACKGROUND_RECOGNITION"
        private const val ExtraResultCode = "media_projection_result_code"
        private const val ExtraResultData = "media_projection_result_data"

        fun devicePlaybackIntent(
            context: Context,
            resultCode: Int,
            resultData: Intent,
        ): Intent =
            Intent(context, BackgroundMusicRecognitionService::class.java).apply {
                action = ActionRecognizePlayback
                putExtra(ExtraResultCode, resultCode)
                putExtra(ExtraResultData, resultData)
            }

        fun microphoneIntent(context: Context): Intent =
            Intent(context, BackgroundMusicRecognitionService::class.java).apply {
                action = ActionRecognizeMicrophone
            }

        fun cancelIntent(context: Context): Intent =
            Intent(context, BackgroundMusicRecognitionService::class.java).apply {
                action = ActionCancel
            }
    }
}
