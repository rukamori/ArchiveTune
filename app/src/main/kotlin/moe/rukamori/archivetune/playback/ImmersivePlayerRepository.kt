/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.playback

import android.content.Context
import android.media.AudioManager
import android.os.Build
import androidx.media3.common.C
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.isActive
import moe.rukamori.archivetune.db.entities.FormatEntity
import moe.rukamori.archivetune.db.entities.Song
import moe.rukamori.archivetune.extensions.togglePlayPause
import moe.rukamori.archivetune.models.ActiveOutputDevice
import moe.rukamori.archivetune.models.MediaMetadata
import javax.inject.Inject
import kotlin.math.roundToInt

data class ImmersivePlayerData(
    val metadata: MediaMetadata,
    val song: Song?,
    val format: FormatEntity?,
    val queueTitle: String?,
    val playbackState: Int,
    val isPlaying: Boolean,
    val canSkipPrevious: Boolean,
    val canSkipNext: Boolean,
    val outputDevice: ActiveOutputDevice,
    val hasPlaybackError: Boolean,
)

data class ImmersivePlaybackProgress(
    val positionMs: Long,
    val durationMs: Long,
    val volume: Float,
)

@OptIn(ExperimentalCoroutinesApi::class)
@ViewModelScoped
class ImmersivePlayerRepository @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connection = MutableStateFlow<PlayerConnection?>(null)
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    fun attach(playerConnection: PlayerConnection) {
        connection.value = playerConnection
    }

    fun detach(playerConnection: PlayerConnection) {
        connection.compareAndSet(playerConnection, null)
    }

    fun observePlayer(): Flow<ImmersivePlayerData?> =
        connection.flatMapLatest { playerConnection ->
            if (playerConnection == null) {
                flowOf(null)
            } else {
                val media =
                    combine(
                        playerConnection.mediaMetadata,
                        playerConnection.currentSong,
                        playerConnection.currentFormat,
                    ) { metadata, song, format -> Triple(metadata, song, format) }
                val transport =
                    combine(
                        playerConnection.playbackState,
                        playerConnection.isPlaying,
                        playerConnection.canSkipPrevious,
                        playerConnection.canSkipNext,
                        playerConnection.queueTitle,
                    ) { playbackState, isPlaying, canSkipPrevious, canSkipNext, queueTitle ->
                        TransportData(playbackState, isPlaying, canSkipPrevious, canSkipNext, queueTitle)
                    }
                combine(
                    media,
                    transport,
                    playerConnection.service.activeAudioDevice,
                    playerConnection.error,
                ) { (metadata, song, format), transportData, outputDevice, error ->
                    metadata?.let {
                        ImmersivePlayerData(
                            metadata = it,
                            song = song,
                            format = format,
                            queueTitle = transportData.queueTitle,
                            playbackState = transportData.playbackState,
                            isPlaying = transportData.isPlaying,
                            canSkipPrevious = transportData.canSkipPrevious,
                            canSkipNext = transportData.canSkipNext,
                            outputDevice = outputDevice,
                            hasPlaybackError = error != null,
                        )
                    }
                }
            }
        }

    fun observeProgress(): Flow<ImmersivePlaybackProgress> =
        connection.flatMapLatest { playerConnection ->
            if (playerConnection == null) {
                flowOf(ImmersivePlaybackProgress(0L, 0L, readVolumeFraction()))
            } else {
                flow {
                    while (currentCoroutineContext().isActive) {
                        val player = playerConnection.player
                        val duration = player.duration.takeUnless { it == C.TIME_UNSET }?.coerceAtLeast(0L) ?: 0L
                        emit(
                            ImmersivePlaybackProgress(
                                positionMs = player.currentPosition.coerceAtLeast(0L),
                                durationMs = duration,
                                volume = readVolumeFraction(),
                            ),
                        )
                        delay(250L)
                    }
                }
            }
        }

    fun togglePlayPause() {
        connection.value?.player?.togglePlayPause()
    }

    fun skipPrevious() {
        connection.value?.seekToPrevious()
    }

    fun skipNext() {
        connection.value?.seekToNext()
    }

    fun seekTo(positionMs: Long) {
        connection.value?.player?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun toggleLike() {
        connection.value?.toggleLike()
    }

    fun setVolume(fraction: Float) {
        val minVolume = readMinVolume()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(minVolume + 1)
        val safeFraction = fraction.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: return
        val volume = (minVolume + ((maxVolume - minVolume) * safeFraction).roundToInt()).coerceIn(minVolume, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, volume, 0)
    }

    private fun readVolumeFraction(): Float {
        val minVolume = readMinVolume()
        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(minVolume + 1)
        val volume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return ((volume - minVolume).toFloat() / (maxVolume - minVolume).toFloat()).coerceIn(0f, 1f)
    }

    private fun readMinVolume(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            audioManager.getStreamMinVolume(AudioManager.STREAM_MUSIC)
        } else {
            0
        }

    private data class TransportData(
        val playbackState: Int,
        val isPlaying: Boolean,
        val canSkipPrevious: Boolean,
        val canSkipNext: Boolean,
        val queueTitle: String?,
    )
}
