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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.androidauto.AndroidAutoActionSlot
import moe.rukamori.archivetune.androidauto.AndroidAutoCustomAction
import moe.rukamori.archivetune.androidauto.AndroidAutoSettingsSnapshot
import moe.rukamori.archivetune.androidauto.AndroidAutoSettingsUseCases
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface AndroidAutoSettingsState {
    data object Loading : AndroidAutoSettingsState
    data class Success(val model: AndroidAutoSettingsUiModel) : AndroidAutoSettingsState
    data object Empty : AndroidAutoSettingsState
    data class Error(@param:StringRes val messageRes: Int) : AndroidAutoSettingsState
}

@Immutable
data class AndroidAutoSettingsUiModel(
    val snapshot: AndroidAutoSettingsSnapshot,
    val busy: Boolean,
)

sealed interface AndroidAutoSettingsAction {
    data class SetOnlineRecommendations(val enabled: Boolean) : AndroidAutoSettingsAction
    data class SetOnlineVoiceSearch(val enabled: Boolean) : AndroidAutoSettingsAction
    data class SetLocalSongs(val enabled: Boolean) : AndroidAutoSettingsAction
    data class SetMeteredPlayback(val enabled: Boolean) : AndroidAutoSettingsAction
    data class SetMeteredArtwork(val enabled: Boolean) : AndroidAutoSettingsAction
    data class SetAction(
        val slot: AndroidAutoActionSlot,
        val action: AndroidAutoCustomAction,
    ) : AndroidAutoSettingsAction
    data object RequestAudioPermission : AndroidAutoSettingsAction
    data object OpenAppPermissions : AndroidAutoSettingsAction
    data object ExternalActionFailed : AndroidAutoSettingsAction
    data object PermissionResult : AndroidAutoSettingsAction
    data object Retry : AndroidAutoSettingsAction
}

sealed interface AndroidAutoSettingsEvent {
    data object RequestAudioPermission : AndroidAutoSettingsEvent
    data object OpenAppPermissions : AndroidAutoSettingsEvent
}

@HiltViewModel
class AndroidAutoSettingsViewModel @Inject constructor(
    private val useCases: AndroidAutoSettingsUseCases,
) : ViewModel() {
    private data class Controls(
        val busy: Boolean = false,
        @param:StringRes val error: Int? = null,
    )

    private val controls = MutableStateFlow(Controls())
    private val permissionVersion = MutableStateFlow(0L)
    private val eventChannel = Channel<AndroidAutoSettingsEvent>(Channel.BUFFERED)
    private var updateJob: Job? = null

    val events = eventChannel.receiveAsFlow()

    val state = combine(
        useCases.observeSettings(),
        controls,
        permissionVersion,
    ) { snapshot, controls, _ ->
        controls.error?.let(AndroidAutoSettingsState::Error)
            ?: AndroidAutoSettingsState.Success(
                AndroidAutoSettingsUiModel(
                    snapshot = snapshot.copy(hasLocalAudioPermission = useCases.hasLocalAudioPermission()),
                    busy = controls.busy,
                ),
            )
    }.catch { error ->
        if (error is CancellationException) throw error
        Timber.e(error, "Failed to observe Android Auto settings")
        emit(AndroidAutoSettingsState.Error(R.string.android_auto_settings_load_failed))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AndroidAutoSettingsState.Loading)

    fun onAction(action: AndroidAutoSettingsAction) {
        when (action) {
            AndroidAutoSettingsAction.RequestAudioPermission -> eventChannel.trySend(AndroidAutoSettingsEvent.RequestAudioPermission)
            AndroidAutoSettingsAction.OpenAppPermissions -> eventChannel.trySend(AndroidAutoSettingsEvent.OpenAppPermissions)
            AndroidAutoSettingsAction.ExternalActionFailed -> controls.update {
                it.copy(error = R.string.open_app_settings_error)
            }
            AndroidAutoSettingsAction.PermissionResult -> permissionVersion.update { it + 1 }
            AndroidAutoSettingsAction.Retry -> controls.update { it.copy(error = null) }
            else -> update(action)
        }
    }

    private fun update(action: AndroidAutoSettingsAction) {
        if (updateJob?.isActive == true) return
        val model = (state.value as? AndroidAutoSettingsState.Success)?.model ?: return
        controls.update { it.copy(busy = true, error = null) }
        updateJob = viewModelScope.launch {
            try {
                when (action) {
                    is AndroidAutoSettingsAction.SetOnlineRecommendations ->
                        useCases.setOnlineRecommendations(action.enabled)
                    is AndroidAutoSettingsAction.SetOnlineVoiceSearch -> useCases.setOnlineVoiceSearch(action.enabled)
                    is AndroidAutoSettingsAction.SetLocalSongs -> useCases.setLocalSongs(action.enabled)
                    is AndroidAutoSettingsAction.SetMeteredPlayback -> useCases.setMeteredPlayback(action.enabled)
                    is AndroidAutoSettingsAction.SetMeteredArtwork -> useCases.setMeteredArtwork(action.enabled)
                    is AndroidAutoSettingsAction.SetAction ->
                        useCases.setAction(action.slot, action.action, model.snapshot.configuration)
                    else -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Android Auto settings action failed")
                controls.update { it.copy(error = R.string.android_auto_settings_update_failed) }
            } finally {
                controls.update { it.copy(busy = false) }
            }
        }
    }
}
