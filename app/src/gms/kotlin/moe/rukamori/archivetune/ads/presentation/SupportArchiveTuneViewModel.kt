package moe.rukamori.archivetune.ads.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ads.domain.ObserveAdPrivacyOptionsUseCase
import moe.rukamori.archivetune.ads.domain.ObserveSupportAdAvailabilityUseCase
import moe.rukamori.archivetune.ads.domain.ObserveSupportAdEventsUseCase
import moe.rukamori.archivetune.ads.domain.ShowAdPrivacyOptionsUseCase
import moe.rukamori.archivetune.ads.domain.ShowSupportAdUseCase
import moe.rukamori.archivetune.ads.domain.SupportAdAvailability
import moe.rukamori.archivetune.ads.domain.SupportAdEvent
import moe.rukamori.archivetune.ads.domain.SupportAdRequestResult
import javax.inject.Inject

internal sealed interface SupportArchiveTuneScreenState {
    @Immutable
    data class Loading(
        val privacyOptionsRequired: Boolean,
    ) : SupportArchiveTuneScreenState

    @Immutable
    data class Success(
        val model: SupportArchiveTuneUiModel,
    ) : SupportArchiveTuneScreenState

    @Immutable
    data class Empty(
        val privacyOptionsRequired: Boolean,
    ) : SupportArchiveTuneScreenState

    @Immutable
    data class Error(
        val privacyOptionsRequired: Boolean,
    ) : SupportArchiveTuneScreenState
}

@Immutable
internal data class SupportArchiveTuneUiModel(
    val privacyOptionsRequired: Boolean,
)

internal enum class SupportArchiveTuneUiEvent {
    RewardEarned,
    AdFailed,
    ActivityUnavailable,
    PrivacyOptionsUpdated,
    PrivacyOptionsFailed,
}

@HiltViewModel
internal class SupportArchiveTuneViewModel
    @Inject
    constructor(
        observeAvailability: ObserveSupportAdAvailabilityUseCase,
        observeEvents: ObserveSupportAdEventsUseCase,
        observePrivacyOptions: ObserveAdPrivacyOptionsUseCase,
        private val showSupportAd: ShowSupportAdUseCase,
        private val showPrivacyOptions: ShowAdPrivacyOptionsUseCase,
    ) : ViewModel() {
        val screenState: StateFlow<SupportArchiveTuneScreenState> =
            combine(observeAvailability(), observePrivacyOptions()) { availability, privacyRequired ->
                when (availability) {
                    SupportAdAvailability.Preparing ->
                        SupportArchiveTuneScreenState.Loading(privacyRequired)
                    SupportAdAvailability.Ready ->
                        SupportArchiveTuneScreenState.Success(
                            SupportArchiveTuneUiModel(
                                privacyOptionsRequired = privacyRequired,
                            ),
                        )
                    SupportAdAvailability.ConsentRequired ->
                        SupportArchiveTuneScreenState.Success(
                            SupportArchiveTuneUiModel(
                                privacyOptionsRequired = privacyRequired,
                            ),
                        )
                    SupportAdAvailability.Unavailable ->
                        SupportArchiveTuneScreenState.Empty(privacyRequired)
                    SupportAdAvailability.Failed ->
                        SupportArchiveTuneScreenState.Error(privacyRequired)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = SupportArchiveTuneScreenState.Loading(false),
            )

        private val eventChannel = Channel<SupportArchiveTuneUiEvent>(Channel.BUFFERED)
        val events = eventChannel.receiveAsFlow()

        init {
            viewModelScope.launch {
                observeEvents().collect { event ->
                    eventChannel.send(event.toUiEvent())
                }
            }
        }

        fun onSupportArchiveTuneClick() {
            handleRequestResult(showSupportAd())
        }

        fun onPrivacyOptionsClick() {
            handleRequestResult(showPrivacyOptions())
        }

        private fun handleRequestResult(result: SupportAdRequestResult) {
            if (result == SupportAdRequestResult.ActivityUnavailable) {
                eventChannel.trySend(SupportArchiveTuneUiEvent.ActivityUnavailable)
            }
        }

        private fun SupportAdEvent.toUiEvent(): SupportArchiveTuneUiEvent =
            when (this) {
                SupportAdEvent.RewardEarned -> SupportArchiveTuneUiEvent.RewardEarned
                SupportAdEvent.AdFailed -> SupportArchiveTuneUiEvent.AdFailed
                SupportAdEvent.ActivityUnavailable -> SupportArchiveTuneUiEvent.ActivityUnavailable
                SupportAdEvent.PrivacyOptionsUpdated -> SupportArchiveTuneUiEvent.PrivacyOptionsUpdated
                SupportAdEvent.PrivacyOptionsFailed -> SupportArchiveTuneUiEvent.PrivacyOptionsFailed
            }
    }
