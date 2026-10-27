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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.lyrics.PrepareLyricsUseCase
import moe.rukamori.archivetune.lyrics.PreparedLyrics
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface LyricsRenderScreenState {
    data object Loading : LyricsRenderScreenState

    @Immutable
    data class Success(
        val lyrics: PreparedLyrics,
    ) : LyricsRenderScreenState

    data object Empty : LyricsRenderScreenState

    @Immutable
    data class Error(
        val reason: LyricsRenderError,
    ) : LyricsRenderScreenState
}

enum class LyricsRenderError {
    INVALID_LYRICS,
}

@HiltViewModel
class LyricsRenderViewModel
    @Inject
    constructor(
        private val prepareLyrics: PrepareLyricsUseCase,
    ) : ViewModel() {
        private val _state = MutableStateFlow<LyricsRenderScreenState>(LyricsRenderScreenState.Loading)
        val state: StateFlow<LyricsRenderScreenState> = _state.asStateFlow()

        private val _playbackPositionMs = MutableStateFlow(0L)
        val playbackPositionMs: StateFlow<Long> = _playbackPositionMs.asStateFlow()
        private val playbackClock = LyricsPlaybackClock()

        private var observationJob: Job? = null
        private var binding: Binding? = null
        private var translationVisibilityJob: Job? = null

        fun onShowTranslationsChanged(show: Boolean) {
            val lyrics = (_state.value as? LyricsRenderScreenState.Success)?.lyrics ?: return
            if (!lyrics.hasTranslations) return
            translationVisibilityJob?.cancel()
            translationVisibilityJob = viewModelScope.launch {
                try {
                    prepareLyrics.setShowTranslations(show)
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    reportException(exception)
                    _state.value = LyricsRenderScreenState.Error(LyricsRenderError.INVALID_LYRICS)
                }
            }
        }

        fun onPlaybackPositionChanged(positionMs: Long) {
            _playbackPositionMs.value = playbackClock.reset(positionMs)
        }

        fun onPlaybackFrame(
            positionMs: Long,
            sampleTimeNanos: Long,
            playbackSpeed: Float,
            isAdvancing: Boolean,
            durationMs: Long,
        ) {
            _playbackPositionMs.value =
                playbackClock.update(positionMs, sampleTimeNanos, playbackSpeed, isAdvancing, durationMs)
        }

        fun bind(
            mediaId: String,
            durationMs: Long,
        ) {
            val nextBinding = Binding(mediaId, durationMs.coerceAtLeast(0L))
            if (binding == nextBinding) return

            val mediaChanged = binding?.mediaId != mediaId
            binding = nextBinding
            observationJob?.cancel()
            if (mediaChanged) _state.value = LyricsRenderScreenState.Loading
            observationJob =
                viewModelScope.launch {
                    prepareLyrics.observe(nextBinding.mediaId, nextBinding.durationMs).collect { result ->
                        _state.value =
                            result.fold(
                                onSuccess = { lyrics ->
                                    when {
                                        lyrics == null -> LyricsRenderScreenState.Loading
                                        lyrics.lines.isEmpty() -> LyricsRenderScreenState.Empty
                                        else -> LyricsRenderScreenState.Success(lyrics)
                                    }
                                },
                                onFailure = { throwable ->
                                    if (throwable is CancellationException) throw throwable
                                    reportException(throwable)
                                    LyricsRenderScreenState.Error(LyricsRenderError.INVALID_LYRICS)
                                },
                            )
                    }
                }
        }

        private data class Binding(
            val mediaId: String,
            val durationMs: Long,
        )
    }
