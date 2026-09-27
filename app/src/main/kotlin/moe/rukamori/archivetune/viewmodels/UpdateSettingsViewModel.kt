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
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.updates.ObserveUpdateSettingsUseCase
import moe.rukamori.archivetune.updates.UpdateUpdateSettingsUseCase
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface UpdateSettingsScreenState {
    data object Loading : UpdateSettingsScreenState

    data class Success(
        val model: UpdateSettingsUiModel,
    ) : UpdateSettingsScreenState

    data object Empty : UpdateSettingsScreenState

    data class Error(
        @param:StringRes val messageRes: Int,
        val lastKnownModel: UpdateSettingsUiModel,
    ) : UpdateSettingsScreenState
}

@Immutable
data class UpdateSettingsUiModel(
    val automaticChecksEnabled: Boolean,
    val notificationsEnabled: Boolean,
)

sealed interface UpdateSettingsAction {
    data class SetAutomaticChecksEnabled(val enabled: Boolean) : UpdateSettingsAction
    data class SetNotificationsEnabled(val enabled: Boolean) : UpdateSettingsAction
    data object DismissError : UpdateSettingsAction
}

@HiltViewModel
class UpdateSettingsViewModel
    @Inject
    constructor(
        observeSettings: ObserveUpdateSettingsUseCase,
        private val updateSettings: UpdateUpdateSettingsUseCase,
    ) : ViewModel() {
        private val error = MutableStateFlow<Int?>(null)
        private var automaticChecksJob: Job? = null
        private var notificationsJob: Job? = null

        val state: StateFlow<UpdateSettingsScreenState> =
            combine(observeSettings(), error) { settings, errorRes ->
                val model =
                    UpdateSettingsUiModel(
                        automaticChecksEnabled = settings.automaticChecksEnabled,
                        notificationsEnabled = settings.notificationsEnabled,
                    )
                errorRes?.let { UpdateSettingsScreenState.Error(it, model) }
                    ?: UpdateSettingsScreenState.Success(model)
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "Failed to observe update settings")
                emit(
                    UpdateSettingsScreenState.Error(
                        messageRes = R.string.error_unknown,
                        lastKnownModel = DEFAULT_MODEL,
                    ),
                )
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = UpdateSettingsScreenState.Loading,
            )

        fun onAction(action: UpdateSettingsAction) {
            when (action) {
                is UpdateSettingsAction.SetAutomaticChecksEnabled -> setAutomaticChecksEnabled(action.enabled)
                is UpdateSettingsAction.SetNotificationsEnabled -> setNotificationsEnabled(action.enabled)
                UpdateSettingsAction.DismissError -> error.value = null
            }
        }

        private fun setAutomaticChecksEnabled(enabled: Boolean) {
            automaticChecksJob?.cancel()
            automaticChecksJob = launchUpdate {
                updateSettings.setAutomaticChecksEnabled(enabled)
            }
        }

        private fun setNotificationsEnabled(enabled: Boolean) {
            notificationsJob?.cancel()
            notificationsJob = launchUpdate {
                updateSettings.setNotificationsEnabled(enabled)
            }
        }

        private fun launchUpdate(block: suspend () -> Unit): Job =
            viewModelScope.launch {
                error.value = null
                try {
                    block()
                } catch (throwable: CancellationException) {
                    throw throwable
                } catch (throwable: Exception) {
                    Timber.e(throwable, "Failed to update automatic update settings")
                    error.update { R.string.error_unknown }
                }
            }

        private companion object {
            val DEFAULT_MODEL =
                UpdateSettingsUiModel(
                    automaticChecksEnabled = true,
                    notificationsEnabled = false,
                )
        }
    }
