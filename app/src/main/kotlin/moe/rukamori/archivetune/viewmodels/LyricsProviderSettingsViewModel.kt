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
import com.google.common.collect.ImmutableList
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import moe.rukamori.archivetune.R
import moe.rukamori.archivetune.constants.PreferredLyricsProvider
import moe.rukamori.archivetune.lyrics.LyricsProviderSettingsUseCase
import moe.rukamori.archivetune.utils.reportException
import javax.inject.Inject

sealed interface LyricsProviderSettingsState {
    data object Loading : LyricsProviderSettingsState

    data class Success(val data: LyricsProviderSettingsUiData) : LyricsProviderSettingsState

    data object Empty : LyricsProviderSettingsState

    data class Error(@StringRes val messageRes: Int) : LyricsProviderSettingsState
}

@Immutable
data class LyricsProviderSettingsUiData(
    val providers: ImmutableList<PreferredLyricsProvider>,
    val selectedProviders: ImmutableList<PreferredLyricsProvider>,
    val draftProviders: ImmutableList<PreferredLyricsProvider>,
    val isSheetVisible: Boolean = false,
    val isSaving: Boolean = false,
)

@HiltViewModel
class LyricsProviderSettingsViewModel @Inject constructor(
    private val settings: LyricsProviderSettingsUseCase,
) : ViewModel() {
    private val mutableState = MutableStateFlow<LyricsProviderSettingsState>(LyricsProviderSettingsState.Loading)
    val state = mutableState.asStateFlow()

    private var observeJob: Job? = null
    private var saveJob: Job? = null

    init {
        observe()
    }

    fun retry() {
        if (saveJob?.isActive == true) return
        observeJob?.cancel()
        mutableState.value = LyricsProviderSettingsState.Loading
        observe()
    }

    fun openSheet() {
        val data = currentData() ?: return
        if (data.isSaving || data.isSheetVisible) return
        mutableState.value = LyricsProviderSettingsState.Success(
            data.copy(draftProviders = data.selectedProviders, isSheetVisible = true),
        )
    }

    fun dismissSheet() {
        val data = currentData() ?: return
        if (!data.isSheetVisible) return
        mutableState.value = LyricsProviderSettingsState.Success(
            data.copy(draftProviders = data.selectedProviders, isSheetVisible = false),
        )
    }

    fun setProviderChecked(provider: PreferredLyricsProvider, checked: Boolean) {
        val data = currentData() ?: return
        if (!data.isSheetVisible || data.isSaving) return
        val draft = data.providers.filter {
            if (it == provider) checked else it in data.draftProviders
        }
        mutableState.value = LyricsProviderSettingsState.Success(
            data.copy(draftProviders = ImmutableList.copyOf(draft)),
        )
    }

    fun confirmSelection() {
        val data = currentData() ?: return
        if (!data.isSheetVisible || data.isSaving || saveJob?.isActive == true) return
        mutableState.value = LyricsProviderSettingsState.Success(data.copy(isSheetVisible = false, isSaving = true))
        saveJob = viewModelScope.launch {
            try {
                settings.setEnabledProviders(data.draftProviders)
                currentData()?.let { current ->
                    mutableState.value = LyricsProviderSettingsState.Success(current.copy(isSaving = false))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                mutableState.value = LyricsProviderSettingsState.Error(R.string.error_unknown)
            }
        }
    }

    private fun observe() {
        observeJob = viewModelScope.launch {
            try {
                settings.observe().collect { selection ->
                    val current = currentData()
                    mutableState.value = LyricsProviderSettingsState.Success(
                        LyricsProviderSettingsUiData(
                            providers = selection.providers,
                            selectedProviders = selection.selectedProviders,
                            draftProviders = if (current?.isSheetVisible == true) {
                                ImmutableList.copyOf(selection.providers.filter { it in current.draftProviders })
                            } else {
                                selection.selectedProviders
                            },
                            isSheetVisible = current?.isSheetVisible == true,
                            isSaving = current?.isSaving == true,
                        ),
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                reportException(e)
                mutableState.value = LyricsProviderSettingsState.Error(R.string.error_unknown)
            }
        }
    }

    private fun currentData(): LyricsProviderSettingsUiData? =
        (mutableState.value as? LyricsProviderSettingsState.Success)?.data
}
