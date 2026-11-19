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
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.playback.notification.MediaNotificationAction
import moe.rukamori.archivetune.playback.notification.MediaNotificationConfiguration
import moe.rukamori.archivetune.playback.notification.MediaNotificationNavigation
import moe.rukamori.archivetune.playback.notification.MediaNotificationSettingsRepository
import moe.rukamori.archivetune.playback.notification.MediaNotificationSystemStatus
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface MediaNotificationSettingsState {
    data object Loading : MediaNotificationSettingsState

    data class Error(@param:StringRes val messageRes: Int) : MediaNotificationSettingsState

    data class Ready(
        val configuration: MediaNotificationConfiguration,
        val systemStatus: MediaNotificationSystemStatus,
        val availableActions: List<MediaNotificationAction>,
        val isReordering: Boolean,
    ) : MediaNotificationSettingsState {
        val canAddAction: Boolean
            get() = configuration.actions.size < MediaNotificationAction.MAX_ACTIONS && availableActions.isNotEmpty()

        val isDefault: Boolean
            get() = configuration == MediaNotificationConfiguration()
    }
}

sealed interface MediaNotificationSettingsEvent {
    data class Message(@param:StringRes val messageRes: Int) : MediaNotificationSettingsEvent
}

@HiltViewModel
class MediaNotificationSettingsViewModel @Inject constructor(
    private val repository: MediaNotificationSettingsRepository,
) : ViewModel() {
    private val pendingOrder = MutableStateFlow<List<MediaNotificationAction>?>(null)
    private val systemStatus = MutableStateFlow(repository.systemStatus())
    private val eventChannel = Channel<MediaNotificationSettingsEvent>(Channel.BUFFERED)

    val events = eventChannel.receiveAsFlow()

    val state: StateFlow<MediaNotificationSettingsState> =
        combine(repository.configuration, pendingOrder, systemStatus) { configuration, pending, status ->
            val effective = pending?.let { configuration.copy(actions = it) } ?: configuration
            MediaNotificationSettingsState.Ready(
                configuration = effective,
                systemStatus = status,
                availableActions = MediaNotificationAction.entries.filterNot { it in effective.actions },
                isReordering = pending != null,
            ) as MediaNotificationSettingsState
        }.catch { error ->
            if (error is CancellationException) throw error
            Timber.e(error, "Failed to observe media notification settings")
            emit(MediaNotificationSettingsState.Error(R.string.media_notification_settings_load_failed))
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MediaNotificationSettingsState.Loading)

    private val readyState: MediaNotificationSettingsState.Ready?
        get() = state.value as? MediaNotificationSettingsState.Ready

    fun refreshSystemStatus() {
        systemStatus.value = repository.systemStatus()
    }

    fun addAction(action: MediaNotificationAction) {
        persist { repository.updateActions { current -> if (action in current) current else current + action } }
    }

    fun removeAction(action: MediaNotificationAction) {
        persist { repository.updateActions { current -> current - action } }
    }

    fun moveActionBy(
        action: MediaNotificationAction,
        offset: Int,
    ) {
        persist {
            repository.updateActions { current ->
                val from = current.indexOf(action)
                val to = from + offset
                if (from < 0 || to !in current.indices) current else current.toMutableList().apply { add(to, removeAt(from)) }
            }
        }
    }

    fun onReorder(
        fromIndex: Int,
        toIndex: Int,
    ) {
        val base = pendingOrder.value ?: readyState?.configuration?.actions ?: return
        if (fromIndex !in base.indices || toIndex !in base.indices || fromIndex == toIndex) return
        pendingOrder.value = base.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
    }

    fun onReorderFinished() {
        val order = pendingOrder.value ?: return
        viewModelScope.launch {
            try {
                repository.updateActions { current ->
                    if (current.toSet() == order.toSet()) order else current
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Failed to save media notification button order")
                eventChannel.trySend(MediaNotificationSettingsEvent.Message(R.string.media_notification_settings_save_failed))
            } finally {
                pendingOrder.update { if (it == order) null else it }
            }
        }
    }

    fun setNavigation(navigation: MediaNotificationNavigation) {
        persist { repository.setNavigation(navigation) }
    }

    fun setSeekIncrement(seconds: Int) {
        persist { repository.setSeekIncrement(seconds) }
    }

    fun setSleepTimerMinutes(minutes: Int) {
        persist { repository.setSleepTimerMinutes(minutes) }
    }

    fun setStopOnDismiss(enabled: Boolean) {
        persist { repository.setStopOnDismiss(enabled) }
    }

    fun resetToDefaults() {
        pendingOrder.value = null
        persist(successMessage = R.string.media_notification_reset_done) { repository.resetToDefaults() }
    }

    fun onExternalActionFailed() {
        eventChannel.trySend(MediaNotificationSettingsEvent.Message(R.string.open_app_settings_error))
    }

    private fun persist(
        @StringRes successMessage: Int? = null,
        block: suspend () -> Unit,
    ) {
        viewModelScope.launch {
            try {
                block()
                successMessage?.let { eventChannel.trySend(MediaNotificationSettingsEvent.Message(it)) }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Failed to save media notification settings")
                eventChannel.trySend(MediaNotificationSettingsEvent.Message(R.string.media_notification_settings_save_failed))
            }
        }
    }
}
