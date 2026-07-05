/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

sealed interface PoTokenState {
    data object Loading : PoTokenState

    data class Success(
        val gvsToken: String,
        val playerToken: String,
        val visitorData: String,
    ) : PoTokenState

    data object Empty : PoTokenState

    data class Error(
        val message: String,
    ) : PoTokenState
}

sealed interface PoTokenEvent {
    data class TokensGenerated(
        val gvsToken: String,
        val playerToken: String,
        val visitorData: String,
    ) : PoTokenEvent

    data class Error(
        val message: String,
    ) : PoTokenEvent
}

@HiltViewModel
class PoTokenViewModel
    @Inject
    constructor() : ViewModel() {
        private val _state = MutableStateFlow<PoTokenState>(PoTokenState.Empty)
        val state: StateFlow<PoTokenState> = _state.asStateFlow()

        private val _isRegenerateSheetVisible = MutableStateFlow(false)
        val isRegenerateSheetVisible: StateFlow<Boolean> = _isRegenerateSheetVisible.asStateFlow()

        private val _events = Channel<PoTokenEvent>(capacity = Channel.BUFFERED)
        val events = _events.receiveAsFlow()

        fun showRegenerateSheet() {
            _isRegenerateSheetVisible.value = true
        }

        fun dismissRegenerateSheet() {
            _isRegenerateSheetVisible.value = false
        }

        fun onExtractionStarted() {
            _state.value = PoTokenState.Loading
        }

        fun onTokensExtracted(
            visitorData: String,
            poToken: String,
            playerToken: String,
        ) {
            _state.value =
                PoTokenState.Success(
                    gvsToken = poToken,
                    playerToken = playerToken,
                    visitorData = visitorData,
                )
            viewModelScope.launch {
                _events.send(
                    PoTokenEvent.TokensGenerated(
                        gvsToken = poToken,
                        playerToken = playerToken,
                        visitorData = visitorData,
                    ),
                )
            }
        }

        fun onExtractionError(message: String) {
            _state.value = PoTokenState.Error(message)
            viewModelScope.launch {
                _events.send(PoTokenEvent.Error(message))
            }
        }

        fun onExtractionCancelled() {
            _state.value = PoTokenState.Empty
        }
    }
