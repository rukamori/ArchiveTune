/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        observeOnboardingDataUseCase: ObserveOnboardingDataUseCase,
        private val buildOnboardingUiStateUseCase: BuildOnboardingUiStateUseCase,
        private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    ) : ViewModel() {
        private val currentPage = MutableStateFlow(0)
        private val refreshSignals = MutableStateFlow(0)
        private val mutableEvents = MutableSharedFlow<OnboardingEvent>(extraBufferCapacity = 1)
        private var completionJob: Job? = null

        val events = mutableEvents.asSharedFlow()

        val screenState: StateFlow<OnboardingScreenState> =
            observeOnboardingDataUseCase(refreshSignals)
                .combine(currentPage) { data, page ->
                    val uiState = buildOnboardingUiStateUseCase(data, page)
                    if (uiState.pages.isEmpty()) {
                        OnboardingScreenState.Empty
                    } else {
                        OnboardingScreenState.Success(uiState)
                    }
                }.catch { emit(OnboardingScreenState.Error(R.string.onboarding_error_generic)) }
                .stateIn(
                    scope = viewModelScope,
                    started = SharingStarted.WhileSubscribed(5_000),
                    initialValue = OnboardingScreenState.Loading,
                )

        fun onNext() {
            val success = screenState.value as? OnboardingScreenState.Success ?: return
            val lastPage = success.uiState.pages.lastIndex
            if (success.uiState.currentPage < lastPage) {
                currentPage.value = success.uiState.currentPage + 1
            } else {
                complete()
            }
        }

        fun onBack() {
            val success = screenState.value as? OnboardingScreenState.Success ?: return
            currentPage.value = (success.uiState.currentPage - 1).coerceAtLeast(0)
        }

        fun onPermissionAction(action: OnboardingPermissionAction) {
            viewModelScope.launch {
                when (action) {
                    is OnboardingPermissionAction.RequestRuntimePermission -> {
                        mutableEvents.emit(OnboardingEvent.RequestPermission(action.permission))
                    }

                    OnboardingPermissionAction.OpenInstallPackagesSettings -> {
                        mutableEvents.emit(OnboardingEvent.OpenInstallPackagesSettings)
                    }
                }
            }
        }

        fun onCommunityAction(action: OnboardingCommunityActionUiModel) {
            viewModelScope.launch {
                mutableEvents.emit(OnboardingEvent.OpenUri(action.url))
            }
        }

        fun onPermissionResult() {
            refreshSignals.update { it + 1 }
        }

        fun complete() {
            if (completionJob?.isActive == true) return

            completionJob =
                viewModelScope.launch {
                    completeOnboardingUseCase()
                }
        }
    }
