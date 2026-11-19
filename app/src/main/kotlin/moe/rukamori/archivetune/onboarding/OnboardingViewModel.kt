/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.onboarding

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import java.io.IOException
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class OnboardingViewModel
    @Inject
    constructor(
        private val savedStateHandle: SavedStateHandle,
        observeOnboardingDataUseCase: ObserveOnboardingDataUseCase,
        buildOnboardingUiStateUseCase: BuildOnboardingUiStateUseCase,
        private val completeOnboardingUseCase: CompleteOnboardingUseCase,
    ) : ViewModel() {
        private val currentPage = savedStateHandle.getStateFlow("onboardingPage", 0)
        private val blockedPermissions = savedStateHandle.getStateFlow("blockedPermissions", arrayListOf<String>())
        private val refreshSignals = MutableStateFlow(0)
        private val retrySignals = MutableStateFlow(0)
        private val interaction = MutableStateFlow(InteractionState())
        private val eventChannel = Channel<OnboardingEvent>(Channel.BUFFERED)

        val events = eventChannel.receiveAsFlow()

        private val loadedState =
            retrySignals.flatMapLatest {
                observeOnboardingDataUseCase(refreshSignals)
                    .map<OnboardingData, OnboardingScreenState> { data ->
                        val uiState = buildOnboardingUiStateUseCase(data, 0)
                        if (uiState.pages.isEmpty()) {
                            OnboardingScreenState.Empty
                        } else {
                            OnboardingScreenState.Success(uiState)
                        }
                    }.onStart {
                        if (screenState.value !is OnboardingScreenState.Success) {
                            emit(OnboardingScreenState.Loading)
                        }
                    }
                    .catch { error ->
                        if (error !is IOException) throw error
                        emit(OnboardingScreenState.Error(R.string.onboarding_error_generic))
                    }
            }

        val screenState: StateFlow<OnboardingScreenState> =
            combine(loadedState, currentPage, blockedPermissions, interaction) { loaded, page, blocked, actionState ->
                if (loaded is OnboardingScreenState.Success) {
                    OnboardingScreenState.Success(
                        loaded.uiState.copy(
                            currentPage = page.coerceIn(0, loaded.uiState.pages.lastIndex),
                            permissions =
                                ImmutableList.copyOf(
                                    loaded.uiState.permissions.map { permission ->
                                        val action = permission.action
                                        if (
                                            action is OnboardingPermissionAction.RequestRuntimePermission &&
                                            action.permission in blocked
                                        ) {
                                            permission.copy(
                                                status = OnboardingPermissionStatus.BLOCKED,
                                                action = OnboardingPermissionAction.OpenAppSettings,
                                            )
                                        } else {
                                            permission
                                        }
                                    },
                                ),
                            isBusy = actionState.pendingEvent != null || actionState.isCompleting,
                            isCompleting = actionState.isCompleting,
                            completionErrorResId = actionState.completionErrorResId,
                            actionErrorId = actionState.failureCount,
                            actionErrorResId =
                                actionState.failedEvent?.let { event ->
                                    if (event is OnboardingEvent.OpenUri) {
                                        R.string.onboarding_error_open_link
                                    } else {
                                        R.string.onboarding_error_open_permission
                                    }
                                },
                        ),
                    )
                } else {
                    loaded
                }
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = OnboardingScreenState.Loading,
            )

        fun onNext(page: OnboardingPageId) {
            val state = (screenState.value as? OnboardingScreenState.Success)?.uiState ?: return
            if (isBusy() || state.pages.getOrNull(currentPage.value)?.id != page) return
            if (currentPage.value < state.pages.lastIndex) {
                savedStateHandle["onboardingPage"] = currentPage.value + 1
                dismissActionError()
            } else {
                complete()
            }
        }

        fun onBack(page: OnboardingPageId) {
            val state = (screenState.value as? OnboardingScreenState.Success)?.uiState ?: return
            if (isBusy() || state.pages.getOrNull(currentPage.value)?.id != page) return
            savedStateHandle["onboardingPage"] = (currentPage.value - 1).coerceAtLeast(0)
            dismissActionError()
        }

        fun retryLoading() {
            retrySignals.update { it + 1 }
        }

        fun onPermissionAction(action: OnboardingPermissionAction) {
            val event =
                when (action) {
                    is OnboardingPermissionAction.RequestRuntimePermission -> OnboardingEvent.RequestPermission(action.permission)
                    OnboardingPermissionAction.OpenAppSettings -> OnboardingEvent.OpenAppSettings
                    OnboardingPermissionAction.OpenInstallPackagesSettings -> OnboardingEvent.OpenInstallPackagesSettings
                }
            launchExternalAction(event)
        }

        fun onCommunityAction(action: OnboardingCommunityActionUiModel) {
            launchExternalAction(OnboardingEvent.OpenUri(action.url))
        }

        fun onLogin() {
            launchExternalAction(OnboardingEvent.OpenLogin)
        }

        fun onLoginCompleted() {
            interaction.update { it.copy(pendingEvent = null) }
            val state = (screenState.value as? OnboardingScreenState.Success)?.uiState ?: return
            if (state.pages.getOrNull(currentPage.value)?.id == OnboardingPageId.LOGIN) {
                savedStateHandle["onboardingPage"] = currentPage.value + 1
            }
        }

        fun onLoginDismissed() {
            interaction.update { it.copy(pendingEvent = null) }
        }

        fun refreshPermissions() {
            refreshSignals.update { it + 1 }
        }

        fun onPermissionResult(permission: String?, granted: Boolean, canRequestAgain: Boolean) {
            if (permission != null) {
                val blocked = blockedPermissions.value.toMutableSet()
                if (!granted && !canRequestAgain) {
                    blocked.add(permission)
                } else {
                    blocked.remove(permission)
                }
                savedStateHandle["blockedPermissions"] = ArrayList(blocked)
            }
            interaction.update { it.copy(pendingEvent = null) }
            refreshPermissions()
        }

        fun onSettingsResult() {
            interaction.update { it.copy(pendingEvent = null) }
            refreshPermissions()
        }

        fun onExternalActionFinished(event: OnboardingEvent) {
            interaction.update { if (it.pendingEvent == event) it.copy(pendingEvent = null) else it }
        }

        fun onExternalActionFailed(event: OnboardingEvent) {
            interaction.update { it.copy(pendingEvent = null, failedEvent = event, failureCount = it.failureCount + 1) }
        }

        fun retryExternalAction() {
            val event = interaction.value.failedEvent ?: return
            launchExternalAction(event)
        }

        fun dismissActionError() {
            interaction.update { it.copy(failedEvent = null) }
        }

        private fun isBusy(): Boolean = interaction.value.let { it.pendingEvent != null || it.isCompleting }

        private fun launchExternalAction(event: OnboardingEvent) {
            if (isBusy()) return
            interaction.update { it.copy(pendingEvent = event, failedEvent = null) }
            viewModelScope.launch { eventChannel.send(event) }
        }

        private fun complete() {
            if (isBusy()) return
            interaction.update { it.copy(isCompleting = true, completionErrorResId = null, failedEvent = null) }
            viewModelScope.launch {
                try {
                    completeOnboardingUseCase()
                } catch (_: IOException) {
                    interaction.update { it.copy(isCompleting = false, completionErrorResId = R.string.onboarding_error_save) }
                }
            }
        }

        private data class InteractionState(
            val pendingEvent: OnboardingEvent? = null,
            val failedEvent: OnboardingEvent? = null,
            val isCompleting: Boolean = false,
            val completionErrorResId: Int? = null,
            val failureCount: Int = 0,
        )
    }
