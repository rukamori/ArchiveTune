/*
 * ArchiveTune (2026)
 * © Rukamori — github.com/rukamori
 * GPL-3.0 License | Contributors: see git history
 * Do not remove or alter this notice. - Per GPL-3.0 Section 4 & Section 5
 */

package moe.rukamori.archivetune.viewmodels

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.LastFmProvider
import moe.rukamori.archivetune.lastfm.LastFM
import moe.rukamori.archivetune.scrobbling.LastFmServiceConfig
import moe.rukamori.archivetune.scrobbling.LastFmSettingsData
import moe.rukamori.archivetune.scrobbling.LoginLastFmUseCase
import moe.rukamori.archivetune.scrobbling.LogoutLastFmUseCase
import moe.rukamori.archivetune.scrobbling.ObserveLastFmSettingsUseCase
import moe.rukamori.archivetune.scrobbling.SaveLastFmServiceConfigUseCase
import moe.rukamori.archivetune.scrobbling.UpdateLastFmScrobblingOptionsUseCase
import timber.log.Timber
import javax.inject.Inject

sealed interface LastFmSettingsScreenState {
    data object Loading : LastFmSettingsScreenState

    data class Success(
        val model: LastFmSettingsUiModel,
    ) : LastFmSettingsScreenState

    data object Empty : LastFmSettingsScreenState

    data class Error(
        val messageResId: Int,
    ) : LastFmSettingsScreenState
}

@Immutable
data class LastFmSettingsUiModel(
    val provider: LastFmProvider,
    val resolvedEndpoint: String,
    val customEndpoint: String,
    val apiKeyOverride: String,
    val secretOverride: String,
    val serviceConfigured: Boolean,
    val endpointValid: Boolean,
    val username: String,
    val isLoggedIn: Boolean,
    val scrobblingEnabled: Boolean,
    val nowPlayingEnabled: Boolean,
    val minTrackDurationSeconds: Int,
    val scrobbleDelayPercent: Float,
    val scrobbleDelaySeconds: Int,
    val loginDialog: LastFmLoginDialogUiModel,
    val serviceEditor: LastFmServiceEditorUiModel,
    val timingEditor: LastFmTimingEditorUiModel,
) {
    val canLogin: Boolean
        get() = serviceConfigured && endpointValid

    val canEnableScrobbling: Boolean
        get() = isLoggedIn && serviceConfigured && endpointValid
}

@Immutable
data class LastFmLoginDialogUiModel(
    val visible: Boolean = false,
    val username: String = "",
    val password: String = "",
    val isLoggingIn: Boolean = false,
    val errorMessageResId: Int? = null,
)

@Immutable
data class LastFmServiceEditorUiModel(
    val visible: Boolean = false,
    val provider: LastFmProvider = LastFmProvider.LASTFM,
    val customEndpoint: String = "",
    val apiKeyOverride: String = "",
    val secretOverride: String = "",
    val isSaving: Boolean = false,
    val errorMessageResId: Int? = null,
) {
    val showCustomEndpoint: Boolean
        get() = provider == LastFmProvider.CUSTOM

    val showApiCredentials: Boolean
        get() = provider != LastFmProvider.LASTFM
}

enum class LastFmTimingSetting {
    MIN_TRACK_DURATION,
    DELAY_PERCENT,
    DELAY_SECONDS,
}

@Immutable
data class LastFmTimingEditorUiModel(
    val setting: LastFmTimingSetting? = null,
    val minTrackDurationSeconds: Int = LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
    val scrobbleDelayPercent: Float = LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
    val scrobbleDelaySeconds: Int = LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS,
) {
    val visible: Boolean
        get() = setting != null
}

