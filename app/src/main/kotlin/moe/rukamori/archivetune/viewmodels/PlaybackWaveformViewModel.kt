/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import moe.rukamori.archivetune.playback.WaveformRepository
import timber.log.Timber
import javax.inject.Inject

@Immutable
data class SongWaveform(
    val amplitudes: ImmutableList<Float>,
)

sealed interface PlaybackWaveformState {
    data object Empty : PlaybackWaveformState

    data class Loading(
        val mediaId: String,
    ) : PlaybackWaveformState

    data class Ready(
        val mediaId: String,
        val waveform: SongWaveform,
    ) : PlaybackWaveformState

    data class Unavailable(
        val mediaId: String,
    ) : PlaybackWaveformState
}

@HiltViewModel
class PlaybackWaveformViewModel
    @Inject
    constructor(
        private val repository: WaveformRepository,
    ) : ViewModel() {
        private val _state = MutableStateFlow<PlaybackWaveformState>(PlaybackWaveformState.Empty)
        val state: StateFlow<PlaybackWaveformState> = _state.asStateFlow()
        private var generationJob: Job? = null

        fun load(
            mediaId: String,
            durationMs: Int,
        ) {
            when (val current = _state.value) {
                is PlaybackWaveformState.Loading -> if (current.mediaId == mediaId) return
                is PlaybackWaveformState.Ready -> if (current.mediaId == mediaId) return
                is PlaybackWaveformState.Unavailable -> if (current.mediaId == mediaId) return
                PlaybackWaveformState.Empty -> Unit
            }
            generationJob?.cancel()
            _state.value = PlaybackWaveformState.Loading(mediaId)
            generationJob =
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val amplitudes = repository.getOrGenerate(mediaId, durationMs)
                        val waveform =
                            withContext(Dispatchers.Default) {
                                SongWaveform(
                                    amplitudes = ImmutableList.copyOf(amplitudes.map { sample -> (sample.toInt() and 0xff) / 255f }),
                                )
                            }
                        _state.value = PlaybackWaveformState.Ready(mediaId, waveform)
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Timber.w(exception, "Unable to generate a waveform for %s", mediaId)
                        _state.value = PlaybackWaveformState.Unavailable(mediaId)
                    }
                }
        }

        fun cancel() {
            generationJob?.cancel()
            generationJob = null
            _state.value = PlaybackWaveformState.Empty
        }
    }
