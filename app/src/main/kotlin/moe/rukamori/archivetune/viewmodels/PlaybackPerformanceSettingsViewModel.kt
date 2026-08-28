/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.preload.ObservePlaybackPerformanceSettingsUseCase
import moe.rukamori.archivetune.playback.preload.PlaybackPerformanceSettings
import moe.rukamori.archivetune.playback.preload.SetLowDataModeUseCase
import moe.rukamori.archivetune.playback.preload.SetPreloadNextSongUseCase
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface PlaybackPerformanceSettingsUiState {
    data object Loading : PlaybackPerformanceSettingsUiState

    data class Success(
        val data: PlaybackPerformanceSettingsUiData,
    ) : PlaybackPerformanceSettingsUiState

    data object Empty : PlaybackPerformanceSettingsUiState

    data class Error(
        @StringRes val messageRes: Int,
    ) : PlaybackPerformanceSettingsUiState
}

@Immutable
data class PlaybackPerformanceSettingsUiData(
    val lowDataModeEnabled: Boolean,
    val preloadNextSongEnabled: Boolean,
    val preloadNextSongAvailable: Boolean,
)

@HiltViewModel
class PlaybackPerformanceSettingsViewModel
    @Inject
    constructor(
        private val observePlaybackPerformanceSettings: ObservePlaybackPerformanceSettingsUseCase,
        private val setLowDataMode: SetLowDataModeUseCase,
        private val setPreloadNextSong: SetPreloadNextSongUseCase,
    ) : ViewModel() {
        private val mutableUiState =
            MutableStateFlow<PlaybackPerformanceSettingsUiState>(PlaybackPerformanceSettingsUiState.Loading)
        val uiState: StateFlow<PlaybackPerformanceSettingsUiState> = mutableUiState.asStateFlow()

        private var observeJob: Job? = null
        private var updateJob: Job? = null

        init {
            observeSettings()
        }

        fun retry() {
            if (observeJob?.isActive == true) return

            mutableUiState.value = PlaybackPerformanceSettingsUiState.Loading
            observeSettings()
        }

        fun onLowDataModeChange(enabled: Boolean) {
            updateSettings {
                setLowDataMode(enabled)
            }
        }

        fun onPreloadNextSongChange(enabled: Boolean) {
            updateSettings {
                setPreloadNextSong(enabled)
            }
        }

        private fun observeSettings() {
            lateinit var nextJob: Job
            nextJob =
                viewModelScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        observePlaybackPerformanceSettings().collect { settings ->
                            mutableUiState.value = settings.toUiState()
                        }
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        mutableUiState.value =
                            PlaybackPerformanceSettingsUiState.Error(R.string.error_unknown)
                    } finally {
                        if (observeJob === nextJob) {
                            observeJob = null
                        }
                    }
                }
            observeJob = nextJob
            nextJob.start()
        }

        private fun updateSettings(update: suspend () -> Unit) {
            val previousJob = updateJob
            lateinit var nextJob: Job
            nextJob =
                viewModelScope.launch(start = CoroutineStart.LAZY) {
                    try {
                        update()
                    } catch (cancellation: CancellationException) {
                        throw cancellation
                    } catch (throwable: Throwable) {
                        reportException(throwable)
                        observeJob?.cancelAndJoin()
                        mutableUiState.value =
                            PlaybackPerformanceSettingsUiState.Error(R.string.error_unknown)
                    } finally {
                        if (updateJob === nextJob) {
                            updateJob = null
                        }
                    }
                }
            updateJob = nextJob
            previousJob?.cancel()
            nextJob.start()
        }

        private fun PlaybackPerformanceSettings.toUiState(): PlaybackPerformanceSettingsUiState =
            if (hasPersistedValue) {
                PlaybackPerformanceSettingsUiState.Success(
                    PlaybackPerformanceSettingsUiData(
                        lowDataModeEnabled = lowDataModeEnabled,
                        preloadNextSongEnabled = preloadNextSongEnabled,
                        preloadNextSongAvailable = !lowDataModeEnabled,
                    ),
                )
            } else {
                PlaybackPerformanceSettingsUiState.Empty
            }
    }