@HiltViewModel
class LastFmSettingsViewModel
    @Inject
    constructor(
        observeSettings: ObserveLastFmSettingsUseCase,
        private val loginLastFm: LoginLastFmUseCase,
        private val logoutLastFm: LogoutLastFmUseCase,
        private val saveServiceConfig: SaveLastFmServiceConfigUseCase,
        private val updateOptions: UpdateLastFmScrobblingOptionsUseCase,
    ) : ViewModel() {
        private val loginDialog = MutableStateFlow(LastFmLoginDialogUiModel())
        private val serviceEditor = MutableStateFlow(LastFmServiceEditorUiModel())
        private val timingEditor = MutableStateFlow(LastFmTimingEditorUiModel())
        private var loginJob: Job? = null

        val state: StateFlow<LastFmSettingsScreenState> =
            combine(
                observeSettings(),
                loginDialog,
                serviceEditor,
                timingEditor,
            ) { settings, login, editor, timing ->
                LastFmSettingsStatePayload(
                    settings = settings,
                    loginDialog = login,
                    serviceEditor = editor,
                    timingEditor = timing,
                )
            }.map<LastFmSettingsStatePayload, LastFmSettingsScreenState> { payload ->
                LastFmSettingsScreenState.Success(payload.toUiModel())
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                Timber.e(throwable, "Failed to load Last.fm settings")
                emit(LastFmSettingsScreenState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = LastFmSettingsScreenState.Loading,
            )

        fun openLoginDialog() {
            loginDialog.value = LastFmLoginDialogUiModel(visible = true)
        }

        fun dismissLoginDialog() {
            if (!loginDialog.value.isLoggingIn) {
                loginDialog.value = LastFmLoginDialogUiModel()
            }
        }

        fun updateLoginUsername(value: String) {
            loginDialog.update { dialog ->
                dialog.copy(username = value, errorMessageResId = null)
            }
        }

        fun updateLoginPassword(value: String) {
            loginDialog.update { dialog ->
                dialog.copy(password = value, errorMessageResId = null)
            }
        }

        fun login() {
            if (loginJob?.isActive == true) return
            val dialog = loginDialog.value
            val model = currentModel()
            if (dialog.username.isBlank() || dialog.password.isBlank()) {
                loginDialog.update { it.copy(errorMessageResId = R.string.lastfm_login_missing_credentials) }
                return
            }
            if (model?.canLogin != true) {
                loginDialog.update { it.copy(errorMessageResId = R.string.lastfm_service_not_configured) }
                return
            }

            loginJob =
                viewModelScope.launch(Dispatchers.IO) {
                    loginDialog.update {
                        it.copy(isLoggingIn = true, errorMessageResId = null)
                    }
                    loginLastFm(dialog.username, dialog.password)
                        .onSuccess {
                            loginDialog.value = LastFmLoginDialogUiModel()
                        }.onFailure { throwable ->
                            if (throwable is CancellationException) throw throwable
                            Timber.e(throwable, "Last.fm-compatible login failed")
                            loginDialog.update {
                                it.copy(
                                    isLoggingIn = false,
                                    errorMessageResId = throwable.loginErrorResId(),
                                )
                            }
                        }
                }
        }

        fun logout() {
            viewModelScope.launch(Dispatchers.IO) {
                logoutLastFm()
            }
        }

        fun openServiceEditor() {
            val model = currentModel() ?: return
            serviceEditor.value =
                LastFmServiceEditorUiModel(
                    visible = true,
                    provider = model.provider,
                    customEndpoint =
                        model.customEndpoint.ifBlank {
                            if (model.provider == LastFmProvider.CUSTOM) model.resolvedEndpoint else ""
                        },
                    apiKeyOverride = model.apiKeyOverride,
                    secretOverride = model.secretOverride,
                )
        }

        fun dismissServiceEditor() {
            if (!serviceEditor.value.isSaving) {
                serviceEditor.value = LastFmServiceEditorUiModel()
            }
        }

        fun updateServiceProvider(provider: LastFmProvider) {
            serviceEditor.update { editor ->
                editor.copy(provider = provider, errorMessageResId = null)
            }
        }

        fun updateCustomEndpoint(value: String) {
            serviceEditor.update { editor ->
                editor.copy(customEndpoint = value, errorMessageResId = null)
            }
        }

        fun updateApiKeyOverride(value: String) {
            serviceEditor.update { editor ->
                editor.copy(apiKeyOverride = value, errorMessageResId = null)
            }
        }

        fun updateSecretOverride(value: String) {
            serviceEditor.update { editor ->
                editor.copy(secretOverride = value, errorMessageResId = null)
            }
        }

        fun saveServiceEditor() {
            val editor = serviceEditor.value
            if (editor.isSaving) return
            if (editor.provider == LastFmProvider.CUSTOM &&
                LastFmServiceConfig.normalizeEndpointOrNull(editor.customEndpoint) == null
            ) {
                serviceEditor.update { it.copy(errorMessageResId = R.string.lastfm_endpoint_invalid) }
                return
            }

            viewModelScope.launch(Dispatchers.IO) {
                serviceEditor.update {
                    it.copy(isSaving = true, errorMessageResId = null)
                }
                val saved =
                    saveServiceConfig(
                        provider = editor.provider,
                        customEndpoint = editor.customEndpoint,
                        apiKeyOverride = editor.apiKeyOverride,
                        secretOverride = editor.secretOverride,
                    )
                if (saved == null) {
                    serviceEditor.update {
                        it.copy(
                            isSaving = false,
                            errorMessageResId = R.string.lastfm_endpoint_invalid,
                        )
                    }
                } else {
                    serviceEditor.value = LastFmServiceEditorUiModel()
                }
            }
        }

        fun setScrobblingEnabled(enabled: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setScrobblingEnabled(enabled)
            }
        }

        fun setNowPlayingEnabled(enabled: Boolean) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setNowPlayingEnabled(enabled)
            }
        }

        fun setMinTrackDurationSeconds(value: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setMinTrackDurationSeconds(value.coerceIn(10, 60))
            }
        }

        fun setScrobbleDelayPercent(value: Float) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setScrobbleDelayPercent(value.coerceIn(0.3f, 0.95f))
            }
        }

        fun setScrobbleDelaySeconds(value: Int) {
            viewModelScope.launch(Dispatchers.IO) {
                updateOptions.setScrobbleDelaySeconds(value.coerceIn(30, 360))
            }
        }

        fun openTimingEditor(setting: LastFmTimingSetting) {
            val model = currentModel() ?: return
            timingEditor.value =
                LastFmTimingEditorUiModel(
                    setting = setting,
                    minTrackDurationSeconds = model.minTrackDurationSeconds,
                    scrobbleDelayPercent = model.scrobbleDelayPercent,
                    scrobbleDelaySeconds = model.scrobbleDelaySeconds,
                )
        }

        fun dismissTimingEditor() {
            timingEditor.value = LastFmTimingEditorUiModel()
        }

        fun updateTimingMinTrackDuration(value: Int) {
            timingEditor.update { editor ->
                editor.copy(minTrackDurationSeconds = value.coerceIn(10, 60))
            }
        }

        fun updateTimingDelayPercent(value: Float) {
            timingEditor.update { editor ->
                editor.copy(scrobbleDelayPercent = value.coerceIn(0.3f, 0.95f))
            }
        }

        fun updateTimingDelaySeconds(value: Int) {
            timingEditor.update { editor ->
                editor.copy(scrobbleDelaySeconds = value.coerceIn(30, 360))
            }
        }

        fun saveTimingEditor() {
            val editor = timingEditor.value
            when (editor.setting) {
                LastFmTimingSetting.MIN_TRACK_DURATION -> setMinTrackDurationSeconds(editor.minTrackDurationSeconds)
                LastFmTimingSetting.DELAY_PERCENT -> setScrobbleDelayPercent(editor.scrobbleDelayPercent)
                LastFmTimingSetting.DELAY_SECONDS -> setScrobbleDelaySeconds(editor.scrobbleDelaySeconds)
                null -> return
            }
            timingEditor.value = LastFmTimingEditorUiModel()
        }

        private fun currentModel(): LastFmSettingsUiModel? = (state.value as? LastFmSettingsScreenState.Success)?.model

        private fun LastFmSettingsStatePayload.toUiModel(): LastFmSettingsUiModel =
            LastFmSettingsUiModel(
                provider = settings.serviceConfig.provider,
                resolvedEndpoint = settings.serviceConfig.endpoint,
                customEndpoint = settings.serviceConfig.customEndpoint,
                apiKeyOverride = settings.serviceConfig.apiKeyOverride,
                secretOverride = settings.serviceConfig.secretOverride,
                serviceConfigured = settings.serviceConfig.initialized,
                endpointValid = settings.serviceConfig.endpointValid,
                username = settings.username,
                isLoggedIn = settings.isLoggedIn,
                scrobblingEnabled = settings.scrobblingEnabled,
                nowPlayingEnabled = settings.nowPlayingEnabled,
                minTrackDurationSeconds = settings.minTrackDurationSeconds,
                scrobbleDelayPercent = settings.scrobbleDelayPercent,
                scrobbleDelaySeconds = settings.scrobbleDelaySeconds,
                loginDialog = this.loginDialog,
                serviceEditor = this.serviceEditor,
                timingEditor = this.timingEditor,
            )

        private fun Throwable.loginErrorResId(): Int =
            when (this) {
                is LastFM.LastFmException -> {
                    when (code) {
                        4 -> R.string.lastfm_login_invalid_credentials
                        10 -> R.string.lastfm_login_invalid_api_key
                        13 -> R.string.lastfm_login_authentication_error
                        26 -> R.string.lastfm_login_api_key_suspended
                        else -> R.string.lastfm_login_failed
                    }
                }

                else -> {
                    val message = message.orEmpty()
                    if (
                        message.contains("network", ignoreCase = true) ||
                        message.contains("connect", ignoreCase = true) ||
                        message.contains("timeout", ignoreCase = true)
                    ) {
                        R.string.lastfm_login_network_error
                    } else {
                        R.string.lastfm_login_failed
                    }
                }
            }
    }

private data class LastFmSettingsStatePayload(
    val settings: LastFmSettingsData,
    val loginDialog: LastFmLoginDialogUiModel,
    val serviceEditor: LastFmServiceEditorUiModel,
    val timingEditor: LastFmTimingEditorUiModel,
)
