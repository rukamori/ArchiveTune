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
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.aicontentfilter.AiContentFilterRefreshResult
import moe.rukamori.archivetune.aicontentfilter.ObserveAiContentFilterUseCase
import moe.rukamori.archivetune.aicontentfilter.RefreshAiContentFilterUseCase
import moe.rukamori.archivetune.aicontentfilter.UpdateAiContentFilterSettingsUseCase
import moe.rukamori.archivetune.db.MusicDatabase
import moe.rukamori.archivetune.lyrics.LyricsHelper
import moe.rukamori.archivetune.paxsenix.PaxsenixLyrics
import moe.rukamori.archivetune.paxsenix.models.PaxsenixStats
import moe.rukamori.archivetune.settings.AppLanguageOption
import moe.rukamori.archivetune.settings.AppLanguageSettingsUseCases
import timber.log.Timber
import javax.inject.Inject

sealed interface PaxsenixStatsState {
    data object Loading : PaxsenixStatsState

    data class Success(
        val stats: PaxsenixStats,
    ) : PaxsenixStatsState

    data object Error : PaxsenixStatsState
}

@Immutable
sealed interface AppLanguageSettingsState {
    data object Loading : AppLanguageSettingsState
    data class Success(val model: AppLanguageSettingsUiModel) : AppLanguageSettingsState
    data object Empty : AppLanguageSettingsState
    data class Error(val messageResId: Int) : AppLanguageSettingsState
}

@Immutable
data class AppLanguageSettingsUiModel(
    val useSystemLanguage: Boolean,
    val selectedLanguage: AppLanguageOption,
    val languages: ImmutableList<AppLanguageOption>,
    val saving: Boolean,
    val selectorVisible: Boolean,
)

sealed interface AiContentFilterSettingsState {
    data object Loading : AiContentFilterSettingsState

    data class Success(
        val model: AiContentFilterSettingsUiModel,
    ) : AiContentFilterSettingsState

    data object Empty : AiContentFilterSettingsState

    data class Error(
        val messageResId: Int,
    ) : AiContentFilterSettingsState
}

@Immutable
data class AiContentFilterSettingsUiModel(
    val enabled: Boolean,
    val includeModerateConfidence: Boolean,
    val blocklistCount: Int,
    val warnlistCount: Int,
    val refreshing: Boolean,
)

@Immutable
sealed interface AiContentFilterSettingsEffect {
    data class ShowMessage(
        val messageResId: Int,
    ) : AiContentFilterSettingsEffect

    data class OpenUrl(
        val url: String,
    ) : AiContentFilterSettingsEffect
}

