/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.ads.presentation

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.ads.domain.ObserveSupportAdAvailabilityUseCase
import moe.rukamori.archivetune.ads.domain.ObserveSupportAdEventsUseCase
import moe.rukamori.archivetune.ads.domain.SetPersonalizedAdsConsentUseCase
import moe.rukamori.archivetune.ads.domain.ShowSupportAdUseCase
import moe.rukamori.archivetune.ads.domain.SupportAdAvailability
import moe.rukamori.archivetune.ads.domain.SupportAdEvent
import moe.rukamori.archivetune.ads.domain.SupportAdRequestResult
import javax.inject.Inject

internal sealed interface SupportArchiveTuneScreenState {
    val model: SupportArchiveTuneUiModel

    @Immutable
    data class Loading(
        override val model: SupportArchiveTuneUiModel,
    ) : SupportArchiveTuneScreenState

    @Immutable
    data class Success(
        override val model: SupportArchiveTuneUiModel,
    ) : SupportArchiveTuneScreenState

    @Immutable
    data class Empty(
        override val model: SupportArchiveTuneUiModel,
    ) : SupportArchiveTuneScreenState

    @Immutable
    data class Error(
        override val model: SupportArchiveTuneUiModel,
    ) : SupportArchiveTuneScreenState
}

@Immutable
internal data class SupportArchiveTuneUiModel(
    val privacyOptionsRequired: Boolean,
    val consentDialogPurpose: ConsentDialogPurpose?,
)

internal enum class ConsentDialogPurpose {
    SupportAd,
    PrivacyOptions,
}

internal enum class SupportArchiveTuneUiEvent {
    RewardEarned,
    AdFailed,
    ActivityUnavailable,
    PrivacyOptionsUpdated,
}

@HiltViewModel
internal class SupportArchiveTuneViewModel
    @Inject
    constructor(
        observeAvailability: ObserveSupportAdAvailabilityUseCase,
        observeEvents: ObserveSupportAdEventsUseCase,
        private val showSupportAd: ShowSupportAdUseCase,
        private val setPersonalizedAdsConsent: SetPersonalizedAdsConsentUseCase,
    ) : ViewModel() {
        private val consentDialogPurpose = MutableStateFlow<ConsentDialogPurpose?>(null)

        val screenState: StateFlow<SupportArchiveTuneScreenState> =
            combine(observeAvailability(), consentDialogPurpose) { availability, dialogPurpose ->
                val model =
                    SupportArchiveTuneUiModel(
                        privacyOptionsRequired = true,
                        consentDialogPurpose = dialogPurpose,
                    )
                when (availability) {
                    SupportAdAvailability.Preparing -> SupportArchiveTuneScreenState.Loading(model)

                    SupportAdAvailability.Ready,
                    SupportAdAvailability.ConsentRequired,
                    -> SupportArchiveTuneScreenState.Success(model)

                    SupportAdAvailability.Unavailable -> SupportArchiveTuneScreenState.Empty(model)

                    SupportAdAvailability.Failed -> SupportArchiveTuneScreenState.Error(model)
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue =
                    SupportArchiveTuneScreenState.Loading(
                        SupportArchiveTuneUiModel(
                            privacyOptionsRequired = true,
                            consentDialogPurpose = null,
                        ),
                    ),
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
            consentDialogPurpose.value = ConsentDialogPurpose.PrivacyOptions
        }

        fun onConsentSelected(personalized: Boolean) {
            val purpose = consentDialogPurpose.value ?: return
            consentDialogPurpose.value = null
            setPersonalizedAdsConsent(personalized)
            if (purpose == ConsentDialogPurpose.SupportAd) {
                handleRequestResult(showSupportAd())
            } else {
                eventChannel.trySend(SupportArchiveTuneUiEvent.PrivacyOptionsUpdated)
            }
        }

        fun onConsentDialogDismissed() {
            consentDialogPurpose.value = null
        }

        private fun handleRequestResult(result: SupportAdRequestResult) {
            when (result) {
                SupportAdRequestResult.ConsentRequired -> {
                    consentDialogPurpose.value = ConsentDialogPurpose.SupportAd
                }

                SupportAdRequestResult.ActivityUnavailable -> {
                    eventChannel.trySend(SupportArchiveTuneUiEvent.ActivityUnavailable)
                }

                SupportAdRequestResult.ConfigurationMissing -> {
                    eventChannel.trySend(SupportArchiveTuneUiEvent.AdFailed)
                }

                SupportAdRequestResult.Accepted,
                SupportAdRequestResult.AlreadyPending,
                -> {
                    Unit
                }
            }
        }

        private fun SupportAdEvent.toUiEvent(): SupportArchiveTuneUiEvent =
            when (this) {
                SupportAdEvent.RewardEarned -> SupportArchiveTuneUiEvent.RewardEarned
                SupportAdEvent.AdFailed -> SupportArchiveTuneUiEvent.AdFailed
                SupportAdEvent.ActivityUnavailable -> SupportArchiveTuneUiEvent.ActivityUnavailable
            }
    }
