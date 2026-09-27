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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.canvas.CanvasConfiguration
import moe.rukamori.archivetune.canvas.CanvasHealthStatus
import moe.rukamori.archivetune.canvas.CanvasSettingsUseCases
import moe.rukamori.archivetune.canvas.CanvasSource
import moe.rukamori.archivetune.ui.utils.formatFileSize
import timber.log.Timber
import javax.inject.Inject

@Immutable
sealed interface CanvasSettingsState {
    data object Loading : CanvasSettingsState
    data class Success(val model: CanvasSettingsUiModel) : CanvasSettingsState
    data object Empty : CanvasSettingsState
    data class Error(@param:StringRes val messageRes: Int) : CanvasSettingsState
}

@Immutable
data class CanvasCacheOption(val limitMb: Int, val formattedSize: String)

@Immutable
data class CanvasCacheOptions(val values: List<CanvasCacheOption>)

@Immutable
data class CanvasSettingsUiModel(
    val configuration: CanvasConfiguration,
    val health: CanvasHealthStatus,
    val canRefreshHealth: Boolean,
    val cacheSize: String,
    val cacheLimit: String,
    val cacheProgress: Float,
    val cacheOptions: CanvasCacheOptions,
    val dialog: CanvasSettingsDialog?,
    val busy: Boolean,
)

enum class CanvasSettingsDialog { CLEAR_CACHE }

sealed interface CanvasSettingsAction {
    data class SetEnabled(val enabled: Boolean) : CanvasSettingsAction
    data class SelectSource(val source: CanvasSource) : CanvasSettingsAction
    data class SetWifiOnly(val wifiOnly: Boolean) : CanvasSettingsAction
    data class SetCacheLimit(val limitMb: Int) : CanvasSettingsAction
    data object ShowClearCache : CanvasSettingsAction
    data object DismissDialog : CanvasSettingsAction
    data object ClearCache : CanvasSettingsAction
    data object RefreshHealth : CanvasSettingsAction
    data object Retry : CanvasSettingsAction
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class CanvasSettingsViewModel @Inject constructor(
    private val useCases: CanvasSettingsUseCases,
) : ViewModel() {
    private data class Controls(
        val dialog: CanvasSettingsDialog? = null,
        val busy: Boolean = false,
        @param:StringRes val error: Int? = null,
    )

    private val controls = MutableStateFlow(Controls())
    private val refresh = MutableStateFlow(0L)
    private var actionJob: Job? = null
    private val options = CanvasCacheOptions(CanvasSettingsUseCases.CACHE_LIMITS.map { limit ->
        CanvasCacheOption(limit, formatFileSize(limit.coerceAtLeast(0) * 1024L * 1024L))
    })
    private val health = useCases.policy
        .map { it.copy(configuration = it.configuration.copy(cacheLimitMb = 0)) }
        .distinctUntilChanged()
        .combine(useCases.spotifyConnected) { policy, connected -> policy to connected }
        .transformLatest { (policy, connected) ->
            emit(useCases.pendingHealth(policy, connected))
            emit(useCases.checkHealth(policy, connected))
        }
    private val cacheBytes = flow {
        while (currentCoroutineContext().isActive) {
            emit(useCases.cacheBytes())
            delay(2_000)
        }
    }

    val state = refresh.flatMapLatest {
        combine(useCases.policy, health, cacheBytes, controls) { policy, health, bytes, controls ->
            when {
                controls.error != null -> CanvasSettingsState.Error(controls.error)
                policy.configurationError -> CanvasSettingsState.Error(R.string.canvas_settings_load_failed)
                !policy.ready -> CanvasSettingsState.Loading
                else -> {
                    val limit = policy.configuration.cacheLimitMb * 1024L * 1024L
                    CanvasSettingsState.Success(
                        CanvasSettingsUiModel(
                            configuration = policy.configuration,
                            health = health,
                            canRefreshHealth = policy.networkAllowed && !health.checking,
                            cacheSize = formatFileSize(bytes),
                            cacheLimit = formatFileSize(limit.coerceAtLeast(0)),
                            cacheProgress = if (limit > 0) (bytes.toDouble() / limit).toFloat().coerceIn(0f, 1f) else 0f,
                            cacheOptions = options,
                            dialog = controls.dialog,
                            busy = controls.busy,
                        ),
                    )
                }
            }
        }.catch { error ->
            if (error is CancellationException) throw error
            Timber.e(error, "Failed to observe canvas settings")
            emit(CanvasSettingsState.Error(R.string.canvas_settings_load_failed))
    }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CanvasSettingsState.Loading)

    fun onAction(action: CanvasSettingsAction) {
        when (action) {
            CanvasSettingsAction.DismissDialog -> controls.update { it.copy(dialog = null) }
            CanvasSettingsAction.ShowClearCache -> controls.update { it.copy(dialog = CanvasSettingsDialog.CLEAR_CACHE) }
            CanvasSettingsAction.RefreshHealth -> {
                if ((state.value as? CanvasSettingsState.Success)?.model?.canRefreshHealth == true) refresh.update { it + 1 }
            }
            CanvasSettingsAction.Retry -> {
                controls.update { it.copy(error = null) }
                refresh.update { it + 1 }
            }
            else -> update(action)
        }
    }

    private fun update(action: CanvasSettingsAction) {
        if (actionJob?.isActive == true) return
        controls.update { it.copy(busy = true, dialog = null, error = null) }
        actionJob = viewModelScope.launch {
            try {
                when (action) {
                    is CanvasSettingsAction.SetEnabled -> useCases.setEnabled(action.enabled)
                    is CanvasSettingsAction.SelectSource -> useCases.setSource(action.source)
                    is CanvasSettingsAction.SetWifiOnly -> useCases.setWifiOnly(action.wifiOnly)
                    is CanvasSettingsAction.SetCacheLimit -> useCases.setCacheLimit(action.limitMb)
                    CanvasSettingsAction.ClearCache -> useCases.clearCache()
                    else -> Unit
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                Timber.e(error, "Canvas settings action failed")
                controls.update { it.copy(error = R.string.canvas_settings_update_failed) }
            } finally {
                controls.update { it.copy(busy = false) }
            }
        }
    }
}