@HiltViewModel
class ContentSettingsViewModel
    @Inject
    constructor(
        private val lyricsHelper: LyricsHelper,
        private val database: MusicDatabase,
        observeAiContentFilter: ObserveAiContentFilterUseCase,
        private val updateAiContentFilterSettings: UpdateAiContentFilterSettingsUseCase,
        private val refreshAiContentFilterLists: RefreshAiContentFilterUseCase,
        private val appLanguageSettings: AppLanguageSettingsUseCases,
    ) : ViewModel() {
        private val _appLanguageState = MutableStateFlow<AppLanguageSettingsState>(AppLanguageSettingsState.Loading)
        val appLanguageState = _appLanguageState.asStateFlow()
        private data class AppLanguageControls(
            val saving: Boolean = false,
            val selectorVisible: Boolean = false,
        )

        private val appLanguageControls = MutableStateFlow(AppLanguageControls())
        private var appLanguageObservationJob: Job? = null
        private var appLanguageUpdateJob: Job? = null
        private val _paxsenixStatsState = MutableStateFlow<PaxsenixStatsState>(PaxsenixStatsState.Loading)
        val paxsenixStatsState = _paxsenixStatsState.asStateFlow()
        private val refreshingAiContentFilter = MutableStateFlow(false)
        private val _aiContentFilterEffects = MutableSharedFlow<AiContentFilterSettingsEffect>(extraBufferCapacity = 1)
        val aiContentFilterEffects = _aiContentFilterEffects.asSharedFlow()
        private var aiContentFilterRefreshJob: Job? = null
        private var aiContentFilterEnabledJob: Job? = null
        private var aiContentFilterModerateJob: Job? = null

        val aiContentFilterState: StateFlow<AiContentFilterSettingsState> =
            combine(
                observeAiContentFilter(),
                refreshingAiContentFilter,
            ) { (settings, status), refreshing ->
                AiContentFilterSettingsUiModel(
                    enabled = settings.enabled,
                    includeModerateConfidence = settings.includeModerateConfidence,
                    blocklistCount = status.blocklistCount,
                    warnlistCount = status.warnlistCount,
                    refreshing = refreshing,
                )
            }.map<AiContentFilterSettingsUiModel, AiContentFilterSettingsState> { model ->
                AiContentFilterSettingsState.Success(model)
            }.catch { throwable ->
                if (throwable is CancellationException) throw throwable
                emit(AiContentFilterSettingsState.Error(R.string.error_unknown))
            }.stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5_000),
                initialValue = AiContentFilterSettingsState.Loading,
            )

        init {
            loadAppLanguageSettings()
            startAiContentFilterRefresh(force = false, showSuccess = false)
        }

        fun loadAppLanguageSettings() {
            appLanguageObservationJob?.cancel()
            _appLanguageState.value = AppLanguageSettingsState.Loading
            appLanguageObservationJob = viewModelScope.launch {
                combine(appLanguageSettings.observeSettings(), appLanguageControls) { settings, controls ->
                    AppLanguageSettingsUiModel(
                        useSystemLanguage = settings.useSystemLanguage,
                        selectedLanguage = appLanguageSettings.languages.first { it.languageTag == settings.languageTag },
                        languages = appLanguageSettings.languages,
                        saving = controls.saving,
                        selectorVisible = controls.selectorVisible && !settings.useSystemLanguage,
                    )
                }.catch { exception ->
                    if (exception is CancellationException) throw exception
                    Timber.e(exception, "Failed to observe app language settings")
                    _appLanguageState.value = AppLanguageSettingsState.Error(R.string.error_unknown)
                }.collect { model ->
                    _appLanguageState.value = AppLanguageSettingsState.Success(model)
                }
            }
        }

        fun setUseSystemLanguage(enabled: Boolean) {
            val model = (_appLanguageState.value as? AppLanguageSettingsState.Success)?.model ?: return
            if (model.useSystemLanguage == enabled) return
            updateAppLanguage { appLanguageSettings.setUseSystemLanguage(enabled) }
        }

        fun setAppLanguage(language: AppLanguageOption) {
            val model = (_appLanguageState.value as? AppLanguageSettingsState.Success)?.model ?: return
            dismissAppLanguageSelector()
            if (model.useSystemLanguage || model.selectedLanguage == language) return
            updateAppLanguage { appLanguageSettings.setLanguage(language.languageTag) }
        }

        fun openAppLanguageSelector() {
            val model = (_appLanguageState.value as? AppLanguageSettingsState.Success)?.model ?: return
            if (model.useSystemLanguage || model.saving) return
            appLanguageControls.update { it.copy(selectorVisible = true) }
        }

        fun dismissAppLanguageSelector() {
            appLanguageControls.update { it.copy(selectorVisible = false) }
        }

        private fun updateAppLanguage(update: suspend () -> Unit) {
            if (appLanguageUpdateJob?.isActive == true) return
            appLanguageUpdateJob = viewModelScope.launch {
                appLanguageControls.update { it.copy(saving = true, selectorVisible = false) }
                try {
                    update()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (exception: Exception) {
                    Timber.e(exception, "Failed to save app language settings")
                    appLanguageObservationJob?.cancel()
                    _appLanguageState.value = AppLanguageSettingsState.Error(R.string.error_unknown)
                } finally {
                    appLanguageControls.update { it.copy(saving = false) }
                }
            }
        }

        fun fetchPaxsenixStats() {
            _paxsenixStatsState.value = PaxsenixStatsState.Loading
            viewModelScope.launch(Dispatchers.IO) {
                PaxsenixLyrics
                    .getStats()
                    .onSuccess { _paxsenixStatsState.value = PaxsenixStatsState.Success(it) }
                    .onFailure { _paxsenixStatsState.value = PaxsenixStatsState.Error }
            }
        }

        fun clearLyricsCache() {
            viewModelScope.launch(Dispatchers.IO) {
                lyricsHelper.clearCache()
                database.query {
                    clearRemoteLyrics()
                }
            }
        }

        fun setAiContentFilterEnabled(enabled: Boolean) {
            if (!enabled) aiContentFilterRefreshJob?.cancel()
            aiContentFilterEnabledJob?.cancel()
            aiContentFilterEnabledJob =
                viewModelScope.launch(Dispatchers.IO) {
                    updateAiContentFilterSettings.setEnabled(enabled)
                    if (enabled) {
                        startAiContentFilterRefresh(force = false, showSuccess = false)
                    }
                }
        }

        fun setAiContentFilterIncludeModerate(enabled: Boolean) {
            aiContentFilterModerateJob?.cancel()
            aiContentFilterModerateJob =
                viewModelScope.launch(Dispatchers.IO) {
                    updateAiContentFilterSettings.setIncludeModerateConfidence(enabled)
                }
        }

        fun refreshAiContentFilter() {
            startAiContentFilterRefresh(force = true, showSuccess = true)
        }

        fun openAiContentFilterSource() {
            _aiContentFilterEffects.tryEmit(AiContentFilterSettingsEffect.OpenUrl(AISLIST_URL))
        }

        private fun startAiContentFilterRefresh(
            force: Boolean,
            showSuccess: Boolean,
        ) {
            aiContentFilterRefreshJob?.cancel()
            aiContentFilterRefreshJob =
                viewModelScope.launch(Dispatchers.IO) {
                    refreshingAiContentFilter.value = true
                    try {
                        when (refreshAiContentFilterLists(force)) {
                            is AiContentFilterRefreshResult.Success -> {
                                if (showSuccess) {
                                    _aiContentFilterEffects.emit(
                                        AiContentFilterSettingsEffect.ShowMessage(R.string.ai_content_filter_updated),
                                    )
                                }
                            }

                            AiContentFilterRefreshResult.Unavailable -> {
                                _aiContentFilterEffects.emit(
                                    AiContentFilterSettingsEffect.ShowMessage(R.string.ai_content_filter_update_failed),
                                )
                            }
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        _aiContentFilterEffects.emit(
                            AiContentFilterSettingsEffect.ShowMessage(R.string.ai_content_filter_update_failed),
                        )
                    } finally {
                        refreshingAiContentFilter.value = false
                    }
                }
        }

        private companion object {
            const val AISLIST_URL = "https://aisloplist.com"
        }
    }
