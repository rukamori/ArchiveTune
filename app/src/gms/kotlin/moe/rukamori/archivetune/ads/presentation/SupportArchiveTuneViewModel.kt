/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ads.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import moe.rukamori.archivetune.ads.domain.OpenSupportPageUseCase
import moe.rukamori.archivetune.ads.domain.SupportPageOpenResult
import javax.inject.Inject

internal sealed interface SupportArchiveTuneScreenState {
    @Immutable
    data object Loading : SupportArchiveTuneScreenState

    @Immutable
    data object Success : SupportArchiveTuneScreenState

    @Immutable
    data object Empty : SupportArchiveTuneScreenState

    @Immutable
    data class Error(
        val reason: SupportArchiveTuneError,
    ) : SupportArchiveTuneScreenState
}

internal enum class SupportArchiveTuneError {
    PageUnavailable,
}

internal enum class SupportArchiveTuneUiEvent {
    OpenFailed,
}

@HiltViewModel
internal class SupportArchiveTuneViewModel
    @Inject
    constructor(
        private val openSupportPage: OpenSupportPageUseCase,
    ) : ViewModel() {
        private val _screenState =
            MutableStateFlow<SupportArchiveTuneScreenState>(SupportArchiveTuneScreenState.Success)
        val screenState: StateFlow<SupportArchiveTuneScreenState> = _screenState.asStateFlow()

        private val eventChannel = Channel<SupportArchiveTuneUiEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        fun onSupportArchiveTuneClick() {
            if (_screenState.value is SupportArchiveTuneScreenState.Loading) return
            _screenState.value = SupportArchiveTuneScreenState.Loading
            when (openSupportPage()) {
                SupportPageOpenResult.Opened -> {
                    _screenState.value = SupportArchiveTuneScreenState.Success
                }

                SupportPageOpenResult.Unavailable -> {
                    _screenState.value =
                        SupportArchiveTuneScreenState.Error(SupportArchiveTuneError.PageUnavailable)
                    eventChannel.trySend(SupportArchiveTuneUiEvent.OpenFailed)
                }
            }
        }
    }
